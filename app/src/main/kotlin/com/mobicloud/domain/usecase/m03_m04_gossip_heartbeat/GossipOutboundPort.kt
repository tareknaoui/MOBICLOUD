package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse

interface GossipOutboundPort {
    suspend fun sendBloomGossip(targetIp: String, targetPort: Int, msg: BloomFilterGossip): Result<Unit>
    suspend fun sendDeltaSyncRequest(targetIp: String, targetPort: Int, req: DeltaSyncRequest): Result<DeltaSyncResponse>
}
