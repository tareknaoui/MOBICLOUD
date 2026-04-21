# Story 5.2: Chiffrement AES-256 GCM des Fragments (Zero-Trust)

Status: done

## Story

En tant que nœud hébergeur,
Je veux que chaque bloc Erasure soit chiffré avec une clé éphémère unique avant distribution,
Afin de ne jamais pouvoir lire le contenu du bloc que je stocke (Zero-Trust).

## Acceptance Criteria

1. **Given** K+N blocs Erasure sont générés pour un fichier
   **When** chaque bloc est préparé pour la distribution
   **Then** une clé AES-256 éphémère dérivée est générée pour chaque bloc : `HKDF-SHA256(FileMasterKey, info="block_key_<i>")` → clé AES-256 de 32 bytes

2. **And** chaque bloc est chiffré individuellement avec sa clé éphémère + un IV aléatoire 96 bits (12 bytes), tag GCM 128 bits

3. **And** la `FileMasterKey` est chiffrée avec la clé publique EC du destinataire (ECIES manuel : ECDH éphémère + HKDF + AES-GCM) et encapsulée dans un `WrappedFileMasterKey`

4. **And** les clés éphémères par bloc ne sont jamais stockées en clair sur disque (RAM uniquement pendant l'opération) — seuls le ciphertext + IV + `WrappedFileMasterKey` sont persistés

5. **And** `core/security/FragmentCipherUseCase.kt` encapsule toute la logique cryptographique, avec les signatures :
   - `suspend fun encrypt(fragments: List<ErasureFragment>, recipientPublicKeyBytes: ByteArray): Result<EncryptedBundle>`
   - `suspend fun decrypt(bundle: EncryptedBundle, recipientPrivateKey: PrivateKey): Result<List<ErasureFragment>>`

6. **And** un test unitaire JVM vérifie :
   - déchiffrement avec la bonne clé privée = bloc original (SHA-256 match)
   - déchiffrement avec une clé privée incorrecte = `Result.Failure`
   - un IV modifié (tamper) = `Result.Failure` (intégrité GCM)

## Tasks / Subtasks

- [x] Task 1 : Modèles Domain (AC: #3, #4)
  - [x] Subtask 1.1 : Créer `domain/models/EncryptedFragment.kt` :
    ```kotlin
    data class EncryptedFragment(
        val index: Int,
        val isParity: Boolean,
        val ciphertext: ByteArray,  // ciphertext || 16-byte GCM tag (Cipher.doFinal)
        val iv: ByteArray,          // 12 bytes (96-bit)
        val originalFileSize: Long
    )
    ```
  - [x] Subtask 1.2 : Créer `domain/models/WrappedFileMasterKey.kt` :
    ```kotlin
    data class WrappedFileMasterKey(
        val ephemeralPublicKeyBytes: ByteArray,  // X.509 SubjectPublicKeyInfo, ~65 bytes
        val iv: ByteArray,                       // 12 bytes
        val encryptedKey: ByteArray              // 32 bytes ciphertext + 16 bytes GCM tag = 48 bytes
    )
    ```
  - [x] Subtask 1.3 : Créer `domain/models/EncryptedBundle.kt` :
    ```kotlin
    data class EncryptedBundle(
        val encryptedFragments: List<EncryptedFragment>,
        val wrappedFileMasterKey: WrappedFileMasterKey
    )
    ```
  - [x] Subtask 1.4 : Ajouter `init { require(index >= 0); require(iv.size == 12); require(ciphertext.isNotEmpty()) }` dans `EncryptedFragment`

- [x] Task 2 : Helper HKDF-SHA256 (AC: #1, #3)
  - [x] Subtask 2.1 : Créer `core/security/HkdfSha256.kt` — fonction interne `internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray? = null, info: ByteArray, outputLen: Int): ByteArray` via HMAC-SHA256 (RFC 5869, extract + expand) — **pas de dépendance externe, pur `javax.crypto.Mac`**
  - [x] Subtask 2.2 : Extract : `Mac.getInstance("HmacSHA256")` avec salt par défaut = `ByteArray(32)` si null → PRK 32 bytes
  - [x] Subtask 2.3 : Expand : boucles HMAC avec compteur 1-byte, concaténer jusqu'à `outputLen` bytes

- [x] Task 3 : `FragmentCipherUseCase` — chiffrement (AC: #1, #2, #3, #4, #5)
  - [x] Subtask 3.1 : Créer `app/src/main/kotlin/com/mobicloud/core/security/FragmentCipherUseCase.kt` annoté `@Singleton @Inject constructor()` — **placé dans `core/security/`** (contient des imports `javax.crypto`, hors domain pur)
  - [x] Subtask 3.2 : `encrypt` — générer `FileMasterKey` via `SecureRandom().nextBytes(ByteArray(32))`
  - [x] Subtask 3.3 : Pour chaque `ErasureFragment` : `blockKey = hkdfSha256(ikm=fileMasterKey, info="block_key_$index".toByteArray())` ; `iv = ByteArray(12).also { SecureRandom().nextBytes(it) }` ; `cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(ENCRYPT_MODE, SecretKeySpec(blockKey, "AES"), GCMParameterSpec(128, iv)) }` ; `ciphertext = cipher.doFinal(fragment.data)` → crée `EncryptedFragment`
  - [x] Subtask 3.4 : ECIES — générer paire éphémère : `KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }.generateKeyPair()`
  - [x] Subtask 3.5 : ECIES — décoder clé publique destinataire : `KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(recipientPublicKeyBytes))` (format X.509 SubjectPublicKeyInfo — c'est exactement ce que `publicKeyBytes` de `NodeIdentity` contient)
  - [x] Subtask 3.6 : ECIES — ECDH : `KeyAgreement.getInstance("ECDH").apply { init(ephemeralPrivateKey); doPhase(recipientPublicKey, true) }.generateSecret()` → 32 bytes shared secret
  - [x] Subtask 3.7 : ECIES — wrapping key : `hkdfSha256(ikm=sharedSecret, info="ecies_key".toByteArray())` → 32 bytes
  - [x] Subtask 3.8 : ECIES — chiffrer FileMasterKey : AES-256-GCM avec `wrappingKey` + IV aléatoire 12 bytes → `WrappedFileMasterKey(ephemeralKeyPair.public.encoded, iv, encryptedKey)`
  - [x] Subtask 3.9 : Effacer `fileMasterKey` et les `blockKey` de la RAM après usage : `fileMasterKey.fill(0)` ; `blockKey.fill(0)`
  - [x] Subtask 3.10 : Tout le corps de `encrypt` s'exécute dans `withContext(Dispatchers.Default)` (AC architecture §220 CPU-lourd)

- [x] Task 4 : `FragmentCipherUseCase` — déchiffrement (AC: #5, #6)
  - [x] Subtask 4.1 : `decrypt(bundle, recipientPrivateKey: java.security.PrivateKey)` — ECDH inverse : décoder `ephemeralPublicKeyBytes` via `KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(...))` ; `KeyAgreement.getInstance("ECDH").apply { init(recipientPrivateKey); doPhase(ephemeralPublicKey, true) }.generateSecret()`
  - [x] Subtask 4.2 : Dériver wrapping key (même HKDF), déchiffrer FileMasterKey AES-GCM → `fileMasterKey`
  - [x] Subtask 4.3 : Pour chaque `EncryptedFragment` : dériver `blockKey_i`, AES-GCM déchiffrer → `ErasureFragment(index, isParity, plaintext, originalFileSize)`
  - [x] Subtask 4.4 : Tout AEADBadTagException ou exception crypto → `Result.failure(...)` (jamais throw non géré)
  - [x] Subtask 4.5 : Effacer `fileMasterKey` et les `blockKey` en fin de traitement

- [x] Task 5 : Tests unitaires JVM (AC: #6)
  - [x] Subtask 5.1 : Créer `app/src/test/kotlin/com/mobicloud/core/security/FragmentCipherUseCaseTest.kt`
  - [x] Subtask 5.2 : Générer une paire EC P-256 **logicielle** (non-Keystore) : `KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }.generateKeyPair()` — **les tests JVM peuvent utiliser `javax.crypto` sans émulateur**
  - [x] Subtask 5.3 : Créer 3 fragments Erasure fictifs (données aléatoires 1 MiB, index 0/1/2)
  - [x] Subtask 5.4 : Test 1 (AC #6a) : `encrypt(fragments, keyPair.public.encoded)` puis `decrypt(bundle, keyPair.private)` → `List<ErasureFragment>` avec data SHA-256 identique au plaintext original
  - [x] Subtask 5.5 : Test 2 (AC #6b) : `decrypt(bundle, wrongKeyPair.private)` → `Result.isFailure == true`
  - [x] Subtask 5.6 : Test 3 (AC #6c) : Modifier 1 byte du ciphertext d'un fragment puis `decrypt` → `Result.isFailure == true` (intégrité AES-GCM)
  - [x] Subtask 5.7 : Test 4 (AC #6c) : Modifier `WrappedFileMasterKey.encryptedKey` → `Result.isFailure == true`
  - [x] Subtask 5.8 : Test 5 : `encrypt(emptyList(), ...)` → `Result.failure(IllegalArgumentException)` (fragments vide = sans valeur métier)

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Description | Action |
|---|---|---|
| `core/security/KeystoreManager.kt` | Génère et lit la paire EC P-256 Keystore | **NE PAS TOUCHER** — Story 5.2 n'interagit pas avec le Keystore |
| `domain/models/ErasureFragment.kt` | `data class ErasureFragment(index, isParity, data: ByteArray, originalFileSize)` | **CONSOMMER tel quel** — `data` est mutable-friendly pour Story 5.2 |
| `domain/models/ErasureParameters.kt` | K=4, N=2, blockSize=1MiB | **RÉFÉRENCER** — pas de modification |
| `core/erasure/ErasureCodec.kt` | Interface domain sans import Android | **NE PAS TOUCHER** |
| `core/erasure/ErasureCodingJni.kt` | Bridge JNI DirectByteBuffer | **NE PAS TOUCHER** |
| `domain/usecase/m08_m09_erasure_coding/EncodeErasureFragmentsUseCase.kt` | Produit `List<ErasureFragment>` en clair | **CONSOMMER** — Story 5.2 chiffre cette liste |

### ⚠️ CONTRAINTE CRITIQUE — CLÉ KEYSTORE ET ECDH

Le `KeystoreManager` génère la clé EC avec `PURPOSE_SIGN | PURPOSE_VERIFY` **sans** `PURPOSE_AGREE_KEY`. L'Android Keystore ne supporte `PURPOSE_AGREE_KEY` qu'à partir d'**API 31**. Notre `minSdk = 24`.

**Conséquences pour cette story :**
- `encrypt()` n'a besoin que de la clé **publique** du destinataire (disponible dans `NodeIdentity.publicKeyBytes`). ✅ Aucun problème.
- `decrypt()` dans les **tests JVM** utilise une paire logicielle générée en mémoire. ✅ Aucun problème.
- `decrypt()` en **production** (Story 6.3) nécessitera une clé privée EC capable de faire ECDH → **solution différée à Story 6.3** : générer une 2ème paire EC dans `EncryptedSharedPreferences` via `androidx-security-crypto` OU utiliser un `KeyAgreement` software sur la clé exportée en TEE (si disponible).

**Action Story 5.2 :** Déclarer le paramètre `recipientPrivateKey: java.security.PrivateKey` (interface pure) dans `decrypt()`. Ne pas coupler à Keystore. Story 6.3 fournira la clé concrète.

### 📐 Algorithme Cryptographique — Spécifications Précises

**HKDF-SHA256 (RFC 5869) — Implémentation pure `javax.crypto`:**
```kotlin
internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray? = null, info: ByteArray, outputLen: Int = 32): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    // Extract
    mac.init(SecretKeySpec(salt ?: ByteArray(32), "HmacSHA256"))
    val prk = mac.doFinal(ikm)
    // Expand
    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    val result = ByteArray(outputLen)
    var prev = ByteArray(0)
    var pos = 0; var ctr = 1
    while (pos < outputLen) {
        mac.update(prev); mac.update(info); mac.update(ctr.toByte())
        prev = mac.doFinal()
        val len = minOf(prev.size, outputLen - pos)
        prev.copyInto(result, pos, 0, len)
        pos += len; ctr++
    }
    return result
}
```

**AES-256-GCM :**
- `Cipher.getInstance("AES/GCM/NoPadding")` — disponible API 19+
- `GCMParameterSpec(128, iv)` — tag 128 bits (16 bytes)
- `doFinal()` retourne `ciphertext || tag` concatenés (taille = plaintext.size + 16)
- **IV : ne jamais réutiliser une même paire (clé, IV)** — toujours `SecureRandom().nextBytes(ByteArray(12))`

**ECDH :**
- `KeyAgreement.getInstance("ECDH")` — disponible API 19+, fournisseur par défaut (non-Keystore)
- `ECGenParameterSpec("secp256r1")` = P-256 (identique à `"prime256v1"`)
- `doPhase(peerPublicKey, true)` puis `generateSecret()` → 32 bytes
- Éphémère : `KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }`

**Format `NodeIdentity.publicKeyBytes` :**
- Produit par `keyPair.public.encoded` dans `KeystoreManager` → **X.509 SubjectPublicKeyInfo** (DER encodé, ~65 bytes pour P-256 non compressé)
- Décoder avec : `KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyBytes))`

### 🏗️ Architecture Compliance (IMPÉRATIF)

- **Placement :** `FragmentCipherUseCase.kt` dans `core/security/` (conforme architecture.md — contient des imports `javax.crypto`). Les modèles `EncryptedFragment`, `WrappedFileMasterKey`, `EncryptedBundle` dans `domain/models/` (pure Kotlin, pas d'import Android).
- **Dispatchers :** `withContext(Dispatchers.Default)` pour encrypt + decrypt (CPU-lourd — architecture §220).
- **Result<T> obligatoire** : Toute `AEADBadTagException`, `InvalidKeyException`, `GeneralSecurityException` → `Result.failure(...)`. Zéro exception silencieuse (architecture §214).
- **Injection Hilt :** `@Singleton @Inject constructor()` sur `FragmentCipherUseCase`. Pas d'instanciation manuelle.
- **Domain zéro-Android :** Les 3 modèles `EncryptedFragment`, `WrappedFileMasterKey`, `EncryptedBundle` dans `domain/models/` doivent avoir **zéro import Android** (purs `kotlin.*` / `java.*`).

### 🧪 Stratégie de Tests

**Tests JVM purs (avantage sur Story 5.1) :**
`javax.crypto` et `java.security` sont disponibles sur la JVM hôte (x86_64 Windows). **Aucun émulateur, aucune `.so` ARM nécessaire** pour les tests 5.2. Tous les tests tournent avec `./gradlew :app:testDebugUnitTest`.

**Cas de test obligatoires :**
1. Round-trip encrypt→decrypt : SHA-256 identique
2. Clé privée incorrecte → `Result.failure`
3. Tamper ciphertext → `Result.failure` (AEADBadTagException géré)
4. Tamper `WrappedFileMasterKey` → `Result.failure`
5. Fragments vide → `Result.failure(IllegalArgumentException)`

**Anti-pattern à éviter :**
- Ne pas `catch (Exception e)` générique → retour `Result.failure(e)` uniquement sur les exceptions crypto connues (`GeneralSecurityException` et sous-classes).

### 🔗 Intégration avec les Stories Adjacentes

- **Story 5.1 (done) → Story 5.2 (cette story) :** Consommer `List<ErasureFragment>` de `EncodeErasureFragmentsUseCase.encode()`. Le champ `data` (plaintext) est le plaintext à chiffrer. Produire une **nouvelle liste** `List<EncryptedFragment>` — ne pas modifier l'`ErasureFragment` original (immutabilité fonctionnelle).
- **Story 5.2 → Story 5.3 :** La Story 5.3 (distribution) consomme `EncryptedBundle.encryptedFragments`. Le `EncryptedBundle.wrappedFileMasterKey` sera stocké dans la `CatalogEntry` DHT. **Garantir la sérializabilité** de tous les modèles (ajouter `@Serializable` si nécessaire pour Protobuf/JSON).
- **Story 5.2 → Story 6.3 :** `decrypt()` sera appelé pendant la reconstruction. Ne pas coupler `decrypt()` au Keystore — le paramètre `recipientPrivateKey: java.security.PrivateKey` laisse Story 6.3 libre de fournir la clé.

### 📁 Arborescence Cible Après Implémentation

```
app/src/main/kotlin/com/mobicloud/
├── core/
│   └── security/
│       ├── KeystoreManager.kt              ← INCHANGÉ
│       ├── FragmentCipherUseCase.kt        ← NOUVEAU (encrypt + decrypt)
│       └── HkdfSha256.kt                  ← NOUVEAU (internal fun, RFC 5869)
├── domain/
│   └── models/
│       ├── ErasureFragment.kt              ← INCHANGÉ (consommé)
│       ├── ErasureParameters.kt            ← INCHANGÉ
│       ├── EncryptedFragment.kt            ← NOUVEAU
│       ├── WrappedFileMasterKey.kt         ← NOUVEAU
│       └── EncryptedBundle.kt              ← NOUVEAU

app/src/test/kotlin/com/mobicloud/core/security/
└── FragmentCipherUseCaseTest.kt            ← NOUVEAU (5 tests JVM, aucun émulateur)
```

### 🎯 Contraintes Non-Négociables

- **NFR-03 (CPU ≤ 5%) :** Pas de boucle JNI — chiffrement 100% JVM `javax.crypto`. Performance acceptable pour K+N ≤ 6 blocs de 1 MiB max.
- **Zero-Trust (FR-03.2) :** Le nœud hébergeur reçoit uniquement `EncryptedFragment.ciphertext` — jamais la `FileMasterKey`, jamais les `blockKey`. Garantir par design : `FragmentCipherUseCase.encrypt()` ne retourne rien de déchiffrable sans `WrappedFileMasterKey`.
- **IV non-réutilisable :** `SecureRandom` obligatoire pour chaque IV. Interdire les IVs statiques ou dérivés (vulnérabilité GCM).
- **Interdit :** Ajouter BouncyCastle ou toute autre dépendance externe de crypto. `javax.crypto` + `java.security` suffisent.

### 📚 Références

- [Source: epics.md#Story-5.2](../planning-artifacts/epics.md) — AC BDD + objectif Zero-Trust
- [Source: architecture.md#Authentication-Security](../planning-artifacts/architecture.md) — AES-256 GCM, HKDF, ECIES, clés éphémères RAM
- [Source: architecture.md#Enforcement-Guidelines](../planning-artifacts/architecture.md) — Dispatchers.Default CPU-lourd, Result<T> obligatoire
- [Source: architecture.md#Project-Structure](../planning-artifacts/architecture.md) — core/security/FragmentCipherUseCase.kt
- [Source: 5-1-moteur-erasure-coding-c-ndkjni.md](./5-1-moteur-erasure-coding-c-ndkjni.md) — ErasureFragment model, ErasureCodec interface, patterns tests JVM, Completion Notes sur mutable-friendly data

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Aucun blocage — tests JVM purs compilés et passés du premier coup (après correction mineure de syntaxe `xor` sur `Byte`).

### Completion Notes List

- Implémentation complète de `FragmentCipherUseCase` avec chiffrement AES-256-GCM par bloc + ECIES (ECDH éphémère + HKDF + AES-GCM) pour la FileMasterKey.
- `HkdfSha256.kt` : RFC 5869 pur `javax.crypto`, zéro dépendance externe.
- Modèles domain (`EncryptedFragment`, `WrappedFileMasterKey`, `EncryptedBundle`) zéro import Android.
- 5 tests JVM couvrent : round-trip SHA-256, clé incorrecte, tamper ciphertext, tamper WrappedKey, fragments vides.
- `fileMasterKey` et `blockKey` effacés via `fill(0)` dans les blocs `finally`.
- `withContext(Dispatchers.Default)` sur encrypt et decrypt (CPU-lourd, conforme architecture §220).

### File List

- app/src/main/kotlin/com/mobicloud/domain/models/EncryptedFragment.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/domain/models/WrappedFileMasterKey.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/domain/models/EncryptedBundle.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/core/security/HkdfSha256.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/core/security/FragmentCipherUseCase.kt (nouveau)
- app/src/test/kotlin/com/mobicloud/core/security/FragmentCipherUseCaseTest.kt (nouveau)

## Change Log

- Implémentation complète Story 5.2 : chiffrement AES-256-GCM Zero-Trust avec ECIES (Date: 2026-04-20)

## Review Findings

### Decision Needed

- [x] [Review][Decision] Portée du `catch` dans `runCatching` vs. anti-pattern spec — Résolu : remplacé `runCatching` par `try-catch(GeneralSecurityException | IllegalArgumentException)` dans `encrypt` et `try-catch(GeneralSecurityException)` dans `decrypt`. Les exceptions inattendues (NPE, OOM) propagent maintenant en crash plutôt qu'être avalées silencieusement.

### Patches

- [x] [Review][Patch] HKDF counter truncation + `outputLen` sans validation [HkdfSha256.kt] — `require(outputLen in 1..(255 * 32))` ajouté. `prk` et `prev` zeroed après usage.
- [x] [Review][Patch] `sharedSecret`, `wrappingKey` et `prk` jamais effacés en mémoire [FragmentCipherUseCase.kt, HkdfSha256.kt] — `sharedSecret.fill(0)` + `wrappingKey.fill(0)` dans blocs `finally` de `wrapFileMasterKey` et `unwrapFileMasterKey`. `prk.fill(0)` dans `hkdfSha256`.
- [x] [Review][Patch] `WrappedFileMasterKey` sans bloc `init` de validation [WrappedFileMasterKey.kt] — `init { require(ephemeralPublicKeyBytes.isNotEmpty()); require(iv.size == 12); require(encryptedKey.isNotEmpty()) }` ajouté.
- [x] [Review][Patch] `SecureRandom` instancié par fragment dans la boucle `.map` [FragmentCipherUseCase.kt] — Extrait en `private val secureRandom = SecureRandom()` au niveau de la classe `@Singleton`.
- [x] [Review][Patch] Test 3 modifie le `ciphertext` au lieu de l'IV — non-conforme AC #6c [FragmentCipherUseCaseTest.kt] — Test renommé et modifié pour tampérer `frag.iv` (intégrité IV → GCM tag failure).

### Deferred

- [x] [Review][Defer] `recipientPublicKeyBytes` non validé avant parsing X.509 [FragmentCipherUseCase.kt] — Entrée malformée produit une exception capturée par `runCatching` → `Result.failure` déjà correct. Amélioration future uniquement. — deferred, pre-existing
- [x] [Review][Defer] `EncryptedFragment` accepte `originalFileSize = 0` sans contrainte [EncryptedFragment.kt] — Impact sémantique dépend de la reconstruction aval (Story 6.x). — deferred, pre-existing
- [x] [Review][Defer] Couverture de test insuffisante (fragments parity-only, single-fragment) [FragmentCipherUseCaseTest.kt] — Cas limites non requis par l'AC. — deferred, pre-existing
- [x] [Review][Defer] Ordre et unicité des indices de fragments dans `decrypt` non vérifiés [FragmentCipherUseCase.kt] — Responsabilité de la couche appelante (Story 5.3+). — deferred, pre-existing
- [x] [Review][Defer] Valeur par défaut du sel HKDF (`ByteArray(32)`) non documentée [HkdfSha256.kt] — Conforme RFC 5869 §2.2 mais non spécifié dans la story. Documentation future. — deferred, pre-existing
