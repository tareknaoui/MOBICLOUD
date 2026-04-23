# Story 6.4: UI de Téléchargement & Notifications de Progression

Status: done

## Story

En tant qu'utilisateur,
Je veux voir la progression de mon téléchargement distribué en temps réel dans l'Explorer,
Afin de savoir combien de blocs ont été récupérés et depuis combien de nœuds.

## Acceptance Criteria

1. **Given** un téléchargement distribué est en cours
   **When** l'utilisateur consulte l'entrée du fichier dans l'Explorer
   **Then** un indicateur `DownloadProgressIndicator` s'affiche en haut de la liste, montrant une barre de progression déterminée avec le texte "⬇ X/K blocs" (ex: "⬇ 3/4 blocs")
   **And** chaque nœud contributeur ayant déjà livré un bloc est listé avec son `nodeId.take(4)` et sa latence mesurée (ex: "a3f2... 42ms")

2. **And** l'indicateur distingue visuellement les blocs data (couleur verte, comme `ErasureProgressIndicator`) des blocs parité (couleur ambre)
   **And** les blocs reçus avec succès sont colorés (data = `#00FF41`, parité = `#FFB300`), les blocs échoués en rouge (`#FF3333`), les blocs en attente en fond sombre

3. **And** si un nœud ne répond pas sous 5 secondes, il est marqué "⏳ Attente" dans la liste des nœuds
   **And** si un nœud de secours est utilisé (fallback `DownloadFileBlocksUseCase` AC#6 Story 6.2), il apparaît dans la liste avec le label "(secours)"

4. **And** après `DownloadState.Assembled`, un `ModalBottomSheet` s'ouvre automatiquement affichant :
   - "✓ Fichier récupéré en Xms — depuis Y nœuds"
   - Un bouton "Ouvrir" qui lance un `Intent.ACTION_VIEW` via `FileProvider` pour le `filePath`
   - Un bouton "Fermer" qui ferme le sheet et reset `downloadState` → `Idle`

5. **And** si le téléchargement échoue avec `DownloadProgressState.Failed` (raison contenant "blocs valides") ou `DownloadState.Error`, le Snackbar affiche "Fichier irrécupérable — trop peu de nœuds actifs" (message enrichi, pas le message d'erreur technique brut)

6. **And** pendant la phase `DownloadState.Decrypting`, l'indicateur montre une `LinearProgressIndicator` indéterminée avec le texte "🔓 Déchiffrement X/K blocs"

## Tasks / Subtasks

### 🔢 Bloc Données (Tasks 1–3) — enrichissement du modèle pour l'UI

- [x] **Task 1** : Ajouter `latencyMs: Long` à `DownloadedBlock` (AC: #1)
  - [x] Subtask 1.1 : Dans `domain/models/DownloadedBlock.kt`, ajouter `val latencyMs: Long = 0L` (valeur par défaut `0L` pour rétro-compatibilité + tests existants non cassés).
    Étendre `equals`/`hashCode` pour couvrir `latencyMs` (pattern déjà appliqué sur `ciphertext` et `iv` dans Story 6.3).
  - [x] Subtask 1.2 : Dans `data/p2p/tcp/BlockDownloadClient.kt`, mesurer le temps réseau total dans `downloadBlock()` :
    ```kotlin
    val startMs = System.currentTimeMillis()
    return@withContext withTimeout(timeoutMs) { doTransfer(socketRef, location) }
        .map { it.copy(latencyMs = System.currentTimeMillis() - startMs) }
    ```
    Le `.map {}` sur un `Result<DownloadedBlock>` propage le succès avec `latencyMs` peuplé. Les échecs ne sont pas affectés (`Result.failure` passe tel quel).

- [x] **Task 2** : Étendre `DownloadProgressState` avec contributions et nœuds lents (AC: #1, #2, #3)
  - [x] Subtask 2.1 : Modifier `domain/usecase/m08_m09_erasure_coding/DownloadProgressState.kt` :
    ```kotlin
    sealed class DownloadProgressState {

        data class BlockContribution(
            val nodeId: String,
            val fragmentIndex: Int,
            val latencyMs: Long,
            val isFallback: Boolean = false
        )

        data class Progress(
            val received: Int,
            val k: Int,
            val failed: Int,
            val contributions: List<BlockContribution> = emptyList(),
            val slowNodeIds: Set<String> = emptySet()
        ) : DownloadProgressState()

        data class Completed(val blocks: Map<Int, DownloadedBlock>) : DownloadProgressState()
        data class Failed(val reason: String, val received: Int, val k: Int) : DownloadProgressState()
    }
    ```
  - [x] Subtask 2.2 : Vérifier que les 2 consommateurs existants de `Progress(received, k, failed)` compilent encore :
    - `ExplorerViewModel.startDownload()` : accès positional → toujours OK (champs ajoutés en fin)
    - Tout test sur `DownloadProgressState.Progress` : adapter les constructions si nécessaires (les nouveaux champs ont des valeurs par défaut)

- [x] **Task 3** : Mettre à jour `DownloadFileBlocksUseCase` pour peupler contributions et slow nodes (AC: #1, #2, #3)
  - [x] Subtask 3.1 : Ajouter dans `DownloadFileBlocksUseCase` (variables locales au `channelFlow`) :
    ```kotlin
    val contributions = Collections.synchronizedList(mutableListOf<DownloadProgressState.BlockContribution>())
    val slowNodeIds = ConcurrentHashMap.newKeySet<String>()
    ```
    Import requis : `java.util.Collections`, `java.util.concurrent.ConcurrentHashMap` (déjà présent dans le fichier pour `usedNodeIds`).
  - [x] Subtask 3.2 : Pour chaque `launch` job dans `locations.map { loc -> launch { ... } }`, ajouter un timer slow **avant** le download :
    ```kotlin
    val slowJob = launch {
        delay(SLOW_THRESHOLD_MS)
        slowNodeIds.add(loc.nodeId)
    }
    ```
    Annuler `slowJob` dès que le download se termine (succès ou échec) : dans le `finally` du job :
    ```kotlin
    finally { slowJob.cancel() }
    ```
  - [x] Subtask 3.3 : Sur succès du download (`dr.result.onSuccess { block -> ... }`), ajouter la contribution :
    ```kotlin
    contributions.add(
        DownloadProgressState.BlockContribution(
            nodeId = /* nodeId utilisé — voir Subtask 3.4 */,
            fragmentIndex = block.fragmentIndex,
            latencyMs = block.latencyMs,
            isFallback = /* voir Subtask 3.4 */
        )
    )
    ```
  - [x] Subtask 3.4 : Exposer le `nodeId` et le flag `isFallback` du job : la `DownloadResult` privée est étendue :
    ```kotlin
    private data class DownloadResult(
        val fragmentIndex: Int,
        val result: Result<DownloadedBlock>,
        val nodeId: String = "",
        val isFallback: Boolean = false
    )
    ```
    Dans chaque `launch`, peupler `results.trySend(DownloadResult(loc.fragmentIndex, result, effectiveNodeId, isFallback))` où `effectiveNodeId` est `fallback.identity.nodeId` si fallback utilisé, sinon `loc.nodeId`.
  - [x] Subtask 3.5 : Inclure `contributions.toList()` et `slowNodeIds.toSet()` dans chaque `send(DownloadProgressState.Progress(...))` du receive loop.
  - [x] Subtask 3.6 : Ajouter la constante : `const val SLOW_THRESHOLD_MS = 5_000L` dans le `companion object`.

- [x] **Task 4** : Étendre `DownloadState` avec les nouveaux champs UI (AC: #1, #2, #3, #4)
  - [x] Subtask 4.1 : Modifier `presentation/explorer/DownloadState.kt` :
    ```kotlin
    data class Downloading(
        val fileHash: String,
        val received: Int,
        val k: Int,
        val failed: Int,
        val contributions: List<DownloadProgressState.BlockContribution> = emptyList(),  // NOUVEAU
        val slowNodeIds: Set<String> = emptySet()                                         // NOUVEAU
    ) : DownloadState()

    data class Assembled(
        val fileHash: String,
        val filePath: String,
        val durationMs: Long = 0L,  // NOUVEAU
        val nodeCount: Int = 0       // NOUVEAU
    ) : DownloadState()
    ```
    Import requis : `com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadProgressState`

### 🎛️ Bloc ViewModel (Task 5) — orchestration des états enrichis

- [x] **Task 5** : Mettre à jour `ExplorerViewModel.startDownload()` (AC: #1, #2, #3, #4)
  - [x] Subtask 5.1 : Ajouter un tracker de temps de départ dans `ExplorerViewModel` :
    ```kotlin
    private var downloadStartMs: Long = 0L
    ```
    Le setter est appelé dans `initiateDownload()` juste avant `_downloadState.value = DownloadState.Locating(fileHash)` :
    ```kotlin
    downloadStartMs = System.currentTimeMillis()
    ```
  - [x] Subtask 5.2 : Dans le `when (state)` de `downloadFileBlocksUseCase.invoke(...).collect { ... }`, étendre le cas `Progress` :
    ```kotlin
    is DownloadProgressState.Progress -> {
        Log.i("MobiCloud:DL", "fileHash=${fileHash.take(8)} progress=${state.received}/${state.k} failed=${state.failed}")
        _downloadState.value = DownloadState.Downloading(
            fileHash = fileHash,
            received = state.received,
            k = state.k,
            failed = state.failed,
            contributions = state.contributions,   // NOUVEAU
            slowNodeIds = state.slowNodeIds         // NOUVEAU
        )
    }
    ```
  - [x] Subtask 5.3 : Dans le branchement `AssembleProgress.Finalized` → `AssembleResult.Success`, calculer la durée et le nombre de nœuds uniques :
    ```kotlin
    is AssembleDownloadedFileUseCase.AssembleResult.Success -> {
        val durationMs = System.currentTimeMillis() - downloadStartMs
        val nodeCount = (_downloadState.value as? DownloadState.Downloading)
            ?.contributions?.map { it.nodeId }?.toSet()?.size ?: 0
        DownloadState.Assembled(fileHash, r.filePath, durationMs, nodeCount)
    }
    ```
  - [x] Subtask 5.4 : Ajouter un reset `downloadState → Idle` appelable depuis l'UI (pour le bouton "Fermer" du BottomSheet) :
    ```kotlin
    fun resetDownloadState() {
        downloadJob?.cancel()
        locateJob?.cancel()
        _downloadState.value = DownloadState.Idle
    }
    ```

### 🎨 Bloc UI (Tasks 6–8) — composants et intégration dans ExplorerScreen

- [x] **Task 6** : Créer `DownloadProgressIndicator` (AC: #1, #2, #3, #6)
  - [x] Subtask 6.1 : Créer `presentation/explorer/components/DownloadProgressIndicator.kt`.
    Ce composable suit le patron visuel de `ErasureProgressIndicator` (thème OLED noir, texte monospace, couleurs identiques) :
    ```kotlin
    @Composable
    fun DownloadProgressIndicator(
        state: DownloadState,
        modifier: Modifier = Modifier
    )
    ```
    N'affiche rien si `state` n'est pas `Downloading` ou `Decrypting` (renvoyer `Unit` sinon).
  - [x] Subtask 6.2 : Cas `DownloadState.Downloading` — afficher :
    - Ligne titre : `"⬇ ${state.received}/${state.k} blocs"` (color `#E0E0E0`, monospace)
    - `LinearProgressIndicator(progress = { state.received.toFloat() / state.k.coerceAtLeast(1) }, color = #00FF41, trackColor = #1A1A1A)`
    - Grille de blocs (même pattern que `ErasureProgressIndicator.Distributing`) : K+N cases colorées par fragmentIndex — données reçues : vert, parité reçue : ambre, échoué : rouge, en attente data : `#0D2B0D`, en attente parité : `#2B2000`. Le `fragmentIndex` de chaque `contribution` determine la case colorée.
    - Liste des nœuds contributeurs (si `contributions` non vide) : pour chaque contribution unique par `nodeId`, afficher `"${nodeId.take(4)}... ${latencyMs}ms${if (isFallback) " (secours)" else ""}"`. Couleur : `#9E9E9E`.
    - Nœuds lents (dans `slowNodeIds`) : afficher `"⏳ ${nodeId.take(4)}... Attente"` en couleur `#FFB300`.
  - [x] Subtask 6.3 : Cas `DownloadState.Decrypting` — afficher :
    - Ligne titre : `"🔓 Déchiffrement ${state.processed}/${state.k} blocs"` (color `#E0E0E0`, monospace)
    - `LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = #00FF41, trackColor = #1A1A1A)` — indéterminé (pas de `progress` lambda).

- [x] **Task 7** : Créer `AssembledBottomSheet` (AC: #4)
  - [x] Subtask 7.1 : Créer `presentation/explorer/components/AssembledBottomSheet.kt`.
    ```kotlin
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AssembledBottomSheet(
        state: DownloadState.Assembled,
        onOpen: (String) -> Unit,   // callback avec filePath
        onDismiss: () -> Unit,
        sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    )
    ```
    Utiliser `ModalBottomSheet` (Material3 `@ExperimentalMaterial3Api`). Contenu :
    - Texte principal : `"✓ Fichier récupéré en ${state.durationMs}ms — depuis ${state.nodeCount} nœud${if (state.nodeCount > 1) "s" else ""}"` (couleur `#00FF41`, monospace, fontSize 14sp)
    - Texte secondaire : `state.filePath.takeLast(40)` (couleur `#9E9E9E`, monospace, fontSize 11sp)
    - Row avec deux boutons :
      - `Button("Ouvrir")` → appelle `onOpen(state.filePath)` puis `onDismiss()`
      - `TextButton("Fermer")` → appelle `onDismiss()`
  - [x] Subtask 7.2 : Dans le callback `onOpen(filePath)` (géré dans `ExplorerScreen`), ouvrir le fichier via Intent :
    ```kotlin
    // minSdk = 24 → OBLIGATOIRE d'utiliser FileProvider (Uri.fromFile lève FileUriExposedException)
    val file = File(filePath)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, getMimeType(filePath))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(intent) }
    catch (e: ActivityNotFoundException) { /* Snackbar "Aucune application pour ouvrir ce fichier" */ }
    ```
    `getMimeType(filePath)` = `MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(filePath).extension) ?: "application/octet-stream"`.
  - [x] Subtask 7.3 : Déclarer le `FileProvider` dans `app/src/main/AndroidManifest.xml` (au sein de `<application>`) si absent :
    ```xml
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_provider_paths" />
    </provider>
    ```
  - [x] Subtask 7.4 : Créer `app/src/main/res/xml/file_provider_paths.xml` :
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <paths>
        <external-files-path name="downloads" path="Downloads/" />
        <files-path name="internal_files" path="." />
    </paths>
    ```
    Couvre `context.getExternalFilesDir(DIRECTORY_DOWNLOADS)` (chemin primaire Story 6.3) et `context.filesDir` (fallback Story 6.3).

- [x] **Task 8** : Mettre à jour `ExplorerScreen` (AC: #1, #4, #5, #6)
  - [x] Subtask 8.1 : Ajouter l'affichage du `DownloadProgressIndicator` dans le `Column` principal, entre `ErasureProgressIndicator` et `PullToRefreshBox` :
    ```kotlin
    val inProgressDownloadState = downloadState.takeIf {
        it is DownloadState.Downloading || it is DownloadState.Decrypting
    }
    if (inProgressDownloadState != null) {
        DownloadProgressIndicator(
            state = inProgressDownloadState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    ```
  - [x] Subtask 8.2 : Remplacer le `LaunchedEffect(terminalDownloadState)` du Snackbar `Assembled` par un contrôle de `showAssembledSheet` :
    ```kotlin
    val assembledState = downloadState as? DownloadState.Assembled
    if (assembledState != null) {
        AssembledBottomSheet(
            state = assembledState,
            onOpen = { filePath ->
                val file = File(filePath)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                    ?: "application/octet-stream"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(intent) }
                catch (e: ActivityNotFoundException) {
                    scope.launch { snackbarHostState.showSnackbar("Aucune application pour ouvrir ce fichier") }
                }
            },
            onDismiss = { viewModel.resetDownloadState() }
        )
    }
    ```
    Import requis : `android.webkit.MimeTypeMap`, `androidx.core.content.FileProvider`, `java.io.File`.
    `context` = `LocalContext.current`, `scope` = `rememberCoroutineScope()`.
  - [x] Subtask 8.3 : Modifier le `LaunchedEffect(terminalDownloadState)` existant (Story 6.3) : **supprimer** le cas `Assembled` (maintenant géré par le BottomSheet) et enrichir le cas `Error` :
    ```kotlin
    val terminalDownloadState = remember(downloadState) {
        downloadState.takeIf { it is DownloadState.Error }
    }
    LaunchedEffect(terminalDownloadState) {
        val s = terminalDownloadState as? DownloadState.Error ?: return@LaunchedEffect
        val friendlyMessage = if (s.message.contains("blocs valides") || s.message.contains("nœuds actifs"))
            "Fichier irrécupérable — trop peu de nœuds actifs"
        else
            "Erreur : ${s.message}"
        snackbarHostState.showSnackbar(friendlyMessage)
    }
    ```

### 🧪 Bloc Tests (Task 9)

- [x] **Task 9** : Tests JVM pour le ViewModel et les utilitaires (AC: #1, #3, #4)
  - [x] Subtask 9.1 : Dans `ExplorerViewModelTest.kt` (s'il existe — sinon créer), ajouter :
    - **Test 1 — downloadStartMs** : vérifier que `_downloadState.value as Assembled` contient `durationMs > 0` après un cycle `Locating → Downloading → Assembled` simulé.
    - **Test 2 — contributions tracking** : peupler un `DownloadProgressState.Progress` avec 2 contributions mockées → vérifier que `DownloadState.Downloading.contributions.size == 2`.
    - **Test 3 — slowNodeIds** : peupler `Progress(slowNodeIds = setOf("abc"))` → vérifier que `DownloadState.Downloading.slowNodeIds.contains("abc")`.
    - **Test 4 — resetDownloadState** : appeler `resetDownloadState()` → `_downloadState.value == Idle`.
  - [x] Subtask 9.2 : Tests JVM pour `DownloadFileBlocksUseCase` — slow detection :
    - Utiliser `TestCoroutineScheduler` (kotlinx-coroutines-test) pour avancer le temps de `6_000ms` sans attendre.
    - Vérifier qu'après `delay(6_000)` simulé, un `Progress` avec `slowNodeIds.contains(loc.nodeId)` est émis.
    - Vérifier que si le download réussit avant `5_000ms`, `slowNodeIds` ne contient PAS ce nodeId.

---

### Review Findings

- [x] [Review][Decision] `file_provider_paths.xml` — `path="."` expose tout filesDir via FileProvider — décision: Option A conservée (écriture dans la racine filesDir, restrict impossible sans modifier AssembleDownloadedFileUseCase hors scope PFE) — La spec prescrit `path="."` pour couvrir le fallback `context.filesDir`, mais cela expose l'intégralité du répertoire (Room databases, SharedPreferences, clés). Il faut connaître le chemin exact de sortie de `AssembleDownloadedFileUseCase` pour restreindre le path. Options : (a) restreindre à un sous-dossier dédié si AssembleDownloadedFileUseCase écrit dans `filesDir/Downloads/`, (b) maintenir `path="."` en acceptant le risque (MVP/PFE), (c) migrer le chemin de sortie du fichier reconstruit vers `getExternalFilesDir` uniquement. [app/src/main/res/xml/file_provider_paths.xml]
- [x] [Review][Decision] `AssembledBottomSheet` — `onOpen` appelle systématiquement `onDismiss()` après l'Intent, même si `ActivityNotFoundException` est levée — décision: Option B appliquée (`onDismiss()` retiré du bouton "Ouvrir", reset uniquement après startActivity réussi) — Résultat : l'état `Assembled` est perdu et le BottomSheet est fermé même quand l'ouverture échoue, empêchant tout réessai. Options : (a) Déplacer `onDismiss()` dans le callback `onOpen` uniquement en cas de succès (ne l'appeler que si `startActivity` ne lève pas d'exception), (b) maintenir le comportement actuel (reset systématique, simplicité). [app/src/main/kotlin/com/mobicloud/presentation/explorer/components/AssembledBottomSheet.kt:63-65]
- [x] [Review][Patch] `failedFragments = emptySet<Int>()` — blocs échoués jamais colorés en rouge (AC#2 non conforme) — corrigé: ajout `failedFragmentIndices: Set<Int>` dans Progress, Downloading, DownloadFileBlocksUseCase et DownloadProgressIndicator — La variable est hardcodée vide et la branche `Color(0xFFFF3333)` ne s'exécute jamais. Correction : ajouter `failedFragmentIndices: Set<Int> = emptySet()` dans `DownloadState.Downloading` et `DownloadProgressState.Progress`, peupler depuis `DownloadFileBlocksUseCase` sur chaque `onFailure` (enregistrer le `fragmentIndex` qui échoue), et passer la valeur jusqu'à `DownloadProgressIndicator`. [app/src/main/kotlin/com/mobicloud/presentation/explorer/components/DownloadProgressIndicator.kt:63]
- [x] [Review][Patch] `nodeCount` toujours 0 dans `AssembledBottomSheet` (AC#4) — corrigé: ajout `lastNodeCount: Int` dans ViewModel, mis à jour à chaque Progress, utilisé au moment de Assembled — Au moment du calcul `(_downloadState.value as? DownloadState.Downloading)?.contributions...`, l'état vient d'être mis à `Decrypting` ; le cast retourne null et `nodeCount = 0`. Correction : capturer `nodeCount` juste avant la transition `Downloading → Decrypting` (stocker dans un champ ViewModel `private var lastNodeCount: Int = 0` mis à jour dans le branchement `Progress`), ou l'inclure dans `DownloadState.Decrypting`. [app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt:162-163]
- [x] [Review][Patch] `downloadStartMs` sans `@Volatile` — corrigé: annoté `@Volatile` — visibilité cross-thread non garantie — Le champ est écrit depuis `initiateDownload()` (main thread) et lu depuis le lambda `collect` qui tourne sur un dispatcher IO/Default. Sur ARM, la JVM ne garantit pas la visibilité sans barrière mémoire. Correction : annoter `@Volatile private var downloadStartMs: Long = 0L` ou utiliser `AtomicLong`. [app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt:595]
- [x] [Review][Patch] Condition `"blocs valides"` dans `ExplorerScreen` — faux positif: `DownloadFileBlocksUseCase` émet bien `"Seulement X/K blocs valides"` (ligne 176), la condition est correcte. Dismissed. — Les messages émis par le use case sont "Insuffisant : X/K blocs localisés" et "k invalide : K", aucun ne contient "blocs valides". La condition `s.message.contains("blocs valides")` ne s'active jamais ; l'else branch affiche toujours `"Erreur : Insuffisant : ..."` au lieu du message enrichi. Correction : aligner la condition de détection avec les messages réels (ex : `.contains("Insuffisant")` ou `.contains("blocs localisés")`). [app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt:509-511]
- [x] [Review][Defer] Fallback peers : snapshot `activePeers` potentiellement périmé [DownloadFileBlocksUseCase.kt:258] — deferred, pre-existing (Story 6.2 — pattern prescrit par la spec)
- [x] [Review][Defer] Aucun timeout sur `assembleDownloadedFileUseCase.collect` [ExplorerViewModel.kt:135-170] — deferred, design transverse (périmètre d'une story de résilience future)
- [x] [Review][Defer] Race `initiateDownload()` : callback d'un `locateJob` annulé peut encore émettre `Located` avant la prise d'effet du cancel [ExplorerViewModel.kt:96-103] — deferred, impact limité (le dernier état écrit gagne, pattern commun sous Kotlin coroutines)

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Description | Action |
|---|---|---|
| `presentation/explorer/components/ErasureProgressIndicator.kt` | Pattern visuel upload (OLED, blocs colorés, LinearProgressIndicator) | **COPIER LE STYLE** — même couleurs, même fonts, même Surface/Border |
| `presentation/explorer/DownloadState.kt` | States `Idle/Locating/Located/Downloading/Decrypting/Assembled/Error` | **MODIFIER** (Task 4) |
| `presentation/explorer/ExplorerViewModel.kt` | `initiateDownload()`, `startDownload()`, `resetJob`, `downloadJob`, `locateJob` | **MODIFIER** (Task 5) |
| `presentation/explorer/ExplorerScreen.kt` | `terminalDownloadState` LaunchedEffect Snackbar Story 6.3 | **MODIFIER** (Task 8) — supprimer cas Assembled, enrichir Error |
| `domain/models/DownloadedBlock.kt` | `blockId, fragmentIndex, isParity, ciphertext, iv` + `equals`/`hashCode` | **MODIFIER** (Task 1) — ajouter `latencyMs` |
| `data/p2p/tcp/BlockDownloadClient.kt` | `downloadBlock()` → `doTransfer()` → `Result<DownloadedBlock>` | **MODIFIER** (Task 1) — mesurer latence |
| `domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCase.kt` | Orchestrateur K+2, `Channel<DownloadResult>`, `coroutineScope` | **MODIFIER** (Task 3) |
| `domain/usecase/m08_m09_erasure_coding/DownloadProgressState.kt` | `Progress/Completed/Failed` | **MODIFIER** (Task 2) |
| `presentation/explorer/components/CatalogEntryCard.kt` | Card de chaque entrée avec bouton "↓" | **NE PAS MODIFIER** |
| `domain/usecase/m08_m09_erasure_coding/AssembleDownloadedFileUseCase.kt` | Pipeline déchiffrement/réassemblage Story 6.3 | **NE PAS MODIFIER** |
| `domain/models/ErasureParameters.kt` | `k=4, n=2` | **NE PAS MODIFIER** |

### ⚠️ CONTRAINTES CRITIQUES

**1. minSdk = 24 → FileProvider OBLIGATOIRE pour Intent.ACTION_VIEW**
`Uri.fromFile()` lève `FileUriExposedException` sur Android 7.0+ (API 24+). L'utilisation de `FileProvider.getUriForFile()` avec `FLAG_GRANT_READ_URI_PERMISSION` est la seule approche valide. Le `<provider>` dans le Manifest est nécessaire — si absent, l'app crashe à l'exécution. Cf. chemin de sortie Story 6.3 : `context.getExternalFilesDir(DIRECTORY_DOWNLOADS)/mobicloud_${fileHash.take(16)}` — toujours sous `external-files-path` dans `file_provider_paths.xml`. Le fallback `context.filesDir` est couvert par `files-path`.

**2. `DownloadFileBlocksUseCase` : synchronisation du slow timer**
Chaque `slowJob` est lancé dans le même `coroutineScope` que le job principal. Son `cancel()` dans le `finally` est essentiel pour éviter des émissions orphelines après `completed.size >= k` et l'annulation des jobs perdants (`jobs.forEach { it.cancel() }`). Le cancel du `coroutineScope` en fin de bloc annule aussi tous les `slowJob` restants — aucune fuite de coroutines possible.

**3. Thread-safety de `contributions` et `slowNodeIds`**
- `contributions` : `Collections.synchronizedList(mutableListOf())` — les `add()` depuis les `launch` parallèles sont thread-safe. Le `toList()` sur le thread principal (receive loop) produit un snapshot cohérent.
- `slowNodeIds` : `ConcurrentHashMap.newKeySet()` — déjà utilisé dans `DownloadFileBlocksUseCase` pour `usedNodeIds` (Story 6.2). Pattern identique.
- Ne pas utiliser `mutableListOf()` nu pour `contributions` — race condition garantie entre les `async` blocs parallèles.

**4. `ModalBottomSheet` + `@ExperimentalMaterial3Api`**
Le `ModalBottomSheet` de Material3 est encore annoté `@ExperimentalMaterial3Api` (même version que le projet). L'annotation `@OptIn(ExperimentalMaterial3Api::class)` est requise sur le composable. Pattern identique à `ErasureProgressIndicator` qui utilise `Surface` sans annotation expérimentale — la `ModalBottomSheet` est la seule exception ici.

**5. Pas de modification de `AssembleDownloadedFileUseCase` ni `DecodeErasureFragmentsUseCase`**
Zéro régression Stories 6.3 / 5.1. La propagation de `latencyMs` dans `DownloadedBlock` passe de `BlockDownloadClient` → `DownloadProgressState.Progress.contributions` → ViewModel sans toucher au pipeline de déchiffrement. `AssembleDownloadedFileUseCase` n'utilise pas `latencyMs`.

**6. ErasureParameters().k = 4, n = 2 (K total = 6 blocs)**
Le pool K+2 = 6 blocs max. La grille de `DownloadProgressIndicator` doit allouer `k + n` cases (4 data + 2 parité = 6). Utiliser `ErasureParameters().run { k + n }` pour la taille dynamique, cohérent avec `ErasureProgressIndicator.Distributing` qui utilise `state.total` (= `params.k + params.n`).

**7. `latencyMs` dans `BlockDownloadClient` : mesure wall-clock totale**
La mesure commence avant `Socket().connect()` (inclus dans `withTimeout`) et se termine après `Result.success(DownloadedBlock(...))`. Elle couvre : connexion TCP + envoi requête + réception réponse + vérification SHA-256. C'est la latence "perçue" par l'utilisateur, cohérente avec la description UX "a3f2... 42ms". Ne pas mesurer seulement le `doTransfer` pour éviter d'ignorer le temps de connexion.

**8. Gestion de `ActivityNotFoundException` pour l'Intent "Ouvrir"**
Sur certains appareils Android en mode restreint, aucune application ne sait ouvrir les fichiers `.bin` (ciphertext). `context.startActivity(intent)` peut lever `ActivityNotFoundException`. L'attraper et afficher un Snackbar "Aucune application pour ouvrir ce fichier" est la seule UX correcte — ne pas laisser crasher.

**9. Reset `downloadState → Idle` après dismiss du BottomSheet**
`viewModel.resetDownloadState()` doit annuler `downloadJob` et `locateJob` (via `?.cancel()`) avant de setter `Idle`. Sinon un pipeline en cours continuerait à émettre des états orphelins sur `_downloadState`. Le pattern est cohérent avec la preemption déjà implementée dans `initiateDownload()`.

**10. Compatibilité `terminalDownloadState` refactored**
Story 6.3 utilise `remember(downloadState) { downloadState.takeIf { it is Assembled || it is Error } }`. Story 6.4 change ce `remember` pour ne retenir que `Error`. Supprimer le `Assembled` de cette clé évite d'afficher à la fois le BottomSheet ET un Snackbar résiduel "Fichier reconstruit : /path".

### 📁 Arborescence cible après implémentation

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   ├── models/
│   │   └── DownloadedBlock.kt                              ← MODIFIÉ (+ latencyMs)
│   └── usecase/m08_m09_erasure_coding/
│       ├── DownloadProgressState.kt                        ← MODIFIÉ (+ BlockContribution, contributions, slowNodeIds)
│       └── DownloadFileBlocksUseCase.kt                    ← MODIFIÉ (+ slow timer, contributions tracking)
├── data/p2p/tcp/
│   └── BlockDownloadClient.kt                              ← MODIFIÉ (mesure latence)
└── presentation/explorer/
    ├── DownloadState.kt                                    ← MODIFIÉ (+ contributions, slowNodeIds, durationMs, nodeCount)
    ├── ExplorerViewModel.kt                                ← MODIFIÉ (+ downloadStartMs, resetDownloadState)
    ├── ExplorerScreen.kt                                   ← MODIFIÉ (BottomSheet, DownloadProgressIndicator, Error msg)
    └── components/
        ├── DownloadProgressIndicator.kt                    ← NOUVEAU
        └── AssembledBottomSheet.kt                         ← NOUVEAU

app/src/main/
├── AndroidManifest.xml                                     ← MODIFIÉ (+ FileProvider <provider>)
└── res/xml/file_provider_paths.xml                         ← NOUVEAU
```

### 🔗 Références

- `ErasureProgressIndicator.kt` — patron visuel à copier (couleurs, Surface OLED, monospace)
- `ExplorerViewModel.startDownload()` l.115-171 — point d'insertion Tasks 5.2 et 5.3
- `DownloadFileBlocksUseCase.kt` l.82-142 — boucle receive à enrichir (Task 3)
- Story 6.3 Dev Notes §6 — pattern `Dispatchers.Default` / `Dispatchers.IO`
- Story 6.2 Dev Notes §7 — pattern `ConcurrentHashMap.newKeySet()` pour `usedNodeIds`
- Story 6.3 Subtask 10.2 — note explicite : "périmètre explicite de Story 6.4" pour l'UI Decrypting

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Tasks 1–9 complétées le 2026-04-23. Compilation sans erreur (`BUILD SUCCESSFUL`). Tests Story 6.4 tous passants (ExplorerViewModelTest ×4 nouveaux, DownloadFileBlocksUseCaseTest ×3 nouveaux). Les 4 échecs `ErasureProgressViewModelTest` sont pré-existants (Story 6.3) et hors périmètre.
- `latencyMs` mesuré comme wall-clock total (connexion + transfert) dans `BlockDownloadClient`, propagé via `DownloadResult` → `DownloadProgressState.BlockContribution` → `DownloadState.Downloading`.
- `SLOW_THRESHOLD_MS = 5_000L` : chaque job lance un `slowJob` annulé dans `finally` — aucune fuite de coroutines.
- `FileProvider` déclaré dans le Manifest + `file_provider_paths.xml` couvrant `external-files-path` (Downloads) et `files-path` (fallback).
- `AssembledBottomSheet` remplace le Snackbar `Assembled` de Story 6.3 ; `terminalDownloadState` ne retient plus que `Error`.
- `resetDownloadState()` annule `downloadJob` et `locateJob` avant de passer à `Idle` — cohérent avec la preemption de `initiateDownload`.

### File List

- `app/src/main/kotlin/com/mobicloud/domain/models/DownloadedBlock.kt` (modifié — +latencyMs)
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockDownloadClient.kt` (modifié — mesure latence wall-clock)
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DownloadProgressState.kt` (modifié — +BlockContribution, contributions, slowNodeIds)
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCase.kt` (modifié — slow timer, contributions tracking, DownloadResult étendu)
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/DownloadState.kt` (modifié — +contributions, slowNodeIds, durationMs, nodeCount)
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt` (modifié — +downloadStartMs, resetDownloadState, Assembled enrichi)
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt` (modifié — BottomSheet, DownloadProgressIndicator, Error message enrichi)
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/DownloadProgressIndicator.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/AssembledBottomSheet.kt` (nouveau)
- `app/src/main/AndroidManifest.xml` (modifié — +FileProvider)
- `app/src/main/res/xml/file_provider_paths.xml` (nouveau)
- `app/src/test/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModelTest.kt` (modifié — +4 tests Story 6.4)
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DownloadFileBlocksUseCaseTest.kt` (modifié — +3 tests slow detection + contributions)

## Change Log

- 2026-04-23 — Story 6.4 implémentée : UI de téléchargement distribué avec DownloadProgressIndicator (barre de progression, grille de blocs colorés, liste des nœuds avec latence, détection nœuds lents), AssembledBottomSheet (boutons Ouvrir/Fermer via FileProvider), enrichissement du pipeline avec latencyMs/contributions/slowNodeIds, message d'erreur enrichi.
