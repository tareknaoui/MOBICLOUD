package com.mobicloud.data.local.metadata

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.mobicloud.domain.models.HashcashToken
import com.mobicloud.domain.repository.HashcashTokenRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import javax.inject.Inject

class LocalHashcashTokenRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val protoBuf: ProtoBuf
) : HashcashTokenRepository {

    companion object {
        private const val PREFS_FILE = "mobicloud_hashcash_prefs"
        private const val PREF_KEY_TOKEN = "hashcash_token_protobuf_base64"
    }

    // Lazy init: EncryptedSharedPreferences.create() est coûteux (dérivation de clé AES).
    // L'instance est créée une seule fois et réutilisée pour toutes les opérations.
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun saveToken(token: HashcashToken): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bytes = protoBuf.encodeToByteArray(token)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            // apply() est asynchrone et non-bloquant — préférable à commit() sur Dispatchers.IO
            encryptedPrefs.edit()
                .putString(PREF_KEY_TOKEN, base64)
                .apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun getToken(): Result<HashcashToken> = withContext(Dispatchers.IO) {
        try {
            val base64 = encryptedPrefs.getString(PREF_KEY_TOKEN, null)
                ?: return@withContext Result.failure(Exception("Aucun HashcashToken trouvé."))

            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val token = protoBuf.decodeFromByteArray<HashcashToken>(bytes)
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearToken(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // apply() est asynchrone et non-bloquant — préférable à commit() sur Dispatchers.IO
            encryptedPrefs.edit()
                .remove(PREF_KEY_TOKEN)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

