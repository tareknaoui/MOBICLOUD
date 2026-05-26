import { useEffect, useRef, useState } from 'react';
import Cytoscape from 'cytoscape';
import coseBilkent from 'cytoscape-cose-bilkent';
import type { TopologyData, RelayNode, TransferEvent } from '../../services/api';
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
interface Props { topology: TopologyData | null; error: string | null; theme: Theme; latestTransfer?: TransferEvent | null; }

interface Particle {
  fromX: number; fromY: number;
  toX: number; toY: number;
  startTime: number; duration: number;
  color: string;
}

interface KnownNode {
  id: string; isSuperPair: boolean; isConnected: boolean;
  clusterId: string; reliabilityScore: number; freeBytes: number;
  ip: string; port: number; lastSeen: number; departedAt: number | null;
}

interface SelectedNode {
  id: string; isSuperPair: boolean; isConnected: boolean;
  clusterId: string; reliabilityScore: number; freeBytes: number;
  ip: string; port: number; lastSeen: number; color: string;
}

interface SelectedCluster {
  clusterId: string; color: string;
  memberCount: number; connectedCount: number;
  totalFreeBytes: number; avgReliability: number;
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmtBytes(b: number) {
  if (b >= 1e9) return `${(b / 1e9).toFixed(1)} GB`;
  if (b >= 1e6) return `${(b / 1e6).toFixed(1)} MB`;
  if (b >= 1e3) return `${(b / 1e3).toFixed(1)} KB`;
  return `${b} B`;
}

function fmtAgo(ms: number | undefined): string {
  // Code review P9/P12 : guard undefined (drift API backend) + clamp negatif (horloge browser).
  if (!ms || !Number.isFinite(ms)) return '—';
  const s = Math.max(0, Math.floor((Date.now() - ms) / 1000));
  if (s < 5) return 'à l\'instant';
  if (s < 60) return `il y a ${s}s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `il y a ${m}m`;
  return `il y a ${Math.floor(m / 60)}h`;
}

function reliabilityColor(score: number): string {
  if (score >= 0.7) return '#34c759';
  if (score >= 0.4) return '#ff9f0a';
  return '#ff3b30';
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
export default function GraphView2D({ topology, error, theme, latestTransfer }: Props) {
  const containerRef   = useRef<HTMLDivElement>(null);
  const canvasRef      = useRef<HTMLCanvasElement>(null);
  const particlesRef   = useRef<Particle[]>([]);
  const cyRef          = useRef<Cytoscape.Core | null>(null);
  const knownNodesRef  = useRef(new Map<string, KnownNode>());
  const clusterColorRef = useRef(new Map<string, string>());
  const colorCursorRef = useRef(0);
  const layoutDoneRef  = useRef(false);
  const lastMeshKeyRef = useRef<string>('');

  const isDark   = theme === 'dark';
  const bgColor  = isDark ? '#030712' : '#e8edf5';

  const [selected, setSelected]             = useState<SelectedNode | null>(null);
  const [selectedCluster, setSelectedCluster] = useState<SelectedCluster | null>(null);
  const [tooltipPos, setTooltipPos]         = useState({ x: 0, y: 0 });
  const [connectedCount, setConnectedCount] = useState(0);

  // Code review P10 : clear le panel selectionne si le nœud disparait de la topologie
  // (typique apres /admin/reset ou departure naturelle d'un peer).
  useEffect(() => {
    if (!selected || !topology) return;
    if (!topology.nodes.some(n => n.id === selected.id)) {
      setSelected(null);
    }
  }, [topology, selected]);

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

    cy.on('tap', evt => {
      if (evt.target === cy) {
        setSelected(null);
        setSelectedCluster(null);
      } else if (evt.target.isNode && evt.target.isNode()) {
        const rp = evt.target.renderedPosition();
        setTooltipPos({ x: rp.x, y: rp.y });
        if (evt.target.hasClass('cluster')) {
          const children = evt.target.children().not('.cluster');
          const members = children.toArray() as Cytoscape.NodeSingular[];
          const connected = members.filter(m => m.data('isConnected'));
          const totalFree = members.reduce((s, m) => s + (m.data('freeBytes') as number), 0);
          const avgRel = members.length > 0
            ? members.reduce((s, m) => s + (m.data('reliabilityScore') as number), 0) / members.length
            : 0;
          setSelectedCluster({
            clusterId: evt.target.data('id').replace('cluster-', ''),
            color: evt.target.data('color'),
            memberCount: members.length,
            connectedCount: connected.length,
            totalFreeBytes: totalFree,
            avgReliability: avgRel,
          });
          setSelected(null);
        } else {
          const d = evt.target.data() as SelectedNode;
          setSelected(d);
          setSelectedCluster(null);
        }
      }
    });

    return () => { cy.destroy(); cyRef.current = null; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Resize (sync canvas size too) ──────────────────────────────────────────
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      cyRef.current?.resize();
      if (canvasRef.current) {
        canvasRef.current.width  = el.clientWidth;
        canvasRef.current.height = el.clientHeight;
      }
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // ── Particle RAF loop ───────────────────────────────────────────────────────
  useEffect(() => {
    let rafId: number;
    function easeInOut(t: number) { return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t; }
    function loop() {
      const canvas = canvasRef.current;
      if (!canvas) { rafId = requestAnimationFrame(loop); return; }
      const ctx = canvas.getContext('2d');
      if (!ctx) { rafId = requestAnimationFrame(loop); return; }
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const now = performance.now();
      particlesRef.current = particlesRef.current.filter(p => {
        const t = Math.min(1, (now - p.startTime) / p.duration);
        const et = easeInOut(t);
        const x = p.fromX + (p.toX - p.fromX) * et;
        const y = p.fromY + (p.toY - p.fromY) * et;
        // Glow halo
        const grad = ctx.createRadialGradient(x, y, 0, x, y, 10);
        grad.addColorStop(0, p.color + 'cc');
        grad.addColorStop(1, p.color + '00');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(x, y, 10, 0, Math.PI * 2);
        ctx.fill();
        // Core dot
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(x, y, 3, 0, Math.PI * 2);
        ctx.fill();
        return t < 1;
      });
      rafId = requestAnimationFrame(loop);
    }
    rafId = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(rafId);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Spawn particle on transfer event ───────────────────────────────────────
  useEffect(() => {
    if (!latestTransfer || !cyRef.current) return;
    const cy = cyRef.current;
    const fromNode = cy.$(`#${latestTransfer.from}`);
    const toNode   = cy.$(`#${latestTransfer.to}`);
    if (!fromNode.length || !toNode.length) return;
    const fromPos = fromNode.renderedPosition();
    const toPos   = toNode.renderedPosition();
    const color =
      latestTransfer.kind === 'election' ? '#facc15' :   // jaune — Bully
      latestTransfer.kind === 'signal'   ? '#22d3ee' :   // cyan  — Gossip
      (fromNode.data('color') as string) || '#60a5fa';   // couleur cluster — bloc
    particlesRef.current.push({
      fromX: fromPos.x, fromY: fromPos.y,
      toX: toPos.x, toY: toPos.y,
      startTime: performance.now(),
      duration: latestTransfer.kind === 'block' ? 1200 : 800,
      color,
    });
  }, [latestTransfer]);

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

    // Fallback cluster for members without clusterId: use the super-peer's cluster
    // (relay only stores clusterId for REGISTER_PEER, not for JOIN members)
    const superPeerClusterId = topology.nodes.find(n => n.isSuperPair && n.clusterId)?.clusterId ?? null;

    const newNodeIds: string[] = [];

    cy.batch(() => {
      for (const n of topology.nodes) {
        const isNew   = !known.has(n.id);
        const effectiveClusterId = n.clusterId || (!n.isSuperPair && superPeerClusterId ? superPeerClusterId : null);
        const color   = effectiveClusterId ? (colorMap.get(effectiveClusterId) ?? '#60a5fa') : '#60a5fa';
        const parentId = effectiveClusterId ? `cluster-${effectiveClusterId}` : undefined;

        // Ensure cluster compound node exists before adding child
        if (effectiveClusterId && !cy.$(`#${parentId}`).length) {
          cy.add({
            data: { id: parentId, color, shortLabel: effectiveClusterId.slice(0, 8) + '…' },
            classes: 'cluster',
          });
        }

        const cls  = n.isSuperPair ? 'superpeer' : 'member';
        const data = {
          id: n.id, label: n.id.slice(0, 6),
          isSuperPair: n.isSuperPair, isConnected: n.isConnected,
          clusterId: effectiveClusterId ?? '', reliabilityScore: n.reliabilityScore,
          freeBytes: n.freeBytes, ip: n.ip, port: n.port, lastSeen: n.lastSeen, color,
          parent: parentId,
        };

        if (isNew) {
          // Position: near cluster centroid if layout is done, random otherwise
          let pos = { x: (Math.random() - 0.5) * 300, y: (Math.random() - 0.5) * 300 };
          if (effectiveClusterId && layoutDoneRef.current) {
            const siblings = cy.nodes(`[clusterId = "${effectiveClusterId}"]`).not('.cluster');
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
          existingNode.data('lastSeen', n.lastSeen);
          existingNode.data('color', color);
          existingNode.removeClass('superpeer member offline').addClass(cls);
        }

        known.set(n.id, {
          id: n.id, isSuperPair: n.isSuperPair, isConnected: n.isConnected,
          clusterId: effectiveClusterId ?? '', reliabilityScore: n.reliabilityScore,
          freeBytes: n.freeBytes, ip: n.ip, port: n.port, lastSeen: n.lastSeen, departedAt: null,
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
    if (!layoutDoneRef.current && cy.nodes().not('.cluster').length > 0) {
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
        cy.fit(cy.nodes().not('.cluster'), 60);
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

      {/* Particle canvas overlay — pointer-events: none pour ne pas bloquer les clics Cytoscape */}
      <canvas
        ref={canvasRef}
        style={{ position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 5 }}
      />

      {selected && (
        <div style={{
          position: 'absolute',
          left: Math.min(tooltipPos.x + 16, cw - 248),
          top: Math.min(Math.max(tooltipPos.y - 12, 8), ch - 260),
          zIndex: 30,
          background: isDark ? '#111827' : '#ffffff',
          border: `1px solid ${isDark ? '#1e3a5f' : '#bfdbfe'}`,
          borderRadius: 10, padding: '10px 14px', fontSize: 12,
          color: isDark ? '#e2e8f0' : '#0f172a',
          boxShadow: isDark
            ? '0 4px 24px rgba(0,0,0,0.6), 0 0 0 1px rgba(96,165,250,0.1)'
            : '0 4px 24px rgba(0,0,0,0.14)',
          minWidth: 230, pointerEvents: 'auto',
        }}>
          {/* Header */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 8 }}>
            <span style={{
              width: 11, height: 11, borderRadius: '50%', display: 'inline-block', flexShrink: 0,
              background: selected.isSuperPair ? '#facc15' : selected.isConnected ? selected.color : '#6b7280',
              boxShadow: selected.isSuperPair ? '0 0 6px rgba(250,204,21,0.7)' : 'none',
            }} />
            <span style={{ fontWeight: 700, fontSize: 13 }}>
              {selected.isSuperPair ? 'Super-Peer' : selected.isConnected ? 'Member' : 'Offline'}
            </span>
            <button
              onClick={() => setSelected(null)}
              style={{
                marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer',
                color: isDark ? '#64748b' : '#94a3b8', fontSize: 14, lineHeight: 1, padding: '0 2px',
              }}
              title="Fermer"
            >✕</button>
          </div>

          {/* Node ID */}
          <div style={{
            fontFamily: 'monospace', fontSize: 10,
            color: isDark ? '#94a3b8' : '#64748b',
            background: isDark ? '#1f2937' : '#f1f5f9',
            borderRadius: 4, padding: '3px 6px', marginBottom: 10,
            wordBreak: 'break-all', lineHeight: 1.5,
          }}>
            {selected.id}
          </div>

          {/* Detail rows */}
          <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '5px 12px', alignItems: 'baseline' }}>
            <span style={{ color: isDark ? '#64748b' : '#94a3b8', whiteSpace: 'nowrap' }}>Cluster</span>
            <span style={{ fontFamily: 'monospace', fontSize: 10, color: isDark ? '#93c5fd' : '#2563eb' }}>
              {selected.clusterId ? selected.clusterId.slice(0, 12) + '…' : '—'}
            </span>

            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Fiabilité</span>
            <span style={{ fontWeight: 600, color: reliabilityColor(selected.reliabilityScore) }}>
              {(selected.reliabilityScore * 100).toFixed(1)}%
            </span>

            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Stockage libre</span>
            <span>{fmtBytes(selected.freeBytes)}</span>

            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Adresse</span>
            <span style={{ fontFamily: 'monospace', fontSize: 10 }}>
              {selected.ip || '—'}:{selected.port || '—'}
            </span>

            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Vu</span>
            <span style={{ color: isDark ? '#94a3b8' : '#64748b', fontSize: 11 }}>
              {selected.lastSeen ? fmtAgo(selected.lastSeen) : '—'}
            </span>
          </div>
        </div>
      )}

      {selectedCluster && (
        <div style={{
          position: 'absolute',
          left: Math.min(tooltipPos.x + 16, cw - 248),
          top: Math.min(Math.max(tooltipPos.y - 12, 8), ch - 200),
          zIndex: 30,
          background: isDark ? '#111827' : '#ffffff',
          border: `1px solid ${selectedCluster.color}55`,
          borderRadius: 10, padding: '10px 14px', fontSize: 12,
          color: isDark ? '#e2e8f0' : '#0f172a',
          boxShadow: isDark ? '0 4px 24px rgba(0,0,0,0.6)' : '0 4px 24px rgba(0,0,0,0.14)',
          minWidth: 200, pointerEvents: 'auto',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 8 }}>
            <span style={{ width: 11, height: 11, borderRadius: 3, display: 'inline-block', flexShrink: 0, background: selectedCluster.color }} />
            <span style={{ fontWeight: 700, fontSize: 13 }}>Cluster</span>
            <button onClick={() => setSelectedCluster(null)} style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: isDark ? '#64748b' : '#94a3b8', fontSize: 14, lineHeight: 1, padding: '0 2px' }} title="Fermer">✕</button>
          </div>
          <div style={{ fontFamily: 'monospace', fontSize: 10, color: isDark ? '#94a3b8' : '#64748b', background: isDark ? '#1f2937' : '#f1f5f9', borderRadius: 4, padding: '3px 6px', marginBottom: 10, wordBreak: 'break-all' }}>
            {selectedCluster.clusterId}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '5px 12px', alignItems: 'baseline' }}>
            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Membres</span>
            <span><strong style={{ color: selectedCluster.connectedCount === selectedCluster.memberCount ? '#34c759' : '#ff9f0a' }}>{selectedCluster.connectedCount}</strong>/{selectedCluster.memberCount} connectés</span>

            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Stockage libre</span>
            <span>{fmtBytes(selectedCluster.totalFreeBytes)}</span>

            <span style={{ color: isDark ? '#64748b' : '#94a3b8' }}>Fiab. moy.</span>
            <span style={{ fontWeight: 600, color: reliabilityColor(selectedCluster.avgReliability) }}>
              {(selectedCluster.avgReliability * 100).toFixed(1)}%
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
