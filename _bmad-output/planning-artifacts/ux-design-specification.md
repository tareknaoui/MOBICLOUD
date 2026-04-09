---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7]
inputDocuments: ["c:\\Users\\naoui\\Desktop\\Projets\\PFE\\_bmad-output\\planning-artifacts\\prd.md"]
---

# UX Design Specification PFE

**Author:** Naoui
**Date:** 2026-03-27T00:47:16+01:00

---

## Executive Summary

### Project Vision

MobiCloud transforme un groupe de smartphones géolocalisés à proximité (campus, zones de crise, conférences) en un cloud de stockage autonome, sécurisé et intelligent, sans nécessiter d'Internet ou de serveur central. Le système repose sur l'IA pour prédire la fiabilité des nœuds, ajuste la redondance des fichiers dynamiquement, et garantit une sécurité absolue (Zero-Trust) où aucun hébergeur ne peut lire les données qu'il stocke.

### Target Users

1. **Étudiants en amphi / Campus :** Ont besoin d'échanger des cours et des fichiers lourds localement de manière transparente.
2. **Participants de conférences :** Souhaitent distribuer ou récupérer des médias (présentations, photos) au sein d'un événement éphémère.
3. **Équipes de terrain / Zones blanches :** Nécessitent un système de sauvegarde critique et résilient face aux conditions imprévisibles.
4. **Chercheurs / IoT :** Cherchent à agréger des données locales depuis un réseau de capteurs distribué.

### Key Design Challenges

- **Rendre le Zero-Trust tangible :** L'interface doit rassurer les utilisateurs que leurs données sont morcelées et indéchiffrables par les pairs qui les hébergent.
- **Transparence utilitariste de l'état du réseau :** Le défi est de faire remonter l'état du Foreground Service vers l'UI en Jetpack Compose (via des Flows), sans réveiller l'écran ou le CPU inutilement.
- **Gestion stricte de l'énergie (NFR-03) :** Le système fonctionnant principalement sur batterie, l'interface doit encourager la confiance sans consommer de cycles CPU ou de batterie pour des effets visuels superflus.

### Design Opportunities

- **Gamification de l'Hébergement (Karma) :** Capitaliser visuellement sur le "Ratio de Réciprocité" pour créer un profil utilisateur valorisant l'hébergement de données et décourageant le parasitisme (free-riding).
- **Tableau de bord statique et économe :** Créer un "cadran de diagnostiques" clair et basse consommation affichant les métriques essentielles (Batterie locale, Score IA, Karma) plutôt qu'une cartographie animée coûteuse en énergie.
- **Explorateur de fichiers DHT P2P :** L'interface principale doit se concentrer sur l'efficacité utilitariste d'un explorateur de fichiers décentralisé, offrant une navigation rapide sans fioritures.
- **Onboarding sans friction :** Intégrer l'authentification et la génération des clés cryptographiques de manière transparente lors de la première connexion.

## Core User Experience

### Defining Experience

La valeur fondamentale de MobiCloud réside dans deux interactions principales : la navigation intuitive dans un explorateur de fichiers décentralisé (DHT) et la surveillance rassurante de la santé de son propre appareil au sein du réseau, via un tableau de bord statique et utilitariste.

### Platform Strategy

- **Native Android :** Interface développée exclusivement de manière native (Jetpack Compose).
- **Background-first (Headless) :** Le cœur du système s'exécute silencieusement via un Foreground Service (maintien du MulticastLock, découverte BLE, transferts Wi-Fi Direct), permettant au réseau de survivre même lorsque l'écran est éteint.
- **UI Réactive et Économe :** L'interface utilisateur ne calcule rien : elle se contente d'observer les états émis par le service en arrière-plan via des Kotlin StateFlow, évitant tout réveil inutile du CPU.

### Effortless Interactions

- **Rejoindre le cloud éphémère :** L'authentification mutuelle, l'échange de clés publiques et la découverte des pairs se font instantanément et de façon invisible dès le lancement (« Zéro-configuration »).
- **Stockage Fire-and-Forget :** Le dépôt d'un fichier déclenche automatiquement son fractionnement, son chiffrement par bloc (Zero-Trust), le calcul de la redondance adaptative et sa distribution anti-corrélée sans que l'utilisateur ait à configurer quoi que ce soit.

