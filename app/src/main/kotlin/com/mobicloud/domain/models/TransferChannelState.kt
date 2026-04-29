package com.mobicloud.domain.models

enum class TransferChannelState {
    DIRECT,    // TCP P2P direct réussi
    RELAY_HA,  // Fallback via Serveurs Relais HA
    OFFLINE    // Tous les canaux ont échoué
}
