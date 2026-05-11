# Explication simple — Comment on délimite un cluster dans MobiCloud

---

## C'est quoi un cluster ?

Imagine un groupe WhatsApp. Mais au lieu de se partager des messages, les gens du groupe se partagent de l'espace de stockage sur leurs téléphones.

Un **cluster** dans MobiCloud c'est exactement ça : un groupe de téléphones qui se connaissent et qui stockent des fichiers ensemble.

La question c'est : **comment on décide qui fait partie du groupe ?**

---

## Le problème qu'on avait avant

Avant, on utilisait le nom du WiFi (le SSID) pour délimiter le groupe.

> "Tous les téléphones connectés au WiFi *USTHB-5GHz* → même cluster."

Le souci :
- Un téléphone en 4G n'a pas de WiFi → il n'entre dans aucun groupe
- Deux personnes dans le même bâtiment mais sur des WiFi différents → groupes séparés
- Deux personnes à 1000km peuvent se retrouver dans le même groupe si Firebase les met en contact

**C'était une mauvaise frontière.**

---

## La nouvelle approche en 3 étapes

### Étape 1 — Les téléphones se découvrent (comme d'habitude)

Les téléphones s'annoncent entre eux via le WiFi local ou via internet (Firebase).
C'est la phase de découverte, elle ne change pas.

### Étape 2 — Un chef est élu

Parmi tous les téléphones qui se voient, le plus fiable devient le **Super-Pair** (le chef du groupe).
L'élection se fait automatiquement par un algorithme qui compare les scores de fiabilité.

### Étape 3 — Chaque téléphone demande à rejoindre le groupe (nouveau)

Au lieu de rejoindre automatiquement, **chaque téléphone envoie une demande formelle au chef** :

> "Bonjour, je suis Carol, je veux rejoindre le cluster. Voici ma position GPS et mon espace libre."

Le chef vérifie :
- Est-ce que Carol est à moins de 5km de moi ? ✅ → **Accepté**
- Est-ce que Dave est à moins de 5km de moi ? ❌ (il est à Paris) → **Refusé**

Si quelqu'un est refusé, il cherche d'autres téléphones proches de lui et crée son propre groupe.

---

## Un exemple avec des vraies personnes

Tu as 4 amis avec leurs téléphones :

| Personne | Réseau | Où |
|----------|--------|----|
| Alice | WiFi | USTHB, Bab Ezzouar |
| Carol | 4G | Bab Ezzouar (800m d'Alice) |
| Bob | WiFi | Café Didouche (10km d'Alice) |
| Dave | 4G | Paris (1350km d'Alice) |

**Ce qui se passe :**

1. Les 4 téléphones se découvrent (via WiFi local + Firebase)
2. Alice gagne l'élection (meilleur score de fiabilité)
3. Carol demande à rejoindre → Alice calcule : 800m < 5km ✅ → **Carol est dans le groupe**
4. Bob demande à rejoindre → Alice calcule : 10km > 5km ❌ → **Bob est refusé**
5. Dave demande à rejoindre → Alice calcule : 1350km > 5km ❌ → **Dave est refusé**
6. Bob et Dave cherchent d'autres téléphones proches d'eux → ils forment leurs propres groupes

**Résultat :** 3 groupes géographiquement cohérents, formés automatiquement, sans aucune configuration.

---

## Pourquoi c'est mieux que le WiFi = cluster ?

| | Avant (SSID) | Maintenant (GPS + JOIN) |
|--|-------------|------------------------|
| Fonctionne en 4G | ❌ | ✅ |
| Frontière logique | Non (dépend du routeur) | Oui (distance réelle) |
| Adhésion explicite | ❌ (automatique) | ✅ (demande + acceptation) |
| Paris peut rejoindre Alger | ✅ (bug) | ❌ (filtré) |

---

## La limite honnête de cette approche

Le GPS à l'intérieur des bâtiments n'est pas toujours précis.
Deux personnes dans la même salle peuvent avoir une distance calculée de 200m à cause du signal GPS faible.

C'est pourquoi on garde le GPS comme **amélioration future**, et en attendant on ajoute simplement une **taille maximale de cluster** (ex: 10 téléphones max).

Quand le groupe est plein, les nouveaux arrivants sont automatiquement redirigés pour former un nouveau groupe.

---

## En une phrase

> MobiCloud forme des groupes de téléphones automatiquement :
> chaque téléphone **demande formellement** à rejoindre un groupe,
> le chef vérifie que le demandeur est **assez proche géographiquement**,
> et les refusés forment **leur propre groupe** ailleurs.
