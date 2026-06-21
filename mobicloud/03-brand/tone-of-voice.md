# Voix et Ton — MobiCloud

**Phase :** 5 — Marque
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (fondé sur personas validés ; non testé avec utilisateurs réels)

---

## Les 4 Traits de Personnalité Vocale

*Ce sont les 4 qualificatifs qui définissent comment MobiCloud parle — dans tous les contextes, tous les formats.*

---

### 1. Direct
On dit ce qu'on veut dire. On ne tourne pas autour du pot pour paraître humble. On n'utilise pas de superlatifs pour paraître important. Une phrase suffit si une phrase suffit.

| À faire | À éviter |
|---|---|
| "Tes fichiers sont chiffrés. Tes amis ne peuvent pas les lire." | "Notre solution de pointe utilise un chiffrement de niveau militaire pour protéger vos précieuses données." |
| "Un relay hébergé en Algérie — conforme ARPCE." | "Nous nous engageons à respecter toutes les réglementations locales et internationales en vigueur." |

---

### 2. Précis (sans jargon inutile)
On utilise les bons termes techniques quand ils s'adressent à un public technique (DSI, jury académique). On les traduit en bénéfices concrets pour un public non-technique (étudiants, direction générale). Pas de jargon décoratif, pas de termes vagues.

| À faire | À éviter |
|---|---|
| "Reed-Solomon RS(k,m) — vos fichiers survivent à m pannes simultanées." | "Notre technologie de rupture transforme la façon dont vos données sont stockées." |
| "Le relay route le trafic chiffré. Il ne stocke rien." | "Notre infrastructure sécurisée gère vos données de manière optimale." |

---

### 3. Local (ancré dans la réalité algérienne)
On parle la langue du contexte. Pour les DSI, on cite les numéros de loi exacts. Pour les étudiants, on parle de téléphone cassé, de groupe de TP, de Baridimob. On ne plagie pas la voix d'une startup californienne traduite en français.

| À faire | À éviter |
|---|---|
| "Payez en DZD — pas de carte internationale." | "Notre pricing flexible s'adapte à tous les marchés." |
| "Conforme à la Law 11-25 (juillet 2025) et à l'ARPCE Décision 48." | "Nous sommes conformes aux meilleures pratiques de protection des données mondiales." |
| "Tes fichiers survivent si ton téléphone tombe." | "Notre solution assure la continuité de vos actifs numériques." |

---

### 4. Honnête sur les limites
On ne promet pas l'infini. On indique ce qui est validé et ce qui est en cours. Cette honnêteté n'est pas une faiblesse — c'est ce qui rend les promesses qu'on fait crédibles.

| À faire | À éviter |
|---|---|
| "Testé sur 3 nœuds — architecture prévue pour des clusters beaucoup plus grands." | "Notre solution scalable s'adapte à des millions d'utilisateurs." |
| "Relay en cours de migration vers des serveurs algériens — disponible avant lancement commercial." | "Hébergement souverain garanti." (si pas encore fait) |

---

## "Nous sommes / Nous ne sommes pas"

*Pour prendre des décisions rapides sur le ton. Poser la question : est-ce que ça ressemble à la colonne de gauche ou de droite ?*

| Nous sommes | Nous ne sommes pas |
|---|---|
| Techniques et compréhensibles | Techniques et incompréhensibles |
| Confiants | Arrogants |
| Locaux et fiers | Nationalistes et fermés |
| Sérieux sur la souveraineté | Politiquement militants |
| Directs sur les limites | Pessimistes ou défaitistes |
| Accessibles | Condescendants |
| Rassurants | Alarmistes |
| Une startup algérienne | Une startup qui "fait de l'Algérie" |

---

## Contextes et Registres

### Contexte 1 : Pitch DSI (B2G, premier contact)
**Registre :** Institutionnel. Sobre. Factuel. Basé sur les textes de loi.
**Objectif :** Créer un sentiment d'urgence réglementaire + montrer une sortie simple.

