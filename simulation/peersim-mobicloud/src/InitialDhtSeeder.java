package mobicloud;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.core.Control;

/**
 * Initialise la DHT au demarrage : injecte K entrees dans 1 seul noeud
 * (= simule un upload qui n'a pas encore eu le temps de se propager).
 * Le but est de mesurer le temps de convergence du gossip.
 */
public class InitialDhtSeeder implements Control {

    private static final String PAR_PROTOCOL = "protocol";
    private static final String PAR_INITIAL_BLOCKS = "initialBlocks";

    private final int dhtPid;
    private final int initialBlocks;

    public InitialDhtSeeder(String prefix) {
        dhtPid = Configuration.getPid(prefix + "." + PAR_PROTOCOL);
        initialBlocks = Configuration.getInt(prefix + "." + PAR_INITIAL_BLOCKS, 100);
    }

    @Override
    public boolean execute() {
        // Injecte initialBlocks entrees dans le noeud 0
        Node uploader = Network.get(0);
        MobiCloudGossipDht dht = (MobiCloudGossipDht) uploader.getProtocol(dhtPid);
        for (int blockId = 0; blockId < initialBlocks; blockId++) {
            int hostNodeId = CommonState.r.nextInt(Network.size());
            dht.putEntry(blockId, hostNodeId, 1L);
        }
        System.out.printf("[SEED] Inject %d entries dans noeud 0%n", initialBlocks);
        return false;
    }
}
