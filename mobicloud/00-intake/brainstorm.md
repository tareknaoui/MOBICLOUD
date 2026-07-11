# Brainstorm — Variations d'Idées MobiCloud

**Phase :** 2 — Brainstorm
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Élevée (direction validée par le fondateur)

---

## Variations Explorées

Sept variations ont été présentées sur un spectre allant du plus simple au plus ambitieux :

| Variation | Direction Centrale | Verdict |
|---|---|---|
| 1 — Grand Public Groupe de Confiance | Original : app de sauvegarde en groupe fermé | À garder comme levier grand public long terme |
| 2 — B2G d'Abord | Sauter le grand public, vendre aux institutions | **Beachhead sélectionné** |
| 3 — Relay-as-a-Service | Monétiser l'infrastructure relay, client open-source | **Actif central identifié** |
| 4 — Coffre-fort Documents Étudiants | Vertical étroit pour le segment universitaire | Utile pour les premiers pilots, pas autonome |
| 5 — Backup Buddy 1:1 | Réduire au minimum : sauvegarde à 2 personnes | Mis de côté — le prototype dépasse déjà ceci |
| 6 — Mesh Hors Ligne | Retirer le relay, utiliser WiFi Direct/Bluetooth | Rejeté — WiFi Direct sur Android est non fiable par conception |
| 7 — Cloud Souverain Africain | Vision 10x : continent du cloud souverain | Étoile polaire pour la narrative, pas la roadmap |

## Direction du Fondateur (synthèse verbatim)

**« Tu n'es pas une app company, tu es une infrastructure company. »**

Le relay est déjà construit, déployé et opérationnel. C'est le seul composant centralisé contrôlé par MobiCloud qu'aucun concurrent ne peut répliquer sans permission. L'app Android peut être open-source — le relay tournant sur des serveurs algériens, sous juridiction algérienne, est l'actif.

La voie institutionnelle B2G (Variation 2) est le bon premier client parce que :
- Aucun marketing ou distribution grand public requis
- Un seul contrat de pilot prouve le modèle
- La souveraineté des données n'est pas un argument de vente — c'est une obligation légale pour les institutions, et la pression augmente
- L'urgence est du côté de l'acheteur, pas du vendeur

## Direction Sélectionnée — Idée Affinée

