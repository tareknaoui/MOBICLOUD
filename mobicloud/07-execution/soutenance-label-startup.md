# Préparation — Soutenance Label Startup

**Phase :** Exécution (post-soutenance PFE) · **Objectif :** obtenir le label Startup pour lancer officiellement en Algérie
**Projet :** mobicloud · **Date :** 2026-07-12
**Statut du dossier officiel :** Pas encore de template/critères officiels en main — ce document est construit sur les critères standards de ce type de comité, à ajuster dès que le dossier réel est disponible.

> **Différence avec `DEFENSE_PREP.md`** : ce document-là était pour un jury académique (maîtrise technique attendue, ligne par ligne). Ici, le jury évalue un **projet d'entreprise** : innovation, marché, modèle économique, équipe, scalabilité. Ne pas retomber dans le réflexe "expliquer l'algorithme Bully en détail" — c'est hors sujet pour ce jury.

---

## 1. Ce que ce type de comité évalue généralement

*(À vérifier/ajuster dès que le dossier officiel du programme est en main — ce sont les critères standards publiquement connus des comités de labellisation startup en Algérie, pas une garantie exhaustive.)*

| Critère | Ce qu'ils cherchent | Où MobiCloud est fort / faible |
|---|---|---|
| **Caractère innovant** | Nouveauté technologique ou de modèle, pas juste une app de plus | **Fort** : stockage mobile-natif P2P, aucun concurrent algérien sur ce créneau |
| **Contenu technologique** | Vraie techno, pas un wrapper | **Fort** : erasure coding Reed-Solomon, chiffrement E2E, élection distribuée Bully, prototype réel |
| **Potentiel de scalabilité** | Est-ce que ça peut grossir, ou c'est un projet local plafonné ? | **Fort sur le papier** (coût marginal ≈0 par abonné), **non prouvé** (zéro utilisateur réel) |
| **Modèle économique** | Comment ça gagne de l'argent, qui paie | **Solide** : B2B2C opérateur (revenue-share), freemium B2C — voir §3 |
| **Équipe** | Compétences pour exécuter | **Point faible objectif** : fondateur solo — à assumer, pas à cacher (§5) |
| **Éligibilité administrative** | Structure récente, seuils CA, actionnariat majoritairement personnes physiques | À vérifier selon le statut juridique actuel du projet |

---

## 2. Trame de pitch recommandée (10-12 minutes)

### Slide 1 — Accroche (30s)
> *"En Algérie, les données de millions de citoyens et de centaines d'institutions publiques transitent par des serveurs américains ou européens — souvent en violation de la loi. MobiCloud est la première solution de stockage souverain qui ne nécessite aucun serveur : les fichiers vivent, chiffrés, sur les téléphones d'un groupe de confiance."*

### Slide 2 — Le problème (1 min)
- Clouds étrangers (Google Drive, OneDrive) **non conformes** pour les institutions (Law 11-25, ARPCE Décision 48)
- Pas de facturation DZD → exclusion des citoyens sans carte internationale
- Perte de données quotidienne (téléphone cassé/volé) sans solution locale abordable
- *(Source : `02-strategy/positioning.md` §1, `02-strategy/value-proposition.md`)*

### Slide 3 — La solution (1 min)
- App Android : sauvegarde chiffrée, répartie sur les téléphones d'un groupe de confiance
- Aucune donnée ne transite par un serveur étranger — le relais ne route que du trafic chiffré, il ne stocke rien
- Chiffrement de bout en bout : illisible même par MobiCloud
- **Démo vivante si possible** : montrer le prototype sur 3-4 téléphones réels, casser un appareil, montrer la reconstruction

### Slide 4 — Pourquoi c'est défendable (1 min)
| Avantage | Pourquoi un géant ne peut pas copier facilement |
|---|---|
| Souveraineté par conception | Un acteur étranger devrait construire un datacenter algérien (3-5 ans, cadre réglementaire) |
| Facturation DZD | Exclut structurellement les concurrents étrangers |
| Coût de stockage ≈ 0 | L'espace est fourni par les téléphones, pas par MobiCloud — marges structurellement imbattables |
| Prototype fonctionnel | Avance technique réelle sur tout nouvel entrant |

