'use strict';
/**
 * simulate-screenshot.js — 20 pairs pour screenshots dashboard
 * Cluster A : 12 nœuds   Cluster B : 8 nœuds
 */

const crypto = require('crypto');
const http   = require('http');
const WebSocket = require('ws');

const RELAY_URL = 'ws://localhost:10000';

const CLUSTER_A = crypto.randomUUID();
const CLUSTER_B = crypto.randomUUID();

// 12 dans A, 8 dans B
const DISTRIBUTION = [
  ...Array(12).fill(0),
  ...Array(8).fill(1),
];
const CLUSTER_IDS = [CLUSTER_A, CLUSTER_B];

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
  return { type, payload: buf.slice(5) };
}
const MSG = { AUTH: 0x01, AUTH_OK: 0x02, REGISTER_PEER: 0x03, JOIN: 0x0B, ELECTION_BROADCAST: 0x10, ERROR: 0xFF };
function rand(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min; }
function randFloat(min, max) { return Math.random() * (max - min) + min; }
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

class Peer {
  constructor(clusterIdx) {
    this.nodeId    = crypto.randomBytes(8).toString('hex');
    this.clusterId = CLUSTER_IDS[clusterIdx];
    this.clusterLabel = clusterIdx === 0 ? 'A' : 'B';
    this.keyPair   = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    this.ip        = `10.${rand(0,255)}.${rand(0,255)}.${rand(1,254)}`;
    this.port      = rand(30000, 60000);
    // Stockage varié pour un beau dashboard
    this.totalBytes = rand(2, 32) * 1_073_741_824;  // 2–32 GB
    this.freeBytes  = Math.floor(this.totalBytes * randFloat(0.2, 0.9));
    this.reliability = parseFloat(randFloat(0.55, 0.99).toFixed(2));
    this.isSuperPeer = false;
    this.ws   = null;
    this.alive = false;
  }

  log(msg) {
    const role = this.isSuperPeer ? '[SP]' : '[M ]';
    console.log(`${role} [${this.clusterLabel}] [${this.nodeId.slice(0,8)}] ${msg}`);
  }

  async connect() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(RELAY_URL);
      this.ws.on('open', () => resolve());
      this.ws.on('error', reject);
      this.ws.on('close', () => { this.alive = false; });
    });
  }

  async authenticate() {
    const timestamp = Date.now();
    const signedData = Buffer.from(`MobiCloud-HA-AUTH:${this.nodeId}:${timestamp}`, 'utf8');
    const signature = crypto.sign('SHA256', signedData, this.keyPair.privateKey);
    const pubKeySpkiDer = this.keyPair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
    const payload = Buffer.from(JSON.stringify({ nodeId: this.nodeId, pubKeySpkiDer, timestamp, signature: signature.toString('base64') }), 'utf8');
    this.ws.send(buildFrame(MSG.AUTH, payload));
    return new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error('AUTH timeout')), 8000);
      this.ws.once('message', (data) => {
        clearTimeout(t);
        const f = parseFrame(data);
        if (f?.type === MSG.AUTH_OK) resolve();
        else reject(new Error('AUTH_OK non reçu'));
      });
    });
  }

  sendJoin() {
    const payload = Buffer.from(JSON.stringify({
      ip: this.ip, port: this.port,
      freeBytes: this.freeBytes,
      totalBytes: this.totalBytes,
      clusterId: this.clusterId,
      currentMemberCount: 1
    }), 'utf8');
    this.ws.send(buildFrame(MSG.JOIN, payload));
    this.log(`JOIN → cluster ${this.clusterId.slice(0,8)}`);
  }

  registerAsSuperPeer() {
    this.isSuperPeer = true;
    const payload = Buffer.from(JSON.stringify({
      ip: this.ip, port: this.port,
      reliabilityScore: this.reliability,
      electedAt: Date.now(),
      clusterId: this.clusterId,
      freeBytes: this.freeBytes,
      totalBytes: this.totalBytes,
      currentMemberCount: 1
    }), 'utf8');
    this.ws.send(buildFrame(MSG.REGISTER_PEER, payload));
    this.log(`REGISTER_PEER totalBytes=${(this.totalBytes/1e9).toFixed(1)}GB freeBytes=${(this.freeBytes/1e9).toFixed(1)}GB`);
  }

  heartbeat() {
    if (!this.alive || !this.ws || this.ws.readyState !== 1) return;
    if (this.isSuperPeer) this.registerAsSuperPeer();
    else this.sendJoin();
  }

  sendElectionBroadcast() {
    const payload = Buffer.from(JSON.stringify({
      type: 'BULLY_VICTORY', newSuperPeer: this.nodeId,
      clusterId: this.clusterId, ts: Date.now()
    }), 'utf8');
    this.ws.send(buildFrame(MSG.ELECTION_BROADCAST, payload));
  }

  async start() {
    try {
      await this.connect();
      await this.authenticate();
      this.alive = true;
      this.log('authentifié');
      this.sendJoin();
      return true;
    } catch (e) {
      this.log(`échec: ${e.message}`);
      return false;
    }
  }
}

// ─── Annonce fragments ────────────────────────────────────────────────────────
function postJson(path, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const opts = { hostname: 'localhost', port: 10000, path, method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } };
    const req = http.request(opts, r => { let s = ''; r.on('data', d => s += d); r.on('end', () => resolve(JSON.parse(s))); });
    req.on('error', reject);
    req.write(data); req.end();
  });
}

function pickRandom(arr, n) {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}

