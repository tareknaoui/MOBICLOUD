# Signaux de Demande & Profilage d'Audience : MobiCloud

> Date de recherche : 2026-06-21. Toutes les étiquettes : [Données] = fait sourcé, [Estimation] = calcul dérivé, [Hypothèse] = inférence raisonnée, [Opinion] = jugement éditorial. Tiers de sources : T1 = officiel/primaire, T2 = analyste/trade, T3 = secondaire/inféré.

---

## Demande de Recherche

### Direction de Tendance pour le Stockage Distribué/Souverain en Algérie & Afrique

**Marché mondial du stockage décentralisé** [Données, T2] :
- Taille du marché : 603,48M $ en 2025 → 689,78M $ en 2026 → 1 574,67M $ d'ici 2032 (TCAC 14,68 %)
- Source : 360iresearch.com

**Dépenses cloud souverain** [Données, T2] :
- Dépenses cloud souverain mondiales projetées pour augmenter de **35,6 % en 2026** (CIO Dive)
- Cloud souverain Moyen-Orient & Afrique croissant à **23 % TCAC jusqu'en 2033** (Grand View Research)
- L'Afrique a ~0,6 % de la capacité data center mondiale malgré 19 % de la population mondiale [Données, T2 — TechCabal GITEX Africa 2026]

**Algérie spécifiquement** [Données, T1/T2] :
- La poussée cloud souverain de l'Algérie explicitement active : AventureCloudz lancé en avril 2026 (partenariat Djezzy + Taubyte)
- La Loi n° 25-11 (juillet 2025) ajoute des exigences de DPO, obligations de DPIA, notification de violation → pression de conformité institutionnelle croissante
- Cadre de Cybersécurité Algérie renforcé en janvier 2026 ciblant l'infrastructure nationale
- IPO AYRADE à la Bourse d'Alger juin 2026, revenu en croissance de 117 % GA (192M → 416M DZD en 2025) [Données, T2]
- Décret présidentiel 20-05 : tous les SI de l'État doivent nommer un CISO/RSSI — crée le rôle d'acheteur institutionnel pour les outils de sécurité/conformité

**Maroc** [Données, T2] :
- Roadmap politique « Cloud First » 2025–2030, Oracle élargissant la région Casablanca, data center renouvelable de 500MW à Dakhla

**Égypte** [Données, T2] :
- Région cloud public Huawei lancée au Caire en 2025

### Hotspots Géographiques
- **Algérie** : poussée souveraine active, cadre de protection des données obligatoire, numérisation institutionnelle santé + enseignement supérieur [Données]
- **Maroc** : politique Cloud First, présence Oracle, roadmap gouvernementale active [Données]
- **Égypte** : entrée cloud Huawei, grand marché entreprise [Données]
- **Afrique du Sud, Kenya, Nigeria** : investissement hyperscaler, marchés plus matures — moins d'urgence souveraine [Données, T2]
- [Estimation] L'Algérie est le marché de stockage souverain d'Afrique du Nord le plus prioritaire pour MobiCloud étant donné le resserrement réglementaire + la loi locale sur les données

