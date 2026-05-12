package com.mobicloud.domain.repository

import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.Leave
import com.mobicloud.domain.models.m11_join.MemberUpdate

interface IMemberHeartbeatSender {
    suspend fun send(destNodeId: ByteArray, hb: Heartbeat): Result<Unit>
    suspend fun broadcast(memberNodeIds: List<ByteArray>, update: MemberUpdate): Result<Unit>
    suspend fun sendLeave(spNodeId: ByteArray, leave: Leave): Result<Unit>
}
