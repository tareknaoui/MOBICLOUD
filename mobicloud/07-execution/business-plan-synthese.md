# MobiCloud — Synthèse Business Plan

**Stockage distribué souverain pour l'Algérie**
Anis Naoui · naoui.tarekanis@gmail.com · +213 779 430 0903 · 2026

---

## 1. Le projet en une phrase

MobiCloud est une solution algérienne de **stockage mobile distribué** : les fichiers des utilisateurs sont sauvegardés, **chiffrés**, répartis sur les téléphones d'un groupe de confiance — et **ne quittent jamais le sol algérien**. Un prototype fonctionnel existe déjà et tourne sur de vrais appareils.

---

## 2. L'opportunité nationale

Dans le cadre de **l'agenda national de souveraineté numérique** et des réglementations en vigueur sur la localisation et la protection des données (encadrées par l'ARPCE et l'ANPDP), les données des Algériens doivent rester sur le territoire national. Les clouds étrangers (Google Drive, OneDrive) sont devenus un **risque légal** pour les institutions et n'ont aucune alternative locale crédible.

| Le problème | Conséquence |
|---|---|
| Clouds étrangers désormais non conformes | Institutions exposées juridiquement |
| Aucune solution souveraine mobile-native | Dépendance technologique étrangère |
| Pas de facturation en DZD | Exclusion des citoyens sans carte internationale |
| Perte de données quotidienne | Pas de sauvegarde abordable et locale |

**L'enjeu est stratégique :** souveraineté numérique, indépendance des données, infrastructure nationale.

---

## 3. La solution

- **Application Android** : sauvegarde chiffrée, répartie sur les téléphones d'un groupe de confiance (famille, proches).
- **Souveraineté réelle** : les données ne transitent jamais par un serveur étranger.
- **Confidentialité** : chiffrement de bout en bout — les données sont **illisibles, y compris par nous**.
- **Résilience** : si un téléphone casse ou se perd, les fichiers sont reconstruits depuis les autres appareils du groupe.

---

## 4. L'avantage concurrentiel (ce que les géants ne peuvent pas copier)

| Avantage | Pourquoi c'est défendable |
|---|---|
| **Souveraineté par conception** | Données en Algérie — Google devrait construire un datacenter local (3-5 ans) |
| **Facturation en DZD** | Exclut structurellement les concurrents étrangers (pas de carte internationale) |
| **Prototype fonctionnel** | Avance technique réelle de 6-12 mois sur tout nouvel entrant |
| **Coût de stockage = 0** | L'espace est fourni par les téléphones → marges imbattables |

---

## 5. Le marché

| Segment | Taille | Statut |
|---|---|---|
| Grand public via opérateur (prépayé) | ~20M abonnés Mobilis | Cible commerciale prioritaire |
| Institutions publiques | 600-700 sous obligation de conformité | Valeur nationale / souveraineté |
| Jeunes / étudiants Android | Population massive, sensible à la data | Adoption virale |

**Qui paie, au juste ?** Le **payeur commercial, c'est l'opérateur** (qui distribue et facture). Les **institutions publiques et la souveraineté** sont le bénéfice national et la perspective — pas le canal de vente immédiat.

---

## 6. Le modèle économique

**Voie commerciale principale : le partenariat opérateur (B2B2C).** MobiCloud est intégré comme une feature dans les forfaits de l'opérateur (Mobilis en priorité — filiale Algerie Telecom).

**Comment l'argent circule (à 2 parties, pas 3) :**
- L'abonné paie (ou la feature est bundlée) → l'opérateur et MobiCloud se partagent la valeur.
- **Le contributeur est l'abonné lui-même** : il prête de l'espace, il reçoit son backup. Pas de cash entre utilisateurs.

**Le mécanisme de récompense des contributeurs (innovation clé) :**
- **Socle :** réciprocité — pour être sauvegardé, on met à disposition de l'espace.
- **Booster :** les contributeurs les plus fiables reçoivent du **crédit data**, financé par l'opérateur. La data coûte ~40 DZD à l'opérateur mais en vaut ~250 perçus par l'abonné prépayé → forte motivation, **coût nul pour MobiCloud**.
- **Fiabilité garantie :** l'opérateur fournit quelques nœuds toujours en ligne (« nœuds-ancre ») qui assurent que les données restent **récupérables à tout moment**, même si des téléphones du groupe sont hors-ligne.

