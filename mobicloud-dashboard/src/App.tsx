import { useState, useEffect, useRef } from 'react';
import GraphView2D from './components/GraphView2D';
import type { SelectedNode } from './components/GraphView2D';
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
import { resetAllNodes, subscribeToTransfers, fetchFragments, killNode } from './services/api';

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
      {theme === 'dark' ? 'Light' : 'Dark'}
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

function fmtBytes(b: number) {
  if (b >= 1e9) return `${(b / 1e9).toFixed(1)} GB`;
  if (b >= 1e6) return `${(b / 1e6).toFixed(1)} MB`;
  if (b >= 1e3) return `${(b / 1e3).toFixed(1)} KB`;
  return `${b} B`;
}
function reliabilityColor(s: number) {
  const v = Math.min(1, Math.max(0, s));
  return v >= 0.7 ? '#34c759' : v >= 0.4 ? '#ff9f0a' : '#ff3b30';
}
function mimeIcon(mime: string) {
  if (mime.startsWith('image/')) return 'IMG';
  if (mime.startsWith('video/')) return 'VID';
  if (mime.startsWith('audio/')) return 'AUD';
  if (mime === 'application/pdf') return 'PDF';
  if (mime === 'application/zip') return 'ZIP';
  return 'FILE';
}
function fmtAgo(ms: number) {
  const s = Math.max(0, Math.floor((Date.now() - ms) / 1000));
  if (s < 5) return 'à l\'instant';
  if (s < 60) return `il y a ${s}s`;
  const m = Math.floor(s / 60);
  return m < 60 ? `il y a ${m}m` : `il y a ${Math.floor(m / 60)}h`;
}

interface NodePanelProps {
  node: SelectedNode;
  fragmentFiles: FragmentFile[];
  hlFileId: string | null;
  killing: string | null;
  onClose: () => void;
  onHighlight: (id: string | null) => void;
  onKill: (id: string) => void;
}

