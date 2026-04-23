package com.mobicloud.presentation.explorer

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.mobicloud.core.security.FragmentCipherUseCase
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.WrappedFileMasterKey
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.LocalizeFileBlocksUseCase
import com.mobicloud.domain.models.EncryptionIdentity
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ErasureProgressViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var catalogRepository: CatalogRepository
    private lateinit var gossipSyncUseCase: GossipSyncUseCase
    private lateinit var encodeErasureFragmentsUseCase: EncodeErasureFragmentsUseCase
    private lateinit var fragmentCipherUseCase: FragmentCipherUseCase
    private lateinit var distributeEncryptedBlocksUseCase: DistributeEncryptedBlocksUseCase
    private lateinit var securityRepository: SecurityRepository
    private lateinit var localizeFileBlocksUseCase: LocalizeFileBlocksUseCase
    private lateinit var downloadFileBlocksUseCase: com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
    private lateinit var assembleDownloadedFileUseCase: AssembleDownloadedFileUseCase
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private val catalogFlow = MutableStateFlow<List<CatalogEntry>>(emptyList())
    private val fakeUri: Uri = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        catalogRepository = mockk()
        gossipSyncUseCase = mockk(relaxed = true)
        encodeErasureFragmentsUseCase = mockk()
        fragmentCipherUseCase = mockk()
        distributeEncryptedBlocksUseCase = mockk()
        securityRepository = mockk()
        localizeFileBlocksUseCase = mockk(relaxed = true)
        downloadFileBlocksUseCase = mockk(relaxed = true)
        assembleDownloadedFileUseCase = mockk(relaxed = true)
        context = mockk()
        contentResolver = mockk()

        every { catalogRepository.getAllEntriesFlow() } returns catalogFlow
        every { context.contentResolver } returns contentResolver
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        every { contentResolver.openInputStream(any()) } returns
            "test content".toByteArray().inputStream()
        // Story 6.3 — encrypt path consomme désormais getEncryptionIdentity() (clé EC dédiée
        // au chiffrement, distincte de l'identité Keystore SIGN/VERIFY).
        coEvery { securityRepository.getEncryptionIdentity() } returns Result.success(
            EncryptionIdentity(Random.nextBytes(91), mockk(relaxed = true))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ExplorerViewModel(
        catalogRepository = catalogRepository,
        gossipSyncUseCase = gossipSyncUseCase,
        encodeErasureFragmentsUseCase = encodeErasureFragmentsUseCase,
        fragmentCipherUseCase = fragmentCipherUseCase,
        distributeEncryptedBlocksUseCase = distributeEncryptedBlocksUseCase,
        securityRepository = securityRepository,
        localizeFileBlocksUseCase = localizeFileBlocksUseCase,
        downloadFileBlocksUseCase = downloadFileBlocksUseCase,
        assembleDownloadedFileUseCase = assembleDownloadedFileUseCase,
        context = context
    )

    private fun fakeErasureFragments() =
        (0 until 4).map {
            ErasureFragment(index = it, isParity = false, data = Random.nextBytes(50), originalFileSize = 100L)
        } + (4 until 6).map {
            ErasureFragment(index = it, isParity = true, data = Random.nextBytes(50), originalFileSize = 100L)
        }

    private fun fakeBundle() = EncryptedBundle(
        encryptedFragments = (0 until 4).map {
            EncryptedFragment(it, false, Random.nextBytes(50), Random.nextBytes(12), 100L)
        } + (4 until 6).map {
            EncryptedFragment(it, true, Random.nextBytes(50), Random.nextBytes(12), 100L)
        },
        wrappedFileMasterKey = WrappedFileMasterKey(Random.nextBytes(65), Random.nextBytes(12), Random.nextBytes(48))
    )

    private fun fakeEntry(nodeCount: Int = 6) = CatalogEntry(
        fileHash = "abc123def4567890123456789012345678901234567890123456789012345678",
        ownerPubKeyHash = "ownerHash01234567",
        versionClock = System.currentTimeMillis(),
        fragmentLocations = (0 until nodeCount).map { FragmentLocation(it, "h$it", listOf("n$it")) }
    )

    // withContext(Dispatchers.IO) dispatche sur de vrais threads même avec StandardTestDispatcher.
    // advanceUntilIdle() peut retourner avant que ces threads aient posté leurs continuations.
    // Thread.sleep(100) laisse le temps aux threads IO de poster, puis advanceUntilIdle() les exécute.
    // Limitation CI : le sleep réel peut être trop court sous forte charge. Fix structurel :
    // injecter un TestDispatcher pour Dispatchers.IO dans le ViewModel (refactoring futur).
    private fun TestScope.advanceWithIoFlush() {
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()
    }

    // Test 1 — Séquence d'états : Encoding → Encrypting → Distributing(0,6,4) → ... → Success
    @Test
    fun `storeFile émet les phases Encoding Encrypting Distributing Success dans l ordre`() = runTest {
        val bundle = fakeBundle()
        val entry = fakeEntry()
        val states = mutableListOf<StoreState>()

        // yield() crée des points de suspension entre les changements d'état
        // pour que le collector StateFlow puisse capturer chaque état intermédiaire
        coEvery { encodeErasureFragmentsUseCase(any(), any()) } coAnswers {
            yield()
            Result.success(fakeErasureFragments())
        }
        coEvery { securityRepository.getIdentity() } coAnswers {
            yield()
            Result.success(NodeIdentity("nodeId", Random.nextBytes(65)))
        }
        coEvery { fragmentCipherUseCase.encrypt(any(), any()) } coAnswers {
            yield()
            Result.success(bundle)
        }
        coEvery { distributeEncryptedBlocksUseCase.distribute(any(), any(), any(), any()) } coAnswers {
            yield()
            val callback = arg<((Int, Boolean) -> Unit)?>(3)
            (0 until 6).forEach { callback?.invoke(it, true) }
            Result.success(entry)
        }

        val viewModel = createViewModel()
        val collectJob = launch { viewModel.storeState.collect { states.add(it) } }

        viewModel.storeFile(fakeUri)
        advanceWithIoFlush()
        collectJob.cancel()

        val nonIdle = states.filter { it !is StoreState.Idle }

        assertTrue("Encoding doit être émis — états capturés: $nonIdle",
            nonIdle.any { it is StoreState.InProgress.Encoding })
        assertTrue("Encrypting doit être émis — états capturés: $nonIdle",
            nonIdle.any { it is StoreState.InProgress.Encrypting })
        assertTrue("Distributing doit être émis — états capturés: $nonIdle",
            nonIdle.any { it is StoreState.InProgress.Distributing })
        assertTrue("Success doit être émis — états capturés: $nonIdle",
            nonIdle.any { it is StoreState.Success })

        val encodingIdx = nonIdle.indexOfFirst { it is StoreState.InProgress.Encoding }
        val encryptingIdx = nonIdle.indexOfFirst { it is StoreState.InProgress.Encrypting }
        val distributingIdx = nonIdle.indexOfFirst { it is StoreState.InProgress.Distributing }
        val successIdx = nonIdle.indexOfFirst { it is StoreState.Success }

        assertTrue("Encoding avant Encrypting", encodingIdx < encryptingIdx)
        assertTrue("Encrypting avant Distributing", encryptingIdx < distributingIdx)
        assertTrue("Distributing avant Success", distributingIdx < successIdx)

        val firstDistributing = nonIdle.first { it is StoreState.InProgress.Distributing }
            as StoreState.InProgress.Distributing
        assertEquals("total = K+N = 6", 6, firstDistributing.total)
        assertEquals("dataBlockCount = K = 4", 4, firstDistributing.dataBlockCount)
        assertEquals("confirmed initial = 0", 0, firstDistributing.confirmed)

        val successState = nonIdle.first { it is StoreState.Success } as StoreState.Success
        assertEquals("nodeCount = 6 nœuds confirmés", 6, successState.nodeCount)
    }

    // Test 2 — onBlockResult(index=2, success=false) → failedIndices=[2], confirmed inchangé
    @Test
    fun `onBlockResult false pour index 2 provoque failedIndices 2 et confirmed inchangé`() = runTest {
        val bundle = fakeBundle()
        val entry = fakeEntry()
        val states = mutableListOf<StoreState>()

        coEvery { encodeErasureFragmentsUseCase(any(), any()) } returns Result.success(fakeErasureFragments())
        coEvery { securityRepository.getIdentity() } returns
            Result.success(NodeIdentity("nodeId", Random.nextBytes(65)))
        coEvery { fragmentCipherUseCase.encrypt(any(), any()) } returns Result.success(bundle)

        // yield() après chaque callback pour capturer l'état intermédiaire post-échec
        coEvery { distributeEncryptedBlocksUseCase.distribute(any(), any(), any(), any()) } coAnswers {
            val callback = arg<((Int, Boolean) -> Unit)?>(3)
            yield()
            callback?.invoke(0, true)
            yield()
            callback?.invoke(1, true)
            yield()
            callback?.invoke(2, false)  // bloc 2 échoue — confirmed reste à 2
            yield()                     // collector voit Distributing(confirmed=2, failedIndices=[2])
            callback?.invoke(3, true)
            callback?.invoke(4, true)
            callback?.invoke(5, true)
            Result.success(entry)
        }

        val viewModel = createViewModel()
        val collectJob = launch { viewModel.storeState.collect { states.add(it) } }

        viewModel.storeFile(fakeUri)
        advanceWithIoFlush()
        collectJob.cancel()

        val distributingStates = states.filterIsInstance<StoreState.InProgress.Distributing>()
        val stateAfterFailure = distributingStates.firstOrNull { 2 in it.failedIndices && it.confirmed == 2 }

        assertNotNull(
            "Un état Distributing(confirmed=2, failedIndices=[2]) doit exister.\n" +
                "États Distributing observés: ${distributingStates.map { "c=${it.confirmed},f=${it.failedIndices}" }}",
            stateAfterFailure
        )
        assertEquals("failedIndices = {2}", setOf(2), stateAfterFailure!!.failedIndices)
        assertEquals("confirmed = 2 (bloc 2 en échec ne compte pas)", 2, stateAfterFailure.confirmed)
    }

    // Test 3 — Guard anti-concurrence : appel de storeFile() pendant InProgress est ignoré
    @Test
    fun `storeFile pendant InProgress est ignoré`() = runTest {
        val bundle = fakeBundle()
        val entry = fakeEntry()

        coEvery { securityRepository.getIdentity() } returns
            Result.success(NodeIdentity("nodeId", Random.nextBytes(65)))
        coEvery { fragmentCipherUseCase.encrypt(any(), any()) } returns Result.success(bundle)
        coEvery { distributeEncryptedBlocksUseCase.distribute(any(), any(), any(), any()) } returns
            Result.success(entry)

        // Utilise CompletableDeferred pour maintenir la coroutine en état InProgress.Encoding
        val proceed = CompletableDeferred<Unit>()
        coEvery { encodeErasureFragmentsUseCase(any(), any()) } coAnswers {
            proceed.await()  // suspend jusqu'à déblocage explicite
            Result.success(fakeErasureFragments())
        }

        val viewModel = createViewModel()

        // Premier appel : démarre le pipeline, bloqué à encode (proceed.await())
        viewModel.storeFile(fakeUri)
        // Flush IO puis avance jusqu'à ce que encode soit suspendu sur proceed.await()
        advanceWithIoFlush()

        assertTrue(
            "L'état doit être InProgress.Encoding (était: ${viewModel.storeState.value})",
            viewModel.storeState.value is StoreState.InProgress
        )

        // Deuxième appel pendant InProgress — le guard synchrone le bloque
        viewModel.storeFile(fakeUri)

        // encode ne doit avoir été appelé qu'une seule fois (le 2ème appel a été ignoré)
        coVerify(exactly = 1) { encodeErasureFragmentsUseCase(any(), any()) }

        // Déblocage pour que la coroutine se termine proprement
        proceed.complete(Unit)
        advanceWithIoFlush()
    }

    // Test 4 — Fichier trop volumineux : storeFile() passe à Error sans lancer le pipeline
    @Test
    fun `storeFile rejette un fichier plus grand que 100 Mo`() = runTest {
        val cursor: Cursor = mockk()
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndexOrThrow(any()) } returns 0
        every { cursor.getLong(0) } returns 105_000_000L
        every { cursor.close() } returns Unit
        every { contentResolver.query(any(), any(), null, null, null) } returns cursor

        val viewModel = createViewModel()
        viewModel.storeFile(fakeUri)
        advanceWithIoFlush()

        val state = viewModel.storeState.value
        assertTrue(
            "L'état doit être Error pour fichier > 100 Mo (était: $state)",
            state is StoreState.Error
        )
        assertTrue(
            "Le message doit mentionner la taille max (100)",
            (state as StoreState.Error).message.contains("100")
        )
    }
}