### Critical Success Moments

- **Magie du hors-ligne :** Le moment où un utilisateur télécharge instantanément un document alors que son appareil n'a ni 4G ni Wi-Fi Internet.
- **Hausse du Karma :** La satisfaction de voir son "Ratio de Réciprocité" (Karma) s'envoler après avoir hébergé de manière invisible les données du réseau, débloquant la capacité de publier ses propres fichiers lourds.
- **Preuve d'efficience :** Le constat, après plusieurs heures de séminaire, que MobiCloud en tâche de fond n'a drainé que 2 ou 3% de la batterie.

### Experience Principles

1. **L'utilitarisme comme boussole :** Prioriser de l'information claire et statique face aux animations coûteuses en énergie.
2. **Confiance par la clarté :** Exposer les métriques vitales (Score IA de Fiabilité, Batterie, Karma) pour rendre le "Zero-Trust" et l'équité tangibles.
3. **Zéro-friction cryptographique :** La complexité cryptographique doit être mathématiquement paranoïaque en backend, mais totalement invisible en frontend.

## Desired Emotional Response

### Primary Emotional Goals

- **Confiance Absolue (Trust) :** L'utilisateur doit avoir la certitude intuitive que ses données sont en sécurité, même lorsqu'elles sont physiquement stockées chez des inconnus.
- **Autonomie (Empowerment) :** Le soulagement profond de pouvoir sauvegarder et échanger des données critiques alors que toute infrastructure classique (Internet/Wi-Fi public) est défaillante ou absente.
- **Fierté Communautaire (Pride) :** Le sentiment d'être un membre utile et récompensé d'un réseau d'entraide local, valorisé par le système de Karma.

### Emotional Journey Mapping

