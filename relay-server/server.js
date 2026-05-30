'use strict';
const http = require('http');
const crypto = require('crypto');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = parseInt(process.env.PORT || '10000', 10);

// Types de messages (doit correspondre exactement au protocole Android Story 8.2)
const MSG = {
  AUTH: 0x01, AUTH_OK: 0x02,
  REGISTER_PEER: 0x03, GET_PEERS: 0x04, PEERS: 0x05,
  UPLOAD: 0x06, FORWARD: 0x07, ACK: 0x08,
  PING: 0x09, PONG: 0x0A,
  // JOIN (0x0B) : ajoute le nœud à l'annuaire de présence sans le déclarer Super-Pair.
  // Story Bully — sépare la "présence dans le réseau" de l'élection formelle.
  // Un nœud envoie JOIN au démarrage ; seul le gagnant Bully envoie ensuite REGISTER_PEER.
  JOIN: 0x0B,
  // Story 9.4 — récupération inter-cluster (mode pull) : requester demande
  // un blockId à un Super-Pair distant, le serveur pivote 80 bytes vers le destinataire.
  // La réponse passe par le canal UPLOAD/FORWARD existant — pas de nouveau message retour.
  REQUEST_BLOCK: 0x0C,
  REQUEST_BLOCK_FORWARDED: 0x0D,
  // SIGNAL (0x0E) : forwarding P2P éphémère — utilisé pour Gossip DHT entre pairs.
  // Pas de buffering : si le destinataire est absent le signal est droppé.
  SIGNAL: 0x0E,
  SIGNAL_RECEIVED: 0x0F,
  // ELECTION_BROADCAST (0x10) : broadcast Bully — forward à toutes les sessions sauf l'émetteur.
  // Fire-and-forget, pas de buffering. ELECTION_RECEIVED (0x11) = opcode de livraison.
  ELECTION_BROADCAST: 0x10,
  ELECTION_RECEIVED: 0x11,
  ERROR: 0xFF
};

// Story 12.1 — MIRROR: app/.../ClusterConstants.kt#MAX_CLUSTER_SIZE
const MAX_CLUSTER_SIZE_SERVER = 2;

const TTL_MS = 180_000;              // TTL registre signaling — 3 min pour survivre aux pauses Doze Android (~90s)
const AUTH_WINDOW_MS = 30_000;       // fenêtre anti-replay auth
const AUTH_TIMEOUT_MS = 10_000;      // délai max pour envoyer AUTH après connexion
const MAX_BLOCK_SIZE = 20_000_000;   // 20 MB — support de plus gros blocs
const MAX_RELAY_BUFFER_ENTRIES = 500; // cap total des blocs en attente en RAM
const MAX_SIGNALING_PEERS = 100;      // cap total des Super-Pairs enregistrés
const HEARTBEAT_INTERVAL_MS = 60_000; // ping ws protocol-level — 60s pour tolérer les pauses réseau Doze (kill après 2 cycles = 120s sans pong)

// Regex IP : IPv4 ou IPv6 compacte — pas de hostname
const IP_RE = /^[\d.:a-fA-F]{2,45}$/;

// ─── Framing ────────────────────────────────────────────────────────────────

function buildFrame(type, payloadBuf = Buffer.alloc(0)) {
  const buf = Buffer.allocUnsafe(5 + payloadBuf.length);
  buf.writeUInt8(type, 0);
  buf.writeUInt32LE(payloadBuf.length, 1);
  payloadBuf.copy(buf, 5);
  return buf;
}

function parseFrame(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 5) return null;
  const type = buf.readUInt8(0);
  const length = buf.readUInt32LE(1);
  if (buf.length !== 5 + length) return null;
  if (length > MAX_BLOCK_SIZE + 128) return null; // +128 pour header JSON UPLOAD
  return { type, payload: buf.slice(5) };
}

function sendError(ws, message) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(buildFrame(MSG.ERROR, Buffer.from(message, 'utf8')));
  }
}

// Envoyer sans risque de crash si la socket est en train de fermer
function safeSend(ws, frame) {
  try {
    if (ws.readyState === WebSocket.OPEN) ws.send(frame);
  } catch { /* socket déjà fermée */ }
}

// ─── Authentification EC P-256 ───────────────────────────────────────────────

function verifyAuth(payload) {
  let msg;
  try { msg = JSON.parse(payload.toString('utf8')); } catch { return { ok: false, reason: 'AUTH JSON invalide' }; }

  const { nodeId, pubKeySpkiDer, timestamp, signature } = msg;
  if (!nodeId || !pubKeySpkiDer || !timestamp || !signature) {
    return { ok: false, reason: 'AUTH champs manquants' };
  }

  // nodeId : exactement 16 chars hex ASCII (pas de multi-octet UTF-8)
  if (typeof nodeId !== 'string' || !/^[0-9a-fA-F]{16}$/.test(nodeId)) {
    return { ok: false, reason: 'nodeId invalide (attendu 16 chars hex ASCII)' };
  }

  // Fenêtre anti-replay 30s — timestamp doit être un nombre fini
  const ts = Number(timestamp);
  if (!Number.isFinite(ts) || ts <= 0) {
    return { ok: false, reason: 'AUTH timestamp invalide (doit être Unix ms fini)' };
  }
  const skew = Math.abs(Date.now() - ts);
  if (skew > AUTH_WINDOW_MS) return { ok: false, reason: `AUTH timestamp hors fenêtre (écart ${skew}ms)` };

  // Payload signé — doit correspondre exactement à ce que signe l'Android Keystore (Story 8.2)
  const signedData = Buffer.from(`MobiCloud-HA-AUTH:${nodeId}:${timestamp}`, 'utf8');

  let publicKey;
  try {
    publicKey = crypto.createPublicKey({
      key: Buffer.from(pubKeySpkiDer, 'base64'),
      format: 'der',
      type: 'spki'
    });
  } catch (e) {
    return { ok: false, reason: `Clé publique invalide : ${e.message}` };
  }

  // Vérifier que la clé est bien EC P-256 (prime256v1)
  if (publicKey.asymmetricKeyType !== 'ec') {
    return { ok: false, reason: 'Clé doit être de type EC' };
  }
  const { namedCurve } = publicKey.asymmetricKeyDetails;
  if (namedCurve !== 'prime256v1') {
    return { ok: false, reason: `Courbe EC invalide : attendu prime256v1, reçu ${namedCurve}` };
  }

  try {
    const sigBuf = Buffer.from(signature, 'base64');
    const valid = crypto.verify('SHA256', signedData, publicKey, sigBuf);
    if (!valid) return { ok: false, reason: 'Signature EC P-256 invalide' };
  } catch (e) {
    return { ok: false, reason: `Erreur vérification signature : ${e.message}` };
  }

  return { ok: true, nodeId, publicKey };
}

