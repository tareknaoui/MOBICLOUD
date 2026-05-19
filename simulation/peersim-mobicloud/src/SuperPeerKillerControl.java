package mobicloud;

import peersim.config.Configuration;
import peersim.core.*;

/**
 * Tue le super-peer elu exactement au cycle killAtCycle.
 *
 * Sert a mesurer le temps de re-election Bully apres panne du super-peer :
 * on observe combien de cycles il faut avant qu'un nouveau super-peer emerge.
 */
public class SuperPeerKillerControl implements Control {

    private static final String PAR_PROTOCOL    = "protocol";
    private static final String PAR_KILL_CYCLE  = "killAtCycle";

    private final int  bullyPid;
    private final long killAtCycle;
    private boolean    done = false;

    public SuperPeerKillerControl(String prefix) {
        bullyPid    = Configuration.getPid(prefix + "." + PAR_PROTOCOL);
        killAtCycle = Configuration.getLong(prefix + "." + PAR_KILL_CYCLE, 20);
    }

    @Override
    public boolean execute() {
        if (done || CommonState.getTime() != killAtCycle) return false;

        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (!node.isUp()) continue;
            MobiCloudBullyElection proto =
                (MobiCloudBullyElection) node.getProtocol(bullyPid);
            if (proto.isSuperPeer) {
                node.setFailState(Fallible.DOWN);
                System.err.println("[SuperPeerKiller] noeud " + i
                    + " (score=" + String.format("%.4f", proto.score)
                    + ") tue au cycle " + killAtCycle);
                done = true;
                return false;
            }
        }
        return false;
    }
}
