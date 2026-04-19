package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse

interface GossipIncomingHandler {
    fun onBloomGossipReceived(msg: BloomFilterGossip, senderIp: String, senderPort: Int)
    fun onDeltaSyncRequestReceived(req: DeltaSyncRequest): DeltaSyncResponse?
}