**Infrastructure Relay Souveraine + App Android, vendue aux institutions algériennes (B2G d'abord)**

**Ce que MobiCloud vend :**
Un serveur relay hébergé en Algérie sur infrastructure algérienne (pas Render/US) + le client de stockage distribué Android + contrat de support + paliers d'abonnement optionnels pour le grand public. Données patients / dossiers étudiants / documents gouvernementaux ne quittent jamais le territoire algérien. Les fournisseurs cloud étrangers ne peuvent pas répliquer légalement cette proposition.

**Clarification technique (corrigée par rapport au brainstorm initial) :**
MobiCloud N'EST PAS une solution hors ligne / mesh. Internet (4G ou WiFi) est requis pour les transferts inter-appareils car le relay gère la traversée NAT entre appareils sur des réseaux différents. La distinction clé est : *les données sont stockées sur les propres téléphones des utilisateurs, pas sur un serveur* — mais le transfert nécessite la connectivité. La présenter comme « locale » ou « hors ligne » est inexact et tromperait les acheteurs institutionnels.

**Premier client cible :** Une université ou un ministère. Un seul contrat de pilot signé (même non payé) valide le modèle et finance ensuite le développement grand public.

**Marché grand public :** Secondaire. Le revenu B2G finance le produit grand public — tenter l'inverse (construire une base grand public pour impressionner les institutions) ajoute 3 ans au calendrier.

**Ce qui est mis de côté :**
- Mesh Hors Ligne (WiFi Direct non fiable sur Android, retiré du projet pour de bonnes raisons)
- Backup Buddy (le prototype dépasse déjà ceci ; tout recommencer coûte 6 mois pour aucun gain)
- Vision africaine 10x (narrative de pitch uniquement, pas roadmap)

## Tensions Clés Résolues

| Tension | Résolution |
|---|---|
| App company vs. infrastructure company | Infrastructure company. Le relay est le moat. |
| Grand public vs. B2G d'abord | B2G d'abord. Grand public financé par le premier contrat institutionnel. |
| Relay comme centre de coût vs. point de monétisation | Le Relay-as-a-Service est le modèle de revenus naturel. |
| Juridiction algérienne (requise pour B2G) | Le relay doit quitter Render (US) pour un hébergement algérien immédiatement. |

## Variation 8 — Bundle Opérateur Téléphonique (B2B2C) *(ajoutée post-gate)*

**L'idée :** Intégrer MobiCloud dans les offres des opérateurs téléphoniques algériens (Djezzy, Ooredoo, Mobilis) sous forme de bundle. Exemple : un client paie 1 500 DZD/mois et obtient 4 000 DZD de crédit d'appel + 50 Go de données + stockage MobiCloud distribué.

**Ce qui est fort :**
- Distribution instantanée à des dizaines de millions d'abonnés sans budget marketing.
- Facturation en DZD déjà gérée par l'opérateur — zéro friction de paiement.
- Mobilis est filiale d'Algerie Telecom, qui gère le fonds de 11M$ cybersécurité/IA — alignement direct.
- Le modèle bundle est prouvé en Afrique (Spotify, YouTube, BeIN Sports déjà bundlés avec des forfaits opérateur).

**Contrainte technique fondamentale :**
Le stockage MobiCloud est distribué sur les téléphones des membres du groupe — un abonné seul sans cluster actif n'a pas de stockage utilisable. "50 Go de MobiCloud" ≠ "50 Go sur un serveur". Nécessite au minimum 3 téléphones en cluster actif.

Solutions possibles :
- **Pack famille/groupe** : vendre le bundle à 3+ abonnés simultanément (pack famille Djezzy). Le groupe forme automatiquement un cluster.
- **Framing feature, pas quota** : l'opérateur inclut "MobiCloud Premium — sauvegarde distribuée avec tes contacts" sans mentionner un quota de Go.

**Ce qui est risqué :**
- C'est du **B2B2C**, pas du B2C direct : négociation opérateur = cycles longs (6–18 mois), SLA, intégration technique, revenue share (l'opérateur prend 30–50 % typiquement).
- L'opérateur peut exiger l'exclusivité réseau.
- Le relay doit être sur infrastructure algérienne avant toute intégration opérateur (même prérequis que B2G).

**Verdict :** Canal B2C à très fort potentiel à moyen terme. À initier après le premier contrat institutionnel B2G (qui valide la fiabilité et finance la capacité à honorer un SLA opérateur). Premier contact logique : **Mobilis** via le lien Algerie Telecom / fonds cybersécurité.

---

## Updated Brief Notes

Le brief d'intake (`00-intake/brief.md`) décrit trois voies de monétisation. Après brainstorm et gate, la direction consolidée est :
- **Primaire :** RaaS (Relay-as-a-Service) + contrat de support pour institutions (B2G)
- **Secondaire :** Bundle opérateur téléphonique via Mobilis/Djezzy/Ooredoo (B2B2C) — après premier contrat B2G
- **Tertiaire :** Abonnement freemium consommateur direct

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun introduit par cette direction qui n'était pas déjà dans l'intake.

**Drapeaux Jaunes :**
- Le relay doit quitter l'infrastructure US avant toute conversation institutionnelle B2G — c'est un prérequis, pas une tâche future.
- Le positionnement « infrastructure company » nécessite une dynamique commerciale différente (cycles plus longs, relations, marchés publics) du profil de compétences actuel du fondateur technique solo.

## Sources
- Direction du fondateur (juin 2026) — direct
