package com.mobicloud.domain.repository

interface LocalDiscoveryRepository {
    fun start(tcpPort: Int)
    fun stop()
    fun updateTcpPort(port: Int)
    /** Appelé par MobicloudP2PService lors de la victoire/perte d'une élection Bully. */
    fun updateSuperPairStatus(isSuperPair: Boolean)
    /** Story 12.1 — mise à jour du nombre de membres actifs dans le cluster (pour HELLO multicast). */
    fun updateCurrentMemberCount(count: Int)
}