> *"Depuis la Law 11-25 (juillet 2025), l'utilisation de Google Drive pour des données institutionnelles expose votre établissement à des sanctions. MobiCloud est la première solution mobile-native hébergée sur sol algérien : vos fichiers restent sur les appareils de vos membres, le relay est en Algérie, la facturation est en DZD. Pas de serveur à gérer. Pas de contrat étranger. Pilot en 2 semaines."*

---

### Contexte 2 : Onboarding étudiant (B2C, premier lancement de l'app)
**Registre :** Amical. Simple. Concret. Presque comme si un ami t'expliquait.
**Objectif :** Rassurer, rendre l'action simple, créer la confiance rapidement.

> *"Tes fichiers sont maintenant chez toi — et chez tes amis. Chiffrés, fragmentés, reconstituables. Si ton téléphone casse demain, ils sont toujours là. Tes amis stockent des morceaux qu'ils ne peuvent même pas lire."*

---

### Contexte 3 : Explication technique (jury, opérateur, partenaire tech)
**Registre :** Précis. Académique sur la forme, mais sans jargon creux.
**Objectif :** Montrer la profondeur technique, pas la performer.

> *"L'architecture repose sur un erasure coding Reed-Solomon RS(k,m) paramétrable. Dans le cluster de démo à 3 nœuds : RS(2,1) — 3 fragments, 2 suffisent pour reconstituer, 1 panne tolérée. Pour un cluster de N membres : k et m scalent pour tolérer m pannes simultanées sans perte de données. La coordination est assurée par une topologie super-peer avec élection Bully — failover automatique sans intervention humaine."*

---

### Contexte 4 : Communication de crise (bug, indisponibilité, perte de données)
**Registre :** Responsable. Sans excuses creuses. Action-first.
**Objectif :** Maintenir la confiance en étant honnête et en montrant qu'on agit.

> *"Le cluster a présenté une instabilité entre 14h00 et 15h30 le [date] — aucune donnée perdue, mais 4 utilisateurs ont vu leur app déconnectée. Cause identifiée : [cause]. Correctif déployé à 15h35. Voici ce qu'on a appris et ce qu'on change."*

---

## Vocabulaire MobiCloud

*Termes à utiliser systématiquement. Termes à éviter.*

| Terme correct | Terme à éviter | Pourquoi |
|---|---|---|
| "stocker" | "partager" | L'app stocke — elle ne partage pas les fichiers en clair |
| "relay" | "serveur cloud" | Le relay ne stocke pas — l'appeler "cloud" crée une fausse impression |
| "cluster" | "réseau" ou "groupe" (vague) | "Cluster" est le terme technique exact ; "réseau" est trop vague |
| "members" / "membres du cluster" | "utilisateurs" (en contexte technique) | Précision sur le rôle dans l'architecture |
| "fragment chiffré" | "morceau de fichier" | Le chiffrement est une propriété critique — toujours le mentionner |
| "sol algérien" | "notre infrastructure" | L'ancrage géographique est l'argument légal — toujours l'expliciter |
| "Law 11-25" | "la loi sur les données" (vague) | Le numéro exact = crédibilité et clarté |
| "RS(k,m)" | "chiffrement Reed-Solomon" | Reed-Solomon est de l'erasure coding, pas du chiffrement |
| "reconstruction" | "récupération" | Terme technique exact pour l'opération RS |
| "Baridimob / CCP" | "virement bancaire" | Ancrage dans la réalité de paiement algérien |

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun.

**Drapeaux Jaunes :**
- Le registre B2C très direct peut sonner "argot" sur certains canaux institutionnels. Garder deux registres clairement distincts et ne jamais les mélanger dans le même document.
- Le ton "honnête sur les limites" est contre-intuitif dans un pitch commercial. Préparer des formulations qui maintiennent la crédibilité tout en restant commercialement attractives : "Validé à 3 nœuds — architecture conçue pour N, voici le plan de validation" plutôt que "Pas encore testé à grande échelle."

## Sources
- `01-discovery/target-audience.md` — personas Karim et Yasmine
- `03-brand/mission-vision-values.md` — valeurs de référence
- `02-strategy/positioning.md` — attributs différenciants
