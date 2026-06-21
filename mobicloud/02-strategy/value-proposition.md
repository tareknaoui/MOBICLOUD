# Proposition de Valeur — MobiCloud

**Phase :** 4 — Stratégie
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (B2G fondé sur données réglementaires ; B2C fondé sur proxies Afrique du Sud/Nigeria)

---

## Canvas de Proposition de Valeur

### Profil Client — B2G (Karim, DSI d'université algérienne)

**Jobs-to-be-done :**
- *Fonctionnel :* Fournir un stockage de fichiers mobile aux personnels et étudiants, conforme à la loi algérienne, sans provisionner de serveur.
- *Social :* Être le DSI qui a résolu le problème de conformité avant que la direction générale ne reçoive un avertissement de l'ANPDP.
- *Émotionnel :* Ne plus avoir d'anxiété à chaque fois qu'un audit IT est annoncé.

**Douleurs :**
- Google Drive et OneDrive sont désormais illégaux pour les données institutionnelles (Law 11-25 + ARPCE Décision 48) — mais tout le monde les utilise encore. [Données, sources réglementaires Tier 1]
- Nextcloud est conforme mais nécessite un serveur qu'on n'a pas et un sysadmin qu'on ne peut pas embaucher.
- CERIST est lent et non mobile-natif — pas adapté pour le travail de terrain ou sur téléphone.
- AYRADE coûte cher et nécessite une infrastructure existante côté client.
- Le cycle d'appel d'offres BOMOP prend 12–24 mois pour approuver un nouveau prestataire.

**Gains attendus :**
- Solution conforme qui peut être déployée en quelques jours, pas en quelques mois.
- Aucun nouveau serveur à acheter ou à maintenir.
- Fonctionne sur les téléphones Android que le personnel possède déjà.
- Facturation en DZD (pas de devise étrangère, pas de bon de commande international complexe).
- Contrat sous le seuil gré à gré — pas besoin de passer par BOMOP.

---

### Carte de Valeur — MobiCloud (B2G)

**Produits & Services :**
- Relay WebSocket hébergé sur infrastructure algérienne (conforme ARPCE)
- Application Android (stockage distribué, chiffrement, erasure coding)
- Support technique et documentation de conformité (ANPDP, ARPCE)
- Contrat de service avec SLA

**Soulageurs de Douleur :**

| Douleur | Comment MobiCloud la soulage |
|---|---|
| Google Drive illégal pour données institutionnelles | Relay sur sol algérien = conformité ARPCE Décision 48 et Law 11-25 par design |
| Nextcloud nécessite serveur + sysadmin | Aucun serveur de stockage nécessaire — les données résident sur les appareils des utilisateurs |
| CERIST pas mobile-natif | App Android native — fonctionne sur smartphones du personnel |
| Cycle BOMOP trop long | Contrat gré à gré sous le seuil d'appel d'offres — pas de BOMOP |
| Coût infrastructure AYRADE | Pas d'infrastructure à provisionner côté client |

**Créateurs de Gains :**

| Gain attendu | Comment MobiCloud le crée |
|---|---|
| Déploiement rapide | Pilot en quelques jours : installer l'app, pointer vers le relay algérien, former 10 personnes |
| Réponse à la direction en cas d'audit | Documentation ANPDP fournie avec le contrat |
| Zéro dépendance à un datacenter étranger | Par architecture : données stockées sur téléphones Algériens, relay sur sol Algérien |
| Budget maîtrisé | Contrat annuel fixe en DZD, sans coût variable sur consommation cloud |

**Fit :** Fort sur le segment institutionnel B2G. Les 3 douleurs principales (illégalité Google Drive, complexité Nextcloud, lenteur CERIST) sont directement adressées par l'architecture même du produit. [Opinion, à valider avec entretiens DSI]

---

### Profil Client — B2C (Yasmine, étudiante, Alger)

**Jobs-to-be-done :**
- *Fonctionnel :* Ne pas perdre 3 ans de photos, de notes de cours et de travaux universitaires si son téléphone casse ou se fait voler.
- *Social :* Être celle dans le groupe qui a "configuré quelque chose d'intelligent" pour la sauvegarde.
- *Émotionnel :* Dormir tranquille en sachant que ses fichiers survivront à son téléphone.

