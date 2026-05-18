---
title: 'Métriques Relay Server sur le Dashboard Android'
type: 'feature'
created: '2026-05-18'
status: 'draft'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Le dashboard Android affiche uniquement des données locales (batterie, pairs vus par le device). L'état réel du réseau MobiCloud — combien de nœuds sont actuellement connectés au relay, combien de blocs sont en transit, combien de super-peers sont actifs — est invisible à l'utilisateur.

**Approach:** Ajouter un polling HTTP du endpoint `/health` du relay server (`https://mobicloud-relay-3.onrender.com/health`) depuis l'app Android. Exposer les métriques via un nouveau `RelayMetricsRepository`, les injecter dans le `DashboardViewModel`, et afficher une carte "Serveur Relay" dans le mode Expert du dashboard.

## Boundaries & Constraints

**Always:**
- Polling toutes les 30s uniquement (pas de surcharge réseau)
- La carte n'est visible qu'en mode Expert (pour ne pas surcharger l'UI grand public)
- URL du relay dérivée de `RELAY_SERVER_URLS` existant (pas de duplication hardcodée)
- Si le relay est injoignable, afficher état "Hors ligne" sans crash — jamais de `throw` non catchée vers le ViewModel
- OkHttp déjà présent dans le module `app` (même instance utilisée par le WebSocket client) — ne pas créer de second client

**Ask First:**
- Si tu veux aussi afficher les métriques de `/metrics/events` (compteurs auth, blocs forwardés)

**Never:**
- Modifier le protocole WebSocket binaire (MSG types, framing)
- Afficher ces métriques en mode Simple (risque de surcharger un utilisateur non-technique)
- Ajouter une dépendance Retrofit — OkHttp direct + `org.json` suffisent

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Relay joignable | `/health` retourne JSON valide | `RelayServerHealth(sessions, pendingBlocks, participants, registeredSuperPeers)` parsé | — |
| Relay hors ligne | `IOException` ou timeout | `RelayServerHealth.OFFLINE` émis dans le Flow | Retry au prochain tick (30s) |
| JSON malformé | Réponse HTTP 200 mais payload invalide | `RelayServerHealth.OFFLINE` | Log warning, no crash |
| App en arrière-plan | `WhileSubscribed(5000)` expire | Le polling coroutine est annulé automatiquement | Reprend au premier subscriber |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt:30-32` -- `RELAY_SERVER_URLS` — source de vérité pour l'URL du relay
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt` -- à modifier : injection + nouveau StateFlow `relayHealth`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt` -- à modifier : afficher `RelayMetricsCard` en mode Expert
- `app/src/main/kotlin/com/mobicloud/domain/models/` -- créer `RelayServerHealth.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/` -- créer `RelayMetricsRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/` -- créer `RelayMetricsRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/` -- créer `RelayMetricsModule.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/components/` -- créer `RelayMetricsCard.kt`

## Tasks & Acceptance

**Execution:**
- [ ] `app/src/main/kotlin/com/mobicloud/domain/models/RelayServerHealth.kt` -- Créer data class `RelayServerHealth(val sessions: Int, val pendingBlocks: Int, val participants: Int, val registeredSuperPeers: Int, val isOnline: Boolean)` avec companion `OFFLINE = RelayServerHealth(0,0,0,0,false)` -- modèle domaine sans dépendance Android/OkHttp
- [ ] `app/src/main/kotlin/com/mobicloud/domain/repository/RelayMetricsRepository.kt` -- Créer interface avec `val health: StateFlow<RelayServerHealth>` -- contrat domaine
- [ ] `app/src/main/kotlin/com/mobicloud/data/repository/RelayMetricsRepositoryImpl.kt` -- Implémenter : boucle `while(true) { fetch("/health") → parse JSON → emit; delay(30_000) }` via `flow { ... }.stateIn(...)`. URL = `RELAY_SERVER_URLS.first().replace("wss://", "https://")`. Utiliser un `OkHttpClient()` dédié (timeout 10s). Capturer `IOException` + `JSONException` → émettre `OFFLINE`. -- source réelle de données server-side
- [ ] `app/src/main/kotlin/com/mobicloud/di/RelayMetricsModule.kt` -- `@Binds @Singleton RelayMetricsRepositoryImpl → RelayMetricsRepository` -- câblage Hilt
- [ ] `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt` -- Injecter `RelayMetricsRepository`, ajouter `val relayHealth: StateFlow<RelayServerHealth> = relayMetricsRepository.health.stateIn(...)` -- expose au Compose
- [ ] `app/src/main/kotlin/com/mobicloud/presentation/dashboard/components/RelayMetricsCard.kt` -- Créer composant `@Composable fun RelayMetricsCard(health: RelayServerHealth)` : `Card` avec titre "SERVEUR RELAY", badge vert/rouge online/offline, et 4 KPIs : Sessions actives, Participants, Super-Peers, Blocs en transit -- UI expert uniquement
- [ ] `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt` -- Dans le bloc `if (isExpertMode)`, après `SectionLabel("Diagnostic technique")`, collecter `viewModel.relayHealth` et afficher `RelayMetricsCard(relayHealth)` -- rend la carte visible

**Acceptance Criteria:**
- Given le mode Expert est activé et le relay est joignable, when le dashboard s'ouvre, then une carte "SERVEUR RELAY" apparaît avec des valeurs non-nulles dans les 35 secondes
- Given le relay est injoignable (pas de réseau), when le dashboard est en mode Expert, then la carte affiche "Hors ligne" sans crash
- Given l'app passe en arrière-plan > 5s, when elle revient au premier plan, then le polling reprend sans fuite mémoire
- Given le mode Simple est actif, when le dashboard s'affiche, then aucune carte relay n'est visible

## Design Notes

**Dérivation de l'URL HTTP depuis l'URL WebSocket :**
```kotlin
val httpBaseUrl = RELAY_SERVER_URLS.first()
    .replace("wss://", "https://")  // → "https://mobicloud-relay-3.onrender.com"
val healthUrl = "$httpBaseUrl/health"
```

**Structure du flow de polling dans RelayMetricsRepositoryImpl :**
```kotlin
override val health: StateFlow<RelayServerHealth> = flow {
    while (true) {
        val result = runCatching { fetchHealth() }
        emit(result.getOrElse { RelayServerHealth.OFFLINE })
        delay(30_000L)
    }
}.stateIn(scope, SharingStarted.WhileSubscribed(5000L), RelayServerHealth.OFFLINE)
```

## Verification

**Commands:**
- `./gradlew :app:compileDebugKotlin` -- expected: BUILD SUCCESSFUL, 0 erreurs

**Manual checks (if no CLI):**
- En mode Expert sur appareil physique avec réseau : la carte "SERVEUR RELAY" affiche Sessions > 0 si au moins un appareil est connecté au relay
- En mode avion : la carte affiche "Hors ligne" sans ANR ni crash
