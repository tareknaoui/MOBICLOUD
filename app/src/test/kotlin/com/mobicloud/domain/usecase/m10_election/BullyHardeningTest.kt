package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.ElectionEvent
import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.electionSignedBytes
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.LocalRepairBuffer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests adversariaux qui prouvent que les 4 protections du protocole Bully bloquent
 * l'attaque correspondante :
 *
 *  H1 — Score signé : un attaquant qui rejoue un payload avec score gonflé est rejeté.
 *  H2 — verify ELECTION/ALIVE : un payload non signé n'est pas accepté.
 *  H3 — Anti-replay timestamp : un payload trop vieux est rejeté.
 *  H4 — Cooldown persisté : kill+restart ne bypass pas le cooldown.
 */
class BullyHardeningTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var trustScoreProvider: ITrustScoreProvider
    private lateinit var peerRepository: PeerRepository
    private lateinit var networkClient: IElectionNetworkClient
    private lateinit var localRepairBuffer: LocalRepairBuffer
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var wifiNetworkRepository: WifiNetworkRepository

    private val localNodeId = "local-node-AAA"
    private val localIdentity = NodeIdentity(localNodeId, ByteArray(65))
    private val attackerNodeId = "attacker-BBB"
    private val attackerIdentity = NodeIdentity(attackerNodeId, ByteArray(65))

    private val incomingFlow = MutableSharedFlow<ElectionPayload>()

    @Before
    fun setUp() {
        securityRepository = mockk()
        trustScoreProvider = mockk()
        peerRepository = mockk()
        networkClient = mockk()
        localRepairBuffer = mockk()
        networkEventRepository = mockk()
        nodeSettingsRepository = mockk()
        wifiNetworkRepository = mockk()

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { trustScoreProvider.getTrustScore(any()) } returns 0
        every { networkClient.incomingMessages } returns incomingFlow
        coEvery { localRepairBuffer.drain() } returns emptyList()
        coEvery { networkEventRepository.pushEvent(any()) } returns Unit
        every { peerRepository.peers } returns MutableStateFlow(listOf(activeAttackerPeer()))
        every { wifiNetworkRepository.getCurrentSsid() } returns null
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 2_000_000_000L,
            clusterId = ""
        )
    }

    private fun buildUseCase(esm: ElectionStateManager = ElectionStateManager()) =
        ProcessIncomingElectionEventUseCase(
            securityRepository = securityRepository,
            trustScoreProvider = trustScoreProvider,
            peerRepository = peerRepository,
            networkClient = networkClient,
            electionStateManager = esm,
            localRepairBuffer = localRepairBuffer,
            networkEventRepository = networkEventRepository,
            nodeSettingsRepository = nodeSettingsRepository,
            wifiNetworkRepository = wifiNetworkRepository
        )

    private fun activeAttackerPeer() = Peer(
        identity = attackerIdentity,
        lastSeenTimestampMs = System.currentTimeMillis(),
        isActive = true,
        isSuperPair = true // pour permettre le test ABDICATION/COORDINATOR
    )

    // ── H1 — Score gonflé : la signature ne couvre pas le score → rejet ─────────

    @Test
    fun `H1 - payload avec score modifie est rejete car signature ne matche pas`() = runTest {
        // Mock signature verifier : true UNIQUEMENT si data == bytes canoniques attendus
        // (avec le score d'origine 0.1f). Si le score a ete modifie a 999f, les bytes
        // signes seront differents -> verifier retourne false -> rejet.
        val originalScore = 0.1f
        val timestampMs = System.currentTimeMillis()
        val expectedBytes = electionSignedBytes(
            ElectionMessageType.ELECTION,
            attackerNodeId,
            originalScore,
            "",
            timestampMs
        )
        coEvery {
            securityRepository.verifySignature(any(), any(), any())
        } answers {
            val data = firstArg<ByteArray>()
            Result.success(data.contentEquals(expectedBytes))
        }

        val useCase = buildUseCase()
        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            result = useCase().first()
        }
        // Attaque : payload avec score gonfle a 999f mais timestamp et signature originaux
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = attackerNodeId,
                type = ElectionMessageType.ELECTION,
                reliabilityScore = 999f, // gonfle pour tenter d'etre prioritaire
                signatureBytes = ByteArray(0),
                timestampMs = timestampMs
            )
        )
        job.join()

        assertTrue("Score gonfle doit etre rejete", result!!.isFailure)
        assertTrue(
            "Erreur doit mentionner signature invalide",
            result!!.exceptionOrNull()?.message?.contains("signature", ignoreCase = true) == true
        )
    }

    // ── H2 — ELECTION/ALIVE doit verifier la signature (avant : ignore) ─────────

    @Test
    fun `H2 - ELECTION sans signature valide est rejete`() = runTest {
        // verifySignature retourne TOUJOURS false (pas de signature valide)
        coEvery {
            securityRepository.verifySignature(any(), any(), any())
        } returns Result.success(false)

        val useCase = buildUseCase()
        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            result = useCase().first()
        }
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = attackerNodeId,
                type = ElectionMessageType.ELECTION,
                reliabilityScore = 0.1f,
                signatureBytes = ByteArray(0),
                timestampMs = System.currentTimeMillis()
            )
        )
        job.join()

        assertTrue("ELECTION non signe doit etre rejete", result!!.isFailure)
    }

    @Test
    fun `H2 - ALIVE sans signature valide est rejete`() = runTest {
        coEvery {
            securityRepository.verifySignature(any(), any(), any())
        } returns Result.success(false)

        val useCase = buildUseCase()
        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            result = useCase().first()
        }
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = attackerNodeId,
                type = ElectionMessageType.ALIVE,
                reliabilityScore = 0.5f,
                signatureBytes = ByteArray(0),
                timestampMs = System.currentTimeMillis()
            )
        )
        job.join()

        assertTrue("ALIVE non signe doit etre rejete", result!!.isFailure)
    }

    // ── H3 — Replay : payload trop vieux rejete ─────────────────────────────────

    @Test
    fun `H3 - payload hors fenetre timestamp est rejete (replay)`() = runTest {
        coEvery {
            securityRepository.verifySignature(any(), any(), any())
        } returns Result.success(true) // signature valide pour isoler le check timestamp

        val useCase = buildUseCase()
        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            result = useCase().first()
        }
        // Timestamp 5 minutes dans le passe (>> fenetre 30s)
        val ancientTimestamp = System.currentTimeMillis() - 5 * 60_000L
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = attackerNodeId,
                type = ElectionMessageType.COORDINATOR,
                reliabilityScore = 0.9f,
                signatureBytes = ByteArray(0),
                timestampMs = ancientTimestamp
            )
        )
        job.join()

        assertTrue("Payload replay (vieux) doit etre rejete", result!!.isFailure)
        assertTrue(
            "Erreur doit mentionner fenetre/replay",
            result!!.exceptionOrNull()?.message?.let {
                it.contains("fenetre") || it.contains("replay") || it.contains("hors")
            } == true
        )
    }

    @Test
    fun `H3 - payload dans la fenetre est accepte`() = runTest {
        coEvery {
            securityRepository.verifySignature(any(), any(), any())
        } returns Result.success(true)
        coEvery {
            peerRepository.registerOrUpdatePeer(any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        val useCase = buildUseCase()
        var result: Result<ElectionEvent>? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            result = useCase().first()
        }
        // Timestamp courant = dans la fenetre
        incomingFlow.emit(
            ElectionPayload(
                senderNodeId = attackerNodeId,
                type = ElectionMessageType.COORDINATOR,
                reliabilityScore = 0.9f,
                signatureBytes = ByteArray(0),
                timestampMs = System.currentTimeMillis()
            )
        )
        job.join()

        assertTrue("Payload frais doit etre accepte", result!!.isSuccess)
    }

    // ── H4 — Cooldown persiste a travers une recreation d'instance (kill+restart) ─

    @Test
    fun `H4 - cooldown persiste a travers une nouvelle instance d ElectionStateManager`() {
        // Simule kill+restart : un meme CooldownStore (= le SharedPrefs sur disque)
        // est partage entre deux instances successives.
        val sharedStore = InMemoryCooldownStore()

        val esm1 = ElectionStateManager(sharedStore)
        esm1.setCooldown(5 * 60_000L) // 5 min

        assertTrue("Instance #1 doit etre en cooldown", esm1.isInCooldown())
        val savedUntil = esm1.cooldownUntil()
        assertNotEquals("Cooldown non zero", 0L, savedUntil)

        // KILL: instance #1 disparait. RESTART: nouvelle instance lit le store.
        val esm2 = ElectionStateManager(sharedStore)

        assertEquals(
            "Instance #2 doit relire le timestamp persiste",
            savedUntil,
            esm2.cooldownUntil()
        )
        assertTrue(
            "Instance #2 doit toujours etre en cooldown apres restart",
            esm2.isInCooldown()
        )
    }

    @Test
    fun `H4 - sans persistance le cooldown est perdu (controle negatif)`() {
        // Controle : une instance fraiche avec un store VIDE n'est pas en cooldown.
        // Confirme que le test H4 ci-dessus mesure bien la persistance et pas autre chose.
        val freshStore = InMemoryCooldownStore()
        val esm = ElectionStateManager(freshStore)

        assertFalse(
            "Une instance neuve sans data persistee ne doit pas etre en cooldown",
            esm.isInCooldown()
        )
    }
}
