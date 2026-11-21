package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.m11_join.decodeEncryptionKeyBinding
import com.mobicloud.data.local.m11_join.toEntity
import com.mobicloud.data.local.m11_join.toMemberInfoOrNull
import com.mobicloud.domain.models.EncryptionIdentity
import com.mobicloud.domain.models.EncryptionKeyBinding
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.m11_join.JoinRedirectReason
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.joinRequestSignedBytes
import com.mobicloud.domain.models.verifiedEncryptionPublicKey
import com.mobicloud.domain.models.verifyEncryptionKeyBinding
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Clé de chiffrement signée par l'identité et transmise au JOIN (réponse à R2-10). */
@OptIn(ExperimentalSerializationApi::class)
class EncryptionKeyBindingTest {

    // ── Crypto réelle (ECDSA P-256) ──────────────────────────────────────────

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun sign(keyPair: KeyPair, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(keyPair.private); update(data); sign() }

    private fun verify(data: ByteArray, sig: ByteArray, pub: ByteArray): Result<Boolean> = runCatching {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pub))
        Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(data); verify(sig) }
    }

    /** SecurityRepository d'un appareil : identité [identity], clé de chiffrement [encryption]. */
    private fun device(identity: KeyPair, encryption: KeyPair = ecKeyPair()): SecurityRepository = mockk {
        coEvery { getIdentity() } returns Result.success(NodeIdentity("aabb", identity.public.encoded, 0.9f))
        coEvery { getEncryptionIdentity() } answers { Result.success(EncryptionIdentity(encryption.public.encoded, encryption.private)) }
        coEvery { signData(any()) } answers { Result.success(sign(identity, firstArg())) }
        coEvery { verifySignature(any(), any(), any()) } answers { verify(firstArg(), secondArg(), thirdArg()) }
    }

    private val nodeId = byteArrayOf(0x01, 0x02)
    private val identity = ecKeyPair()
    private val encryption = ecKeyPair()

    private suspend fun issue(repo: SecurityRepository = device(identity, encryption), id: ByteArray = nodeId) =
        IssueEncryptionKeyBindingUseCase(repo)(id).getOrThrow()

    // ── Émission et vérification ─────────────────────────────────────────────

    @Test
    fun `a binding issued by the device verifies against its identity key`() = runTest {
        val repo = device(identity, encryption)
        val binding = issue(repo)
        assertArrayEquals(encryption.public.encoded, binding.encryptionPublicKey)
        assertTrue(verifyEncryptionKeyBinding(binding, nodeId, identity.public.encoded, repo))
    }

    @Test
    fun `a substituted encryption key is rejected`() = runTest {
        val repo = device(identity, encryption)
        val forged = issue(repo).copy(encryptionPublicKey = ecKeyPair().public.encoded)
        assertFalse(verifyEncryptionKeyBinding(forged, nodeId, identity.public.encoded, repo))
    }

    @Test
    fun `a binding presented for another node is rejected`() = runTest {
        val repo = device(identity, encryption)
        assertFalse(verifyEncryptionKeyBinding(issue(repo), byteArrayOf(0x09), identity.public.encoded, repo))
    }

    @Test
    fun `a binding signed by another identity is rejected`() = runTest {
        val attacker = ecKeyPair()
        val repo = device(identity, encryption)
        val signedByAttacker = issue(device(attacker, encryption))
        assertFalse(verifyEncryptionKeyBinding(signedByAttacker, nodeId, identity.public.encoded, repo))
    }

    @Test
    fun `the binding is cached and re-issued only when the encryption key changes`() = runTest {
        var currentEncryption = encryption
        val repo = device(identity, encryption)
        coEvery { repo.getEncryptionIdentity() } answers {
            Result.success(EncryptionIdentity(currentEncryption.public.encoded, currentEncryption.private))
        }
        val issuer = IssueEncryptionKeyBindingUseCase(repo)

        val first = issuer(nodeId).getOrThrow()
        assertEquals(first, issuer(nodeId).getOrThrow())
        coVerify(exactly = 1) { repo.signData(any()) }

        currentEncryption = ecKeyPair() // import d'un code de récupération
        val second = issuer(nodeId).getOrThrow()
        assertNotEquals(first, second)
        assertTrue(verifyEncryptionKeyBinding(second, nodeId, identity.public.encoded, repo))
    }

    @Test
    fun `issuing fails cleanly when no encryption key is available`() = runTest {
        val repo = device(identity, encryption)
        coEvery { repo.getEncryptionIdentity() } returns Result.failure(IllegalStateException("prefs"))
        assertTrue(IssueEncryptionKeyBindingUseCase(repo)(nodeId).isFailure)
    }

    // ── Lecture côté membre ──────────────────────────────────────────────────

    private fun member(binding: EncryptionKeyBinding?, signingKey: ByteArray = identity.public.encoded) = MemberInfo(
        nodeId = nodeId, publicKey = signingKey, ipAddress = "", port = 0, freeBytes = 0L,
        role = MemberRole.MEMBER, encryptionKeyBinding = binding
    )

    @Test
    fun `a member's encryption key is usable only once verified end to end`() = runTest {
        val repo = device(identity, encryption)
        val binding = issue(repo)
        assertArrayEquals(encryption.public.encoded, member(binding).verifiedEncryptionPublicKey(repo))
        assertNull("sans lien : aucune clé", member(null).verifiedEncryptionPublicKey(repo))
        assertNull(
            "un Super-Pair malveillant ne peut pas substituer la clé",
            member(binding.copy(encryptionPublicKey = ecKeyPair().public.encoded)).verifiedEncryptionPublicKey(repo)
        )
        assertNull(
            "le lien doit être signé par la clé d'identité du membre",
            member(binding, signingKey = ecKeyPair().public.encoded).verifiedEncryptionPublicKey(repo)
        )
    }

    // ── Admission au cluster (Super-Pair) ────────────────────────────────────

    private fun superPair(repo: SecurityRepository, registry: MemberRegistry): ProcessJoinRequestUseCase {
        val joinStateMachine = mockk<JoinStateMachine>(relaxed = true)
        every { joinStateMachine.currentState } returns MutableStateFlow(NodeJoinState.SuperPair("cluster-1"))
        val signaling = mockk<SignalingRepository>()
        coEvery { signaling.fetchActiveSuperPeerHints() } returns Result.success(emptyList())
        return ProcessJoinRequestUseCase(
            repo, signaling, registry, joinStateMachine, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true)
        )
    }

    private fun joinRequest(binding: EncryptionKeyBinding?): JoinRequest {
        val ts = System.currentTimeMillis()
        val pub = identity.public.encoded
        return JoinRequest(
            senderNodeId = nodeId, candidatePublicKey = pub, freeBytes = 1_000L, reliabilityScore = 0.8f,
            timestampMs = ts, signatureBytes = sign(identity, joinRequestSignedBytes(nodeId, pub, 1_000L, 0.8f, ts)),
            encryptionKeyBinding = binding
        )
    }

    @Test
    fun `the super-pair admits a verified key and publishes it to the cluster`() = runTest {
        val registry = RamMemberRegistry()
        val binding = issue()
        val response = superPair(device(ecKeyPair()), registry)(joinRequest(binding))

        assertTrue(response is JoinResponse.JoinAccept)
        val published = (response as JoinResponse.JoinAccept).memberSnapshot.single { it.nodeId.contentEquals(nodeId) }
        assertEquals(binding, published.encryptionKeyBinding)
    }

    @Test
    fun `the super-pair rejects a join carrying a forged key`() = runTest {
        val registry = RamMemberRegistry()
        val forged = issue().copy(encryptionPublicKey = ecKeyPair().public.encoded)
        val response = superPair(device(ecKeyPair()), registry)(joinRequest(forged))

        assertTrue(response is JoinResponse.JoinRedirect)
        assertEquals(JoinRedirectReason.INVALID_SIGNATURE, (response as JoinResponse.JoinRedirect).reason)
        assertEquals(0, registry.size())
    }

    @Test
    fun `an older client without a binding is still admitted`() = runTest {
        val registry = RamMemberRegistry()
        val response = superPair(device(ecKeyPair()), registry)(joinRequest(null))
        assertTrue(response is JoinResponse.JoinAccept)
        assertNull((response as JoinResponse.JoinAccept).memberSnapshot.single().encryptionKeyBinding)
    }

    // ── Compatibilité du format et persistance ───────────────────────────────

    /** JoinRequest tel qu'émis/lu par les versions précédentes de l'application. */
    @Serializable
    private class LegacyJoinRequest(
        val senderNodeId: ByteArray,
        val candidatePublicKey: ByteArray,
        val freeBytes: Long,
        val reliabilityScore: Float,
        val timestampMs: Long,
        val signatureBytes: ByteArray
    )

    @Test
    fun `wire format stays compatible in both directions`() = runTest {
        val withBinding = joinRequest(issue())
        val legacy = ProtoBuf.decodeFromByteArray(LegacyJoinRequest.serializer(), ProtoBuf.encodeToByteArray(JoinRequest.serializer(), withBinding))
        assertArrayEquals(withBinding.signatureBytes, legacy.signatureBytes)

        val fromLegacy = ProtoBuf.decodeFromByteArray(JoinRequest.serializer(), ProtoBuf.encodeToByteArray(LegacyJoinRequest.serializer(), legacy))
        assertNull(fromLegacy.encryptionKeyBinding)
        assertEquals(withBinding.timestampMs, fromLegacy.timestampMs)
    }

    @Test
    fun `the binding survives a database round trip`() = runTest {
        val m = member(issue())
        val restored = m.toEntity(clusterId = "c", lastSeen = 1L).toMemberInfoOrNull()
        assertEquals(m, restored)
    }

    @Test
    fun `an unreadable stored binding drops the key but keeps the member`() {
        assertNull(byteArrayOf(0x7F, 0x7F, 0x7F).decodeEncryptionKeyBinding())
        val entity = member(null).toEntity("c", 1L).copy(encryptionKeyBinding = byteArrayOf(0x7F, 0x7F))
        val restored = entity.toMemberInfoOrNull()
        assertArrayEquals(nodeId, restored!!.nodeId)
        assertNull(restored.encryptionKeyBinding)
    }
}
