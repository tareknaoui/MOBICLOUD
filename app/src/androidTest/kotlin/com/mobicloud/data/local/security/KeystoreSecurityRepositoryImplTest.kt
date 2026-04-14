package com.mobicloud.data.local.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.Signature

@RunWith(AndroidJUnit4::class)
class KeystoreSecurityRepositoryImplTest {

    @Before
    fun setUp() = runBlocking {
        // F-11 fix: keystore operations must not run on main thread
        withContext(Dispatchers.IO) {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KeystoreSecurityRepositoryImpl.KEY_ALIAS)) {
                keyStore.deleteEntry(KeystoreSecurityRepositoryImpl.KEY_ALIAS)
            }
        }
    }

    @Test
    fun testGenerateIdentityAndVerifySignature() = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val repository = KeystoreSecurityRepositoryImpl(context)

        // 1. Generate Identity
        val result = repository.generateIdentity()
        assertTrue("Identity generation should succeed", result.isSuccess)

        val identity = result.getOrNull()
        assertNotNull(identity)
        assertTrue(identity!!.publicKeyBytes.isNotEmpty())
        assertTrue(identity.nodeId.isNotEmpty())
        // Verify nodeId is 16 hex chars (SHA-256 prefix — F-2 fix)
        assertEquals("nodeId should be 16 hex characters", 16, identity.nodeId.length)
        assertTrue(
            "nodeId should be lowercase hex",
            identity.nodeId.matches(Regex("[0-9a-f]{16}"))
        )

        // 2. Fetch Identity from Keystore to ensure persistence
        val fetchedResult = repository.getIdentity()
        assertTrue("Fetching identity should succeed", fetchedResult.isSuccess)
        val fetchedIdentity = fetchedResult.getOrNull()

        assertEquals(identity.nodeId, fetchedIdentity?.nodeId)

        // 3. Prove Sign/Verify works internally (Sign with Private Key from Keystore)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(
            KeystoreSecurityRepositoryImpl.KEY_ALIAS, null
        ) as KeyStore.PrivateKeyEntry
        val privateKey = entry.privateKey
        val publicKey = entry.certificate.publicKey

        val dataToSign = "Test MobiCloud Data".toByteArray()

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(dataToSign)
        }
        val signedData = signature.sign()

        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update(dataToSign)
        }
        val isVerified = verifier.verify(signedData)

        assertTrue("Signature verification should succeed", isVerified)
    }
}