### Slide 5 — La technologie (1-2 min, vulgarisé — pas de jargon type "erasure coding RS(k,n)")
> *"Chaque fichier est découpé et chiffré de façon à ce qu'il puisse être reconstruit même si un ou plusieurs membres du groupe sont hors ligne — sans jamais qu'un seul appareil détienne le fichier complet en clair."*
- Mentionner : chiffrement AES-256 lié au matériel (Android Keystore), tolérance aux pannes qui grandit avec la taille du groupe
- **Ne pas** dérouler l'algorithme Bully ou le format binaire du protocole relais — hors sujet ici

### Slide 6 — Le marché (1 min)
| Segment | Taille | Statut |
|---|---|---|
| Grand public via opérateur (B2B2C) | ~20M abonnés Mobilis | Cible commerciale prioritaire |
| Institutions publiques (B2G) | 600-700 sous obligation de conformité | Valeur nationale, perspective moyen terme |
| Jeunes/étudiants Android | Population massive | Adoption virale, moteur de traction actuel |

### Slide 7 — Le modèle économique (1-2 min)
- **B2B2C opérateur** (levier d'échelle) : MobiCloud intégré aux forfaits Mobilis, revenue-share 30-50%
- **Mutualisation, pas un marché à 3 parties** : le contributeur EST l'abonné — il prête de l'espace, il reçoit son backup ; pas de cash entre utilisateurs
- Coût marginal par abonné ≈ 0 → marges structurellement élevées dès l'échelle
- *(Chiffres détaillés : `07-execution/business-plan-synthese.md` §6-7 — les avoir sous la main pour le Q&A, pas nécessairement sur slide)*

### Slide 8 — Traction & roadmap (1 min)
- Prototype fonctionnel validé sur appareils réels
- Stratégie de lancement B2C en cours (viralité structurelle : le produit exige 3+ personnes pour fonctionner → coefficient viral intégré)
- Prochaine étape : cohorte de démo (20-50 clusters), puis ouverture du front opérateur
- **Être honnête** : zéro utilisateur payant à ce jour — le présenter comme un état, pas une faiblesse cachée

### Slide 9 — L'équipe (30s) — voir §5 pour le cadrage
### Slide 10 — L'ask (30s)
- Ce que le label apporte concrètement au projet (accompagnement, avantages fiscaux, accès aux fonds/marchés) — formuler précisément une fois le dossier officiel en main

---

## 3. Les chiffres/messages à marteler (mémoriser, pas lire)

- **"Coût de stockage ≈ 0"** — parce que les téléphones des utilisateurs fournissent l'espace, pas MobiCloud
- **"~20 millions d'abonnés Mobilis"** — l'échelle du marché B2B2C si le partenariat aboutit
- **"600 à 700 institutions publiques"** sous obligation légale de conformité (Law 11-25)
- **"Le contributeur EST l'abonné"** — anticipe la question piège "comment vous rémunérez les gens qui stockent ?" (réponse : réciprocité, pas de cash — voir §4)
- **"Prototype réel, pas un mockup"** — le démontrer si possible plutôt que de le dire

---

## 4. Questions difficiles anticipées (Q&A prep)

*(Format : question probable → réponse préparée, courte et directe — pas de réponse évasive)*

**Q : "Vous êtes seul. Comment vous allez scaler ?"**
> "Aujourd'hui je m'appuie sur des outils IA de développement pour tenir le rôle d'une petite équipe technique — c'est documenté dans mon plan financier. Le label et les premiers revenus (partenariat opérateur) sont précisément ce qui me permet de recruter au bon moment plutôt que de lever du capital dilutif prématurément."

**Q : "C'est juste une app de stockage de plus, en quoi c'est innovant ?"**
> "Aucune app de stockage n'est mobile-native ET souveraine en Algérie aujourd'hui. Les alternatives conformes (AYRADE, CERIST) nécessitent un serveur ; les alternatives mobiles (Google Drive) sont illégales pour les institutions. MobiCloud est la seule à combiner les deux — sans serveur à provisionner."

**Q : "Comment vous rémunérez les gens qui stockent les données des autres ?"**
> "Ce n'est pas un marché à 3 parties type Storj — le contributeur EST l'abonné. Karim prête 10 Go, Karim reçoit 10 Go de backup réparti. C'est une mutualisation, pas un paiement cash — ce qui garde le coût de stockage à zéro pour MobiCloud."

**Q : "Vous n'avez aucun utilisateur payant. Comment on sait que ça marche ?"**
> "Le prototype fonctionne sur appareils réels — c'est prouvé techniquement. La traction commerciale est l'étape suivante, avec un plan concret : cohorte de démo, puis pilote opérateur. Je ne prétends pas avoir de traction que je n'ai pas."

**Q : "Pourquoi un utilisateur choisirait ça plutôt que Google Drive ?"**
> "Pour le grand public : parce que Google Drive facture en devises étrangères (carte internationale requise) et que les données quittent le pays. Pour les institutions : parce que Google Drive est désormais illégal pour leurs données (Law 11-25 + ARPCE)."

**Q : "Qu'est-ce qui empêche Google ou un concurrent de faire pareil ?"**
> "Construire un datacenter en Algérie prend 3 à 5 ans et un cadre réglementaire qu'aucun acteur étranger n'a aujourd'hui. Et même s'ils le faisaient, notre coût de stockage resterait structurellement inférieur — on n'a pas de datacenter à amortir."

**Q : "Quel est votre plus gros risque ?"**
> Répondre honnêtement (voir `04-product/roadmap.md` §Drapeaux) : la stabilité du cluster en conditions réelles — si un groupe de 3-4 personnes n'est pas assez résilient, la rétention s'effondre. C'est le risque qu'on instrumente en priorité avant tout investissement de croissance.

**Q : "Vous ciblez le grand public ou les institutions ?"**
> "Les deux, mais dans cet ordre : le grand public d'abord, parce que c'est lançable seul, sans cycle de vente de 12-18 mois. La traction B2C devient la preuve qui ouvre la porte du partenariat opérateur — qui est le vrai levier d'échelle."

---

## 5. Comment cadrer le "fondateur solo" (point sensible, ne pas éviter)

Le jury va probablement le remarquer. Le cadrage recommandé, pas une excuse :
1. **Assumer, pas minimiser** : "Je suis seul aujourd'hui" plutôt que d'essayer de le noyer dans le discours.
2. **Montrer que c'est compensé** : l'usage d'outils IA de développement comme substitut à une équipe technique junior (déjà chiffré dans `05-financial/`).
3. **Montrer une trajectoire, pas un état figé** : le label / le premier revenu opérateur sont précisément le levier pour recruter — pas un vœu pieux, une séquence.

**Ne jamais dire** : "je vais recruter bientôt" sans préciser avec quoi (ça sonne creux). Toujours relier au levier de financement concret (label, fonds AT, revenue-share opérateur).

---

## 6. Pièges à éviter en soutenance

- **Jargon technique non vulgarisé** (Bully, Reed-Solomon RS(k,n), erasure coding) — dérouler l'intuition, pas l'algorithme
- **Comparaison directe avec AYRADE/CERIST comme concurrents** — se positionner en complémentaire, pas en challenger d'un acteur soutenu par l'État (cf. `positioning.md` §5)
- **Surpromettre la traction** — dire "zéro utilisateur, voici le plan" bat "on a une grosse demande" suivi d'un silence gênant si on creuse
- **Mentionner le PFE/l'académique comme cadre principal** — ce jury évalue une startup, pas un mémoire (cf. `go-to-market.md` : "ne pas mentionner l'académique, le PFE" dans les pitchs commerciaux — même logique ici)

---

## 7. Documents à avoir sous la main (pas forcément sur les slides)

- `07-execution/business-plan-synthese.md` — chiffres détaillés si le jury creuse
- `02-strategy/positioning.md` + `value-proposition.md` — ce document, pour la matière brute des slides 2-4
- `02-strategy/business-model.md` — flux d'argent détaillé si question sur la monétisation
- `04-product/roadmap.md` — pour répondre honnêtement sur les risques/limites actuelles
- Prototype fonctionnel sur 3-4 téléphones — **si la démo live est possible, elle vaut plus que 5 slides**

## Sources
- `02-strategy/positioning.md`, `02-strategy/value-proposition.md` — narratif et différenciation
- `07-execution/business-plan-synthese.md` — chiffres et modèle économique condensés
- `04-product/roadmap.md` — état honnête des risques et limites
- `DEFENSE_PREP.md` — pour référence, format différent (jury académique technique)
