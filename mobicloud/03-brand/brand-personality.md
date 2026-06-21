# Personnalité de Marque — MobiCloud

**Phase :** 5 — Marque
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (non testé avec utilisateurs réels)

---

## Archétype Primaire : Le Gardien (Guardian / Protector)

**Motivation centrale :** Protéger ce qui appartient aux gens — leurs fichiers, leur vie privée, leur souveraineté.

**Croyance :** Les gens méritent de contrôler ce qui leur appartient. Une technologie qui prend le contrôle à leur place n'est pas un service — c'est une dépendance.

**Expression dans le produit :**
- Les fichiers ne quittent jamais les téléphones des utilisateurs — par architecture, pas par promesse marketing.
- Le relay ne stocke rien. Il ne peut pas stocker. Ce n'est pas une politique de confidentialité — c'est une contrainte technique.
- Le chiffrement est matériel (Android Keystore) — même MobiCloud ne peut pas lire les fragments.
- La conformité algérienne n'est pas une feature optionnelle — c'est la raison pour laquelle le relay est en Algérie.

**Ce que l'archétype Gardien n'est pas :**
- Paternaliste ("on sait mieux que toi") — MobiCloud donne le contrôle, ne le reprend pas sous une autre forme.
- Alarmiste ("le danger est partout") — la sécurité est présentée comme une propriété positive, pas une peur exploitée.

---

## Archétype Secondaire : L'Expert (Sage)

**Rôle :** Donner de la crédibilité et de la profondeur à la posture de Gardien. Le Gardien protège — l'Expert explique pourquoi on peut lui faire confiance.

**Expression dans le produit :**
- Les termes techniques sont utilisés correctement (RS(k,m), pas "chiffrement RS") — signal implicite de compétence.
- Les numéros de loi sont cités exactement (Law 11-25, ARPCE Décision 48) — signal de préparation.
- Les limites sont documentées (cluster testé à 3 nœuds) — signal d'honnêteté intellectuelle.
- La validation IEEE est mentionnée quand pertinent — signal de rigueur académique.

**Ce que l'archétype Sage n'est pas :**
- Condescendant ("vous ne comprenez peut-être pas, mais...").
- Abstrait ("notre technologie de pointe transforme le paradigme du stockage").

---

## Attributs Émotionnels

*Ce que l'utilisateur doit ressentir à chaque point de contact avec MobiCloud.*

| Point de contact | Sentiment visé | Sentiment à éviter |
|---|---|---|
| Premier lancement de l'app | "C'est simple — j'ai compris comment ça marche." | Overwhelm, confusion |
| Fichier sauvegardé avec succès | "Mes fichiers sont en sécurité — pour de vrai." | Incertitude ("est-ce que ça a vraiment marché ?") |
| Pitch DSI (premier rendez-vous) | "Enfin une solution qui comprend notre contrainte réglementaire." | Scepticisme sur la conformité réelle |
| Perte d'un membre du cluster | "Le système a géré — je n'ai rien eu à faire." | Angoisse, sentiment de fragilité |
| Facturation | "Simple. En DZD. Pas de surprise." | Friction, sentiment de dépendance à l'étranger |

---

## Positionnement Émotionnel vs. Concurrents

*Comment MobiCloud se sent différemment de chaque concurrent.*

| Concurrent | Ce qu'on ressent avec eux | Ce qu'on ressent avec MobiCloud |
|---|---|---|
| Google Drive | Pratique, mais jamais tout à fait sûr (données chez Google, en dollars, hors Algérie) | Maîtrise. Ce qui est là est là, et c'est à moi. |
| AYRADE | Officiel, mais distant. Infrastructure lourde, relation formelle. | Accessible. Déployé en jours, pas en mois. |
| Hivenet | Innovant, mais étranger. EUR, EU, pas pour moi. | Local. Conçu pour l'Algérie, pas adapté après coup. |
| Nextcloud | Puissant, mais épuisant. Sysadmin, serveur, maintenance. | Libéré. Pas de serveur à gérer. |
| USB drive | Rudimentaire, anxiogène. Elle va tomber en panne — quand ? | Résilient. Le cluster survit à une panne. |

