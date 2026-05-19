package mobicloud;

import peersim.config.Configuration;
import peersim.core.*;

/**
 * Simule le churn mobile : a chaque step, une fraction churnRate des noeuds
 * actifs tombe (DOWN), puis une fraction reviveRate des noeuds DOWN se reconnecte.
 *
 * Equivalent des deconnexions/reconnexions frequentes sur reseau mobile (4G/WiFi).
 */
public class ChurnControl implements Control {

    private static final String PAR_CHURN_RATE  = "churnRate";
    private static final String PAR_REVIVE_RATE = "reviveRate";

    private final double churnRate;
    private final double reviveRate;

    public ChurnControl(String prefix) {
        churnRate  = Configuration.getDouble(prefix + "." + PAR_CHURN_RATE,  0.20);
        reviveRate = Configuration.getDouble(prefix + "." + PAR_REVIVE_RATE, 0.50);
    }

    @Override
    public boolean execute() {
        int n      = Network.size();
        int toKill = (int) (n * churnRate);
        int killed = 0;

        // Deconnecter aleatoirement toKill noeuds actifs
        for (int attempt = 0; attempt < n * 3 && killed < toKill; attempt++) {
            Node node = Network.get(CommonState.r.nextInt(n));
            if (node.isUp()) {
                node.setFailState(Fallible.DOWN);
                killed++;
            }
        }

        // Reconnecter une fraction des noeuds DOWN (retour WiFi/4G)
        int toRevive = (int) (killed * reviveRate);
        int revived  = 0;
        for (int attempt = 0; attempt < n * 3 && revived < toRevive; attempt++) {
            Node node = Network.get(CommonState.r.nextInt(n));
            if (!node.isUp()) {
                node.setFailState(Fallible.OK);
                revived++;
            }
        }

        return false;
    }
}
