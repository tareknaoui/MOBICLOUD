# Brief d'Intake

**Phase :** 1 — Entretien d'Intake
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Élevée (réponses directes du fondateur)

---

## Le Problème

Dans les régions à connexion internet peu fiable ou à stockage cloud coûteux, les gens perdent leurs fichiers quand leur téléphone casse ou se fait voler. Ce n'est pas un cas marginal de niche — c'est l'expérience par défaut de centaines de millions d'utilisateurs Android dans les marchés émergents, où :
- Les abonnements de stockage cloud coûtent cher relativement aux salaires locaux
- La connectivité fiable est inconstante, même là où la couverture 4G existe
- Aucune habitude de sauvegarde systématique n'existe ; la plupart des gens acceptent la perte de fichiers comme normale

**Comment les gens font face actuellement :**
- WhatsApp « Garder dans la discussion » comme sauvegarde accidentelle et involontaire
- Transferts USB manuels vers un ordinateur portable ou le téléphone d'un proche
- Clés USB chinoises bon marché qui tombent en panne après 6 mois
- Ne rien faire — accepter la perte de fichiers comme inévitable

Le problème est réel et sous-traité. L'écart entre « les téléphones sont partout » et « une sauvegarde fiable existe » n'a été comblé par aucun produit grand public.

## La Solution

**MobiCloud** — une application Android qui permet à un groupe de smartphones de *stocker* (pas de partager) collectivement les fichiers les uns des autres, de manière distribuée et chiffrée.

**Comment ça fonctionne (technique) :**
1. Les fichiers sont chiffrés sur l'appareil via Android Keystore
2. L'erasure coding Reed-Solomon découpe chaque fichier en k+m fragments distribués entre les membres du cluster — les paramètres k et m sont configurables. Dans la démo testée à 3 téléphones : RS(2,1) — 3 fragments, 2 suffisent pour reconstituer, tolère 1 panne de nœud. Dans des clusters plus grands (ex : 50 téléphones) : k et m scalent en conséquence, tolérant m pannes de nœuds simultanées. La tolérance aux pannes croît avec la taille du cluster.
3. Les fragments sont distribués sur les téléphones des membres du groupe
4. Un serveur relay WebSocket (~10 $/mois) gère la traversée NAT pour la communication inter-réseaux (4G↔4G, 4G↔WiFi) — internet est requis pour les transferts entre appareils sur des réseaux différents
5. Une topologie super-peer gère la coordination du cluster : un pair élu via l'algorithme Bully gère l'orchestration du cluster ; le failover est automatique
6. Aucun contenu de fichier n'est jamais stocké sur le relay — le relay ne fait que router le trafic chiffré en transit. Les données au repos résident exclusivement sur les appareils des membres.

**Distinction importante — « distribué » ≠ « hors ligne » :**
MobiCloud nécessite une connexion internet (4G ou WiFi) pour les transferts de fichiers inter-appareils, car le relay est nécessaire quand les appareils sont sur des réseaux différents (ce qui est le cas courant). Ce N'EST PAS une solution hors ligne / mesh. Ce qui le distingue du stockage cloud, c'est que les données sont *stockées* sur les propres téléphones des utilisateurs — pas sur un serveur central — et que le relay ne gère que le routage, jamais la persistance.

**Ce qui fonctionne aujourd'hui (prototype) :**
- Clusters de 3 téléphones avec upload de fichiers et stockage distribué
- Auto-réparation automatique quand un téléphone passe hors ligne
- Failover super-peer via élection Bully
- Testé sur de vrais appareils (pas seulement en émulateur)

