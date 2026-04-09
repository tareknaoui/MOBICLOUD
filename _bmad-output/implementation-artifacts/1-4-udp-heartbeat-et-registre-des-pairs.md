# Story 1.4: UDP Heartbeat et Registre des Pairs

**Story ID:** 1.4
**Story Key:** 1-4-udp-heartbeat-et-registre-des-pairs
**Epic:** 1 (Identité & Découverte Réseau)
**Status:** done

## Story

**As a** Nœud du réseau,
**I want** d'écouter et de diffuser ma présence géographique sur le LAN via de très légers paquets UDP périodiques,
**so that** je maintienne en temps réel une liste fiable (en RAM) des téléphones actifs autour de moi.

## Acceptance Criteria

1. **Given** la couche réseau active (Story 1.3),
2. **When** le Heartbeat timer déclenche (ex: toutes les 2 secondes),
3. **Then** l'appareil diffuse son identité (Protobuf) en Multicast UDP sans établir de connexion TCP lourde.
4. **And** le registre local RAM ajoute ou supprime les pairs selon un timeout de péremption (ex: 5 secondes sans Heartbeat = déconnecté).

## Tasks / Subtasks

- [x] **Task 1 (AC: 1, 2, 3): Implémenter le diffuseur UDP (Heartbeat Broadcaster)**
  - [x] Créer une classe ou un gestionnaire d'envoi de datagrammes UDP Multicast périodiques via `Dispatchers.IO`.
  - [x] Le payload du UDP doit encapsuler le modèle d'identité du noeud formaté/sérialisé via `kotlinx.serialization` (Protobuf).
- [x] **Task 2 (AC: 1, 3): Implémenter le récepteur UDP (Heartbeat Receiver)**
  - [x] Créer une boucle d'écoute Coroutine (ex: Flow de DatagramPackets) sur le port Multicast dédié.
  - [x] Gérer le parsing Protobuf (`ignoreUnknownKeys = true`) pour chaque paquet UDP reçu.
- [x] **Task 3 (AC: 4): Créer le Registre des Pairs en RAM**
  - [x] Concevoir le `PeerRegistry` (couche Domain) qui maintient l'état actif des pairs observables de tous les autres modules (ex: `StateFlow<List<Peer>>`).
  - [x] Implémenter le mécanisme de Timeout (Eviction Cache) : si un pair ne donne pas signe de vie après 5 secondes, le supprimer du registre.
