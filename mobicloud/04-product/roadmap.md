# Roadmap Produit — MobiCloud

**Phase :** 6 — Produit
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (V0 validé sur prototype réel ; V1+ fondé sur besoins B2G hypothétiques non confirmés par clients)

---

## Principe directeur

**Le prototype existant n'est pas la V1 — c'est la V0.**

La V0 prouve que l'architecture fonctionne. La V1 est ce qui doit fonctionner pour qu'un DSI le déploie sur son institution sans intervention du fondateur. Ce sont deux produits différents.

---

## Vue d'ensemble des versions

```
V0 — Prototype (EXISTANT)
    ↓ relay algérien + re-replication + onboarding
V1 — Pilot B2G Ready (Mois 1–6)
    ↓ quota + dashboard + freemium
V2 — Lancement B2C (Mois 6–12)
    ↓ API opérateur + multi-tenant + SLA enterprise
V3 — Scale Opérateur (Mois 12–24)
```

---

## V0 — Prototype (Existant, validé)

**Statut :** Livré. Testé sur appareils réels.

**Ce qui fonctionne :**
- Cluster de 3 téléphones Android
- Chiffrement AES-256 via Android Keystore
- Erasure coding RS(2,1) — 3 fragments, 2 suffisent pour reconstituer, 1 panne tolérée
- Distribution des fragments entre les membres du cluster
- Topologie super-peer avec failover automatique (algorithme Bully)
- Relay WebSocket pour la traversée NAT (actuellement sur Render, US)
- Upload et reconstitution de fichiers testés sur vrais appareils

**Ce qui ne fonctionne pas encore :**
- Cluster au-delà de 3 nœuds (MAX_CLUSTER_SIZE = 3 par paramètre, pas par limite architecturale)
- Re-réplication après perte permanente d'un nœud (la tolérance actuelle est temporaire — nœud hors ligne, pas disparu définitivement)
- Relay sur infrastructure algérienne (bloquant pour B2G)
- Onboarding utilisateur non-technique

---

## V1 — Pilot B2G Ready (Mois 1–6)

**Objectif :** Un DSI peut déployer MobiCloud sur 20–50 membres de son institution en moins d'une journée, sans assistance du fondateur, avec un document de conformité prêt pour l'audit.

**Critère de succès :** 1 institution déploie le pilot, 40% des utilisateurs actifs à J+30, 0 perte de données sur la période.

### Features Must (bloquantes pour le pilot)

| Feature | Pourquoi bloquante | Complexité estimée |
|---|---|---|
| **Relay migré sur infrastructure algérienne** | Prérequis légal absolu — aucun DSI ne signe sans ça | Infrastructure, pas code |
| **Cluster jusqu'à 50 membres** | MAX_CLUSTER_SIZE = 3 est un paramètre — le changer entraîne des ajustements RS(k,m) et tests de charge | Moyenne |
| **Re-réplication après perte permanente d'un nœud** | Sans ça, la perte d'un membre = dégradation silencieuse de la tolérance aux pannes | Élevée |
| **Onboarding en 3 étapes max** | Le DSI ne peut pas former individuellement 200 personnes — l'app doit s'expliquer seule | Moyenne |
| **Document de conformité généré automatiquement** | Le RSSI a besoin d'un document ARPCE/ANPDP pour l'audit — le produire manuellement à chaque client n'est pas scalable | Faible |

### Features Should (importantes mais non bloquantes pour le démarrage du pilot)

| Feature | Valeur |
|---|---|
| Tableau de bord admin DSI (membres actifs, uptime cluster, quota utilisé) | Le DSI a besoin de visibilité sans accéder aux appareils individuels |
| Notifications push (nœud hors ligne, cluster dégradé) | Alerter l'admin sans qu'il surveille manuellement |
| Invitation par lien ou QR code | Remplacement de la configuration manuelle actuelle |
| Rapport d'uptime automatique (30 jours glissants) | Preuve de SLA pour renouvellement de contrat |

### Features Won't (hors scope V1)

- Open-source publication de l'app
- API d'intégration opérateur
- Tier payant B2C et gestion des abonnements
- Support iOS
- Web app

