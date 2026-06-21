# User Stories — MobiCloud

**Phase :** 6 — Produit
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (fondé sur personas validés — stories non testées avec utilisateurs réels)

---

## Personas de référence

- **Karim** — DSI d'université algérienne. Décideur. Technicien de formation mais pas développeur. Veut la conformité sans charge opérationnelle.
- **Yasmine** — Étudiante, 22 ans, Alger. Non-technique. Veut ne plus perdre ses fichiers. Budget limité.

---

## Flux 1 — Création et Invitation au Cluster

### Story 1.1 (Karim, admin)
> En tant qu'administrateur institution, je veux créer un cluster pour mon établissement en moins de 5 minutes, sans configurer de serveur, pour que mes collègues puissent rejoindre sans assistance technique.

**Critères d'acceptation :**
- [ ] L'app crée un cluster en 1 action (pas de paramètres manuels à saisir)
- [ ] Un code d'invitation (QR ou lien profond) est généré immédiatement
- [ ] Le fondateur n'intervient pas dans le processus
- [ ] Le cluster est visible dans le tableau de bord admin dans les 60 secondes

---

### Story 1.2 (Yasmine, membre)
> En tant que membre invitée, je veux rejoindre le cluster de mon groupe en scannant un QR code, pour ne pas avoir à entrer d'adresse IP ou de configuration réseau.

