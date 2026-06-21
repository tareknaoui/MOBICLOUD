# Plan de Validation — MobiCloud

**Phase :** 8 — Validation
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Élevée sur la structure du plan ; les résultats des expériences sont par définition inconnus.

---

## Principe

**Toutes les décisions stratégiques de ce dossier reposent sur des hypothèses non validées.**

Le plan de validation transforme ces hypothèses en expériences concrètes avec des critères de succès mesurables. Une hypothèse validée devient une certitude qu'on peut présenter à un client, un investisseur ou un jury. Une hypothèse invalidée déclenche un pivot ou un arrêt — avant d'avoir dépensé du temps et de l'argent sur la mauvaise direction.

---

## Matrice des Hypothèses — Classées par Risque

*Risque = (Probabilité d'être fausse) × (Impact si fausse)*

| # | Hypothèse | Risque | Validée par |
|---|---|---|---|
| H1 | Les DSI algériens ont une urgence de conformité réelle et sont prêts à agir | **CRITIQUE** | Expérience 1 |
| H2 | Le cluster reste stable avec 20+ membres sur 30 jours en conditions réelles | **CRITIQUE** | Expérience 3 |
| H3 | Les institutions peuvent payer 500K–2M DZD/an en gré à gré | **ÉLEVÉ** | Expérience 1 + 2 |
| H4 | Des non-techniciens peuvent rejoindre et utiliser le cluster en < 10 minutes | **ÉLEVÉ** | Expérience 4 |
| H5 | Le relay algérien a une latence acceptable pour l'usage quotidien | **MOYEN** | Expérience 5 |
| H6 | Les étudiants algériens paient pour un service de backup (200–300 DZD/mois) | **MOYEN** | Expérience 6 |
| H7 | RS(k,m) scale correctement au-delà de 3 nœuds sans perte de données | **MOYEN** | Expérience 3 |
| H8 | Mobilis considère MobiCloud comme une feature différenciante pour ses bundles | **FAIBLE** | Expérience 7 |

---

## Expérience 1 — Entretiens DSI (Demande B2G)

**Hypothèse testée :** H1, H3
**Priorité :** Maximale — c'est l'expérience qui valide ou invalide toute la stratégie B2G.
**Statut :** Différée depuis Phase 3.7 — à lancer en premier.

**Protocole détaillé :** voir `06-validation/experiment-01-dsi-interviews.md`

**Critère de succès :** 4 DSI sur 5 interviewés confirment que la conformité Law 11-25 est une urgence active pour eux, et qu'ils ont un budget pour la résoudre.

**Critère d'échec (pivot) :** Moins de 2 DSI sur 5 décrivent l'urgence de conformité spontanément (sans qu'on la mentionne en premier) → la narration B2G est construite sur une hypothèse fausse, réévaluer le positionnement.

**Timing :** Mois 1–2 (avant toute dépense commerciale)

---

## Expérience 2 — Pilot Institutionnel (Willingness to Pay)

**Hypothèse testée :** H3 — les institutions paient réellement, pas seulement en intention.
**Méthode :** Proposer un pilot payant (même à tarif réduit) dès le début — pas "gratuit puis payant".

**Protocole :**
1. Après l'entretien DSI concluant, proposer un "pilot payant à conditions préférentielles" : 250 000 DZD pour 60 jours (imputable sur le contrat annuel si concluant).
2. Si l'institution refuse de payer même un montant réduit → willingness to pay à 0. L'hypothèse H3 est fausse pour ce segment.
3. Si l'institution accepte → signer le bon de commande. C'est le premier revenu réel.

**Critère de succès :** 1 institution sur 3 approchées accepte un pilot payant (même réduit).

**Critère d'échec :** 0 institution sur 5 accepte de payer — même un centime. → Revoir le modèle économique B2G (gratuit puis revenu sur le relay, pas sur le pilot).

**Timing :** Mois 3–5 (après entretiens DSI, avant déploiement pilot)

---

## Expérience 3 — Stabilité Cluster à l'Échelle (Critique Technique)

**Hypothèse testée :** H2, H7
**C'est le kill criterion du fondateur.** Si cette expérience échoue, l'entreprise ne peut pas exister.

**Protocole :**
1. Déployer un cluster de 10 membres sur appareils réels (pas d'émulateurs) pendant 30 jours consécutifs en conditions normales d'utilisation (téléphones qui s'éteignent, changent de réseau, perdent la connexion).
2. Mesurer : uptime cluster (% du temps où au moins k membres sont actifs), nombre de fichiers perdus (doit être 0), nombre de reconstituions RS déclenchées, temps de failover super-peer.
3. Répéter à 20 membres, puis 50 membres.

**Critère de succès :**
- 0 perte de données sur 30 jours à 10 membres
- Failover super-peer < 30 secondes à chaque test
- RS(k,m) reconstruit correctement après retrait forcé de m nœuds simultanément

**Critère d'échec (kill criterion) :**
- Perte de données documentée → arrêt total jusqu'à correction
- Cluster qui se fragmente de manière non-récupérable > 2 fois sur 30 jours → revoir l'architecture de coordination

**Timing :** Mois 2–4 (en parallèle des entretiens DSI — peut commencer immédiatement avec le prototype existant en augmentant MAX_CLUSTER_SIZE)

---

## Expérience 4 — Test Onboarding Utilisateur

**Hypothèse testée :** H4
**Méthode :** Test utilisateur avec 5 personnes non-techniques.

**Protocole :**
1. Recruter 5 personnes non-techniques (famille, amis non-informaticiens) qui n'ont jamais vu l'app.
2. Leur donner un téléphone avec l'app installée et une seule instruction : "rejoins le cluster de [personne A] et sauvegarde une photo."
3. Observer sans intervenir. Mesurer : temps jusqu'à la première sauvegarde réussie, nombre de blocages, taux de succès.
4. Itérer sur l'UX jusqu'à ce que 4/5 réussissent en < 10 minutes sans aide.

**Critère de succès :** 4 personnes sur 5 complètent le flow en < 10 minutes sans assistance.

**Critère d'échec :** < 2/5 réussissent → l'onboarding est fondamentalement cassé → réécrire le flow avant tout pilot institutionnel.

**Timing :** Mois 3 (avant le pilot B2G — le pilot ne peut pas commencer avec un onboarding cassé)

---

## Expérience 5 — Performance du Relay Algérien

**Hypothèse testée :** H5
**Méthode :** Benchmark comparatif Render (US) vs relay algérien.

**Protocole :**
1. Déployer le relay sur infrastructure algérienne (Algerie Telecom / OVH Algeria / CERIST commercial).
2. Mesurer sur 7 jours : latence médiane (ms), latence P95, débit de transfert (Mo/s), taux d'erreur connexion WebSocket.
3. Comparer aux mêmes métriques sur Render (US) — baseline existante.
4. Tester depuis des appareils sur 4G (Mobilis, Djezzy, Ooredoo) et WiFi.

**Critère de succès :** Latence médiane < 200ms, débit > 1 Mo/s sur 4G, taux d'erreur < 1%.

**Critère d'échec :** Latence > 500ms ou débit < 200 Ko/s → investiguer l'hébergeur (problème de peering réseau algérien) avant tout lancement commercial.

**Timing :** Mois 2 (immédiatement après la migration relay)

---

## Expérience 6 — Willingness to Pay B2C

**Hypothèse testée :** H6
**Méthode :** Smoke test sur un groupe d'étudiants cible.

**Protocole :**
1. Présenter MobiCloud à un groupe de 20 étudiants (résidence universitaire, groupe de TP) avec une démo réelle.
2. Annoncer un prix de 300 DZD/mois après la période d'essai gratuite.
3. Mesurer : % qui disent "je paierais ça" vs "trop cher" vs "je ne paierais pas du tout".
4. Variante : tester 200 DZD et 500 DZD pour identifier le prix optimal.

**Critère de succès :** > 30% des personnes exposées déclarent une intention de paiement à 300 DZD/mois.

**Critère d'échec :** < 10% d'intention de paiement → le B2C direct n'est pas viable au prix envisagé. Options : descendre le prix (impact sur LTV) ou renoncer au B2C direct jusqu'après le bundle opérateur.

**Timing :** Mois 4–6 (en parallèle du pilot B2G — les étudiants sont accessibles rapidement)

---

## Expérience 7 — Signal Opérateur (Mobilis)

**Hypothèse testée :** H8
**Méthode :** Prise de contact exploratoire (pas un pitch commercial).

**Protocole :**
1. Identifier le responsable Innovation/Partenariats chez Mobilis via LinkedIn.
2. Envoyer un message selon le template de `02-strategy/go-to-market.md` (section "Pitch Opérateur").
3. Mesurer : réponse obtenue ou non, nature de la réponse (intérêt / indifférence / refus), délai.
4. Si réponse positive → préparer un dossier technique pour la réunion exploratoire.

**Critère de succès :** Une réunion exploratoire obtenue dans les 60 jours suivant le premier contact.

**Critère d'échec :** 0 réponse après 3 tentatives → canal opérateur inaccessible sans intermédiaire (AYRADE ou Algerie Telecom fund). Ajuster la stratégie.

**Timing :** Mois 12 (après le premier contrat B2G signé — prérequis pour la crédibilité)

---

## Tableau de Bord des Expériences

| # | Expérience | Timing | Statut | Résultat |
|---|---|---|---|---|
| 1 | Entretiens DSI (5 entretiens) | Mois 1–2 | À lancer | — |
| 2 | Pilot payant (willingness to pay B2G) | Mois 3–5 | En attente de E1 | — |
| 3 | Stabilité cluster 10–50 membres | Mois 2–4 | Peut démarrer maintenant | — |
| 4 | Test onboarding (5 non-techniciens) | Mois 3 | En attente MVP onboarding | — |
| 5 | Performance relay algérien | Mois 2 | En attente migration | — |
| 6 | Willingness to pay B2C | Mois 4–6 | En attente cluster stable | — |
| 7 | Signal Mobilis | Mois 12 | En attente contrat B2G | — |

---

## Décisions Déclenchées par les Expériences

| Si... | Alors... |
|---|---|
| E1 : 0/5 DSI ne mentionnent l'urgence spontanément | Revoir le positionnement B2G — peut-être que l'angle n'est pas la conformité mais l'économie de coût |
| E3 : Perte de données documentée | Arrêt de toute commercialisation jusqu'à correction technique — ne pas vendre un produit instable |
| E4 : < 2/5 réussissent l'onboarding | Repousser le pilot — ne pas déployer chez une institution avec un onboarding cassé |
| E1 + E2 : DSI intéressés mais ne paient pas | Revoir le modèle freemium : peut-être pilot gratuit + facturer uniquement le support SLA |
| E6 : < 10% WTP B2C | Abandonner le B2C direct Year 1, se concentrer exclusivement sur B2G jusqu'au bundle opérateur |

---

## Drapeaux

**Drapeaux Rouges :**
- L'Expérience 3 (stabilité cluster) est la seule expérience dont l'échec entraîne un arrêt du projet, pas un pivot. Si le cluster perd des données en conditions réelles, il n'y a pas de produit.

**Drapeaux Jaunes :**
- Les Expériences 1 et 4 dépendent de l'accès à des DSI et des non-techniciens acceptant de participer. Le fondateur devra activer son réseau personnel pour recruter ces participants avant de formaliser les entretiens.
- L'Expérience 7 (Mobilis) est séquentiellement bloquée par E1 + E2 + E3. Ne pas brûler les étapes — un contact Mobilis prématuré sans référence client peut fermer la porte définitivement.

## Sources
- `00-intake/brief.md` — kill criterion fondateur
- `04-product/mvp-scope.md` — critères de sortie MVP
- `02-strategy/go-to-market.md` — timing commercial qui contraint le timing des expériences
- `01-discovery/confidence-dashboard.md` — niveau de confiance par hypothèse
