import { useState } from 'react';
import type { ClusterInfo, ClustersData } from '../../hooks/useClusters';

interface Props {
  data: ClustersData | null;
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmtBytes(b: number) {
  if (b >= 1e9) return `${(b / 1e9).toFixed(1)} GB`;
  if (b >= 1e6) return `${(b / 1e6).toFixed(1)} MB`;
  if (b >= 1e3) return `${(b / 1e3).toFixed(1)} KB`;
  return `${b} B`;
}

function fmtBytesShort(b: number) {
  if (b >= 1e9) return `${(b / 1e9).toFixed(1)}G`;
  if (b >= 1e6) return `${(b / 1e6).toFixed(0)}M`;
  return `${Math.round(b / 1e3)}K`;
}

function reliabilityColor(s: number): string {
  if (s >= 0.7) return '#34c759';
  if (s >= 0.4) return '#ff9f0a';
  return '#ff3b30';
}

function storageBarColor(pct: number): string {
  if (pct >= 60) return '#34c759';
  if (pct >= 25) return '#ff9f0a';
  return '#ff3b30';
}

function fmtAgo(ms: number): string {
  if (!ms) return '—';
  const s = Math.floor((Date.now() - ms) / 1000);
  if (s < 5) return 'now';
  if (s < 60) return `${s}s ago`;
  return `${Math.floor(s / 60)}m ago`;
}

// ── Storage drill-down for one cluster ───────────────────────────────────────
function ClusterDrillDown({ cluster }: { cluster: ClusterInfo }) {
  const maxFreeBytes = Math.max(...cluster.members.map(m => m.freeBytes), 1);
  const totalFree = cluster.totalFreeBytes;

  // Sort: SP first, then by freeBytes desc
  const sorted = [...cluster.members].sort((a, b) => {
    if (a.isSuperPair !== b.isSuperPair) return a.isSuperPair ? -1 : 1;
    return b.freeBytes - a.freeBytes;
  });

  return (
    <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 5 }}>

