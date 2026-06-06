package com.mobicloud.domain.usecase.m00_identity

import android.util.Base64
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * Couvre la détection "données précédentes" de [ImportIdentityUseCase].
 *
 * Règle métier : [ImportIdentityResult.previousOwnerPubKeyHash] n'est non-null QUE si
 * l'appareil avait déjà une identité DISTINCTE de celle restaurée. Un téléphone vierge
 * (peekIdentity == null) ne doit jamais déclencher de faux positif.
 */
class ImportIdentityUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var identityRepository: IdentityRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var useCase: ImportIdentityUseCase

    // Identité restaurée depuis le code (l'ancien téléphone).
    private val restoredIdentity = NodeIdentity(
        nodeId = "restored-node",
        publicKeyBytes = byteArrayOf(10, 20, 30, 40),
        reliabilityScore = 1.0f
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        securityRepository = mockk()
        identityRepository = mockk()
        catalogRepository = mockk(relaxed = true)
        useCase = ImportIdentityUseCase(securityRepository, identityRepository, catalogRepository)

        // Stub Base64 (android.util) — non disponible en JVM pure.
        mockkStatic(Base64::class)
        // decode renvoie un payload identité 4 parties "a|b|c|d".
        every { Base64.decode(any<String>(), any()) } returns "a|b|c|d".toByteArray(Charsets.UTF_8)
        every { Base64.encodeToString(any(), any()) } returns "RECONSTRUCTED_CODE"

        coEvery { securityRepository.importFromRecoveryCode(any()) } returns Result.success(restoredIdentity)
        coEvery { identityRepository.restoreIdentity(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `fresh device returns null previous hash (no false positive)`() = runTest {
        coEvery { identityRepository.peekIdentity() } returns Result.success(null)

        val result = useCase("dummy-code")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()!!.previousOwnerPubKeyHash)
    }

    @Test
    fun `distinct prior identity returns its pub key hash`() = runTest {
        val priorIdentity = NodeIdentity(
            nodeId = "old-device-node",
            publicKeyBytes = byteArrayOf(99, 98, 97), // différent de restoredIdentity
            reliabilityScore = 0.5f
        )
        coEvery { identityRepository.peekIdentity() } returns Result.success(priorIdentity)

        val result = useCase("dummy-code")

        assertTrue(result.isSuccess)
        assertEquals(
            sha256Hex(priorIdentity.publicKeyBytes),
            result.getOrNull()!!.previousOwnerPubKeyHash
        )
    }

    @Test
    fun `re-importing same identity returns null (no spurious delete prompt)`() = runTest {
        // L'appareil avait déjà exactement l'identité qu'on restaure.
        coEvery { identityRepository.peekIdentity() } returns Result.success(restoredIdentity)

        val result = useCase("dummy-code")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()!!.previousOwnerPubKeyHash)
    }

    @Test
    fun `import fails when security repository rejects code`() = runTest {
        coEvery { identityRepository.peekIdentity() } returns Result.success(null)
        coEvery { securityRepository.importFromRecoveryCode(any()) } returns
            Result.failure(IllegalArgumentException("bad code"))

        val result = useCase("dummy-code")

        assertTrue(result.isFailure)
    }
}