---

## V2 — Lancement B2C (Mois 6–12)

**Objectif :** Des étudiants et jeunes professionnels peuvent créer un cluster entre amis, sauvegarder leurs fichiers automatiquement, et récupérer leurs données sur un nouveau téléphone — sans connaître le fonctionnement technique.

**Critère de succès :** 500 utilisateurs actifs à J+30 sur 50 clusters distincts, churn mensuel < 30%, 0 perte de données documentée.

### Features Must

| Feature | Pourquoi |
|---|---|
| **Quota par cluster (tier freemium / payant)** | Monétisation B2C — sans quota, pas de modèle économique consumer |
| **Paiement DZD (CCP / Baridimob / recharge opérateur)** | Sans ça, la cible B2C ne peut pas payer |
| **Backup automatique en arrière-plan** | L'utilisateur ne doit pas penser à "sauvegarder" — ça doit se faire seul |
| **Récupération sur nouveau téléphone** | Scénario de perte/vol — c'est la promesse centrale du produit |
| **Interface utilisateur grand public (non-technique)** | Yasmine n'a pas besoin de savoir ce qu'est RS(k,m) |

### Features Should

| Feature | Valeur |
|---|---|
| Vue "mes fichiers" avec statut de distribution (combien de copies, où) | Rassurer l'utilisateur sur l'état de ses données |
| Tableau de bord contributeur (espace donné vs espace utilisé) | Équité perçue dans le groupe |
| Mode économie de données (ne synchroniser qu'en WiFi) | Réduire la consommation data pour les utilisateurs limités |
| Partage de cluster par lien WhatsApp/Telegram | Canal d'acquisition organique |

---

## V3 — Scale Opérateur (Mois 12–24)

**Objectif :** L'infrastructure relay peut gérer des milliers de clusters simultanément, avec une API d'intégration que Mobilis peut appeler pour activer/désactiver des clusters d'abonnés.

**Critère de succès :** Pilot bundle Mobilis actif sur 1 wilaya, SLA 99.9% tenu sur 30 jours, revenue share reçu le premier mois.

### Features Must

| Feature | Pourquoi |
|---|---|
| **API relay multi-tenant** | Mobilis doit pouvoir créer/supprimer des clusters via API sans intervention manuelle |
| **Relay scalé horizontalement** (plusieurs instances + store partagé) | Évite le split-brain documenté en mai 2026 — prérequis SLA enterprise |
| **Tableau de bord opérateur** (clusters actifs, utilisateurs, volumétrie relay) | L'opérateur doit pouvoir superviser son parc abonnés sans MobiCloud |
| **Facturation automatisée par cluster actif** | Revenue share calculé et facturé automatiquement |
| **Documentation d'intégration opérateur** | L'équipe technique Mobilis doit pouvoir intégrer sans assistance continue |

---

## Drapeaux

**Drapeaux Rouges :**
- La re-réplication (V1) est le changement le plus risqué techniquement — c'est le seul cas où une implémentation incorrecte peut entraîner une perte de données silencieuse. À traiter en priorité et à tester exhaustivement avant tout pilot.
- Le relay scalé horizontalement (V3) nécessite un store partagé entre instances (Redis ou équivalent) — le fondateur a déjà observé le split-brain sur 2 instances sans store partagé. Ne pas répéter l'incident.

**Drapeaux Jaunes :**
- Le timeline V1 (Mois 1–6) est optimiste pour un fondateur solo. La migration relay + re-replication + onboarding est 3-4 mois de travail dense. Identifier ce qui peut être différé sans bloquer le pilot.
- La V2 dépend d'une solution de paiement DZD fonctionnelle — CCP API / Baridimob API — dont la disponibilité et la documentation ne sont pas confirmées.

## Sources
- `00-intake/brief.md` — état du prototype, gaps connus
- `02-strategy/go-to-market.md` — jalons commerciaux qui contraignent le timing produit
- `02-strategy/value-proposition.md` — jobs-to-be-done par persona
- Historique projet MobiCloud (incident split-brain relay, mai 2026)
