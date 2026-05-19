package mobicloud;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Linkable;
import peersim.core.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * Modele PeerSim du protocole DHT replique + gossip CRDT LWW de MobiCloud.
 *
 * A chaque cycle (= 2 secondes simulees), chaque noeud :
 *   1. Choisit aleatoirement K voisins (fan-out gossip)
 *   2. Echange sa DHT locale avec eux
 *   3. Resoud les conflits par Last-Writer-Wins (timestamp)
 *
 * Equivalent du GossipSyncUseCase.runGossipCycle() dans le code Android,
 * mais simplifie pour la simulation (pas de Bloom filter -- exchange complet).
 *
 * Si un protocol.battery est configure, chaque echange gossip consomme
 * de l'energie (TX pour l'emetteur, RX pour le recepteur).
 */
public class MobiCloudGossipDht implements CDProtocol {

    private static final String PAR_LINKABLE = "linkable";
    private static final String PAR_FANOUT   = "fanout";
    private static final String PAR_BATTERY  = "battery";

    private final int linkablePid;
    private final int fanout;
    private final int batteryPid; // -1 si pas configure

    /** Entree DHT modelisee : (blockId, hostNodeId, timestamp). */
    public static class DhtEntry {
        public final int hostNodeId;
        public final long timestamp;
        public DhtEntry(int hostNodeId, long timestamp) {
            this.hostNodeId = hostNodeId;
            this.timestamp = timestamp;
        }
    }

    /** DHT locale du noeud : blockId -> entry. */
    public Map<Integer, DhtEntry> dht = new HashMap<>();

    public MobiCloudGossipDht(String prefix) {
        linkablePid = Configuration.getPid(prefix + "." + PAR_LINKABLE);
        fanout      = Configuration.getInt(prefix + "." + PAR_FANOUT, 2);
        batteryPid  = Configuration.getPid(prefix + "." + PAR_BATTERY, -1);
    }

    @Override
    public Object clone() {
        try {
            MobiCloudGossipDht copy = (MobiCloudGossipDht) super.clone();
            copy.dht = new HashMap<>(this.dht);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if (!node.isUp()) return;
        Linkable linkable = (Linkable) node.getProtocol(linkablePid);
        if (linkable.degree() == 0) return;

        // Choisir 'fanout' voisins aleatoirement et echanger la DHT
        for (int i = 0; i < fanout && i < linkable.degree(); i++) {
            int idx = CommonState.r.nextInt(linkable.degree());
            Node peer = linkable.getNeighbor(idx);
            if (peer == null || !peer.isUp()) continue;

            MobiCloudGossipDht peerDht = (MobiCloudGossipDht) peer.getProtocol(protocolID);
            mergeDht(this.dht, peerDht.dht);
            mergeDht(peerDht.dht, this.dht);

            // Consommation batterie : TX pour l'emetteur, RX pour le pair
            if (batteryPid >= 0) {
                BatteryProtocol myBat   = (BatteryProtocol) node.getProtocol(batteryPid);
                BatteryProtocol peerBat = (BatteryProtocol) peer.getProtocol(batteryPid);
                myBat.deductSend(node);
                peerBat.deductReceive(peer);
            }
        }
    }

    /**
     * Fusion CRDT Last-Writer-Wins : pour chaque blockId, on garde l'entree
     * avec le timestamp le plus recent.
     */
    private static void mergeDht(Map<Integer, DhtEntry> target, Map<Integer, DhtEntry> source) {
        for (Map.Entry<Integer, DhtEntry> e : source.entrySet()) {
            DhtEntry existing = target.get(e.getKey());
            if (existing == null || e.getValue().timestamp > existing.timestamp) {
                target.put(e.getKey(), e.getValue());
            }
        }
    }

    /** API utilisee par les Initializer / Observer. */
    public void putEntry(int blockId, int hostNodeId, long timestamp) {
        dht.put(blockId, new DhtEntry(hostNodeId, timestamp));
    }

    public int size() {
        return dht.size();
    }
}
