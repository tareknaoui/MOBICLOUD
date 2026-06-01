# Graph Report - mobicloud-dashboard  (2026-05-31)

## Corpus Check
- Corpus is ~9,213 words - fits in a single context window. You may not need a graph.

## Summary
- 175 nodes · 237 edges · 16 communities (12 shown, 4 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.9)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Data Hooks & State|Data Hooks & State]]
- [[_COMMUNITY_npm Dependencies|npm Dependencies]]
- [[_COMMUNITY_Graph Visualization|Graph Visualization]]
- [[_COMMUNITY_Architecture & Patterns|Architecture & Patterns]]
- [[_COMMUNITY_TS App Config|TS App Config]]
- [[_COMMUNITY_TS Node Config|TS Node Config]]
- [[_COMMUNITY_API Service Layer|API Service Layer]]
- [[_COMMUNITY_Dev Dependencies|Dev Dependencies]]
- [[_COMMUNITY_Cluster Panel UI|Cluster Panel UI]]
- [[_COMMUNITY_TS Project References|TS Project References]]
- [[_COMMUNITY_Vite Proxy Config|Vite Proxy Config]]
- [[_COMMUNITY_Package Manifest|Package Manifest]]
- [[_COMMUNITY_ESLint Module|ESLint Module]]

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 17 edges
2. `compilerOptions` - 16 edges
3. `App (Root Component)` - 12 edges
4. `App()` - 9 edges
5. `GraphView2D Component` - 8 edges
6. `API Service (api.ts)` - 7 edges
7. `scripts` - 5 edges
8. `Theme` - 5 edges
9. `TopologyData` - 5 edges
10. `useClusters Hook` - 5 edges

## Surprising Connections (you probably didn't know these)
- `GraphView2D Component` --semantically_similar_to--> `ClusterPanel Component`  [INFERRED] [semantically similar]
  mobicloud-dashboard/src/components/GraphView2D/index.tsx → mobicloud-dashboard/src/components/ClusterPanel/index.tsx
- `useTopology Hook` --semantically_similar_to--> `useClusters Hook`  [INFERRED] [semantically similar]
  mobicloud-dashboard/src/hooks/useTopology.ts → mobicloud-dashboard/src/hooks/useClusters.ts
- `Props` --references--> `TopologyData`  [EXTRACTED]
  src/components/GraphView2D/index.tsx → src/services/api.ts
- `Props` --references--> `Theme`  [EXTRACTED]
  src/components/NetworkPanel/index.tsx → src/hooks/useTheme.ts
- `TopologyState` --references--> `TopologyData`  [EXTRACTED]
  src/hooks/useTopology.ts → src/services/api.ts

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Real-time Data Pipeline: Hooks → API → Relay** — dashboard_usetopology, dashboard_usehealth, dashboard_useclusters, dashboard_api [INFERRED 0.90]
- **Topology Visualisation Flow: useTopology → App → GraphView2D + Particles** — dashboard_usetopology, dashboard_app, dashboard_graphview2d [EXTRACTED 1.00]
- **Cluster Health Display: useClusters + useHealth → App → ClusterPanel** — dashboard_useclusters, dashboard_usehealth, dashboard_clusterpanel [INFERRED 0.90]

## Communities (16 total, 4 thin omitted)

### Community 0 - "Data Hooks & State"
Cohesion: 0.14
Nodes (13): useClusters(), useHealth(), LogEntry, useLogs(), useTheme(), useTopology(), CAT_COLOR, LEVEL_COLOR (+5 more)

### Community 1 - "npm Dependencies"
Cohesion: 0.09
Nodes (21): dependencies, cytoscape, cytoscape-cose-bilkent, react, react-dom, react-force-graph-2d, react-force-graph-3d, recharts (+13 more)

### Community 2 - "Graph Visualization"
Cohesion: 0.12
Nodes (15): CLUSTER_COLORS, fmtAgo(), fmtBytes(), GraphView2D(), KnownNode, Particle, Props, reliabilityColor() (+7 more)

### Community 3 - "Architecture & Patterns"
Cohesion: 0.18
Nodes (20): Churn Rate KPI, Incremental Cytoscape Topology Update (no re-layout), Canvas Particle Animation for Transfer Events, Short-Polling Pattern (setTimeout loop), SSE Transfer Event Stream, Super-Peer / Cluster Topology Visualisation, API Service (api.ts), App (Root Component) (+12 more)

### Community 4 - "TS App Config"
Cohesion: 0.11
Nodes (18): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+10 more)

### Community 5 - "TS Node Config"
Cohesion: 0.11
Nodes (17): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+9 more)

### Community 6 - "API Service Layer"
Cohesion: 0.27
Nodes (11): HealthState, TopologyState, EventsData, fetchEvents(), fetchHealth(), fetchTopology(), get(), HealthData (+3 more)

### Community 7 - "Dev Dependencies"
Cohesion: 0.14
Nodes (14): devDependencies, eslint, @eslint/js, eslint-plugin-react-hooks, eslint-plugin-react-refresh, globals, @types/cytoscape, @types/node (+6 more)

### Community 8 - "Cluster Panel UI"
Cohesion: 0.21
Nodes (8): ClusterCard(), ClusterDrillDown(), fmtBytes(), Props, reliabilityColor(), ClusterInfo, ClusterMember, ClustersData

## Knowledge Gaps
- **81 isolated node(s):** `name`, `private`, `version`, `type`, `dev` (+76 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `Dev Dependencies` to `npm Dependencies`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **What connects `name`, `private`, `version` to the rest of the system?**
  _84 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Data Hooks & State` be split into smaller, more focused modules?**
  _Cohesion score 0.1383399209486166 - nodes in this community are weakly interconnected._
- **Should `npm Dependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `Graph Visualization` be split into smaller, more focused modules?**
  _Cohesion score 0.12380952380952381 - nodes in this community are weakly interconnected._
- **Should `TS App Config` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `TS Node Config` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._