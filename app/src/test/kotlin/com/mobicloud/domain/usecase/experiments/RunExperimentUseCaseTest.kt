package com.mobicloud.domain.usecase.experiments

import com.mobicloud.core.security.SealedFile
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.EncryptionIdentity
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.models.WrappedFileMasterKey
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.LocalizeFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadProgressState
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncryptThenEncodeUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.OptimalPeersResult
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.PeerSelectionException
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.SelectOptimalPeersUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RunExperimentUseCaseTest {

    @get:Rule val tmp = TemporaryFolder()

    private val select = mockk<SelectOptimalPeersUseCase>()
    private val encryptThenEncode = mockk<EncryptThenEncodeUseCase>()
    private val distribute = mockk<DistributeEncryptedBlocksUseCase>()
    private val security = mockk<SecurityRepository>()
    private val localize = mockk<LocalizeFileBlocksUseCase>()
    private val download = mockk<DownloadFileBlocksUseCase>()
    private val assemble = mockk<AssembleDownloadedFileUseCase>()
    private val catalog = mockk<CatalogRepository>()

    private val records = mutableListOf<MeasurementRecord>()
    private val sink = object : MeasurementSink {
        override suspend fun append(record: MeasurementRecord) { records += record }
        override val location = "memory"
    }
    private val reading = EnergyReading(1_500.0, 300.0, 100, 12, "uA", false)
    private val meter = object : EnergyMeter {
        override suspend fun <T> measure(block: suspend () -> T): Pair<T, EnergyReading> = block() to reading
    }
    private lateinit var scratch: File
    private val environment by lazy {
        object : ExperimentEnvironment {
            override val deviceModel = "Test Phone"
            override val androidApi = 34
            override fun networkType() = "WIFI"
            override fun batteryPercent() = 80
            override fun scratchDir() = scratch
        }
    }

    private val entry = CatalogEntry(
        fileHash = "h", ownerPubKeyHash = "o", versionClock = 1,
        fragmentLocations = listOf(
            FragmentLocation(0, "f0", listOf("a")),
            FragmentLocation(1, "f1", listOf("b")),
            FragmentLocation(2, "f2", listOf("c", "a")) // réplique secondaire
        ),
        k = 2, n = 1
    )

    private fun useCase() = RunExperimentUseCase(
        select, encryptThenEncode, distribute, security, localize, download, assemble, catalog, meter, sink, environment
    )

    @Before
    fun setUp() {
        scratch = tmp.newFolder("scratch")
        coEvery { select(any(), any(), any()) } returns Result.success(OptimalPeersResult(ErasureParameters(2, 1), emptyList()))
        coEvery { security.getEncryptionIdentity() } returns Result.success(EncryptionIdentity(ByteArray(65) { 1 }, mockk()))
        val wrapped = WrappedFileMasterKey(ByteArray(65) { 1 }, ByteArray(12), ByteArray(48))
        coEvery { encryptThenEncode.seal(any(), any()) } returns SealedFile(ByteArray(4_112), ByteArray(12) { 1 }, wrapped)
        coEvery { encryptThenEncode.encode(any(), any(), any()) } returns EncryptedBundle(
            (0..2).map { EncryptedFragment(it, it == 2, ByteArray(100), ByteArray(12), 20) },
            wrapped
        )
        coEvery { distribute.distribute(any(), any(), any(), any(), any(), any()) } returns Result.success(entry)
        coEvery { catalog.moveToTrash(any()) } returns Result.success(Unit)
        coEvery { catalog.getEntry(any()) } returns Result.success(entry)
        coEvery { localize.invoke(any()) } returns Result.success(
            (0..2).associate { "b$it" to ResolvedBlockLocation("b$it", it, "n$it", "", 0, 1f) }
        )
        every { download.invoke(any(), any()) } returns flowOf(
            DownloadProgressState.Progress(received = 1, k = 2, failed = 0),
            DownloadProgressState.Completed((0..1).associateWith { DownloadedBlock("b$it", it, false, ByteArray(100), ByteArray(12)) })
        )
        every { assemble.invoke(any(), any(), any()) } answers {
            val out = File(scratch, "preview.bin").apply { writeBytes(ByteArray(20)) }
            flowOf(AssembleDownloadedFileUseCase.AssembleProgress.Finalized(AssembleDownloadedFileUseCase.AssembleResult.Success(out.absolutePath)))
        }
    }

    private fun config(reps: Int = 2, roundTrip: Boolean = true) =
        ExperimentConfig(runId = "r1", label = "wifi_test", fileSizeBytes = 4_096, repetitions = reps, roundTrip = roundTrip, cooldownMs = 0)

    @Test
    fun `a round-trip campaign records one upload and one download per repetition`() = runTest {
        val summary = useCase()(config())

        assertEquals(ExperimentSummary(attempted = 4, succeeded = 4, failed = 0), summary)
        assertEquals(
            listOf(ExperimentOperation.UPLOAD, ExperimentOperation.DOWNLOAD, ExperimentOperation.UPLOAD, ExperimentOperation.DOWNLOAD),
            records.map { it.operation }
        )
        assertEquals(listOf(1, 1, 2, 2), records.map { it.repetition })
        assertTrue(records.all { it.success && it.energy == reading && it.label == "wifi_test" && it.networkType == "WIFI" })
    }

    @Test
    fun `upload accounts for every copy sent and every host used`() = runTest {
        useCase()(config(reps = 1, roundTrip = false))
        val up = records.single()
        assertEquals(4_096L, up.fileBytes)
        assertEquals(2, up.k); assertEquals(1, up.n)
        assertEquals("3 fragments de 100 o, dont un sur 2 hôtes", 400L, up.wireBytes)
        assertEquals(3, up.hostCount)
        assertTrue(up.transferMs >= 0 && up.totalMs >= up.transferMs)
    }

    @Test
    fun `download counts the k fragments received and cleans the reconstructed file`() = runTest {
        useCase()(config(reps = 1))
        val down = records.last()
        assertEquals(ExperimentOperation.DOWNLOAD, down.operation)
        assertEquals(200L, down.wireBytes)
        assertEquals(3, down.hostCount)
        assertTrue(down.assembleMs >= 0)
        assertFalse("fichier reconstruit supprimé", File(scratch, "preview.bin").exists())
    }

    @Test
    fun `generated test files are removed and hidden from the explorer`() = runTest {
        useCase()(config(reps = 2))
        assertTrue("aucun fichier de test laissé", scratch.listFiles().orEmpty().isEmpty())
        coVerify(exactly = 2) { catalog.moveToTrash(any()) }
    }

    @Test
    fun `a failed upload is recorded and its download is skipped`() = runTest {
        coEvery { select(any(), any(), any()) } returns Result.failure(PeerSelectionException.InsufficientRedundancyNodes(2, 2))

        val summary = useCase()(config(reps = 1))

        assertEquals(ExperimentSummary(attempted = 2, succeeded = 0, failed = 2), summary)
        assertFalse(records[0].success)
        assertTrue(records[0].error.contains("redondance"))
        assertEquals(reading, records[0].energy)
        assertEquals("skipped: upload failed", records[1].error)
    }

    @Test
    fun `a failed download keeps the reason`() = runTest {
        every { download.invoke(any(), any()) } returns flowOf(DownloadProgressState.Failed("2 hosts offline", received = 1, k = 2))

        useCase()(config(reps = 1))

        assertTrue(records[0].success)
        assertFalse(records[1].success)
        assertTrue(records[1].error.contains("2 hosts offline"))
    }

    @Test
    fun `one failing repetition does not stop the campaign`() = runTest {
        var calls = 0
        coEvery { distribute.distribute(any(), any(), any(), any(), any(), any()) } answers {
            if (++calls == 1) Result.failure(RuntimeException("relay down")) else Result.success(entry)
        }

        val summary = useCase()(config(reps = 3, roundTrip = false))

        assertEquals(ExperimentSummary(attempted = 3, succeeded = 2, failed = 1), summary)
        assertEquals("relay down", records[0].error)
    }
}
