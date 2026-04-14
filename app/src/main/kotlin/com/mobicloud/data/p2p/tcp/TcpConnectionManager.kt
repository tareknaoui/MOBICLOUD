package com.mobicloud.data.p2p.tcp

import android.util.Log
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Composant central pour l'échange TCP P2P.
 * Gère à la fois l'écoute entrante (Serveur) et la connexion sortante (Client).
 */
class TcpConnectionManager @Inject constructor(
    private val securityRepository: SecurityRepository
) {
    private var serverSocket: ServerSocket? = null

    /** Ensemble des nodeId avec lesquels un handshake TCP a déjà réussi. */
    private val handshaked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Retourne true si un handshake TCP a déjà été complété avec ce nœud. */
    fun isConnected(nodeId: String): Boolean = nodeId in handshaked

    // F-03 [Review][Patch]: Référence stockée pour permettre interrupt() dans stopServer().
    private var serverThread: Thread? = null

    /**
     * Démarre le serveur ServerSocket sur un port disponible et l'écoute en arrière-plan.
     * @return Le port assigné pour annoncer sur Firebase.
     */
    suspend fun startServer(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(0) // Port dynamique attribué par le système
            val port = serverSocket!!.localPort
            Log.i("MobiCloud:TCP", "Serveur TCP P2P démarré localement sur le port $port")

            // F-03 [Review][Patch]: Thread stocké pour permettre stopServer() de l'interrompre proprement.
            serverThread = Thread {
                while (!Thread.currentThread().isInterrupted && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        handleIncomingConnection(clientSocket)
                    } catch (e: Exception) {
                        if (serverSocket?.isClosed == false) {
                            Log.e("MobiCloud:TCP", "Erreur lors de l'acceptation de la socket", e)
                        }
                    }
                }
            }.also { it.start() }

            Result.success(port)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gère une connexion P2P entrante.
     * Lit l'identité distante et envoie la sienne.
     *
     * F-01 [Review][Patch]: runBlocking supprimé — getIdentity() n'est pas suspend.
     * F-02 [Review][Patch]: getOrElse remplace getOrThrow() pour éviter un crash non géré.
     */
    private fun handleIncomingConnection(socket: Socket) {
        try {
            val input = ObjectInputStream(socket.getInputStream())
            val remotePublicId = input.readUTF()

            // handleIncomingConnection runs on a plain Thread (not a coroutine).
            // runBlocking is justified here: the thread is inherently blocking (socket I/O).
            val localIdentity = runBlocking { securityRepository.getIdentity() }.getOrElse { error ->
                Log.e("MobiCloud:TCP", "Impossible de récupérer l'identité locale (handshake entrant)", error)
                return
            }

            val output = ObjectOutputStream(socket.getOutputStream())
            output.writeUTF(localIdentity.nodeId)
            output.flush()

            // F-05 [Review][Patch]: Logs TESTPOC remplacés par des logs structurés au niveau INFO.
            Log.i("MobiCloud:TCP", "Handshake TCP entrant réussi | pair=$remotePublicId | ip=${socket.inetAddress.hostAddress}")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lors du Handshake entrant", e)
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    /**
     * Initie une connexion TCP avec un Pair distant (dont l'IP/Port ont été récupérés par Firebase).
     *
     * F-04 [Review][Patch]: Même pattern getOrElse que handleIncomingConnection — cohérence unifiée.
     */
    suspend fun connectToPeer(peer: Peer) = withContext(Dispatchers.IO) {
        if (peer.ipAddress == null || peer.port == null) return@withContext
        var socket: Socket? = null
        try {
            // F-04 [Review][Patch]: Pattern unifié avec handleIncomingConnection.
            val localIdentity = securityRepository.getIdentity().getOrElse { error ->
                Log.e("MobiCloud:TCP", "Impossible de récupérer l'identité locale (connexion sortante)", error)
                return@withContext
            }
            Log.i("MobiCloud:TCP", "Tentative de connexion TCP sortante vers ${peer.ipAddress}:${peer.port}...")

            // Timeout à 5 secondes pour ne pas bloquer infiniment
            socket = Socket()
            socket.connect(java.net.InetSocketAddress(peer.ipAddress!!, peer.port!!), 5000)

            // Envoi localIdentity -> Output
            val output = ObjectOutputStream(socket.getOutputStream())
            output.writeUTF(localIdentity.nodeId)
            output.flush()

            // Réception remoteIdentity <- Input
            val input = ObjectInputStream(socket.getInputStream())
            val remoteId = input.readUTF()

            // F-05 [Review][Patch]: Logs TESTPOC remplacés par des logs structurés au niveau INFO.
            Log.i("MobiCloud:TCP", "Handshake TCP sortant réussi | pair=$remoteId | ip=${peer.ipAddress}")
            handshaked.add(peer.identity.nodeId)
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lors de la connexion sortante TCP vers ${peer.ipAddress}", e)
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    fun stopServer() {
        // F-03 [Review][Patch]: Interruption du thread avant fermeture de la socket.
        serverThread?.interrupt()
        serverThread = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        handshaked.clear()
    }
}