**Ce qui n'est pas encore implémenté :**
- Re-réplication après perte permanente d'un nœud (identifié comme prochaine version)
- Mécanisme d'incentive pour la contribution au stockage (retiré du scope)
- Tailles de cluster au-dessus de 3 (contrainte de démo intentionnelle — l'architecture en supporte de plus grandes en changeant la constante MAX_CLUSTER_SIZE)

## Les Clients

### Primaire : Grand public (B2C)
**Profil :** Étudiants universitaires et jeunes professionnels dans les marchés émergents — spécifiquement l'Algérie et l'Afrique du Nord — qui :
- Partagent des espaces de vie (résidences) ou travaillent dans le même bâtiment
- Possèdent des téléphones Android (plateforme dominante sur le marché)
- Ne peuvent pas se permettre ou accéder de façon fiable à Google Drive / iCloud à cause des coûts de bande passante ou des coupures de connectivité
- Se font déjà suffisamment confiance pour partager des mots de passe Wi-Fi (modèle de confiance social)

**Douleur :** Ils perdent des fichiers quand leur téléphone casse ou se fait voler. Ils n'ont aucune option de sauvegarde abordable et fiable.

### Secondaire : Institutionnel (B2G)
**Profil :** Secteur public algérien — universités, hôpitaux, ministères — qui sont :
- Activement à la recherche d'alternatives souveraines, hébergées localement, aux services cloud étrangers
- Soumis à la législation algérienne sur la souveraineté des données (Loi n° 11-25, Décret présidentiel 25-321)
- Empêchés d'utiliser Google Drive / Microsoft 365 pour les documents sensibles en raison des exigences de conformité

**Douleur :** Les fournisseurs cloud étrangers ne peuvent pas, légalement ou structurellement, offrir des données qui ne quittent jamais la juridiction algérienne. MobiCloud le peut.

## L'Équipe

**Fondateur solo :** Étudiant en dernière année d'informatique (Algérie). Maîtrise technique totale — développement Android, conception de systèmes distribués et simulation, le tout réalisé par une seule personne.

**Forces :** Profondeur technique full-stack, connaissance intime de l'architecture du système, prototype réel existant.
**Lacunes :** Pas de co-fondateur business ou design, pas de relations commerciales ou institutionnelles, pas de background marketing.

## Pourquoi Maintenant

1. **La pénétration 4G en Afrique a dépassé 50 % en 2024** — les téléphones sont connectés mais le cloud reste cher relativement aux revenus locaux. L'écart d'infrastructure se comble ; l'écart d'accessibilité financière, non. [Données, GSMA 2024]
2. **La législation algérienne sur la souveraineté numérique est active** — Loi n° 11-25 (juillet 2025), Décret présidentiel 25-321 (déc. 2025), Décret présidentiel 26-07 (jan. 2026). Le vent réglementaire favorable est réel et récent.
3. **Android Keystore et les bibliothèques d'erasure coding sont matures** — les primitives techniques pour construire cela sans matériel spécialisé existent désormais sur les appareils grand public.
4. **Aucune app de stockage P2P Android grand public n'existe sans friction crypto/blockchain** — le gap dans le marché est authentique.

## Paysage Concurrentiel (Évaluation du Fondateur)

| Concurrent | Type | Écart vs. MobiCloud |
|---|---|---|
| Google Drive / iCloud | Cloud centralisé | Juridiction étrangère, coût d'abonnement, connectivité requise |
| Filecoin / Storj | Décentralisé (blockchain) | Nécessite un wallet crypto, pas d'UX grand public, orienté développeurs |
| IPFS | Protocole, pas produit | Pas d'app grand public |
| Briar | Messagerie P2P | Messagerie, pas stockage |
| Hivenet | Stockage distribué, app Android | Basé en UE, pas d'angle souveraineté, pas de focus Afrique |
| Cubbit | B2B géo-distribué | Entreprise uniquement, pas de mobile grand public |

**[Opinion] :** La niche grand public — groupe de confiance, téléphone-à-téléphone, sans crypto, Afrique-first — semble réellement inoccupée.

## Modèle Économique (Préliminaire)

Trois voies identifiées, aucune entièrement validée :

**Voie 1 — Institutionnel B2G (la plus défendable à court terme) :**
Vendre aux universités, hôpitaux ou ministères algériens comme solution de stockage intranet souverain avec contrat de support. Modèle de revenus : licence annuelle par institution + frais de support. Le relay hébergé sur infrastructure algérienne est un prérequis. Nécessite des relations institutionnelles (actuellement absentes).

**Voie 2 — Abonnement (grand public) :**
Modèle d'abonnement à paliers pour particuliers et groupes. Palier gratuit : petit groupe (jusqu'à 3 téléphones), stockage limité. Palier payant : clusters plus grands, plus de quota de stockage, bande passante relay prioritaire. Modèle de revenus : abonnement mensuel ou annuel par utilisateur ou par cluster. Nécessite une base d'utilisateurs réelle avant de générer un revenu significatif.

