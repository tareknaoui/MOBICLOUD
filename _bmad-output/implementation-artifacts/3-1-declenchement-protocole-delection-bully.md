# Story 3.1: Déclenchement & Protocole d'Élection Bully

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant que nœud MobiCloud,
Je veux participer à une élection Bully lorsqu'aucun Super-Pair n'est joignable,
Afin que le cluster désigne automatiquement son meilleur coordinateur sans intervention humaine.

## Acceptance Criteria

1. **Given** aucun Super-Pair actif n'est détecté dans la `PeerRegistry` depuis > 5 secondes
2. **When** le nœud déclenche le protocole d'élection
3. **Then** il envoie un message `ELECTION` Protobuf signé (avec son `nodeId` et `reliabilityScore`) à tous les pairs connus
4. **And** tout pair recevant un `ELECTION` avec un score inférieur au sien répond `ALIVE` et lance sa propre candidature
5. **And** tout pair recevant un `ELECTION` avec un score supérieur reste silencieux
6. **And** si aucune réponse `ALIVE` n'est reçue après 3 secondes, le nœud se déclare vainqueur et envoie `COORDINATOR` à tous les pairs
7. **And** tous les pairs mettent à jour leur `PeerRegistry` avec le nouveau Super-Pair désigné
8. **And** la logique est encapsulée dans `domain/usecase/m10_election/RunBullyElectionUseCase.kt`

## Tasks / Subtasks

- [x] Task 1: Interfaces et Modèles Protobuf
  - [x] Vérifier ou ajouter les modèles de payload P2P spécifiques (`ELECTION`, `ALIVE`, `COORDINATOR`).
  - [x] Veiller à inclure la signature (Android Keystore hardware-backed).
- [x] Task 2: Implémentation du Use Case `RunBullyElectionUseCase`
  - [x] Squelette de classe `RunBullyElectionUseCase` avec `@Inject`.
  - [x] Étape 1 : Monitorer l'absence de Super-Pair > 5s via le `PeerRepository`.
  - [x] Étape 2 : Broadcast de `ELECTION` aux pairs actifs.
  - [x] Étape 3 : Gestion concurrentielle du timeout de 3s (réponse `ALIVE`).
  - [x] Étape 4 : Déclaration (broadcast `COORDINATOR`) et mise à jour locale.
- [x] Task 3: Ingestion d'événements Bully
  - [x] (Si applicable dans ce scope) préparer les Callbacks/Flows de réception de `ELECTION`, `ALIVE`, `COORDINATOR` pour qu'ils soient traités.
- [x] Task 4: Tests Unitaires
  - [x] Mock de `PeerRepository` et dispatcher.
  - [x] Vérifier la victoire si pas de réponse `ALIVE`.
  - [x] Vérifier l'abandon / repli si réponse `ALIVE` + score supérieur reçu.

## Dev Notes

- **Contexte Architectonique (V4.0) :**
  - **Le Hashcash est RETIRÉ.** L'anti-Sybil repose sur l'`Android Keystore` (générée Epic 1).
  - Éviter d'utiliser ou importer Firebase ici : Firebase `SignalingRepository` sera appelé à la Story 3.2. Cette story se concentre sur l'élection Bully LAN stricte. 
  - Tout I/O ou traitement P2P doit tourner sur un `Dispatcher` approprié (les algorithmes CPU-bound ne doivent pas bloquer le thread principal ; les Sockets sur `Dispatchers.IO`).
- **Communication & Erreurs :**
  - Retourner `Result<T>` ou la `sealed class Resource<T>`. AUCUNE exception silencieuse.

### Project Structure Notes

- **Alignment with Clean Architecture:** `domain/usecase/m10_election/RunBullyElectionUseCase.kt` -> Zéro dépendance à l'OS Android dans ce package.

### References

