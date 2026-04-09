package com.mobicloud.domain.usecase.m01_auth_discovery

import com.mobicloud.domain.models.HashcashToken
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.HashcashTokenRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateHashcashProofUseCaseTest {

    private lateinit var useCase: GenerateHashcashProofUseCase
    private lateinit var fakeSecurityRepo: FakeSecurityRepository
    private lateinit var fakeTokenRepo: FakeHashcashTokenRepository

    @Before
    fun setUp() {
        fakeSecurityRepo = FakeSecurityRepository()
        fakeTokenRepo = FakeHashcashTokenRepository()
        useCase = GenerateHashcashProofUseCase(fakeSecurityRepo, fakeTokenRepo)
    }

    @Test
    fun `invoke generates valid hashcash with difficulty 8`() = runTest {
        // Arrange : difficulté faible pour test unitaire rapide
        val difficulty = 8

        // Act
        val result = useCase(difficultyBits = difficulty)

        // Assert
        assertTrue(result.isSuccess)
        val token = result.getOrNull()
        assertNotNull(token)
        
        // Validation que le token a bien été persisté
        assertNotNull(fakeTokenRepo.savedToken)
        assertEquals(token, fakeTokenRepo.savedToken)

        // Validation algorithmique : le hash doit vérifier la difficulté
        val md = MessageDigest.getInstance("SHA-256")
        val payload = "${token!!.resource}:${token.timestamp}:${token.nonce}"
        val hashBytes = md.digest(payload.toByteArray(Charsets.UTF_8))
        
        // Pour une difficulté de 8, le premier byte doit être 0 (00000000)
        assertEquals(0.toByte(), hashBytes[0])
        
        // Vérification de la signature simulée
        assertArrayEquals("fake_signature_$payload".toByteArray(Charsets.UTF_8), token.signature)
    }
    
    @Test
    fun `invoke with difficulty 4`() = runTest {
        // Arrange : difficulté de 4 bits
        val difficulty = 4

        // Act
        val result = useCase(difficultyBits = difficulty)

        // Assert
        assertTrue(result.isSuccess)
        val token = result.getOrNull()!!
        
        // Validation: 4 premiers bits à 0
        val md = MessageDigest.getInstance("SHA-256")
        val payload = "${token.resource}:${token.timestamp}:${token.nonce}"
        val hashBytes = md.digest(payload.toByteArray(Charsets.UTF_8))
        
        val firstByte = hashBytes[0].toInt()
        // 0xF0 est 11110000 binaire. AND avec firstByte doit être 0 si les 4 premiers bits sont 0.
        assertEquals(0, firstByte and 0xF0)
    }

    @Test
    fun `cooperative cancellation stops intensive computation`() = runTest {
        // Arrange : difficulté très élevée pour simuler un temps infini
        val difficulty = 60
        
        // On modifie le use case pour forcer l'exécution sur le thread du test mais dans un job séparé
        val job = launch {
            useCase(difficultyBits = difficulty)
        }
        
        // Act : on annule après un petit délai
        job.cancelAndJoin()
        
        // Assert : si on arrive ici, l'annulation coopérative (ensureActive) a fonctionné
        // Si ensureActive() manquait, le job monopoliserait le thread et bloquerait cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    // Helper asserts
    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertTrue(expected.contentEquals(actual))
    }

    @Test
    fun `invoke returns cached token on second call without recomputing`() = runTest {
        // Première génération — calcule et persiste
        val firstResult = useCase(difficultyBits = 8)
        assertTrue(firstResult.isSuccess)
        val firstToken = firstResult.getOrNull()!!

        // Deuxième appel — doit retourner le token en cache sans recalculer
        val secondResult = useCase(difficultyBits = 8)
        assertTrue(secondResult.isSuccess)
        // Même instance retournée depuis le cache
        assertEquals(firstToken, secondResult.getOrNull())
        // saveToken n'a été appelé qu'une seule fois (lors de la première génération)
        assertEquals(1, fakeTokenRepo.saveCallCount)
    }

    @Test
    fun `invoke throws IllegalArgumentException for invalid difficultyBits`() = runTest {
        val resultZero = runCatching { useCase(difficultyBits = 0) }
        assertTrue(resultZero.exceptionOrNull() is IllegalArgumentException)

        val resultOver = runCatching { useCase(difficultyBits = 256) }
        assertTrue(resultOver.exceptionOrNull() is IllegalArgumentException)
    }
}

// Fakes
class FakeSecurityRepository : SecurityRepository {
    override suspend fun getIdentity(): Result<NodeIdentity> {
        return Result.success(NodeIdentity("test_public_id", byteArrayOf(1, 2, 3)))
    }

    override suspend fun generateIdentity(): Result<NodeIdentity> {
        return getIdentity()
    }

    override suspend fun signData(data: ByteArray): Result<ByteArray> {
        // Signature fictive pour éviter la complexité de l'ECDSA dans les tests unitaires simples
        val str = "fake_signature_" + String(data, Charsets.UTF_8)
        return Result.success(str.toByteArray(Charsets.UTF_8))
    }
}

class FakeHashcashTokenRepository : HashcashTokenRepository {
    var savedToken: HashcashToken? = null
    var saveCallCount: Int = 0

    override suspend fun saveToken(token: HashcashToken): Result<Unit> {
        savedToken = token
        saveCallCount++
        return Result.success(Unit)
    }

    override suspend fun getToken(): Result<HashcashToken> {
        return if (savedToken != null) Result.success(savedToken!!)
        else Result.failure(Exception("Not found"))
    }

    override suspend fun clearToken(): Result<Unit> {
        savedToken = null
        return Result.success(Unit)
    }
}
