# Story 2.4 : Dashboard Tactique — Composants UX de Diagnostic

Status: done

## Story

En tant qu'utilisateur,
Je veux voir un tableau de bord affichant mon état de nœud, les pairs découverts et les événements réseau en temps réel,
Afin d'avoir une visibilité complète sur la santé de mon cluster local.

## Acceptance Criteria

1. **Given** l'utilisateur est sur l'onglet "Dashboard"
   **When** l'écran s'affiche
   **Then** le composant `ReliabilityGauge` affiche le score de fiabilité local sous forme d'une jauge animée circulaire (0–100%)

2. **And** les composants `KpiDiagnosticCard` affichent : Niveau de batterie (%), Uptime (hh:mm), Réseau actif (Wifi/4G), Nombre de pairs actifs

3. **And** le composant `RadarLogConsole` affiche un flux scrollable des 50 derniers événements réseau P2P avec horodatage

4. **And** les données sont mises à jour en temps réel via `StateFlow` (pas de pull manuel)

5. **And** si aucun pair n'est découvert, un message "Aucun pair détecté — scan en cours..." s'affiche

## Tasks / Subtasks

- [x] Task 1 : Créer les modèles domaine pour le Dashboard (AC: #2, #3)
  - [x] Créer `domain/models/NodeDiagnostics.kt` : `data class NodeDiagnostics(batteryPercent: Int, uptimeMs: Long, networkType: NetworkType, activePeerCount: Int, reliabilityScore: Float)`
  - [x] Créer `domain/models/NetworkType.kt` : `enum class NetworkType { WIFI, CELLULAR, UNKNOWN }`
  - [x] Créer `domain/models/NetworkLogEvent.kt` : `data class NetworkLogEvent(val timestampMs: Long, val message: String)`
  - [x] Aucun import Android — Kotlin pur

- [x] Task 2 : Créer les interfaces domaine (AC: #2, #3, #4)
  - [x] Créer `domain/repository/DiagnosticsRepository.kt` : interface avec `val diagnostics: StateFlow<NodeDiagnostics>`
  - [x] Créer `domain/repository/NetworkEventRepository.kt` : interface avec `val events: StateFlow<List<NetworkLogEvent>>` et `fun pushEvent(message: String)`

- [x] Task 3 : Implémenter `DiagnosticsRepositoryImpl` (AC: #2)
  - [x] Créer `data/repository/DiagnosticsRepositoryImpl.kt` dans le package `data/repository/`
  - [x] Injecter `@ApplicationContext context: Context`, `PeerRepository`, `IdentityRepository` via Hilt
  - [x] `batteryPercent` : `BatteryManager.EXTRA_LEVEL / EXTRA_SCALE * 100` via sticky broadcast `ACTION_BATTERY_CHANGED`
  - [x] `uptimeMs` : `SystemClock.elapsedRealtime()`
  - [x] `networkType` : `ConnectivityManager.getNetworkCapabilities()` — `TRANSPORT_WIFI → WIFI`, `TRANSPORT_CELLULAR → CELLULAR`, sinon `UNKNOWN`
  - [x] `activePeerCount` : `peerRepository.peers.value.count { it.isActive }`
  - [x] `reliabilityScore` : `identityRepository.getIdentity().getOrElse { return@getOrElse NodeIdentity("", ByteArray(0), 0f) }.reliabilityScore`
  - [x] Exposer via `MutableStateFlow<NodeDiagnostics>` rafraîchi par coroutine `delay(5_000L)` (affichage UI, pas calcul Score — la période de 30s reste dans MobicloudP2PService)
  - [x] `Dispatchers.Default` pour les calculs Android non-I/O

- [x] Task 4 : Implémenter `NetworkEventRepositoryImpl` (AC: #3)
  - [x] Créer `data/repository/NetworkEventRepositoryImpl.kt` dans le package `data/repository/`
  - [x] `MutableStateFlow<List<NetworkLogEvent>>(emptyList())` en mémoire (ring buffer max 50)
  - [x] `pushEvent(message: String)` : prepend un `NetworkLogEvent(System.currentTimeMillis(), message)` et tronque à 50 éléments
  - [x] **Thread-safe** : utiliser `synchronized` ou `MutableStateFlow.update { }` atomique
  - [x] Annoter `@Singleton` via Hilt

- [x] Task 5 : Créer les modules Hilt (AC: #4)
  - [x] Créer `di/DiagnosticsModule.kt` : `@Binds @Singleton DiagnosticsRepository → DiagnosticsRepositoryImpl`
  - [x] Créer `di/NetworkEventModule.kt` : `@Binds @Singleton NetworkEventRepository → NetworkEventRepositoryImpl`
  - [x] **Ne pas modifier** `FirebaseModule.kt`, `SignalingModule.kt`, `ReliabilityModule.kt`

- [x] Task 6 : Intégrer `NetworkEventRepository` dans `MobicloudP2PService` (AC: #3)
  - [x] Injecter `NetworkEventRepository` dans `MobicloudP2PService`
  - [x] Appeler `networkEventRepository.pushEvent(...)` aux événements clés :
    - Heartbeat UDP reçu : `"[UDP] Heartbeat reçu de ${peer.nodeId.take(8)}"`
    - Pair activé/inactivé (eviction) : `"[PEER] ${peer.nodeId.take(8)} → INACTIVE"`
    - Firebase peer découvert : `"[FIREBASE] Pair distant découvert : ${peer.nodeId.take(8)}"`
    - Enregistrement Firebase réussi/échoué : `"[TRACKER] Enregistrement Firebase réussi"` / `"[TRACKER] Firebase indisponible — mode local"`
    - TCP connexion établie : `"[TCP] Connexion établie avec ${nodeId.take(8)}"`

- [x] Task 7 : Mettre à jour `DashboardViewModel` (AC: #1, #2, #3, #4, #5)
  - [x] Injecter `DiagnosticsRepository` et `NetworkEventRepository` en plus de `NetworkServiceController` existant
  - [x] Ajouter `val diagnostics: StateFlow<NodeDiagnostics>` — `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), default)`
  - [x] Ajouter `val networkEvents: StateFlow<List<NetworkLogEvent>>` — `.stateIn(...)`
  - [x] Conserver `val serviceStatus: StateFlow<ServiceStatus>` **sans le modifier** (déjà fonctionnel)
  - [x] Dériver `val hasActivePeers: StateFlow<Boolean>` de `diagnostics.map { it.activePeerCount > 0 }`

- [x] Task 8 : Créer les composants Composable (AC: #1, #2, #3, #5)
  - [x] Créer `presentation/dashboard/components/ReliabilityGauge.kt`
  - [x] Créer `presentation/dashboard/components/KpiDiagnosticCard.kt`
  - [x] Créer `presentation/dashboard/components/RadarLogConsole.kt`
  - [x] **Ne pas créer** `ErasureProgressIndicator` — c'est pour l'Epic 5

- [x] Task 9 : Mettre à jour `DashboardScreen.kt` (AC: #1, #2, #3, #4, #5)
  - [x] Remplacer le placeholder actuel par l'UI complète
  - [x] Conserver `collectAsStateWithLifecycle()` (déjà présent dans le placeholder)
  - [x] **NE PAS** supprimer `DashboardRoute` ou le `@Serializable` — c'est la route de navigation

- [x] Task 10 : Tests unitaires (AC: #3, #5)
  - [x] Tester `NetworkEventRepositoryImpl` : ring buffer (max 50), pushEvent thread-safety, ordering (plus récent en tête)
  - [x] Tester `DashboardViewModel` avec MockK : `diagnostics`, `networkEvents`, `hasActivePeers` via `StateFlow` fake

## Dev Notes

> [!CAUTION] **DISASTER PREVENTION — LIRE AVANT TOUTE IMPLÉMENTATION :**
>
> 1. **`DashboardScreen.kt` ET `DashboardViewModel.kt` EXISTENT DÉJÀ** : Ce sont des placeholders fonctionnels. **NE PAS** les recréer — modifier uniquement. La route `DashboardRoute` et `@Serializable` doivent rester intacts (navigation déjà câblée dans Story 1.2).
>
> 2. **`MobicloudP2PService` : injecter `NetworkEventRepository`, ne pas modifier la logique existante** : Les 6 boucles P2P existantes (`Loop 1` UDP broadcaster, `Loop 2` heartbeat receiver, `Loop 3` eviction, `Loop 4` Firebase discover, `Loop 5` Firebase announce, `Loop 6` reliability score) doivent rester intactes. Se contenter d'ajouter des appels `networkEventRepository.pushEvent(...)` aux événements pertinents.
>
> 3. **`ReliabilityScoreProviderImpl` : NE PAS dupliquer la logique** : La lecture batterie/uptime/réseau est déjà implémentée dans `data/repository/ReliabilityScoreProviderImpl.kt`. `DiagnosticsRepositoryImpl` peut lire les mêmes APIs Android (BatteryManager, SystemClock, ConnectivityManager) indépendamment — c'est acceptable car les périodes sont différentes (5s affichage vs 30s calcul de score).
>
> 4. **`data/repository/` vs `data/repository_impl/`** : L'architecture V4.0 prescrit `data/repository/` mais le projet a hérité `data/repository_impl/` pour certains fichiers (Story 2.2). **Créer les nouveaux fichiers dans `data/repository/`** (chemin canonique) — pas dans `data/repository_impl/`.
>
> 5. **Aucun import Android dans `domain/`** : `NodeDiagnostics`, `NetworkType`, `NetworkLogEvent`, `DiagnosticsRepository`, `NetworkEventRepository` doivent être en Kotlin pur. Les imports `BatteryManager`, `ConnectivityManager`, `SystemClock` vont exclusivement dans `data/repository/DiagnosticsRepositoryImpl.kt`.
>
> 6. **`RadarLogConsole` : 50 événements max, scrollable, PAS d'animations continues** : Le UX design prescrit explicitement d'éviter les animations coûteuses en batterie. Le composant est un `LazyColumn` statique avec `reverseLayout = true` (plus récent en haut). PAS d'animation Lottie, PAS de pastille clignotante animée — juste du texte.
>
> 7. **`ReliabilityGauge` : Canvas Compose, PAS d'animation continue** : Utiliser `Canvas { drawArc(...) }` dans Compose. L'arc est recalculé à chaque recomposition (StateFlow déclenche la recomposition). PAS d'`animateFloatAsState` avec durée infinie — l'animation de transition est optionnelle avec une durée courte (300ms max).
>
> 8. **`collectAsStateWithLifecycle()` OBLIGATOIRE** (pas `collectAsState()`) : Suspend la collecte quand l'UI est en arrière-plan. Déjà présent dans `DashboardScreen.kt` existant — conserver.
>
> 9. **`Result<T>` obligatoire pour `DiagnosticsRepositoryImpl`** : Si `identityRepository.getIdentity()` échoue, retourner un `NodeDiagnostics` par défaut (score=0f) et ne pas planter.
>
> 10. **Ne PAS lire la batterie via `Flow` avec listener** : Utiliser le sticky broadcast `ACTION_BATTERY_CHANGED` (synchrone, sans listener), comme dans `ReliabilityScoreProviderImpl`. PAS de `registerReceiver` avec BroadcastReceiver callbacks.

### Infrastructure Existante (Inventaire Précis)

| Fichier | Statut | Action Requise |
|---|---|---|
| `presentation/dashboard/DashboardScreen.kt` | ✅ Placeholder fonctionnel (Box + Text) | **MODIFIER** — remplacer le contenu par l'UI complète |
| `presentation/dashboard/DashboardViewModel.kt` | ✅ `serviceStatus: StateFlow<ServiceStatus>` existant | **MODIFIER** — ajouter `diagnostics` et `networkEvents` |
| `domain/repository/PeerRepository.kt` | ✅ `val peers: StateFlow<List<Peer>>` | NE PAS toucher — utiliser dans `DiagnosticsRepositoryImpl` |
| `domain/repository/IdentityRepository.kt` | ✅ `suspend fun getIdentity(): Result<NodeIdentity>` | NE PAS toucher |
| `domain/repository/NetworkServiceController.kt` | ✅ `val serviceStatus: StateFlow<ServiceStatus>` | NE PAS toucher |
| `domain/models/NodeIdentity.kt` | ✅ `nodeId: String`, `reliabilityScore: Float` | NE PAS toucher |
| `domain/models/Peer.kt` | ✅ `isActive: Boolean`, `identity: NodeIdentity` | NE PAS toucher |
| `domain/models/ServiceStatus.kt` | ✅ `STOPPED, STARTING, RUNNING, ERROR` | NE PAS toucher |
| `data/repository/ReliabilityScoreProviderImpl.kt` | ✅ Lit BatteryManager, SystemClock, ConnectivityManager | NE PAS modifier — réimplémenter indépendamment dans DiagnosticsRepositoryImpl |
| `data/network/service/MobicloudP2PService.kt` | ⚠️ Injecter `NetworkEventRepository` + appels `pushEvent` | **MODIFIER** — ajouter injection + logs events |
| `di/ReliabilityModule.kt` | ✅ Existant (Story 2.3) | NE PAS toucher |

### Fichiers à Créer / Modifier

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   ├── models/
│   │   ├── NodeDiagnostics.kt           ← NOUVEAU (data class, Kotlin pur)
│   │   ├── NetworkType.kt               ← NOUVEAU (enum : WIFI, CELLULAR, UNKNOWN)
│   │   └── NetworkLogEvent.kt           ← NOUVEAU (data class : timestampMs, message)
│   └── repository/
│       ├── DiagnosticsRepository.kt     ← NOUVEAU (interface : StateFlow<NodeDiagnostics>)
│       └── NetworkEventRepository.kt    ← NOUVEAU (interface : StateFlow<List<NetworkLogEvent>> + pushEvent)
├── data/
│   └── repository/
│       ├── DiagnosticsRepositoryImpl.kt ← NOUVEAU (Android APIs : Battery + SystemClock + Connectivity)
│       └── NetworkEventRepositoryImpl.kt ← NOUVEAU (ring buffer in-memory, max 50)
├── di/
│   ├── DiagnosticsModule.kt             ← NOUVEAU (Hilt @Binds @Singleton)
│   └── NetworkEventModule.kt            ← NOUVEAU (Hilt @Binds @Singleton)
└── presentation/
    └── dashboard/
        ├── DashboardViewModel.kt        ← MODIFIER (ajouter diagnostics + networkEvents)
        ├── DashboardScreen.kt           ← MODIFIER (remplacer placeholder par UI complète)
        └── components/
            ├── ReliabilityGauge.kt      ← NOUVEAU (composable Canvas)
            ├── KpiDiagnosticCard.kt     ← NOUVEAU (composable carte KPI)
            └── RadarLogConsole.kt       ← NOUVEAU (composable LazyColumn logs)

app/src/main/kotlin/com/mobicloud/data/network/service/
└── MobicloudP2PService.kt               ← MODIFIER (injection NetworkEventRepository + pushEvent calls)

app/src/test/kotlin/com/mobicloud/
└── data/repository/
    ├── NetworkEventRepositoryImplTest.kt ← NOUVEAU
    └── DashboardViewModelTest.kt         ← NOUVEAU
```

### Détail : Modèles Domaine

```kotlin
// domain/models/NetworkType.kt
package com.mobicloud.domain.models

enum class NetworkType { WIFI, CELLULAR, UNKNOWN }

// domain/models/NodeDiagnostics.kt
package com.mobicloud.domain.models

data class NodeDiagnostics(
    val batteryPercent: Int,      // 0–100
    val uptimeMs: Long,           // SystemClock.elapsedRealtime()
    val networkType: NetworkType,
    val activePeerCount: Int,
    val reliabilityScore: Float   // 0.0–1.0
) {
    companion object {
        val DEFAULT = NodeDiagnostics(0, 0L, NetworkType.UNKNOWN, 0, 0f)
    }
}

// domain/models/NetworkLogEvent.kt
package com.mobicloud.domain.models

data class NetworkLogEvent(
    val timestampMs: Long,
    val message: String
)
```

### Détail : Interfaces Domaine

```kotlin
// domain/repository/DiagnosticsRepository.kt
package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeDiagnostics
import kotlinx.coroutines.flow.StateFlow

interface DiagnosticsRepository {
    val diagnostics: StateFlow<NodeDiagnostics>
}

// domain/repository/NetworkEventRepository.kt
package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NetworkLogEvent
import kotlinx.coroutines.flow.StateFlow

interface NetworkEventRepository {
    val events: StateFlow<List<NetworkLogEvent>>
    fun pushEvent(message: String)
}
```

### Détail : `DiagnosticsRepositoryImpl.kt`

```kotlin
// data/repository/DiagnosticsRepositoryImpl.kt
package com.mobicloud.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import com.mobicloud.domain.models.NetworkType
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.PeerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val peerRepository: PeerRepository,
    private val identityRepository: IdentityRepository
) : DiagnosticsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _diagnostics = MutableStateFlow(NodeDiagnostics.DEFAULT)
    override val diagnostics: StateFlow<NodeDiagnostics> = _diagnostics

    init {
        scope.launch {
            while (isActive) {
                _diagnostics.value = buildDiagnostics()
                delay(5_000L) // Rafraîchissement affichage toutes les 5s (≠ calcul score 30s)
            }
        }
    }

    private suspend fun buildDiagnostics(): NodeDiagnostics {
        val battery = getBatteryPercent()
        val uptime = SystemClock.elapsedRealtime()
        val network = getNetworkType()
        val peers = peerRepository.peers.value.count { it.isActive }
        val score = identityRepository.getIdentity().getOrNull()?.reliabilityScore ?: 0f
        return NodeDiagnostics(battery, uptime, network, peers, score)
    }

    private fun getBatteryPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (scale > 0) (level * 100 / scale) else 0
    }

    private fun getNetworkType(): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkType.UNKNOWN
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.UNKNOWN
        }
    }
}
```

### Détail : `NetworkEventRepositoryImpl.kt`

```kotlin
// data/repository/NetworkEventRepositoryImpl.kt
package com.mobicloud.data.repository

import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.repository.NetworkEventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_EVENTS = 50

@Singleton
class NetworkEventRepositoryImpl @Inject constructor() : NetworkEventRepository {

    private val _events = MutableStateFlow<List<NetworkLogEvent>>(emptyList())
    override val events: StateFlow<List<NetworkLogEvent>> = _events

    override fun pushEvent(message: String) {
        _events.update { current ->
            val newEvent = NetworkLogEvent(System.currentTimeMillis(), message)
            (listOf(newEvent) + current).take(MAX_EVENTS)
        }
    }
}
```

### Détail : `DashboardViewModel.kt` (mis à jour)

```kotlin
// presentation/dashboard/DashboardViewModel.kt
package com.mobicloud.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NetworkServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    networkServiceController: NetworkServiceController,
    diagnosticsRepository: DiagnosticsRepository,
    networkEventRepository: NetworkEventRepository
) : ViewModel() {

    val serviceStatus: StateFlow<ServiceStatus> = networkServiceController.serviceStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ServiceStatus.STOPPED)

    val diagnostics: StateFlow<NodeDiagnostics> = diagnosticsRepository.diagnostics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NodeDiagnostics.DEFAULT)

    val networkEvents: StateFlow<List<NetworkLogEvent>> = networkEventRepository.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val hasActivePeers: StateFlow<Boolean> = diagnosticsRepository.diagnostics
        .map { it.activePeerCount > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
}
```

### Détail : `ReliabilityGauge.kt`

```kotlin
// presentation/dashboard/components/ReliabilityGauge.kt
package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Seuils de couleur : Vert Terminal (> 60%), Ambre (> 30%), Rouge (≤ 30%)
private val colorSain = Color(0xFF00FF41)
private val colorAlerte = Color(0xFFFFB300)
private val colorCritique = Color(0xFFFF3333)
private val colorTrack = Color(0xFF333333)

@Composable
fun ReliabilityGauge(
    score: Float,             // 0.0–1.0
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val scorePercent = (score * 100).toInt().coerceIn(0, 100)
    val arcColor = when {
        score > 0.6f -> colorSain
        score > 0.3f -> colorAlerte
        else -> colorCritique
    }
    val sweepAngle = score * 270f  // Arc de 270° maximum

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            val startAngle = 135f
            // Arc de fond (gris technique)
            drawArc(
                color = colorTrack,
                startAngle = startAngle,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke
            )
            // Arc de valeur (coloré selon seuil)
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = stroke
            )
        }
        Text(
            text = "$scorePercent%",
            color = arcColor,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
```

### Détail : `KpiDiagnosticCard.kt`

```kotlin
// presentation/dashboard/components/KpiDiagnosticCard.kt
package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val borderColor = Color(0xFF333333)
private val labelColor = Color(0xFF9E9E9E)
private val dataColor = Color(0xFFE0E0E0)

@Composable
fun KpiDiagnosticCard(
    label: String,
    value: String,
    accentColor: Color = Color(0xFF00FF41),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .border(1.dp, borderColor)
            .padding(0.dp)
    ) {
        // Liseré coloré vertical gauche (état en un coup d'œil)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(IntrinsicSize.Min)
                .padding(vertical = 8.dp)
        ) {
            // Utiliser drawBehind ou background pour le liseré — Modifier.background(accentColor)
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 12.sp
            )
            Text(
                text = value,
                color = dataColor,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
```

### Détail : `RadarLogConsole.kt`

```kotlin
// presentation/dashboard/components/RadarLogConsole.kt
package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicloud.domain.models.NetworkLogEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val borderColor = Color(0xFF333333)
private val timestampColor = Color(0xFF9E9E9E)
private val messageColor = Color(0xFFE0E0E0)
private val activeColor = Color(0xFF00FF41)
private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun RadarLogConsole(
    events: List<NetworkLogEvent>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, borderColor)
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "RADAR LOG",
                color = activeColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            // Statut textuel statique — pas d'animation clignotante (économie batterie)
            Text(
                text = "[ACTIF]",
                color = activeColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        if (events.isEmpty()) {
            Text(
                text = "> EN ATTENTE D'ÉVÉNEMENTS RÉSEAU_",
                color = timestampColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            // reverseLayout = true → le plus récent est en haut (prepend dans NetworkEventRepositoryImpl)
            LazyColumn(
                modifier = Modifier.heightIn(max = 160.dp),
                reverseLayout = false  // déjà en ordre chronologique inversé (prepend)
            ) {
                items(events) { event ->
                    Row {
                        Text(
                            text = timeFormat.format(Date(event.timestampMs)) + " ",
                            color = timestampColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = event.message,
                            color = messageColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
```

### Détail : `DashboardScreen.kt` (mis à jour)

```kotlin
// presentation/dashboard/DashboardScreen.kt
package com.mobicloud.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.domain.models.NetworkType
import com.mobicloud.presentation.dashboard.components.KpiDiagnosticCard
import com.mobicloud.presentation.dashboard.components.RadarLogConsole
import com.mobicloud.presentation.dashboard.components.ReliabilityGauge
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
object DashboardRoute  // NE PAS MODIFIER — câblé dans la navigation Story 1.2

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val networkEvents by viewModel.networkEvents.collectAsStateWithLifecycle()
    val hasActivePeers by viewModel.hasActivePeers.collectAsStateWithLifecycle()

    val uptimeFormatted = formatUptime(diagnostics.uptimeMs)
    val networkLabel = when (diagnostics.networkType) {
        NetworkType.WIFI -> "Wifi"
        NetworkType.CELLULAR -> "4G"
        NetworkType.UNKNOWN -> "—"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Score de fiabilité central
        ReliabilityGauge(
            score = diagnostics.reliabilityScore,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Message si aucun pair détecté (AC #5)
        if (!hasActivePeers) {
            Text(
                text = "Aucun pair détecté — scan en cours...",
                color = Color(0xFFFFB300),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // KPI Cards — grille 2×2
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiDiagnosticCard(
                label = "BATTERIE",
                value = "${diagnostics.batteryPercent}%",
                modifier = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label = "UPTIME",
                value = uptimeFormatted,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiDiagnosticCard(
                label = "RÉSEAU",
                value = networkLabel,
                modifier = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label = "PAIRS ACTIFS",
                value = "${diagnostics.activePeerCount}",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))

        // Console de logs réseau
        RadarLogConsole(
            events = networkEvents,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatUptime(uptimeMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
    return "%02d:%02d".format(hours, minutes)
}
```

### Détail : Modules Hilt

```kotlin
// di/DiagnosticsModule.kt
package com.mobicloud.di

import com.mobicloud.data.repository.DiagnosticsRepositoryImpl
import com.mobicloud.domain.repository.DiagnosticsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds @Singleton
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository
}

// di/NetworkEventModule.kt
package com.mobicloud.di

import com.mobicloud.data.repository.NetworkEventRepositoryImpl
import com.mobicloud.domain.repository.NetworkEventRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkEventModule {
    @Binds @Singleton
    abstract fun bindNetworkEventRepository(impl: NetworkEventRepositoryImpl): NetworkEventRepository
}
```

### Détail : Events à pousser dans `MobicloudP2PService`

Ajouter `@Inject lateinit var networkEventRepository: NetworkEventRepository` dans le service, puis insérer des appels `pushEvent(...)` aux points suivants :

```kotlin
// Dans UdpHeartbeatReceiver ou Loop 2 (heartbeat reçu) :
networkEventRepository.pushEvent("[UDP] Heartbeat reçu de ${peer.identity.nodeId.take(8)}")

// Dans Loop 3 (eviction) — si un pair passe INACTIVE :
networkEventRepository.pushEvent("[PEER] ${nodeId.take(8)} → INACTIVE")

// Dans Loop 5 (Firebase announce — succès) :
networkEventRepository.pushEvent("[TRACKER] Enregistrement Firebase réussi")

// Dans Loop 5 (Firebase announce — échec) :
networkEventRepository.pushEvent("[TRACKER] Firebase indisponible — mode local")

// Dans Loop 4 (Firebase peer découvert) :
networkEventRepository.pushEvent("[FIREBASE] Pair distant : ${peer.identity.nodeId.take(8)}")

// Dans Loop 2 (TCP connexion établie) :
networkEventRepository.pushEvent("[TCP] Connexion avec ${nodeId.take(8)}")
```

### Spécifications UX Design (Source : ux-design-specification.md)

**Palette impérative :**
- Fond : `#000000` (OLED pur, aucune exception)
- `Success/Active` : `#00FF41` (Vert Terminal — score > 60%)
- `Warning` : `#FFB300` (Ambre — score 30–60%, message "Aucun pair")
- `Error/Critical` : `#FF3333` (Rouge — score < 30%)
- Bordures : `#333333`
- Texte primaire : `#E0E0E0`, secondaire : `#9E9E9E`

**Typographie :**
- Labels de KPI : 12sp `Roboto/Inter` (gris secondaire)
- Valeurs de KPI : 24sp `Roboto Mono / JetBrains Mono` (blanc cassé)
- Logs `RadarLogConsole` : 11sp monospace
- Score `ReliabilityGauge` : 20sp monospace

**Anti-Patterns interdits (UX Spec) :**
- ❌ Animations SVG continues (Lottie)
- ❌ Spinners/roues de chargement infinies
- ❌ Pastille clignotante animée dans RadarLogConsole (coûteuse en CPU)
- ❌ Ripple effects Material par défaut
- ❌ Fond autre que #000000 (flash blanc = défaut)

**`collectAsStateWithLifecycle()` obligatoire** — suspend la collecte UI quand l'écran est en arrière-plan (NFR-03 économie batterie).

### Learnings de la Story 2.3 (Prévention de Régressions)

- **Chemin `data/repository/` canonique** — Les nouveaux `RepositoryImpl` vont dans `data/repository/` (pas `data/repository_impl/` — dette pré-existante pour certains fichiers anciens).
- **`@Singleton` obligatoire** sur `NetworkEventRepositoryImpl` — si deux instances, les événements ne sont pas partagés entre `MobicloudP2PService` et le ViewModel.
- **`MutableStateFlow.update { }` atomique** — évite les race conditions sur le ring buffer des 50 événements. Ne pas faire `_events.value = _events.value.toMutableList().apply { ... }`.
- **`Result<T>` obligatoire** — `DiagnosticsRepositoryImpl.buildDiagnostics()` encapsule les appels Android dans `runCatching` ou gère les null proprement.
- **`Dispatchers.Default`** pour les calculs dans `DiagnosticsRepositoryImpl` (CPU léger, pas I/O).
- **`IdentityRepository.getIdentity()`** est une `suspend fun` — doit être appelée dans une coroutine (la coroutine de `DiagnosticsRepositoryImpl.init { scope.launch { ... } }` est appropriée).
- **`ITrustScoreProvider` dans `domain/repository/` (ancienne version)** : il existe aussi `domain/usecase/m01_discovery/ITrustScoreProvider.kt` — packages distincts, pas de conflit.
- **`@TestOnly` pour les mocks** — si `StaticMockTrustScore` est dans le source set de production, ajouter `@VisibleForTesting`.

### Références

- [Source: epics.md#Story 2.4] — Acceptance criteria complets
- [Source: architecture.md#Complete Project Directory Structure] — `presentation/dashboard/` avec composants
- [Source: architecture.md#Enforcement Guidelines] — `Dispatchers.Default`, `Result<T>`, Hilt `@Inject`
- [Source: ux-design-specification.md#Custom Components] — specs détaillées `ReliabilityGauge`, `KpiDiagnosticCard`, `RadarLogConsole`
- [Source: ux-design-specification.md#Color System] — palette `#00FF41`, `#FFB300`, `#FF3333`
- [Source: ux-design-specification.md#Anti-Patterns to Avoid] — pas d'animations continues
- [Source: Story 2.3 Dev Notes] — patterns `Result<T>`, `Dispatchers.Default`, `data/repository/` path, `@Singleton` Hilt
- [Source: DashboardViewModel.kt] — `serviceStatus` existant, pattern `stateIn`
- [Source: DashboardScreen.kt] — `DashboardRoute @Serializable`, `collectAsStateWithLifecycle`
- [Source: PeerRepository.kt] — `val peers: StateFlow<List<Peer>>` + `isActive`
- [Source: IdentityRepository.kt] — `suspend fun getIdentity(): Result<NodeIdentity>`
- [Source: MobicloudP2PService.kt] — structure des loops P2P, injection Hilt, `reliabilityScoreFlow`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created
- **Story 2.4 implémentée le 2026-04-16** par claude-sonnet-4-6 :
  - 3 modèles domaine Kotlin pur créés (NodeDiagnostics, NetworkType, NetworkLogEvent)
  - 2 interfaces domaine créées (DiagnosticsRepository, NetworkEventRepository)
  - DiagnosticsRepositoryImpl : rafraîchissement 5s via BatteryManager, SystemClock, ConnectivityManager + Dispatchers.Default
  - NetworkEventRepositoryImpl : ring buffer thread-safe max 50 via MutableStateFlow.update{}
  - 2 modules Hilt créés (DiagnosticsModule, NetworkEventModule)
  - MobicloudP2PService : injection + 6 points pushEvent (UDP, PEER eviction, FIREBASE, TRACKER, TCP)
  - DashboardViewModel : diagnostics + networkEvents + hasActivePeers StateFlows ajoutés
  - 3 composants Composable créés : ReliabilityGauge (Canvas), KpiDiagnosticCard, RadarLogConsole (LazyColumn)
  - DashboardScreen.kt : placeholder remplacé par UI complète, DashboardRoute @Serializable préservé
  - 12 tests unitaires : 6 NetworkEventRepositoryImplTest + 6 DashboardViewModelTest — tous PASS ✅
  - Bugs préexistants corrigés dans SignalingRepositoryImplTest (slot type, coEvery, Log mock, port Long)

### Review Findings

- [x] [Review][Patch] Scope coroutine de `DiagnosticsRepositoryImpl` jamais annulé — injecté via AppModule `CoroutineScope` [DiagnosticsRepositoryImpl.kt:34]
- [x] [Review][Patch] `SimpleDateFormat` non thread-safe — remplacé par `remember { SimpleDateFormat(...) }` dans le composable [RadarLogConsole.kt:29]
- [x] [Review][Dismiss] `RadarLogConsole` : `reverseLayout = true` inapplicable — prepend déjà en place, ajout aurait inversé l'affichage [RadarLogConsole.kt:68]
- [x] [Review][Patch] `[FIREBASE]` event message corrigé → `[FIREBASE] Pair distant découvert : ...` [MobicloudP2PService.kt:177]
- [x] [Review][Patch] `[TCP]` event message corrigé → `[TCP] Connexion établie avec ...` [MobicloudP2PService.kt:185]
- [x] [Review][Defer] `ConnectivityManager.activeNetwork` peut retourner données stale sur API 29+ [DiagnosticsRepositoryImpl.kt:56] — deferred, pattern pré-existant dans ReliabilityScoreProviderImpl
- [x] [Review][Defer] Eviction StateFlow race condition — `peers.value` snapshot potentiellement stale après `evictStalePeers` [MobicloudP2PService.kt:229] — deferred, limitation architecturale pré-existante
- [x] [Review][Defer] `connectionJobs` map croissance non bornée [MobicloudP2PService.kt:183] — deferred, bug pré-existant hors périmètre Story 2.4
- [x] [Review][Defer] `hasActivePeers` peut flasher false au retour en foreground (WhileSubscribed 5s) [DashboardViewModel.kt:35] — deferred, comportement intentionnel WhileSubscribed

### Change Log

- 2026-04-16 : Implémentation complète Story 2.4 — Dashboard Tactique (claude-sonnet-4-6)

### File List

**Fichiers créés :**
- `app/src/main/kotlin/com/mobicloud/domain/models/NetworkType.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/NodeDiagnostics.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/NetworkLogEvent.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/DiagnosticsRepository.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/NetworkEventRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/DiagnosticsRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/NetworkEventRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/DiagnosticsModule.kt`
- `app/src/main/kotlin/com/mobicloud/di/NetworkEventModule.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/components/ReliabilityGauge.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/components/KpiDiagnosticCard.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/components/RadarLogConsole.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/NetworkEventRepositoryImplTest.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/DashboardViewModelTest.kt`

**Fichiers modifiés :**
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt`
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt` (correction bugs préexistants)
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
