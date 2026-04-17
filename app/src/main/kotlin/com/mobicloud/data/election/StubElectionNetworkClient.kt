package com.mobicloud.data.election

import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.repository.IElectionNetworkClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation stub de IElectionNetworkClient.
 * Les messages d'élection seront émis ici depuis le vrai transport UDP (Story future).
 */
@Singleton
class StubElectionNetworkClient @Inject constructor() : IElectionNetworkClient {

    private val _incomingMessages = MutableSharedFlow<ElectionPayload>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<ElectionPayload> = _incomingMessages

    override suspend fun broadcastElectionMessage(payload: ElectionPayload): Result<Unit> =
        Result.success(Unit)
}
