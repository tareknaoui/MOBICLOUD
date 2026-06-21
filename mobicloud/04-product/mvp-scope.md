# Scope MVP — MobiCloud

**Phase :** 6 — Produit
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (fondé sur besoins B2G inférés — à valider avec entretiens DSI, Expérience #1)

---

## Définition du MVP

**Le MVP de MobiCloud = V1 Pilot B2G Ready.**

Pourquoi B2G, pas B2C :
- Un pilot B2G génère du revenu (contrat gré à gré DZD) et une référence client — deux actifs qui débloquent la suite.
- Un pilot B2C sans revenu consomme du temps fondateur sans actif durable si le cluster s'avère instable.
- La stabilité cluster (le kill criterion du fondateur) se valide mieux sur un déploiement institutionnel contrôlé (50 utilisateurs internes) que sur un lancement grand public.

**Question centrale du MVP :** *Est-ce qu'un DSI peut déployer MobiCloud sur son institution, sans assistance technique continue, et maintenir un uptime >99% sur 60 jours ?*

---

## MoSCoW — Priorisation des Features

### MUST — Bloquant pour le premier pilot

| # | Feature | Justification |
|---|---|---|
| M1 | Relay WebSocket hébergé sur infrastructure algérienne | Prérequis légal ARPCE Décision 48 — sans ça, aucun DSI ne peut signer |
| M2 | Cluster jusqu'à 50 membres avec RS(k,m) adapté | Un pilot institutionnel avec 3 utilisateurs n'a pas de valeur démonstrative |
| M3 | Re-réplication après perte permanente d'un nœud | Sans ça, la tolérance aux pannes se dégrade silencieusement — inacceptable pour un SLA |
| M4 | Onboarding en 3 étapes max (sans documentation externe) | Le DSI ne peut pas former 200 personnes individuellement |
| M5 | Document de conformité généré automatiquement | Le RSSI a besoin d'un document ARPCE/ANPDP pour répondre à son audit — sans ça, l'institution ne peut pas justifier le déploiement en interne |

### SHOULD — Important, inclure si le temps le permet avant le pilot

| # | Feature | Justification |
|---|---|---|
| S1 | Tableau de bord admin (membres actifs, uptime, quota) | Visibilité pour le DSI sans accès aux appareils individuels |
| S2 | Notifications push (nœud dégradé, cluster instable) | Alerter sans surveillance manuelle |
| S3 | Invitation par QR code ou lien profond | Remplace la configuration manuelle actuelle |
| S4 | Rapport d'uptime auto (30 jours glissants) | Preuve SLA pour le renouvellement de contrat |

### COULD — Valeur réelle, mais différable post-pilot

| # | Feature | Pourquoi différable |
|---|---|---|
| C1 | Backup automatique en arrière-plan | Priorité B2C — le pilot B2G peut démarrer en upload manuel |
| C2 | Vue "mes fichiers" avec statut de distribution | Utile mais non bloquant pour la validation technique |
| C3 | Mode économie de données (WiFi uniquement) | Le personnel institutionnel est souvent en WiFi |
| C4 | Compression avant chiffrement | Optimisation de performance, pas une feature fonctionnelle |
| C5 | Support multi-langue (arabe) | Utile mais non bloquant pour un pilot francophone |

### WON'T — Hors scope MVP (décision explicite)

| # | Feature | Pourquoi hors scope |
|---|---|---|
| W1 | Cluster au-delà de 50 membres | Non testé — à valider progressivement post-pilot, pas avant |
| W2 | API intégration opérateur | V3 — nécessite un SLA enterprise que le MVP n'a pas encore prouvé |
| W3 | Tier payant B2C et gestion des abonnements | V2 — le MVP valide la rétention avant de monétiser |
| W4 | Support iOS | Hors scope architectural (Android Keystore spécifique) |
| W5 | Web app | Hors scope — le produit est mobile-natif par design |
| W6 | Open-source publication | Différé — à décider après le premier pilot signé |
| W7 | Mécanisme d'incentive / réciprocité | Retiré du scope général (voir perspectives rapport) |

---

## Critères de Sortie du MVP

*Le MVP est terminé quand ces 5 conditions sont réunies simultanément.*

| Critère | Mesure | Seuil |
|---|---|---|
| Relay algérien opérationnel | Certificat hébergement + test fonctionnel inter-appareils | 100% |
| Cluster stable à N membres (N ≥ 20) | Uptime continu sur 7 jours, 0 perte de données | 100% |
| Re-réplication fonctionnelle | Test : retirer un nœud définitivement → vérifier reconstruction automatique sur 24h | 100% |
| Onboarding autonome | Test utilisateur : 5 non-techniciens configurent un cluster sans aide — temps < 10 min | 80% succès |
| Document de conformité généré | Export PDF depuis l'app avec les données ARPCE requises | 100% |

---

## Ce que le MVP ne prouve PAS

*Être honnête sur les limites de ce que le pilot valide.*

| Hypothèse non validée par le MVP | Ce qu'il faudra pour la valider |
|---|---|
| La stability du cluster tient à 200+ membres | Tests de charge + pilot plus grand (V1.5 ou V2) |
| Le RS(k,m) scale correctement à des paramètres élevés | Tests exhaustifs avec grands clusters avant tout déploiement large |
| Les DSI renouvellent le contrat après 12 mois | Mesurer le NPS institutionnel à 6 mois et 12 mois |
| Le modèle B2C tient en conditions réelles | Pilot B2C dédié avec métriques de rétention (V2) |
| Le relay tient à l'échelle (100+ clusters simultanés) | Tests de charge relay avant négociation opérateur |

---

## Risques Techniques MVP

| Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|
| Re-réplication entraîne une corruption silencieuse de données | Faible (mais non nul) | Critique | Tests unitaires + tests d'intégration exhaustifs + checksum avant/après reconstruction |
| Cluster instable à 20+ membres (latence, désynchronisation) | Moyenne | Élevé | Tests de charge progressifs : 5 → 10 → 20 → 50 membres avant le pilot |
| Relay algérien : latence plus élevée qu'avec Render US | Faible | Moyen | Benchmark comparatif — si latence > 500ms, optimiser avant pilot |
| Onboarding incompris par des non-techniciens | Élevé | Moyen | Test utilisateur avec 5 personnes non-techniques avant pilot — itérer sur l'UX |
| Document de conformité non accepté par le RSSI | Moyenne | Élevé | Faire relire le template par un avocat spécialisé droit numérique algérien avant le premier pitch |

---

## Drapeaux

**Drapeaux Rouges :**
- La re-réplication (M3) est le seul changement qui, si mal implémenté, peut entraîner une perte de données réelle. C'est le risque technique le plus critique du MVP. Ne pas l'expédier.

**Drapeaux Jaunes :**
- Le document de conformité automatique (M5) nécessite de savoir exactement ce que le RSSI d'une université algérienne doit fournir à l'ANPDP. Sans cette information, le document généré peut être inutile. Obtenir un template auprès d'un avocat droit numérique algérien avant d'implémenter.
- L'onboarding (M4) est la feature la plus difficile à estimer — la complexité de l'UX dépend entièrement de tests avec de vrais utilisateurs non-techniciens.

## Sources
- `04-product/roadmap.md` — versions et features par phase
- `02-strategy/value-proposition.md` — jobs-to-be-done par persona
- `01-discovery/target-audience.md` — profil Karim DSI (critères de décision)
- `00-intake/brief.md` — état du prototype et gaps connus
