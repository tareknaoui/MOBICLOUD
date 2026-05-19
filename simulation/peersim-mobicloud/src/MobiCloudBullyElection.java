package mobicloud;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Linkable;
import peersim.core.Node;

/**
 * Modele PeerSim simplifie du protocole Bully Election de MobiCloud.
 *
 * Au demarrage, AUCUN noeud n'est super-peer.
 * A chaque cycle, chaque noeud regarde ses voisins et applique les regles :
 *
 *   1. Si je vois un super-peer avec un score > le mien → je m'efface (je n'essaie pas
 *      de me presenter)
 *   2. Si je suis super-peer ET un autre super-peer avec un score > le mien existe parmi
 *      mes voisins → j'abdique (regle d'unicite par cluster : seul le meilleur reste)
 *   3. Si je ne vois AUCUN super-peer avec score >= le mien → je me declare super-peer
 *
 * Au final, le noeud avec le score MAX du reseau doit etre l'unique super-peer.
 *
 * Equivalent simplifie de RunBullyElectionUseCase dans le code Android, mais sans
 * les delais de monitoring (geres ici par les cycles successifs).
 */
public class MobiCloudBullyElection implements CDProtocol {

    private static final String PAR_LINKABLE = "linkable";
    private final int linkablePid;

    /** Score de fiabilite assigne aleatoirement au demarrage (0.0 - 1.0). */
    public double score;

    /** Statut actuel du noeud. */
    public boolean isSuperPeer;

    public MobiCloudBullyElection(String prefix) {
        linkablePid = Configuration.getPid(prefix + "." + PAR_LINKABLE);
        // Score aleatoire (init reproductible via random.seed dans le config)
        score = CommonState.r.nextDouble();
        isSuperPeer = false;
    }

    @Override
    public Object clone() {
        try {
            MobiCloudBullyElection copy = (MobiCloudBullyElection) super.clone();
            // Chaque noeud clone aura son propre score aleatoire
            copy.score = CommonState.r.nextDouble();
            copy.isSuperPeer = false;
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

        // Trouver le score MAX parmi mes voisins (et leurs statuts super-peer)
        double maxNeighborScore = 0.0;
        boolean higherSuperPeerExists = false;

        for (int i = 0; i < linkable.degree(); i++) {
            Node peer = linkable.getNeighbor(i);
            if (peer == null || !peer.isUp()) continue;

            MobiCloudBullyElection peerProto =
                (MobiCloudBullyElection) peer.getProtocol(protocolID);

            if (peerProto.score > maxNeighborScore) {
                maxNeighborScore = peerProto.score;
            }
            if (peerProto.isSuperPeer && peerProto.score > this.score) {
                higherSuperPeerExists = true;
            }
        }

        // Application des regles Bully :
        if (higherSuperPeerExists) {
            // Regle 1 ou 2 : un super-peer plus fort existe → j'abandonne
            this.isSuperPeer = false;
        } else if (this.score >= maxNeighborScore) {
            // Regle 3 : je suis le meilleur visible → je deviens super-peer
            this.isSuperPeer = true;
        }
        // Sinon : je ne suis pas le meilleur, j'attends qu'un super-peer apparaisse
    }
}
