import { useState, useEffect, useRef } from 'react';
import { fetchHealth, fetchEvents } from '../services/api';
import type { HealthData, EventsData } from '../services/api';

interface HealthState {
  health: HealthData | null;
  events: EventsData | null;
  error: string | null;
  history: { t: number; sessions: number; pendingBlocks: number }[];
}

const POLL_MS = 5000;
const MAX_HISTORY = 60;

export function useHealth(): HealthState {
  const [state, setState] = useState<HealthState>({
    health: null,
    events: null,
    error: null,
    history: []
  });
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const [healthResult, eventsResult] = await Promise.allSettled([fetchHealth(), fetchEvents()]);
        if (!cancelled) {
          const health = healthResult.status === 'fulfilled' ? healthResult.value : null;
          const events = eventsResult.status === 'fulfilled' ? eventsResult.value : null;
          setState(prev => ({
            health: health ?? prev.health,
            events: events ?? prev.events,
            error: !health && !events ? 'Relay offline' : null,
            history: health
              ? [...prev.history.slice(-MAX_HISTORY + 1), { t: Date.now(), sessions: health.sessions, pendingBlocks: health.pendingBlocks }]
              : prev.history
          }));
        }
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
