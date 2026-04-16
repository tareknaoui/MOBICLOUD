package com.mobicloud.data.repository

import android.util.Base64
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignalingRepositoryImplTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var repository: SignalingRepositoryImpl

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val localNodeId = "local-node-id"
    private val localPublicKeyBytes = byteArrayOf(1, 2, 3, 4)
    private val localIdentity = NodeIdentity(localNodeId, localPublicKeyBytes)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        securityRepository = mockk()
        firebaseDatabase = mockk()
        repository = SignalingRepositoryImpl(securityRepository, firebaseDatabase)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Construit un mock DataSnapshot enfant avec tous les champs renseignés.
     * L'ordre de filtrage dans observeRemoteNodes() est :
     *   1. nodeId absent → null
     *   2. nodeId == localNodeId → null   (avant Base64)
     *   3. timestamp absent → null
     *   4. timestamp > TTL → null         (avant Base64)
     *   5. pubKeyB64 / ip / port absents → null
     *   6. Base64.decode + construction Peer
     */
    private fun mockChildSnapshot(
        nodeId: String,
        pubKeyB64: String = "AQID",
        ip: String = "1.2.3.4",
        port: Int = 8080,
        timestamp: Long = System.currentTimeMillis()
    ): DataSnapshot {
        val child = mockk<DataSnapshot>()
        every { child.key } returns nodeId
        every { child.child("nodeId").getValue(String::class.java) } returns nodeId
        every { child.child("publicKeyBase64").getValue(String::class.java) } returns pubKeyB64
        every { child.child("ip").getValue(String::class.java) } returns ip
        every { child.child("port").getValue(Long::class.java) } returns port.toLong()
        every { child.child("timestamp").getValue(Long::class.java) } returns timestamp
        return child
    }

    /**
     * Configure les mocks Firebase pour `observeRemoteNodes()` et retourne
     * la référence "nodes/" ainsi que le slot capturant le ValueEventListener.
     */
    private fun buildNodesRefMock(): Pair<DatabaseReference, CapturingSlot<ValueEventListener>> {
        val rootRef = mockk<DatabaseReference>()
        val nodesRef = mockk<DatabaseReference>()
        val listenerSlot = slot<ValueEventListener>()

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        every { firebaseDatabase.reference } returns rootRef
        every { rootRef.child("nodes") } returns nodesRef
        every { nodesRef.addValueEventListener(capture(listenerSlot)) } returns mockk()
        every { nodesRef.removeEventListener(any<ValueEventListener>()) } just Runs

        return nodesRef to listenerSlot
    }

    // -------------------------------------------------------------------------
    // Test 1 : filtrage TTL — entrées > 60 s ignorées
    // -------------------------------------------------------------------------

    @Test
    fun `observeRemoteNodes filtre les entrees plus vieilles que 60s`() =
        testScope.runTest {
            val (_, listenerSlot) = buildNodesRefMock()

            val staleTimestamp = System.currentTimeMillis() - 70_000L
            val parentSnapshot = mockk<DataSnapshot>()
            val staleChild = mockChildSnapshot(
                nodeId = "remote-node",          // ≠ localNodeId → ne sera pas exclu par ce filtre
                timestamp = staleTimestamp        // > 60 s → doit être filtré
            )
            every { parentSnapshot.children } returns listOf(staleChild)

            val results = mutableListOf<List<Peer>>()
            val job = launch { repository.observeRemoteNodes().collect { results.add(it) } }

            advanceUntilIdle()
            assertTrue("Le ValueEventListener doit être enregistré", listenerSlot.isCaptured)

            listenerSlot.captured.onDataChange(parentSnapshot)
            advanceUntilIdle()
            job.cancel()

            assertTrue("Au moins un résultat attendu", results.isNotEmpty())
            assertTrue(
                "L'entrée périmée (TTL expiré) doit être filtrée → liste vide",
                results.last().isEmpty()
            )
        }

    // -------------------------------------------------------------------------
    // Test 2 : exclusion du nœud local
    // -------------------------------------------------------------------------

    @Test
    fun `observeRemoteNodes exclut le noeud local`() =
        testScope.runTest {
            val (_, listenerSlot) = buildNodesRefMock()

            val parentSnapshot = mockk<DataSnapshot>()
            val localChild = mockChildSnapshot(
                nodeId = localNodeId,                    // même nodeId que le nœud local
                timestamp = System.currentTimeMillis()   // timestamp frais
            )
            every { parentSnapshot.children } returns listOf(localChild)

            val results = mutableListOf<List<Peer>>()
            val job = launch { repository.observeRemoteNodes().collect { results.add(it) } }

            advanceUntilIdle()
            listenerSlot.captured.onDataChange(parentSnapshot)
            advanceUntilIdle()
            job.cancel()

            assertTrue("Au moins un résultat attendu", results.isNotEmpty())
            assertTrue(
                "Le nœud local doit être exclu → liste vide",
                results.last().isEmpty()
            )
        }

    // -------------------------------------------------------------------------
    // Test 3 : registerNode retourne Result.failure si une exception est levée
    // -------------------------------------------------------------------------

    @Test
    fun `registerNode retourne Result_failure si Firebase lance une exception`() =
        testScope.runTest {
            coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)

            mockkStatic(Base64::class)
            every { Base64.encodeToString(any(), any()) } returns "AQID"

            // Faire lever une exception lors de l'accès à la référence Firebase
            val rootRef = mockk<DatabaseReference>()
            val nodesRef = mockk<DatabaseReference>()
            every { firebaseDatabase.reference } returns rootRef
            every { rootRef.child("nodes") } returns nodesRef
            every { nodesRef.child(localNodeId) } throws RuntimeException("Firebase indisponible")

            val result = repository.registerNode("1.2.3.4", 8080)

            assertTrue("registerNode doit retourner Result.failure", result.isFailure)
        }

    // -------------------------------------------------------------------------
    // Test 4 : construction correcte d'un Peer avec source = REMOTE_FIREBASE
    // -------------------------------------------------------------------------

    @Test
    fun `observeRemoteNodes construit Peer avec source REMOTE_FIREBASE`() =
        testScope.runTest {
            val (_, listenerSlot) = buildNodesRefMock()

            val remotePublicKeyBytes = byteArrayOf(10, 20, 30)
            mockkStatic(Base64::class)
            every { Base64.decode("AQID", Base64.NO_WRAP) } returns remotePublicKeyBytes

            val freshTimestamp = System.currentTimeMillis()
            val parentSnapshot = mockk<DataSnapshot>()
            val remoteChild = mockChildSnapshot(
                nodeId = "remote-node-id",
                pubKeyB64 = "AQID",
                ip = "5.6.7.8",
                port = 9090,
                timestamp = freshTimestamp
            )
            every { parentSnapshot.children } returns listOf(remoteChild)

            val results = mutableListOf<List<Peer>>()
            val job = launch { repository.observeRemoteNodes().collect { results.add(it) } }

            advanceUntilIdle()
            listenerSlot.captured.onDataChange(parentSnapshot)
            advanceUntilIdle()
            job.cancel()

            assertTrue("Au moins un résultat attendu", results.isNotEmpty())
            val peers = results.last()
            assertEquals("Un seul pair attendu", 1, peers.size)

            val peer = peers.first()
            assertEquals(
                "La source doit être REMOTE_FIREBASE",
                DiscoverySource.REMOTE_FIREBASE,
                peer.source
            )
            assertEquals("remote-node-id", peer.identity.nodeId)
            assertEquals("5.6.7.8", peer.ipAddress)
            assertEquals(9090, peer.port)
        }
}
