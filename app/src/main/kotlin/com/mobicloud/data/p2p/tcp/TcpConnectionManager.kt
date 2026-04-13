package com.mobicloud.data.p2p.tcp

import android.util.Log
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject

/**
 * Composant central pour l'échange TCP P2P.
 * Gère à la fois l'écoute entrante (Serveur) et la connexion sortante (Client).
 */
class TcpConnectionManager @Inject constructor(
    private val securityRepository: SecurityRepository
) {
    private var serverSocket: ServerSocket? = null

    /**
     * Démarre le serveur ServerSocket sur un port disponible et l'écoute en arrière-plan.
     * @return Le port assigné pour annoncer sur Firebase.
     */
    suspend fun startServer(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(0) // Port dynamique attribué par le système
            val port = serverSocket!!.localPort
            Log.i("MobiCloud:TCP", "Serveur TCP P2P démarré localement sur le port $port")

            Thread {
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
            }.start()

            Result.success(port)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gère une connexion P2P entrante.
     * Lit l'identité distante et envoie la sienne.
     */
    private fun handleIncomingConnection(socket: Socket) {
        try {
            val input = ObjectInputStream(socket.getInputStream())
            val remotePublicId = input.readUTF()

            val localIdentity = securityRepository.getIdentity().getOrThrow()
            val output = ObjectOutputStream(socket.getOutputStream())
            output.writeUTF(localIdentity.publicId)
            output.flush()

            Log.w("TESTPOC", "===============================================")
            Log.w("TESTPOC", "[SUCCESS] P2P_SUCCESS: Handshake TCP ENTRANT réussi avec le pair : $remotePublicId")
            Log.w("TESTPOC", "IP Distante : ${socket.inetAddress.hostAddress}")
            Log.w("TESTPOC", "===============================================")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lors du Handshake entrant", e)
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    /**
     * Initie une connexion TCP avec un Pair distant (dont l'IP/Port ont été récupérés par Firebase).
     */
    suspend fun connectToPeer(peer: Peer) = withContext(Dispatchers.IO) {
        if (peer.ipAddress == null || peer.port == null) return@withContext
        var socket: Socket? = null
        try {
            val localIdentity = securityRepository.getIdentity().getOrThrow()
            Log.i("MobiCloud:TCP", "Tentative de connexion TCP sortante vers ${peer.ipAddress}:${peer.port}...")
            
            // Timeout à 5 secondes pour ne pas bloquer infiniment
            socket = Socket()
            socket.connect(java.net.InetSocketAddress(peer.ipAddress!!, peer.port!!), 5000)
            
            // Envoi localIdentity -> Output
            val output = ObjectOutputStream(socket.getOutputStream())
            output.writeUTF(localIdentity.publicId)
            output.flush()

            // Réception remoteIdentity <- Input
            val input = ObjectInputStream(socket.getInputStream())
            val remoteId = input.readUTF()

            Log.w("TESTPOC", "===============================================")
            Log.w("TESTPOC", "[SUCCESS] P2P_SUCCESS: Handshake TCP SORTANT réussi !")
            Log.w("TESTPOC", "Le Node distant '$remoteId' (IP: ${peer.ipAddress}) a retourné un Ack.")
            Log.w("TESTPOC", "PREUVE DU PONT ROUTIER SINGLE-HOP VALIDÉE.")
            Log.w("TESTPOC", "===============================================")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lors de la connexion sortante TCP vers ${peer.ipAddress}", e)
        } finally {
            try { socket?.close() } catch(e: Exception) {}
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
    }
}