### Requêtes/Signaux Connexes en Hausse
- « Sovereign cloud Algeria » lié au lancement d'AventureCloudz et à la couverture de l'IPO AYRADE [Données, T2]
- « Cloud souverain Algérie » tendance dans les médias tech algériens francophones (Ecofin Agency, Le Chiffre d'Affaires, Algerie360)
- Requêtes « Data sovereignty government » en hausse à travers le MEA selon CIO Dive [Données, T2]
- Les amendements de la loi de protection des données algérienne (Loi 25-11, 2025) génèrent une intention de recherche pilotée par la conformité dans le segment B2G [Estimation]

---

## Paysage Tarifaire

| Concurrent | Plan Gratuit | Starter / PME | Pro / Entreprise | Modèle |
|---|---|---|---|---|
| **Hivenet** | 10 Go gratuits | ~1-3 €/mois (200 Go–1 To) à 0,01 €/Go | ~16,50 €/mois (5 To) — 3,3 €/To | P2P distribué grand public ; paiement par Go |
| **Cubbit DS3** | Aucun (entreprise uniquement) | N/A | Devis sur mesure ; revendique jusqu'à 80 % d'économies vs hyperscalers ; $/To/mois forfaitaire, pas de frais d'egress | B2B entreprise ; distribué compatible S3 |
| **AYRADE** | Aucun | Devis VPS/cloud en DZD (pas de liste de prix publique ; basé sur devis) | Contrat cloud souverain entreprise ; cible : banques, énergie, hôpitaux | Cloud souverain B2G/B2B ; facturation DZD, pas de change nécessaire |
| **Nextcloud** | Gratuit (auto-hébergé, AGPL) | Hébergement managé à partir de ~4-5 €/mois (50-100 Go via partenaires) | Entreprise : 68,94–204,75 €/utilisateur/an (min 100 utilisateurs) ; modèle à palier de support | OSS auto-hébergé + support entreprise optionnel ; pas de frais par Go |
| **Google Drive** | 15 Go | 2,99 €/mois (100 Go) | 9,99 €/mois (2 To) | SaaS grand public ; non conforme à la loi algérienne sur les données |
| **MobiCloud** *(proposé)* | — | — | — | *Voir recommandation ci-dessous* |

**Notes sur les concurrents individuels :**
- Hivenet : remise de 30 % active en date de 2026 ; tarification du calcul encore en finalisation ; pas de client Android-natif pour l'Algérie [Données, T2]
- Cubbit : résidence des données UE/UK uniquement ; pas de présence de nœud en Algérie ; pas viable pour la souveraineté des données algérienne [Données, T2]
- AYRADE : Tarification opaque (sur devis uniquement), la facturation en DZD est un avantage structurel en Algérie ; l'IPO signale une légitimité institutionnelle [Données, T2]
- Nextcloud : Le logiciel gratuit sous-cote tous sur le coût de licence ; nécessite une capacité IT interne pour auto-héberger ; couramment déployé dans les universités françaises [Données, T2]

**Point de prix médian** [Estimation] : 3–10 €/mois pour 1-2 To grand public ; 50-100 €/utilisateur/an pour institutionnel/managé

**Modèle le plus courant** [Données] : Abonnement mensuel par Go/To (grand public) ; par utilisateur annuel + palier de support (institutionnel)

### Fourchette de Tarification Recommandée pour MobiCloud & Rationnel

**B2C (étudiants/jeunes professionnels) :**
- Palier gratuit : 2-5 Go (onboarding, viralité)
- Palier payant : 200-500 DZD/mois (~1,30-3,30 €) pour 50-100 Go [Estimation]
- Rationnel : Spotify Premium est à ~1 299 DZD/mois en Algérie ; Coursera Plus ~2 499 DZD/mois. L'utilité du stockage est de moindre urgence que le divertissement, donc le prix doit se situer bien en dessous de ces ancres. L'architecture P2P signifie un coût marginal quasi-nul par utilisateur. [Données + Opinion]

**B2G (institutions) :**
- Contrat annuel : 500 000–2 000 000 DZD/an par institution [Estimation]
- Rationnel : S'aligne avec le seuil « gré à gré » (sous le seuil d'appel d'offres public pour la négociation directe). Le revenu par client d'AYRADE impliqué de 10 000 clients × 416M DZD = ~41 600 DZD en moyenne — mais les grands clients institutionnels sont beaucoup plus élevés. Cibler 5-10x la moyenne. [Estimation, T3]
- Pas de comptage par Go pour les institutions ; une licence de capacité annuelle forfaitaire est attendue par les acheteurs du secteur public [Opinion]

---

## Évaluation du Consentement à Payer

### B2G — Preuves d'un WTP Fort
- Le plus gros acheteur du secteur IT algérien est le gouvernement ; budget de transformation numérique publique avec 500+ projets pour 2025-2026 [Données, T1 — trade.gov]
- Les nominations obligatoires de CISO/RSSI (Décret 20-05) forcent les dépenses de conformité [Données, T1]
- La Loi 25-11 (2025) augmente les enjeux de conformité de protection des données ; les institutions font face à un risque d'audit pour le stockage étranger non conforme [Données, T1]
- Le revenu d'AYRADE a crû de 117 % en 2025 — preuve que les institutions algériennes PAIENT pour le cloud souverain [Données, T2]
- La facturation DZD supprime la barrière de change qui tue les deals avec les concurrents étrangers [Données, Opinion]
- La roadmap de numérisation de la santé impose explicitement la numérisation des dossiers patients par les hôpitaux [Données, T2]

### B2G — Preuves d'un WTP Faible
- Les réglementations de marchés publics favorisent le soumissionnaire le moins-disant (évaluation prix-d'abord) [Données, T1 — trade.gov]
- Cycle de vente de 12-24 mois avec inertie budgétaire [Données, Opinion]
- Les bundles Huawei et le CERIST offrent des alternatives subventionnées/gratuites avec des relations institutionnelles déjà établies [Données, T2]
- Rigidité budgétaire : les dépenses IT sont souvent sous-financées dans les universités algériennes spécifiquement [Hypothèse]

### B2C — Preuves d'un WTP Fort
- 30 % des répondants à une enquête arabe prêts à payer pour des services numériques (étude Frontiers, incl. échantillon Algérie) [Données, T2]
- Spotify, Coursera, Evernote ont tous des utilisateurs algériens payants à des points de prix de 749–2 499 DZD/mois [Données, T3]
- 21,1M d'utilisateurs TikTok et 12M d'utilisateurs Instagram signalent un confort avec les écosystèmes d'apps [Données, T1 — DataReportal 2025]
- Âge médian 28,6 → population jeune avec un comportement digital-natif [Données, T1]
- Entrepreneuriat étudiant croissant (264 projets enregistrés dans les universités mars 2026) → cohorte férue de technique [Données, T2]

### B2C — Preuves d'un WTP Faible
- Faible revenu par habitant ; les alternatives gratuites (Google Drive, clés USB) normalisent le stockage à coût zéro [Hypothèse]
- Les coûts de données mobiles en Algérie créent une friction pour la synchronisation P2P [Hypothèse]
- Pas de culture de paiement établie pour le stockage spécifiquement (vs. streaming/social) [Hypothèse]
- L'exigence internet pour les transferts est un point de friction quand la bande passante est faible [Données — contexte projet]

### Facteurs Clés Pilotant le WTP sur ce Marché
1. **Cadrage souveraineté** — « Les données algériennes restent en Algérie » résonne avec les institutions post-Loi 25-11 [Opinion]
2. **Paiement DZD** — élimine la barrière change/carte qui bloque 60 %+ des utilisateurs algériens des SaaS étrangers [Estimation]
3. **UX mobile-first** — 116 % de pénétration mobile ; toute solution nécessitant une configuration desktop perd le B2C [Données]
4. **Ancre à coût quasi-nul** — doit être tarifé vs. gratuit (USB/Google Drive), pas vs. Cubbit [Opinion]
5. **Endossement institutionnel** — la validation des universités ou du MESRS réduit dramatiquement la friction B2C [Hypothèse]

---

## Persona Primaire : Acheteur B2G

**Nom (fictif) :** Mourad Hamdi

**Rôle :** DSI (Directeur des Systèmes d'Information) / responsable numérique

**Type d'institution :** Université publique (l'une des 108 universités/établissements d'enseignement supérieur d'Algérie) ou CHU (Centre Hospitalier Universitaire)

**Démographie :**
- Âge : 40-55 [Estimation]
- Localisation : Alger, Oran, Constantine, Annaba — grands centres urbains où les projets de numérisation se concentrent [Estimation]
- Éducation : Diplôme d'ingénieur (informatique/télécoms) ou Master en management IT [Hypothèse]
- Genre : majoritairement masculin dans le leadership IT institutionnel algérien actuel [Hypothèse]
- Employé par : Ministère de l'Enseignement Supérieur & de la Recherche Scientifique (universités) ou Ministère de la Santé (hôpitaux)

**Objectifs :**
- Se conformer aux mandats de transformation numérique nationale (DSP, vision Algérie 2030)
- Remplir les obligations CISO/RSSI sous le Décret présidentiel 20-05
- Moderniser la gestion documentaire et réduire la dépendance USB/e-mail
- Démontrer la souveraineté institutionnelle dans le traitement des données à la hiérarchie
- Éviter les risques d'audit de marchés liés à l'utilisation de cloud étranger/non conforme

**Frustrations :**
- Le personnel utilise Google Drive / WhatsApp personnels pour les documents institutionnels — cauchemar de conformité [Opinion, T3]
- Les fournisseurs cloud étrangers (Google, Microsoft) exigent un paiement en EUR/USD — marché bloqué
- L'infrastructure CERIST est sous-financée et lente ; les bundles Huawei viennent avec des préoccupations de lock-in
- Pas de solution mobile-native adaptée à la réalité de connectivité algérienne (4G fréquente, pas de WiFi toujours allumé)
- Difficulté à justifier les dépenses IT au DG/recteur qui voit l'IT comme un centre de coût, pas un actif stratégique [Hypothèse]

**Comment ils découvrent les fournisseurs :**
- GITEX Africa (événements endossés par le gouvernement) [Données, T2]
- Conférences EEPAD/DZ Tech et réunions ministérielles
- Réseau de pairs : autres DSI du même secteur ministériel
- Circulaires ministérielles recommandant des fournisseurs approuvés [Hypothèse]
- Publications d'appels d'offres sur les plateformes BAOSEM/BOMOP [Données, T1]

**Critères de Décision (classés) :**
1. Résidence des données algérienne / conformité à la loi nationale [Données — pression Loi 25-11]
2. Facturation DZD / pas de dépendance au change [Données]
3. Certification de sécurité / validation RSSI locale
4. Prix (biais moins-disant dans les réglementations de marchés publics) [Données, T1]
5. Support local continu / SLA en français/arabe

**Budget / Taille de Contrat Typique :**
- Sous le seuil gré à gré : < ~10-12M DZD (~65-80K €) évite l'appel d'offres public complet [Estimation, T3]
- Budget logiciel IT annuel par université de taille moyenne : estimé 5-20M DZD [Estimation — dérivé des données revenu/client AYRADE]
- Cible MobiCloud : 500K–2M DZD/an par institution

**Cycle de Vente :** 12-24 mois (cycle budgétaire, validation interne, approbation hiérarchique) [Données, Opinion]

**Objections Courantes :**
- « On utilise déjà le cloud CERIST / Algérie Télécom — pourquoi changer ? »
- « On a besoin d'un client de référence avant de s'engager — qui d'autre utilise ça ? »
- « Est-ce certifié ANPT ou approuvé nationalement ? »
- « Notre budget est fixé pour cette année ; parlons-en au prochain cycle. »
- « Que se passe-t-il si la startup échoue ? Nos données sont à risque. »

**Citation (composite issue des signaux de recherche) :**
> « On cherche une solution 100% algérienne pour stocker nos documents sensibles. Les solutions étrangères posent problème avec nos obligations légales, et le personnel utilise des clés USB ou WhatsApp — c'est inacceptable pour des données institutionnelles. » [Hypothèse — composite]

---

## Persona Secondaire : Utilisateur B2C

**Nom (fictif) :** Lina Benmansour

**Rôle :** Étudiante en master / jeune professionnelle (1-3 ans d'expérience)

**Démographie :**
- Âge : 20-30 [Données — âge médian 28,6, DataReportal 2025]
- Localisation : Alger, Oran, Sétif, Constantine — principales villes universitaires [Estimation]
- Appareil : Smartphone Android (appareil informatique principal ; le mobile génère ~60 % du trafic web algérien) [Données, T1]
- Revenu : 0–60 000 DZD/mois (bourse étudiante à salaire débutant) [Estimation]
- Connectivité : Mix de données mobiles 4G et WiFi domicile/campus

**Objectifs :**
- Ne jamais perdre les documents de mémoire, les supports de cours ou les fichiers de portfolio
- Partager de gros fichiers (vidéos, travaux de design, PDF) sans compression WhatsApp
- Accéder aux fichiers entre téléphone et ordinateur portable occasionnel de façon fluide
- Garder les fichiers privés de la surveillance étrangère (conscience croissante après les débats Snowden/TikTok) [Hypothèse]
- Coût faible ou nul — ou au plus un budget « un café par mois »

**Frustrations :**
- Le stockage du téléphone se remplit vite ; les limites Google Photos/Drive atteintes sans payer en EUR/USD
- WhatsApp compresse les fichiers et fait expirer les liens de téléchargement
- Les clés USB cassent, se perdent ou portent des virus
- Vitesses d'upload lentes en 4G lors du partage de gros fichiers
- Les services étrangers semblent peu fiables (« et si Google bloquait l'Algérie comme il l'a fait pour le streaming YouTube ? ») [Opinion, T3]

**Comment ils découvrent les apps :**
- TikTok (21,1M d'utilisateurs algériens) — vidéos de démo courtes [Données, T1]
- Recommandations de groupes WhatsApp/Telegram par les pairs [Données, Opinion]
- Reels Instagram (12M d'utilisateurs) [Données, T1]
- Bouche-à-oreille sur le campus universitaire (découverte pair-à-pair, CAC quasi-nul) [Opinion]
- Tutoriels YouTube (21,1M d'utilisateurs en Algérie) [Données, T1]

**Critères de Décision :**
1. Palier gratuit disponible (pas de friction pour essayer) [Opinion]
2. Fonctionne sur Android, rapide, intuitif
3. Les fichiers vraiment en sécurité / pas perdus
4. Algérien = digne de confiance (résonance locale)
5. Option de paiement DZD si palier payant

**WTP :**
- Palier gratuit : très haute probabilité d'adoption [Estimation]
- 200–500 DZD/mois : possible pour le segment féru de technique (benchmarké vs. Spotify 1 299 DZD)
- Au-dessus de 1 000 DZD/mois : très faible adoption sans forte différenciation [Estimation]

**Objections Courantes :**
- « Google Drive est gratuit et je l'utilise déjà. »
- « Je ne fais pas confiance à une nouvelle startup algérienne pour garder mes fichiers en sécurité. »
- « Je n'ai pas de carte de crédit / ne peux pas payer en ligne. »
- « Mon internet est lent — la synchronisation P2P va vider mon forfait data. »

**Citation (composite) :**
> « Je stocke tout sur WhatsApp et les pièces jointes disparaissent. Google Drive c'est bien mais en euros c'est compliqué, et j'ai peur que mes données partent à l'étranger. Si y'a une appli algérienne gratuite qui marche bien sur mobile, je l'utilise direct. » [Hypothèse — composite]

---

## Anti-Persona (Qui NE PAS Cibler)

| Profil | Pourquoi l'exclure |
|---|---|
| **Grandes entreprises privées (banque, énergie, télécoms)** | Ont déjà des contrats avec AYRADE/Huawei ; cycles budgétaires + marchés verrouillés ; ne changeront pas pour le risque startup |
| **ONG/INGO étrangères opérant en Algérie** | Les exigences de conformité des données pointent vers l'extérieur (RGPD, pas la loi algérienne) ; méthodes de paiement étrangères |
| **Utilisateurs ruraux/péri-urbains avec 2G/3G uniquement** | L'architecture relay P2P nécessite une 4G+ constante pour les transferts inter-cluster ; mauvaise UX |
| **Diaspora algérienne (France, Canada)** | Pas de moteur de souveraineté des données ; meilleures alternatives (iCloud, Dropbox) avec paiement EUR |
| **Administrateurs seniors réfractaires à la tech (DG, Recteur)** | Autorité budgétaire finale mais pas l'évaluateur ; mauvaise personne à qui vendre en premier |
| **Freelancers solo nécessitant un cloud professionnel (compatible AWS S3)** | Cubbit/Backblaze servent déjà ceci ; MobiCloud n'est pas encore compatible S3 |

---

## Où Atteindre Chaque Persona

| Persona | Canal | Densité | Coût | Notes |
|---|---|---|---|---|
| B2G — DSI/Directeur IT | GITEX Africa (éditions Marrakech/Alger) | Moyenne | Élevé (déplacement + stand) | Visibilité d'endossement gouvernemental |
| B2G — DSI/Directeur IT | Groupes de travail Ministère Enseignement Supérieur / réunions ANPT | Élevée | Faible (basé sur relations) | Nécessite une intro chaleureuse |
| B2G — DSI/Directeur IT | Plateformes d'appels d'offres BAOSEM/BOMOP | Élevée | Quasi-nul | Répondre aux RFP une fois certifié |
| B2G — DSI/Directeur IT | LinkedIn Algérie (profils Directeurs IT, DSI, RSSI) | Moyenne | Faible-Moyen | Démarchage francophone ; communauté DSI en croissance |
| B2G — DSI/Directeur IT | Conférences DZ Tech / SIT Algeria | Élevée | Faible | Audience concentrée de décideurs |
| B2C — Étudiant | TikTok Algérie (21,1M d'utilisateurs) | Très Élevée | Quasi-nul (organique) | Vidéos courtes de style démo, partage entre pairs |
| B2C — Étudiant | Groupes WhatsApp/Telegram de campus | Très Élevée | Nul | Partage viral ; boucle de référral |
| B2C — Étudiant | YouTube Algérie (21,1M d'utilisateurs) | Élevée | Faible (pub) | Format tutoriel / how-to |
| B2C — Étudiant | Reels Instagram Algérie (12M d'utilisateurs) | Élevée | Faible | Démo visuelle de la fonctionnalité de partage de fichiers |
| B2C — Étudiant | Programme d'ambassadeurs de campus universitaire | Élevée | Quasi-nul (indemnité) | Convertit la confiance physique en adoption numérique |
| B2C — Jeune professionnel | LinkedIn Algérie (en croissance) | Moyenne | Faible | Angle de cas d'usage stockage professionnel |

---

## Lacunes de Données

1. **Tarification exacte d'AYRADE en DZD** — le site web est sur devis uniquement ; pas de liste de prix publique disponible. Impossible de compléter le tableau concurrentiel avec des chiffres DZD réels. [Action : démarchage direct ou analyse de documents d'appels d'offres]

2. **Données de budget IT institutionnel algérien** — pas de données publiques sur les dépenses logicielles IT annuelles par université ou par hôpital. Toutes les estimations sont dérivées du revenu agrégé / nombre de clients d'AYRADE. [Action : minage du rapport annuel du MESRS, lecture plus approfondie de trade.gov]

3. **Volume de recherche par mot-clé spécifique pour l'Algérie** — données Google Trends Algérie non accessibles publiquement à un niveau granulaire. Pas de volume de recherche spécifique DZ pour « cloud souverain », « stockage P2P », « partage fichiers mobile ». [Action : extraction de données SimilarWeb ou SEMrush Algérie]

4. **Données d'installation d'app Android des concurrents pour l'Algérie** — Aucun nombre d'installations Play Store Algérie disponible pour les apps Hivenet, Nextcloud ou AYRADE. [Action : rapport de marché Sensor Tower / data.ai Algérie]

5. **Étude quantitative de consentement à payer pour le stockage de fichiers spécifiquement en Algérie** — Seules des données proxy disponibles (prix Spotify/Coursera, 30 % de WTP pour le bien-être numérique dans un échantillon arabe). Aucune enquête directe sur le WTP du stockage cloud en Algérie. [Action : entretiens utilisateurs primaires, 20-30 répondants]

6. **Benchmarks de taux de conversion de pilot B2G** — Pas de données sur quel % de PoC institutionnels algériens convertissent en contrats payants pour le logiciel. [Action : interviewer 2-3 fondateurs SaaS B2B algériens]

7. **Présence Hivenet Algérie / base d'utilisateurs** — Pas de données sur si Hivenet a des utilisateurs algériens ou un taux d'adoption Android. [Action : Discord communautaire Hivenet / géographie des avis d'app store]

---

*Sources consultées : DataReportal Digital 2025 Algeria, trade.gov Algeria Digital Economy & Selling to Public Sector, CIO Dive Sovereign Cloud 2026, 360iResearch Decentralized Storage Market, TechCabal GITEX Africa 2026, Ecofin Agency AventureCloudz, DLA Piper / CMS Law Algeria Data Protection, Le Chiffre d'Affaires AYRADE IPO, Algerie360, Grand View Research MEA Sovereign Cloud, Hivenet F6S/subscribe page (search-derived), Nextcloud pricing page (search-derived), Frontiers WTP Arab sample study, Morocco World News Cloud First 2025–2030.*
