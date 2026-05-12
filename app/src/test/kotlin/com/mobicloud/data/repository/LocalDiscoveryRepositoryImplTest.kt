package com.mobicloud.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.HelloMessage
import com.mobicloud.domain.models.HelloPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.LocationRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.mobicloud.data.repository.LocalDiscoveryRepositoryImpl.Companion.MULTICAST_TIMEOUT_MS
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Tests JVM pour la logique métier de LocalDiscoveryRepositoryImpl.
 *
 * Stratégie :
 * - signPayload est surchargée pour utiliser un JVM EC P-256 (pas AndroidKeystore)
 * - processIncomingBytes et logFallbackIfNeeded sont internes et testés directement
 * - verifySignature est interne et testée directement
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class LocalDiscoveryRepositoryImplTest {

    private lateinit var identityRepository: IdentityRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope

    companion object {
        // P12 — clés générées une fois par classe (companion = JVM static) au lieu de par instance de test
        private val testKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        private val peerKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        private fun generateNodeId(publicKeyBytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
                .take(8).joinToString("") { "%02x".format(it) }
    }

    // DN3 — nodeId dérivé cryptographiquement de publicKeyBytes (SHA-256 take 8 bytes)
    private val localIdentity = NodeIdentity(
        nodeId = generateNodeId(testKeyPair.public.encoded),
        publicKeyBytes = testKeyPair.public.encoded,
        reliabilityScore = 0.9f
    )

    private val peerIdentity = NodeIdentity(
        nodeId = generateNodeId(peerKeyPair.public.encoded),
        publicKeyBytes = peerKeyPair.public.encoded,
        reliabilityScore = 0.8f
    )

    private inner class TestableLocalDiscoveryRepositoryImpl : LocalDiscoveryRepositoryImpl(
        identityRepository, peerRepository, networkEventRepository, locationRepository, context, scope
    ) {
        var lastPayloadSigned: ByteArray? = null

        override suspend fun signPayload(payloadBytes: ByteArray): ByteArray {
            lastPayloadSigned = payloadBytes
            return Signature.getInstance("SHA256withECDSA").apply {
                initSign(testKeyPair.private)
                update(payloadBytes)
            }.sign()
        }
    }

    private lateinit var repository: TestableLocalDiscoveryRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L

        identityRepository = mockk()
        peerRepository = mockk(relaxed = true)
        networkEventRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

        coEvery { identityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        repository = TestableLocalDiscoveryRepositoryImpl()
    }

    // P11 — annuler le scope de test pour éviter les fuites de coroutines entre tests
    @After
    fun tearDown() {
        scope.cancel()
    }

    // ─── Test 1 : émission — signPayload appelé avec les bons bytes du payload ──

    @Test
    fun `signPayload est appelé avec les bytes encodés du payload HelloPayload`() = runTest {
        val payload = HelloPayload(
            nodeId = localIdentity.nodeId,
            publicKeyBytes = localIdentity.publicKeyBytes,
            tcpPort = 9090,
            reliabilityScore = localIdentity.reliabilityScore
        )
        val payloadBytes = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), payload)

        val signature = repository.signPayload(payloadBytes)

        assertTrue("signPayload doit avoir reçu les bytes du payload", repository.lastPayloadSigned != null)
        assertTrue(
            "Les bytes signés doivent correspondre à l'encodage ProtoBuf du payload",
            repository.lastPayloadSigned!!.contentEquals(payloadBytes)
        )
        assertTrue(
            "La signature produite par signPayload doit être vérifiable",
            repository.verifySignature(payloadBytes, signature, testKeyPair.public.encoded)
        )
    }

    // ─── Test 2 : réception valide → registerOrUpdatePeer avec LAN_MULTICAST ──

    @Test
    fun `réception HELLO valide insère le pair avec source LAN_MULTICAST`() = runTest {
        val bytes = buildSignedHelloBytes(peerIdentity, peerKeyPair.private)

        val updated = repository.processIncomingBytes(
            bytes = bytes,
            sourceAddress = "192.168.1.10",
            localNodeId = localIdentity.nodeId
        )

        assertTrue("processIncomingBytes doit retourner true pour un HELLO valide", updated)

        val identitySlot = slot<NodeIdentity>()
        val sourceSlot = slot<DiscoverySource>()
        coVerify(exactly = 1) {
            peerRepository.registerOrUpdatePeer(
                identity = capture(identitySlot),
                timestampMs = any(),
                source = capture(sourceSlot),
                ipAddress = any(),
                port = any(),
                isSuperPair = any()
            )
        }
        assertTrue(
            "La source doit être LAN_MULTICAST",
            sourceSlot.captured == DiscoverySource.LAN_MULTICAST
        )
        assertTrue(
            "Le nodeId du pair enregistré doit correspondre",
            identitySlot.captured.nodeId == peerIdentity.nodeId
        )
    }

    // ─── Test 3 : signature invalide → registerOrUpdatePeer NON appelé ──

    @Test
    fun `réception HELLO avec signature invalide ne doit pas appeler registerOrUpdatePeer`() = runTest {
        val payload = HelloPayload(
            nodeId = peerIdentity.nodeId,
            publicKeyBytes = peerIdentity.publicKeyBytes,
            tcpPort = 9090,
            reliabilityScore = peerIdentity.reliabilityScore
        )
        val tamperedSignature = ByteArray(64) { 0xFF.toByte() }
        val message = HelloMessage(payload = payload, signature = tamperedSignature)
        val bytes = MobiCloudProtoBuf.encodeToByteArray(HelloMessage.serializer(), message)

        val updated = repository.processIncomingBytes(
            bytes = bytes,
            sourceAddress = "192.168.1.10",
            localNodeId = localIdentity.nodeId
        )

        assertFalse("processIncomingBytes doit retourner false pour une signature invalide", updated)
        coVerify(exactly = 0) {
            peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ─── Test 4 : propres datagrams filtrés (nodeId == localNodeId) ──

    @Test
    fun `réception de son propre HELLO est ignorée sans appeler registerOrUpdatePeer`() = runTest {
        val bytes = buildSignedHelloBytes(localIdentity, testKeyPair.private)

        val updated = repository.processIncomingBytes(
            bytes = bytes,
            sourceAddress = "192.168.1.1",
            localNodeId = localIdentity.nodeId
        )

        assertFalse("Les propres datagrams doivent être filtrés", updated)
        coVerify(exactly = 0) {
            peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ─── Test 5 : verifySignature — roundtrip JVM EC P-256 ──

    @Test
    fun `verifySignature retourne true pour une signature EC P-256 valide`() {
        val data = "test-payload".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(peerKeyPair.private)
            update(data)
        }.sign()

        assertTrue(
            repository.verifySignature(data, signature, peerKeyPair.public.encoded)
        )
    }

    @Test
    fun `verifySignature retourne false pour des bytes de signature corrompus`() {
        val data = "test-payload".toByteArray()
        val corruptedSig = ByteArray(64) { 0x00 }

        assertFalse(
            repository.verifySignature(data, corruptedSig, peerKeyPair.public.encoded)
        )
    }

    @Test
    fun `verifySignature retourne false si les données ont été altérées après signature`() {
        val data = "original-payload".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(peerKeyPair.private)
            update(data)
        }.sign()

        val tamperedData = "tampered-payload".toByteArray()
        assertFalse(
            repository.verifySignature(tamperedData, signature, peerKeyPair.public.encoded)
        )
    }

    // ─── Test 6 : 30s sans HELLO → pushEvent fallback (P3 — test via logFallbackIfNeeded) ──

    @Test
    fun `logFallbackIfNeeded déclenche pushEvent après MULTICAST_TIMEOUT_MS sans HELLO valide`() = runTest {
        // Simuler un temps courant > lastValidHelloMs + MULTICAST_TIMEOUT_MS
        val oldTimestamp = 1000L
        every { SystemClock.elapsedRealtime() } returns oldTimestamp + MULTICAST_TIMEOUT_MS + 1000L

        val result = repository.logFallbackIfNeeded(
            lastValidHelloMs = oldTimestamp,
            alreadyLogged = false
        )

        assertTrue("logFallbackIfNeeded doit retourner true après le timeout", result)
        coVerify(exactly = 1) {
            networkEventRepository.pushEvent("Multicast indisponible — fallback Relais HA seul")
        }
    }

    @Test
    fun `logFallbackIfNeeded ne déclenche pas pushEvent si le timeout n'est pas atteint`() = runTest {
        val now = 1000L
        every { SystemClock.elapsedRealtime() } returns now

        val result = repository.logFallbackIfNeeded(
            lastValidHelloMs = now - 5_000L,
            alreadyLogged = false
        )

        assertFalse("logFallbackIfNeeded doit retourner false si le timeout n'est pas atteint", result)
        coVerify(exactly = 0) {
            networkEventRepository.pushEvent(any())
        }
    }

    @Test
    fun `logFallbackIfNeeded ne rappelle pas pushEvent si alreadyLogged est true`() = runTest {
        every { SystemClock.elapsedRealtime() } returns 1_000_000L

        val result = repository.logFallbackIfNeeded(
            lastValidHelloMs = 0L,
            alreadyLogged = true
        )

        assertTrue("logFallbackIfNeeded doit retourner true si déjà loggé", result)
        coVerify(exactly = 0) {
            networkEventRepository.pushEvent(any())
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @OptIn(ExperimentalSerializationApi::class)
    private fun buildSignedHelloBytes(
        identity: NodeIdentity,
        privateKey: java.security.PrivateKey,
        tcpPort: Int = 9090
    ): ByteArray {
        val payload = HelloPayload(
            nodeId = identity.nodeId,
            publicKeyBytes = identity.publicKeyBytes,
            tcpPort = tcpPort,
            reliabilityScore = identity.reliabilityScore
        )
        val payloadBytes = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), payload)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(payloadBytes)
        }.sign()
        val message = HelloMessage(payload = payload, signature = signature)
        return MobiCloudProtoBuf.encodeToByteArray(HelloMessage.serializer(), message)
    }
}