      {/* Cluster-wide storage bar */}
      <div style={{ marginBottom: 4 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 9, color: 'var(--text-dim)', marginBottom: 3, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
          <span>Stockage libre total</span>
          <span style={{ color: 'var(--text-sec)', fontWeight: 700 }}>{fmtBytes(totalFree)}</span>
        </div>
        <div style={{ height: 5, borderRadius: 3, background: 'var(--border)', overflow: 'hidden' }}>
          <div style={{
            height: '100%',
            width: `${Math.min(100, (totalFree / (maxFreeBytes * cluster.members.length)) * 100)}%`,
            background: 'var(--accent-cyan)',
            borderRadius: 3,
            transition: 'width 0.4s ease',
          }} />
        </div>
      </div>

      {/* Per-node rows */}
      {sorted.map(m => {
        const barPct = maxFreeBytes > 0 ? (m.freeBytes / maxFreeBytes) * 100 : 0;
        const relPct = m.reliabilityScore * 100;
        return (
          <div key={m.nodeId} style={{
            background: 'var(--bg-base)',
            borderRadius: 6,
            padding: '5px 8px',
            border: `1px solid ${m.isSuperPair ? 'rgba(250,204,21,0.25)' : 'var(--border)'}`,
            opacity: m.isConnected ? 1 : 0.55,
          }}>
            {/* Row 1: identity + status */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 4 }}>
              <span style={{
                width: 7, height: 7, borderRadius: '50%', flexShrink: 0,
                background: m.isSuperPair ? '#facc15' : m.isConnected ? '#60a5fa' : '#6b7280',
                boxShadow: m.isSuperPair ? '0 0 5px rgba(250,204,21,0.6)' : 'none',
              }} />
              <span style={{ fontFamily: 'monospace', fontSize: 10, color: 'var(--text-primary)', fontWeight: m.isSuperPair ? 700 : 400 }}>
                {m.nodeId.slice(0, 10)}…
              </span>
              {m.isSuperPair && (
                <span style={{ fontSize: 8, color: '#facc15', fontWeight: 700, letterSpacing: '0.05em', marginLeft: 2 }}>SP</span>
              )}
              <span style={{ marginLeft: 'auto', fontSize: 9, color: m.isConnected ? 'var(--accent-green)' : '#6b7280' }}>
                {m.isConnected ? 'online' : `offline · ${fmtAgo(m.lastSeen)}`}
              </span>
            </div>

            {/* Row 2: storage bar */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <div style={{ flex: 1, height: 4, borderRadius: 2, background: 'var(--border)', overflow: 'hidden' }}>
                <div style={{
                  height: '100%',
                  width: `${barPct}%`,
                  background: storageBarColor(barPct),
                  borderRadius: 2,
                  transition: 'width 0.4s ease',
                }} />
              </div>
              <span style={{ fontSize: 9, color: 'var(--text-sec)', width: 42, textAlign: 'right', flexShrink: 0 }}>
                {fmtBytesShort(m.freeBytes)}
              </span>
            </div>

            {/* Row 3: reliability bar */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 3 }}>
              <div style={{ flex: 1, height: 3, borderRadius: 2, background: 'var(--border)', overflow: 'hidden' }}>
                <div style={{
                  height: '100%',
                  width: `${relPct}%`,
                  background: reliabilityColor(m.reliabilityScore),
                  borderRadius: 2,
                  transition: 'width 0.4s ease',
                }} />
              </div>
              <span style={{ fontSize: 9, color: reliabilityColor(m.reliabilityScore), width: 42, textAlign: 'right', flexShrink: 0, fontWeight: 600 }}>
                {relPct.toFixed(0)}% fiab.
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ── Cluster card (collapsed + expanded) ──────────────────────────────────────
function ClusterCard({ cluster, expanded, onToggle }: {
  cluster: ClusterInfo;
  expanded: boolean;
  onToggle: () => void;
}) {
  const healthOk = cluster.connectedCount === cluster.memberCount && cluster.superPeer;
  const healthColor = !cluster.superPeer
    ? 'var(--accent-red)'
    : cluster.connectedCount < cluster.memberCount
    ? 'var(--accent-orange)'
    : 'var(--accent-green)';

  return (
    <div style={{
      background: 'var(--bg-card2)',
      borderRadius: 8,
      border: `1px solid ${expanded ? 'var(--accent-blue)' : 'var(--border)'}`,
      overflow: 'hidden',
      transition: 'border-color 0.2s',
    }}>
      {/* Header — always visible, clickable */}
      <button
        onClick={onToggle}
        style={{
          width: '100%', background: 'none', border: 'none', cursor: 'pointer',
          padding: '7px 10px', display: 'flex', alignItems: 'center', gap: 6,
          textAlign: 'left',
        }}
      >
        <span style={{ width: 6, height: 6, borderRadius: '50%', background: healthColor, flexShrink: 0 }} />
        <span style={{ fontSize: 10, color: 'var(--accent-blue)', fontFamily: 'monospace', flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {cluster.clusterId === '__no_cluster__' ? '(sans cluster)' : cluster.clusterId.slice(0, 14) + '…'}
        </span>
        <span style={{ fontSize: 9, color: 'var(--text-dim)', flexShrink: 0 }}>
          {cluster.connectedCount}/{cluster.memberCount}
        </span>
        <span style={{ fontSize: 10, color: 'var(--text-dim)', flexShrink: 0, marginLeft: 2, transition: 'transform 0.2s', transform: expanded ? 'rotate(90deg)' : 'none' }}>
          ›
        </span>
      </button>

      {/* Collapsed summary */}
      {!expanded && (
        <div style={{ padding: '0 10px 7px', display: 'flex', flexDirection: 'column', gap: 3 }}>
          {cluster.superPeer ? (
            <div style={{ fontSize: 10, color: 'var(--accent-yellow)' }}>
              ⭐ SP: {cluster.superPeer.nodeId.slice(0, 8)}… · {(cluster.superPeer.reliabilityScore * 100).toFixed(0)}%
            </div>
          ) : (
            <div style={{ fontSize: 10, color: 'var(--accent-red)' }}>⚠ Pas de Super-Peer</div>
          )}
          <div style={{ display: 'flex', gap: 10, fontSize: 10, color: 'var(--text-sec)' }}>
            <span>Libre: <strong style={{ color: 'var(--text-primary)' }}>{fmtBytes(cluster.totalFreeBytes)}</strong></span>
            <span>Fiab. moy: <strong style={{ color: reliabilityColor(cluster.avgReliabilityScore) }}>{(cluster.avgReliabilityScore * 100).toFixed(0)}%</strong></span>
          </div>
        </div>
      )}

      {/* Expanded drill-down */}
      {expanded && (
        <div style={{ padding: '0 10px 10px' }}>
          <ClusterDrillDown cluster={cluster} />
        </div>
      )}
    </div>
  );
}

// ── Main panel ────────────────────────────────────────────────────────────────
export default function ClusterPanel({ data }: Props) {
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const churn = data?.churnRate ?? 0;
  const churnColor = churn >= 30 ? 'var(--accent-red)' : churn >= 15 ? 'var(--accent-orange)' : 'var(--accent-green)';

  function toggle(id: string) {
    setExpandedId(prev => (prev === id ? null : id));
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', background: 'var(--bg-card)', borderRadius: 10, border: '1px solid var(--border)', overflow: 'hidden' }}>
      {/* Panel header */}
      <div style={{ padding: '6px 12px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <span style={{ color: 'var(--accent-blue)', fontSize: 11, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase' }}>Clusters</span>
        <span style={{ fontSize: 9, color: 'var(--text-dim)', fontStyle: 'italic' }}>cliquer pour détails stockage</span>
        <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ fontSize: 10, color: 'var(--text-sec)' }}>Churn :</span>
          <span style={{ fontSize: 11, fontWeight: 700, color: churnColor }}>{churn}%</span>
          {churn >= 30 && <span style={{ fontSize: 10, color: 'var(--accent-red)' }}>⚠</span>}
        </span>
      </div>

      {/* Cluster list */}
      <div style={{ overflowY: 'auto', padding: '6px 8px', display: 'flex', flexDirection: 'column', gap: 6 }}>
        {(!data || data.clusters.length === 0) && (
          <div style={{ color: 'var(--text-muted)', fontSize: 11, fontStyle: 'italic', padding: 8 }}>Aucun cluster actif</div>
        )}
        {data?.clusters.map(c => (
          <ClusterCard
            key={c.clusterId}
            cluster={c}
            expanded={expandedId === c.clusterId}
            onToggle={() => toggle(c.clusterId)}
          />
        ))}
      </div>
    </div>
  );
}
