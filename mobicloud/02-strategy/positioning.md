# Positionnement — MobiCloud

**Phase :** 4 — Stratégie
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Élevée sur la différenciation légale ; Moyenne sur l'angle produit (non validé par clients)

---

*Cadre utilisé : April Dunford — "Obviously Awesome" (5 composantes, dans l'ordre)*

---

## 1. Alternatives Compétitives

*Que feraient les clients si MobiCloud n'existait pas ?*

**Pour les institutions B2G :**
| Alternative | Pourquoi elle est utilisée | Où elle échoue |
|---|---|---|
| Google Drive / OneDrive | Familier, gratuit ou peu cher, mobile-natif | **Illégal** pour données institutionnelles algériennes (Law 11-25 + ARPCE Décision 48) |
| CERIST (cloud national) | Conforme, étatique, fiable | Pas mobile-natif ; capacité limitée ; seulement 80+ entités connectées ; pas disponible pour toutes les institutions |
| AYRADE | Conforme, DZD, infrastructure algérienne | Centralisé (serveur requis côté client) ; coûteux ; pas d'app mobile pour le terrain |
| Nextcloud auto-hébergé | Conforme si hébergé localement ; open-source | Nécessite serveur + sysadmin + maintenance — la plupart des institutions n'ont pas cette capacité |
| Ne rien faire / USB | Pas de coût | Exposition légale croissante ; risque de perte de données ; pas de mobilité |

**Pour les consommateurs B2C :**
| Alternative | Pourquoi elle est utilisée | Où elle échoue |
|---|---|---|
| Google Drive (free 15 Go) | Gratuit dans les limites, familier | Se remplit vite ; tier payant nécessite carte internationale ; données hors Algérie |
| WhatsApp "Starred Messages" | Zéro effort, déjà installé | Backup accidentel ; disparaît si compte supprimé ; non structuré |
| Clé USB | Pas d'abonnement | Tombe en panne ; perte physique ; pas automatique |
| Hivenet | Le plus proche techniquement | Facturation EUR (inaccessible) ; serveurs EU (non conforme) ; failures documentées (upload silencieux, "file not found") |

---

## 2. Attributs Uniques

*Ce que MobiCloud a que les alternatives n'ont pas.*

| Attribut | Description factuelle | Alternative qui manque cet attribut |
|---|---|---|
| **Relay sur sol algérien** | Infrastructure WebSocket hébergée physiquement en Algérie, conforme ARPCE Décision 48 | Google, Microsoft, Hivenet, Cubbit |
| **Facturation DZD** | Pas de carte internationale requise ; paiement via circuits bancaires algériens | Tous les acteurs étrangers (Hivenet, Cubbit, Google, Dropbox) |
| **Stockage P2P sans serveur central** | Les données résident sur les téléphones des membres — le relay ne stocke que du trafic chiffré en transit | AYRADE, CERIST, Nextcloud (hébergement serveur requis) |
| **Android Keystore + RS(k,m) scalable** | Chiffrement AES-256 lié au matériel de l'appareil + erasure coding paramétrable : tolérance aux pannes croît avec la taille du cluster (RS(2,1) en démo 3 nœuds ; paramètres plus élevés dans les grands clusters) | Hivenet (failures documentées sur upload/download), alternatives basées sur simple réplication |
| **Topologie super-peer + failover Bully** | Haute disponibilité sans infrastructure dédiée — cluster survit au départ d'un nœud | Toutes les solutions centralisées |
| **Prototype fonctionnel validé** | Testé sur appareils réels (3 nœuds), failover démontré, contribution IEEE documentée | Aucun concurrent de la niche algérienne |

---

## 3. Valeur

*Ce que ces attributs permettent concrètement pour le client.*

| Attribut | → Valeur pour B2G | → Valeur pour B2C |
|---|---|---|
| Relay sur sol algérien | "Votre DSI peut répondre à l'audit avec un document ARPCE — sans reconstruire votre infrastructure IT" | "Vos données ne quittent jamais le territoire algérien" |
| Facturation DZD | "Contrat payable comme n'importe quel prestataire algérien — pas de bon de commande international" | "Abonnement payable sur Baridimob comme votre forfait téléphonique" |
| Stockage P2P sans serveur | "Déployez en quelques jours sur les téléphones existants de vos personnels — pas d'achat serveur" | "Tes fichiers survivent à ton téléphone parce qu'ils sont chez tes contacts" |
| AES-256 + RS(k,m) scalable | "Vos données sont chiffrées même en transit — même MobiCloud ne peut pas les lire" | "Tes amis stockent des fragments chiffrés qu'ils ne peuvent pas lire" |
| Failover automatique + tolérance scalable | "Dans un grand cluster, plusieurs membres peuvent être hors ligne simultanément sans perte de données — pas limité à 1 panne comme dans la démo à 3 nœuds" | "Même si plusieurs amis éteignent leur téléphone, tes fichiers restent récupérables" |

---

## 4. Marché Cible

*Qui se soucie le plus de cette valeur ?*

**Critère de sélection du segment primaire :**
Le client idéal est celui pour lequel la valeur de MobiCloud est **critique, pas juste pratique**. Pour les institutions B2G algériennes, ce n'est pas un avantage optionnel — c'est une obligation légale. La conformité à la Law 11-25 n'est pas une préférence.

**Segment primaire — Institutions algériennes sous pression de conformité :**
- DSI d'universités publiques, hôpitaux régionaux, ministères
- Déjà conscients de la Law 11-25 et du Décret 26-07 (cybersecurity units)
- Utilisent actuellement Google Drive ou OneDrive en sachant que c'est non-conforme
- Ne peuvent pas se permettre Nextcloud (pas de sysadmin) ni AYRADE (pas d'infrastructure)
- Ont un budget d'environ 500K–2M DZD/an pour résoudre ce problème

**Segment secondaire — Étudiants algériens ayant déjà perdu des fichiers :**
- Ont vécu l'expérience de perdre des données lors d'un écran cassé ou d'un vol de téléphone
- Ne peuvent pas payer Google One en euros
- Font confiance à leurs colocataires ou à leur groupe d'études (base pour le cluster social)

**Anti-persona :** Entreprises multinationales (besoin de certification SOC 2 / ISO 27001) ; développeurs (préfèrent IPFS/Storj) ; utilisateurs hors Algérie (autre réglementation, autre infrastructure).

---

## 5. Catégorie de Marché

*Le cadre qui rend la valeur de MobiCloud évidente instantanément.*

**Catégorie choisie : Nouvelle sous-catégorie — "Stockage Souverain Mobile-Natif"**

Pourquoi pas "cloud souverain" seul :
- "Cloud souverain" existe déjà (AYRADE, CERIST). MobiCloud n'est pas une alternative à AYRADE — c'est complémentaire. Se positionner contre eux attire une comparaison sur l'infrastructure serveur qu'on n'a pas.

Pourquoi pas "stockage P2P" seul :
- Trop technique. Les DSI ne cherchent pas du "P2P" — ils cherchent de la "conformité" et de la "mobilité".

La sous-catégorie "Stockage Souverain Mobile-Natif" combine :
- **Souverain** → le mot que le gouvernement algérien utilise ("l'Algérie choisit la maîtrise") — accroche immédiate auprès des DSI sous pression réglementaire
- **Mobile-natif** → la différence technique vs AYRADE/CERIST — pas de serveur, fonctionne sur les téléphones que le personnel possède déjà

**Pitch de positionnement complet :**
> *"MobiCloud est la première solution de stockage souverain mobile-natif pour les institutions algériennes : vos données restent sur sol algérien, sur les appareils de vos membres, sans serveur à gérer. Pour les institutions qui ne peuvent plus se permettre d'utiliser Google Drive, et qui ne peuvent pas se payer AYRADE."*

---

## Résumé de Positionnement (format condensé)

| Composante | MobiCloud |
|---|---|
| Pour | Les institutions algériennes (DSI/RSSI) + étudiants sans cloud abordable |
| Qui ont besoin de | Stockage mobile-natif conforme à la loi algérienne, sans serveur |
| MobiCloud est | La première solution de stockage souverain mobile-natif pour l'Algérie |
| Contrairement à | Google Drive (illégal), Nextcloud (nécessite serveur), AYRADE (centralisé, coûteux), Hivenet (EU, EUR) |
| MobiCloud permet | De stocker des fichiers sur sol algérien via les téléphones Android existants, sans infrastructure serveur, avec une conformité ARPCE documentée |

---

## Drapeaux

**Drapeaux Rouges :**
- Ce positionnement repose sur l'hypothèse que les DSI algériens ont une urgence de conformité et agissent en conséquence. Non encore validé par des entretiens directs.

**Drapeaux Jaunes :**
- Le qualificateur "mobile-natif" peut nécessiter une pédagogie : certains DSI pourraient confondre "mobile-natif" avec "application mobile pour un cloud centralisé" (comme l'app Dropbox). Clarifier systématiquement : "les données sont stockées sur les téléphones de vos membres, pas sur un serveur MobiCloud."
- Le positionnement anti-Google est fort mais implique que MobiCloud doit aider l'institution à migrer hors de Google Drive — charge opérationnelle non estimée.

## Sources
- `01-discovery/competitor-landscape.md` — alternatives compétitives
- `01-discovery/target-audience.md` — segments et comportements
- `01-discovery/market-analysis.md` — cadre réglementaire
- `01-discovery/industry-trends.md` — framing souveraineté algérienne