**Douleurs :**
- Le free tier Google Drive (15 Go) se remplit vite — le tier payant nécessite une carte internationale et coûte l'équivalent d'un forfait mensuel. [Données, prix Afrique]
- WhatsApp Starred Messages et "Keep in Chat" sont des backups accidentels fragiles — disparaissent si le compte est supprimé.
- Les clés USB chinoises tombent en panne après 6 mois.
- La connexion réseau est variable — la synchronisation cloud échoue souvent au moment critique.

**Gains attendus :**
- Sauvegarde automatique sans action manuelle.
- Gratuit ou très peu cher (< 500 DZD/mois).
- Privé — pas de société étrangère qui lit ses fichiers.
- Fonctionne même si elle change de réseau (4G → WiFi).

---

### Carte de Valeur — MobiCloud (B2C)

**Soulageurs de Douleur :**

| Douleur | Comment MobiCloud la soulage |
|---|---|
| Google Drive trop cher / carte internationale | 200–300 DZD/mois, payable en DZD |
| Fichiers perdus si téléphone casse | Erasure coding RS(k,m) paramétrable : dans la démo à 3 nœuds, RS(2,1) tolère 1 panne ; dans un cluster de 50+ membres, les paramètres scalent pour tolérer plusieurs pannes simultanées |
| WhatsApp backup fragile | Sauvegarde automatique en arrière-plan, pas liée à une application de messagerie |
| Clés USB qui tombent en panne | Pas de hardware supplémentaire — les téléphones des membres du groupe sont le stockage |

**Créateurs de Gains :**

| Gain attendu | Comment MobiCloud le crée |
|---|---|
| Paix d'esprit | Les fichiers existent en 3 copies sur 3 téléphones différents — pas un seul point de défaillance |
| Confidentialité | Chiffrement AES-256 via Android Keystore — même les membres du groupe voient des fragments chiffrés illisibles |
| Gratuité pour groupes restreints | Tier gratuit jusqu'à 3 membres / 5 Go |
| Pas d'abonnement étranger | Produit local, facturation DZD |

**Fit :** Modéré. La douleur principale (perte de fichiers) est réelle et documentée. Mais le gain critique (paix d'esprit via fiabilité) dépend entièrement de la stabilité du cluster en conditions réelles — non validée. [Opinion, risque élevé sur rétention B2C]

---

## Proposition de Valeur — Une Phrase

**B2G :**
> *"MobiCloud aide les institutions publiques algériennes à stocker leurs fichiers de manière conforme à la loi, en distribuant les données sur les téléphones Android de leurs membres via un relay hébergé en Algérie — sans serveur à gérer, sans cloud étranger, sans risque légal."*

**B2C :**
> *"MobiCloud aide les étudiants algériens à ne plus perdre leurs fichiers en les sauvegardant automatiquement sur les téléphones de leurs contacts, chiffrés et reconstituables même si un téléphone disparaît."*

---

## Points de Preuve et Signaux de Crédibilité

| Signal | Auprès de qui | Disponibilité |
|---|---|---|
| Prototype fonctionnel testé sur vrais appareils (clusters 3 nœuds, failover Bully) | DSI technique, opérateurs, jury académique | Disponible maintenant |
| Validation IEEE du gap mobile-natif + intermittent-connectivity | DSI, investisseurs, jury | Publiée |
| Law 11-25 (juillet 2025) + ARPCE Décision 48 (2017) | DSI, direction générale d'institution | Textes officiels publics |
| Relay sur sol algérien (une fois migré) | DSI, RSSI, ANPDP | À construire — prérequis absolu |
| Premier pilot institutionnel signé | Opérateurs, autres institutions, investisseurs | À obtenir — objectif Year 1 |

---

## Drapeaux

**Drapeaux Rouges :**
- Le fit B2C est non validé. Aucune donnée réelle sur la rétention en conditions d'utilisation normale hors laboratoire.

**Drapeaux Jaunes :**
- La proposition "aucun serveur à gérer" s'adresse à des DSI qui comprennent la distinction technique entre relay (routage) et serveur de stockage. Il faudra pédagogiser cette nuance dans le pitch.
- Le fit B2C dépend du cluster étant stable — le kill criterion identifié par le fondateur est exactement cette fragilité.

## Sources
- `01-discovery/target-audience.md` — personas et pain hierarchy
- `01-discovery/competitor-landscape.md` — alternatives compétitives
- `01-discovery/market-analysis.md` — données réglementaires
