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

function FileRow({ f, nodeId, onClick, accent = '#a855f7' }: { f: FragmentFile; nodeId: string; onClick: () => void; accent?: string }) {
  const myFrag = f.fragments.find(fr => fr.nodeId === nodeId);
  const alive  = f.fragments.filter(fr => fr.nodeConnected).length;
  const sc     = alive < f.k ? '#ef4444' : alive === f.fragments.length ? '#22c55e' : '#f97316';
  return (
    <div onClick={onClick}
      style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px', borderRadius: 7, cursor: 'pointer', marginBottom: 5, background: 'var(--bg-card2)', border: '1px solid var(--border)', transition: 'border-color 0.15s' }}
      onMouseEnter={e => (e.currentTarget.style.borderColor = accent)}
      onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}
    >
      <span style={{ fontSize: 10, fontFamily: 'monospace', background: 'var(--bg-card)', color: accent, padding: '2px 5px', borderRadius: 3, flexShrink: 0 }}>{mimeIcon(f.mimeType)}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.fileName}</div>
        <div style={{ fontSize: 10, color: 'var(--text-dim)', marginTop: 2 }}>
          {fmtBytes(f.totalSize)} · RS({f.k},{f.n})
          {myFrag && <span style={{ color: accent, marginLeft: 5 }}>fragment #{myFrag.index}</span>}
        </div>
      </div>
      <div style={{ textAlign: 'right', flexShrink: 0 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: sc }}>{alive}/{f.fragments.length}</div>
        <div style={{ fontSize: 9, color: alive >= f.k ? '#22c55e' : '#ef4444', marginTop: 1 }}>{alive >= f.k ? '✓' : '✗'}</div>
      </div>
      <span style={{ color: 'var(--text-dim)', fontSize: 12 }}>›</span>
    </div>
  );
}

interface NodeSidebarProps {
  node: SelectedNode;
  fragmentFiles: FragmentFile[];
  killing: string | null;
  onClose: () => void;
  onFocusNode: (nodeId: string) => void;
  onKill: (id: string) => void;
}

