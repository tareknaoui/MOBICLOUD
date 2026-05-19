package mobicloud;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

/**
 * Mesure a chaque cycle : combien de noeuds ont la DHT complete (= convergence)
 * Sortie : CSV "cycle, avg_size, converged_nodes, total_nodes"
 */
public class ConvergenceObserver implements Control {

    private static final String PAR_PROTOCOL = "protocol";
    private static final String PAR_TARGET_SIZE = "targetSize";

    private final int dhtPid;
    private final int targetSize;

    public ConvergenceObserver(String prefix) {
        dhtPid = Configuration.getPid(prefix + "." + PAR_PROTOCOL);
        targetSize = Configuration.getInt(prefix + "." + PAR_TARGET_SIZE, 100);
    }

    @Override
    public boolean execute() {
        long totalSize = 0;
        int convergedNodes = 0;
        int aliveNodes = 0;
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (!node.isUp()) continue;
            aliveNodes++;
            MobiCloudGossipDht dht = (MobiCloudGossipDht) node.getProtocol(dhtPid);
            int s = dht.size();
            totalSize += s;
            if (s >= targetSize) convergedNodes++;
        }
        double avg = aliveNodes > 0 ? (double) totalSize / aliveNodes : 0;
        // CSV : cycle ; avg_dht ; converged_nodes ; alive_nodes
        System.out.printf("%d;%.2f;%d;%d%n",
            peersim.core.CommonState.getTime(),
            avg,
            convergedNodes,
            aliveNodes);
        return false;
    }
}
