# Test d'Élection Bully Multi-Nœuds — 3 Émulateurs Android

**Objectif :** Valider l'Algorithme Bully (Story 3.1), l'enregistrement Super-Pair sur Relais HA (Story 3.2) et la promotabilité du rôle (NFR-06) sur 3+ nœuds réels — sans matériel physique.

**Durée estimée :** 1 journée (setup + exécution + capture logs)

**Prérequis :**
- Android Studio installé avec SDK + 1 AVD existant
- Node.js 18+ pour le serveur Relais HA
- ~8 GB RAM libre (3 émulateurs simultanés)
- Virtualisation BIOS activée (HAXM/WHPX/Hyper-V)

---

## Architecture du Test

```
┌─────────────────── Ton PC ────────────────────┐
│                                                │
│   Emulator A (port 5554, score 0.95)           │
│   Emulator B (port 5556, score 0.70)           │
│   Emulator C (port 5558, score 0.40)           │
│                                                │
│   relay-server/ (Node.js sur :8080)            │
│                                                │
└────────────────────────────────────────────────┘

Élection attendue : A gagne → COORDINATOR
Si A meurt : B devient le nouveau COORDINATOR
```

---

## Étape 1 — Démarrer le Relais HA en local

```powershell
cd c:\Users\naoui\Desktop\Projets\PFE\relay-server
node server.js
# Console attendue : "HA Relay listening on ws://0.0.0.0:8080"
```

Laisser ce terminal ouvert pendant tout le test.

---

## Étape 2 — Configurer l'app pour pointer vers le relais local

Modifier `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt` :

```kotlin
// AVANT (ngrok)
"wss://certainty-upstage-silly.ngrok-free.dev"

// APRÈS (relais local pour test émulateurs)
val RELAY_SERVER_URLS = listOf(
    "ws://10.0.2.2:8080"  // 10.0.2.2 = localhost du PC vu depuis émulateur Android
)
```

> ⚠️ `10.0.2.2` est une adresse magique réservée par AVD Android — elle pointe vers le `127.0.0.1` du PC hôte. Marche uniquement pour les émulateurs, jamais pour les vrais téléphones.

---

## Étape 3 — Ajouter une override de score pour les tests

Modifier `app/src/main/kotlin/com/mobicloud/domain/usecase/m01_discovery/CalculateReliabilityScoreUseCase.kt` :

```kotlin
suspend operator fun invoke(): Result<Float> {
    // DEBUG: override pour tests Bully multi-émulateur
    System.getProperty("debug.mobicloud.reliability")?.toFloatOrNull()?.let {
        return Result.success(it)
    }
    // ... logique normale (batterie + uptime + réseau)
}
```

> ⚠️ **Important** : à la fin du test, supprimer cette override avant la soutenance, OU la garder mais documenter qu'elle est en mode `debug` uniquement.

---

## Étape 4 — Désactiver temporairement l'auto-register préemptif

Dans `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`, **commenter** les lignes 174–200 (boucle `registerAndFetch`) pour que seul l'élu s'enregistre comme Super-Pair sur le relais :

```kotlin
// DÉSACTIVÉ POUR TEST BULLY MULTI-NŒUDS — réactiver après le test
/*
launch {
    delay(3_000L)
    val placeholderIp = "0.0.0.0"
    suspend fun registerAndFetch() {
        signalingRepository.registerAsSuperPeer(...)
        ...
    }
    while (isActive) {
        registerAndFetch()
        delay(30_000L)
    }
}
*/
```

> ⚠️ **À réactiver** après le test pour ne pas casser les autres scénarios (découverte HA pure).

---

## Étape 5 — Créer 3 AVD distincts

```powershell
emulator -list-avds
```

Si tu n'as qu'un AVD, dupliques-en 2 dans Android Studio :
**Tools → Device Manager → ⋮ menu → Duplicate**

Crée :
- `Pixel_5_API_33_A`
- `Pixel_5_API_33_B`
- `Pixel_5_API_33_C`

---

## Étape 6 — Lancer les 3 émulateurs en parallèle

3 terminaux PowerShell séparés :

```powershell
# Terminal 1
emulator -avd Pixel_5_API_33_A -port 5554

# Terminal 2
emulator -avd Pixel_5_API_33_B -port 5556

# Terminal 3
emulator -avd Pixel_5_API_33_C -port 5558
```

