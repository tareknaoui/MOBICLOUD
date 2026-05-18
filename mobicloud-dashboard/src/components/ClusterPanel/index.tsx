import type { ClustersData } from '../../hooks/useClusters';

interface Props {
  data: ClustersData | null;
}

function fmtBytes(b: number) {
  if (b >= 1e9) return `${(b / 1e9).toFixed(1)}G`;
  if (b >= 1e6) return `${(b / 1e6).toFixed(0)}M`;
  return `${Math.round(b / 1e3)}K`;
}

export default function ClusterPanel({ data }: Props) {
  const churn = data?.churnRate ?? 0;
  const churnColor = churn >= 30 ? 'var(--accent-red)' : churn >= 15 ? 'var(--accent-orange)' : 'var(--accent-green)';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--bg-card)', borderRadius: 10, border: '1px solid var(--border)', overflow: 'hidden' }}>
      <div style={{ padding: '6px 12px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <span style={{ color: 'var(--accent-blue)', fontSize: 11, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase' }}>Clusters</span>
        <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ fontSize: 10, color: 'var(--text-sec)' }}>Churn 5min :</span>
          <span style={{ fontSize: 11, fontWeight: 700, color: churnColor }}>{churn}%</span>
          {churn >= 30 && <span style={{ fontSize: 10, color: 'var(--accent-red)' }}>⚠ INSTABLE</span>}
        </span>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '6px 8px', display: 'flex', flexDirection: 'column', gap: 6 }}>
        {(!data || data.clusters.length === 0) && (
          <div style={{ color: 'var(--text-muted)', fontSize: 11, fontStyle: 'italic', padding: 8 }}>Aucun cluster actif</div>
        )}
        {data?.clusters.map(c => (
          <div key={c.clusterId} style={{ background: 'var(--bg-card2)', borderRadius: 8, padding: '6px 10px', border: '1px solid var(--border)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <span style={{ fontSize: 10, color: 'var(--accent-blue)', fontFamily: 'monospace' }}>
                {c.clusterId === '__no_cluster__' ? '(sans cluster)' : c.clusterId.slice(0, 12) + '…'}
              </span>
              <span style={{ marginLeft: 'auto', fontSize: 10, color: 'var(--text-dim)' }}>
                {c.connectedCount}/{c.memberCount} connectés
              </span>
            </div>
            {c.superPeer ? (
              <div style={{ fontSize: 10, color: 'var(--accent-yellow)', marginBottom: 2 }}>
                ⭐ SP: {c.superPeer.nodeId.slice(0, 8)} · score {(c.superPeer.reliabilityScore * 100).toFixed(0)}%
              </div>
            ) : (
              <div style={{ fontSize: 10, color: 'var(--accent-red)', marginBottom: 2 }}>⚠ Pas de Super-Peer</div>
            )}
            <div style={{ display: 'flex', gap: 12, fontSize: 10, color: 'var(--text-sec)' }}>
              <span>Libre: {fmtBytes(c.totalFreeBytes)}</span>
              <span>Fiab. moy: {(c.avgReliabilityScore * 100).toFixed(0)}%</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
