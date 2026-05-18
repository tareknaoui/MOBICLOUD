import { useState, useEffect, useRef } from 'react';
import { BASE } from '../services/api';

export interface LogEntry {
  ts: number;
  level: 'INFO' | 'WARN' | 'ERROR';
  category: string;
  message: string;
  [key: string]: unknown;
}

const POLL_MS = 1500;
const MAX_LOCAL = 300;

export function useLogs() {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const lastTs = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const url = lastTs.current > 0
          ? `${BASE}/metrics/logs?since=${lastTs.current}`
          : `${BASE}/metrics/logs`;
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const { logs: incoming } = await res.json() as { logs: LogEntry[] };
        if (!cancelled && incoming.length > 0) {
          lastTs.current = incoming[incoming.length - 1].ts;
          setLogs(prev => {
            const merged = [...prev, ...incoming];
            return merged.slice(-MAX_LOCAL);
          });
        }
      } catch { /* relay offline — silencieux */ }
      finally {
        if (!cancelled) timer.current = setTimeout(poll, POLL_MS);
      }
    }

    poll();
    return () => {
      cancelled = true;
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return logs;
}
