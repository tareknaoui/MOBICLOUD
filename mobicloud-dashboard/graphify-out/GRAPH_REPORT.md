# Graph Report - mobicloud-dashboard  (2026-06-01)

## Corpus Check
- Corpus is ~9,206 words - fits in a single context window. You may not need a graph.

## Summary
- 194 nodes · 253 edges · 19 communities (13 shown, 6 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 14 edges (avg confidence: 0.91)
- Token cost: 6,500 input · 1,800 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Data Hooks & State|Data Hooks & State]]
- [[_COMMUNITY_App Shell & P2P Concepts|App Shell & P2P Concepts]]
- [[_COMMUNITY_Cytoscape Dependencies|Cytoscape Dependencies]]
- [[_COMMUNITY_Graph Visualization|Graph Visualization]]
- [[_COMMUNITY_TypeScript App Config|TypeScript App Config]]
- [[_COMMUNITY_TypeScript Node Config|TypeScript Node Config]]
- [[_COMMUNITY_Animation & Polling Patterns|Animation & Polling Patterns]]
- [[_COMMUNITY_Dev Dependencies & ESLint|Dev Dependencies & ESLint]]
- [[_COMMUNITY_Cluster Panel UI|Cluster Panel UI]]
- [[_COMMUNITY_Realtime Log & Events|Realtime Log & Events]]
- [[_COMMUNITY_TypeScript Root Config|TypeScript Root Config]]
- [[_COMMUNITY_Vite & Relay Config|Vite & Relay Config]]
- [[_COMMUNITY_Docs & App Config|Docs & App Config]]
- [[_COMMUNITY_ESLint Settings|ESLint Settings]]
- [[_COMMUNITY_Package JSON|Package JSON]]
- [[_COMMUNITY_App Entry Point|App Entry Point]]

## God Nodes (most connected - your core abstractions)
1. `compilerOptions` - 17 edges
2. `compilerOptions` - 16 edges
3. `App (Root Component)` - 16 edges
4. `App()` - 9 edges
5. `GraphView2D Component` - 6 edges
6. `API Service (api.ts)` - 6 edges
7. `scripts` - 5 edges
8. `Theme` - 5 edges
9. `TopologyData` - 5 edges
10. `ClusterPanel Component` - 5 edges

## Surprising Connections (you probably didn't know these)
- `index.html Entry Point` --references--> `App (Root Component)`  [EXTRACTED]
  mobicloud-dashboard/index.html → mobicloud-dashboard/src/App.tsx
- `Realtime Log Event Categories` --semantically_similar_to--> `Bully Election Algorithm`  [INFERRED] [semantically similar]
  mobicloud-dashboard/src/components/RealtimeLog/index.tsx → mobicloud-dashboard/src/App.tsx
- `Dashboard README` --references--> `tsconfig.app.json (App TypeScript Config)`  [EXTRACTED]
  mobicloud-dashboard/README.md → mobicloud-dashboard/tsconfig.app.json
- `Props` --references--> `TopologyData`  [EXTRACTED]
  src/components/GraphView2D/index.tsx → src/services/api.ts
- `Props` --references--> `Theme`  [EXTRACTED]
  src/components/NetworkPanel/index.tsx → src/hooks/useTheme.ts

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **App orchestrates all dashboard panels via hooks and renders them in layout** — app_tsx, clusterpanel_component, networkpanel_component, realtimelog_component, graphview2d_component [EXTRACTED 1.00]
- **Cluster Topology, Super-Peer Election, and Bully Algorithm form the core P2P management pattern** — concept_cluster_topology, concept_superpeer, concept_bully_election [INFERRED 0.95]
- **useTopology, useHealth, useClusters all poll the API service and feed data upward to App** — hook_usetopology, hook_usehealth, hook_useclusters [INFERRED 0.95]

## Communities (19 total, 6 thin omitted)

### Community 0 - "Data Hooks & State"
Cohesion: 0.14
Nodes (20): useClusters(), HealthState, useHealth(), useLogs(), useTheme(), TopologyState, useTopology(), EventsData (+12 more)

### Community 1 - "App Shell & P2P Concepts"
Cohesion: 0.14
Nodes (22): App (Root Component), ClusterPanel Component, Bully Election Algorithm, Churn Rate Monitoring, Cluster Topology Visualization, KPI Dashboard Sections, Network Activity Time-Series Chart, Realtime Log Event Categories (+14 more)

### Community 2 - "Cytoscape Dependencies"
Cohesion: 0.09
Nodes (21): dependencies, cytoscape, cytoscape-cose-bilkent, react, react-dom, react-force-graph-2d, react-force-graph-3d, recharts (+13 more)

### Community 3 - "Graph Visualization"
Cohesion: 0.12
Nodes (15): CLUSTER_COLORS, fmtAgo(), fmtBytes(), GraphView2D(), KnownNode, Particle, Props, reliabilityColor() (+7 more)

### Community 4 - "TypeScript App Config"
Cohesion: 0.11
Nodes (18): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+10 more)

### Community 5 - "TypeScript Node Config"
Cohesion: 0.11
Nodes (17): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+9 more)

### Community 6 - "Animation & Polling Patterns"
Cohesion: 0.19
Nodes (14): Incremental Cytoscape Topology Update (no re-layout), Canvas Particle Animation for Transfer Events, Short-Polling Pattern (setTimeout loop), SSE Transfer Event Stream, Super-Peer / Cluster Topology Visualisation, API Service (api.ts), cytoscape-cose-bilkent Type Declaration, GraphView2D Component (+6 more)

### Community 7 - "Dev Dependencies & ESLint"
Cohesion: 0.14
Nodes (14): devDependencies, eslint, @eslint/js, eslint-plugin-react-hooks, eslint-plugin-react-refresh, globals, @types/cytoscape, @types/node (+6 more)

### Community 8 - "Cluster Panel UI"
Cohesion: 0.21
Nodes (8): ClusterCard(), ClusterDrillDown(), fmtBytes(), Props, reliabilityColor(), ClusterInfo, ClusterMember, ClustersData

### Community 9 - "Realtime Log & Events"
Cohesion: 0.33
Nodes (4): LogEntry, CAT_COLOR, LEVEL_COLOR, Props

## Knowledge Gaps
- **86 isolated node(s):** `name`, `private`, `version`, `type`, `dev` (+81 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `devDependencies` connect `Dev Dependencies & ESLint` to `Cytoscape Dependencies`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **What connects `name`, `private`, `version` to the rest of the system?**
  _91 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Data Hooks & State` be split into smaller, more focused modules?**
  _Cohesion score 0.13793103448275862 - nodes in this community are weakly interconnected._
- **Should `App Shell & P2P Concepts` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._
- **Should `Cytoscape Dependencies` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `Graph Visualization` be split into smaller, more focused modules?**
  _Cohesion score 0.12380952380952381 - nodes in this community are weakly interconnected._
- **Should `TypeScript App Config` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._