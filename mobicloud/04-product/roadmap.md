# Roadmap Produit — MobiCloud

**Phase :** 6 — Produit
**Projet :** mobicloud
**Date :** 2026-06-21 (mise à jour 2026-07-11 — réaligné sur le pivot GTM B2C du 2026-06-22)
**Confiance :** Moyenne (V0 validé sur prototype réel ; V1 fondé sur la logique du pivot B2C, non encore validé par des utilisateurs réels)

---

## Principe directeur

**Le prototype existant n'est pas la V1 — c'est la V0.**

La V0 prouve que l'architecture fonctionne. La V1 est ce qui doit fonctionner pour qu'un utilisateur B2C reste après son premier cluster, sans intervention du fondateur.

**Ce document remplace l'ancienne séquence "B2G d'abord".** Depuis le pivot du 2026-06-22 (`07-execution/b2c-attaque.md`), le B2G est mis en dormance et le B2C devient le fer de lance — lançable seul, sans avocat, sans fonds, sans cycle DSI de 6-18 mois. L'opérateur avance en parallèle, mais sur un prérequis d'infrastructure indépendant (Move 0), pas derrière un contrat B2G.

---

## Vue d'ensemble des versions

```
V0 — Prototype (EXISTANT)
    ↓ stabilité cluster + re-réplication + mécanique d'invitation
V1 — B2C Beta Ready (MAINTENANT — semaines 1-8)
    ↓ traction + rétention J+30 prouvées
V1-bis — Move 0 Opérateur (EN PARALLÈLE, indépendant de V1)
    ↓ relay algérien + conformité ANPDP/ARPCE + cohorte de démo
V2 — B2G Pilot Ready (DORMANT — repris si opportunité institutionnelle concrète)
V3 — Scale Opérateur (déclenché par la traction V1, pas par un calendrier)
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
- Mécanique d'invitation intégrée (le produit force le besoin de 3+ membres, mais l'app ne facilite pas encore l'invitation)
- Onboarding utilisateur non-technique

---

## V1 — B2C Beta Ready (Maintenant, semaines 1-8)

**Objectif :** Un groupe de 3-6 amis/colocs installe l'app ensemble, forme un cluster fonctionnel, et reste actif à J+30 — sans aide du fondateur au-delà de l'installation initiale.

**Critère de succès (métriques `07-execution/b2c-attaque.md` §6) :** Activation >60% sous 48h, K-factor >1, rétention J+30 >40%, stabilité cluster J+30 >70%.

### Features Must (bloquantes pour la beta)

| Feature | Pourquoi bloquante | Complexité estimée |
|---|---|---|
| ~~Re-réplication après perte permanente d'un nœud~~ **FAIT (2026-07-11)** | Le plumbing (plans signés, gossip, circuit-breaker) existait déjà (Story 7.3) mais était inatteignable en prod : 1 seul hôte par fragment + seuil=1 → jamais de donneur au moment où il aurait fallu réparer. Fix, calibré par coût : duplication brute (2 hôtes/fragment) **seulement quand n≤1** (RS(2,1), petits clusters cold-start) ; dès que `dynamicN` relève la parité à 2+, aucune duplication n'est payée — l'erasure coding fournit déjà la tolérance, moins cher en octets et en bande passante relay. Reste à valider : test réel de panne permanente sur device (pas juste unitaire) | Fait, à valider terrain |
| **Cluster stable à 4-6 membres** | La résilience réelle commence à 4+ (un cluster de 3 est fragile — b2c-attaque §6) ; MAX_CLUSTER_SIZE=3 est un paramètre, pas une limite archi | Moyenne |
| **Mécanique d'invitation intégrée (lien WhatsApp/Telegram vers le cluster)** | C'est le moteur de la stratégie : la viralité forcée ne s'enclenche que si inviter est trivial. Sans ce flux, le K-factor reste à 0 | Moyenne |
| **Onboarding en 3 étapes max** | Le produit doit s'expliquer seul à un groupe d'amis, pas à un DSI qui forme ses équipes | Moyenne |
| **Instrumentation des 4 métriques clés** | Sans données sur activation/K-factor/rétention/stabilité, impossible de savoir si ça marche — condition de tout le reste (§6 b2c-attaque) | Faible |

### Features Should

| Feature | Valeur |
|---|---|
| Vue "mes fichiers" avec statut de distribution (combien de copies, où) | Rassurer l'utilisateur sur l'état de ses données |
| Mode économie de données (ne synchroniser qu'en WiFi) | Réduire la consommation data — sensible pour un public étudiant |
| Notification de dégradation cluster (membre parti, tolérance réduite) | Pousser à inviter un remplaçant avant que la perte devienne critique |

### Features Won't (hors scope V1)

- **Monétisation / paiement DZD** — explicitement différé (b2c-attaque §5 : "ne monétise pas avant d'avoir prouvé la rétention")
- **Migration du relay vers l'Algérie** — pas un prérequis pour une beta B2C directe entre amis ; c'est un prérequis opérateur/B2G (voir V1-bis)
- Dashboard admin, document de conformité auto-généré, SSO institutionnel (spécifique B2G, dormant)
- Support iOS, web app

---

## V1-bis — Move 0 Opérateur (En parallèle, indépendant de V1)

**Objectif :** Réunir les munitions nécessaires pour décrocher un premier rendez-vous Mobilis (`07-execution/operateur-attaque.md`), sans dépendre de l'avancement B2C au-delà d'une cohorte de démo.

**Ne bloque pas V1 et n'est pas bloqué par V1** — c'est un chantier distinct (infrastructure + conformité), pas un chantier produit.

### Features Must

| Feature | Pourquoi bloquante | Complexité estimée |
|---|---|---|
| **Relay migré sur infrastructure algérienne agréée** | Prérequis dur pour tout opérateur et pour un futur B2G — aucun ne route via un serveur US | Infrastructure, pas code |
| **Dossier conformité ANPDP/ARPCE** | Obligatoire avant toute discussion opérateur sérieuse (`07-execution/conformite-arpce-anpdp.md`) | Faible (déjà rédigé, à finaliser) |
| **Cohorte de démo vivante (20-50 clusters)** | Munition commerciale — "voici N clusters actifs" vaut plus qu'une slide (operateur-attaque §3) | Réutilise la traction V1 |

### Features Should
- Relay HA avec store partagé (Redis ou équivalent) — anticipe la V3, évite de répéter le split-brain de mai 2026 avant même le premier pilote.

---

## V2 — B2G Pilot Ready (DORMANT)

**Statut :** Mis de côté depuis le pivot du 2026-06-22. Repris seulement si une opportunité institutionnelle concrète se présente (ex. AYRADE aboutit, DSI champion identifié) — pas de travail actif dessus tant que ce déclencheur n'existe pas.

**Contenu conservé pour référence (ex-V1 de ce document) :**

| Feature | Pourquoi bloquante | Complexité estimée |
|---|---|---|
| Cluster jusqu'à 50 membres | Nécessite ajustements RS(k,m) et tests de charge au-delà de ce que V1 couvre | Moyenne |
| Tableau de bord admin DSI (membres actifs, uptime, quota) | Le DSI a besoin de visibilité sans accéder aux appareils individuels | Moyenne |
| Document de conformité généré automatiquement | Produire le document ARPCE/ANPDP manuellement à chaque client ne scale pas | Faible |
| Invitation par lien ou QR code | Remplacement de la configuration manuelle — recoupe en partie la mécanique V1 | Faible (probablement déjà couvert par V1) |

---

## V3 — Scale Opérateur (Déclenché par la traction, pas par un calendrier)

**Objectif :** L'infrastructure relay peut gérer des milliers de clusters simultanément, avec une API d'intégration que Mobilis peut appeler pour activer/désactiver des clusters d'abonnés.

**Déclencheur (b2c-attaque §7) :** ~quelques milliers d'utilisateurs actifs + rétention J+30 stable >40%. Pas de date fixe — la traction V1 décide du timing.

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
- La re-réplication (V1) était le changement le plus risqué techniquement — implémentée le 2026-07-11 (réplique secondaire best-effort + seuil=2). Testée unitairement (17 cas) mais **pas encore validée par un test de panne permanente réel sur device** — à faire avant tout pilote payant.
- La mécanique d'invitation (V1) est un nouveau risque introduit par le pivot : toute la stratégie de croissance repose sur un K-factor >1. Si inviter reste compliqué dans l'app, la viralité forcée ne s'enclenche jamais et le cold-start reste bloqué.
- Le relay scalé horizontalement (V3) nécessite un store partagé entre instances (Redis ou équivalent) — le fondateur a déjà observé le split-brain sur 2 instances sans store partagé. Ne pas répéter l'incident.

**Drapeaux Jaunes :**
- Ne pas migrer le relay vers l'Algérie en urgence pour le B2C — ce n'est pas un prérequis pour une beta entre amis. Le réserver au chantier V1-bis (opérateur/B2G futur) pour ne pas retarder V1 sur un sujet qui ne le bloque pas.
- La monétisation B2C dépend d'une solution de paiement DZD fonctionnelle — CCP API / Baridimob API — non validée, et de toute façon différée tant que la rétention n'est pas prouvée.

## Sources
- `07-execution/b2c-attaque.md` — logique du pivot, métriques, plan des 100 premiers utilisateurs
- `07-execution/operateur-attaque.md` — Move 0, cohorte de démo, chemin d'entrée Mobilis
- `02-strategy/contributor-incentives.md`, `02-strategy/operator-model.md` — mécanismes de récompense référencés par V1/V1-bis
- `00-intake/brief.md` — état du prototype, gaps connus
- Historique projet MobiCloud (incident split-brain relay, mai 2026)