function NodePanel({ node, fragmentFiles, hlFileId, killing, onClose, onHighlight, onKill }: NodePanelProps) {
  const used = Math.max(0, node.totalBytes - node.freeBytes);
  const pct = node.totalBytes > 0 ? Math.min(100, (used / node.totalBytes) * 100) : 0;
  const barColor = pct >= 90 ? '#ff3b30' : pct >= 70 ? '#ff9f0a' : '#34c759';
  const relPct = Math.min(100, Math.max(0, node.reliabilityScore * 100));
  const nodeFiles = fragmentFiles.filter(f => f.fragments.some(fr => fr.nodeId === node.id));

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', padding: '8px 10px', fontSize: 11, color: 'var(--text-primary)', overflowY: 'auto' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6, flexShrink: 0 }}>
        <span style={{
          width: 9, height: 9, borderRadius: '50%', display: 'inline-block', flexShrink: 0,
          background: node.isSuperPair ? '#facc15' : node.isConnected ? node.color : '#6b7280',
          boxShadow: node.isSuperPair ? '0 0 5px rgba(250,204,21,0.7)' : 'none',
        }} />
        <span style={{ fontWeight: 700, fontSize: 12 }}>
          {node.isSuperPair ? 'Super-Peer' : node.isConnected ? 'Member' : 'Offline'}
        </span>
        <span style={{ fontFamily: 'monospace', fontSize: 9, color: 'var(--text-dim)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {node.id.slice(0, 14)}…
        </span>
        <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-dim)', fontSize: 13, padding: 0, lineHeight: 1 }}>✕</button>
      </div>

      {/* Grid info */}
      <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '4px 8px', alignItems: 'center', fontSize: 10, flexShrink: 0 }}>
        <span style={{ color: 'var(--text-dim)' }}>Cluster</span>
        <span style={{ fontFamily: 'monospace', fontSize: 9, color: 'var(--accent-blue)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {node.clusterId ? node.clusterId.slice(0, 12) + '…' : '—'}
        </span>

        <span style={{ color: 'var(--text-dim)' }}>Fiabilité</span>
        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ fontWeight: 700, color: reliabilityColor(node.reliabilityScore), minWidth: 30 }}>{relPct.toFixed(0)}%</span>
          <div style={{ flex: 1, height: 4, borderRadius: 2, background: 'var(--bg-card2)', overflow: 'hidden' }}>
            <div style={{ width: `${relPct}%`, height: '100%', background: reliabilityColor(node.reliabilityScore), borderRadius: 2 }} />
          </div>
        </span>

        <span style={{ color: 'var(--text-dim)' }}>Stockage</span>
        <span>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 9, marginBottom: 2, color: 'var(--text-sec)' }}>
            <span><strong style={{ color: 'var(--text-primary)' }}>{fmtBytes(used)}</strong> / {fmtBytes(node.totalBytes)}</span>
            <span style={{ color: barColor }}>{pct.toFixed(0)}%</span>
          </div>
          <div style={{ height: 4, borderRadius: 2, background: 'var(--bg-card2)', overflow: 'hidden' }}>
            <div style={{ width: `${pct}%`, height: '100%', background: barColor, borderRadius: 2, transition: 'width 0.4s ease' }} />
          </div>
          <div style={{ fontSize: 9, marginTop: 2, color: 'var(--text-dim)' }}>Libre : {fmtBytes(node.freeBytes)}</div>
        </span>

        <span style={{ color: 'var(--text-dim)' }}>Adresse</span>
        <span style={{ fontFamily: 'monospace', fontSize: 9 }}>{node.ip || '—'}:{node.port || '—'}</span>

        <span style={{ color: 'var(--text-dim)' }}>Vu</span>
        <span style={{ fontSize: 9, color: 'var(--text-sec)' }}>{node.lastSeen ? fmtAgo(node.lastSeen) : '—'}</span>
      </div>

      {/* Fragments */}
      {nodeFiles.length > 0 && (
        <div style={{ marginTop: 7, borderTop: '1px solid var(--border)', paddingTop: 6, flexShrink: 0 }}>
          <div style={{ fontSize: 9, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.07em', color: '#a855f7', marginBottom: 4 }}>
            Fragments ({nodeFiles.length})
          </div>
          {nodeFiles.map(f => {
            const isHl = hlFileId === f.fileId;
            const myFrag = f.fragments.find(fr => fr.nodeId === node.id);
            const alive = f.fragments.filter(fr => fr.nodeConnected).length;
            const sc = alive < f.k ? '#ef4444' : alive < f.n ? '#f97316' : '#22c55e';
            return (
              <div key={f.fileId} onClick={() => onHighlight(isHl ? null : f.fileId)}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '3px 5px', borderRadius: 4, cursor: 'pointer', marginBottom: 2,
                  background: isHl ? '#3b0764' : 'var(--bg-card2)', border: `1px solid ${isHl ? '#a855f7' : 'var(--border)'}` }}>
                <span style={{ fontSize: 8, fontFamily: 'monospace', background: 'var(--bg-card)', color: '#a855f7', padding: '1px 3px', borderRadius: 2, flexShrink: 0 }}>{mimeIcon(f.mimeType)}</span>
                <span style={{ flex: 1, fontSize: 9, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 600 }}>{f.fileName}</span>
                {myFrag && <span style={{ fontSize: 8, fontFamily: 'monospace', color: '#a855f7', fontWeight: 700 }}>#{myFrag.index}</span>}
                <span style={{ fontSize: 8, fontWeight: 700, color: sc }}>{alive}/{f.n}</span>
                <span style={{ fontSize: 8, color: isHl ? '#a855f7' : 'var(--text-dim)' }}>{isHl ? '●' : '○'}</span>
              </div>
            );
          })}
        </div>
      )}

      {/* Kill */}
      {node.isConnected && (
        <button onClick={() => onKill(node.id)} disabled={killing === node.id}
          style={{ marginTop: 'auto', paddingTop: 6, width: '100%', background: killing === node.id ? '#7f1d1d' : '#dc2626',
            color: '#fff', border: 'none', borderRadius: 5, padding: '5px 0', fontSize: 10, fontWeight: 700,
            cursor: killing === node.id ? 'wait' : 'pointer', flexShrink: 0 }}>
          {killing === node.id ? 'Killing…' : 'Kill node'}
        </button>
      )}
    </div>
  );
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
  const [selectedNode, setSelectedNode] = useState<SelectedNode | null>(null);
  const [killing, setKilling]           = useState<string | null>(null);

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

  async function handleKill(nodeId: string) {
    let secret = sessionStorage.getItem('mc_admin_secret') ?? '';
    if (!secret) {
      secret = window.prompt('Admin secret (ADMIN_SECRET) :') ?? '';
      if (!secret) return;
      sessionStorage.setItem('mc_admin_secret', secret);
    }
    setKilling(nodeId);
    try {
      await killNode(nodeId, secret);
      setSelectedNode(null);
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'Secret incorrect') sessionStorage.removeItem('mc_admin_secret');
      window.alert(`Kill échoué : ${e instanceof Error ? e.message : 'erreur inconnue'}`);
    } finally {
      setKilling(null);
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
            {resetState === 'loading' ? 'Reset…'
              : resetState === 'ok' ? `OK: ${resetMsg}`
              : resetState === 'err' ? `Err: ${resetMsg}`
              : 'Reset nodes'}
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
            onSelectNode={(node) => { setSelectedNode(node); if (!node) setHlFileId(null); }}
          />
        </div>

        {/* ── Panneau droit — 32% ── */}
        <div style={{ flex: '0 0 32%', display: 'flex', flexDirection: 'column', overflowY: 'auto', gap: 8, padding: '10px 10px', background: 'var(--bg-base)' }}>

          {/* Navigation fragments — en haut pour être toujours visible */}
          {hlFileId && (() => {
            const hlFile = fragmentFiles.find(f => f.fileId === hlFileId);
            if (!hlFile) return null;
            const frags = hlFile.fragments.map(fr => {
              const n = topology?.nodes.find(nd => nd.id === fr.nodeId);
              return { nodeId: fr.nodeId, index: fr.index, sizeBytes: fr.sizeBytes, isConnected: n?.isConnected ?? fr.nodeConnected, clusterId: n?.clusterId ?? '' };
            });
            const btnStyle = (disabled: boolean): React.CSSProperties => ({
              background: disabled ? 'var(--bg-card2)' : '#7c3aed',
              border: '1px solid #7c3aed', borderRadius: 5,
              cursor: disabled ? 'not-allowed' : 'pointer',
              color: disabled ? '#6b21a8' : '#fff',
              padding: '3px 12px', fontSize: 14, fontWeight: 700, opacity: disabled ? 0.35 : 1,
            });
            return (
              <div style={{ background: 'var(--bg-card2)', borderRadius: 8, border: '2px solid #7c3aed', overflow: 'hidden', flexShrink: 0 }}>
                {/* Header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', background: '#3b0764', borderBottom: '1px solid #4c1d95' }}>
                  <span style={{ fontSize: 9, fontFamily: 'monospace', background: '#4c1d95', color: '#d8b4fe', padding: '1px 4px', borderRadius: 3 }}>{mimeIcon(hlFile.mimeType)}</span>
                  <span style={{ flex: 1, fontSize: 11, fontWeight: 700, color: '#d8b4fe', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{hlFile.fileName}</span>
                  <span style={{ fontSize: 10, color: '#a78bfa', fontWeight: 600 }}>k={hlFile.k}/{hlFile.n}</span>
                  <button onClick={() => setHlFileId(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#a78bfa', fontSize: 15, lineHeight: 1, padding: '0 2px' }}>✕</button>
                </div>
                {/* Flèches navigation */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 14px', borderBottom: '1px solid var(--border)' }}>
                  <button style={btnStyle(navIdx === 0)} disabled={navIdx === 0} onClick={() => setNavIdx(i => i - 1)}>◀</button>
                  <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-primary)' }}>
                    Nœud {navIdx + 1} / {frags.length}
                  </span>
                  <button style={btnStyle(navIdx === frags.length - 1)} disabled={navIdx === frags.length - 1} onClick={() => setNavIdx(i => i + 1)}>▶</button>
                </div>
                {/* Tableau */}
                <div style={{ maxHeight: 160, overflowY: 'auto' }}>
                  {frags.map((fr, i) => {
                    const active = i === navIdx;
                    return (
                      <div key={fr.nodeId} onClick={() => { setNavIdx(i); graphFocusRef.current(fr.nodeId); }}
                        style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '5px 10px', cursor: 'pointer', borderBottom: '1px solid var(--border)',
                          background: active ? '#3b0764' : 'transparent', transition: 'background 0.15s' }}>
                        <span style={{ fontSize: 10, color: '#a855f7', fontWeight: 700, minWidth: 12 }}>{active ? '▶' : ' '}</span>
                        <span style={{ fontSize: 10, fontWeight: 700, color: '#a855f7', minWidth: 24 }}>#{fr.index}</span>
                        <span style={{ fontFamily: 'monospace', fontSize: 9, color: 'var(--text-sec)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>{fr.nodeId.slice(0, 12)}…</span>
                        <span style={{ fontSize: 9, fontWeight: 700, color: fr.isConnected ? '#22c55e' : '#ef4444' }}>{fr.isConnected ? '● En ligne' : '○ Hors ligne'}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })()}

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
        {/* Nœud sélectionné ou chart réseau */}
        <div style={{ borderRight: '1px solid var(--border)', overflow: 'hidden' }}>
          {selectedNode ? (
            <NodePanel
              node={selectedNode}
              fragmentFiles={fragmentFiles}
              hlFileId={hlFileId}
              killing={killing}
              onClose={() => setSelectedNode(null)}
              onHighlight={(id) => setHlFileId(id)}
              onKill={handleKill}
            />
          ) : (
            <div style={{ padding: 6, height: '100%' }}>
              <NetworkPanel history={history} theme={theme} />
            </div>
          )}
        </div>

        {/* Log temps réel — grande zone */}
        <div style={{ minHeight: 0, overflow: 'hidden' }}>
          <RealtimeLog logs={logs} />
        </div>
      </div>

    </div>
  );
}
