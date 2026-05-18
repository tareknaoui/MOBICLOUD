import GraphView2D from './components/GraphView2D';
import NetworkPanel from './components/NetworkPanel';
import RealtimeLog from './components/RealtimeLog';
import ClusterPanel from './components/ClusterPanel';
import { useTopology } from './hooks/useTopology';
import { useHealth } from './hooks/useHealth';
import { useLogs } from './hooks/useLogs';
import { useClusters } from './hooks/useClusters';
import { useTheme } from './hooks/useTheme';
import type { HealthData, EventsData } from './services/api';

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
      {theme === 'dark' ? '☀ Clair' : '☾ Sombre'}
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

  const relayOffline = !!(topoError || healthError);
  const nodeCount = topology?.nodes.length ?? 0;
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
              {relayOffline ? 'Relay offline' : 'Relay connecté'}
            </span>
          </span>
          {!relayOffline && health && <>
            <span style={{ color: 'var(--border)' }}>|</span>
            <span style={{ color: 'var(--text-sec)' }}>{health.sessions} sessions · {health.participants} nœuds</span>
            <span style={{ color: 'var(--border)' }}>|</span>
            <span style={{ color: churn >= 30 ? 'var(--accent-red)' : churn >= 15 ? 'var(--accent-orange)' : 'var(--text-sec)', fontWeight: churn >= 30 ? 700 : 400 }}>
              churn {churn}%{churn >= 30 ? ' ⚠' : ''}
            </span>
          </>}
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
            <span style={{ fontWeight: 700, letterSpacing: '0.05em', textTransform: 'uppercase', color: 'var(--text-sec)', fontSize: 10 }}>Topologie</span>
            <span style={{ color: 'var(--border)' }}>·</span>
            {([['#facc15','Super-Peer'],['#60a5fa','Member'],['#6b7280','Offline']] as [string,string][]).map(([c,l]) => (
              <span key={l} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: c, display: 'inline-block' }} />
                {l}
              </span>
            ))}
          </div>
          <GraphView2D topology={topology} error={topoError} theme={theme} />
        </div>

        {/* ── Panneau droit — 32% ── */}
        <div style={{ flex: '0 0 32%', display: 'flex', flexDirection: 'column', overflowY: 'auto', gap: 8, padding: '10px 10px', background: 'var(--bg-base)' }}>

          <KpiSection title="Cluster" accent="var(--accent-blue)">
            <KpiCard label="Sessions WS actives" value={health?.sessions ?? '—'} accent="var(--accent-blue)" />
            <KpiCard label="Nœuds enregistrés"   value={health?.participants ?? '—'} />
            <KpiCard label="Super-Peers élus"     value={health?.registeredSuperPeers ?? '—'} accent="var(--accent-yellow)" />
            <KpiCard label="Membres"              value={nodeCount > 0 ? nodeCount - superPeerCount : '—'} />
          </KpiSection>

          <KpiSection title="Réseau" accent="var(--accent-cyan)">
            <KpiCard label="Blocs relay buffer"   value={health?.pendingBlocks ?? '—'} warn={(health?.pendingBlocks ?? 0) > 50} />
            <KpiCard label="Blocs forwardés"      value={events?.forwardedBlocks ?? '—'} />
            <KpiCard label="Élections Bully"      value={events?.electionBroadcasts ?? '—'} accent="var(--accent-yellow)" />
            <KpiCard label="Signaux Gossip"        value={events?.signalsSent ?? '—'} accent="var(--accent-cyan)" />
          </KpiSection>

          <KpiSection title="Sécurité" accent="var(--accent-red)">
            <KpiCard label="Auth réussies"        value={events?.authSuccesses ?? '—'} accent="var(--accent-green)" />
            <KpiCard label="Auth échouées"        value={events?.authFailures ?? '—'} warn={(events?.authFailures ?? 0) > 0} />
            <KpiCard label="Taux succès auth"     value={fmtPct(events?.authSuccesses ?? 0, totalAuth)} />
            <KpiCard label="Uptime relay"         value={events ? fmtUptime(events.uptimeMs) : '—'} accent="var(--accent-green)" />
          </KpiSection>

          <KpiSection title="Stabilité" accent="var(--accent-orange)">
            <KpiCard label="Churn 5min"           value={events ? `${events.churnRate}%` : '—'} warn={(events?.churnRate ?? 0) >= 30} />
            <KpiCard label="Départs totaux"       value={events?.departures ?? '—'} />
            <KpiCard label="Connexions totales"   value={events?.joinEvents ?? '—'} />
            <KpiCard label="Signaux droppés"      value={events?.droppedSignals ?? '—'} warn={(events?.droppedSignals ?? 0) > 10} />
          </KpiSection>

          {/* Clusters */}
          <ClusterPanel data={clusters} />
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
