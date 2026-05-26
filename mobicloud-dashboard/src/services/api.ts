// En dev, les requêtes passent par le proxy Vite (/relay → relay server) pour éviter CORS.
// En prod (build), VITE_RELAY_URL doit pointer directement vers le relay.
export const BASE = import.meta.env.DEV ? '/relay' : (import.meta.env.VITE_RELAY_URL ?? 'http://localhost:10000');

export interface RelayNode {
  id: string;
  isSuperPair: boolean;
  clusterId: string;
  reliabilityScore: number;
  freeBytes: number;
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

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<T>;
}

export const fetchTopology = () => get<TopologyData>('/metrics/topology');
export const fetchHealth = () => get<HealthData>('/health');
export const fetchEvents = () => get<EventsData>('/metrics/events');

export async function resetAllNodes(secret: string): Promise<{ ok: boolean; disconnected: number }> {
  const res = await fetch(`${BASE}/admin/reset`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${secret}`, 'Content-Type': 'application/json' },
  });
  if (res.status === 401) throw new Error('Secret incorrect');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
