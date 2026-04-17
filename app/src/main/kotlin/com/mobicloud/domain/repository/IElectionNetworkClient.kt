package com.mobicloud.domain.repository

import com.mobicloud.domain.models.ElectionPayload
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface abstraite pour la diffusion et la réception réseau
 * des payloads de l'élection Bully (ELECTION, ALIVE, COORDINATOR).
 *
 * Le canal [incomingMessages] est un [SharedFlow] afin de garantir un fan-out
 * correct : [RunBullyElectionUseCase] et [ProcessIncomingElectionEventUseCase]
 * peuvent tous deux s'abonner sans que l'un "vole" les messages de l'autre.
 */
interface IElectionNetworkClient {
    /**
     * Flux réactif partagé (hot, fan-out) des messages d'élection entrants.
     * Toute émission est diffusée à tous les collecteurs actifs.
     */
    val incomingMessages: SharedFlow<ElectionPayload>

    /**
     * Diffuse un payload d'élection sur le réseau P2P local (UDP ou Multicast).
     */
    suspend fun broadcastElectionMessage(payload: ElectionPayload): Result<Unit>
}