- **Découverte (Onboarding) :** Curiosité face à la "magie technique" du P2P invisible, suivie d'un puissant soulagement de la connectivité spontanée.
- **Action Principale (Dépôt d'un fichier) :** Sécurité. L'interface suggère clairement que le fichier est "scellé et fragmenté" avant de quitter le téléphone.
- **Tâche de fond (Hébergement) :** Sérénité (Calm). L'utilisateur sait que l'application tourne, mais le cadran de diagnostic statique prouve que sa batterie est en sécurité (NFR-03).
- **Situation de Crise (Batterie critique) :** Sentiment de contrôle. Au lieu de la panique, l'UI indique simplement une "Évacuation d'Urgence" automatisée et maîtrisée.

### Micro-Emotions

- **Confiance > Scepticisme :** Vaincre la résistance psychologique initiale du concept "je laisse mes fichiers à des inconnus".
- **Sérénité > Anxiété énergétique :** Désamorcer la peur historique des applications P2P qui drainent la batterie des smartphones.
- **Accomplissement > Passivité :** Transformer l'hébergement passif en une boucle de gameplay gratifiante (Karma).

### Design Implications

- **Design de Confiance (Zero-Trust) ➔** Emploi de métaphores visuelles de sécurité (cadenas, blocs, fragmentation cryptographique chiffrée) au moment de l'envoi.
- **Design de Sérénité (Batterie) ➔** Esthétique utilitariste, mode sombre profond (économie OLED), absence totale d'animations superflues, typographie "data-driven" (style terminal/monospace) pour le cadran de diagnostic.
- **Design de Fierté (Karma) ➔** Mise en majesté du "Ratio de Réciprocité" dans le profil, associant la fiabilité de l'appareil à la réputation de l'utilisateur.

### Emotional Design Principles

1. **La transparence détruit la méfiance :** Exposer les scores bruts (Batterie, Score IA) désamorce la peur de la boîte noire algorithmique.
2. **La sobriété comme signal de performance :** Une interface sévère, statique et ultra-rapide communique implicitement le sérieux énergétique de l'application.
3. **Récompenser l'altruisme réseau :** Héberger des données pour les autres n'est pas une corvée invisible, c'est un statut gagné.

## UX Pattern Analysis & Inspiration

### Inspiring Products Analysis

- **Syncthing / Resilio Sync (Partage P2P) :** Brillent par leur clarté sur la gestion des "Nœuds" et les jauges de synchronisation fragmentées. L'état de santé des pairs connectés est un modèle d'efficacité.
- **Bridgefy / Briar (Messagerie Offline) :** Excellentes dans l'expérience de découverte Bluetooth sans compte utilisateur ("magie" du mesh). Cependant, leur esthétique peut parfois sembler trop militante ou brute pour le grand public.
- **System Settings Android (Paramètres de Batterie/Stockage) :** La référence absolue pour inspirer confiance. L'utilisation de graphiques vectoriels statiques, de chiffres bruts et d'un layout épuré donne un sentiment de contrôle technique total sans consommer d'énergie.

### Transferable UX Patterns

**Navigation Patterns :**
- *Bottom Navigation Bar* à trois onglets stricts : Explorateur DHT (Fichiers) | Tableau de Bord (Mon Nœud) | Réseau (Pairs détectés). Sans menus tiroirs complexes.

**Interaction Patterns :**
- *Statut de Service Persistant :* Une petite pastille de couleur globale (Vert/Ambre/Rouge) près du titre de l'application indiquant de façon non-intrusive la santé du Foreground Service.
- *Micro-feedbacks cryptographiques :* Des icônes "cadenas" ou "matrices" qui s'activent de façon statique lors de l'upload pour signifier visuellement le découpage (Erasure Coding).
- *Feedback d'attente technique :* Remplacer le "syndrome de l'écran vide" lors des négociations Wi-Fi Direct (3-5 sec) par un log console court statique plutôt qu'un spinner animée, préservant la batterie tout en informant l'avancée.

**Visual Patterns :**
- *Dark Hub OLED :* Des fonds vraiment noirs (#000000) pour éteindre les pixels, avec l'utilisation de couleurs d'accentuation haute-visibilité (ex: un vert terminal ou un ambre orangé) uniquement pour les données vitales (Karma, Batterie).

### Anti-Patterns to Avoid

- **La "Galaxie" animée :** Représenter les nœuds par des particules flottantes en mouvement (drainage massif de batterie).
- **Le tunnel d'onboarding lourd :** Imposer des écrans de création de profil alors que le réseau est asynchrone et sans serveur central ("Zéro-configuration" requis).
- **Le syndrome de l'écran vide ("Empty State" silencieux) :** Un explorateur de fichiers vide sans feedback sur la recherche en cours des pairs BLE.

### Design Inspiration Strategy

**What to Adopt :**
- L'esthétique "Terminal/Système" des paramètres natifs Android pour le cadran de diagnostic.
- Le concept de "Jauges de fragments" vu dans les clients Torrent/Syncthing pour montrer l'avancement multiparts d'un fichier.

**What to Adapt :**
- Simplifier l'UI de gestion des pairs pour ne montrer qu'un "compteur de pairs à portée".

**What to Avoid :**
- Les animations SVG continues (Lottie) de l'UX grand public classique.

## Design System Foundation

### 1.1 Design System Choice

**Material Design 3 "Override" (Ultra-Lean & Static)**

Pour MobiCloud, nous adoptons une approche pragmatique : conserver la base robuste de Material Design 3 (imposée par notre starter technique Android `atick-faisal/Jetpack-Android-Starter`) mais en appliquant un "override" global systématique pour la forcer à se conformer à notre vision utilitariste et basse consommation.

### Rationale for Selection

- **Alignement Architectural :** Garantit la compatibilité directe avec l'Epic 0 (Initialisation du Starter) et évite de réinventer la gestion de l'accessibilité ou des layouts de base.
- **Zero-Overhead Énergétique (NFR-03) :** Bien que MD3 inclue nativement des animations, l'override de ses thèmes (`RippleTheme`, `LocalIndication`) permet d'éliminer à la racine ces cycles de rendu (GPU/CPU) non-essentiels.
- **Identité "Dashboard de Cyber-Sécurité" préservée :** La possibilité de forcer sélectivement le mode sombre, le fond OLED pur et les tokens typographiques nous donne cette sévérité recherchée.

### Implementation Approach

- **Dark Mode OLED Pur Forcé :** Appliquer un `colorScheme` en mode sombre uniquement, écrasant le fond d'écran (`background`) à `#000000` (True Black) pour éteindre physiquement un maximum de pixels.
- **Interactions Hyper-Statiques :** Écraser globalement le comportement des clics pour remplacer les ondulations tactiles (Ripples) par un état visuel plat et instantané, respectant l'objectif d'absence d'animation.
- **Typographie Data-Driven Remplacée :** Remplacer les tokens typographiques par défaut de MD3. Utiliser une Sans-Serif nette (Inter/Roboto) alliée à une police Monospace stricte (ex: JetBrains Mono/Roboto Mono) pour les chiffres et les états, évitant le tremblement de la grille.

### Customization Strategy

- **Palette Restreinte "Monitoring" imposée au `colorScheme` :** Rediriger les couleurs primaires/secondaires vers la palette drastiquement limitée : Noir absolu (Fonds), Gris technique (Textes), Vert Terminal (Sain), Ambre Fluorescent (Alerte).
- **Tokens Rigides :** Intégration stricte via un fichier `MobiCloudTheme` encadrant les composants Material, forçant les développeurs à respecter l'esthétique "sévère".

## Information Architecture & Screen Flow

L'architecture de l'information de MobiCloud est conçue pour être plate (flat navigation), évitant les sous-menus profonds afin de garantir un accès immédiat aux fonctions utilitaires.

### 1. Flux d'Onboarding (Topologie Zéro-Configuration)

- **Écran d'Amorçage (Splash / Init) :**
  - Exécution silencieuse : Génération de la paire de clés cryptographiques locales.
- **Écran des Permissions (Vital) :**
  - Demandes groupées (Bluetooth/BLE, Localisation/Wi-Fi Direct, Stockage, Notifications pour le Foreground Service).
  - *Micro-copy :* Explication utilitariste ("Pourquoi nous avons besoin de ces accès pour créer le réseau hors-ligne").
- *Redirection automatique et définitive vers l'Explorateur.*

### 2. Navigation Principale (Bottom Navigation Bar)

#### Onglet 1 : Explorateur DHT (Écran par défaut)
C'est le cœur de l'interaction "Fire-and-Forget".
- **Top Bar :** Titre + Pastille globale de statut du Foreground Service (Vert/Ambre/Rouge).
- **Vue Principale (Liste) :** Fichiers découverts sur le réseau local.
  - *Composant Fichier :* Nom, Poids brut, Statut de disponibilité (Jauge de complétion des fragments, ex: 10/10 blocs).
- **Floating Action Button (Upload) :**
  - Action "Partager un fichier local".
  - *Feedback Visuel :* Modale transitoire montrant la jauge de découpage (Erasure Coding) et de chiffrement (Icône Cadenas) avant injection sur le réseau.

#### Onglet 2 : Mon Nœud (Tableau de Bord Diagnostique)
L'écran matérialisant la "Confiance et la Fierté" (NFR-03 & Anti-clandestin).
- **En-tête Identité :** Hash tronqué du nœud (ex: `0x4F...B2A`).
- **Bloc "Santé & Énergie" :**
  - Score IA de Fiabilité (0-100%).
  - Impact Batterie (estimation statique, ex: `-2.4%/heure`).
- **Bloc "Karma & Réciprocité" :**
  - Ratio de Réciprocité (Volume Hébergé vs Volume Publié).
  - Palier/Statut communautaire (ex: *Contributeur Solide*, *Nouveau Nœud*).
- **Bloc "Stockage" :**
  - Jauge utilitariste de l'espace alloué aux fragments tiers vs Espace total disponible.

#### Onglet 3 : Réseau P2P (Statut de la Constellation)
Permet de diagnostiquer l'état du "Datalake" sans animations coûteuses.
- **Cartes de Synthèse (Cards) :**
  - Nœuds découverts (BLE).
  - Connexions actives (Wi-Fi Direct).
- **Console de Logs (Terminal-style) :**
  - Un composant texte défilant affichant les événements bruts du Foreground Service (`Découverte pair X...`, `Négociation WFD locale...`, `Réception fragment partiel...`). Cela remplace le "syndrome de l'écran vide" lors des temps de latence réseau.

### 3. États Globaux Transverses

- **Alertes de Migration (Snackbar/Bannière) :** Si la batterie devient critique, une bannière ambre informe de façon persistante : "Évacuation d'urgence en cours..."

<!-- UX Design Specification Completed -->
