package com.mobicloud.data.repository_impl

import android.util.Base64
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BootstrapRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseBootstrapRepositoryImpl @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val firebaseDatabase: FirebaseDatabase
) : BootstrapRepository {

    override suspend fun announcePresence(ip: String, port: Int): Result<Unit> {
        return try {
            val identity = securityRepository.getIdentity().getOrThrow()
            
            val nodeData = mapOf(
                "publicId" to identity.publicId,
                "publicKeyBytes" to Base64.encodeToString(identity.publicKeyBytes, Base64.NO_WRAP),
                "ipAddress" to ip,
                "port" to port,
                "lastSeenTimestampMs" to System.currentTimeMillis()
            )

            firebaseDatabase.reference
                .child("active_nodes")
                .child(identity.publicId)
                .setValue(nodeData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeActivePeers(): Flow<List<Peer>> = callbackFlow {
        val reference = firebaseDatabase.reference.child("active_nodes")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val peers = mutableListOf<Peer>()
                for (child in snapshot.children) {
                    try {
                        val publicId = child.child("publicId").getValue(String::class.java) ?: continue
                        val pubKeyB64 = child.child("publicKeyBytes").getValue(String::class.java) ?: continue
                        val ip = child.child("ipAddress").getValue(String::class.java) ?: continue
                        val port = child.child("port").getValue(Int::class.java) ?: continue
                        val timestamp = child.child("lastSeenTimestampMs").getValue(Long::class.java) ?: continue

                        val publicKeyBytes = Base64.decode(pubKeyB64, Base64.NO_WRAP)
                        
                        val identity = NodeIdentity(publicId, publicKeyBytes)
                        val peer = Peer(
                            identity = identity,
                            lastSeenTimestampMs = timestamp,
                            source = DiscoverySource.REMOTE_FIREBASE,
                            ipAddress = ip,
                            port = port
                        )
                        peers.add(peer)
                    } catch (e: Exception) {
                        // Ignorer les noeuds mal formés
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