- [x] **Task 4: Intégration et Injection (Hilt)**
  - [x] Exposer l'interface `IPeerRegistry` et injecter son implémentation singleton.
  - [x] Lier la boucle de Broadcast/Receive au cycle de vie activé par le noeud (le `MobicloudP2PService` met en place la couche système).
  - [x] Écrire les Tests Unitaires pour le registre (timeout d'éviction) et le parsing Protobuf.

## Developer Context & Guardrails

### 🏗 Architecture Compliance (CRITICAL)
- **Couche Network Isolée:** Le code UDP natif (DatagramSocket, MulticastSocket) **DOIT** se situer dans la couche infrastructure logicielle Android (`data/p2p` ou `data/network`).
- **Domain Mapped:** Le registre in-memory appartient au `domain/repository/PeerRegistry`. Ses données doivent être observables de manière purement réactive via `StateFlow` ou `SharedFlow`. 
- **Non-Blocking I/O:** Toute lecture (`socket.receive`) UDP et écriture doit être rigoureusement encapsulée dans `Dispatchers.IO`. Le nettoyage du registre (timer) peut tourner sur `Dispatchers.Default`.

### ⚙️ Technical Guidelines
> [!IMPORTANT]
> - **Protobuf Payload:** Le payload du Heartbeat UDP doit être ultraléger. Ne diffusez qu'un identifiant clair, une signature asymétrique minimale, afin de ne pas déclencher le MTU (Maximum Transmission Unit) du réseau Wi-Fi local ni saturer le CPU/Batterie.
> - **Multicast Network Limits:** Utilisez le `MulticastLock` précédemment acquis. Prenez soin de bloquer la réception des propres paquets (Loopback) pour éviter qu'un noeud s'ajoute lui-même comme étant un noeud distant dans son propre réseau.
> - **Error Handling:** `ZÉRO Exception silencieuse`. Les exceptions UDP (ex: SocketTimeoutException) ou Protobuf (SerializationException) doivent renvoyer et se gérer via un validateur natif `Result<T>`.

### 📂 File Structure Requirements
Arborescence structurale type attendue :
```text
app/src/main/kotlin/com/mobicloud/
 ├── domain/
 │   ├── models/
 │   │   └── Peer.kt
 │   └── repository/
 │       └── PeerRegistry.kt (Interface et Flow de l'état des noeuds actifs)
 ├── data/
 │   ├── p2p/
 │   │   ├── UdpHeartbeatBroadcaster.kt
 │   │   └── UdpHeartbeatReceiver.kt
 │   └── repository_impl/
 │       └── PeerRegistryImpl.kt (Gestion en RAM et coroutine de Purge/Timeout)
 └── di/
     └── NetworkModule.kt (Bind Hilt)
```

### 🧪 Testing Requirements
- **Unit Testing (Domain):** Vérifier que `PeerRegistryImpl` ajoute des noeuds, met à jour le timestamp si déjà existant, et PURGE correctement ceux expirant après le délai paramétré. Utiliser l'horloge virtuelle de `runTest` ou `delay()`.
- **Integration Testing (Data):** Tester de manière isolée la sérialisation et désérialisation du Protobuf d'identité pour garantir qu'aucune exception ne passe lors de mismatch.

## Previous Story Intelligence
- La Story 1.3 a mis en place le `MobicloudP2PService` avec le `MulticastLock`. C'est là que l'écoute réseau Multicast a pu commencer.
- La règle de "Zéro Exception Silencieuse" a été stricte lors du Review adversarial précédent. Respecter l'interface retournant `Result<Unit>` au domaine pour toute anomalie I/O.
- Pensez à gérer gracieusement l'arrêt des `Job` ou `Flow` (ex: `cancelChildren`) en cas d'interruption du service.

## Dev Agent Record
- **Implementation Notes:** L'implémentation de `UdpHeartbeatBroadcaster` et `UdpHeartbeatReceiver` a été complétée et corrigée. Les erreurs de compilation liées au modèle `NodeIdentity` (utilisation de `publicId` au lieu de `deviceId`) ont été fixées. Le service réseau `MobicloudP2PService` a été mis à jour de la boucle de broadcast non valide vers une boucle de diffusion intégrée avec le broadcaster. La duplication du composant `ProtoBuf` dans `P2PModule` a été résolue afin que Hilt compile avec succès.
- **Completion Notes:** Tous les critères d'acceptation sont atteints. Les tests unitaires sont vérifiés et passent tous avec succès.

## File List
- app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt
- app/src/main/kotlin/com/mobicloud/data/p2p/UdpHeartbeatBroadcaster.kt
- app/src/main/kotlin/com/mobicloud/data/p2p/UdpHeartbeatReceiver.kt
- app/src/main/kotlin/com/mobicloud/data/repository_impl/PeerRegistryImpl.kt
- app/src/main/kotlin/com/mobicloud/di/P2PModule.kt
- app/src/test/kotlin/com/mobicloud/data/repository_impl/PeerRegistryImplTest.kt
- app/src/test/kotlin/com/mobicloud/data/p2p/UdpHeartbeatBroadcasterTest.kt
- app/src/test/kotlin/com/mobicloud/data/p2p/UdpHeartbeatReceiverTest.kt

## Change Log
- 2026-04-07: Fixed code compilation errors and syntax usages. Resolved duplicate Hilt bindings. All UDP broadcasting features fully complete and tested successfully.

### Review Findings
- [x] [Review][Patch] UdpHeartbeatBroadcaster s'arrête définitivement à la première exception réseau `socket.send` [app/src/main/kotlin/com/mobicloud/data/p2p/UdpHeartbeatBroadcaster.kt:33]
- [x] [Review][Patch] UdpHeartbeatReceiver tight-loop causant usage CPU élevé lors d'une SocketException non bloquante [app/src/main/kotlin/com/mobicloud/data/p2p/UdpHeartbeatReceiver.kt:38]
- [x] [Review][Patch] MobicloudP2PService reste en background de façon oisive sans stopper le service si `getIdentity()` échoue [app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt:83]
- [x] [Review][Defer] Sauts d'horloge système (backward/forward jump) liés à `System.currentTimeMillis()` impactant l'éviction de `PeerRegistryImpl` [app/src/main/kotlin/com/mobicloud/data/repository_impl/PeerRegistryImpl.kt:33] — deferred, pre-existing
