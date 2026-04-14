package com.mobicloud.data.p2p

import com.mobicloud.domain.models.NodeIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean  // F-02: AtomicBoolean pour visibilité + atomicité
import kotlin.math.roundToLong

@OptIn(ExperimentalSerializationApi::class)  // ProtoBuf type requires opt-in at class level
class UdpHeartbeatBroadcaster(
    private val protoBuf: ProtoBuf,
    private val socket: DatagramSocket,
    private val multicastAddress: String,
    private val port: Int,
    private val initialIntervalMs: Long = 1000L,
    private val maxIntervalMs: Long = 32000L,
    private val normalIntervalMs: Long = 2000L,
    private val backoffFactor: Double = 2.0,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    init {
        // F-07: Guard contre une boucle CPU infinie si initialIntervalMs = 0
        require(initialIntervalMs > 0L) { "initialIntervalMs must be strictly positive, got $initialIntervalMs" }
    }

    // F-04: extraBufferCapacity=64 évite la perte de signal quand resetBackoff() + setStable()
    // sont appelés en rafale avant que le collecteur ne consomme le premier signal.
    private val resetTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    // F-02: AtomicBoolean garantit la visibilité ET l'atomicité de getAndSet() entre threads.
    private val isStable = AtomicBoolean(false)

    fun resetBackoff() {
        isStable.set(false)
        resetTrigger.tryEmit(Unit)
    }

    fun setStable(stable: Boolean) {
        val wasStable = isStable.getAndSet(stable)
        if (wasStable != stable) {
            // F-01: Toute transition d'état (stable→instable OU instable→stable)
            // interrompt immédiatement le délai courant pour appliquer le nouvel intervalle.
            // Avant ce fix, setStable(false) ne déclenchait pas de signal → délai parasite 0-2s.
            resetTrigger.tryEmit(Unit)
        }
    }

    suspend fun startBroadcasting(identity: NodeIdentity): Result<Unit> = withContext(ioDispatcher) {
        try {
            val address = InetAddress.getByName(multicastAddress)
            val payload = protoBuf.encodeToByteArray(identity)

            var currentIntervalMs = initialIntervalMs

            while (isActive) {
                try {
                    val packet = DatagramPacket(payload, payload.size, address, port)
                    socket.send(packet)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Ignore non-fatal IO errors to resume broadcasting later
                }

                try {
                    val delayTime = if (isStable.get()) normalIntervalMs else currentIntervalMs
                    withTimeout(delayTime) {
                        resetTrigger.first()

                    }
                    // Signal reçu (reset ou changement d'état) — recalculer l'intervalle
                    if (!isStable.get()) {
                        currentIntervalMs = initialIntervalMs
                    }
                } catch (e: TimeoutCancellationException) {
                    // Timeout naturel — faire avancer le backoff exponentiel
                    if (!isStable.get()) {
                        currentIntervalMs = (currentIntervalMs * backoffFactor).roundToLong().coerceAtMost(maxIntervalMs)
                    }
                }
            }
            // F-03: Sortie nominale coopérative (isActive=false sans exception).
            // En pratique, l'annulation coroutine lève CancellationException aux points de
            // suspension ci-dessus avant d'atteindre cette ligne.
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
