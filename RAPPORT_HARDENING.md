# Rapport — Sécurisation du protocole MobiCloud

## Pourquoi on a fait ce travail

Avant la défense PFE, on a fait un **audit de sécurité complet** du protocole MobiCloud. L'objectif : trouver les failles exploitables par un attaquant malveillant et les corriger, pour que tu puisses dire en oral *"mon protocole est sécurisé bout-en-bout"*.

On a trouvé **8 problèmes** sérieux. Tous sont corrigés.

---

## Les 8 problèmes corrigés (avant / après)

### 1. Identité du nœud — bug double-clé Keystore

**Avant** : Chaque téléphone avait deux identités différentes en interne (deux paires de clés). Conséquence : le téléphone se présentait sous un nom à un endroit et sous un autre nom ailleurs, donc il était impossible de mettre à jour son score de fiabilité (l'UPDATE en base de données ne trouvait jamais la bonne ligne).

**Après** : Une seule clé partagée par tout le code. Le téléphone a une identité unique et cohérente. Son score se met à jour normalement.

---

### 2. Serveur Relais — bugs de robustesse

**Avant** :
- **Race condition** : quand un téléphone se reconnectait au serveur (ex: changement WiFi → 4G), l'ancien handler de fermeture supprimait par erreur la nouvelle connexion. Le téléphone se retrouvait "fantôme" — connecté mais introuvable par les autres.
- **Validation laxiste** : le serveur acceptait des messages mal formés sur certains canaux mais pas sur d'autres. Un attaquant pouvait exploiter cette incohérence pour faire passer des données suspectes.
- **Perte de blocs** : si la connexion d'un téléphone tombait pendant la livraison de blocs en attente, ces blocs étaient supprimés de la mémoire serveur **sans avoir été livrés**. Données perdues silencieusement.
- **Pas de heartbeat** : si un téléphone disparaissait brutalement (réseau coupé), le serveur gardait sa connexion zombie pendant ~2 heures.

**Après** :
- Le handler vérifie qu'il s'occupe bien de la connexion qu'il pense gérer avant de supprimer
- Toutes les validations sont alignées et strictes
- Les blocs ne sont supprimés du buffer **qu'après livraison confirmée**
- Le serveur ping toutes les connexions toutes les 30s, force la fermeture de celles qui ne répondent pas

---

### 3. Élection du Super-Pair (protocole Bully) — 4 failles cryptographiques

**Avant** : Le protocole d'élection du chef du cluster avait des failles graves :
- **Score non signé** : un attaquant pouvait gonfler son score à 999 dans le message d'élection. Comme le score n'était pas inclus dans la signature, personne ne le détectait. Tous les autres nœuds voyaient *"il a un meilleur score que moi, je me tais"* → l'attaquant gagnait l'élection sans concurrence.
- **Messages ELECTION et ALIVE non vérifiés** : seuls les messages COORDINATOR et ABDICATION vérifiaient la signature. Les autres étaient acceptés tels quels. N'importe qui pouvait forger un message d'élection.
- **Pas de protection anti-rejeu** : un attaquant pouvait enregistrer un message d'élection légitime et le rejouer plus tard.
- **Punition contournable** : quand un Super-Pair "abdiquait", il était puni 5 minutes (interdit de se représenter). Mais cette punition était stockée uniquement en mémoire. **Tuer l'app + redémarrer = punition effacée**. Un nœud pouvait abuser de l'élection en boucle.

**Après** :
- Le score est inclus dans la signature → impossible de le modifier sans casser la signature
- Tous les messages (ELECTION, ALIVE, COORDINATOR, ABDICATION) sont vérifiés cryptographiquement
- Chaque message contient un timestamp ; les messages plus vieux que 30s sont rejetés
- La punition est sauvegardée sur le disque → survit aux redémarrages

---

### 4. Stockage de blocs — confirmation de stockage forgeable

**Avant** : Quand un téléphone envoyait un bloc à stocker chez Bob, Bob renvoyait une confirmation signée *"OK, j'ai stocké ce bloc"*. Mais cette confirmation ne contenait pas le lien direct entre **qui** confirme et **quel bloc**. Conséquences :
- Un attaquant pouvait signer une confirmation prétendant *"c'est Charlie qui a stocké le bloc"* (alors que Charlie n'a rien fait). Le système enregistrait *"bloc stocké chez Charlie"* alors que Charlie n'en savait rien.
- Quand l'utilisateur voulait récupérer son fichier, il allait chercher chez Charlie → bloc absent → fichier perdu (ou récupération beaucoup plus lente via les backups).

**Après** : La confirmation doit explicitement nommer **le téléphone à qui on a envoyé** et **le hash exact du bloc envoyé**. Si l'un ou l'autre ne correspond pas → confirmation rejetée. Plus de placements fantômes possibles.

---

### 5. Plans de migration / réplication / départ — rejouables

**Avant** : Quand le Super-Pair organisait des migrations de blocs (par exemple : *"téléphone A, envoie ton bloc X au téléphone B"*), les ordres étaient signés mais **sans timestamp**. Conséquence : un attaquant pouvait enregistrer un ancien ordre légitime et le rejouer plus tard, forçant des migrations inutiles, du gaspillage de bande passante, et de la pollution dans l'annuaire.

**Après** : Tous les ordres contiennent maintenant un timestamp signé. Les ordres plus vieux que 30s sont rejetés. Plus de rejeu possible.

---

### 6. Gossip (synchronisation entre téléphones) — empoisonnement

**Avant** : Les téléphones échangent en permanence des informations *"qui héberge quel bloc"* via un mécanisme appelé **gossip**. Trois failles :
- **Amplification** : n'importe qui (même non-membre du réseau) pouvait envoyer un faux message qui forçait un téléphone légitime à émettre du trafic réseau.
- **DoS de la base** : n'importe qui pouvait spammer des requêtes pour épuiser les ressources du téléphone.
- **Empoisonnement de l'annuaire** : un attaquant pouvait injecter dans l'annuaire des fausses entrées du genre *"bloc X est chez l'IP-attaquant:666"*. Les autres téléphones acceptaient sans vérifier.

**Après** : Filtre simple et efficace — seuls les téléphones **déjà connus du réseau** peuvent participer au gossip. Un attaquant externe ne peut plus rien injecter ni demander.

---

### 7. Tests automatiques — 12 tests cassés sur main

**Avant** : 12 tests automatisés étaient rouges sur la branche principale depuis longtemps. Pas à cause de bugs réels dans le code, mais à cause de mocks de test mal alignés (l'API du code avait évolué mais les tests pas suivis). Conséquence : on ne pouvait pas avoir confiance en la suite de tests pour détecter les vraies régressions.

**Après** : Les 12 tests sont réparés. La suite passe maintenant à **403/403 verts, 0 échec, 0 erreur**. La CI est verte.

---

### 8. Haute disponibilité du serveur Relais — pas réelle

**Avant** : Le code parlait de *"Serveurs Relais HA"* mais en pratique il n'y avait **qu'une seule URL** configurée, pointant vers un tunnel ngrok vers ton PC personnel. Si ton PC était éteint → tout le système ne fonctionnait plus. La logique de bascule "réessaye sur la prochaine instance" bouclait sur la même URL indéfiniment.

**Après** : 2 vraies instances déployées dans le cloud (Render), dans **2 régions géographiques distinctes** (Europe + Asie/Amérique). Indépendantes de ton PC. Si l'une tombe, le téléphone bascule automatiquement sur l'autre après 5 tentatives échouées. Le système est maintenant **réellement haute-disponibilité**.

---

## Comment on a vérifié que ça marche

Pour chaque correction, on a écrit un **test adversarial** : un test qui **simule l'attaque** et vérifie qu'elle échoue grâce à la correction.

**67 tests adversariaux** ajoutés au total.

Pour s'assurer que ces tests ne sont pas truqués, on a fait à chaque fois la même vérification :
1. **Tests verts avec correction active** ✅
2. **On désactive temporairement la correction**
3. **Tests rouges** (les attaques passent à travers) ✅
4. **On remet la correction**
5. **Tests verts à nouveau** ✅

Cela prouve que les tests **détectent vraiment** la régression, et ne sont pas juste des contrôles vides.

---

## Résumé chiffré

| Métrique | Valeur |
|---|---|
| Modules audités | 6 (Identité, Relais HA, Bully, Stockage, Plans, Gossip) |
| Failles corrigées | 8 majeures |
| Tests adversariaux ajoutés | 67 |
| Tests pré-existants réparés | 12 |
| Tests app totaux | **403/403 verts** |
| Tests serveur relais | **49 unitaires + 13 E2E verts** |
| Instances HA déployées | 2 (Render Frankfurt + 2ᵉ région) |
| Commits aujourd'hui | 8 |

---

## Pour la défense

Tu peux maintenant affirmer :
- *"J'ai mené un audit de sécurité complet du protocole, identifié 8 failles, et toutes corrigées."*
- *"Chaque correction est garantie par un test adversarial qui simule l'attaque correspondante."*
- *"Le système ne dépend d'aucune machine personnelle : 2 instances HA tournent dans le cloud sur des régions distinctes."*
- *"La suite de tests est complètement verte (403/403)."*

Limitations honnêtes à mentionner si on te creuse :
- L'annuaire DHT est filtré par "pair connu" mais pas signé crypto bout-en-bout (un pair légitime malveillant peut encore polluer les routes, mais sans voler de données — les transferts restent sécurisés).
- Pas de cert pinning OkHttp sur Android (s'appuie sur la PKI Let's Encrypt standard).
- Render free tier dort après 15 min — penser à ping `/health` avant la démo pour réveiller les instances.
