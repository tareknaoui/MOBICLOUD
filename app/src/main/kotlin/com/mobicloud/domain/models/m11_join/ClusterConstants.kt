package com.mobicloud.domain.models.m11_join

// Plafond batterie côté SP + critère unique d'admission (Story 12.1)
// 50 membres × heartbeat 30 s = 1 667 msg/min.
// TEST ONLY — réduit à 2 pour tester la création de cluster par un 3ème nœud.
const val MAX_CLUSTER_SIZE = 1

// Bump de version protocole JOIN suite au retrait GPS du payload signé (Story 12.1).
const val JOIN_PROTOCOL_VERSION = 2

// Compromis batterie vs détection mort : 30 s = 2 cycles radio min, détection décès ≤ 2 min
// (3 heartbeats manqués = SP_TIMEOUT_MS = 90 s) sans surcharger la radio 4G en permanence.
const val HEARTBEAT_INTERVAL_MS = 30_000L

// 3 heartbeats manqués = mort réelle du membre. Anti-flap 4G↔WiFi : le handover
// peut couper 10-20 s sans que le nœud soit réellement mort — 90 s absorbe 2 handovers.
const val SP_TIMEOUT_MS = 90_000L

// NFR-08 : admission ≤ 5 s end-to-end via relai HA (RTT 4G ≈ 100 ms, traitement SP ≈ 10 ms,
// 2 allers-retours = 420 ms ; 5 s laisse 10× la marge pour les pics réseau transitoires).
const val JOIN_REQUEST_TIMEOUT_MS = 5_000L

// Anti-cascade auto-élection en flap réseau transitoire : 20 s d'isolement garantit
// qu'une coupure 4G passagère (reconnexion ≤ 10 s) ne déclenche pas une Bully solo
// et un nouveau cluster orphelin. Inférieur à SP_TIMEOUT_MS (90 s) pour converger vite.
const val ISOLATION_BACKOFF_MS = 20_000L

// 15s = 1/6 de SP_TIMEOUT_MS — granularité d'éviction acceptable (max 105s détection mort réelle),
// et 4× moins de scans que toutes les 5s.
const val LIVENESS_CHECK_INTERVAL_MS = 15_000L