function NodeSidebar({ node, fragmentFiles, killing, onClose, onFocusNode, onKill }: NodeSidebarProps) {
  const [activeFileId, setActiveFileId] = useState<string | null>(null);
  const used = Math.max(0, node.totalBytes - node.freeBytes);
  const pct  = node.totalBytes > 0 ? Math.min(100, (used / node.totalBytes) * 100) : 0;
  const barColor = pct >= 90 ? '#ff3b30' : pct >= 70 ? '#ff9f0a' : '#34c759';
  const relPct   = Math.min(100, Math.max(0, node.reliabilityScore * 100));
  // Fichiers dont CE nœud héberge au moins un fragment
  const hostedFiles  = fragmentFiles.filter(f => f.fragments.some(fr => fr.nodeId === node.id));
  // Fichiers uploadés PAR ce nœud
  const ownedFiles   = fragmentFiles.filter(f => f.uploaderNodeId === node.id && !f.fragments.some(fr => fr.nodeId === node.id));
  const activeFile = activeFileId ? fragmentFiles.find(f => f.fileId === activeFileId) ?? null : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', fontSize: 12, color: 'var(--text-primary)' }}>

      {/* ── Header nœud ── */}
      <div style={{ flexShrink: 0, padding: '10px 14px 8px', borderBottom: '1px solid var(--border)', background: 'var(--bg-surface)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 8 }}>
          <span style={{
            width: 11, height: 11, borderRadius: '50%', flexShrink: 0, display: 'inline-block',
            background: node.isSuperPair ? '#facc15' : node.isConnected ? node.color : '#6b7280',
            boxShadow: node.isSuperPair ? '0 0 6px rgba(250,204,21,0.8)' : 'none',
          }} />
          <span style={{ fontWeight: 700, fontSize: 13 }}>
            {node.isSuperPair ? 'Super-Peer' : node.isConnected ? 'Member' : 'Offline'}
          </span>
          <span style={{ fontFamily: 'monospace', fontSize: 10, color: 'var(--text-dim)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {node.id.slice(0, 16)}…
          </span>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-dim)', fontSize: 16, lineHeight: 1, padding: 0 }}>✕</button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '5px 10px', fontSize: 11 }}>
          <span style={{ color: 'var(--text-dim)' }}>Cluster</span>
          <span style={{ fontFamily: 'monospace', fontSize: 10, color: 'var(--accent-blue)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {node.clusterId ? node.clusterId.slice(0, 12) + '…' : '—'}
          </span>
          <span style={{ color: 'var(--text-dim)' }}>Fiabilité</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontWeight: 700, color: reliabilityColor(node.reliabilityScore), minWidth: 32 }}>{relPct.toFixed(0)}%</span>
            <div style={{ flex: 1, height: 5, borderRadius: 3, background: 'var(--bg-card2)', overflow: 'hidden' }}>
              <div style={{ width: `${relPct}%`, height: '100%', background: reliabilityColor(node.reliabilityScore), borderRadius: 3 }} />
            </div>
          </span>
          <span style={{ color: 'var(--text-dim)' }}>Stockage</span>
          <span>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, marginBottom: 3 }}>
              <span><strong>{fmtBytes(used)}</strong> / {fmtBytes(node.totalBytes)}</span>
              <span style={{ color: barColor }}>{pct.toFixed(0)}%</span>
            </div>
            <div style={{ height: 5, borderRadius: 3, background: 'var(--bg-card2)', overflow: 'hidden' }}>
              <div style={{ width: `${pct}%`, height: '100%', background: barColor, borderRadius: 3, transition: 'width 0.4s' }} />
            </div>
          </span>
          <span style={{ color: 'var(--text-dim)' }}>IP</span>
          <span style={{ fontFamily: 'monospace', fontSize: 10 }}>{node.ip || '—'}:{node.port || '—'}</span>
        </div>
      </div>

      {/* ── Corps : liste fichiers OU détail fichier ── */}
      <div style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto', padding: '10px 14px' }}>

        {!activeFile ? (
          /* ── Listes fichiers ── */
          <>
            {/* Fichiers dont ce nœud héberge un fragment */}
            <div style={{ fontSize: 10, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#a855f7', marginBottom: 8 }}>
              Fragments hébergés — {hostedFiles.length}
            </div>
            {hostedFiles.length === 0 ? (
              <div style={{ color: 'var(--text-dim)', fontSize: 11, fontStyle: 'italic', marginBottom: 14 }}>Aucun fragment stocké ici</div>
            ) : hostedFiles.map(f => <FileRow key={f.fileId} f={f} nodeId={node.id} onClick={() => setActiveFileId(f.fileId)} />)}

            {/* Fichiers uploadés par ce nœud (mais dont les fragments sont ailleurs) */}
            {ownedFiles.length > 0 && (
              <>
                <div style={{ fontSize: 10, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#38bdf8', marginTop: 14, marginBottom: 8 }}>
                  Fichiers uploadés — {ownedFiles.length}
                </div>
                {ownedFiles.map(f => <FileRow key={f.fileId} f={f} nodeId={node.id} onClick={() => setActiveFileId(f.fileId)} accent="#38bdf8" />)}
              </>
            )}
          </>
        ) : (
          /* ── Détail d'un fichier : distribution des fragments ── */
          <>
            <button
              onClick={() => setActiveFileId(null)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#a855f7', fontSize: 11, padding: 0, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 4 }}
            >
              ‹ Retour aux fichiers
            </button>

            {/* File header */}
            <div style={{ background: '#3b0764', border: '1px solid #4c1d95', borderRadius: 8, padding: '10px 12px', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 6 }}>
                <span style={{ fontSize: 10, fontFamily: 'monospace', background: '#4c1d95', color: '#d8b4fe', padding: '2px 6px', borderRadius: 3 }}>{mimeIcon(activeFile.mimeType)}</span>
                <span style={{ fontWeight: 700, fontSize: 13, color: '#d8b4fe', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{activeFile.fileName}</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '4px 8px', fontSize: 10, color: '#a78bfa' }}>
                <span>Taille : <strong style={{ color: '#d8b4fe' }}>{fmtBytes(activeFile.totalSize)}</strong></span>
                <span>Schéma : <strong style={{ color: '#d8b4fe' }}>RS({activeFile.k},{activeFile.n})</strong></span>
                <span>En ligne : <strong style={{ color: activeFile.onlineFragments >= activeFile.k ? '#22c55e' : '#ef4444' }}>{activeFile.onlineFragments}/{activeFile.fragments.length}</strong></span>
              </div>
            </div>

            {/* Fragment holders */}
            <div style={{ fontSize: 10, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#a855f7', marginBottom: 8 }}>
              Distribution des fragments
            </div>
            {activeFile.fragments.map((fr) => {
              const isMine = fr.nodeId === node.id;
              return (
                <div key={fr.nodeId}
                  onClick={() => onFocusNode(fr.nodeId)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 8, padding: '9px 10px',
                    borderRadius: 7, marginBottom: 5, cursor: 'pointer',
                    background: isMine ? '#1e1b4b' : 'var(--bg-card2)',
                    border: `1px solid ${isMine ? '#6366f1' : 'var(--border)'}`,
                    transition: 'border-color 0.15s',
                  }}
                  onMouseEnter={e => { if (!isMine) e.currentTarget.style.borderColor = '#7c3aed'; }}
                  onMouseLeave={e => { if (!isMine) e.currentTarget.style.borderColor = 'var(--border)'; }}
                >
                  <span style={{ fontSize: 11, fontWeight: 700, color: '#a855f7', minWidth: 22, textAlign: 'center' }}>
                    #{fr.index}
                  </span>
                  <span style={{
                    width: 8, height: 8, borderRadius: '50%', flexShrink: 0,
                    background: fr.nodeConnected ? '#22c55e' : '#6b7280',
                  }} />
                  <span style={{ fontFamily: 'monospace', fontSize: 10, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', color: 'var(--text-sec)' }}>
                    {fr.nodeId.slice(0, 14)}…
                  </span>
                  {isMine && (
                    <span style={{ fontSize: 9, background: '#4338ca', color: '#c7d2fe', padding: '1px 5px', borderRadius: 3, flexShrink: 0 }}>ce nœud</span>
                  )}
                  <span style={{ fontSize: 10, fontWeight: 700, color: fr.nodeConnected ? '#22c55e' : '#ef4444', flexShrink: 0 }}>
                    {fr.nodeConnected ? '● En ligne' : '○ Hors ligne'}
                  </span>
                  <span style={{ fontSize: 10, color: 'var(--text-dim)', flexShrink: 0 }}>{fmtBytes(fr.sizeBytes)}</span>
                  <span style={{ fontSize: 10, color: 'var(--text-dim)' }}>›</span>
                </div>
              );
            })}

            <div style={{ marginTop: 10, padding: '8px 10px', borderRadius: 7, background: 'var(--bg-card2)', border: '1px solid var(--border)', fontSize: 10 }}>
              <span style={{ color: 'var(--text-dim)' }}>Récupérabilité : </span>
              <span style={{ fontWeight: 700, color: activeFile.onlineFragments >= activeFile.k ? '#22c55e' : '#ef4444' }}>
                {activeFile.onlineFragments >= activeFile.k
                  ? `✓ Récupérable (${activeFile.onlineFragments} fragments en ligne ≥ k=${activeFile.k})`
                  : `✗ Perdu (${activeFile.onlineFragments}/${activeFile.k} fragments minimum)`}
              </span>
            </div>
          </>
        )}
      </div>

      {/* ── Kill button ── */}
      {node.isConnected && (
        <div style={{ flexShrink: 0, padding: '8px 14px', borderTop: '1px solid var(--border)' }}>
          <button onClick={() => onKill(node.id)} disabled={killing === node.id}
            style={{ width: '100%', background: killing === node.id ? '#7f1d1d' : '#dc2626',
              color: '#fff', border: 'none', borderRadius: 6, padding: '7px 0', fontSize: 11, fontWeight: 700,
              cursor: killing === node.id ? 'wait' : 'pointer' }}>
            {killing === node.id ? 'Killing…' : 'Kill node'}
          </button>
        </div>
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

        {/* ── Panneau droit — 32% : NodeSidebar si nœud sélectionné, sinon KPIs ── */}
        <div style={{ flex: '0 0 32%', display: 'flex', flexDirection: 'column', overflow: 'hidden', background: 'var(--bg-base)' }}>

          {selectedNode ? (
            <NodeSidebar
              node={selectedNode}
              fragmentFiles={fragmentFiles}
              killing={killing}
              onClose={() => setSelectedNode(null)}
              onFocusNode={(nodeId) => graphFocusRef.current(nodeId)}
              onKill={handleKill}
            />
          ) : (
          <div style={{ flex: '1 1 0', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8, padding: '10px 10px' }}>

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
          )}
        </div>
      </div>

      {/* ── Bande du bas : chart + log ── */}
      <div style={{
        flexShrink: 0, height: 220,
        display: 'grid', gridTemplateColumns: '280px 1fr',
        borderTop: '1px solid var(--border)',
        background: 'var(--bg-base)',
      }}>
        {/* Chart réseau */}
        <div style={{ borderRight: '1px solid var(--border)', overflow: 'hidden', padding: 6 }}>
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
