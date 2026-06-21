# Mission, Vision & Valeurs — MobiCloud

**Phase :** 5 — Marque
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Élevée (fondée sur la stratégie et le positionnement validés en Phase 4)

---

## Mission — Pourquoi MobiCloud existe

*Trois options — choisir ou combiner.*

---

**Option A — Axée souveraineté (ton institutionnel)**
> *"Donner aux institutions algériennes les moyens de stocker leurs données sur leur propre sol, sur leurs propres appareils — sans dépendre d'un cloud étranger, sans provisionner un seul serveur."*

**Option B — Axée protection (ton grand public)**
> *"Protéger les fichiers des Algériens contre la perte, le vol et la dépendance aux plateformes étrangères — en les gardant là où ils appartiennent : sur les téléphones de leurs propriétaires."*

**Option C — Synthèse (ton hybride B2G + B2C)**
> *"Construire l'infrastructure de stockage souverain et mobile que l'Algérie n'avait pas encore : distribuée sur les appareils de ses utilisateurs, conforme à ses lois, accessible sans carte internationale ni serveur dédié."*

**Recommandation :** Option C pour les documents institutionnels et le pitch investisseur. Option B pour la communication grand public et TikTok.

---

## Vision — Le monde qu'on construit

*Deux options.*

---

**Option A — Algérie-centrée**
> *"Un jour, aucune institution algérienne ne perdra de données parce qu'un cloud étranger a coupé l'accès, fait faillite ou été soumis à une loi étrangère. Les données algériennes seront algériennes — par architecture, pas seulement par politique."*

**Option B — Continentale (narrative long terme)**
> *"Une Afrique du Nord où la souveraineté numérique n'est pas un privilège réservé aux grands groupes : un étudiant à Oran, un médecin à Annaba, un DSI à Alger — chacun maîtrise ses données depuis le téléphone dans sa poche."*

**Recommandation :** Option A pour le court terme (pitch, soutenance, premiers clients). Option B comme narrative d'ambition à moyen terme (partenariat opérateur, expansion régionale).

---

## Valeurs — Les principes qui guident les décisions

*5 valeurs de travail — elles doivent permettre de trancher des dilemmes réels, pas juste décorer un site web.*

---

### 1. Souveraineté par design
**Ce que ça veut dire :** La conformité algérienne n'est pas un argument de vente qu'on ajoute après coup — c'est une contrainte d'architecture. Si le relay n'est pas sur sol algérien, on ne vend pas. Si les données transitent hors des appareils des utilisateurs, on ne lance pas.

**Comment ça tranche un dilemme :** Si un opérateur demande d'héberger le relay à l'étranger pour réduire les coûts → on refuse, même si ça coûte un contrat.

---

### 2. Résilience réelle
**Ce que ça veut dire :** Un système qui tombe quand un nœud part n'est pas distribué — c'est juste centralisé avec plus d'étapes. On construit pour les pannes réelles : téléphones éteints, connexions coupées, membres qui quittent le groupe.

**Comment ça tranche un dilemme :** Si une feature semble utile mais crée un point de défaillance unique → on ne la livre pas tant qu'on n'a pas le failover.

---

### 3. Simplicité radicale
**Ce que ça veut dire :** Si un DSI a besoin d'un sysadmin pour déployer MobiCloud, on a échoué. Si un étudiant a besoin de comprendre le Reed-Solomon pour utiliser l'app, on a échoué. La complexité technique est notre problème, pas celui de l'utilisateur.

**Comment ça tranche un dilemme :** Toute feature qui nécessite une configuration manuelle côté utilisateur est un bug, pas une option.

---

### 4. Honnêteté technique
**Ce que ça veut dire :** On ne promet pas ce qu'on n'a pas encore testé. Le cluster de 3 téléphones est validé ; le cluster de 50 est une capacité architecturale, pas une promesse client. On documente les limites avant qu'un client les découvre.

**Comment ça tranche un dilemme :** Si un DSI demande "ça tient à 500 utilisateurs ?" et qu'on ne l'a pas testé → on dit "non testé, voici ce qu'on sait et comment on le validerait."

---

### 5. Ancrage local
**Ce que ça veut dire :** DZD, pas d'euros. Interface en français et en darija pour le B2C. Support joignable en heure algérienne. Relay en Algérie. MobiCloud n'est pas une startup qui "s'adapte" au marché algérien depuis Paris ou Dubaï — c'est un produit algérien par nature.

**Comment ça tranche un dilemme :** Si une opportunité exige de basculer vers une facturation en euros ou vers des serveurs hors Algérie → on l'écarte ou on la différencie clairement de l'offre souveraine.

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun.

**Drapeaux Jaunes :**
- La valeur "Ancrage local" peut entrer en tension avec une future expansion régionale (Maroc, Tunisie). Quand l'expansion arrive, reformuler en "Ancrage souverain par pays" plutôt qu'en "Ancrage algérien" exclusivement.
- La valeur "Honnêteté technique" est difficile à tenir dans un pitch commercial où l'acheteur veut des certitudes. Préparer des formulations précises : "testé à N nœuds, architecture prévue pour M — voici le plan de validation."

## Sources
- `02-strategy/positioning.md` — positionnement et attributs uniques
- `02-strategy/lean-canvas.md` — avantage concurrentiel
- `00-intake/brief.md` — principes techniques et limites connues
