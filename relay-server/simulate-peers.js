'use strict';
/**
 * simulate-peers.js — Simulateur de N pairs MobiCloud connectés au relay
 *
 * Usage :
 *   node simulate-peers.js [--peers 10] [--clusters 3] [--url ws://localhost:10000] [--churn]
 */

const crypto = require('crypto');
const WebSocket = require('ws');

// ─── Config CLI ───────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
function getArg(name, def) {
  const i = args.indexOf(name);
  return i !== -1 && args[i + 1] ? args[i + 1] : def;
}
const RELAY_URL     = getArg('--url', 'ws://localhost:10000');
const PEER_COUNT    = parseInt(getArg('--peers', '10'), 10);
const CLUSTER_COUNT = parseInt(getArg('--clusters', '3'), 10);
const CHURN         = args.includes('--churn');

// UUID v4 stables par index de cluster (le serveur valide UUID v4)
const clusterUUIDs = Array.from({ length: CLUSTER_COUNT }, () => crypto.randomUUID());

// ─── Protocole (miroir de server.js) ─────────────────────────────────────────
const MSG = {
  AUTH: 0x01, AUTH_OK: 0x02,
  REGISTER_PEER: 0x03, GET_PEERS: 0x04, PEERS: 0x05,
  JOIN: 0x0B,
  ELECTION_BROADCAST: 0x10, ELECTION_RECEIVED: 0x11,
  ERROR: 0xFF
};

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

// ─── Helpers ──────────────────────────────────────────────────────────────────
function rand(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min; }
function randFloat(min, max) { return Math.random() * (max - min) + min; }
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function makeClusterId(i) {
  return clusterUUIDs[i % clusterUUIDs.length];
}

// ─── Classe Peer ──────────────────────────────────────────────────────────────
class SimulatedPeer {
  constructor(index, clusterIndex) {
    this.index       = index;
    this.nodeId      = crypto.randomBytes(8).toString('hex');  // 16 chars hex
    this.clusterId   = makeClusterId(clusterIndex);
    this.keyPair     = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    this.ip          = `10.${rand(0,255)}.${rand(0,255)}.${rand(1,254)}`;
    this.port        = rand(30000, 60000);
    this.freeBytes   = rand(500, 8000) * 1_000_000;   // 500 MB – 8 GB
    this.reliability = parseFloat(randFloat(0.5, 1.0).toFixed(2));
    this.isSuperPeer = false;
    this.ws          = null;
    this.alive       = false;
  }

  log(msg) {
    const role = this.isSuperPeer ? '[SP]' : '[M ]';
    console.log(`${role} [${this.nodeId.slice(0,8)}] ${msg}`);
  }

  async connect() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(RELAY_URL);
      this.ws.on('open', () => resolve());
      this.ws.on('error', (e) => reject(e));
      this.ws.on('close', () => {
        this.alive = false;
        this.log('déconnecté du relay');
      });
    });
  }

  async authenticate() {
    const timestamp = Date.now();
    const signedData = Buffer.from(
      `MobiCloud-HA-AUTH:${this.nodeId}:${timestamp}`, 'utf8'
    );
    const signature = crypto.sign('SHA256', signedData, this.keyPair.privateKey);
    const pubKeySpkiDer = this.keyPair.publicKey
      .export({ type: 'spki', format: 'der' })
      .toString('base64');

    const payload = Buffer.from(JSON.stringify({
      nodeId: this.nodeId,
      pubKeySpkiDer,
      timestamp,
      signature: signature.toString('base64')
    }), 'utf8');

    this.ws.send(buildFrame(MSG.AUTH, payload));

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('AUTH timeout')), 8000);
      this.ws.once('message', (data) => {
        clearTimeout(timeout);
        const frame = parseFrame(data);
        if (frame?.type === MSG.AUTH_OK) resolve();
        else reject(new Error(`AUTH_OK non reçu, type=${frame?.type}`));
      });
    });
  }

  sendJoin() {
    const payload = Buffer.from(JSON.stringify({
      ip: this.ip,
      port: this.port,
      freeBytes: this.freeBytes,
      clusterId: this.clusterId,
      currentMemberCount: rand(1, 5)
    }), 'utf8');
    this.ws.send(buildFrame(MSG.JOIN, payload));
    this.log(`JOIN → cluster ${this.clusterId}`);
  }

  registerAsSuperPeer() {
    this.isSuperPeer = true;
    const payload = Buffer.from(JSON.stringify({
      ip: this.ip,
      port: this.port,
      reliabilityScore: this.reliability,
      electedAt: Date.now(),
      clusterId: this.clusterId,
      freeBytes: this.freeBytes,
      currentMemberCount: rand(1, 5)
    }), 'utf8');
    this.ws.send(buildFrame(MSG.REGISTER_PEER, payload));
    this.log(`REGISTER_PEER (Super-Peer élu) → cluster ${this.clusterId}`);
  }

  sendElectionBroadcast() {
    if (!this.alive) return;
    const payload = Buffer.from(JSON.stringify({
      type: 'BULLY_VICTORY',
      newSuperPeer: this.nodeId,
      clusterId: this.clusterId,
      ts: Date.now()
    }), 'utf8');
    this.ws.send(buildFrame(MSG.ELECTION_BROADCAST, payload));
    this.log(`ELECTION_BROADCAST (victoire Bully)`);
  }

  disconnect() {
    if (this.ws && this.alive) {
      this.alive = false;
      this.ws.close(1000, 'churn simulé');
    }
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
      this.log(`échec connexion: ${e.message}`);
      return false;
    }
  }
}

