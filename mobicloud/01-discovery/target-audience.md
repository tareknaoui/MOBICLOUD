# Audience Cible

**Phase :** 3 — Recherche de Marché (Synthèse)
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (le parcours d'achat B2G est confirmé par la recherche réglementaire/marchés publics ; les données comportementales B2C sont des proxys d'Afrique du Sud/Nigeria, pas de l'Algérie spécifiquement)

---

## Persona Primaire : Acheteur B2G

**Nom (fictif) :** Karim — DSI (Directeur des Systèmes d'Information)
**Rôle :** Directeur IT / Responsable des Systèmes d'Information
**Institution :** Université publique (2 000–15 000 étudiants) ou hôpital régional, Algérie
**Démographie :** 35–50 ans ; formation ingénierie ou informatique ; a travaillé dans le secteur public 10+ ans ; basé à Alger, Oran ou Constantine

**Objectifs :**
- Maintenir les systèmes IT de l'institution conformes à la loi algérienne évolutive
- Réduire la dépendance aux services cloud étrangers (responsabilité après la Loi 11-25)
- Fournir au personnel et aux étudiants un stockage de fichiers fonctionnant sur les appareils mobiles qu'ils possèdent déjà
- Éviter des projets d'infrastructure serveur on-premise coûteux pour lesquels il n'a ni budget ni personnel
- Ne pas être blâmé si un audit trouve un stockage de données non conforme

**Frustrations :**
- Google Drive et Dropbox sont ce que tout le monde utilise, mais ils sont désormais légalement risqués. Les retirer crée une révolte des utilisateurs sans remplacement.
- La connexion CERIST existe sur le papier mais est trop lente et limitée en capacité pour un usage mobile pratique.
- Nextcloud nécessite un serveur qu'il n'a pas et un sysadmin qu'il ne peut pas embaucher.
- AYRADE est conforme mais coûteux et centralisé — nécessite un investissement en infrastructure.
- Les marchés pour quoi que ce soit de nouveau prennent 12–18 mois sauf si c'est sous le seuil gré à gré.

**Outils actuels :** Accès fibre CERIST (si connecté), Google Workspace (techniquement non conforme), une certaine culture de clés USB, NAS on-premise dans les institutions plus aisées.

**Citation représentative :** *« On a les textes de loi depuis juillet 2025. Mon directeur général m'a demandé ce qu'on fait pour nos données. Je n'ai pas encore de réponse concrète. »* [Hypothèse — pas de verbatim obtenu d'un vrai DSI ; ceci reflète le contexte de pression de conformité de la recherche Wave 3]

**Parcours de décision :**
- **DSI** (Karim) — évaluateur technique, identifie le problème, présélectionne les fournisseurs, mène le pilot
- **RSSI** (Responsable Sécurité des Systèmes d'Information) — validateur sécurité, vérifie la conformité aux obligations de cybersécurité du Décret 26-07
- **DG / Recteur / Directeur** — autorité budgétaire, signe le contrat

**Critères de décision (classés) :**
1. Conformité légale — est-ce que cela satisfait la Loi 11-25 et les exigences ARPCE ?
2. Pas de nouvelle infrastructure serveur requise
3. Fonctionne sur les téléphones Android que le personnel possède déjà
4. Facturation DZD (facturation EUR/USD = marché rejeté)
5. Support local (peut joindre quelqu'un parlant darija ou français, répondant sous 24h)

**Budget :** Contrats gré à gré typiquement sous 3M DZD (22 000 $ aux taux actuels) pour éviter l'appel d'offres ouvert. Contrat initial probablement 500K–2M DZD/an.

**Cycle de vente :** 12–24 mois pour l'appel d'offres ouvert ; 3–6 mois pour le gré à gré sous le seuil avec un champion interne existant.

**Objections courantes :**
- « On utilise déjà CERIST. » → Réponse : CERIST ne fournit pas de sauvegarde mobile ; c'est un réseau, pas un produit de stockage.
- « Vous n'avez pas de client de référence. » → Le premier contrat brise cela. En attendant : démo + pilot à coût zéro.
- « Est-ce certifié ANPT / enregistré ANPDP ? » → MobiCloud doit préparer la documentation de conformité avant le démarchage B2G.
- « Que se passe-t-il si votre entreprise disparaît ? » → Réponse protocole ouvert : si MobiCloud ferme, le code du relay est open-source, les institutions peuvent faire tourner le leur.

**Où atteindre Karim :**
- Événements sectoriels : Forum Algérie Numérique, Salon DISTREE Africa
- LinkedIn (les professionnels IT algériens sont actifs — rechercher « DSI Algérie » retourne de vrais contacts)
- Conférences ANPT et groupes de travail cybersécurité
- Introduction chaleureuse via AYRADE (si partenariat sécurisé)
- Réseaux de recteurs d'universités

---

## Persona Secondaire : Utilisateur B2C

**Nom (fictif) :** Yasmine — Étudiante en master, Alger
**Rôle :** Étudiante universitaire / jeune professionnelle
**Démographie :** 22–28 ans ; téléphone Android (Samsung ou milieu de gamme chinois) ; utilisatrice internet mobile-first ; revenu mensuel 30 000–60 000 DZD (bourse étudiante ou salaire junior) ; Alger, Oran, Constantine
**Appareil :** Android 11+, 64–128 Go de stockage, souvent rempli à 60–80 %

**Objectifs :**
- Ne jamais perdre ses brouillons de mémoire, ses notes de cours et ses photos quand son téléphone casse ou se fait voler
- Ne pas payer 10 €/mois pour Google One quand tout son revenu disponible est ~15 000 DZD/mois
- Avoir quelque chose qui fonctionne quand la connectivité est instable (entre quartiers, dans certains bâtiments)
- Faire confiance au fait que ses fichiers soient privés — non indexés par Google, non visibles par une entreprise

**Frustrations :**
- Le palier gratuit Google Drive (15 Go) se remplit vite avec photos et enregistrements de cours. Le palier payant est inabordable et nécessite une carte de crédit internationale qu'elle n'a pas.
- WhatsApp « Messages favoris » et « Garder dans la discussion » comme sauvegarde accidentelle — fragile, non recherchable, disparaît si le compte WhatsApp est supprimé.
- Les clés USB tombent en panne. Elle a déjà perdu des fichiers deux fois.
- Rien ne fonctionne quand elle est sur le WiFi du campus derrière le NAT — les fichiers qu'elle a partagés ne se synchronisent pas entre son ordinateur portable et son téléphone.

**Outils actuels :** WhatsApp pour le partage de fichiers informel, occasionnellement Google Drive (palier gratuit), clés USB.

**Citation représentative (composite issue du minage d'avis Wave 3) :** *« J'avais 3 ans de photos et de travaux universitaires sur mon téléphone. Quand il a cassé, j'ai tout perdu. Google Drive est trop cher pour moi. J'aimerais qu'il y ait un moyen de sauvegarder automatiquement sur les téléphones de mes amis. »* [Tier 3, composite]

**Critères de décision :**
1. Gratuit ou très peu cher (200–400 DZD/mois maximum, en dessous des 1 299 DZD de Spotify)
2. Fonctionne automatiquement sans intervention de l'utilisateur
3. Pas besoin d'internet constant — synchronise au moins de façon opportuniste
4. Amis/colocataires peuvent être dans le même groupe (adhésion sociale requise)
5. Privé (elle ne veut pas que Google lise ses fichiers)

**WTP (consentement à payer) :** 200–400 DZD/mois. Preuve : Spotify Algérie = 1 299 DZD/mois ; Coursera = 2 499 DZD/mois. Le stockage doit se positionner bien en dessous de ces ancres compte tenu de l'utilité perçue plus faible d'une « sauvegarde » vs. « divertissement ». [Estimation, Wave 3]

**Objections courantes :**
- « Je ne veux pas utiliser le stockage de mon téléphone pour les fichiers des autres. » → Modèle réciproque : ses fichiers sont aussi sur leurs téléphones.
- « Que se passe-t-il si mon ami quitte le groupe ? » → Fonctionnalité de re-réplication (actuellement non implémentée — blocage honnête).
- « Est-ce sûr ? Mon ami peut-il voir mes fichiers ? » → Réponse chiffrement : non, ils stockent des fragments chiffrés qu'ils ne peuvent pas déchiffrer.
- « Ça a besoin d'internet pour fonctionner de toute façon — quelle différence avec le cloud ? » → Distinction clé : les fichiers sont *stockés* sur les téléphones, pas sur le serveur d'une entreprise. Si MobiCloud disparaît, les fichiers non. [Cadrage technique correct]

**Comment elle découvre des apps :**
- TikTok (21,1M d'utilisateurs algériens) : vidéos de démo virales en darija montrant « tes fichiers survivent sur les téléphones de tes amis »
- Groupes d'étude WhatsApp et Telegram : recommandation entre pairs
- Groupes Facebook universitaires : posts sur les apps utiles pour étudiants
- Bouche-à-oreille organique dans les résidences et appartements partagés

**CAC :** Quasi-nul via les canaux organiques. [Estimation, Wave 3]

---

## Anti-Persona (Qui NE PAS Cibler)

**Entreprises multinationales opérant en Algérie.** Elles ont des équipes de conformité, des contrats existants avec des fournisseurs cloud certifiés, et des processus de marchés qui exigent des certifications SOC 2 / ISO 27001 que MobiCloud n'a pas.

**Développeurs individuels voulant du stockage décentralisé.** Ils utiliseront IPFS, Storj, ou Nextcloud auto-hébergé. L'UX grand public de MobiCloud n'est pas pour eux ; et leur barre pour « ça marche » est différente de celle d'un utilisateur final.

**Utilisateurs hors d'Algérie (au lancement).** Chaque pays supplémentaire nécessite une nouvelle infrastructure relay, une nouvelle documentation de conformité, et de nouvelles relations. Rester en Algérie jusqu'à ce qu'un marché soit validé.

**Utilisateurs ruraux sans couverture 4G.** MobiCloud nécessite internet pour les transferts inter-appareils. Un utilisateur dans un village sans connectivité 4G ne peut pas bénéficier de l'architecture basée relay. [Contrainte technique confirmée à l'intake]

---

## Hiérarchie des Douleurs Client

Classée par fréquence × intensité à travers toute la recherche sur la voix du client :

| Rang | Douleur | Segment | Intensité | Fréquence |
|---|---|---|---|---|
| 1 | Exposition à la conformité : utiliser Google Drive/cloud étranger est désormais légalement risqué | B2G | Cheveux-en-feu | Élevée (pression réglementaire confirmée par 4 lois) |
| 2 | Aucun stockage conforme mobile-natif n'existe sans infrastructure serveur coûteuse | B2G | Élevée | Élevée (AYRADE/CERIST ne résolvent pas cela) |
| 3 | Le téléphone casse ou est volé → tous les fichiers perdus | B2C | Élevée | Élevée (documenté dans les données Afrique) |
| 4 | Stockage cloud trop cher relativement au revenu local | B2C | Modérée-Élevée | Élevée (données de prix des marchés Afrique) |
| 5 | L'auto-hébergement (Nextcloud) nécessite serveur et compétences IT que la plupart des institutions n'ont pas | B2G | Modérée | Moyenne (confirmé par les avis Nextcloud) |
| 6 | Les produits de stockage distribué (Hivenet) ont des échecs de fiabilité | B2C | Modérée | Moyenne (documenté dans les avis) |
| 7 | Confidentialité des données : fichiers visibles par des entreprises étrangères | B2C | Modérée | Moyenne (émergent, surtout post-loi) |

---

## Jobs-to-Be-Done

**Jobs fonctionnels :**
- Stocker des fichiers pour qu'ils survivent si un téléphone casse (B2C)
- Garder les données de l'institution en territoire algérien en conformité avec la Loi 11-25 (B2G)
- Fournir un accès mobile aux fichiers au personnel sans provisionner d'infrastructure serveur (B2G)
- Sauvegarder les fichiers automatiquement sans transferts USB manuels (B2C)

**Jobs sociaux :**
- En tant que DSI : « Je maîtrise la situation de conformité. Mon institution est protégée. » (B2G)
- En tant qu'étudiante : « Je suis la personne de mon groupe qui a configuré quelque chose de plus intelligent que WhatsApp pour nos fichiers. » (B2C)

**Jobs émotionnels :**
- Paix d'esprit : savoir que les fichiers survivront à un téléphone qui casse (B2C)
- Soulagement de l'anxiété de conformité : ne pas être la personne responsable quand l'audit trouve un stockage non conforme (B2G)

---

## Carte du Langage

**Mots utilisés pour décrire le problème :**
- B2C : « tout perdu », « téléphone cassé », « pas les moyens », « trop cher », « prend trop de données »
- B2G : « non-conforme », « risque juridique », « données qui quittent le territoire », « mise en conformité »

**Mots utilisés pour le résultat souhaité :**
- B2C : « survivre », « en sécurité », « automatique », « privé », « gratuit ou pas cher »
- B2G : « souverain », « conforme », « local », « sans serveur », « maîtrise des données »

**Mots utilisés dans la frustration :**
- B2C : « arnaque », « perdu », « échoué », « introuvable », « trop cher »
- B2G : « trop technique », « pas de référence », « délai trop long », « budget insuffisant »

**[Opinion]** Le pitch B2G devrait utiliser « conformité » et « souveraineté » — les mots exacts que le gouvernement algérien utilise (« Face aux GAFAM, l'Algérie choisit la maîtrise »). Le pitch B2C devrait utiliser « tes fichiers restent sur ton téléphone » — simple, viscéral, visuel.

---

## Où Atteindre Chaque Persona

| Persona | Canal | Densité | Coût | Priorité |
|---|---|---|---|---|
| Karim (DSI) | Démarchage direct LinkedIn | Moyenne | Gratuit | Élevée |
| Karim (DSI) | Forum Algérie Numérique / DISTREE Africa | Élevée | Faible (billets d'événement) | Élevée |
| Karim (DSI) | Partenariat AYRADE (intro chaleureuse à leurs 10K clients) | Très Élevée | Termes de partenariat | La plus haute si partenariat sécurisé |
| Karim (DSI) | Groupes de travail cybersécurité ANPT | Élevée | Gratuit (invitation) | Moyenne |
| Yasmine (étudiante) | TikTok — vidéo démo en darija | 21M d'utilisateurs | Gratuit | Élevée |
| Yasmine (étudiante) | Groupes universitaires WhatsApp/Telegram | Très haute densité | Gratuit | Élevée |
| Yasmine (étudiante) | Groupes universitaires Facebook | Élevée | Gratuit | Moyenne |
| Yasmine (étudiante) | Bouche-à-oreille dans les résidences | Élevée (mais lente) | Gratuit | Moyenne (volant organique) |

---

## Validation de la Demande

- **Demande de recherche :** En hausse — le stockage décentralisé croît à 14,68 % de TCAC mondialement ; données de volume de recherche spécifiques à l'Algérie indisponibles. [Confiance moyenne]
- **Activité concurrentielle :** Faible en Algérie spécifiquement → marché non prouvé, pas un marché prouvé. [Drapeau jaune]
- **Dépenses client :** La croissance de revenus de 117 % d'AYRADE prouve que les institutions paient déjà pour du stockage souverain. Le gap mobile-natif est non prouvé. [Confiance élevée sur la catégorie ; Moyenne sur le gap]
- **Preuve WTP :** Indirecte (ancres Spotify/Coursera pour le B2C). Aucune enquête WTP directe pour le stockage de fichiers en Algérie. [Confiance faible]
- **Signal de demande global : Modéré-Élevé pour le B2G ; Faible-Moyen pour le B2C.** La douleur institutionnelle est réelle et légalement imposée ; la demande grand public est inférée mais non validée.

---

## Connexions Stratégiques

- Le parcours de décision DSI (DSI → RSSI → DG) s'aligne avec les exigences réglementaires de `market-analysis.md` — le Décret 26-07 impose une unité de cybersécurité qui est le rôle du RSSI.
- La carte du langage B2C (« tes fichiers restent sur ton téléphone ») est l'inverse du cadrage concurrentiel dans `competitor-landscape.md` — où les échecs de Hivenet portent précisément sur des fichiers qui ne sont *pas* là où les utilisateurs l'attendent.
- L'opportunité de partenariat AYRADE dans `competitor-landscape.md` se connecte directement au besoin de Karim : il connaît déjà AYRADE et leur fait confiance ; un MobiCloud-via-AYRADE est une vente à plus faible friction.

---

## Drapeaux

**Drapeaux Rouges :**
- Aucune citation verbatim de vrais utilisateurs algériens n'a été obtenue. Toutes les données du persona B2C sont inférées d'études de marché sud-africaines, nigérianes et africaines générales. Le persona est directionnellement probable mais non validé.
- Le comportement d'achat B2G est modélisé à partir des réglementations de marchés publics et des patterns B2G de marchés émergents généraux. Aucun entretien DSI réel n'a été conduit.

**Drapeaux Jaunes :**
- Le critère d'arrêt B2C (fragilité du cluster causant un churn en semaine 1) est entièrement non testé. Le job « paix d'esprit » de Yasmine ne peut être livré que si le cluster est de façon fiable stable — ce qui n'a pas été testé hors d'un labo.

## Lacunes de Données
- Pas de citations verbatim d'utilisateurs algériens (la recherche n'a trouvé que des analogues indirects)
- Pas d'entretiens confirmés de directeurs IT institutionnels
- Pas de données WTP B2C validées pour le stockage de fichiers en Algérie
- Données de capacité et de liste d'attente CERIST indisponibles

## Sources
- Recherche brute Wave 3 : `01-discovery/raw/customer-voice.md`, `01-discovery/raw/demand-audience.md`
- Statistiques numériques Algérie : DataReportal 2025 (Tier 2)
- Minage d'avis Hivenet : Trustpilot / Play Store (Tier 3)
- Cadrage réglementaire algérien : sources gouvernementales officielles (Tier 1)