- [Source: epics.md#Story-31-Declenchement--Protocole-dÉlection-Bully](file:///c:/Users/DABC/Documents/GitHub/MOBICLOUD/_bmad-output/planning-artifacts/epics.md)
- [Source: architecture.md#Decision-Priority-Analysis](file:///c:/Users/DABC/Documents/GitHub/MOBICLOUD/_bmad-output/planning-artifacts/architecture.md)

## Dev Agent Record

### Agent Model Used

Gemini 3.1 Pro (High)

### Debug Log References

N/A

### Completion Notes List

- Implémentation du payload Protobuf `ElectionPayload` et du Message Type.
- Création de `RunBullyElectionUseCase` gérant la temporisation conditionnelle (>5s de vérification), le broadcast d'ELECTION et le timeout (3s) d'attente d’un pair ALIVE ayant une priorité supérieure. Le bris d’égalité par identifiant lexicographique est fonctionnel.
- Création de `ProcessIncomingElectionEventUseCase` pour réagir aux types `ELECTION`, `ALIVE`, `COORDINATOR`.
- Création et injection de l'interface `IElectionNetworkClient` pour la délégation I/O (selon l'architecture Hexagonale).
- Extension de `Peer` / `PeerRepository` pour refléter l'élection d'un super-pair.
- Tests unitaires complets sur le cas nominal de victoire et le repli (abandon).

### File List
- `app/src/main/kotlin/com/mobicloud/domain/models/ElectionMessageType.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/ElectionPayload.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/ElectionEvent.kt` *(nouveau)*
- `app/src/main/kotlin/com/mobicloud/domain/models/Peer.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/PeerRepository.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/SecurityRepository.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/IElectionNetworkClient.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RunBullyElectionUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/ProcessIncomingElectionEventUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/RunBullyElectionUseCaseTest.kt`

### Review Findings

#### Decision Needed
- [x] [Review][Decision] **F-03** — `NodeIdentity` reconstruit avec clé publique `ByteArray(0)` lors de la réception COORDINATOR — La clé publique réelle du pair n'est pas récupérée depuis le `PeerRepository`; le Super-Pair est enregistré avec une identité cryptographique invalide. Quelle est l'approche voulue : stocker la clé depuis le Peer existant, ou ajouter la clé publique dans `ElectionPayload`? [`ProcessIncomingElectionEventUseCase.kt:60`]
- [x] [Review][Decision] **F-08** — Race condition sur le Flow `incomingMessages` partagé — `RunBullyElectionUseCase` et `ProcessIncomingElectionEventUseCase` consomment le même hot flow; un message ALIVE peut être intercepté par `ProcessIncomingElectionEventUseCase` avant que le `firstOrNull` dans `RunBullyElectionUseCase` ne le voie, causant un deadlock logique. Approche voulue : SharedFlow avec `replay`, ou séparation des flows par type de message ? [`IElectionNetworkClient.kt:14`]
- [x] [Review][Decision] **F-09** — AC1 non respecté : `delay(5000)` fixe ≠ surveillance réactive de l'absence de Super-Pair — L'AC stipule "aucun Super-Pair actif depuis >5 secondes". L'implémentation attend 5s inconditionnellement dès le démarrage sans monitorer dynamiquement l'état. Doit-on implémenter un monitoring réactif via le `StateFlow<List<Peer>>` ? [`RunBullyElectionUseCase.kt:34`]

#### Patch
- [x] [Review][Patch] **F-01** — Résultats des broadcasts ELECTION et COORDINATOR non vérifiés [`RunBullyElectionUseCase.kt:53,77`]
- [x] [Review][Patch] **F-02** — Signature vide propagée silencieusement si `signData` échoue — viole "AUCUNE exception silencieuse" [`RunBullyElectionUseCase.kt:111`, `ProcessIncomingElectionEventUseCase.kt:80`]
- [x] [Review][Patch] **F-04** — Aucune vérification de signature sur les messages COORDINATOR reçus — vecteur d'élection forgée [`ProcessIncomingElectionEventUseCase.kt:56-64`]
- [x] [Review][Patch] **F-05** — `getOrThrow()` non protégé dans `processPayload` — exception non catchée dans le Flow map [`ProcessIncomingElectionEventUseCase.kt:38`]
- [x] [Review][Patch] **F-07** — Tests incompatibles avec `flowOn(Dispatchers.Default)` — `advanceTimeBy` ne contrôle pas `Dispatchers.Default`, tests potentiellement faux positifs [`RunBullyElectionUseCaseTest.kt:77,80`]
- [x] [Review][Patch] **F-10** — AC4 incomplet : réponse ALIVE présente mais lancement de la propre candidature absent (commentaire `// pourrait se faire`) [`ProcessIncomingElectionEventUseCase.kt:49-51`]

#### Deferred
- [x] [Review][Defer] **F-06** — `isHigherPriority` dupliqué dans `RunBullyElectionUseCase` et `ProcessIncomingElectionEventUseCase` [`RunBullyElectionUseCase.kt:97`, `ProcessIncomingElectionEventUseCase.kt:68`] — deferred, pre-existing, à extraire en utilitaire partagé lors d'un refactor