// ─── Données en RAM (aucune persistance disque) ─────────────────────────────

// Map<nodeId, { ws, publicKey }>
const sessions = new Map();

// Map<nodeId, { ip, port, reliabilityScore, electedAt, lastSeen, ttlTimer, isSuperPair }>
// isSuperPair=false → simple présence (JOIN), isSuperPair=true → Super-Pair élu (REGISTER_PEER).
const signalingRegistry = new Map();

// Map<blockId, [{ fromNodeId, destNodeId, data: Buffer, ttlTimer }]>
const relayBuffer = new Map();

// SSE clients dashboard (Set de Response objects)
const sseClients = new Set();

function emitSseEvent(type, data) {
  if (sseClients.size === 0) return;
  const msg = `data: ${JSON.stringify({ type, ...data })}\n\n`;
  for (const res of sseClients) {
    try { res.write(msg); } catch { sseClients.delete(res); }
  }
}

// Compteurs d'événements pour le dashboard admin (réinitialisés au redémarrage du process)
const eventCounters = {
  authFailures: 0,
  authSuccesses: 0,
  electionBroadcasts: 0,
  forwardedBlocks: 0,
  forwardedBlocksFailed: 0,
  signalsSent: 0,
  droppedSignals: 0,
  joinEvents: 0,
  departures: 0,
  startedAt: Date.now()
};

// ─── Log circulaire temps réel (dashboard) ───────────────────────────────────
const MAX_LOGS = 200;
const realtimeLogs = [];

function logEvent(level, category, message, meta = {}) {
  const { ts: _ts, level: _l, category: _c, message: _m, ...safeMeta } = meta;
  const entry = { ts: Date.now(), level, category, message, ...safeMeta };
  realtimeLogs.push(entry);
  if (realtimeLogs.length > MAX_LOGS) realtimeLogs.shift();
  const prefix = level === 'ERROR' ? '[ERR]' : level === 'WARN' ? '[WRN]' : '[INF]';
  console.log(`${prefix}[${category}] ${message}`);
}

// ─── Suivi churn (fenêtre glissante 5 minutes) ───────────────────────────────
const CHURN_WINDOW_MS = 5 * 60 * 1000;
const MAX_CHURN_EVENTS = 10000;
const churnEvents = []; // timestamps de départs

function recordChurn(nodeId) {
  const now = Date.now();
  churnEvents.push({ ts: now, nodeId });
  while (churnEvents.length > 0 && now - churnEvents[0].ts > CHURN_WINDOW_MS) {
    churnEvents.shift();
  }
  if (churnEvents.length > MAX_CHURN_EVENTS) {
    churnEvents.splice(0, churnEvents.length - MAX_CHURN_EVENTS);
  }
}

function getChurnRate() {
  const now = Date.now();
  const recent = churnEvents.filter(e => now - e.ts <= CHURN_WINDOW_MS);
  const uniqueDeparted = new Set(recent.map(e => e.nodeId));
  const total = signalingRegistry.size + uniqueDeparted.size;
  if (total === 0) return 0;
  return Math.round((uniqueDeparted.size / total) * 100);
}

// ─── Vue agrégée par cluster ──────────────────────────────────────────────────
function buildClusterView() {
  const clusters = new Map(); // clusterId → { superPeer, members[], totalFreeBytes, avgReliability }

  for (const [nodeId, entry] of signalingRegistry.entries()) {
    const cid = entry.clusterId || '__no_cluster__';
    if (!clusters.has(cid)) {
      clusters.set(cid, { clusterId: cid, superPeer: null, members: [], totalFreeBytes: 0, reliabilitySum: 0 });
    }
    const c = clusters.get(cid);
    c.members.push({
      nodeId,
      isSuperPair: entry.isSuperPair,
      reliabilityScore: entry.reliabilityScore ?? 0,
      freeBytes: entry.freeBytes ?? 0,
      isConnected: sessions.has(nodeId),
      lastSeen: entry.lastSeen
    });
    c.totalFreeBytes += entry.freeBytes ?? 0;
    c.reliabilitySum += entry.reliabilityScore ?? 0;
    if (entry.isSuperPair) {
      if (!c.superPeer || (entry.reliabilityScore ?? 0) > (c.superPeer.reliabilityScore ?? 0)) {
        c.superPeer = { nodeId, electedAt: entry.electedAt, reliabilityScore: entry.reliabilityScore };
      }
    }
  }

  return Array.from(clusters.values()).map(c => ({
    clusterId: c.clusterId,
    memberCount: c.members.length,
    connectedCount: c.members.filter(m => m.isConnected).length,
    superPeer: c.superPeer,
    totalFreeBytes: c.totalFreeBytes,
    avgReliabilityScore: c.members.length > 0 ? c.reliabilitySum / c.members.length : 0,
    members: c.members
  }));
}

// ─── Signaling (REGISTER_PEER / GET_PEERS) ──────────────────────────────────

