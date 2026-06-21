# Modèle Économique — MobiCloud

**Phase :** 4 — Stratégie
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Faible à Moyenne (aucun chiffre validé par des clients réels — toutes les projections sont des hypothèses)

---

## Modèle de Revenus

MobiCloud est une **entreprise d'infrastructure**, pas une application. La distinction est stratégique : le relay (l'infrastructure centralisée contrôlée par MobiCloud) est le point de monétisation naturel, pas l'application Android (qui peut être open-source).

### Stream 1 — RaaS B2G (Relay-as-a-Service pour Institutions)

**Ce qu'on vend :** Un contrat annuel incluant :
- Le relay WebSocket hébergé sur infrastructure algérienne (conforme ARPCE + Law 11-25)
- L'application Android déployée pour les membres de l'institution
- Le support technique et la documentation de conformité ANPDP/ARPCE
- SLA d'uptime (à définir — 99% suggéré)

**Prix indicatif :** 500 000 – 2 000 000 DZD/an selon la taille de l'institution et le nombre d'utilisateurs [Estimation — aucun benchmark Algeria B2G SaaS disponible]

| Taille institution | Utilisateurs estimés | Prix suggéré |
|---|---|---|
| Petite (faculté, clinique) | 50–200 | 500K DZD/an |
| Moyenne (université, hôpital régional) | 200–1 000 | 1M DZD/an |
| Grande (ministère, CHU) | 1 000+ | 2M DZD/an + |

**Conditions :** Contrat gré à gré sous le seuil d'appel d'offres (~3M DZD en Algérie) pour les premières ventes. Au-delà, appel d'offres BOMOP.

**Renouvellement :** Le coût de migration pour l'institution est élevé (re-certification, re-déploiement, ré-audit) → churn faible une fois déployé. [Opinion]

---

### Stream 2 — Bundle Opérateur Téléphonique (B2B2C)

**Ce qu'on vend :** Un accord de distribution avec Mobilis/Djezzy/Ooredoo pour inclure MobiCloud dans leurs forfaits grand public.

**Modèle :** Revenue share per abonné actif.
- L'opérateur intègre MobiCloud comme feature dans ses bundles premium.
- MobiCloud reçoit X DZD/mois par abonné actif utilisant le service.
- Revenue share typique en Afrique : opérateur garde 50–70%, MobiCloud reçoit 30–50%. [Estimation, benchmarks services bundlés Afrique]

**Contrainte technique :** Le bundle "stockage" nécessite que l'abonné ait des contacts dans le même cluster. L'opérateur peut résoudre cela via des packs famille/groupe.

**Prérequis :** Premier contrat B2G signé + relay algérien opérationnel + SLA démontré avant toute négociation opérateur. Un opérateur ne signera jamais avec un produit sans référence client et sans infrastructure locale.

---

### Stream 3 — Abonnement B2C Direct (Freemium)

**Modèle :**

| Tier | Prix | Inclus | Limites |
|---|---|---|---|
| Gratuit | 0 DZD | App Android + accès relay + cluster jusqu'à 3 membres | 5 Go de quota total par cluster |
| Standard | 200–300 DZD/mois | Clusters jusqu'à 6 membres | 20 Go par cluster |
| Premium | 400–500 DZD/mois | Clusters jusqu'à 10 membres + priorité relay | Quota illimité (limité par espace disque des membres) |

**Friction de paiement :** Paiement via CCP (La Poste Algérienne), Baridimob, ou recharge opérateur — pas de carte internationale requise. [Opinion — mécanisme à valider avec les vraies options de paiement disponibles]

---

## Économie Unitaire

**[Estimation — Hypothèse niveau A (assumption-based) : aucun client réel]**

### B2G

| Métrique | Estimation | Confiance |
|---|---|---|
| ACV (Average Contract Value) | 500K–2M DZD/an (~3,500–14,000 USD) | Faible |
| CAC | Très élevé (12–24 mois de cycle de vente + temps fondateur) | Faible |
| LTV | 3–5 ans de contrat si satisfaction → 1.5M–10M DZD par client | Très faible |
| Churn | Faible une fois déployé (coûts de migration) | Très faible |
| LTV/CAC | Favorable à long terme si cycle ramené à 6 mois via partenariat AYRADE | Hypothèse |

