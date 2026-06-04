package com.mobicloud.data.remote

import com.mobicloud.compose.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SupabaseAuthRequest(val email: String, val password: String)

@Serializable
data class SupabaseAuthResponse(val access_token: String, val user: SupabaseUser)

@Serializable
data class SupabaseUser(val id: String)

@Serializable
data class SupabaseIdentityRecord(
    val encrypted_identity: String,
    val salt: String,
    val iv: String,
    val user_id: String? = null,
    val catalog_backup: String? = null,
    val catalog_iv: String? = null
)

@Singleton
class SupabaseClient @Inject constructor(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl get() = BuildConfig.SUPABASE_URL
    private val anonKey get() = BuildConfig.SUPABASE_ANON_KEY

    suspend fun signUp(email: String, password: String): Result<SupabaseAuthResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(SupabaseAuthRequest(email, password))
                    .toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/signup")
                    .addHeader("apikey", anonKey)
                    .post(body)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val bodyStr = response.body?.string() ?: error("Empty response")
                if (!response.isSuccessful) error("Signup failed (${response.code}): $bodyStr")
                json.decodeFromString<SupabaseAuthResponse>(bodyStr)
            }
        }

    suspend fun signIn(email: String, password: String): Result<SupabaseAuthResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(SupabaseAuthRequest(email, password))
                    .toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/token?grant_type=password")
                    .addHeader("apikey", anonKey)
                    .post(body)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val bodyStr = response.body?.string() ?: error("Empty response")
                if (!response.isSuccessful) error("Login failed (${response.code}): $bodyStr")
                json.decodeFromString<SupabaseAuthResponse>(bodyStr)
            }
        }

    suspend fun upsertIdentity(
        accessToken: String,
        record: SupabaseIdentityRecord
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(record).toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/user_identities")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) error("Backup failed (${response.code})")
        }
    }

    suspend fun fetchIdentity(accessToken: String): Result<SupabaseIdentityRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/user_identities?select=encrypted_identity,salt,iv,catalog_backup,catalog_iv&limit=1")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val bodyStr = response.body?.string() ?: error("Empty response")
                if (!response.isSuccessful) error("Fetch failed (${response.code}): $bodyStr")
                val records = json.decodeFromString<List<SupabaseIdentityRecord>>(bodyStr)
                records.firstOrNull() ?: error("Aucune identité trouvée pour ce compte.")
            }
        }
}
