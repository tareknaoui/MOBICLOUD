package mobicloud;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

/**
 * Mesure a chaque cycle : niveau moyen de batterie et nombre de noeuds
 * encore actifs (batterie > 0).
 *
 * Sortie CSV : cycle;avg_battery;alive_nodes;dead_battery_nodes;total_nodes
 */
public class BatteryObserver implements Control {

    private static final String PAR_BATTERY = "battery";

    private final int batteryPid;

    public BatteryObserver(String prefix) {
        batteryPid = Configuration.getPid(prefix + "." + PAR_BATTERY);
    }

    @Override
    public boolean execute() {
        double totalBattery = 0;
        int aliveNodes      = 0;
        int deadBattery     = 0;
        int total           = Network.size();

        for (int i = 0; i < total; i++) {
            Node node = Network.get(i);
            BatteryProtocol bp = (BatteryProtocol) node.getProtocol(batteryPid);

            if (node.isUp()) {
                aliveNodes++;
                totalBattery += bp.batteryLevel;
            } else if (bp.batteryLevel <= 0) {
                deadBattery++;
            }
        }

        double avgBattery = aliveNodes > 0 ? totalBattery / aliveNodes : 0;

        // CSV : cycle ; avg_battery ; alive_nodes ; dead_battery_nodes ; total_nodes
        System.out.printf("%d;%.2f;%d;%d;%d%n",
            peersim.core.CommonState.getTime(),
            avgBattery,
            aliveNodes,
            deadBattery,
            total);

        return false;
    }
}