function handleRegisterPeer(nodeId, payload) {
  let entry;
  try { entry = JSON.parse(payload.toString('utf8')); } catch { return false; }

  const { ip, port, reliabilityScore, electedAt, clusterId, freeBytes, currentMemberCount } = entry;
  console.log(`[REGISTER-DBG] nodeId=${nodeId.slice(0,8)} ip=${ip} port=${port} typeof_port=${typeof port}`);
  if (!ip || typeof port !== 'number' || port < 0 || port > 65535) {
    console.warn(`[REGISTER_PEER] rejeté: ip=${ip} port=${port} typeof_port=${typeof port}`);
    return false;
  }

  // Valider le format IP (pas de hostname, pas de loopback non contrôlé)
  if (typeof ip !== 'string' || !IP_RE.test(ip) || ip.length > 45) {
    console.warn(`[REGISTER_PEER] rejeté: IP_RE invalide — ip=${ip}`);
    return false;
  }

  // clusterId optionnel : UUID v4 strict ou "" pour nœuds legacy.
  // Story 9.1 review (option b) : valider format UUID v4 ; coerce en "" + warn si invalide.
  const UUID_V4_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  let clusterIdStr = '';
  if (clusterId === undefined || clusterId === null || clusterId === '') {
    clusterIdStr = '';
  } else if (typeof clusterId === 'string' && UUID_V4_RE.test(clusterId)) {
    clusterIdStr = clusterId;
  } else {
    console.warn(`[SIGNALING] clusterId invalide rejeté (coerce en "") — nodeId=${nodeId.slice(0, 8)} type=${typeof clusterId}`);
    clusterIdStr = '';
  }

  // Story 9.2 — freeBytes optionnel : Number entier fini, 0 ≤ x ≤ MAX_SAFE_INTEGER ;
  // sinon coerce en 0 + warn. Absent/null ⇒ legacy/JOIN amont, on stocke 0 sans warn.
  // Story 9.2 review (P2/P3) : exiger typeof === 'number' (refuse "1234", true/false) ET
  // borner par MAX_SAFE_INTEGER (refuse 1e18 qui passerait isFinite mais perdrait la précision).
  let freeBytesNum = 0;
  if (freeBytes !== undefined && freeBytes !== null) {
    if (typeof freeBytes === 'number'
      && Number.isFinite(freeBytes)
      && freeBytes >= 0
      && freeBytes <= Number.MAX_SAFE_INTEGER) {
      freeBytesNum = Math.floor(freeBytes);
    } else {
      console.warn(`[SIGNALING] freeBytes invalide rejeté (coerce en 0) — nodeId=${nodeId.slice(0, 8)} type=${typeof freeBytes}`);
    }
  }

  // Story 12.1 — currentMemberCount : Number entier fini 0 ≤ x ≤ MAX_CLUSTER_SIZE_SERVER ;
  // coerce en 0 + warn si invalide — même pattern que freeBytes.
  let currentMemberCountNum = 0;
  if (currentMemberCount !== undefined && currentMemberCount !== null) {
    if (typeof currentMemberCount === 'number'
      && Number.isFinite(currentMemberCount)
      && currentMemberCount >= 0
      && currentMemberCount <= MAX_CLUSTER_SIZE_SERVER) {
      currentMemberCountNum = Math.floor(currentMemberCount);
    } else {
      console.warn(`[SIGNALING] currentMemberCount invalide rejeté (coerce en 0) — nodeId=${nodeId.slice(0, 8)} val=${currentMemberCount}`);
    }
  }

  // Cap : refuser si annuaire plein et ce nœud n'est pas déjà enregistré
  if (!signalingRegistry.has(nodeId) && signalingRegistry.size >= MAX_SIGNALING_PEERS) return false;

  // Unicité SP par cluster : si un SP connecté existe déjà pour ce clusterId, rejeter.
  // Cela évite le double-SP après reconnexion simultanée (race Bully post-Doze).
  // Si le SP existant est déconnecté (TTL non encore expiré), on le dégrade pour laisser
  // le nouveau prendre le rôle (failover normal).
  if (clusterIdStr) {
    for (const [existingId, existingEntry] of signalingRegistry.entries()) {
      if (existingEntry.isSuperPair
          && existingEntry.clusterId === clusterIdStr
          && existingId !== nodeId) {
        if (sessions.has(existingId)) {
          logEvent('WARN', 'ELECTION',
            `REGISTER_PEER rejeté : SP déjà actif cluster=${clusterIdStr.slice(0, 8)} sp=${existingId.slice(0, 8)} challenger=${nodeId.slice(0, 8)}`,
            { nodeId: nodeId.slice(0, 8), existingSP: existingId.slice(0, 8) });
          return false;
        } else {
          // SP existant déconnecté → le révoquer pour que le nouveau le remplace
          existingEntry.isSuperPair = false;
          logEvent('INFO', 'ELECTION',
            `SP révoqué (déconnecté) → ${nodeId.slice(0, 8)} prend le rôle cluster=${clusterIdStr.slice(0, 8)}`,
            { revokedSP: existingId.slice(0, 8), newSP: nodeId.slice(0, 8) });
        }
      }
    }
  }

  // Annuler l'ancien timer TTL si re-registration
  const existing = signalingRegistry.get(nodeId);
  if (existing?.ttlTimer) clearTimeout(existing.ttlTimer);

  const ttlTimer = setTimeout(() => {
    signalingRegistry.delete(nodeId);
    recordChurn(nodeId);
    eventCounters.departures++;
    logEvent('WARN', 'DEPART', `Nœud TTL expiré (${TTL_MS / 1000}s sans heartbeat) : ${nodeId.slice(0, 8)}`, { nodeId, reason: 'TTL_EXPIRED' });
  }, TTL_MS);

  // Story stabilite — preserve pubKeyB64 pour que GET_PEERS reste fonctionnel meme
  // apres fermeture WS pendant la fenetre TTL. Code review P1 : priorite a la session
  // fraiche (rotation de cle) sur le cache du registre.
  const pubKeyB64 = sessions.get(nodeId)?.pubKeyB64 ?? existing?.pubKeyB64 ?? '';

  signalingRegistry.set(nodeId, {
    ip, port,
    reliabilityScore: reliabilityScore ?? 0.5,
    electedAt: electedAt ?? Date.now(),
    clusterId: clusterIdStr,
    freeBytes: freeBytesNum,
    currentMemberCount: currentMemberCountNum,
    lastSeen: Date.now(),
    ttlTimer,
    isSuperPair: true,
    pubKeyB64
  });

  logEvent('INFO', 'ELECTION', `Super-Peer élu : ${nodeId.slice(0, 8)} cluster=${clusterIdStr.slice(0, 8) || 'legacy'} score=${(reliabilityScore ?? 0.5).toFixed(2)}`, { nodeId, clusterId: clusterIdStr, reliabilityScore, ip, port });
  return true;
}

