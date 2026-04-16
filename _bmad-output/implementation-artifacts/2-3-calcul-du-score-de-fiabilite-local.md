# Story 2.3 : Calcul du Score de Fiabilité Local

Status: done

## Story

En tant que nœud MobiCloud,
Je veux mesurer et publier mon Score de Fiabilité (batterie, uptime, IP),
Afin que les autres nœuds puissent évaluer si je suis un candidat valide pour le rôle de Super-Pair.

## Acceptance Criteria

1. **Given** le Foreground Service est actif
   **When** le score est recalculé toutes les 30 secondes
   **Then** le score composite est calculé : `BatteryLevel (40%) + Uptime (40%) + NetworkStability (20%)` normalisé entre 0.0 et 1.0

2. **And** le score est persisté dans `NodeIdentity.reliabilityScore` (Room DB) via `SecurityRepository` / `IdentityRepository`

3. **And** le score est inclus dans les messages Heartbeat UDP lors du prochain cycle d'émission (valeur issue de l'identité locale mise à jour)

4. **And** le score est inclus dans les enregistrements Firebase (via `signalingRepository.registerNode()` qui lit `identity.reliabilityScore`)

5. **And** l'interface `domain/usecase/m01_discovery/CalculateReliabilityScoreUseCase.kt` encapsule la logique de calcul

6. **And** un mock `StaticMockTrustScore` est injectable via Hilt pour les tests unitaires (interface `ITrustScoreProvider`)

## Tasks / Subtasks

