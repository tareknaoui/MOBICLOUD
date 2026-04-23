package com.mobicloud.domain.usecase.m08_hosting

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest
import kotlin.random.Random

class ReceiveAndHostBlockUseCaseTest {

    private lateinit var hostedBlockRepository: HostedBlockRepository
    private lateinit var securityRepository: SecurityRepository

    private val receiverNodeId = "node_hosting_01"
    private val receiverPublicKey = Random.nextBytes(65)
    private val receiverIdentity = NodeIdentity(receiverNodeId, receiverPublicKey)
    private val fakeSignature = Random.nextBytes(64)

    @Before
    fun setUp() {
        hostedBlockRepository = mockk(relaxed = true)
        securityRepository = mockk()

        coEvery { securityRepository.getIdentity() } returns Result.success(receiverIdentity)
        coEvery { securityRepository.signData(any()) } returns Result.success(fakeSignature)
        coEvery {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        } returns Result.success("/tmp/blocks/fake")
    }

    private fun sha256hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }

    private fun buildUseCase(diskSpaceBytes: Long = 200L * 1024 * 1024) =
        ReceiveAndHostBlockUseCase(
            hostedBlockRepository = hostedBlockRepository,
            securityRepository = securityRepository,
            diskSpaceProvider = { diskSpaceBytes }
        )

    private fun buildMessage(
        ciphertext: ByteArray = Random.nextBytes(512),
        blockIdOverride: String? = null,
        fragmentIndex: Int = 0,
        isParity: Boolean = false
    ): BlockTransferMessage {
        val blockId = blockIdOverride ?: sha256hex(ciphertext)
        return BlockTransferMessage(
            blockId = blockId,
            ownerId = "node_owner_42",
            fragmentIndex = fragmentIndex,
            isParity = isParity,
            ciphertext = ciphertext,
            iv = Random.nextBytes(12),
            originalFileSize = 1024L
        )
    }

    @Test
    fun `valid block is persisted and signed ACK returned`() = runTest {
        val useCase = buildUseCase()
        val message = buildMessage()

        val result = useCase.receive(message)

        assertTrue("Result should be Success", result is ReceiveBlockResult.Success)
        val success = result as ReceiveBlockResult.Success
        assertEquals(message.blockId, success.ack.blockId)
        assertEquals(message.blockId, success.ack.blockHash)
        assertEquals(receiverNodeId, success.ack.receiverNodeId)
        assertTrue(success.ack.signature.contentEquals(fakeSignature))

        coVerify(exactly = 1) {
            hostedBlockRepository.saveBlock(
                blockId = message.blockId,
                ownerId = message.ownerId,
                fragmentIndex = message.fragmentIndex,
                isParity = message.isParity,
                ciphertext = message.ciphertext,
                iv = message.iv
            )
        }
    }

    @Test
    fun `hash mismatch returns HashMismatch and never persists`() = runTest {
        val useCase = buildUseCase()
        val message = buildMessage(blockIdOverride = "deadbeef".repeat(8))

        val result = useCase.receive(message)

        assertTrue("Result should be HashMismatch", result is ReceiveBlockResult.HashMismatch)
        coVerify(exactly = 0) {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `storage full returns StorageFull and never persists`() = runTest {
        val useCase = buildUseCase(diskSpaceBytes = 50L * 1024 * 1024) // 50 MB
        val message = buildMessage()

        val result = useCase.receive(message)

        assertTrue("Result should be StorageFull", result is ReceiveBlockResult.StorageFull)
        coVerify(exactly = 0) {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `oversized block returns TooBig and never persists`() = runTest {
        val useCase = buildUseCase()
        // Payload au-delà de MAX_BLOCK_PAYLOAD_BYTES = 2_000_000
        val oversizedCiphertext = ByteArray(ReceiveAndHostBlockUseCase.MAX_BLOCK_PAYLOAD_BYTES + 1)
        val message = buildMessage(ciphertext = oversizedCiphertext)

        val result = useCase.receive(message)

        assertTrue("Result should be TooBig", result is ReceiveBlockResult.TooBig)
        coVerify(exactly = 0) {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `repository failure returns IoError`() = runTest {
        coEvery {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        } returns Result.failure(IOException("disk error"))

        val useCase = buildUseCase()
        val message = buildMessage()

        val result = useCase.receive(message)

        assertTrue("Result should be IoError", result is ReceiveBlockResult.IoError)
        val ioErr = result as ReceiveBlockResult.IoError
        assertTrue(ioErr.cause is IOException)
    }

    @Test
    fun `getIdentity failure returns IoError without saving block`() = runTest {
        coEvery { securityRepository.getIdentity() } returns Result.failure(RuntimeException("keystore unavailable"))
        val useCase = buildUseCase()
        val message = buildMessage()

        val result = useCase.receive(message)

        assertTrue("Result should be IoError", result is ReceiveBlockResult.IoError)
        coVerify(exactly = 0) {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `signData failure returns IoError after block is saved`() = runTest {
        coEvery { securityRepository.signData(any()) } returns Result.failure(RuntimeException("signing failed"))
        val useCase = buildUseCase()
        val message = buildMessage()

        val result = useCase.receive(message)

        assertTrue("Result should be IoError", result is ReceiveBlockResult.IoError)
        coVerify(exactly = 1) {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `Story 6_3 — message with invalid IV size returns HashMismatch and never persists`() = runTest {
        val useCase = buildUseCase()
        val ciphertext = Random.nextBytes(512)
        val invalidIvMessage = BlockTransferMessage(
            blockId = sha256hex(ciphertext),
            ownerId = "node_owner_42",
            fragmentIndex = 0,
            isParity = false,
            ciphertext = ciphertext,
            iv = ByteArray(8), // taille invalide (≠ 12)
            originalFileSize = 1024L
        )

        val result = useCase.receive(invalidIvMessage)

        assertTrue("Result should be HashMismatch", result is ReceiveBlockResult.HashMismatch)
        coVerify(exactly = 0) {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `already hosted block returns ACK without calling saveBlock`() = runTest {
        val message = buildMessage()
        coEvery { hostedBlockRepository.blockExists(message.blockId) } returns true

        val useCase = buildUseCase()
        val result = useCase.receive(message)

        assertTrue("Result should be Success", result is ReceiveBlockResult.Success)
        val success = result as ReceiveBlockResult.Success
        assertEquals(message.blockId, success.ack.blockId)
        coVerify(exactly = 0) {
            hostedBlockRepository.saveBlock(any(), any(), any(), any(), any(), any())
        }
    }
}