// JOIN : ajoute le nœud à l'annuaire en tant que simple participant (isSuperPair=false).
// Si le nœud est DÉJÀ enregistré comme Super-Pair, le statut est préservé (re-JOIN ne dégrade pas).
// ip/port sont optionnels — peuvent être omis pour les nœuds non joignables directement.
function handleJoin(nodeId, payload) {
  let entry;
  try { entry = JSON.parse(payload.toString('utf8')); } catch (e) {
    console.warn(`[JOIN] JSON parse failed for nodeId=${nodeId.slice(0,8)}: ${e.message}`);
    return false;
  }

  const { ip, port, reliabilityScore, clusterId: payloadClusterId, freeBytes: payloadFreeBytes } = entry;

  // ip/port optionnels — si présents, doivent être valides
  if (ip !== undefined && ip !== null) {
    if (typeof ip !== 'string' || !IP_RE.test(ip) || ip.length > 45) {
      console.warn(`[JOIN] rejeté: ip invalide — ip=${ip}`);
      return false;
    }
  }
  if (port !== undefined && port !== null) {
    if (typeof port !== 'number' || port < 0 || port > 65535) {
      console.warn(`[JOIN] rejeté: port invalide — port=${port} typeof=${typeof port}`);
      return false;
    }
  }

  if (!signalingRegistry.has(nodeId) && signalingRegistry.size >= MAX_SIGNALING_PEERS) return false;

  const existing = signalingRegistry.get(nodeId);
  if (existing?.ttlTimer) clearTimeout(existing.ttlTimer);

  const ttlTimer = setTimeout(() => {
    signalingRegistry.delete(nodeId);
    recordChurn(nodeId);
    eventCounters.departures++;
    logEvent('WARN', 'DEPART', `Nœud TTL expiré (${TTL_MS / 1000}s sans heartbeat) : ${nodeId.slice(0, 8)}`, { nodeId, reason: 'TTL_EXPIRED' });
  }, TTL_MS);

  const wasSuperPair = existing?.isSuperPair ?? false;

  // clusterId : préserver l'existant (re-JOIN heartbeat) ; sinon accepter du payload si UUID v4 valide.
  const UUID_V4_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  let clusterIdStr = existing?.clusterId ?? '';
  if (!clusterIdStr && typeof payloadClusterId === 'string' && UUID_V4_RE.test(payloadClusterId)) {
    clusterIdStr = payloadClusterId;
  }

  // freeBytes : code review P2 — accepter la valeur du payload a chaque re-JOIN si
  // valide (heartbeat reflete le disque a jour). Fallback sur l'existant si absent/invalide.
  // L'ancienne logique `existing === undefined` bloquait toute mise a jour apres la 1re JOIN
  // → drift entre disque reel (ex. 500MB) et registre (5GB) pendant la duree de vie du noeud.
  let freeBytesNum = existing?.freeBytes ?? 0;
  if (typeof payloadFreeBytes === 'number'
      && Number.isFinite(payloadFreeBytes) && payloadFreeBytes >= 0
      && payloadFreeBytes <= Number.MAX_SAFE_INTEGER) {
    freeBytesNum = Math.floor(payloadFreeBytes);
  }

  // Story stabilite — preserve pubKeyB64 pour que GET_PEERS reste fonctionnel meme
  // apres fermeture WS pendant la fenetre TTL. Code review P1 : priorite a la session
  // fraiche (rotation de cle) sur le cache du registre.
  const pubKeyB64 = sessions.get(nodeId)?.pubKeyB64 ?? existing?.pubKeyB64 ?? '';

  signalingRegistry.set(nodeId, {
    ip: ip ?? '0.0.0.0',
    port: port ?? 0,
    reliabilityScore: reliabilityScore ?? 0.5,
    electedAt: existing?.electedAt ?? null,
    clusterId: clusterIdStr,
    freeBytes: freeBytesNum,
    currentMemberCount: existing?.currentMemberCount ?? 0,
    lastSeen: Date.now(),
    ttlTimer,
    isSuperPair: wasSuperPair,
    pubKeyB64
  });

  eventCounters.joinEvents++;
  logEvent('INFO', 'JOIN', `Nœud connecté : ${nodeId.slice(0, 8)} ip=${ip ?? '?'}:${port ?? '?'}${wasSuperPair ? ' [Super-Peer]' : ''} cluster=${clusterIdStr.slice(0,8) || 'none'}`, { nodeId, ip, port });
  return true;
}

function handleGetPeers(ws) {
  const peers = [];
  for (const [nodeId, entry] of signalingRegistry.entries()) {
    // Story stabilite — pubKey lue directement depuis signalingRegistry (cachee a l'AUTH).
    // Reste disponible meme apres fermeture WS pendant la fenetre TTL (60s).
    // Fallback session pour les entries legacy qui n'auraient pas encore pubKeyB64.
    let pubKeySpkiDerB64 = entry.pubKeyB64 ?? '';
    if (!pubKeySpkiDerB64) {
      try {
        const session = sessions.get(nodeId);
        if (session?.pubKeyB64) {
          pubKeySpkiDerB64 = session.pubKeyB64;
        } else if (session?.publicKey) {
          pubKeySpkiDerB64 = session.publicKey
            .export({ format: 'der', type: 'spki' })
            .toString('base64');
        }
      } catch (e) {
        console.warn(`[GET_PEERS] export clé publique échoué pour nodeId=${nodeId.slice(0, 8)} : ${e.message}`);
      }
    }

    peers.push({
      nodeId,
      ip: entry.ip,
      port: entry.port,
      reliabilityScore: entry.reliabilityScore,
      lastSeen: entry.lastSeen,
      isSuperPair: entry.isSuperPair === true,
      clusterId: entry.clusterId ?? '',
      freeBytes: entry.freeBytes ?? 0,
      // Story 10.1 — clé publique EC P-256 SPKI-DER encodée Base64.
      pubKeySpkiDerB64,
      // Story 12.1 — charge cluster (membres actifs) pour load balancing admission.
      currentMemberCount: entry.currentMemberCount ?? 0
    });
  }
  safeSend(ws, buildFrame(MSG.PEERS, Buffer.from(JSON.stringify(peers), 'utf8')));
}

// ─── Relay Store-and-Forward (UPLOAD / FORWARD) ──────────────────────────────

function flushPendingBlocks(nodeId, ws) {
  for (const [blockId, entries] of relayBuffer.entries()) {
    const pending = entries.filter(e => e.destNodeId === nodeId);
    if (pending.length === 0) continue;

    // Ne purger que les entrées effectivement délivrées : si la ws ferme
    // mid-flush, les blocs non envoyés restent en buffer avec leur TTL armé
    // pour une prochaine reconnexion.
    const delivered = [];
    for (const entry of pending) {
      if (ws.readyState !== WebSocket.OPEN) break;
      const forwardPayload = Buffer.allocUnsafe(16 + 64 + entry.data.length);
      Buffer.from(entry.fromNodeId.padEnd(16, '\0'), 'utf8').copy(forwardPayload, 0);
      Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(forwardPayload, 16);
      entry.data.copy(forwardPayload, 80);
      safeSend(ws, buildFrame(MSG.FORWARD, forwardPayload));
      clearTimeout(entry.ttlTimer);
      delivered.push(entry);
      console.log(`[RELAY] FLUSH ${blockId.slice(0, 16)} → nodeId=${nodeId.slice(0, 8)}`);
    }

    if (delivered.length === 0) continue;
    const remaining = entries.filter(e => !delivered.includes(e));
    if (remaining.length === 0) relayBuffer.delete(blockId);
    else relayBuffer.set(blockId, remaining);
  }
}

