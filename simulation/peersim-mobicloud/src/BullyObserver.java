package mobicloud;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;

/**
 * A chaque cycle, mesure :
 *   - Combien de noeuds sont super-peer (= bug si > 1 dans le cas mono-cluster)
 *   - Le score du super-peer elu (devrait converger vers le score MAX du reseau)
 *   - L'identifiant du super-peer (= index du noeud)
 *
 * Sortie CSV : cycle ; nb_super_peers ; score_max_super_peer ; total_noeuds
 */
public class BullyObserver implements Control {

    private static final String PAR_PROTOCOL = "protocol";
    private final int bullyPid;

    public BullyObserver(String prefix) {
        bullyPid = Configuration.getPid(prefix + "." + PAR_PROTOCOL);
    }

    @Override
    public boolean execute() {
        int superPeerCount = 0;
        double maxSuperPeerScore = 0.0;

        for (int i = 0; i < Network.size(); i++) {
            MobiCloudBullyElection bully =
                (MobiCloudBullyElection) Network.get(i).getProtocol(bullyPid);
            if (bully.isSuperPeer) {
                superPeerCount++;
                if (bully.score > maxSuperPeerScore) {
                    maxSuperPeerScore = bully.score;
                }
            }
        }

        // CSV : cycle ; nb_super_peers ; score_max_super_peer ; total_noeuds
        System.out.printf("%d;%d;%.4f;%d%n",
            peersim.core.CommonState.getTime(),
            superPeerCount,
            maxSuperPeerScore,
            Network.size());
        return false;
    }
}
