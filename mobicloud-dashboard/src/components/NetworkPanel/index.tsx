import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { Theme } from '../../hooks/useTheme';

interface HistoryPoint { t: number; sessions: number; pendingBlocks: number; }
interface Props { history: HistoryPoint[]; theme: Theme; }

function fmtTime(ts: number) {
  const d = new Date(ts);
  return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}`;
}

export default function NetworkPanel({ history, theme }: Props) {
  const data = history.map(p => ({ time: fmtTime(p.t), Sessions: p.sessions, 'Relay blocks': p.pendingBlocks }));
  const isDark = theme === 'dark';
  const gridColor = isDark ? '#1f2937' : '#e2e8f0';
  const tickColor = isDark ? '#4b5563' : '#94a3b8';
  const tooltipBg = isDark ? '#111827' : '#ffffff';
  const tooltipBorder = isDark ? '#374151' : '#e2e8f0';
  const legendColor = isDark ? '#6b7280' : '#64748b';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', height: '100%', background: 'var(--bg-card)', borderRadius: 10, border: '1px solid var(--border)' }}>
      <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--accent-green)', padding: '8px 12px 4px' }}>Network activity</div>
      <div style={{ flex: 1, minHeight: 0 }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 4, right: 8, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
            <XAxis dataKey="time" tick={{ fill: tickColor, fontSize: 9 }} interval="preserveStartEnd" />
            <YAxis tick={{ fill: tickColor, fontSize: 9 }} allowDecimals={false} />
            <Tooltip
              contentStyle={{ backgroundColor: tooltipBg, border: `1px solid ${tooltipBorder}`, borderRadius: 6, fontSize: 11 }}
              labelStyle={{ color: tickColor }}
            />
            <Legend wrapperStyle={{ color: legendColor, fontSize: 10, paddingTop: 2 }} />
            <Line type="monotone" dataKey="Sessions" stroke="var(--accent-blue)" dot={false} strokeWidth={1.5} />
            <Line type="monotone" dataKey="Relay blocks" stroke="var(--accent-yellow)" dot={false} strokeWidth={1.5} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