function handleUpload(fromNodeId, payload, senderWs) {
  // Payload binaire: 16 bytes destNodeId + 64 bytes blockId + data
  if (payload.length < 80) {
    sendError(senderWs, 'UPLOAD payload trop court (min 80 bytes)');
    return;
  }
  // Strip uniquement le padding NUL trailing (pas tous les NUL : un \0 interior
  // permettrait de forger une collision avec un autre nodeId/blockId via injection).
  // Aligné sur handleRequestBlock.
  const destNodeId = payload.slice(0, 16).toString('utf8').replace(/\0+$/, '').trim();
  const blockId = payload.slice(16, 80).toString('utf8').replace(/\0+$/, '').trim();
  const data = payload.slice(80); // bloc chiffré AES-256 GCM — JAMAIS transformé (AC#4 Zero-Knowledge)

  // Valider destNodeId et blockId après strip — contraintes strictes alignées sur REQUEST_BLOCK.
  if (!destNodeId || destNodeId.length !== 16) {
    sendError(senderWs, 'UPLOAD destNodeId invalide (attendu 16 chars)');
    return;
  }
  if (!/^[0-9a-fA-F]{64}$/.test(blockId)) {
    sendError(senderWs, 'UPLOAD blockId invalide (attendu 64 chars hex)');
    return;
  }
  // Anti-loop : un nœud ne peut pas s'uploader un bloc à lui-même.
  if (destNodeId.toLowerCase() === String(fromNodeId).toLowerCase()) {
    sendError(senderWs, 'UPLOAD destNodeId == fromNodeId interdit');
    return;
  }

  // Tenter forward immédiat si le destinataire est connecté
  const destSession = sessions.get(destNodeId);
  if (destSession && destSession.ws.readyState === WebSocket.OPEN) {
    const forwardPayload = Buffer.allocUnsafe(16 + 64 + data.length);
    Buffer.from(fromNodeId.padEnd(16, '\0'), 'utf8').copy(forwardPayload, 0);
    Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(forwardPayload, 16);
    data.copy(forwardPayload, 80);
    safeSend(destSession.ws, buildFrame(MSG.FORWARD, forwardPayload));
    eventCounters.forwardedBlocks++;
    logEvent('INFO', 'RELAY', `Bloc forwardé : ${blockId.slice(0, 16)}… → ${destNodeId.slice(0, 8)} (direct)`, { blockId: blockId.slice(0, 16), from: fromNodeId.slice(0, 8), to: destNodeId.slice(0, 8) });
    emitSseEvent('block_transfer', { from: fromNodeId, to: destNodeId, kind: 'block', blockId: blockId.slice(0, 16), bytes: data.length });
  } else {
    // Cap buffer RAM
    if (relayBuffer.size >= MAX_RELAY_BUFFER_ENTRIES) {
      sendError(senderWs, 'Relay buffer plein — réessayer plus tard');
      return;
    }

    const existing = relayBuffer.get(blockId) || [];

    // Déduplication : si (blockId, destNodeId) existe déjà, remplacer l'entrée
    const dupIdx = existing.findIndex(e => e.destNodeId === destNodeId);
    if (dupIdx !== -1) {
      clearTimeout(existing[dupIdx].ttlTimer);
      existing.splice(dupIdx, 1);
    }

    // Capturer l'entrée par référence dans la closure TTL (évite d'évincer des entrées plus récentes)
    const newEntry = { fromNodeId, destNodeId, data, ttlTimer: null };
    const ttlTimer = setTimeout(() => {
      const arr = relayBuffer.get(blockId);
      if (arr) {
        const filtered = arr.filter(e => e !== newEntry);
        if (filtered.length === 0) relayBuffer.delete(blockId);
        else relayBuffer.set(blockId, filtered);
      }
      console.log(`[RELAY] TTL expiré — blockId=${blockId.slice(0, 16)} destNodeId=${destNodeId.slice(0, 8)} purgé`);
    }, TTL_MS);
    newEntry.ttlTimer = ttlTimer;

    existing.push(newEntry);
    relayBuffer.set(blockId, existing);
    logEvent('WARN', 'RELAY', `Bloc bufferisé (dest absent) : ${blockId.slice(0, 16)}… → ${destNodeId.slice(0, 8)}`, { blockId: blockId.slice(0, 16), to: destNodeId.slice(0, 8) });
  }

  // ACK au sender
  safeSend(senderWs, buildFrame(MSG.ACK, Buffer.from(JSON.stringify({ blockId }), 'utf8')));
}

// ─── Story 9.4 — REQUEST_BLOCK (pull inter-cluster) ─────────────────────────

// Le requester envoie REQUEST_BLOCK(destNodeId, blockId) ; le serveur pivote
// 80 bytes vers le destinataire en REQUEST_BLOCK_FORWARDED(fromNodeId, blockId).
// PAS de buffering : si dest absent, on rejette (le requester time-out de toute façon).
function handleRequestBlock(fromNodeId, payload, senderWs) {
  if (payload.length !== 80) {
    sendError(senderWs, 'REQUEST_BLOCK payload invalide (attendu 80 bytes)');
    return;
  }
  // Strip uniquement le padding NUL trailing (pas tous les NUL : un \0 interior
  // permettrait de forger une collision avec un autre nodeId via injection).
  const destNodeId = payload.slice(0, 16).toString('utf8').replace(/\0+$/, '').trim();
  const blockId = payload.slice(16, 80).toString('utf8').replace(/\0+$/, '').trim();

  if (!destNodeId || destNodeId.length !== 16) {
    sendError(senderWs, 'REQUEST_BLOCK destNodeId invalide (attendu 16 chars)');
    return;
  }
  if (!/^[0-9a-fA-F]{64}$/.test(blockId)) {
    sendError(senderWs, 'REQUEST_BLOCK blockId invalide (attendu 64 chars hex)');
    return;
  }
  // AC#10 — anti-loop : un nœud ne peut pas se demander un bloc à lui-même.
  // Comparaison case-insensitive pour éviter le bypass si fromNodeId=auth-canonicalisé
  // et destNodeId arrive en case mixte.
  if (destNodeId.toLowerCase() === String(fromNodeId).toLowerCase()) {
    sendError(senderWs, 'REQUEST_BLOCK destNodeId == fromNodeId interdit');
    return;
  }

  const destSession = sessions.get(destNodeId);
  if (!destSession || destSession.ws.readyState !== WebSocket.OPEN) {
    sendError(senderWs, 'REQUEST_BLOCK destinataire injoignable');
    return;
  }

  // Forward : 16 bytes fromNodeId + 64 bytes blockId
  // Buffer.alloc (zéroïsé) : `allocUnsafe` peut fuiter de la mémoire arbitraire si
  // une copie partielle laisse des octets non-écrits.
  const forwardPayload = Buffer.alloc(80);
  Buffer.from(fromNodeId.padEnd(16, '\0'), 'utf8').copy(forwardPayload, 0);
  Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(forwardPayload, 16);
  safeSend(destSession.ws, buildFrame(MSG.REQUEST_BLOCK_FORWARDED, forwardPayload));
  console.log(`[RELAY] REQUEST_BLOCK ${blockId.slice(0, 16)} : ${fromNodeId.slice(0, 8)} → ${destNodeId.slice(0, 8)}`);
}