**Critères d'acceptation :**
- [ ] Scan QR → rejoindre le cluster en < 30 secondes
- [ ] Aucun champ technique à remplir (pas d'IP, pas de port, pas de clé)
- [ ] Confirmation visuelle que le cluster est actif et combien de membres sont connectés

---

### Story 1.3 (Karim, admin)
> En tant qu'administrateur, je veux voir en temps réel quels membres sont connectés au cluster, pour savoir si le déploiement se déroule correctement.

**Critères d'acceptation :**
- [ ] Tableau de bord admin affiche : membres actifs / membres invités / membres hors ligne
- [ ] Mise à jour en temps réel (< 10 secondes de délai)
- [ ] Identifiant de chaque membre (nom ou identifiant institution — pas de numéro de téléphone exposé)

---

## Flux 2 — Sauvegarde d'un Fichier

### Story 2.1 (Yasmine)
> En tant qu'utilisatrice, je veux sauvegarder une photo depuis ma galerie vers le cluster en 2 taps, pour ne pas avoir à comprendre comment fonctionne le chiffrement ou la distribution.

**Critères d'acceptation :**
- [ ] Sélection fichier → confirmation → sauvegarde distribuée en < 3 actions
- [ ] Barre de progression visible pendant le transfert
- [ ] Confirmation "sauvegardé sur N appareils" à la fin (N = membres actifs ayant reçu un fragment)
- [ ] Aucun message d'erreur réseau visible si le relay rencontre une latence temporaire — retry automatique

---

### Story 2.2 (Yasmine)
> En tant qu'utilisatrice, je veux que mes nouvelles photos soient sauvegardées automatiquement sans que j'aie à y penser, pour ne pas perdre de souvenirs si mon téléphone casse.

**Critères d'acceptation :**
- [ ] Option de backup automatique activable dans les paramètres (désactivée par défaut — opt-in explicite)
- [ ] Backup se déclenche uniquement en WiFi par défaut (option : aussi en 4G)
- [ ] Notification de résumé journalier : "X fichiers sauvegardés aujourd'hui"
- [ ] Le backup n'impacte pas visiblement les performances de l'appareil

---

### Story 2.3 (Karim, admin)
> En tant qu'administrateur, je veux savoir combien d'espace de stockage total est disponible dans le cluster, pour estimer si la capacité est suffisante pour mon institution.

**Critères d'acceptation :**
- [ ] Tableau de bord affiche : espace total du cluster, espace utilisé, espace disponible
- [ ] Décomposition par membre (espace contribué par chaque appareil)
- [ ] Alerte si l'espace disponible passe sous un seuil configurable

---

## Flux 3 — Récupération d'un Fichier

### Story 3.1 (Yasmine)
> En tant qu'utilisatrice, je veux retrouver et télécharger un fichier sauvegardé depuis n'importe quel appareil connecté au cluster, pour pouvoir accéder à mes documents même si je n'ai pas mon téléphone habituel.

**Critères d'acceptation :**
- [ ] Liste des fichiers sauvegardés accessible depuis n'importe quel membre du cluster authentifié
- [ ] Téléchargement d'un fichier en < N secondes (N dépend de la taille — indiquer une estimation)
- [ ] Le fichier téléchargé est identique à l'original (intégrité vérifiée par hash)
- [ ] Si un membre du cluster est hors ligne, la reconstruction s'effectue quand même (avec k fragments disponibles)

---

### Story 3.2 (Yasmine — scénario perte de téléphone)
> En tant qu'utilisatrice dont le téléphone a été volé, je veux récupérer tous mes fichiers sur mon nouveau téléphone après avoir prouvé mon identité, pour ne rien perdre définitivement.

**Critères d'acceptation :**
- [ ] Processus de récupération d'identité sur nouveau téléphone documenté et fonctionnel
- [ ] Tous les fichiers sauvegardés avant la perte sont récupérables
- [ ] L'ancien téléphone est révoqué du cluster (ses fragments ne comptent plus)
- [ ] La re-réplication se déclenche automatiquement pour compenser le nœud perdu

---

## Flux 4 — Failover Super-Peer

### Story 4.1 (Karim, implicite — transparence totale)
> En tant qu'utilisateur d'un cluster actif, je veux que le cluster continue de fonctionner si le super-peer tombe, sans que j'aie à intervenir manuellement.

**Critères d'acceptation :**
- [ ] Si le super-peer devient injoignable, l'élection Bully se déclenche automatiquement
- [ ] Le nouveau super-peer est opérationnel en < 30 secondes
- [ ] Les utilisateurs ne voient pas d'interruption de service > 30 secondes
- [ ] Le tableau de bord admin affiche un avertissement ("super-peer changé à [heure]") mais pas une alerte d'erreur critique

---

### Story 4.2 (Karim, admin)
> En tant qu'administrateur, je veux être notifié si le cluster passe en état dégradé (moins de k+1 membres actifs), pour pouvoir agir avant qu'une panne supplémentaire entraîne une perte de données.

**Critères d'acceptation :**
- [ ] Notification push envoyée à l'admin si le nombre de membres actifs tombe sous le seuil critique
- [ ] Le seuil critique est clairement affiché dans le tableau de bord (ex : "cluster RS(4,2) — 6 membres requis, 5 actifs — risque si 1 membre supplémentaire se déconnecte")
- [ ] Aucune fausse alerte pour des déconnexions temporaires (< 5 minutes) de membres

---

## Flux 5 — Conformité (B2G uniquement)

### Story 5.1 (Karim, RSSI)
> En tant que responsable sécurité, je veux exporter un document prouvant que les données de l'institution ne quittent pas le sol algérien, pour répondre à un audit ANPDP sans solliciter le fondateur.

**Critères d'acceptation :**
- [ ] Export PDF généré depuis l'app en 1 action
- [ ] Le document contient : adresse IP du relay et localisation (Algérie), description de l'architecture (données sur appareils membres — pas sur relay), références légales (ARPCE Décision 48, Law 11-25)
- [ ] Le document est daté et porte un identifiant de version
- [ ] Le document est compréhensible par un auditeur non-technique

---

## Flux 6 — Abonnement et Paiement (B2C, V2)

### Story 6.1 (Yasmine)
> En tant qu'utilisatrice sur le tier gratuit, je veux passer au tier payant pour étendre mon quota de stockage, sans avoir besoin d'une carte bancaire internationale.

**Critères d'acceptation :**
- [ ] Options de paiement proposées : CCP La Poste, Baridimob, recharge opérateur
- [ ] La mise à niveau est effective immédiatement après confirmation de paiement
- [ ] Reçu envoyé par notification (pas par email obligatoire)
- [ ] Downgrade possible à tout moment (retour au tier gratuit sans perte de données si le quota est respecté)

---

## Matrice Stories × Versions

| Story | V1 (Pilot B2G) | V2 (B2C) | V3 (Opérateur) |
|---|:---:|:---:|:---:|
| 1.1 Création cluster admin | ✅ Must | — | — |
| 1.2 Rejoindre par QR code | ✅ Must | ✅ Must | — |
| 1.3 Tableau de bord membres | ✅ Should | ✅ Should | — |
| 2.1 Sauvegarde manuelle | ✅ Must | ✅ Must | — |
| 2.2 Backup automatique | ❌ Won't | ✅ Must | — |
| 2.3 Espace cluster disponible | ✅ Should | ✅ Should | — |
| 3.1 Récupération fichier | ✅ Must | ✅ Must | — |
| 3.2 Récupération après vol | ✅ Must | ✅ Must | — |
| 4.1 Failover transparent | ✅ Must | ✅ Must | — |
| 4.2 Alerte dégradation | ✅ Should | ✅ Should | — |
| 5.1 Export document conformité | ✅ Must | ❌ N/A | — |
| 6.1 Paiement DZD | ❌ Won't | ✅ Must | — |

---

## Drapeaux

**Drapeaux Rouges :**
- Story 3.2 (récupération après vol) dépend de la re-réplication (M3 dans mvp-scope.md) — les deux doivent être développées ensemble. Une story sans l'autre est incomplète.

**Drapeaux Jaunes :**
- Story 5.1 (document conformité) requiert un input légal externe avant implémentation — le contenu du document doit être validé par un avocat droit numérique, pas rédigé par le fondateur seul.
- Story 2.2 (backup automatique) nécessite une gestion fine de la batterie et de la consommation réseau en arrière-plan — sous-estimé sur Android depuis les restrictions background execution (Android 8+).

## Sources
- `04-product/mvp-scope.md` — priorisation MoSCoW
- `01-discovery/target-audience.md` — personas Karim et Yasmine
- `02-strategy/value-proposition.md` — jobs-to-be-done
- `00-intake/brief.md` — flows techniques existants
