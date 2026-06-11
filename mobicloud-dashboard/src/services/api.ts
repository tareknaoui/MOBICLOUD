// En dev, les requêtes passent par le proxy Vite (/relay → relay server) pour éviter CORS.
// En prod (build), VITE_RELAY_URL doit pointer directement vers le relay.
export const BASE = import.meta.env.DEV ? '/relay' : (import.meta.env.VITE_RELAY_URL ?? 'http://localhost:10000');

export interface RelayNode {
  id: string;
  isSuperPair: boolean;
  clusterId: string;
  reliabilityScore: number;
  freeBytes: number;
  totalBytes: number;
  ip: string;
  port: number;
  lastSeen: number;
  isConnected: boolean;
}

export interface TopologyData {
  nodes: RelayNode[];
  links: { source: string; target: string }[];
  activeSessions: number;
}

export interface HealthData {
  status: string;
  sessions: number;
  pendingBlocks: number;
  participants: number;
  registeredSuperPeers: number;
}

export interface EventsData {
  authFailures: number;
  authSuccesses: number;
  electionBroadcasts: number;
  forwardedBlocks: number;
  forwardedBlocksFailed: number;
  signalsSent: number;
  droppedSignals: number;
  joinEvents: number;
  departures: number;
  churnRate: number;
  uptimeMs: number;
}

export interface TransferEvent {
  from: string;
  to: string;
  kind: 'block' | 'signal' | 'election';
  blockId?: string;
  bytes: number;
}

export function subscribeToTransfers(onEvent: (e: TransferEvent) => void): () => void {
  // Use the proxied BASE URL so that the request is same-origin to avoid CORS and browser tracking protection blocks.
  const es = new EventSource(`${BASE}/dashboard/events`);
  es.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data) as TransferEvent & { type: string };
      if (data.type === 'block_transfer') onEvent(data);
    } catch { /* ignore malformed */ }
  };
  return () => es.close();
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<T>;
}

export interface FragmentEntry {
  nodeId: string;
  index: number;
  sizeBytes: number;
  nodeConnected: boolean;
  nodeExists: boolean;
}

export interface FragmentFile {
  fileId: string;
  fileName: string;
  mimeType: string;
  totalSize: number;
  k: number;
  n: number;
  uploadedAt: number;
  uploaderNodeId: string;
  fragments: FragmentEntry[];
  onlineFragments: number;
  recoverable: boolean;
}

export const fetchTopology = () => get<TopologyData>('/metrics/topology');
export const fetchHealth = () => get<HealthData>('/health');
export const fetchEvents = () => get<EventsData>('/metrics/events');
export const fetchFragments = () => get<{ files: FragmentFile[] }>('/metrics/fragments');

export async function resetAllNodes(secret: string): Promise<{ ok: boolean; disconnected: number }> {
  const res = await fetch(`${BASE}/admin/reset`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${secret}`, 'Content-Type': 'application/json' },
  });
  if (res.status === 401) throw new Error('Secret incorrect');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

// Chaos demo : simule la panne d'un seul nœud (déconnexion + retrait de l'annuaire).
export async function killNode(nodeId: string, secret: string): Promise<{ ok: boolean; killed: boolean; nodeId: string }> {
  const res = await fetch(`${BASE}/admin/kill?nodeId=${encodeURIComponent(nodeId)}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${secret}`, 'Content-Type': 'application/json' },
  });
  if (res.status === 401) throw new Error('Secret incorrect');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
