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

    /**
     * Un message d'abdication a été reçu, signalant qu'un Super-Pair existant démissionne de son rôle.
     *
     * @param senderNodeId L'identifiant du nœud qui abdique.
     */
    data class AbdicationReceived(val senderNodeId: String) : ElectionEvent()

    /**
     * Message intentionnellement ignoré en raison des règles métier de l'algorithme Bully :
     *  - Nœud en période de cooldown post-abdication (ne peut pas participer à une élection)
     *  - Score local inférieur à celui de l'émetteur (rester silencieux est la règle AC5)
     *
     * Distinct de [AliveReceived] qui représente un vrai message ALIVE réseau reçu.
     * Aucune action requise de la part de l'appelant.
     */
    object Ignored : ElectionEvent()
}