**Économie unitaire :** le stockage coûte 0 (les téléphones le fournissent), le relais ne fait que router → des **marges très élevées** (chiffrées en section 8.4).

**Le cycle de la valeur (qui reçoit quoi) :**

| Acteur | Ce qu'il reçoit |
|---|---|
| **L'abonné (tous)** | Son **backup souverain** — ses fichiers en sécurité, en Algérie |
| **L'abonné contributeur** | En plus : de la **data offerte** par l'opérateur, en échange du partage de son espace |
| **L'opérateur** | Sa part de revenu **+ la rétention** de l'abonné |
| **MobiCloud** | Sa part (~30 DZD/abonné actif en prépayé ; davantage avec les offres premium) |

> La valeur circule **localement** : l'abonné paie → l'opérateur et MobiCloud se partagent → l'opérateur **redonne de la data au citoyen** qui participe. Tout le monde gagne quelque chose, et tout reste dans l'écosystème algérien.

---

## 7. Projections financières (potentiel, partenariat Mobilis — prépayé)

| Scénario | Activation | Abonnés actifs | Revenu net MobiCloud/an |
|---|---|---|---|
| Conservateur | 0,5% | 100 000 | ~22M DZD (~79K €) |
| **Base** | **1%** | **200 000** | **~55-70M DZD (~200-250K €)** |
| Optimiste | 3% | 600 000 | ~289M DZD (~1,04M €) |

*Hypothèses de travail — non validées par un contrat réel. Revenu net par abonné retenu : ~25 DZD (conservateur), ~30 DZD (base), ~45 DZD (optimiste, avec montée en offres premium) — c'est ce qui explique, avec le taux d'activation, l'écart entre scénarios. Le facteur le plus déterminant reste l'activation, à confirmer en pilote. Taux : 1 EUR = 278 DZD.*

---

## 8. Structure de coûts & capital

### 8.1 Capital de démarrage (pour atteindre le premier revenu)

| Poste | Montant DZD | Type |
|---|---|---|
| Migration relais (Render → hébergeur algérien) | 50 000 – 150 000 | One-time |
| Conformité ARPCE (avocat + dossier) | 200 000 – 350 000 | One-time |
| Conformité ANPDP (enregistrement sous-traitant) | 50 000 – 100 000 | One-time |
| Compte développeur Google Play | ~6 300 | One-time |
| Nom de domaine + certificats | ~5 000 | One-time |
| Réserve de fonctionnement (6 mois × ~38K) | ~230 000 | Provisionné |
| Marge imprévus (~20%) | ~150 000 | — |
| **Total capital réaliste** | **~1 100 000 DZD (~4 000 €)** | |

→ Seuil d'entrée volontairement bas : le projet démarre avec **~4 000 €**.

### 8.2 Charges récurrentes au démarrage (mensuelles)

| Poste | Montant/mois | Nature |
|---|---|---|
| Relais hébergement (Icosnet VPS, sol algérien) | ~6 500 DZD (1 instance) → ~13 000 (HA) | Variable (par palier) — **prix réel vérifié** |
| Outillage IA (Claude Max 5×) | ~25 600 DZD ($100) | Fixe — **prix réel vérifié** |
| Domaine .dz / divers | ~quelques centaines | Fixe |
| Salaire fondateur (Year 1) | 0 DZD | Bootstrapping |
| **Burn mensuel** | **~32 000 – 39 000 DZD/mois** | |

*Hébergeur identifié : **Icosnet** (datacenters Alger & Oran) — données sur sol algérien (conforme ARPCE), **bande passante illimitée** (coût relais prévisible, pas de surfacturation egress), paiement en DZD via CIB/EDAHABIA. Prix VPS de 6 545 DZD/mois (4 Go) à 28 441 DZD/mois (12 Go) selon le palier d'usage.*

### 8.3 Évolution des coûts par palier (les coûts suivent le revenu)

| Palier | Utilisateurs | Relais/an | Équipe | Coûts totaux/an |
|---|---|---|---|---|
| **Pilot** | quelques centaines | ~150K | Fondateur seul | ~0,5M DZD |
| **Croissance** | milliers | ~0,7-1M | + 1-2 personnes | ~8-10M DZD |
| **Scale** | 100K – 1M+ | ~3,6-5M | 10-15 personnes | ~50-60M DZD |

→ Les **gros coûts** (équipe, relais Scale) n'arrivent qu'**après** que le revenu les couvre.