// ─── SIGNAL — forwarding P2P éphémère (Gossip DHT, etc.) ────────────────────
// Payload : 16 bytes destNodeId (UTF-8, NUL-paddé) + data arbitraire.
// Pas de buffering — le signal est droppé si le destinataire est absent.
function handleSignal(fromNodeId, payload) {
  if (payload.length < 16) return;
  const destNodeId = payload.slice(0, 16).toString('utf8').replace(/\0+$/, '').trim();
  const data = payload.slice(16);
  if (!destNodeId || destNodeId.length < 4) return;
  const destSession = sessions.get(destNodeId);
  if (destSession && destSession.ws.readyState === WebSocket.OPEN) {
    const out = Buffer.allocUnsafe(16 + data.length);
    Buffer.from(fromNodeId.padEnd(16, '\0'), 'utf8').copy(out, 0);
    data.copy(out, 16);
    safeSend(destSession.ws, buildFrame(MSG.SIGNAL_RECEIVED, out));
    eventCounters.signalsSent++;
    emitSseEvent('block_transfer', { from: fromNodeId, to: destNodeId, kind: 'signal', bytes: data.length });
  } else {
    eventCounters.droppedSignals++;
    logEvent('WARN', 'GOSSIP', `Signal Gossip droppé (dest absent) : ${fromNodeId.slice(0,8)} → ${destNodeId.slice(0,8)}`, { from: fromNodeId.slice(0,8), to: destNodeId.slice(0,8) });
  }
}

// ─── ELECTION_BROADCAST — broadcast Bully (ELECTION / ALIVE / COORDINATOR) ──
// Payload : JSON sérialisé de ElectionPayload (senderNodeId, type, reliabilityScore,
// signatureBytes base64, clusterId, timestampMs).
// Forward à toutes les sessions connectées SAUF l'émetteur. Fire-and-forget.
function handleElectionBroadcast(fromNodeId, payload) {
  // Filtrer par clusterId : un broadcast Bully ne concerne que les nœuds du même cluster.
  // Sans ce filtre, une élection dans le cluster A perturberait les nœuds du cluster B.
  let senderClusterId = '';
  try { senderClusterId = JSON.parse(payload.toString('utf8')).clusterId ?? ''; } catch { /* ignore */ }

  let forwarded = 0;
  for (const [nodeId, session] of sessions.entries()) {
    if (nodeId === fromNodeId) continue;
    // Si le sender a un clusterId valide, ne forwarder qu'aux nœuds du même cluster.
    // Legacy nodes (clusterId vide) reçoivent tous les broadcasts pour rétrocompatibilité.
    if (senderClusterId) {
      const peerEntry = signalingRegistry.get(nodeId);
      if (peerEntry?.clusterId && peerEntry.clusterId !== senderClusterId) continue;
    }
    if (session.ws.readyState === WebSocket.OPEN) {
      safeSend(session.ws, buildFrame(MSG.ELECTION_RECEIVED, payload));
      forwarded++;
    }
  }
  eventCounters.electionBroadcasts++;
  let msgType = '?';
  let clusterId = '';
  try { const p = JSON.parse(payload.toString('utf8')); msgType = p.type ?? '?'; clusterId = p.clusterId ?? ''; } catch { /* ignore */ }
  logEvent('INFO', 'ELECTION', `Bully broadcast type=${msgType} depuis ${fromNodeId.slice(0,8)} → ${forwarded} nœud(s)`, { from: fromNodeId.slice(0, 8), type: msgType, clusterId: clusterId.slice(0, 8), forwarded });
  for (const [nodeId] of sessions.entries()) {
    if (nodeId !== fromNodeId) emitSseEvent('block_transfer', { from: fromNodeId, to: nodeId, kind: 'election', bytes: payload.length });
  }
}

// ─── Serveur HTTP + WebSocketServer ─────────────────────────────────────────

// Code review P4 : Origines autorisees pour les endpoints sensibles (admin/*).
// Read-only endpoints (/health, /metrics/*) gardent une CORS plus permissive plus bas.
const ALLOWED_ADMIN_ORIGINS = (process.env.ADMIN_ALLOWED_ORIGINS ?? '')
  .split(',').map(s => s.trim()).filter(Boolean);

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Content-Type': 'application/json'
};

function buildAdminCorsHeaders(reqOrigin) {
  const allowed = reqOrigin && ALLOWED_ADMIN_ORIGINS.includes(reqOrigin) ? reqOrigin : 'null';
  return {
    ...CORS_HEADERS,
    'Access-Control-Allow-Origin': allowed,
    'Vary': 'Origin'
  };
}

const ADMIN_SECRET = process.env.ADMIN_SECRET ?? '';
if (!ADMIN_SECRET) {
  console.warn('[ADMIN] ADMIN_SECRET non configure — /admin/* desactive (401 systematique).');
}
if (ALLOWED_ADMIN_ORIGINS.length === 0) {
  console.warn('[ADMIN] ADMIN_ALLOWED_ORIGINS vide — aucune origine autorisee pour /admin/*.');
}

function sendJson(res, data) {
  const body = JSON.stringify(data);
  res.writeHead(200, CORS_HEADERS);
  res.end(body);
}