// Distribue n fragments : priorité aux nœuds du cluster home, débordement sur l'autre si besoin
function pickClusterFirst(peersByCluster, homeClusterId, n) {
  const home  = pickRandom(peersByCluster.get(homeClusterId) ?? [], n);
  if (home.length >= n) return home;
  // débordement : compléter avec l'autre cluster
  const others = [...peersByCluster.entries()]
    .filter(([cid]) => cid !== homeClusterId)
    .flatMap(([, peers]) => peers);
  const extra = pickRandom(others, n - home.length);
  return [...home, ...extra];
}

async function announceFiles(connectedPeers) {
  // Regrouper les nodeIds par cluster
  const peersByCluster = new Map();
  for (const p of connectedPeers) {
    if (!peersByCluster.has(p.clusterId)) peersByCluster.set(p.clusterId, []);
    peersByCluster.get(p.clusterId).push(p.nodeId);
  }
  const clusterIds = [...peersByCluster.keys()];

  // chaque fichier est rattaché à un cluster (alternance A/B pour la démo)
  const FILES = [
    { fileName: 'photo_vacances.jpg',  mimeType: 'image/jpeg',       totalSize: 8_400_000,   k: 3, n: 5, home: clusterIds[0] },
    { fileName: 'video_famille.mp4',   mimeType: 'video/mp4',        totalSize: 524_288_000, k: 4, n: 6, home: clusterIds[0] },
    { fileName: 'rapport_stage.pdf',   mimeType: 'application/pdf',  totalSize: 2_150_000,   k: 2, n: 4, home: clusterIds[1] ?? clusterIds[0] },
    { fileName: 'musique_playlist.mp3',mimeType: 'audio/mpeg',       totalSize: 15_700_000,  k: 3, n: 4, home: clusterIds[1] ?? clusterIds[0] },
    { fileName: 'backup_contacts.zip', mimeType: 'application/zip',  totalSize: 1_200_000,   k: 5, n: 8, home: clusterIds[1] ?? clusterIds[0] },
  ];

  for (const file of FILES) {
    const fileId   = crypto.randomBytes(8).toString('hex');
    const chosen   = pickClusterFirst(peersByCluster, file.home, file.n);
    const fragSize = Math.floor(file.totalSize / file.k);
    const fragments = chosen.map((nodeId, i) => ({ nodeId, index: i, sizeBytes: fragSize }));
    const homeLabel = file.home.slice(0, 8);
    try {
      await postJson('/fragments/announce', { fileId, ...file, fragments });
      console.log(`[FRAG] ${file.fileName} → ${file.n} frags (k=${file.k}) cluster ${homeLabel}…`);
    } catch (e) { console.error(`[FRAG] Erreur annonce ${file.fileName}: ${e.message}`); }
    await sleep(100);
  }
}

async function main() {
  console.log('\n╔══════════════════════════════════════════════════════════╗');
  console.log('║  MobiCloud Screenshot Simulator                          ║');
  console.log('║  Cluster A: 12 nœuds  ·  Cluster B: 8 nœuds             ║');
  console.log('╚══════════════════════════════════════════════════════════╝\n');

  const peers = DISTRIBUTION.map(ci => new Peer(ci));

  console.log('[SIM] Connexion des 20 pairs...');
  const connected = [];
  for (const p of peers) {
    const ok = await p.start();
    if (ok) connected.push(p);
    await sleep(rand(80, 180));
  }
  console.log(`[SIM] ${connected.length}/20 pairs connectés\n`);

  if (connected.length === 0) {
    console.error('[SIM] Aucun pair — relay démarré ?');
    process.exit(1);
  }

  await sleep(500);

  // Élire 1 SP par cluster (meilleure fiabilité)
  const clusterMap = new Map();
  for (const p of connected) {
    if (!clusterMap.has(p.clusterId)) clusterMap.set(p.clusterId, []);
    clusterMap.get(p.clusterId).push(p);
  }
  for (const [, members] of clusterMap) {
    const leader = members.reduce((a, b) => a.reliability > b.reliability ? a : b);
    leader.registerAsSuperPeer();
    await sleep(150);
    leader.sendElectionBroadcast();
    console.log(`[SIM] Cluster ${leader.clusterLabel} → SP: ${leader.nodeId.slice(0,8)} (fiabilité ${leader.reliability})`);
    await sleep(200);
  }

  // Heartbeat toutes les 90s pour reset le TTL (180s)
  setInterval(() => {
    connected.forEach(p => p.heartbeat());
  }, 90_000);

  // Annoncer les fichiers simulés
  await sleep(300);
  await announceFiles(connected);

  console.log('\n✅  Simulation prête — ouvre le dashboard React maintenant');
  console.log('     http://localhost:5173  (ou le port de ton Vite)\n');
  console.log('[SIM] Ctrl+C pour arrêter\n');

  // Keepalive — pas de churn pour screenshots propres
  const electionLoop = setInterval(() => {
    for (const [, members] of clusterMap) {
      const alive = members.filter(p => p.alive);
      if (alive.length === 0) continue;
      const leader = alive.reduce((a, b) => a.reliability > b.reliability ? a : b);
      if (!leader.isSuperPeer) {
        alive.forEach(p => { p.isSuperPeer = false; });
        leader.registerAsSuperPeer();
        leader.sendElectionBroadcast();
      }
    }
  }, 30000);

  process.on('SIGINT', () => {
    clearInterval(electionLoop);
    connected.forEach(p => { try { p.ws?.close(); } catch (_) {} });
    console.log('[SIM] Arrêté.');
    setTimeout(() => process.exit(0), 800);
  });
}

main().catch(e => { console.error(e); process.exit(1); });