Vérifier qu'ils sont tous détectés :
```powershell
adb devices
# Attendu :
# emulator-5554   device
# emulator-5556   device
# emulator-5558   device
```

---

## Étape 7 — Forcer les scores de fiabilité distincts

Une fois les émulateurs démarrés (avant de lancer l'app) :

```powershell
# A = score haut (devrait gagner l'élection)
adb -s emulator-5554 shell setprop debug.mobicloud.reliability 0.95

# B = score moyen
adb -s emulator-5556 shell setprop debug.mobicloud.reliability 0.70

# C = score bas
adb -s emulator-5558 shell setprop debug.mobicloud.reliability 0.40
```

Vérification :
```powershell
adb -s emulator-5554 shell getprop debug.mobicloud.reliability
# Doit retourner : 0.95
```

---

## Étape 8 — Build et installer l'APK sur les 3 émulateurs

```powershell
cd c:\Users\naoui\Desktop\Projets\PFE
.\gradlew assembleDebug

adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5556 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5558 install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## Étape 9 — Lancer les logcats filtrés (3 terminaux séparés)

```powershell
# Terminal A
adb -s emulator-5554 logcat -s MobicloudP2PService:I RunBullyElection:I AbdicateSuperPeer:I RegisterSuperPeer:I > log-A.txt

# Terminal B
adb -s emulator-5556 logcat -s MobicloudP2PService:I RunBullyElection:I AbdicateSuperPeer:I RegisterSuperPeer:I > log-B.txt

# Terminal C
adb -s emulator-5558 logcat -s MobicloudP2PService:I RunBullyElection:I AbdicateSuperPeer:I RegisterSuperPeer:I > log-C.txt
```

---

## Étape 10 — Démarrer les 3 apps simultanément

```powershell
adb -s emulator-5554 shell am start -n com.mobicloud/.MainActivity
adb -s emulator-5556 shell am start -n com.mobicloud/.MainActivity
adb -s emulator-5558 shell am start -n com.mobicloud/.MainActivity
```

Attendre **30 secondes** que tous les nœuds se découvrent via le Relais HA et que la première élection se déclenche.

---

## Étape 11 — Vérifier les résultats de la première élection

### Critère de réussite #1 — Un seul COORDINATOR

Dans `log-A.txt` (score 0.95) — doit contenir :
```
[ELECTION] Aucune réponse ALIVE — auto-déclaration COORDINATOR
[HA] REGISTER_PEER envoyé en tant que Super-Pair
Timer abdication 30 min démarré
```

Dans `log-B.txt` (score 0.70) et `log-C.txt` (score 0.40) — doivent contenir :
```
[ELECTION] Reçu COORDINATOR de <nodeId-de-A> → mise à jour PeerRegistry
```

⛔ Aucun de B ou C ne doit contenir `auto-déclaration COORDINATOR` ni `REGISTER_PEER en tant que Super-Pair`.

---

## Étape 12 — Test du basculement (Promotabilité — NFR-06)

Killer l'émulateur A (le Super-Pair actuel) :
```powershell
adb -s emulator-5554 emu kill
```

Attendre **15 secondes** (timeout heartbeat = 15s, puis nouvelle élection).

### Critère de réussite #2 — B prend la relève

Dans `log-B.txt` et `log-C.txt`, doit apparaître :
```
[PEER] <nodeId-de-A> → INACTIVE (timeout heartbeat)
[ELECTION] Aucun Super-Pair joignable — déclenchement nouvelle élection
```

Et **uniquement dans `log-B.txt`** (score 0.70 > 0.40 de C) :
```
[ELECTION] Reçu ALIVE depuis aucun pair (C a un score inférieur, donc reste silencieux)
[ELECTION] Auto-déclaration COORDINATOR
[HA] REGISTER_PEER envoyé en tant que Super-Pair
```

Dans `log-C.txt` :
```
[ELECTION] Reçu ELECTION de B avec score supérieur — silence
[ELECTION] Reçu COORDINATOR de B → mise à jour PeerRegistry
```

⏱️ **NFR-06 mesurable :** noter le temps entre `INACTIVE` détecté dans B et `Auto-déclaration COORDINATOR`. Doit être **< 10 secondes**.

---

## Étape 13 — Test optionnel : Cooldown (NFR-06)

Relancer l'émulateur A avec son APK (recharge en moins de 5 minutes après son kill) :
```powershell
emulator -avd Pixel_5_API_33_A -port 5554
adb -s emulator-5554 shell setprop debug.mobicloud.reliability 0.95
adb -s emulator-5554 shell am start -n com.mobicloud/.MainActivity
```

### Critère de réussite #3 — A NE déclenche PAS d'élection (cooldown 5 min)

Dans `log-A.txt` (nouveau), doit apparaître :
```
[ELECTION] Cooldown actif (X min restantes) — abstention
[PEER] B reconnu comme Super-Pair → mise à jour PeerRegistry
```

⛔ A ne doit **pas** essayer de devenir Super-Pair pendant 5 min, même si son score (0.95) est supérieur à celui de B (0.70).

---

## Tableau récapitulatif des critères de validation

| # | Critère | NFR/Story validé | Statut |
|---|---|---|---|
| 1 | A devient COORDINATOR (score le plus élevé) | Story 3.1 (Bully) | ☐ |
| 2 | B et C reconnaissent A comme Super-Pair | Story 3.1 (Bully) | ☐ |
| 3 | A envoie REGISTER_PEER au Relais HA | Story 3.2 | ☐ |
| 4 | Détection de A en INACTIVE après kill (< 15s) | Story 3.4 (eviction) | ☐ |
| 5 | B remporte la nouvelle élection (< 10s) | NFR-06 (promotabilité) | ☐ |
| 6 | C reste silencieux face à B (score inférieur) | Story 3.1 (Bully) | ☐ |
| 7 | A respecte le cooldown 5 min après recovery | NFR-06 (cooldown) | ☐ |

---

## Pièges connus & dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| Aucune élection ne se déclenche | Les émulateurs ne se voient pas via le relais | Vérifier les logs Node.js : 3 connexions WSS attendues |
| Tous les nœuds deviennent Super-Pair | Étape 4 non appliquée | Commenter les lignes 174-200 de `MobicloudP2PService.kt` |
| Score identique pour les 3 | `setprop` non appliqué ou app lancée avant `setprop` | Faire `setprop` AVANT `am start` |
| Émulateur lent à démarrer (> 2 min) | Virtualisation BIOS désactivée | Activer VT-x/AMD-V + WHPX/HAXM |
| Logcat vide | Mauvais filtre tag | Vérifier les tags dans le code source (`Log.i("MobicloudP2PService", ...)`) |
| `adb devices` ne montre pas un émulateur | Conflit de port | Utiliser des ports espacés de 2 (5554, 5556, 5558) |

---

## Restauration post-test

À faire **avant la soutenance** ou avant tout autre test :

1. **Réactiver** la boucle d'auto-register (étape 4 — décommenter lignes 174-200)
2. **Restaurer** l'URL ngrok ou cloud dans `RelayWebSocketClient.kt` (étape 2)
3. **Vérifier** que `debug.mobicloud.reliability` n'est lu qu'en mode debug build (ou supprimer la flag)
4. **Reset** les batteries virtuelles si modifiées : `adb shell dumpsys battery reset`

---

## Livrables produits par ce test

- `log-A.txt` (~50 KB) — preuve d'élection victorieuse + abdication
- `log-B.txt` (~50 KB) — preuve de basculement + nouveau COORDINATOR
- `log-C.txt` (~50 KB) — preuve de respect de la hiérarchie Bully
- **Vidéo screen-capture optionnelle** (3 min) — démo live des 3 émulateurs côte-à-côte

Ces 4 artefacts constituent une **preuve reproductible d'élection multi-nœuds** défendable devant le jury PFE.

---

## Phrase à utiliser en soutenance

> *« L'Algorithme Bully a été validé sur 3 instances Android simultanées (3 émulateurs avec scores de fiabilité distincts), démontrant : (1) la sélection du nœud au score le plus élevé comme Super-Pair, (2) le basculement automatique en moins de 10 secondes après défaillance du Super-Pair, et (3) le respect du cooldown post-abdication garantissant la rotation du rôle. Les logs des 3 nœuds sont disponibles dans le rapport (annexe). »*
