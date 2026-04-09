package com.mobicloud.data.network.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.mobicloud.domain.repository.NetworkServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NetworkServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkServiceController {

    override fun startService(): Result<Unit> {
        val intent = Intent(context, MobicloudP2PService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun stopService(): Result<Unit> {
        val intent = Intent(context, MobicloudP2PService::class.java)
        return try {
            context.stopService(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