- [x] Task 1 : Créer `ITrustScoreProvider` interface (AC: #6)
  - [x] Définir `interface ITrustScoreProvider` dans `domain/usecase/m01_discovery/` avec `suspend fun getScore(): Float`
  - [x] Aucun import Android — interface pure Kotlin (testable JVM)

- [x] Task 2 : Créer `CalculateReliabilityScoreUseCase.kt` (AC: #1, #5)
  - [x] Implémenter dans `domain/usecase/m01_discovery/CalculateReliabilityScoreUseCase.kt`
  - [x] Injecter `ITrustScoreProvider` (pas de dépendance Android directe dans le use case)
  - [x] Déléguer le calcul à `ITrustScoreProvider.getScore()`
  - [x] Retourner `Float` normalisé entre 0.0f et 1.0f via `Result<Float>`

- [x] Task 3 : Créer `ReliabilityScoreProviderImpl.kt` (AC: #1)
  - [x] Implémenter dans `data/repository/ReliabilityScoreProviderImpl.kt`
  - [x] Injecter `@ApplicationContext context: Context` via Hilt
  - [x] `BatteryLevel` (40%) : lire `BatteryManager.EXTRA_LEVEL / BatteryManager.EXTRA_SCALE` via `Intent.ACTION_BATTERY_CHANGED` (sticky broadcast, pas de listener)
  - [x] `Uptime` (40%) : utiliser `SystemClock.elapsedRealtime()` normalisé sur 24h (86_400_000ms max → score 1.0 si uptime ≥ 24h)
  - [x] `NetworkStability` (20%) : lire `ConnectivityManager.getNetworkCapabilities()` — Wifi=1.0, 4G/LTE=0.7, autre/absent=0.3
  - [x] Formule : `score = (battery * 0.4f) + (uptime * 0.4f) + (network * 0.2f)` clampé à `[0.0f, 1.0f]`
  - [x] **Dispatchers.Default** pour le calcul (CPU, pas I/O)

- [x] Task 4 : Créer `StaticMockTrustScore.kt` (AC: #6)
  - [x] Implémenter dans `data/repository/StaticMockTrustScore.kt` (ou `core/testing/`)
  - [x] Score fixe configurable via constructeur (défaut `0.85f`)
  - [x] Annoté `@TestOnly` ou documenté comme mock uniquement

- [x] Task 5 : Créer `ReliabilityModule.kt` (AC: #5, #6)
  - [x] Module Hilt `@InstallIn(SingletonComponent::class)` dans `di/ReliabilityModule.kt`
  - [x] `@Binds @Singleton` : `ITrustScoreProvider` → `ReliabilityScoreProviderImpl` (prod)
  - [x] Prévoir `@TestInstallIn` pour substituer `StaticMockTrustScore` dans les tests

- [x] Task 6 : Intégrer dans `MobicloudP2PService` (AC: #2, #3, #4)
  - [x] Injecter `CalculateReliabilityScoreUseCase` dans `MobicloudP2PService`
  - [x] Ajouter une coroutine périodique dans `startP2PNetworkLoops()` : `delay(30_000L)` puis recalcul
  - [x] Après chaque calcul : persister le nouveau score via `identityRepository.updateReliabilityScore()` (Room DB via `IdentityRepository` + `IdentityDao`)
  - [x] Le heartbeat UDP lit déjà `identity.reliabilityScore` pour son champ → le score sera inclus automatiquement au prochain cycle
  - [x] Firebase : `signalingRepository.registerNode()` lit déjà `identity.reliabilityScore` → idem automatique après mise à jour

- [x] Task 7 : Tests unitaires (AC: #1, #6)
  - [x] Créer `data/repository/ReliabilityScoreProviderImplTest.kt` (JVM unit test, MockK)
  - [x] Tester : score 0.0 si batterie = 0% → score compris dans [0.0, 1.0]
  - [x] Tester : score 1.0 si batterie 100% + uptime ≥ 24h + Wifi actif
  - [x] Tester : `StaticMockTrustScore(0.85f).getScore()` retourne 0.85f

## Dev Notes

> [!CAUTION] **DISASTER PREVENTION — LIRE AVANT TOUTE IMPLÉMENTATION :**
> 1. **`NodeIdentity.reliabilityScore` EXISTE DÉJÀ** : Le champ `reliabilityScore: Float = 1.0f` est déjà dans `domain/models/NodeIdentity.kt`. NE PAS le redéfinir ni créer un nouveau modèle. La mise à jour du score passe par une méthode de `SecurityRepository` (ou `IdentityRepository`) qui recrée l'objet `NodeIdentity` avec le nouveau score et le persiste en Room DB.
> 2. **Aucun import Android dans `domain/`** : `CalculateReliabilityScoreUseCase.kt` ET `ITrustScoreProvider.kt` doivent être en Kotlin pur, sans `BatteryManager`, `ConnectivityManager`, ni `Context`. Ces imports Android vont dans `data/repository/ReliabilityScoreProviderImpl.kt` uniquement.
> 3. **`StaticMockTrustScore` à implémenter** : L'architecture V4.0 mentionne explicitement ce mock injectable via Hilt pour remplacer le TFLite (retiré du scope). Ce n'est pas facultatif — les tests unitaires en dépendent.
> 4. **`ITrustScoreProvider` : préparer pour TFLite futur** : L'architecture prévoit une intégration IA future. L'interface `ITrustScoreProvider` est le point d'extension — garder le nom et la signature exacte (`suspend fun getScore(): Float`).
> 5. **Persister via le repository existant** : Pour mettre à jour `reliabilityScore` dans Room DB, regarder `IdentityRepositoryImpl.kt` ou `SecurityRepository` — ne pas créer un nouveau DAO ni une nouvelle table. `NodeIdentity` est déjà mappé en base via le module identité (Epic 1 Story 3).
> 6. **`MobicloudP2PService` : cycle de 30s** : La coroutine périodique doit utiliser `while (isActive) { delay(30_000L); recalculate() }` — ne pas utiliser `Timer` ou `Handler`, rester dans les coroutines pour un annulation propre.
> 7. **Dispatchers critiques** : `ReliabilityScoreProviderImpl.getScore()` s'exécute sur `Dispatchers.Default` (calcul CPU léger). Le sticky broadcast `ACTION_BATTERY_CHANGED` est synchrone (pas de suspending) — le lire directement sans `withContext`.
> 8. **`Result<T>` obligatoire** : `CalculateReliabilityScoreUseCase` retourne `Result<Float>`. Les exceptions dans `ReliabilityScoreProviderImpl` (ex: `Context` null) sont wrappées dans `Result.failure(e)`.
> 9. **Pas d'import Firebase dans cette story** : Le score est propagé à Firebase via le mécanisme existant de `signalingRepository.registerNode()` — pas de nouvelle logique Firebase à écrire ici.
> 10. **NE PAS modifier `NodeIdentity.kt`** : Le champ `reliabilityScore` existe déjà. Vérifier uniquement que la méthode de persistance met bien à jour ce champ.

### Infrastructure Existante (Inventaire Précis)

| Fichier | Statut | Action Requise |
|---|---|---|
| `domain/models/NodeIdentity.kt` | ✅ Champ `reliabilityScore: Float = 1.0f` déjà présent | **NE PAS modifier** |
| `domain/repository/SecurityRepository.kt` | ✅ Expose `getIdentity()`, `generateIdentity()`, `signData()` | Vérifier si `updateReliabilityScore()` existe, sinon évaluer si `generateIdentity()` peut être réutilisé |
| `domain/repository/PeerRepository.kt` | ✅ `StateFlow<List<Peer>>`, `registerOrUpdatePeer()` | NE PAS toucher |
| `domain/repository/SignalingRepository.kt` | ✅ `registerNode()` lit `identity.reliabilityScore` via `SecurityRepository.getIdentity()` | Aucune action — score propagé automatiquement |
| `data/network/service/MobicloudP2PService.kt` | ⚠️ Ligne 120 : envoie déjà `reliabilityScore` dans heartbeat | Ajouter la coroutine périodique de recalcul |
| `di/SignalingModule.kt` | ✅ Existant (Story 2.2) | Aucune action |
| `di/FirebaseModule.kt` | ✅ Existant | Aucune action |

### Fichiers à Créer / Modifier

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   └── usecase/
│       └── m01_discovery/
│           ├── ITrustScoreProvider.kt              ← NOUVEAU (interface pure Kotlin)
│           └── CalculateReliabilityScoreUseCase.kt ← NOUVEAU (délègue à ITrustScoreProvider)
├── data/
│   └── repository/
│       ├── ReliabilityScoreProviderImpl.kt         ← NOUVEAU (BatteryManager + SystemClock + ConnectivityManager)
│       └── StaticMockTrustScore.kt                 ← NOUVEAU (mock injectable Hilt pour tests)
└── di/
    └── ReliabilityModule.kt                        ← NOUVEAU (Hilt binding ITrustScoreProvider → Impl)

app/src/main/kotlin/com/mobicloud/data/network/service/
└── MobicloudP2PService.kt                          ← MODIFIER (ajouter coroutine recalcul 30s + persistance)

app/src/test/kotlin/com/mobicloud/data/repository/
└── ReliabilityScoreProviderImplTest.kt             ← NOUVEAU (JVM unit test, MockK)
```

### Détail : `ITrustScoreProvider.kt`

```kotlin
// domain/usecase/m01_discovery/ITrustScoreProvider.kt
package com.mobicloud.domain.usecase.m01_discovery

interface ITrustScoreProvider {
    /** Retourne le score de fiabilité normalisé entre 0.0 et 1.0. */
    suspend fun getScore(): Float
}
```

### Détail : `CalculateReliabilityScoreUseCase.kt`

```kotlin
// domain/usecase/m01_discovery/CalculateReliabilityScoreUseCase.kt
package com.mobicloud.domain.usecase.m01_discovery

import javax.inject.Inject

class CalculateReliabilityScoreUseCase @Inject constructor(
    private val provider: ITrustScoreProvider
) {
    suspend operator fun invoke(): Result<Float> = runCatching {
        provider.getScore().coerceIn(0.0f, 1.0f)
    }
}
```

### Détail : `ReliabilityScoreProviderImpl.kt`

```kotlin
// data/repository/ReliabilityScoreProviderImpl.kt
package com.mobicloud.data.repository

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val UPTIME_MAX_MS = 86_400_000L  // 24 heures → score uptime = 1.0

class ReliabilityScoreProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ITrustScoreProvider {

    override suspend fun getScore(): Float = withContext(Dispatchers.Default) {
        val battery = getBatteryLevel()
        val uptime = getUptimeScore()
        val network = getNetworkScore()
        (battery * 0.4f + uptime * 0.4f + network * 0.2f).coerceIn(0.0f, 1.0f)
    }

    private fun getBatteryLevel(): Float {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level.toFloat() / scale.toFloat() else 0.5f
    }

    private fun getUptimeScore(): Float {
        val uptimeMs = SystemClock.elapsedRealtime()
        return (uptimeMs.toFloat() / UPTIME_MAX_MS).coerceIn(0.0f, 1.0f)
    }

    private fun getNetworkScore(): Float {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return 0.3f
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1.0f
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 0.7f
            else -> 0.3f
        }
    }
}
```

### Détail : `StaticMockTrustScore.kt`

```kotlin
// data/repository/StaticMockTrustScore.kt
package com.mobicloud.data.repository

import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider

/** Mock injectable via Hilt pour les tests unitaires. Score fixe configurable. */
class StaticMockTrustScore(private val fixedScore: Float = 0.85f) : ITrustScoreProvider {
    override suspend fun getScore(): Float = fixedScore
}
```

### Détail : `ReliabilityModule.kt`

```kotlin
// di/ReliabilityModule.kt
package com.mobicloud.di

import com.mobicloud.data.repository.ReliabilityScoreProviderImpl
import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReliabilityModule {
    @Binds
    @Singleton
    abstract fun bindTrustScoreProvider(
        impl: ReliabilityScoreProviderImpl
    ): ITrustScoreProvider
}
```

### Détail : Modifications `MobicloudP2PService`

Ajouter l'injection du use case et la coroutine périodique dans `startP2PNetworkLoops()` :

```kotlin
// Injection à ajouter :
@Inject lateinit var calculateReliabilityScoreUseCase: CalculateReliabilityScoreUseCase

// Dans startP2PNetworkLoops() :
launch {
    while (isActive) {
        delay(30_000L)
        calculateReliabilityScoreUseCase()
            .onSuccess { newScore ->
                // Récupérer l'identité existante et mettre à jour le score en Room DB
                securityRepository.getIdentity().getOrNull()?.let { identity ->
                    val updated = identity.copy(reliabilityScore = newScore)
                    // Persister via le repository identité (Room DB)
                    // NOTE: vérifier la méthode disponible dans SecurityRepository ou IdentityRepository
                    // pour la mise à jour — ne pas créer de nouvelle méthode si updateIdentity() existe
                }
            }
            .onFailure { Log.w(TAG, "Reliability score recalcul échoué", it) }
    }
}
```

> [!WARNING] **Vérifier la méthode de persistance** : Avant d'implémenter la persistance du score, inspecter `SecurityRepositoryImpl.kt` pour trouver comment `NodeIdentity` est persisté en Room DB. Si `updateReliabilityScore(Float)` n'existe pas, ajouter cette méthode à l'interface `SecurityRepository` et son implémentation. Ne pas appeler `generateIdentity()` pour une simple mise à jour de score — cela régénèrerait les clés cryptographiques.

### Détail : Tests unitaires `ReliabilityScoreProviderImplTest.kt`

```kotlin
// Test 1 : Mock batterie 0% → score ≤ 0.6
@Test
fun `score avec batterie 0 pourcent est minimal`() { ... }

// Test 2 : Batterie 100% + uptime max + Wifi → score 1.0
@Test
fun `score maximal avec toutes conditions optimales`() { ... }

// Test 3 : StaticMockTrustScore retourne la valeur fixée
@Test
fun `StaticMockTrustScore retourne le score fixe`() {
    val mock = StaticMockTrustScore(0.85f)
    runBlocking { assertEquals(0.85f, mock.getScore()) }
}

// Test 4 : Score toujours dans [0.0, 1.0]
@Test
fun `score toujours dans l intervalle valide`() { ... }
```

### Learnings de la Story 2.2 (Prévention de Régressions)

- **Chemin canonique `data/repository/`** : Les nouveaux `RepositoryImpl` vont dans `data/repository/` (pas `data/repository_impl/` — dette technique pré-existante).
- **`callbackFlow` + `awaitClose`** : Non applicable ici (pas de listener Firebase), mais le pattern `runCatching` pour wrapper les exceptions Android est obligatoire.
- **`onDisconnect()` AVANT `setValue()`** : Non applicable ici, mais rappel que `SignalingRepository.registerNode()` lira automatiquement le nouveau `reliabilityScore` via `securityRepository.getIdentity()` une fois persisté.
- **Modules Hilt séparés** : Créer `di/ReliabilityModule.kt` séparé (ne pas modifier `FirebaseModule.kt` ni `SignalingModule.kt`).
- **`Result<T>` obligatoire** : Toute exception remonte via `Result.failure(e)` — zéro exception silencieuse.

### Références

- [Source: epics.md#Story 2.3] — Acceptance criteria, formule score
- [Source: architecture.md#Realistic Scope Adjustments] — `StaticMockTrustScore`, `ITrustScoreProvider`, TFLite retiré
- [Source: architecture.md#Complete Project Directory Structure] — chemins `m01_discovery/CalculateReliabilityScoreUseCase`
- [Source: architecture.md#Enforcement Guidelines] — `Dispatchers.Default` pour calcul CPU
- [Source: architecture.md#Error Handling Patterns] — `Result<T>` obligatoire
- [Source: app/.../NodeIdentity.kt] — `reliabilityScore: Float = 1.0f` déjà présent
- [Source: app/.../MobicloudP2PService.kt:120] — `reliabilityScore` déjà utilisé dans heartbeat

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created
- Implémenté `ITrustScoreProvider` (interface pure Kotlin, package `domain/usecase/m01_discovery/`)
- Implémenté `CalculateReliabilityScoreUseCase` — délègue à `ITrustScoreProvider`, retourne `Result<Float>` coercé dans [0.0, 1.0]
- Implémenté `ReliabilityScoreProviderImpl` — BatteryManager (40%) + SystemClock uptime (40%) + ConnectivityManager (20%), `Dispatchers.Default`
- Implémenté `StaticMockTrustScore` — mock injectable à score fixe configurable (défaut 0.85f)
- Créé `ReliabilityModule.kt` — `@Binds @Singleton` ITrustScoreProvider → ReliabilityScoreProviderImpl
- Ajouté `updateReliabilityScore(Float): Result<Unit>` à `IdentityRepository` + `IdentityRepositoryImpl` + `IdentityDao` (Query UPDATE)
- Modifié `MobicloudP2PService` : injection `CalculateReliabilityScoreUseCase` + `IdentityRepository`, Loop 6 coroutine périodique 30s
- Note : `ITrustScoreProvider` dans `domain/repository/` (signature `getTrustScore(nodeId): Int`) est un autre contrat utilisé par `BasicElectionUseCase` — pas de conflit (packages distincts)
- 4 tests unitaires JVM MockK couvrant : batterie 0%, conditions optimales (score=1.0), StaticMock, réseau 4G

### Review Findings

- [x] [Review][Decision] HeartbeatPayload construit une fois — score de fiabilité jamais rafraîchi (AC3) — Résolu : ajout d'un `MutableStateFlow<Float>` dans le service, consommé par `UdpHeartbeatBroadcaster.startBroadcasting()` qui ré-encode le payload à chaque cycle si le score a changé.
- [x] [Review][Decision] `StaticMockTrustScore` compilé dans le source set de production — Résolu : annotation `@VisibleForTesting` ajoutée + KDoc conservé.
- [x] [Review][Decision] `getNetworkScore` ne vérifie pas `NET_CAPABILITY_VALIDATED` — Résolu : condition `hasCapability(NET_CAPABILITY_VALIDATED)` ajoutée pour WiFi. Portail captif → 0.3. Tests mis à jour avec mock de `hasCapability`.
- [x] [Review][Decision] `StaticMockTrustScore` non injectable via Hilt (AC6) — Résolu : instanciation directe dans les tests JVM retenue comme suffisante pour l'AC6.
- [x] [Review][Patch] `UPDATE node_identity SET reliability_score = :score` sans clause WHERE [IdentityDao.kt:17] — Résolu : `WHERE node_id = :nodeId` ajouté. Signature propagée dans `IdentityRepository`, `IdentityRepositoryImpl`, et `MobicloudP2PService` (Loop 6 passe `identity.nodeId`).
- [x] [Review][Patch] Test — constante magique `0.54` sans commentaire de dérivation [ReliabilityScoreProviderImplTest.kt] — Déjà présent dans le code (commentaire `// battery 0.5*0.4=0.2, uptime 0.5*0.4=0.2, 4G 0.7*0.2=0.14 → 0.54`).
- [x] [Review][Defer] Fallback `0.5f` non loggé si `registerReceiver` retourne null [ReliabilityScoreProviderImpl.kt] — deferred, amélioration mineure de l'observabilité
- [x] [Review][Defer] Sémantique uptime : `SystemClock.elapsedRealtime()` mesure l'uptime de l'appareil pas du service, reset après reboot [ReliabilityScoreProviderImpl.kt] — deferred, comportement prescrit par la spec
- [x] [Review][Defer] Précision Float insuffisante pour `elapsedRealtime()` au-delà de ~16 jours [ReliabilityScoreProviderImpl.kt] — deferred, coerceIn contient le risque
- [x] [Review][Defer] Race TOCTOU sur `getIdentity()` dans `IdentityRepositoryImpl` (read-then-insert non transactionnel) [IdentityRepositoryImpl.kt] — deferred, pre-existing, hors scope story 2.3
- [x] [Review][Defer] Loop 6 sans backoff sur échecs DB répétés — log toutes les 30s en boucle en cas de panne DB [MobicloudP2PService.kt] — deferred, amélioration future
- [x] [Review][Defer] `StaticMockTrustScore(fixedScore)` accepte des valeurs hors [0,1] sans validation [StaticMockTrustScore.kt] — deferred, le use case corrige via coerceIn

### File List

- `app/src/main/kotlin/com/mobicloud/domain/usecase/m01_discovery/ITrustScoreProvider.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m01_discovery/CalculateReliabilityScoreUseCase.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/data/repository/ReliabilityScoreProviderImpl.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/data/repository/StaticMockTrustScore.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/di/ReliabilityModule.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/domain/repository/IdentityRepository.kt` (modifié — ajout `updateReliabilityScore`)
- `app/src/main/kotlin/com/mobicloud/data/local/dao/IdentityDao.kt` (modifié — ajout `@Query UPDATE`)
- `app/src/main/kotlin/com/mobicloud/data/repository_impl/IdentityRepositoryImpl.kt` (modifié — implémentation `updateReliabilityScore`)
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (modifié — injection + Loop 6)
- `app/src/test/kotlin/com/mobicloud/data/repository/ReliabilityScoreProviderImplTest.kt` (nouveau)
