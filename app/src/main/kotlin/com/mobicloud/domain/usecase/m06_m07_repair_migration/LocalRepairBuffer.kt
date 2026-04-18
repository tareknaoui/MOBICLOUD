package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.RepairRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Buffer FIFO in-memory pour les requêtes de réparation en transit lors d'une transition
 * de Super-Pair (abdication → nouvelle élection).
 *
 * Capacité maximale : [MAX_SIZE] entrées. Si dépassée, l'entrée la plus ancienne est droppée
 * et retournée par [enqueue] pour que l'appelant puisse loguer ou gérer l'overflow.
 *
 * Thread-safety assurée par un [Mutex] kotlinx.coroutines (compatible suspend, sans deadlock).
 *
 * P7 — Quand le Circuit-Breaker passe de OPEN → CLOSED, le buffer est automatiquement drainé
 * et les requêtes émises via [pendingAfterCircuitClose] pour que le collecteur (Epic 7)
 * puisse les retransmettre au nouveau Super-Pair.
 */
@Singleton
class LocalRepairBuffer @Inject constructor(
    private val circuitBreakerUseCase: CircuitBreakerUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val buffer = LinkedList<RepairRequest>()
    private val mutex = Mutex()
    private val MAX_SIZE = 50

    /**
     * Flow émettant les listes de requêtes drainées automatiquement lors de la fermeture
     * du Circuit-Breaker (OPEN → CLOSED). L'élément initial (false) est ignoré via [drop].
     * Epic 7 souscrit à ce flow pour retransmettre les requêtes en attente.
     */
    val pendingAfterCircuitClose = kotlinx.coroutines.flow.MutableSharedFlow<List<RepairRequest>>(extraBufferCapacity = 1)

    init {
        // P7 — Observer isCircuitOpen : émettre les requêtes en attente à la fermeture du circuit
        applicationScope.launch {
            circuitBreakerUseCase.isCircuitOpen
                .drop(1)               // ignorer la valeur initiale (false)
                .filter { !it }        // seulement les transitions OPEN → CLOSED
                .collect {
                    val pending = mutex.withLock {
                        if (buffer.isEmpty()) return@collect
                        val items = buffer.toList()
                        buffer.clear()
                        items
                    }
                    if (pending.isNotEmpty()) {
                        pendingAfterCircuitClose.emit(pending)
                    }
                }
        }
    }

    /**
     * Enfile une requête dans le buffer FIFO.
     *
     * @return la [RepairRequest] droppée si le buffer était plein, `null` sinon.
     *         L'appelant est responsable du logging de l'overflow.
     */
    suspend fun enqueue(request: RepairRequest): RepairRequest? = mutex.withLock {
        val dropped = if (buffer.size >= MAX_SIZE) buffer.removeFirst() else null
        buffer.addLast(request)
        dropped
    }

    /**
     * Draine et retourne toutes les requêtes dans l'ordre FIFO, en vidant le buffer.
     * Retourne une liste vide sans vider le buffer si le Circuit-Breaker est actif.
     */
    suspend fun drain(): List<RepairRequest> = mutex.withLock {
        if (circuitBreakerUseCase.isCircuitOpen.value) {
            return@withLock emptyList<RepairRequest>()
        }
        val requests = buffer.toList()
        buffer.clear()
        requests
    }

    /** Retourne la taille courante du buffer (thread-safe). */
    suspend fun size(): Int = mutex.withLock { buffer.size }
}
