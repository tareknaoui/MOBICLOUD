package com.mobicloud.data.repository

import android.util.Base64
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val NODES_PATH = "nodes"
private const val TTL_MS = 60_000L

class SignalingRepositoryImpl @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val firebaseDatabase: FirebaseDatabase
) : SignalingRepository {

    override suspend fun registerNode(ip: String, port: Int): Result<Unit> = runCatching {
        val identity = securityRepository.getIdentity().getOrThrow()
        val ref = firebaseDatabase.reference.child(NODES_PATH).child(identity.nodeId)

        val nodeData = mapOf(
            "nodeId"          to identity.nodeId,
            "publicKeyBase64" to Base64.encodeToString(identity.publicKeyBytes, Base64.NO_WRAP),
            "ip"              to ip,
            "port"            to port,
            "timestamp"       to System.currentTimeMillis()
        )

        // onDisconnect() AVANT setValue() — cleanup automatique à la déconnexion
        ref.onDisconnect().removeValue().await()
        ref.setValue(nodeData).await()
    }

    override fun observeRemoteNodes(): Flow<List<Peer>> = callbackFlow {
        // F01: fail-fast si l'identité locale est indisponible — self-exclusion ne peut pas fonctionner sans nodeId
        val identityResult = securityRepository.getIdentity()
        if (identityResult.isFailure) {
            close(identityResult.exceptionOrNull())
            return@callbackFlow
        }
        val localNodeId = identityResult.getOrThrow().nodeId
        val reference = firebaseDatabase.reference.child(NODES_PATH)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val peers = snapshot.children.mapNotNull { child ->
                    try {
                        val nodeId = child.child("nodeId").getValue(String::class.java)
                            ?: return@mapNotNull null
                        if (nodeId == localNodeId) return@mapNotNull null

                        val pubKeyB64 = child.child("publicKeyBase64").getValue(String::class.java)
                            ?: return@mapNotNull null
                        val ip = child.child("ip").getValue(String::class.java)
                            ?: return@mapNotNull null
                        val port = child.child("port").getValue(Long::class.java)?.toInt()
                            ?: return@mapNotNull null
                        val timestamp = child.child("timestamp").getValue(Long::class.java)
                            ?: return@mapNotNull null

                        if (now - timestamp > TTL_MS) return@mapNotNull null  // Filtrage TTL 60s

                        val publicKeyBytes = Base64.decode(pubKeyB64, Base64.NO_WRAP)
                        Peer(
                            identity = NodeIdentity(nodeId, publicKeyBytes),
                            lastSeenTimestampMs = timestamp,
                            source = DiscoverySource.REMOTE_FIREBASE,
                            ipAddress = ip,
                            port = port
                        )
                    } catch (e: Exception) {
                        Log.w("SignalingRepositoryImpl", "Entrée Firebase ignorée (parsing échoué) — nodeId=${child.key}", e)
                        null
                    }
                }
                trySend(peers)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        reference.addValueEventListener(listener)
        awaitClose { reference.removeEventListener(listener) }
    }
}
