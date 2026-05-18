import { useEffect, useRef, useState } from 'react';
import Cytoscape from 'cytoscape';
import coseBilkent from 'cytoscape-cose-bilkent';
import type { TopologyData, RelayNode } from '../../services/api';
import type { Theme } from '../../hooks/useTheme';

// Register layout plugin once (try/catch survives HMR re-imports)
try { Cytoscape.use(coseBilkent); } catch (_) {}

// ── Constants ─────────────────────────────────────────────────────────────────
const CLUSTER_COLORS = [
  '#60a5fa', '#34d399', '#f472b6', '#fb923c',
  '#a78bfa', '#22d3ee', '#f87171', '#fbbf24',
];
const DEPART_TTL_MS = 30_000;

// ── Types ─────────────────────────────────────────────────────────────────────
interface Props { topology: TopologyData | null; error: string | null; theme: Theme; }

interface KnownNode {
  id: string; isSuperPair: boolean; isConnected: boolean;
  clusterId: string; reliabilityScore: number; freeBytes: number;
  ip: string; port: number; departedAt: number | null;
}

interface SelectedNode {
  id: string; isSuperPair: boolean; isConnected: boolean;
  clusterId: string; reliabilityScore: number; freeBytes: number;
  ip: string; port: number; color: string;
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmtBytes(b: number) {
  if (b >= 1e9) return `${(b / 1e9).toFixed(1)} GB`;
  if (b >= 1e6) return `${(b / 1e6).toFixed(1)} MB`;
  if (b >= 1e3) return `${(b / 1e3).toFixed(1)} KB`;
  return `${b} B`;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function buildStylesheet(isDark: boolean): any[] {
  const textColor   = isDark ? '#94a3b8' : '#475569';
  const offlineBg   = isDark ? '#1f2937' : '#cbd5e1';
  const textOutline = isDark ? '#030712' : '#f1f5f9';
  return [
    {
      selector: 'node.cluster',
      style: {
        'background-color': 'data(color)',
        'background-opacity': 0.07,
        'border-color': 'data(color)',
        'border-width': 2,
        'border-style': 'dashed',
        'border-opacity': 0.45,
        'label': 'data(shortLabel)',
        'text-valign': 'top',
        'text-halign': 'center',
        'font-size': '10px',
        'font-weight': 'bold',
        'color': 'data(color)',
        'text-margin-y': -4,
        'shape': 'round-rectangle',
        'padding': '64px',
        'compound-sizing-wrt-labels': 'exclude',
      },
    },
    {
      selector: 'node.superpeer',
      style: {
        'background-color': '#facc15',
        'border-color': '#facc15',
        'border-width': 3,
        'border-opacity': 0.8,
        'width': 22, 'height': 22,
        'label': 'SP',
        'text-valign': 'bottom',
        'text-margin-y': 4,
        'font-size': '9px',
        'font-weight': 'bold',
        'color': '#facc15',
        'text-outline-color': textOutline,
        'text-outline-width': 2,
      },
    },
    {
      selector: 'node.member',
      style: {
        'background-color': 'data(color)',
        'width': 14, 'height': 14,
        'label': 'data(label)',
        'text-valign': 'bottom',
        'text-margin-y': 4,
        'font-size': '8px',
        'color': textColor,
        'text-outline-color': textOutline,
        'text-outline-width': 1.5,
      },
    },
    {
      selector: 'node.offline',
      style: {
        'background-color': offlineBg,
        'border-color': '#6b7280',
        'border-width': 2,
        'border-style': 'dashed',
        'width': 10, 'height': 10,
        'label': 'data(label)',
        'text-valign': 'bottom',
        'text-margin-y': 4,
        'font-size': '8px',
        'color': '#6b7280',
        'opacity': 0.6,
      },
    },
    {
      selector: 'node:active',
      style: { 'overlay-opacity': 0.1 },
    },
    {
      selector: 'edge',
      style: {
        'line-color': 'data(color)',
        'width': 2,
        'opacity': isDark ? 0.55 : 0.65,
        'curve-style': 'straight',
      },
    },
  ];
}

function animateIn(ids: string[], cy: Cytoscape.Core) {
  for (const id of ids) {
    const node = cy.$(`#${id}`);
    if (!node.length) continue;
    node.lock();
    const size = node.hasClass('superpeer') ? 22 : 14;
    node.style({ width: 0, height: 0, opacity: 0 });
    node.animate(
      { style: { width: size, height: size, opacity: 1 } },
      { duration: 600, easing: 'ease-out-cubic' },
    );
  }
}

// ── Component ─────────────────────────────────────────────────────────────────
export default function GraphView2D({ topology, error, theme }: Props) {
  const containerRef   = useRef<HTMLDivElement>(null);
  const cyRef          = useRef<Cytoscape.Core | null>(null);
  const knownNodesRef  = useRef(new Map<string, KnownNode>());
  const clusterColorRef = useRef(new Map<string, string>());
  const colorCursorRef = useRef(0);
  const layoutDoneRef  = useRef(false);
  const lastMeshKeyRef = useRef<string>('');

  const isDark   = theme === 'dark';
  const bgColor  = isDark ? '#030712' : '#e8edf5';

  const [selected, setSelected]       = useState<SelectedNode | null>(null);
  const [tooltipPos, setTooltipPos]   = useState({ x: 0, y: 0 });
  const [connectedCount, setConnectedCount] = useState(0);

  // ── Mount: create Cytoscape instance ───────────────────────────────────────
  useEffect(() => {
    if (!containerRef.current) return;
    const cy = Cytoscape({
      container: containerRef.current,
      elements: [],
      style: buildStylesheet(isDark),
      layout: { name: 'preset' },
      userZoomingEnabled: true,
      userPanningEnabled: true,
      boxSelectionEnabled: false,
      minZoom: 0.15,
      maxZoom: 5,
    });
    cyRef.current = cy;

    cy.on('tap', 'node:not(.cluster)', evt => {
      const d = evt.target.data() as SelectedNode;
      const rp = evt.target.renderedPosition();
      setSelected(d);
      setTooltipPos({ x: rp.x, y: rp.y });
    });
    cy.on('tap', 'core', () => setSelected(null));

    return () => { cy.destroy(); cyRef.current = null; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Resize ─────────────────────────────────────────────────────────────────
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => cyRef.current?.resize());
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // ── Theme ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    cyRef.current?.style(buildStylesheet(isDark));
  }, [isDark]);

  // ── Topology update (incremental — no re-layout) ────────────────────────
  useEffect(() => {
    if (!topology || !cyRef.current) return;
    const cy    = cyRef.current;
    const known = knownNodesRef.current;
    const colorMap = clusterColorRef.current;
    const now   = Date.now();

    // Purge expired departed nodes
    for (const [id, kn] of known.entries()) {
      if (kn.departedAt !== null && now - kn.departedAt > DEPART_TTL_MS) {
        known.delete(id);
        cy.$(`#${id}`).remove();
        if (kn.clusterId) {
          const cl = cy.$(`#cluster-${kn.clusterId}`);
          if (cl.length && cl.children().length === 0) cl.remove();
        }
      }
    }

    // Mark newly departed nodes (offline, edges removed)
    const currentIds = new Set(topology.nodes.map((n: RelayNode) => n.id));
    for (const [id, kn] of known.entries()) {
      if (!currentIds.has(id) && kn.departedAt === null) {
        kn.departedAt = now;
        kn.isConnected = false;
        const node = cy.$(`#${id}`);
        node.removeClass('superpeer member').addClass('offline');
        node.connectedEdges().remove();
      }
    }

    // Assign stable colors to new clusters
    for (const n of topology.nodes) {
      if (n.clusterId && !colorMap.has(n.clusterId)) {
        colorMap.set(n.clusterId, CLUSTER_COLORS[colorCursorRef.current % CLUSTER_COLORS.length]);
        colorCursorRef.current++;
      }
    }

    const newNodeIds: string[] = [];

    cy.batch(() => {
      for (const n of topology.nodes) {
        const isNew   = !known.has(n.id);
        const color   = n.clusterId ? (colorMap.get(n.clusterId) ?? '#60a5fa') : '#60a5fa';
        const parentId = n.clusterId ? `cluster-${n.clusterId}` : undefined;

        // Ensure cluster compound node exists before adding child
        if (n.clusterId && !cy.$(`#${parentId}`).length) {
          cy.add({
            data: { id: parentId, color, shortLabel: n.clusterId.slice(0, 8) + '…' },
            classes: 'cluster',
          });
        }

        const cls  = n.isSuperPair ? 'superpeer' : 'member';
        const data = {
          id: n.id, label: n.id.slice(0, 6),
          isSuperPair: n.isSuperPair, isConnected: n.isConnected,
          clusterId: n.clusterId, reliabilityScore: n.reliabilityScore,
          freeBytes: n.freeBytes, ip: n.ip, port: n.port, color,
          parent: parentId,
        };

        if (isNew) {
          // Position: near cluster centroid if layout is done, random otherwise
          let pos = { x: (Math.random() - 0.5) * 300, y: (Math.random() - 0.5) * 300 };
          if (n.clusterId && layoutDoneRef.current) {
            const siblings = cy.nodes(`[clusterId = "${n.clusterId}"]`).filter(':not(.cluster)');
            if (siblings.length > 0) {
              const positions = siblings.map(m => (m as Cytoscape.NodeSingular).position());
              pos = {
                x: positions.reduce((s, p) => s + p.x, 0) / positions.length + (Math.random() - 0.5) * 150,
                y: positions.reduce((s, p) => s + p.y, 0) / positions.length + (Math.random() - 0.5) * 150,
              };
            }
          }
          cy.add({ data, classes: cls, position: pos });
          newNodeIds.push(n.id);
        } else {
          const existingNode = cy.$(`#${n.id}`);
          existingNode.data('isSuperPair', n.isSuperPair);
          existingNode.data('isConnected', n.isConnected);
          existingNode.data('reliabilityScore', n.reliabilityScore);
          existingNode.data('freeBytes', n.freeBytes);
          existingNode.data('ip', n.ip);
          existingNode.data('port', n.port);
          existingNode.data('color', color);
          existingNode.removeClass('superpeer member offline').addClass(cls);
        }

        known.set(n.id, {
          id: n.id, isSuperPair: n.isSuperPair, isConnected: n.isConnected,
          clusterId: n.clusterId, reliabilityScore: n.reliabilityScore,
          freeBytes: n.freeBytes, ip: n.ip, port: n.port, departedAt: null,
        });
      }

      // Rebuild full-mesh intra-cluster edges only if active membership changed
      const currentMeshKey = Array.from(known.values())
        .filter(n => n.isConnected && n.clusterId)
        .map(n => `${n.id}:${n.clusterId}`)
        .sort()
        .join('|');

      if (currentMeshKey !== lastMeshKeyRef.current) {
        lastMeshKeyRef.current = currentMeshKey;
        cy.edges().remove();
        const byCluster = new Map<string, string[]>();
        for (const kn of known.values()) {
          if (!kn.isConnected || !kn.clusterId) continue;
          if (!byCluster.has(kn.clusterId)) byCluster.set(kn.clusterId, []);
          byCluster.get(kn.clusterId)!.push(kn.id);
        }
        const edges: Cytoscape.ElementDefinition[] = [];
        for (const [cid, ids] of byCluster) {
          const c = colorMap.get(cid) ?? '#60a5fa';
          for (let i = 0; i < ids.length; i++)
            for (let j = i + 1; j < ids.length; j++)
              edges.push({ data: { id: `e-${ids[i]}-${ids[j]}`, source: ids[i], target: ids[j], color: c } });
        }
        if (edges.length) cy.add(edges);
      }
    });

    // Initial layout: cose-bilkent runs once, then nodes remain draggable
    if (!layoutDoneRef.current && cy.nodes(':not(.cluster)').length > 0) {
      layoutDoneRef.current = true;
      const layout = cy.layout({
        name: 'cose-bilkent',
        quality: 'default',
        animate: false,
        randomize: true,
        idealEdgeLength: 500,
        nodeRepulsion: 4000000,
        gravity: 0.1,
        gravityRange: 3.8,
        gravityCompound: 0.5,
        gravityRangeCompound: 1.5,
        nodeOverlap: 200,
        padding: 100,
        numIter: 3000,
        tile: true,
        tilingPaddingVertical: 350,
        tilingPaddingHorizontal: 350,
      } as never);
      layout.on('layoutstop', () => {
        cy.fit(cy.nodes(':not(.cluster)'), 60);
        animateIn(newNodeIds, cy);
      });
      layout.run();
    } else if (newNodeIds.length > 0) {
      animateIn(newNodeIds, cy);
    }

    setConnectedCount(Array.from(known.values()).filter(n => n.isConnected).length);
  }, [topology]);

  const cw = containerRef.current?.clientWidth ?? 800;
  const ch = containerRef.current?.clientHeight ?? 600;

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%', background: bgColor, overflow: 'hidden' }}>
      {error && (
        <div style={{
          position: 'absolute', top: 10, left: '50%', transform: 'translateX(-50%)',
          zIndex: 20, background: '#dc2626', color: '#fff', fontSize: 11,
          padding: '4px 14px', borderRadius: 20, pointerEvents: 'none',
        }}>
          {error} — retry en cours…
        </div>
      )}
      {!error && connectedCount === 0 && (
        <div style={{
          position: 'absolute', inset: 0, display: 'flex', alignItems: 'center',
          justifyContent: 'center', color: '#6b7280', fontSize: 13, pointerEvents: 'none',
        }}>
          Aucun nœud connecté — lance le simulateur ou l'app Android
        </div>
      )}

      {/* Cytoscape mounts here */}
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />

      {selected && (
        <div style={{
          position: 'absolute',
          left: Math.min(tooltipPos.x + 14, cw - 220),
          top: Math.min(Math.max(tooltipPos.y - 10, 10), ch - 200),
          zIndex: 30,
          background: isDark ? '#111827' : '#ffffff',
          border: `1px solid ${isDark ? '#374151' : '#e2e8f0'}`,
          borderRadius: 8, padding: '8px 12px', fontSize: 12,
          color: isDark ? '#e2e8f0' : '#0f172a',
          pointerEvents: 'none',
          boxShadow: '0 4px 16px rgba(0,0,0,0.3)', minWidth: 200,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
            <span style={{
              width: 10, height: 10, borderRadius: '50%', display: 'inline-block',
              background: selected.isSuperPair ? '#facc15' : selected.isConnected ? selected.color : '#6b7280',
            }} />
            <span style={{ fontWeight: 600 }}>
              {selected.isSuperPair ? '⭐ Super-Peer' : selected.isConnected ? 'Member' : '○ Offline'}
            </span>
          </div>
          <div style={{ fontFamily: 'monospace', fontSize: 10, color: isDark ? '#64748b' : '#94a3b8', marginBottom: 6 }}>
            {selected.id}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '3px 10px' }}>
            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Cluster</span>
            <span style={{ fontFamily: 'monospace', fontSize: 10 }}>{selected.clusterId || '—'}</span>
            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Fiabilité</span>
            <span>{(selected.reliabilityScore * 100).toFixed(1)}%</span>
            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Stockage libre</span>
            <span>{fmtBytes(selected.freeBytes)}</span>
            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>IP</span>
            <span style={{ fontFamily: 'monospace', fontSize: 10 }}>{selected.ip || '—'}:{selected.port || '—'}</span>
          </div>
        </div>
      )}
    </div>
  );
}
