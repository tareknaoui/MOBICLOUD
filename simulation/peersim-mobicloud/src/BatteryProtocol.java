package mobicloud;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Fallible;
import peersim.core.Node;
import peersim.cdsim.CDProtocol;

/**
 * Modele la batterie d'un noeud mobile.
 *
 * Chaque echange gossip consomme de l'energie :
 *   - costPerSend    : cout d'emission d'un message (radio TX)
 *   - costPerReceive : cout de reception d'un message (radio RX)
 *
 * Quand batteryLevel <= 0, le noeud est mis DOWN (panne batterie).
 */
public class BatteryProtocol implements CDProtocol {

    private static final String PAR_INIT_LEVEL    = "initialLevel";
    private static final String PAR_COST_SEND     = "costPerSend";
    private static final String PAR_COST_RECEIVE  = "costPerReceive";

    private final double initialLevel;
    public  final double costPerSend;
    public  final double costPerReceive;

    public double batteryLevel;

    public BatteryProtocol(String prefix) {
        initialLevel   = Configuration.getDouble(prefix + "." + PAR_INIT_LEVEL,   100.0);
        costPerSend    = Configuration.getDouble(prefix + "." + PAR_COST_SEND,    0.5);
        costPerReceive = Configuration.getDouble(prefix + "." + PAR_COST_RECEIVE, 0.3);
        batteryLevel   = initialLevel;
    }

    @Override
    public Object clone() {
        try {
            BatteryProtocol copy = (BatteryProtocol) super.clone();
            copy.batteryLevel = this.initialLevel;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deduit le cout d'un envoi gossip.
     * Si la batterie s'epuise, met le noeud DOWN.
     */
    public void deductSend(Node node) {
        batteryLevel -= costPerSend;
        if (batteryLevel <= 0) {
            batteryLevel = 0;
            node.setFailState(Fallible.DOWN);
        }
    }

    /**
     * Deduit le cout d'une reception gossip.
     */
    public void deductReceive(Node node) {
        batteryLevel -= costPerReceive;
        if (batteryLevel <= 0) {
            batteryLevel = 0;
            node.setFailState(Fallible.DOWN);
        }
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        // La batterie ne se gere pas en nextCycle ici :
        // elle est decrementee directement par MobiCloudGossipDht.
    }
}
