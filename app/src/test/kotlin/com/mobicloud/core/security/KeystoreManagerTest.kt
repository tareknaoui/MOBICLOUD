package com.mobicloud.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.MessageDigest

class KeystoreManagerTest {

    /**
     * Teste la logique pure de dérivation du nodeId (SHA-256 → 8 premiers bytes → 16 chars hex).
     * La génération réelle des clés nécessite le Keystore Android (test instrumenté).
     */

    // --- AC3: nodeId dérivé de SHA-256 tronqué à 8 bytes → 16 chars hex ---

    @Test
    fun `nodeId derivation produces 16-character hex string from 8 bytes of SHA-256`() {
        // Arrange - simuler le processus de dérivation (copie de la logique KeystoreManager)
        val fakePublicKeyBytes = "fake_ec_public_key_bytes".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(fakePublicKeyBytes)
        
        // Act - dériver le nodeId comme le fait KeystoreManager
        val nodeId = digest.take(8).joinToString("") { "%02x".format(it) }
        
        // Assert
        assertEquals(16, nodeId.length)
        // Vérifier que ce sont bien des caractères hexadécimaux [0-9a-f]
        assertTrue(nodeId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `nodeId derivation is deterministic for same public key bytes`() {
        // Arrange
        val fakePublicKeyBytes = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        
        // Act - dériver deux fois
        val nodeId1 = deriveNodeId(fakePublicKeyBytes)
        val nodeId2 = deriveNodeId(fakePublicKeyBytes)
        
        // Assert - le résultat doit être identique (idempotent)
        assertEquals(nodeId1, nodeId2)
    }

    @Test
    fun `nodeId derivation produces different IDs for different public key bytes`() {
        // Arrange
        val keyBytes1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val keyBytes2 = byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)
        
        // Act
        val nodeId1 = deriveNodeId(keyBytes1)
        val nodeId2 = deriveNodeId(keyBytes2)
        
        // Assert - des clés différentes produisent des IDs différents
        assert(nodeId1 != nodeId2) { "Two different keys must not produce the same nodeId" }
    }

    @Test
    fun `nodeId length is exactly 16 characters regardless of input size`() {
        // Test with various input sizes
        val inputs = listOf(
            byteArrayOf(1),
            byteArrayOf(1, 2, 3, 4, 5),
            ByteArray(64) { it.toByte() },
            ByteArray(256) { it.toByte() }
        )
        
        for (input in inputs) {
            val nodeId = deriveNodeId(input)
            assertEquals("NodeID should always be 16 chars, but got ${nodeId.length} for input size ${input.size}", 
                16, nodeId.length)
        }
    }

    // Helper replicating KeystoreManager's private logic for unit testing
    private fun deriveNodeId(publicKeyBytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBytes)
            .take(8)
            .joinToString("") { "%02x".format(it) }

    // Kotlin stdlib missing assertTrue - use custom
    private fun assertTrue(condition: Boolean) {
        if (!condition) throw AssertionError("Expected condition to be true")
    }
}
