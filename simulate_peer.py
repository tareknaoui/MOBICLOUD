import socket
import time

# Configuration (doit correspondre à P2PModule.kt)
MCAST_GRP = '224.0.0.1'
MCAST_PORT = 50000

# Simulation d'un payload Protobuf minimal (NodeIdentity)
# Note: Format binaire exact requis pour une prod réelle, 
# mais ici on teste la réaction du Receiver.
# publicId = "VirtualPeer-1", publicKeyBytes = [0,1,2]
# PAYLOAD_HEX = ... (Généré par test)

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)

print(f"Simulation du pair envoyant des heartbeats sur {MCAST_GRP}:{MCAST_PORT}...")

try:
    while True:
        # Envoi d'un message (ajuster le payload selon le format Protobuf)
        # Pour un test rapide, un Receiver robuste devrait ignorer les données malformées 
        # ou logger une SerializationException.
        sock.sendto(b"\x0a\x0dVirtualPeer-1\x12\x03\x00\x01\x02", (MCAST_GRP, MCAST_PORT))
        time.sleep(2)
except KeyboardInterrupt:
    print("Arrêt de la simulation.")
