package com.mobicloud.domain.repository

import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.Peer

interface BlockSender {
    suspend fun sendBlock(
        block: BlockTransferMessage,
        peer: Peer,
        timeoutMs: Long
    ): Result<BlockAckMessage>
}
