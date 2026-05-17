package com.mobicloud.domain.usecase.m08_hosting

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.HostedBlockPayload
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.RelayRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Story 9.4 — tests du use-case côté répondeur. Behavior best-effort :
 *   - bloc trouvé → uploadBlock appelé avec le BlockTransferMessage sérialisé
 *   - bloc absent → no-op + log
 *   - exceptions getBlock / uploadBlock swallow (sauf CancellationException re-thrown)
 */
@OptIn(ExperimentalSerializationApi::class)
class RespondToBlockRequestUseCaseTest {

    private lateinit var hostedBlockRepository: HostedBlockRepository
    private lateinit var relayRepository: RelayRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        hostedBlockRepository = mockk(relaxed = true)
        relayRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun newUseCase() = RespondToBlockRequestUseCase(
        hostedBlockRepository, relayRepository
    )

    @Test
    fun `bloc trouve — uploadBlock appele avec le BlockTransferMessage serialise`() = runBlocking {
        val blockId = "a".repeat(64)
        val fromNodeId = "requester0000001"
        val payload = HostedBlockPayload(
            blockId = blockId,
            ownerId = "owner00000000001",
            fragmentIndex = 4,
            isParity = true,
            ciphertext = ByteArray(64) { it.toByte() },
            iv = ByteArray(12) { (it + 1).toByte() }
        )
        coEvery { hostedBlockRepository.getBlock(blockId) } returns Result.success(payload)
        coEvery { relayRepository.uploadBlock(any(), any(), any()) } returns Result.success(Unit)

        val dataSlot = slot<ByteArray>()
        coEvery { relayRepository.uploadBlock(fromNodeId, blockId, capture(dataSlot)) } returns Result.success(Unit)

        newUseCase().respond(fromNodeId, blockId)

        coVerify(exactly = 1) { relayRepository.uploadBlock(fromNodeId, blockId, any()) }
        // Vérifie que data se désérialise en BlockTransferMessage cohérent
        val decoded = MobiCloudProtoBuf.decodeFromByteArray(BlockTransferMessage.serializer(), dataSlot.captured)
        assertEquals(blockId, decoded.blockId)
        assertEquals(4, decoded.fragmentIndex)
        assertEquals(true, decoded.isParity)
        assertArrayEquals(payload.ciphertext, decoded.ciphertext)
        assertArrayEquals(payload.iv, decoded.iv)
        // [P5-Fix] ownerId doit venir du bloc hébergé, pas de l'identité locale
        assertEquals("owner00000000001", decoded.ownerId)
    }

    @Test
    fun `bloc absent — uploadBlock JAMAIS appele`() = runBlocking {
        val blockId = "b".repeat(64)
        coEvery { hostedBlockRepository.getBlock(blockId) } returns Result.success(null)

        newUseCase().respond("requester0000001", blockId)

        coVerify(exactly = 0) { relayRepository.uploadBlock(any(), any(), any()) }
    }

    @Test
    fun `getBlock failure — exception swallow et uploadBlock JAMAIS appele`() = runBlocking {
        val blockId = "c".repeat(64)
        coEvery { hostedBlockRepository.getBlock(blockId) } returns Result.failure(
            RuntimeException("disk read error")
        )

        // getOrNull() de Result.failure renvoie null → branche bloc-absent ; pas d'exception escape
        newUseCase().respond("requester0000001", blockId)

        coVerify(exactly = 0) { relayRepository.uploadBlock(any(), any(), any()) }
    }

    @Test
    fun `getBlock throws — exception swallow (best-effort)`() = runBlocking {
        val blockId = "d".repeat(64)
        coEvery { hostedBlockRepository.getBlock(blockId) } throws RuntimeException("explose")

        // Doit se terminer sans propagation d'exception
        newUseCase().respond("requester0000001", blockId)

        coVerify(exactly = 0) { relayRepository.uploadBlock(any(), any(), any()) }
    }

    @Test
    fun `uploadBlock failure — exception swallow (pas de retry)`() = runBlocking {
        val blockId = "e".repeat(64)
        val payload = HostedBlockPayload(
            blockId = blockId,
            ownerId = "owner00000000002",
            fragmentIndex = 0,
            isParity = false,
            ciphertext = ByteArray(16),
            iv = ByteArray(12)
        )
        coEvery { hostedBlockRepository.getBlock(blockId) } returns Result.success(payload)
        coEvery { relayRepository.uploadBlock(any(), any(), any()) } returns Result.failure(
            RuntimeException("relay fermé")
        )

        // Doit se terminer normalement (best-effort, pas de propagation)
        newUseCase().respond("requester0000001", blockId)

        coVerify(exactly = 1) { relayRepository.uploadBlock(any(), any(), any()) }
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException de getBlock est re-thrown`(): Unit = runBlocking {
        val blockId = "1".repeat(64)
        coEvery { hostedBlockRepository.getBlock(blockId) } throws CancellationException("cancelled")

        newUseCase().respond("requester0000001", blockId)
    }

    @Test
    fun `ownerId vide dans payload — transmis tel quel sans injection locale`() = runBlocking {
        val blockId = "2".repeat(64)
        val payload = HostedBlockPayload(
            blockId = blockId,
            ownerId = "",
            fragmentIndex = 0,
            isParity = false,
            ciphertext = ByteArray(16),
            iv = ByteArray(12)
        )
        coEvery { hostedBlockRepository.getBlock(blockId) } returns Result.success(payload)

        val dataSlot = slot<ByteArray>()
        coEvery { relayRepository.uploadBlock(any(), any(), capture(dataSlot)) } returns Result.success(Unit)

        newUseCase().respond("requester0000001", blockId)

        coVerify(exactly = 1) { relayRepository.uploadBlock(any(), blockId, any()) }
        val decoded = MobiCloudProtoBuf.decodeFromByteArray(BlockTransferMessage.serializer(), dataSlot.captured)
        assertEquals("", decoded.ownerId)
    }
}