**Voie 3 — Relay-as-a-Service (infrastructure) :**
Le serveur relay est le seul composant centralisé que MobiCloud contrôle. Facturer par cluster actif, par Go relayé, ou par mois par tenant. Mettre le client Android en open-source ; monétiser le relay. Modèle de revenus : basé sur l'usage. Le moat technique le plus propre — personne ne peut répliquer le relay souverain tournant sur des serveurs algériens.

**Évaluation du fondateur (post-brainstorm) :** B2G + RaaS sont les voies les plus défendables à court terme. L'abonnement grand public est le levier de volume à long terme. Le B2G finance le développement grand public — pas l'inverse.

**Aucun prix validé avec des clients pour l'instant.**

## Lacunes et Risques Connus

| Lacune | Sévérité | Notes |
|---|---|---|
| Pas de mécanisme d'incentive pour la contribution | Élevée | La confiance sociale fonctionne en groupes fermés ; se casse pour des inconnus |
| Pas d'utilisateurs réels (labo uniquement) | Élevée | Aucune validation terrain d'aucune hypothèse |
| Serveur relay sur infrastructure US (Render) | Élevée | Entre en conflit avec la localisation des données algérienne pour les ventes institutionnelles |
| Pas de contacts institutionnels | Élevée | La voie B2G n'a aucune fondation commerciale |
| Perte permanente de nœud → perte de données | Moyenne | RS(2,1) : perdre 2 nœuds sur 3 = fichier irrécupérable |
| Re-réplication non implémentée | Moyenne | Identifiée pour la prochaine version |
| Fondateur solo — pas de compétences business/design | Moyenne | Lacune finançable mais limite la traction initiale |
| Taille de cluster plafonnée à 3 pour la démo | Faible | La contrainte architecturale est trivialement supprimable |

## Définition du Succès (12 Mois)

- 3 groupes pilotes de 10+ utilisateurs réels actifs quotidiennement pendant 30+ jours consécutifs sans perte de données
- 1 conversation institutionnelle ayant progressé jusqu'à un accord de pilot signé (même non payé)
- Serveur relay gérant 50+ clusters simultanés sans crash

## Critères d'Arrêt (Propres au Fondateur)

« Si, après 6 mois de tests en conditions réelles, les groupes arrêtent systématiquement de l'utiliser après la première semaine parce que gérer l'appartenance au cluster est trop fragile — les téléphones partent, les clusters se cassent, les fichiers deviennent inaccessibles — et que la barre de fiabilité pour les utilisateurs non-techniques s'avère fondamentalement inatteignable avec l'architecture actuelle. »

## Défense Concurrentielle

**Contre Google lançant un produit similaire :**
Google lançant un stockage P2P signifie toujours que Google contrôle le relay, que Google lit les métadonnées, et que les données vivent sous juridiction US. L'angle secteur public rejette cela par design. Le positionnement souveraineté est structurellement impossible à répliquer pour tout fournisseur étranger.

---

## Drapeaux

**Drapeaux Rouges :**
- Pas d'utilisateurs réels. Toute hypothèse sur la rétention, l'utilisabilité et l'incentive est non testée.
- Le serveur relay sur infrastructure US est structurellement incompatible avec les ventes institutionnelles B2G sous la loi algérienne actuelle.

**Drapeaux Jaunes :**
- Fondateur solo sans compétences business — le produit technique peut être solide mais l'exécution go-to-market est à haut risque.
- Le problème d'incentive limite l'échelle grand public aux groupes de confiance — plafond social sur le TAM tant qu'aucun mécanisme de récompense n'est ajouté.
- Pas de contacts institutionnels — le B2G est la voie « la plus défendable » mais n'a aucun pipeline commercial.
- Hivenet existe avec une app Android — la différenciation doit être communiquée de manière proactive.

## Sources
- Entretiens fondateur (juin 2026) — direct
- Conclusions du pré-flight : `00-intake/preflight.md`
