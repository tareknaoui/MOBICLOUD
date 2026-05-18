import { useEffect, useRef } from 'react';
import type { LogEntry } from '../../hooks/useLogs';

interface Props {
  logs: LogEntry[];
}

const LEVEL_COLOR: Record<string, string> = {
  INFO: '#60a5fa',
  WARN: '#f59e0b',
  ERROR: '#ef4444'
};

const CAT_COLOR: Record<string, string> = {
  AUTH: '#a78bfa',
  ELECTION: '#facc15',
  DEPART: '#f87171',
  JOIN: '#34d399',
  RELAY: '#38bdf8',
  GOSSIP: '#fb923c',
  SERVER: '#94a3b8',
  WS: '#94a3b8'
};

function fmt(ts: number) {
  const d = new Date(ts);
  return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}.${d.getMilliseconds().toString().padStart(3,'0')}`;
}

export default function RealtimeLog({ logs }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const isAtBottom = useRef(true);

  useEffect(() => {
    if (isAtBottom.current && bottomRef.current) {
      bottomRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);

  function onScroll() {
    if (!containerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
    isAtBottom.current = scrollHeight - scrollTop - clientHeight < 40;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--log-bg)', borderRadius: 10, border: '1px solid var(--border)', overflow: 'hidden' }}>
      <div style={{ padding: '6px 12px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--accent-green)', display: 'inline-block' }} />
        <span style={{ color: 'var(--text-sec)', fontSize: 11, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase' }}>Log temps réel</span>
        <span style={{ color: 'var(--text-muted)', fontSize: 10, marginLeft: 'auto' }}>{logs.length} événements</span>
      </div>
      <div
        ref={containerRef}
        onScroll={onScroll}
        style={{ flex: 1, overflowY: 'auto', fontFamily: 'ui-monospace, Consolas, monospace', fontSize: 12, lineHeight: 1.7 }}
      >
        {logs.length === 0 && (
          <div style={{ color: 'var(--text-muted)', padding: '12px 16px', fontStyle: 'italic' }}>
            En attente d'événements...
          </div>
        )}
        {logs.map((log, i) => (
          <div
            key={`${log.ts}-${i}`}
            style={{
              display: 'flex',
              gap: 8,
              padding: '3px 12px',
              borderBottom: '1px solid var(--border-sub)',
              background: log.level === 'ERROR' ? 'rgba(239,68,68,0.06)' : log.level === 'WARN' ? 'rgba(245,158,11,0.04)' : 'transparent'
            }}
          >
            <span style={{ color: 'var(--text-muted)', flexShrink: 0 }}>{fmt(log.ts)}</span>
            <span style={{ color: LEVEL_COLOR[log.level] ?? '#94a3b8', flexShrink: 0, fontWeight: 700, minWidth: 32 }}>
              {log.level === 'INFO' ? 'INF' : log.level === 'WARN' ? 'WRN' : 'ERR'}
            </span>
            <span style={{ color: CAT_COLOR[log.category] ?? '#94a3b8', flexShrink: 0, minWidth: 64 }}>
              [{log.category}]
            </span>
            <span style={{ color: 'var(--text-primary)', wordBreak: 'break-all' }}>{log.message}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
