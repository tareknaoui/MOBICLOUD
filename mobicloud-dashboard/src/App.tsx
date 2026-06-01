import { useState, useEffect, useRef } from 'react';
import GraphView2D from './components/GraphView2D';
import NetworkPanel from './components/NetworkPanel';
import RealtimeLog from './components/RealtimeLog';
import ClusterPanel from './components/ClusterPanel';
import FragmentMap from './components/FragmentMap';
import { useTopology } from './hooks/useTopology';
import { useHealth } from './hooks/useHealth';
import { useLogs } from './hooks/useLogs';
import { useClusters } from './hooks/useClusters';
import { useTheme } from './hooks/useTheme';
import type { HealthData, EventsData, TransferEvent, FragmentFile } from './services/api';
import { resetAllNodes, subscribeToTransfers, fetchFragments } from './services/api';

function fmtUptime(ms: number) {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const h = Math.floor(m / 60);
  if (h > 0) return `${h}h ${m % 60}m`;
  if (m > 0) return `${m}m ${s % 60}s`;
  return `${s}s`;
}

function fmtPct(n: number, d: number) {
  if (d === 0) return '—';
  return `${((n / d) * 100).toFixed(1)}%`;
}

function ThemeToggle({ theme, onToggle }: { theme: string; onToggle: () => void }) {
  return (
    <button onClick={onToggle} style={{
      background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 6,
      padding: '3px 10px', cursor: 'pointer', fontSize: 12, color: 'var(--text-sec)',
      display: 'flex', alignItems: 'center', gap: 5,
    }}>
      {theme === 'dark' ? '☀ Light' : '☾ Dark'}
    </button>
  );
}

function KpiCard({ label, value, warn, accent }: { label: string; value: string | number; warn?: boolean; accent?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <span style={{ fontSize: 10, color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</span>
      <span style={{ fontSize: 17, fontWeight: 700, color: warn ? 'var(--accent-red)' : accent ?? 'var(--text-primary)', lineHeight: 1 }}>
        {value ?? '—'}
      </span>
    </div>
  );
}

function KpiSection({ title, accent, children }: { title: string; accent: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-card2)', borderRadius: 8, padding: '10px 14px', border: '1px solid var(--border)' }}>
      <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: accent, marginBottom: 10 }}>{title}</div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px 20px' }}>
        {children}
      </div>
    </div>
  );
}

function buildKpis(_health: HealthData | null, events: EventsData | null) {
  return (events?.authSuccesses ?? 0) + (events?.authFailures ?? 0);
}

