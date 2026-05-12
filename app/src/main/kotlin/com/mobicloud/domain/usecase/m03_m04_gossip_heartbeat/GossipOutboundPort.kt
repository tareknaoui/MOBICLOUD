package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse

interface GossipOutboundPort {
    suspend fun sendBloomGossip(targetNodeId: String, msg: BloomFilterGossip): Result<Unit>
    suspend fun sendDeltaSyncRequest(targetNodeId: String, req: DeltaSyncRequest): Result<DeltaSyncResponse>
}
