package com.mobicloud.domain.models

/**
 * Objet domaine produit par UdpHeartbeatReceiver après décodage d'un heartbeat UDP.
 * Contient l'identité reconstruite du nœud émetteur, son IP extraite du DatagramPacket
 * et le port TCP d'écoute inclus dans le payload.
 */
data class HeartbeatMessage(
    val identity: NodeIdentity,
    val senderIp: String,
    val tcpPort: Int
)
