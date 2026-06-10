package com.mobicloud.domain.models.m11_join

// Plafond batterie côté SP + critère unique d'admission (Story 12.1)
// Le SP se compte lui-même (il s'ajoute à son registre) → 3 = 1 SP + 2 membres max par cluster.
const val MAX_CLUSTER_SIZE = 3

// Bump de version protocole JOIN suite au retrait GPS du payload signé (Story 12.1).
const val JOIN_PROTOCOL_VERSION = 2

// Compromis batterie vs détection mort : 30 s = 2 cycles radio min, détection décès ≤ 2 min
// (3 heartbeats manqués = SP_TIMEOUT_MS = 90 s) sans surcharger la radio 4G en permanence.
const val HEARTBEAT_INTERVAL_MS = 30_000L

// 4 heartbeats manqués = mort réelle du membre. Anti-flap 4G↔WiFi + latence relay
// Render (10-40s documentée) : 120s absorbe le worst-case (HB 30s + 40s relay = 70s gap)
// avec 50s de marge, contre 20s seulement avec 90s. Remplacé 90_000L le 2026-05-26.
const val SP_TIMEOUT_MS = 120_000L

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

// Fenêtre anti-replay spécifique aux MEMBER_UPDATE (keepalive + eviction).
// Distincte de BULLY_TIMESTAMP_WINDOW_MS (30s, pour les messages d'élection) : les keepalives
// partent toutes les 60s (SP_TIMEOUT_MS/2 = 120/2) via le relay Render qui peut introduire
// 10-40s de latence queue — la fenêtre doit être ≥ SP_TIMEOUT_MS pour ne pas rejeter des
// heartbeats retardés valides. Mis à jour avec SP_TIMEOUT_MS le 2026-05-26.
const val MEMBER_UPDATE_TIMESTAMP_WINDOW_MS = 120_000L