const httpServer = http.createServer((req, res) => {
  // Preflight CORS
  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS_HEADERS);
    res.end();
    return;
  }

  const url = req.url.split('?')[0];

  // ── POST /admin/reset — déconnecte tous les nœuds et purge l'état du relay ──
  // Code review P4 : Origine restreinte, comparaison timing-safe, log des echecs avec IP.
  if (req.method === 'POST' && url === '/admin/reset') {
    const reqOrigin = req.headers['origin'] ?? '';
    const adminHeaders = buildAdminCorsHeaders(reqOrigin);
    const remoteIp = req.socket?.remoteAddress ?? '?';
    if (ALLOWED_ADMIN_ORIGINS.length > 0 && !ALLOWED_ADMIN_ORIGINS.includes(reqOrigin)) {
      logEvent('WARN', 'ADMIN', `Reset refuse : origine non autorisee (${reqOrigin || 'null'}) ip=${remoteIp}`, { origin: reqOrigin, ip: remoteIp });
      res.writeHead(403, adminHeaders);
      res.end('{"error":"Forbidden origin"}');
      return;
    }
    const auth = req.headers['authorization'] ?? '';
    const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
    let tokenOk = false;
    if (ADMIN_SECRET && token.length === ADMIN_SECRET.length) {
      try {
        tokenOk = crypto.timingSafeEqual(Buffer.from(token), Buffer.from(ADMIN_SECRET));
      } catch { tokenOk = false; }
    }
    if (!ADMIN_SECRET || !tokenOk) {
      logEvent('WARN', 'ADMIN', `Reset refuse : auth invalide ip=${remoteIp} origin=${reqOrigin || 'null'}`, { ip: remoteIp, origin: reqOrigin });
      res.writeHead(401, adminHeaders);
      res.end('{"error":"Unauthorized"}');
      return;
    }
    const disconnected = sessions.size;
    for (const [, session] of sessions.entries()) {
      try { session.ws.close(1001, 'Admin reset'); } catch { /* déjà fermée */ }
    }
    sessions.clear();
    // Code review P5 : defensive — un entry corrompu ne doit pas avorter le reset.
    for (const entry of signalingRegistry.values()) {
      try { if (entry?.ttlTimer) clearTimeout(entry.ttlTimer); } catch { /* ignore */ }
    }
    signalingRegistry.clear();
    for (const entries of relayBuffer.values()) {
      if (!entries) continue;
      for (const e of entries) {
        try { if (e?.ttlTimer) clearTimeout(e.ttlTimer); } catch { /* ignore */ }
      }
    }
    relayBuffer.clear();
    // Code review P11 : purger aussi les KPI pour eviter les compteurs fantomes post-reset.
    for (const k of Object.keys(eventCounters)) {
      if (k !== 'startedAt') eventCounters[k] = 0;
    }
    churnEvents.length = 0;
    logEvent('WARN', 'ADMIN', `Reset administrateur : ${disconnected} nœud(s) déconnecté(s) ip=${remoteIp}`, { disconnected, ip: remoteIp });
    res.writeHead(200, adminHeaders);
    res.end(JSON.stringify({ ok: true, disconnected }));
    return;
  }

  if (req.method !== 'GET') {
    res.writeHead(405, CORS_HEADERS);
    res.end('{"error":"Method Not Allowed"}');
    return;
  }

  if (url === '/health') {
    let superPeerCount = 0;
    for (const entry of signalingRegistry.values()) {
      if (entry.isSuperPair) superPeerCount++;
    }
    sendJson(res, {
      status: 'ok',
      sessions: sessions.size,
      pendingBlocks: relayBuffer.size,
      participants: signalingRegistry.size,
      registeredSuperPeers: superPeerCount
    });

  } else if (url === '/metrics/topology') {
    const nodes = [];
    for (const [nodeId, entry] of signalingRegistry.entries()) {
      nodes.push({
        id: nodeId,
        isSuperPair: entry.isSuperPair ?? false,
        clusterId: entry.clusterId ?? '',
        reliabilityScore: entry.reliabilityScore ?? 0,
        freeBytes: entry.freeBytes ?? 0,
        ip: entry.ip ?? '',
        port: entry.port ?? 0,
        lastSeen: entry.lastSeen ?? 0,
        isConnected: sessions.has(nodeId)
      });
    }
    // Liens intra-cluster : full mesh entre nœuds connectés du même cluster
    const clusterGroups = new Map();
    for (const n of nodes) {
      if (!n.clusterId || !n.isConnected) continue;
      if (!clusterGroups.has(n.clusterId)) clusterGroups.set(n.clusterId, []);
      clusterGroups.get(n.clusterId).push(n.id);
    }
    const links = [];
    for (const members of clusterGroups.values()) {
      for (let i = 0; i < members.length; i++) {
        for (let j = i + 1; j < members.length; j++) {
          links.push({ source: members[i], target: members[j] });
        }
      }
    }

    sendJson(res, { nodes, links, activeSessions: sessions.size });

  } else if (url === '/metrics/events') {
    sendJson(res, {
      authFailures: eventCounters.authFailures,
      authSuccesses: eventCounters.authSuccesses,
      electionBroadcasts: eventCounters.electionBroadcasts,
      forwardedBlocks: eventCounters.forwardedBlocks,
      forwardedBlocksFailed: eventCounters.forwardedBlocksFailed,
      signalsSent: eventCounters.signalsSent,
      droppedSignals: eventCounters.droppedSignals,
      joinEvents: eventCounters.joinEvents,
      departures: eventCounters.departures,
      churnRate: getChurnRate(),
      uptimeMs: Date.now() - eventCounters.startedAt
    });

  } else if (url === '/metrics/clusters') {
    sendJson(res, {
      clusters: buildClusterView(),
      totalNodes: signalingRegistry.size,
      totalConnected: sessions.size,
      churnRate: getChurnRate()
    });

  } else if (url === '/metrics/logs') {
    // Paramètre ?since=timestamp pour ne récupérer que les nouveaux logs
    const sinceParam = new URLSearchParams(req.url.split('?')[1] ?? '').get('since');
    const sinceRaw = sinceParam ? parseInt(sinceParam, 10) : 0;
    const since = Number.isFinite(sinceRaw) && sinceRaw > 0 ? sinceRaw : 0;
    const filtered = since > 0 ? realtimeLogs.filter(e => e.ts > since) : realtimeLogs.slice(-50);
    sendJson(res, { logs: filtered, serverTime: Date.now() });

  } else if (url === '/dashboard/events') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'Access-Control-Allow-Origin': '*',
      'X-Accel-Buffering': 'no',
    });
    res.write(': keep-alive\n\n');
    sseClients.add(res);
    req.on('close', () => sseClients.delete(res));
    return;

  } else {
    res.writeHead(404, CORS_HEADERS);
    res.end('{"error":"Not found"}');
  }
});

// maxPayload : rejeter les frames dépassant la taille max avant buffering complet
const wss = new WebSocketServer({ server: httpServer, maxPayload: MAX_BLOCK_SIZE + 256 });

