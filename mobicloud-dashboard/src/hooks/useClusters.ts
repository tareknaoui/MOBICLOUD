import { useState, useEffect, useRef } from 'react';
import { BASE } from '../services/api';

export interface ClusterMember {
  nodeId: string;
  isSuperPair: boolean;
  reliabilityScore: number;
  freeBytes: number;
  isConnected: boolean;
  lastSeen: number;
}

export interface ClusterInfo {
  clusterId: string;
  memberCount: number;
  connectedCount: number;
  superPeer: { nodeId: string; electedAt: number; reliabilityScore: number } | null;
  totalFreeBytes: number;
  avgReliabilityScore: number;
  members: ClusterMember[];
}

export interface ClustersData {
  clusters: ClusterInfo[];
  totalNodes: number;
  totalConnected: number;
  churnRate: number;
}

export function useClusters() {
  const [data, setData] = useState<ClustersData | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const res = await fetch(`${BASE}/metrics/clusters`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json() as ClustersData;
        if (!cancelled) setData(json);
      } catch { /* silencieux */ }
      finally {
        if (!cancelled) timer.current = setTimeout(poll, 3000);
      }
    }

    poll();
    return () => {
      cancelled = true;
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return data;
}