// ─── Orchestrateur ────────────────────────────────────────────────────────────
async function main() {
  console.log(`\n╔══════════════════════════════════════════════════════════╗`);
  console.log(`║  MobiCloud Peer Simulator                                ║`);
  console.log(`║  ${PEER_COUNT} peers · ${CLUSTER_COUNT} clusters · relay: ${RELAY_URL.padEnd(28)}║`);
  console.log(`║  Churn: ${CHURN ? 'activé ' : 'désactivé'}                                        ║`);
  console.log(`╚══════════════════════════════════════════════════════════╝\n`);

  // Créer les peers répartis sur les clusters
  const peers = Array.from({ length: PEER_COUNT }, (_, i) =>
    new SimulatedPeer(i, i % CLUSTER_COUNT)
  );

  // Connexion progressive (évite un flood simultané)
  console.log(`[SIM] Connexion des ${PEER_COUNT} pairs au relay...`);
  const connected = [];
  for (const p of peers) {
    const ok = await p.start();
    if (ok) connected.push(p);
    await sleep(rand(100, 300));
  }
  console.log(`[SIM] ${connected.length}/${PEER_COUNT} pairs connectés\n`);

  if (connected.length === 0) {
    console.error('[SIM] Aucun pair connecté — le relay est-il démarré sur ' + RELAY_URL + ' ?');
    process.exit(1);
  }

  // Élire 1 Super-Peer par cluster
  await sleep(500);
  const clusterMap = new Map();
  for (const p of connected) {
    if (!clusterMap.has(p.clusterId)) clusterMap.set(p.clusterId, []);
    clusterMap.get(p.clusterId).push(p);
  }
  for (const [clusterId, members] of clusterMap) {
    // Choisir le pair avec la meilleure fiabilité
    const leader = members.reduce((a, b) => a.reliability > b.reliability ? a : b);
    leader.registerAsSuperPeer();
    leader.sendElectionBroadcast();
    console.log(`[SIM] Cluster ${clusterId} → SP élu: ${leader.nodeId.slice(0,8)} (score ${leader.reliability})`);
    await sleep(200);
  }

  console.log('\n[SIM] Simulation en cours — Ctrl+C pour arrêter\n');

  // ── Boucle churn (si activé) ──
  let churnLoop;
  if (CHURN) {
    const disconnected = [];
    churnLoop = setInterval(async () => {
      const alive = connected.filter(p => p.alive);
      if (alive.length === 0) return;

      // Déconnecter un pair aléatoire (pas un Super-Peer)
      const members = alive.filter(p => !p.isSuperPeer);
      if (members.length > 1) {
        const victim = members[rand(0, members.length - 1)];
        victim.disconnect();
        disconnected.push(victim);
        console.log(`[SIM] CHURN: ${victim.nodeId.slice(0,8)} déconnecté (${alive.length - 1} restants)`);
      }

      // Reconnecter un pair précédemment déconnecté
      if (disconnected.length > 0 && Math.random() > 0.4) {
        const comeback = disconnected.splice(rand(0, disconnected.length - 1), 1)[0];
        // Regénérer le nodeId pour simuler une vraie reconnexion
        comeback.nodeId = crypto.randomBytes(8).toString('hex');
        comeback.keyPair = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
        const ok = await comeback.start();
        if (ok) console.log(`[SIM] CHURN: ${comeback.nodeId.slice(0,8)} reconnecté`);
      }
    }, rand(5000, 10000));
  }

  // ── Boucle réélection Bully (toutes les 30s) ──
  const electionLoop = setInterval(() => {
    const alive = connected.filter(p => p.alive);
    for (const [, members] of clusterMap) {
      const aliveMembers = members.filter(p => p.alive);
      if (aliveMembers.length === 0) continue;
      // Nouveau leader = pair avec meilleure fiabilité parmi les vivants
      const newLeader = aliveMembers.reduce((a, b) => a.reliability > b.reliability ? a : b);
      if (!newLeader.isSuperPeer) {
        // L'ancien SP perd son statut
        aliveMembers.forEach(p => { p.isSuperPeer = false; });
        newLeader.registerAsSuperPeer();
        newLeader.sendElectionBroadcast();
      }
    }
    console.log(`[SIM] Bully: vérification leadership (${alive.length} pairs actifs)`);
  }, 30000);

  // Arrêt propre
  process.on('SIGINT', () => {
    console.log('\n[SIM] Arrêt — déconnexion de tous les pairs...');
    clearInterval(electionLoop);
    if (churnLoop) clearInterval(churnLoop);
    connected.forEach(p => { try { p.ws?.close(); } catch (_) {} });
    setTimeout(() => process.exit(0), 1000);
  });
}

main().catch(e => { console.error('[SIM] Erreur fatale:', e); process.exit(1); });