---

## Direction Visuelle (orientations, non maquettes)

*MobiCloud n'a pas encore d'identité visuelle fixée. Ce sont des orientations fondées sur la personnalité de marque.*

### Couleurs — Intention
- **Primaire :** Bleu profond (confiance, stabilité, souveraineté) + accent vert émeraude (résilience, local, Algérie). Pas de palette "dark tech" (trop froide pour le B2C) ni de couleurs trop lumineuses (trop ludique pour le B2G).
- **Éviter :** Vert fluo ou orange (suggère une app de jeu ou de livraison). Rouge (danger, alarme). Gris monotone (ennui institutionnel).

### Typographie — Intention
- **Institutionnel (B2G) :** Sans-serif sobre, poids medium à bold pour les titres. Lisible en petit corps pour les tableaux de conformité.
- **Grand public (B2C) :** Même police, mais appliquée plus librement — interligne élargi, corps plus grand, moins de densité.
- **Éviter :** Polices manuscrites (trop informelles pour la conformité réglementaire). Polices ultra-condensées (illisibles sur mobile).

### Iconographie — Intention
- **Métaphore principale :** Fragments qui se reconstituent (pas de "cadenas" ou "bouclier" — trop générique). Un téléphone qui contribue à un réseau de téléphones.
- **Pas de nuages** dans l'iconographie principale — MobiCloud n'est pas un cloud. C'est le principe différenciateur. Utiliser des téléphones, des nœuds de réseau, des fragments géométriques.
- **Algérie visible** dans les illustrations mais sobrement : géographie (silhouette du pays), non-drapeaux, non-symbolisme politique.

### Ton visuel général
**B2G :** Épuré. Beaucoup d'espace blanc. Données présentées en tableaux clairs. Pas d'illustrations décoratives — chaque élément visuel doit servir un argument.

**B2C :** Plus chaleureux. Téléphones reconnaissables (pas des écrans génériques), scènes de groupe familières (coloc, bibliothèque universitaire, TP). Personnes algériennes, contextes algériens.

---

## Boussole de Marque (pour trancher les décisions futures)

*Quand une décision de communication est ambiguë, poser ces 4 questions.*

**1. Est-ce que c'est vrai ?**
Si l'affirmation n'est pas encore validée (ex: "scale illimité"), ne pas l'utiliser. Même si c'est plus vendeur.

**2. Est-ce que ça parle à notre cible algérienne ?**
Si le contenu aurait exactement le même sens traduit pour un marché européen, il n'est probablement pas assez local.

**3. Est-ce que ça donne le contrôle ou le reprend ?**
MobiCloud doit toujours renforcer le sentiment que l'utilisateur maîtrise ses données — pas qu'il délègue à un nouveau système.

**4. Est-ce que ça protège ou effraie ?**
On peut parler de risques réglementaires (Google Drive = illégal) mais en montrant la sortie, pas en amplifiant l'anxiété. L'urgence doit déboucher sur une action possible.

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun.

**Drapeaux Jaunes :**
- L'archétype Gardien peut dériver vers le paternalisme si le ton n'est pas surveillé. Test rapide : est-ce que la communication "prend soin de" l'utilisateur ou "prend le contrôle à la place de" l'utilisateur ? La première est Gardien, la seconde dérive vers Héros (centrée sur la marque, pas l'utilisateur).
- La direction visuelle "pas de nuages" doit être cohérente jusqu'aux présentations et slides internes — un slide avec une icône nuage dans un deck B2G crée une confusion immédiate.

## Sources
- `03-brand/mission-vision-values.md` — valeurs, Souveraineté par design
- `03-brand/tone-of-voice.md` — registres et vocabulaire
- `02-strategy/positioning.md` — April Dunford, attributs uniques
- `01-discovery/target-audience.md` — personas et pain points
- `01-discovery/competitor-landscape.md` — comparatif émotionnel