export default function App() {
  const { data: topology, error: topoError } = useTopology();
  const { health, events, error: healthError, history } = useHealth();
  const logs = useLogs();
  const clusters = useClusters();
  const { theme, toggle } = useTheme();
  const [resetState, setResetState] = useState<'idle' | 'loading' | 'ok' | 'err'>('idle');
  const [resetMsg, setResetMsg] = useState('');
  const [latestTransfer, setLatestTransfer] = useState<TransferEvent | null>(null);
  const [fragmentFiles, setFragmentFiles] = useState<FragmentFile[]>([]);
  const [hlFileId, setHlFileId]   = useState<string | null>(null);
  const [navIdx, setNavIdx]       = useState(0);
  const graphFocusRef             = useRef<(nodeId: string) => void>(() => {});

  useEffect(() => subscribeToTransfers(setLatestTransfer), []);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const d = await fetchFragments();
        if (!cancelled) setFragmentFiles(d.files ?? []);
      } catch { /* relay offline */ }
    }
    load();
    const id = setInterval(load, 5000);
    return () => { cancelled = true; clearInterval(id); };
  }, []);

  const relayOffline = !!(topoError || healthError);

  async function handleReset() {
    const secret = window.prompt('Admin secret (ADMIN_SECRET):');
    if (!secret) return;
    if (!window.confirm(`Disconnect all nodes (${health?.sessions ?? '?'} sessions)?`)) return;
    setResetState('loading');
    try {
      const r = await resetAllNodes(secret);
      setResetState('ok');
      setResetMsg(`${r.disconnected} node(s) disconnected`);
    } catch (e: unknown) {
      setResetState('err');
      setResetMsg(e instanceof Error ? e.message : 'Unknown error');
    } finally {
      setTimeout(() => setResetState('idle'), 4000);
    }
  }

  // Reset navigation quand le fichier change
  useEffect(() => { setNavIdx(0); }, [hlFileId]);

  // Zoomer sur le nœud courant quand l'index change
  useEffect(() => {
    if (!hlFileId) return;
    const file = fragmentFiles.find(f => f.fileId === hlFileId);
    const fr = file?.fragments[navIdx];
    if (fr) graphFocusRef.current(fr.nodeId);
  }, [navIdx, hlFileId]); // eslint-disable-line react-hooks/exhaustive-deps

  const nodeCount = topology?.nodes.length ?? 0;
  const nodeStatuses = new Map<string, boolean>(
    (topology?.nodes ?? []).map(n => [n.id, n.isConnected])
  );
  const superPeerCount = topology?.nodes.filter(n => n.isSuperPair).length ?? 0;
  const totalAuth = buildKpis(health, events);
  const churn = clusters?.churnRate ?? 0;

  return (
    <div style={{ width: '100vw', height: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg-base)', overflow: 'hidden' }}>

      {/* ── Header ── */}
      <header style={{
        flexShrink: 0, background: 'var(--bg-surface)', borderBottom: '1px solid var(--border)',
        padding: '0 16px', display: 'flex', alignItems: 'center', gap: 12, height: 44
      }}>
        <span style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary)', letterSpacing: '-0.02em' }}>MobiCloud</span>
        <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>Admin Dashboard</span>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12, fontSize: 11 }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: relayOffline ? 'var(--accent-red)' : 'var(--accent-green)', display: 'inline-block' }} />
            <span style={{ color: relayOffline ? 'var(--accent-red)' : 'var(--accent-green)', fontWeight: 600 }}>
              {relayOffline ? 'Relay offline' : 'Relay connected'}
            </span>
          </span>
          {!relayOffline && health && <>
            <span style={{ color: 'var(--border)' }}>|</span>
            <span style={{ color: 'var(--text-sec)' }}>{health.sessions} sessions · {health.participants} nodes</span>
            <span style={{ color: 'var(--border)' }}>|</span>
            <span style={{ color: churn >= 30 ? 'var(--accent-red)' : churn >= 15 ? 'var(--accent-orange)' : 'var(--text-sec)', fontWeight: churn >= 30 ? 700 : 400 }}>
              churn {churn}%{churn >= 30 ? ' ⚠' : ''}
            </span>
          </>}
          <button
            onClick={handleReset}
            disabled={resetState === 'loading'}
            title="Disconnect all nodes and purge relay state"
            style={{
              background: resetState === 'ok' ? 'var(--accent-green)' : resetState === 'err' ? 'var(--accent-red)' : 'var(--accent-red)',
              color: '#fff', border: 'none', borderRadius: 6,
              padding: '3px 10px', cursor: resetState === 'loading' ? 'wait' : 'pointer',
              fontSize: 11, fontWeight: 600, opacity: resetState === 'loading' ? 0.6 : 1,
              display: 'flex', alignItems: 'center', gap: 5,
            }}
          >
            {resetState === 'loading' ? '⏳ Reset…'
              : resetState === 'ok' ? `✓ ${resetMsg}`
              : resetState === 'err' ? `✗ ${resetMsg}`
              : '⟳ Reset nodes'}
          </button>
          <ThemeToggle theme={theme} onToggle={toggle} />
        </div>
      </header>

      {/* ── Corps : graphe + panneau droit ── */}
      <div style={{ flex: '1 1 0', minHeight: 0, display: 'flex', overflow: 'hidden' }}>

        {/* ── Graphe 2D — 68% ── */}
        <div style={{ flex: '0 0 68%', position: 'relative', borderRight: '1px solid var(--border)' }}>
          {/* Légende */}
          <div style={{
            position: 'absolute', top: 10, left: 12, zIndex: 10,
            display: 'flex', gap: 10, alignItems: 'center', fontSize: 11,
            background: 'var(--bg-surface)', borderRadius: 6, padding: '4px 10px',
            border: '1px solid var(--border)'
          }}>
            <span style={{ fontWeight: 700, letterSpacing: '0.05em', textTransform: 'uppercase', color: 'var(--text-sec)', fontSize: 10 }}>Topology</span>
            <span style={{ color: 'var(--border)' }}>·</span>
            {([['#facc15','Super-Peer'],['#60a5fa','Member'],['#6b7280','Offline']] as [string,string][]).map(([c,l]) => (
              <span key={l} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: c, display: 'inline-block' }} />
                {l}
              </span>
            ))}
          </div>
          <GraphView2D
            topology={topology} error={topoError} theme={theme}
            latestTransfer={latestTransfer} fragmentFiles={fragmentFiles}
            onHighlightChange={(id) => { setHlFileId(id); }}
            onRegisterFocus={(fn) => { graphFocusRef.current = fn; }}
          />
        </div>

        {/* ── Panneau droit — 32% ── */}
        <div style={{ flex: '0 0 32%', display: 'flex', flexDirection: 'column', overflowY: 'auto', gap: 8, padding: '10px 10px', background: 'var(--bg-base)' }}>

          <KpiSection title="Cluster" accent="var(--accent-blue)">
            <KpiCard label="Active WS sessions"   value={health?.sessions ?? '—'} accent="var(--accent-blue)" />
            <KpiCard label="Registered nodes"     value={health?.participants ?? '—'} />
            <KpiCard label="Elected cluster heads" value={health?.registeredSuperPeers ?? '—'} accent="var(--accent-yellow)" />
            <KpiCard label="Members"              value={nodeCount > 0 ? nodeCount - superPeerCount : '—'} />
          </KpiSection>

          <KpiSection title="Network" accent="var(--accent-cyan)">
            <KpiCard label="Relay buffer blocks"  value={health?.pendingBlocks ?? '—'} warn={(health?.pendingBlocks ?? 0) > 50} />
            <KpiCard label="Forwarded blocks"     value={events?.forwardedBlocks ?? '—'} />
            <KpiCard label="Bully elections"      value={events?.electionBroadcasts ?? '—'} accent="var(--accent-yellow)" />
            <KpiCard label="Gossip signals"       value={events?.signalsSent ?? '—'} accent="var(--accent-cyan)" />
          </KpiSection>

          <KpiSection title="Security" accent="var(--accent-red)">
            <KpiCard label="Auth successes"       value={events?.authSuccesses ?? '—'} accent="var(--accent-green)" />
            <KpiCard label="Auth failures"        value={events?.authFailures ?? '—'} warn={(events?.authFailures ?? 0) > 0} />
            <KpiCard label="Auth success rate"    value={fmtPct(events?.authSuccesses ?? 0, totalAuth)} />
            <KpiCard label="Relay uptime"         value={events ? fmtUptime(events.uptimeMs) : '—'} accent="var(--accent-green)" />
          </KpiSection>

          <KpiSection title="Stability" accent="var(--accent-orange)">
            <KpiCard label="Churn 5min"           value={events ? `${events.churnRate}%` : '—'} warn={(events?.churnRate ?? 0) >= 30} />
            <KpiCard label="Total departures"     value={events?.departures ?? '—'} />
            <KpiCard label="Total connections"    value={events?.joinEvents ?? '—'} />
            <KpiCard label="Dropped signals"      value={events?.droppedSignals ?? '—'} warn={(events?.droppedSignals ?? 0) > 10} />
          </KpiSection>

          {/* Clusters */}
          <ClusterPanel data={clusters} />

          {/* Navigation fragments — s'affiche quand un fichier est sélectionné depuis le tooltip */}
          {hlFileId && (() => {
            const hlFile = fragmentFiles.find(f => f.fileId === hlFileId);
            if (!hlFile) return null;
            const frags = hlFile.fragments.map(fr => {
              const n = topology?.nodes.find(nd => nd.id === fr.nodeId);
              return { nodeId: fr.nodeId, index: fr.index, sizeBytes: fr.sizeBytes, isConnected: n?.isConnected ?? fr.nodeConnected, clusterId: n?.clusterId ?? '' };
            });
            const btnStyle = (disabled: boolean): React.CSSProperties => ({
              background: 'none', border: '1px solid #7c3aed', borderRadius: 5,
              cursor: disabled ? 'not-allowed' : 'pointer', color: disabled ? '#6b21a8' : '#a855f7',
              padding: '2px 10px', fontSize: 13, fontWeight: 700, opacity: disabled ? 0.35 : 1,
            });
            return (
              <div style={{ background: 'var(--bg-card2)', borderRadius: 8, border: '1px solid #7c3aed', overflow: 'hidden' }}>
                {/* Header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', borderBottom: '1px solid #4c1d95' }}>
                  <span style={{ fontSize: 12 }}>{hlFile.mimeType.startsWith('image') ? '🖼' : hlFile.mimeType.startsWith('video') ? '🎬' : hlFile.mimeType.startsWith('audio') ? '🎵' : hlFile.mimeType.includes('pdf') ? '📄' : hlFile.mimeType.includes('zip') ? '📦' : '📁'}</span>
                  <span style={{ flex: 1, fontSize: 10, fontWeight: 700, color: '#a855f7', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{hlFile.fileName}</span>
                  <span style={{ fontSize: 9, color: 'var(--text-dim)' }}>k={hlFile.k}/{hlFile.n}</span>
                  <button onClick={() => setHlFileId(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-dim)', fontSize: 13, lineHeight: 1 }}>✕</button>
                </div>
                {/* Flèches navigation */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, padding: '6px 12px', borderBottom: '1px solid var(--border)' }}>
                  <button style={btnStyle(navIdx === 0)} disabled={navIdx === 0} onClick={() => setNavIdx(i => i - 1)}>◀</button>
                  <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-primary)', minWidth: 80, textAlign: 'center' }}>
                    Nœud {navIdx + 1} / {frags.length}
                  </span>
                  <button style={btnStyle(navIdx === frags.length - 1)} disabled={navIdx === frags.length - 1} onClick={() => setNavIdx(i => i + 1)}>▶</button>
                </div>
                {/* Tableau */}
                <div style={{ maxHeight: 180, overflowY: 'auto' }}>
                  {frags.map((fr, i) => {
                    const active = i === navIdx;
                    return (
                      <div key={fr.nodeId} onClick={() => { setNavIdx(i); graphFocusRef.current(fr.nodeId); }}
                        style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 10px', cursor: 'pointer', borderBottom: '1px solid var(--border)',
                          background: active ? '#3b0764' : 'transparent' }}>
                        <span style={{ fontSize: 10, color: '#a855f7', fontWeight: 700, minWidth: 14 }}>{active ? '▶' : ''}</span>
                        <span style={{ fontSize: 10, fontWeight: 700, color: '#a855f7', minWidth: 22 }}>#{fr.index}</span>
                        <span style={{ fontFamily: 'monospace', fontSize: 9, color: 'var(--text-sec)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>{fr.nodeId.slice(0, 12)}…</span>
                        <span style={{ fontSize: 9, fontWeight: 700, color: fr.isConnected ? '#22c55e' : '#ef4444' }}>{fr.isConnected ? '● En ligne' : '● Hors ligne'}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })()}

          {/* Fragment Map */}
          <FragmentMap nodeStatuses={nodeStatuses} />
        </div>
      </div>

      {/* ── Bande du bas : chart + log ── */}
      <div style={{
        flexShrink: 0, height: 220,
        display: 'grid', gridTemplateColumns: '280px 1fr',
        borderTop: '1px solid var(--border)',
        background: 'var(--bg-base)',
      }}>
        {/* Chart activité réseau */}
        <div style={{ borderRight: '1px solid var(--border)', padding: 6 }}>
          <NetworkPanel history={history} theme={theme} />
        </div>

        {/* Log temps réel — grande zone */}
        <div style={{ minHeight: 0, overflow: 'hidden' }}>
          <RealtimeLog logs={logs} />
        </div>
      </div>

    </div>
  );
}
