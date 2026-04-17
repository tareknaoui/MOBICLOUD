package com.mobicloud.domain.models

/**
 * Événements émis par [ProcessIncomingElectionEventUseCase].
 *
 * L'appelant (ViewModel/Service) doit réagir à [ShouldStartOwnElection]
 * en déclenchant [RunBullyElectionUseCase] pour compléter le protocole Bully (AC4).
 */
sealed class ElectionEvent {

    /**
     * Le nœud local a répondu ALIVE à un pair de score inférieur et doit maintenant
     * lancer sa propre candidature comme le prescrit l'AC4 du protocole Bully.
     */
    object ShouldStartOwnElection : ElectionEvent()

    /**
     * Un nouveau coordinateur a été désigné et enregistré dans la PeerRegistry.
     *
     * @param coordinatorNodeId L'identifiant du nouveau Super-Pair.
     */
    data class CoordinatorRegistered(val coordinatorNodeId: String) : ElectionEvent()

    /**
     * Message ALIVE reçu — traité en interne par [RunBullyElectionUseCase] via le SharedFlow.
     * Aucune action requise de la part de l'appelant.
     */
    object AliveReceived : ElectionEvent()
}