### 8.4 Le principe différenciant : le stockage coûte 0

Contrairement à un cloud classique qui paie chaque Go de datacenter, MobiCloud ne stocke **rien** côté serveur — l'espace est fourni par les téléphones, le relais ne fait que router. Résultat : **marges de 80-90%** et des coûts qui **suivent** le revenu au lieu de le précéder.

### 8.5 Financement (d'où vient le capital)

| Source | Rôle |
|---|---|
| Bootstrapping (fonds propres) | Démarrage, sans dilution |
| **Fonds cybersécurité/IA (Algerie Telecom)** | Accélération — sécurise 18-24 mois d'opération |
| Premier revenu (pilote opérateur) | Autofinancement dès le premier contrat |
| Label « Startup » / incubation | Appui non-dilutif (bureaux, réseau, crédibilité) |

---

## 9. État d'avancement

- **Prototype fonctionnel** : clusters multi-téléphones testés sur de vrais appareils, basculement automatique en cas de panne.
- **Architecture démontrée sur prototype** (chiffrement, répartition, basculement automatique). *(Reste à prouver : la fiabilité et la rétention en conditions réelles, à l'échelle — c'est l'objet du pilote.)*
- **Prérequis n°1 :** migration du serveur relais vers un hébergeur sur sol algérien (condition de conformité et de souveraineté).

---

## 10. Feuille de route

| Phase | Contenu |
|---|---|
| **1. Souveraineté** | Relais sur sol algérien + conformité ARPCE/ANPDP |
| **2. Pilote opérateur** | Pilote Mobilis : 1 wilaya, segment prépayé, via Pack Groupe, KPIs mesurés |
| **3. Échelle** | Déploiement bundle opérateur + extension grand public |

---

## 11. Risques & ce qu'on fait pour les réduire

*Un projet à ce stade comporte de vrais risques. Les nommer, c'est savoir les piloter.*

| Risque | Réalité | Mitigation |
|---|---|---|
| **Fiabilité du P2P mobile** (risque n°1) | Les téléphones s'éteignent ; la donnée doit rester récupérable au moment voulu. Non encore prouvé à l'échelle. | Erasure coding (tolère des pannes) + nœuds-ancre opérateur (plancher toujours en ligne) + cohorte de démo pour mesurer la rétention réelle. |
| **Aucun utilisateur réel à ce jour** | Toutes les projections sont des hypothèses. | Pilote 1 wilaya pour valider activation et rétention **avant** tout engagement lourd. |
| **Cycle opérateur long** (6-18 mois) | Mobilis est grand, lent, exigeant. | Approche par paliers (RDV → PoC → pilote) ; aucun gros engagement demandé d'emblée. |
| **Concurrence** | CERIST et AYRADE existent — mais centralisés et non mobile-natifs. | Différenciation claire : seul stockage **mobile-natif distribué** + facturation DZD. |
| **Fondateur solo** | Beaucoup de fronts en parallèle. | Outillage IA pour tenir le rôle d'une équipe ; recrutement progressif financé par le revenu. |

---

## 12. Ce que nous recherchons (l'appui du ministère)

| Type d'appui | Détail |
|---|---|
| **Facilitation** | Mise en relation avec les opérateurs (Mobilis / Algerie Telecom) ; accompagnement réglementaire (ARPCE, ANPDP) |
| **Financement** | Accès au fonds cybersécurité/IA — **~2-5M DZD** pour sécuriser 18-24 mois d'opération ; label « Startup » ; incubation |
| **Reconnaissance** | Alignement officiel avec l'agenda national de souveraineté numérique |

---

## 13. Pourquoi MobiCloud sert la stratégie nationale

- **Souveraineté des données** : les données algériennes restent en Algérie, chiffrées.
- **Indépendance technologique** : réduit la dépendance aux clouds étrangers.
- **Conformité** : met les institutions et les citoyens en règle avec les réglementations nationales sur la protection et la localisation des données.
- **Innovation & emploi** : produit algérien, équipe algérienne — **10-15 emplois qualifiés** à l'échelle (développement, support, commercial).

---

> **MobiCloud n'est pas une simple application de stockage. C'est une brique d'infrastructure souveraine : les données des Algériens, gardées en Algérie, chiffrées, facturées en dinars — quelque chose qu'aucun acteur étranger ne peut offrir.**

**Contact :** Anis Naoui · naoui.tarekanis@gmail.com · +213 779 430 0903