wss.on('connection', (ws) => {
  // Heartbeat protocol-level : détecte les sockets zombies (réseau coupé sans close TCP).
  // missedPings : kill après 2 cycles consécutifs sans pong (tolérance Doze Android ~90s).
  // Un seul cycle manqué peut être un pic réseau 4G ou un réveil tardif de Doze.
  ws.missedPings = 0;
  ws.on('pong', () => { ws.missedPings = 0; });

  // État de la connexion — non authentifié jusqu'au premier message AUTH
  let authState = null; // null = non auth ; après AUTH_OK = { nodeId, publicKey }

  // Fermer les connexions non-authentifiées après AUTH_TIMEOUT_MS
  const authTimeout = setTimeout(() => {
    if (!authState) {
      ws.close(1008, 'AUTH timeout');
    }
  }, AUTH_TIMEOUT_MS);

  ws.on('message', (rawData, isBinary) => {
    if (!isBinary) {
      sendError(ws, 'Frames texte non supportées — utiliser frames binaires');
      return;
    }
    const frame = parseFrame(rawData);
    if (!frame) {
      sendError(ws, 'Frame malformée');
      return;
    }

    // Étape 1 : si pas authentifié, seul AUTH est autorisé
    if (!authState) {
      if (frame.type !== MSG.AUTH) {
        sendError(ws, 'Premier message doit être AUTH (0x01)');
        ws.close(1008, 'AUTH requis');
        return;
      }
      const result = verifyAuth(frame.payload);
      if (!result.ok) {
        eventCounters.authFailures++;
        logEvent('ERROR', 'AUTH', `Authentification échouée : ${result.reason}`, { reason: result.reason });
        sendError(ws, `AUTH échouée : ${result.reason}`);
        ws.close(1008, 'AUTH invalide');
        return;
      }
      eventCounters.authSuccesses++;
      logEvent('INFO', 'AUTH', `Authentification réussie : ${result.nodeId.slice(0, 8)}`, { nodeId: result.nodeId });

      // Fermer l'ancienne connexion si le nodeId est déjà enregistré
      const existingSession = sessions.get(result.nodeId);
      if (existingSession) {
        try { existingSession.ws.close(1008, 'Replaced by new connection'); } catch { /* déjà fermée */ }
      }

      clearTimeout(authTimeout);
      // Cache la cle publique en Base64 SPKI-DER une seule fois a l'AUTH (au lieu de
      // re-exporter a chaque GET_PEERS). Stockee aussi dans signalingRegistry pour
      // rester disponible meme apres fermeture WS (pendant la fenetre TTL 60s).
      let cachedPubKeyB64 = '';
      try {
        cachedPubKeyB64 = result.publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
      } catch (e) {
        console.warn(`[AUTH] export pubKey echoue pour ${result.nodeId.slice(0,8)} : ${e.message}`);
      }
      authState = { nodeId: result.nodeId, publicKey: result.publicKey, pubKeyB64: cachedPubKeyB64 };
      sessions.set(result.nodeId, { ws, publicKey: result.publicKey, pubKeyB64: cachedPubKeyB64 });
      safeSend(ws, buildFrame(MSG.AUTH_OK));
      console.log(`[AUTH] nodeId=${result.nodeId.slice(0, 8)} authentifié (${sessions.size} sessions)`);

      // Flush des blocs en attente pour ce nœud
      flushPendingBlocks(result.nodeId, ws);
      return;
    }

    // Étape 2 : messages post-auth
    const { nodeId } = authState;

    switch (frame.type) {
      case MSG.AUTH: {
        sendError(ws, 'Déjà authentifié');
        break;
      }
      case MSG.REGISTER_PEER: {
        const ok = handleRegisterPeer(nodeId, frame.payload);
        if (!ok) {
          sendError(ws, 'REGISTER_PEER rejeté');
          // Envoyer la liste des pairs actifs immédiatement : le nœud rejeté
          // découvre le vrai SP via processPeerList et sa FSM peut céder le rôle.
          handleGetPeers(ws);
        }
        break;
      }
      case MSG.JOIN: {
        const ok = handleJoin(nodeId, frame.payload);
        if (!ok) sendError(ws, 'JOIN payload invalide');
        break;
      }
      case MSG.GET_PEERS: {
        handleGetPeers(ws);
        break;
      }
      case MSG.UPLOAD: {
        handleUpload(nodeId, frame.payload, ws);
        break;
      }
      case MSG.REQUEST_BLOCK: {
        handleRequestBlock(nodeId, frame.payload, ws);
        break;
      }
      case MSG.SIGNAL: {
        handleSignal(nodeId, frame.payload);
        break;
      }
      case MSG.ELECTION_BROADCAST: {
        handleElectionBroadcast(nodeId, frame.payload);
        break;
      }
      case MSG.PING: {
        safeSend(ws, buildFrame(MSG.PONG));
        break;
      }
      default: {
        sendError(ws, `Type de message inconnu : 0x${frame.type.toString(16)}`);
      }
    }
  });

  ws.on('close', () => {
    clearTimeout(authTimeout);
    if (authState) {
      // Race-safe : si une nouvelle connexion a déjà remplacé cette session
      // (reconnexion, NAT rebinding), ne rien supprimer — sinon on évincerait
      // l'entrée de la connexion vivante.
      const currentSession = sessions.get(authState.nodeId);
      if (currentSession && currentSession.ws === ws) {
        sessions.delete(authState.nodeId);
        // Story stabilite — NE PAS supprimer signalingRegistry ici. Une micro-coupure
        // reseau (NAT timeout, 4G handoff, Render cold start) ferme la WS pendant 1-30s ;
        // si on purge l'entree, les autres nœuds qui font GET_PEERS pendant la fenetre
        // de reconnexion ne voient plus ce pair → "serveur voit 4, telephone voit 3".
        // Le TTL de 60s gere deja le cleanup pour les vrais departs. Si le pair revient
        // avant TTL, le prochain JOIN/REGISTER_PEER reset le timer.
        logEvent('INFO', 'WS', `Session fermée : ${authState.nodeId.slice(0, 8)} — entry conservee en signalingRegistry (TTL ${TTL_MS / 1000}s) (${sessions.size} sessions restantes)`, { nodeId: authState.nodeId, remaining: sessions.size });
      } else {
        console.log(`[WS] nodeId=${authState.nodeId.slice(0, 8)} ancienne ws fermée — session active conservée`);
      }
    }
  });

  ws.on('error', (err) => {
    console.error(`[WS] Erreur socket : ${err.message}`);
  });
});

// ─── Démarrage + SIGTERM gracieux (AC#6) ────────────────────────────────────

function startServer() {
  httpServer.listen(PORT, () => {
    console.log(`[SERVER] MobiCloud Relay HA démarré sur port ${PORT}`);
    console.log(`[SERVER] /health → http://localhost:${PORT}/health`);
    logEvent('INFO', 'SERVER', `Relay HA démarré sur port ${PORT}`, { port: PORT });
  });

  // Heartbeat global — ping toutes les ws toutes les 30s, terminate celles qui n'ont pas
  // répondu au précédent ping (zombies). Le 'close' handler nettoiera sessions/registry.
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      ws.missedPings = (ws.missedPings ?? 0) + 1;
      if (ws.missedPings > 2) {
        // 2 cycles consécutifs sans pong (2 × 60s = 120s) → socket zombie
        ws.terminate();
        continue;
      }
      try { ws.ping(); } catch { /* ws en cours de fermeture */ }
    }
  }, HEARTBEAT_INTERVAL_MS);
  heartbeat.unref(); // ne bloque pas l'exit du process

  wss.on('close', () => clearInterval(heartbeat));

  process.once('SIGTERM', () => {
    console.log('[SERVER] SIGTERM reçu — fermeture gracieuse...');
    clearInterval(heartbeat);
    for (const [, session] of sessions.entries()) {
      if (session.ws.readyState === WebSocket.OPEN) {
        session.ws.close(1001, 'Server shutting down');
      }
    }
    httpServer.close(() => {
      console.log('[SERVER] Serveur arrêté proprement.');
      process.exit(0);
    });
    // Filet de sécurité 5s — unref() pour ne pas bloquer un exit propre
    setTimeout(() => process.exit(0), 5000).unref();
  });
}

if (require.main === module) {
  startServer();
}

// ─── Exports pour les tests ──────────────────────────────────────────────────
module.exports = {
  buildFrame, parseFrame, sendError, safeSend, verifyAuth,
  handleRegisterPeer, handleJoin, handleGetPeers, handleUpload, handleRequestBlock, flushPendingBlocks,
  sessions, signalingRegistry, relayBuffer,
  MSG, TTL_MS, AUTH_WINDOW_MS, AUTH_TIMEOUT_MS, MAX_BLOCK_SIZE,
  MAX_RELAY_BUFFER_ENTRIES, MAX_SIGNALING_PEERS,
  httpServer, startServer
};
