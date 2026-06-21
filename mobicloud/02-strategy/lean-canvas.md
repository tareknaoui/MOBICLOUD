# Lean Canvas — MobiCloud

**Phase :** 4 — Stratégie
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (B2G fondé sur données réglementaires solides ; B2C fondé sur hypothèses)

---

## 1. Problème

| Problème | Alternative actuelle |
|---|---|
| Les institutions publiques algériennes (universités, hôpitaux, ministères) stockent leurs données sur des clouds étrangers (Google Drive, OneDrive) désormais illégaux selon la loi algérienne (Law 11-25 + ARPCE Décision 48) | CERIST (trop lent, pas mobile-natif) ; AYRADE (centralisé, coûteux) ; rien (exposition légale) |
| Les auto-hébergements souverains (Nextcloud) nécessitent un serveur et une équipe IT que la plupart des institutions n'ont pas | USB drives ; Nextcloud (nécessite sysadmin) |
| Les particuliers perdent leurs fichiers quand leur téléphone casse ou se fait voler, sans alternative abordable et facturée en DZD | WhatsApp Starred Messages ; USB drives ; Google Drive free tier (15 Go, carte internationale requise) |

---

## 2. Segments Clients

**Segment B2G (prioritaire — beachhead) :**
- DSI/RSSI dans les universités, hôpitaux et ministères algériens
- 600–700 institutions cibles ; Year 1–3 : 150–200 institutions prioritaires
- **Early adopters :** L'institution déjà sous pression de conformité et dont le DSI a eu un avertissement de sa direction générale suite à la Law 11-25

**Segment B2B2C (secondaire) :**
- Opérateurs téléphoniques algériens : Mobilis (premier contact), Djezzy, Ooredoo
- Mobilis est filiale d'Algerie Telecom → alignement avec le fonds cybersécurité/IA de 11M$

**Segment B2C direct (tertiaire) :**
- Étudiants et jeunes professionnels algériens, 20–28 ans, Android, budget limité
- **Early adopters :** Groupes de colocataires en résidence universitaire, équipes de projets PFE

---

## 3. Proposition de Valeur Unique

**Pour B2G :**
> *"MobiCloud est la seule solution de stockage mobile-natif qui garde vos données sur sol algérien — sans serveur à provisionner, sans abonnement étranger, sans risque légal."*

**Pour B2C :**
> *"Tes fichiers survivent si ton téléphone casse — sauvegardés sur les téléphones de tes contacts, chiffrés, jamais sur un serveur."*

**Pour opérateurs (B2B2C) :**
> *"Offrez à vos abonnés la première fonctionnalité de stockage souverain mobile — un argument de vente différenciant qu'aucun opérateur n'a encore."*

---

## 4. Solution

| Feature | Problème adressé |
|---|---|
| **Relay WebSocket hébergé en Algérie** (conforme ARPCE Décision 48) | Données sur sol algérien = conformité légale B2G sans infrastructure serveur propre |
| **App Android : chiffrement AES-256 (Android Keystore) + erasure coding RS(k,m) paramétrable** | Les données ne quittent jamais les appareils des utilisateurs ; résilience scalable : dans un cluster de N nœuds, tolère m pannes simultanées (m croît avec la taille du cluster) |
| **Topologie super-peer avec failover automatique (Bully)** | Haute disponibilité sans infrastructure dédiée ; cluster survit au départ d'un membre |

---

## 5. Canaux

| Canal | Segment | Coût | Priorité |
|---|---|---|---|
| Contrats directs gré à gré (sous seuil AO) via LinkedIn DSI | B2G | Quasi-nul | 1 |
| Partenariat AYRADE (accès à 10 000 institutions clientes) | B2G | Négociation | 2 |
| Forum Algérie Numérique / événements ANPT | B2G | Faible | 3 |
| Négociation bundle opérateur — Mobilis via Algerie Telecom | B2B2C | Cycle long | 4 |
| TikTok organique + groupes WhatsApp/Telegram | B2C | Nul | 5 |
| Fonds Algerie Telecom 11M$ (financement + légitimité) | Tous | Dossier à déposer | Transversal |

---

## 6. Sources de Revenus

| Stream | Modèle | Prix | Timing |
|---|---|---|---|
| **RaaS B2G** | Contrat annuel par institution (relay + app + support) | 500K–2M DZD/an [Estimation] | Dès relay migré |
| **Bundle opérateur** | Revenue share (~50/50) par abonné actif | À négocier avec opérateur | Après premier contrat B2G |
| **Abonnement B2C** | Freemium : gratuit (3 membres, 5 Go) → payant | 200–500 DZD/mois [Estimation] | En parallèle, faible priorité |

---

## 7. Structure de Coûts

| Coût | Type | Estimation |
|---|---|---|
| Relay server algérien (hébergement) | Variable mensuel | 30K–150K DZD/mois [Estimation] |
| Conformité ARPCE + ANPDP (légal, one-time) | Fixe unique | 200K–500K DZD [Estimation] |
| Développement et maintenance | Founder time (pas de salaire externe) | — |
| Support institutionnel (SLA) | Variable selon contrats | À facturer dans ACV |

**Break-even B2G :** 1–2 contrats institutionnels couvrent les coûts d'infrastructure. [Estimation]

---

## 8. Métriques Clés (AARRR)

| Métrique | Définition | Cible Year 1 |
|---|---|---|
| **Acquisition B2G** | Nombre de rendez-vous DSI tenus/mois | 2/mois |
| **Activation B2G** | % DSI ayant déployé un pilot après démo | >30% |
| **Rétention B2G** | Uptime cluster >99% sur 30 jours | >99% |
| **Rétention B2C** | Taux d'utilisation actif à J+30 | >40% [Hypothèse] |
| **Revenu** | ARR institutionnel | 1 contrat signé |
| **Référral B2G** | Nouvelles institutions via recommandation | 1 par institution cliente |

---

## 9. Avantage Concurrentiel (Unfair Advantage)

1. **Conformité légale structurelle** : relay hébergé sur sol algérien + ARPCE = avantage que Google/AWS ne peuvent pas reproduire sans construire un datacenter en Algérie (3–5 ans minimum). [Données, recherche concurrents]
2. **Facturation DZD** : élimine structurellement tous les concurrents étrangers (pas de carte internationale en Algérie). [Données, comportement paiement algérien]
3. **Prototype fonctionnel existant** : clusters 3 nœuds testés sur vrais appareils, super-peer failover validé — avance technique de 6–12 mois sur tout concurrent qui démarrerait.
4. **Validation académique IEEE** : le gap mobile-natif + intermittent-connectivity est documenté dans la littérature scientifique comme contribution originale. [Données, IEEE Xplore]
5. **Alignement fonds Algerie Telecom** : positionnement cybersécurité souveraine = éligibilité directe au fonds 11M$.

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun client réel. Toutes les métriques et projections de revenus sont des hypothèses non validées.
- Le relay est encore sur Render (US) — bloque toute vente B2G. C'est le prérequis absolu #1.

**Drapeaux Jaunes :**
- Lean Canvas B2C entièrement hypothétique — aucune donnée algérienne directe sur les habitudes de backup.
- Le bundle opérateur (B2B2C) est attractif mais nécessite une capacité SLA que le produit n'a pas encore démontrée à l'échelle.

## Sources
- Recherche Phase 3 : `01-discovery/market-analysis.md`, `01-discovery/competitor-landscape.md`, `01-discovery/target-audience.md`