**Hypothèse critique :** Le cycle de vente B2G de 12–24 mois est la principale menace sur l'économie unitaire. Si ramené à 3–6 mois via partenariat AYRADE ou un DSI champion, le modèle devient viable rapidement.

### B2B2C (Opérateur)

| Métrique | Estimation | Confiance |
|---|---|---|
| Revenue/abonné actif | 60–150 DZD/mois (30–50% d'un bundle à 200–300 DZD) | Très faible |
| Volume potentiel | Mobilis : 20M+ abonnés, taux d'activation estimé 1–5% | Très faible |
| CAC | Proche de zéro (distribution par l'opérateur) | Faible |
| Risque principal | Exclusivité opérateur, SLA impossible à tenir à l'échelle avant traction | Élevé |

### B2C Direct

| Métrique | Estimation | Confiance |
|---|---|---|
| CAC | ~0 DZD (TikTok/WhatsApp organique) | Faible |
| ARPU (Average Revenue Per User) | 200–500 DZD/mois | Très faible |
| LTV | 12 mois × ARPU × (1 - churn) — churn inconnu | Très faible |
| Churn | Inconnu — dépend de la stabilité cluster en conditions réelles | DATA GAP |

---

## Scalabilité

**Le relay est le goulot d'étranglement et le levier d'échelle :**

- Un seul relay peut théoriquement gérer des milliers de clusters simultanément (il ne stocke pas les données, il route uniquement du trafic chiffré).
- Le coût marginal d'ajouter un nouveau cluster est essentiellement nul une fois le relay opérationnel.
- **Limite actuelle :** Le relay est une instance unique (Render, US). Migration vers infrastructure algérienne = prérequis avant toute scale.
- **Limite à moyen terme :** Si l'adoption croît (bundle opérateur), le relay devra être scalé horizontalement (plusieurs instances) avec un store partagé pour éviter le split-brain (incident documenté en mai 2026). [Données, historique projet]

**App Android :** Open-source possible → réduit les coûts de distribution, augmente la confiance des institutions (audit du code), facilite les intégrations opérateur. La valeur est dans le relay et le support, pas dans l'app.

---

## Dépendances et Partenariats Clés

| Partenaire | Rôle | Priorité | Statut |
|---|---|---|---|
| **Hébergeur algérien** (Algerie Telecom, OVH Algeria, CERIST commercial, datacenter local) | Héberger le relay sur sol algérien — prérequis absolu #1 | Critique | Non initié |
| **ARPCE** | Enregistrement comme opérateur cloud pour conformité Décision 48 | Critique | Non initié |
| **AYRADE** | Distribution B2G : accès à 10 000 institutions clientes ; crédibilité marché | Stratégique | Non initié |
| **Mobilis / Algerie Telecom** | Distribution B2B2C bundle + lien fonds cybersécurité 11M$ | Important | Non initié |
| **ANPDP** | Enregistrement comme sous-traitant de données personnelles (Law 11-25) | Important | Non initié |
| **Avocat spécialisé droit numérique Algérie** | Validation du montage légal avant première vente B2G | Nécessaire | Non initié |

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun chiffre de ce document n'est validé par des clients réels. Traiter toutes les projections comme des hypothèses de travail, pas comme des prévisions.
- La dépendance à un partenariat AYRADE pour accélérer le cycle B2G est une hypothèse stratégique non testée. AYRADE pourrait refuser ou demander des conditions désavantageuses.

**Drapeaux Jaunes :**
- Le modèle B2B2C bundle opérateur nécessite un SLA enterprise que le produit ne peut pas encore tenir. Ne pas initier les négociations avant le premier contrat B2G signé et le relay scalé.
- La monétisation DZD via CCP/Baridimob est un mécanisme non validé — les options de paiement réelles pour un abonnement numérique en Algérie doivent être vérifiées.

## Sources
- `01-discovery/market-analysis.md` — benchmarks économie unitaire, SAM/SOM
- `01-discovery/competitor-landscape.md` — données AYRADE, revenue share opérateurs africains
- `01-discovery/target-audience.md` — willingness to pay, comportement paiement DZD
- Historique projet MobiCloud (incident split-brain relay, mai 2026) — Données internes
