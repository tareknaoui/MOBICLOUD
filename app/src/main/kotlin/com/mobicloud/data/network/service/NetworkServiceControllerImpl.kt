package com.mobicloud.data.network.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.repository.NetworkServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class NetworkServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkServiceController {

    private val _serviceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    override val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    override fun startService(): Result<Unit> {
        // P6: Guard étendu à STARTING pour éviter un double démarrage lors d'une rotation d'écran
        val current = _serviceStatus.value
        if (current == ServiceStatus.RUNNING || current == ServiceStatus.STARTING) return Result.success(Unit)
        _serviceStatus.value = ServiceStatus.STARTING
        val intent = Intent(context, MobicloudP2PService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _serviceStatus.value = ServiceStatus.RUNNING
            Result.success(Unit)
        } catch (e: Exception) {
            _serviceStatus.value = ServiceStatus.ERROR
            Result.failure(e)
        }
    }

    override fun stopService(): Result<Unit> {
        val intent = Intent(context, MobicloudP2PService::class.java)
        return try {
            context.stopService(intent)
            _serviceStatus.value = ServiceStatus.STOPPED
            Result.success(Unit)
        } catch (e: Exception) {
            _serviceStatus.value = ServiceStatus.ERROR
            Result.failure(e)
        }
    }
}
