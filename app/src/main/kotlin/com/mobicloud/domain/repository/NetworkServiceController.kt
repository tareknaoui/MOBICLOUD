package com.mobicloud.domain.repository

/**
 * Controller to manage the lifecycle of the P2P Network foreground service.
 * Abstraction residing in the domain layer, implementing Clean Architecture.
 */
interface NetworkServiceController {
    /**
     * Starts the P2P network service and acquires the MulticastLock.
     * Keeps the app running in the background for Heartbeat and Gossip.
     *
     * @return [Result.success] if the service was started successfully,
     *         [Result.failure] with the underlying exception otherwise
     *         (e.g., [SecurityException], [android.app.ForegroundServiceStartNotAllowedException]).
     */
    fun startService(): Result<Unit>

    /**
     * Stops the P2P network service and releases the MulticastLock.
     *
     * @return [Result.success] if the service was stopped successfully,
     *         [Result.failure] with the underlying exception otherwise.
     */
    fun stopService(): Result<Unit>
}
