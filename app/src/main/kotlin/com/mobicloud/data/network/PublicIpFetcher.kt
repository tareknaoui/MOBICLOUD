package com.mobicloud.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * Utilitaire pour récupérer l'IP publique du périphérique.
 * Essentiel pour que l'Ancre Firebase diffuse une IP de contact joignable.
 */
class PublicIpFetcher @Inject constructor() {
    private val client = OkHttpClient()

    suspend fun fetchPublicIp(): Result<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.ipify.org")
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val ip = response.body?.string()?.trim()
                if (!ip.isNullOrEmpty()) {
                    Result.success(ip)
                } else {
                    Result.failure(Exception("Response body is empty"))
                }
            } else {
                Result.failure(Exception("HTTP Error: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
