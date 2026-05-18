import { useState, useEffect, useRef } from 'react';
import { fetchTopology } from '../services/api';
import type { TopologyData } from '../services/api';

interface TopologyState {
  data: TopologyData | null;
  error: string | null;
}

const POLL_MS = 3000;

export function useTopology(): TopologyState {
  const [state, setState] = useState<TopologyState>({ data: null, error: null });
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const data = await fetchTopology();
        if (!cancelled) setState({ data, error: null });
      } catch {
        if (!cancelled) setState(prev => ({ ...prev, error: 'Relay offline' }));
      } finally {
        if (!cancelled) timer.current = setTimeout(poll, POLL_MS);
      }
    }

    poll();
    return () => {
      cancelled = true;
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return state;
}
