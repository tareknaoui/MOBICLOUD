# Concurrents Directs : MobiCloud

> Recherche conduite : juin 2026. Étiquettes : [Données] = fait vérifié à partir d'une source, [Estimation] = chiffre dérivé, [Hypothèse] = inférence raisonnée, [Opinion] = jugement éditorial.

---

## Hivenet

- **Site web :** https://www.hivenet.com
- **Fondé :** 2022 [Données]
- **Siège :** Suisse (incorporée en Suisse) [Données]
- **Financement :** 12M€ Série A (mars 2024, mené par SC Ventures) ; total ~20,5M$ sur 2 rounds [Données]

### Produit

- **Offre centrale :** Stockage cloud distribué (grand public/prosumer) + calcul GPU distribué, construit sur un réseau d'espace disque dur et de ressources de calcul contribués par des participants dans le monde entier
- **Fonctionnalités clés (top 5) :**
  1. Chiffrement de bout en bout avec sharding cryptographique — données fragmentées sur des nœuds UE
  2. Apps Android + iOS + Windows + macOS [Données]
  3. Les contributeurs gagnent des crédits en partageant l'espace disque inutilisé (jusqu'à 55,56 % de réduction de facture) [Données]
  4. Transferts Send illimités (partage de gros fichiers) [Données]
  5. Marketplace de calcul GPU (RTX 4090/5090) pour charges de travail IA/ML [Données]
- **Approche technique :** Réseau de nœuds distribués ; données chiffrées et shardées avant de quitter l'appareil ; aucun serveur central ne détient les données complètes ; nœuds basés en UE uniquement pour le stockage [Données]

### Tarification

- **Modèle :** Freemium + abonnement à paliers [Données]
- **Paliers (stockage) :**
  - Gratuit : 25 Go (certaines sources disent 10 Go — probablement palier mis à jour) [Données/Estimation]
  - Payant : ~0,01 €/Go ; plan 5 To = ~3,30 €/To/mois [Données]
  - Paliers 200 Go, 1 To, 2 To, 5 To disponibles [Données]
- **Calcul GPU :** À partir de 0,10 $/heure ; CPU à partir de 0,035 €/h ; facturation à la seconde [Données]
- **Plan gratuit :** Oui, 10–25 Go [Données]

### Position sur le Marché

- **Client cible :** Consommateurs soucieux de la confidentialité, développeurs, petites équipes ; pivot vers le haut de marché vers les acheteurs de calcul IA [Données/Opinion]
- **Slogan de positionnement :** « The Sustainable Cloud » — vert, décentralisé, moins cher qu'AWS/Azure [Données]
- **Différenciateur clé :** Les utilisateurs peuvent monétiser l'espace disque dur libre pour compenser le coût d'abonnement ; angle durabilité (77 % d'émissions de carbone en moins revendiquées) [Données]

### Signaux de Traction

- **Avis (G2) :** 5,0/5 sur 2 avis vérifiés (stockage) ; produit Compute noté 4+ étoiles avec 6 avis Trustpilot [Données]
- **Social/Récompenses :** A reçu le label Bpifrance Deep Tech (juin 2024) [Données]
- **Clients notables :** Aucun client institutionnel majeur nommé publiquement ; principalement segment grand public/développeur [Hypothèse]
- **App :** L'app Android existe sur le Play Store (« Secure Cloud Storage – Hivenet ») avec des avis utilisateurs citant des vitesses d'upload lentes et des fonctionnalités manquantes [Données]

### Forces

- App Android grand public déjà en production [Données]
- La narrative durabilité résonne dans le climat réglementaire UE [Opinion]
- Financée en Série A — runway pour concurrencer [Données]
- La tarification sous-cote significativement Dropbox/Google Drive sur le $/Go [Données]
- Nœuds UE-souverains — conforme RGPD [Données]

### Faiblesses (d'après les avis/plaintes)

- Plaintes dans les avis de l'app Android : uploads lents/non fiables, fonctionnalité d'upload de dossier manquante, pas de streaming, gestion de fichiers faible (problèmes de suppression/téléchargement) [Données]
- Base d'avis très petite (2 avis G2) — signal de confiance limité pour les acheteurs entreprise [Données]
- Pas de présence en Afrique, pas de nœuds sur territoire algérien [Hypothèse]
- Offre de calcul potentiellement chère vs. les clouds GPU spécialisés (A100 à 0,75 $/h ailleurs) [Données]
- Produit de stockage séparé du calcul — pas une expérience mobile-native intégrée [Opinion]
- Pas de capacité offline-first / P2P réseau local — nécessite internet pour tous les transferts [Hypothèse]

### Niveau de Menace pour MobiCloud : **Moyen**

- **Pourquoi :** Hivenet est l'analogie produit la plus proche (Android grand public, stockage distribué, durabilité) mais cible une niche grand public privacy-tech européenne. Il n'a aucune présence sur territoire algérien, aucune dynamique de vente B2G, et ne résout pas le cas d'usage offline/réseau local. Il pourrait menacer MobiCloud dans un futur marché grand public, mais aujourd'hui est sans pertinence pour le SAM B2G Algérie. Si Hivenet construit un jour des nœuds algériens, le niveau de menace devient Élevé pour le segment non régulé.

---

## Cubbit

- **Site web :** https://www.cubbit.io
- **Fondé :** 2016 [Données — basé sur les rounds seed et les calendriers de partenariat SI de 40 ans]
- **Siège :** Bologne, Italie [Données]
- **Financement :** 19,7M$ au total sur 8 rounds (dernier round juillet 2024 : LocalGlobe, ETF Partners, Verve Ventures, CDP Venture Capital, Primo Ventures, 2100 Ventures, Datalogic) [Données]

### Produit

- **Offre centrale :** DS3 (Distributed S3) — stockage objet géo-distribué défini par logiciel permettant aux entreprises et fournisseurs de services managés (MSP) de déployer un stockage cloud souverain, compatible S3, sur leur propre infrastructure ou celle de partenaires
- **Fonctionnalités clés (top 5) :**
  1. API compatible S3 — remplacement drop-in pour les charges de travail AWS S3 [Données]
  2. Géo-distribution avec erasure coding — données découpées et répliquées sur plusieurs emplacements [Données]
  3. DS3 Composer — plateforme de stockage cloud white-label et personnalisable pour MSP et entreprises [Données]
  4. Zéro frais d'egress, pas de frais d'appels API, pas de frais de suppression [Données]
  5. Résiliente aux ransomwares par conception (pas de point de défaillance unique) [Données]
- **Approche technique :** Couche logicielle qui orchestre des nœuds de stockage distribués sur du matériel existant (on-prem, edge, cloud) ; ne nécessite pas de nouvel investissement matériel ; compatibilité protocole S3 [Données]

### Tarification

- **Modèle :** Licence logicielle entreprise (DS3 Composer) ; abonnement DS3 Cloud pour PME/MSP [Données]
- **Paliers :**
  - DS3 Cloud : tarif forfaitaire par To/mois — chiffre exact non listé publiquement ; contact commercial requis [Données]
  - DS3 Composer : licence par To brut de stockage total installé — devis sur mesure uniquement [Données]
- **Plan gratuit :** Pas de palier gratuit public [Données]

### Position sur le Marché

- **Client cible :** Entreprises européennes, MSP, intégrateurs de systèmes, institutions gouvernement/défense [Données]
- **Slogan de positionnement :** « Outsmart cloud storage. Sovereign & geo-resilient by design. » [Données]
- **Différenciateur clé :** Le seul logiciel de stockage géo-distribué compatible S3 qui permet aux entreprises de rester 100 % souveraines sur leur propre matériel tout en atteignant une résilience de type cloud [Opinion]

### Signaux de Traction

- **Avis (Gartner Peer Insights) :** Listé sur Gartner Peer Insights pour Public Cloud Storage 2025 [Données]
- **Clients :** 400+ entreprises et MSP à travers l'Europe ; Leonardo (entreprise de défense de 14 Md€+) ; Rai Way (radiodiffuseur d'État italien) ; Eurosystem SpA (augmentation de revenu de 580 % grâce au stockage après adoption de Cubbit) [Données]
- **Partenariats :** Commvault (cyber-résilience), Worldstream (Pays-Bas, premier partenaire néerlandais), listing sur la marketplace Scaleway [Données]
- **Signal de marché :** Gartner projette que l'IaaS cloud souverain UE croîtra de 3,3x de 6,9 Md$ (2025) à 23,1 Md$ (2027) — Cubbit est positionné directement dans cette vague [Données]

### Forces

- Déploiements entreprise éprouvés incluant des clients de niveau défense [Données]
- Pas de complexité d'app grand public — modèle SaaS B2B/B2G pur avec ROI clair [Opinion]
- La compatibilité S3 signifie zéro friction de migration pour les charges de travail cloud existantes [Données]
- Fort alignement réglementaire européen (RGPD, NIS2, résidence des données) [Données]
- L'étude de cas de hausse de revenu de 580 % est un puissant atout commercial [Données]

### Faiblesses (d'après les avis/plaintes)

- Opacité tarifaire — pas de tarifs publiés ; forte friction pour les marchés publics PME/gouvernement [Hypothèse]
- DS3 Composer « disponible pour un cluster sélectionné de partenaires et clients » — l'accès restreint limite l'adoption [Données]
- Zéro présence en Afrique ; zéro nœud ou partenariat sur territoire algérien annoncé [Données]
- Nécessite un investissement en infrastructure entreprise — pas mobile-native, pas d'app Android [Données]
- Marque eurocentrée ; manque de connaissance du marché local pour le paysage réglementaire d'Afrique du Nord [Opinion]
- Pas de capacité de stockage offline/réseau local [Hypothèse]

### Niveau de Menace pour MobiCloud : **Faible (actuel) → Moyen (horizon 3 ans)**

- **Pourquoi :** Cubbit est un acteur B2B/B2G entreprise pur avec zéro jeu grand public ou mobile, et aucune empreinte en Afrique. Il ne concurrence pas sur le même axe produit (P2P mobile) et ne peut pas satisfaire les exigences territoriales de données de l'Algérie aujourd'hui. Cependant, si un acteur de style Cubbit installait des nœuds algériens ou s'associait à AYRADE, ils pourraient menacer le pitch de vente institutionnel de MobiCloud. Surveillance nécessaire.

---

## AYRADE (Algérie)

- **Site web :** https://www.ayrade.com
- **Fondé :** 2009 [Données]
- **Siège :** Alger, Algérie [Données]
- **Financement/Capital :** IPO sur la Bourse d'Alger (juin 2026), levant ~1 Md de dinars (7,4M$ USD à ~135 DZD/$) en ouvrant 20 % du capital à 800 DZD/action [Données]

### Produit

- **Offre centrale :** Hébergement cloud centralisé traditionnel et services de data center pour les institutions algériennes — colocation, IaaS, cybersécurité et conformité souveraineté des données [Données]
- **Fonctionnalités clés (top 5) :**
  1. Deux data centers opérationnels sur territoire algérien [Données]
  2. Conformité cloud souverain avec la loi algérienne de résidence des données [Données]
  3. Solutions de cybersécurité (automatisation de la conformité réglementaire, intégration IA) [Données]
  4. Bras recherche & innovation pour IA/regtech [Données]
  5. 10 000+ clients incluant ~3 700 clients cloud actifs à travers banque, énergie, santé, administration publique [Données]
- **Approche technique :** Modèle data center / IaaS centralisé traditionnel ; PAS distribué, PAS P2P, PAS mobile-natif [Données]

### Tarification

- **Modèle :** Contrats entreprise/B2G — pas de tarification publique [Données]
- **Paliers :** Devis sur mesure pour colocation, VM cloud, bundles cybersécurité [Hypothèse]
- **Plan gratuit :** Non [Hypothèse]

### Position sur le Marché

- **Client cible :** Institutions publiques algériennes (ministères, hôpitaux, banques, entreprises énergétiques), grandes entreprises [Données]
- **Slogan de positionnement :** Premier opérateur de cloud souverain algérien [Données]
- **Différenciateur clé :** Seul opérateur cloud algérien dédié avec infrastructure physique sur territoire algérien et un historique de 16 ans au service des institutions locales [Données]

### Signaux de Traction

- **Revenu :** 192M DZD (2024) → 416M DZD (2025), +117 % GA ; projeté 1,66 Md DZD en 2026 [Données]
- **Clients :** 10 000+ au total, ~3 700 clients cloud actifs [Données]
- **IPO :** Premier opérateur de cloud souverain à entrer en Bourse d'Alger — juin 2026 [Données]
- **Infrastructure :** 2 data centers + expansion planifiée avec 294 nouveaux serveurs financés par l'IPO [Données]

### Forces

- Seul fournisseur cloud établi sur territoire algérien avec un historique institutionnel [Données]
- 10 000+ relations clients exactement dans le vertical cible de MobiCloud [Données]
- Moat réglementaire identique à celui de MobiCloud (conformité loi de résidence des données) [Données]
- L'afflux de capital de l'IPO financera l'expansion de l'infrastructure [Données]
- Profonde confiance institutionnelle issue de 16 ans sur le marché [Données]

### Faiblesses (d'après les avis/plaintes)

- Architecture centralisée — point de défaillance unique, pas de résilience distribuée [Opinion]
- Modèle IaaS traditionnel — n'exploite pas les appareils mobiles, le P2P ou l'edge computing [Opinion]
- 10 000 clients mais principalement VM/colocation — pas d'UX de stockage mobile-first [Hypothèse]
- Pas d'app mobile Android pour les utilisateurs finaux [Hypothèse]
- Revenu encore petit (~3M€ équivalent 2025) relativement au potentiel du marché institutionnel [Estimation]
- Le modèle d'infrastructure cloud signifie de l'OPEX pour les institutions (serveurs, réseau, SLA) vs. le modèle de réutilisation d'appareils de MobiCloud [Opinion]

### Niveau de Menace pour MobiCloud : **Moyen**

- **Pourquoi :** AYRADE est l'acteur incumbent du cloud souverain algérien. Il N'offre PAS de stockage P2P ou mobile-natif — c'est un opérateur de data center classique. Cependant, il contrôle les relations institutionnelles clés dont MobiCloud a besoin pour gagner. AYRADE est plus probablement un partenaire de canal potentiel ou une référence de validation qu'un concurrent direct sur l'axe produit, mais les institutions pourraient choisir « les VM AYRADE » plutôt que « le P2P MobiCloud » pour la conformité du stockage. La menace est sur la couche vente/relation, pas produit.

---

## UniCloud Africa

- **Site web :** https://unicloudafrica.africa
- **Fondé :** ~2024–2025 (lancé en novembre 2025) [Données]
- **Siège :** Nigeria (pan-africain) [Données]
- **Financement :** Partenariat avec OADC (Open Access Data Centres) ; détails de financement non divulgués [Données]

### Produit

- **Offre centrale :** Plateforme cloud souverain IaaS/PaaS pan-africaine déployée dans 6 pays africains (Nigeria, Ghana, Afrique du Sud, Zambie, Sénégal, Mozambique) avec hébergement des données 100 % dans le pays [Données]
- **Fonctionnalités clés (top 5) :**
  1. SLA d'uptime de 99,999 % avec deux zones de disponibilité active-active par pays [Données]
  2. Zéro frais d'egress de données [Données]
  3. Facturation en monnaie locale [Données]
  4. GPU-as-a-Service pour charges de travail IA/ML [Données]
  5. Conformité ISO 27001 et ISO 22301 [Données]
- **Approche technique :** Infrastructure de style hyperscaler traditionnel déployée localement dans chaque pays ; PAS de P2P distribué ; data center centralisé par pays [Données]

### Tarification

- **Modèle :** Dépense opérationnelle pay-per-use ; monnaie locale [Données]
- **Paliers :** Non listés publiquement [Données]
- **Plan gratuit :** Non [Hypothèse]

### Position sur le Marché

- **Client cible :** Entreprises africaines, gouvernement, santé, finance [Données]
- **Slogan de positionnement :** « The First Connected Sovereign Cloud Platform » pour l'Afrique [Données]
- **Différenciateur clé :** Infrastructure souveraine africaine multi-pays avec facturation en monnaie locale et zéro frais d'egress — ciblant la narrative du « colonialisme des données » [Données]

### Signaux de Traction

- **Portée géographique :** 6 pays lancés (2025) ; expansion vers Kenya, Tanzanie, Rwanda, Ouganda, Côte d'Ivoire, Égypte, Maroc [Données]
- **Notable :** Pas d'Algérie dans le déploiement actuel ou annoncé — gap significatif [Données]
- **Avis :** Aucun avis public G2/Capterra trouvé [Données]

### Forces

- Narrative cloud-souverain alignée avec le sentiment des gouvernements africains [Données]
- Portée multi-pays — pourrait capturer des deals institutionnels pan-africains [Données]
- Zéro frais d'egress est un avantage concurrentiel vs. AWS/Azure [Données]
- La facturation en monnaie locale supprime le risque de change pour les clients institutionnels [Données]

### Faiblesses

- Pas de présence ou de plans annoncés en Algérie [Données]
- Très récent (lancé novembre 2025) — historique non prouvé [Données]
- Modèle data center classique — pas de P2P, pas de mobile-natif, pas d'edge distribué [Opinion]
- Pas d'app mobile grand public/utilisateur final [Hypothèse]
- Soutien financier/de financement non transparent [Données]

### Niveau de Menace pour MobiCloud : **Faible (spécifique Algérie)**

- **Pourquoi :** UniCloud Africa n'opère pas en Algérie et n'a pas annoncé de plans d'entrée en Algérie. Même si c'était le cas, c'est une plateforme IaaS traditionnelle, pas un concurrent de stockage P2P mobile. Pertinent uniquement comme signal macro que l'espace cloud souverain africain attire l'investissement.

---

## Storj / Filecoin / IPFS (Stockage Décentralisé Crypto-natif)

- **Site web :** https://www.storj.io / https://www.filecoin.io
- **Fondé :** Storj : 2014 ; Filecoin/Protocol Labs : 2014 [Données]
- **Siège :** Storj : Atlanta, USA ; Filecoin : San Francisco, USA [Données]
- **Financement :** Les deux bien financés via ventes de tokens et VC ; Filecoin a levé 257M$ d'ICO ; Storj plusieurs rounds VC [Données]

### Produit

- **Offre centrale :** Stockage cloud décentralisé utilisant des nœuds de stockage distribués mondialement, incités par des tokens de cryptomonnaie (token STORJ / token FIL) [Données]
- **Fonctionnalités clés :**
  1. Erasure coding cryptographique pour la redondance
  2. API compatible S3 (Storj)
  3. Incitations économiques basées sur tokens pour les opérateurs de nœuds
  4. Réseau de nœuds mondial (milliers d'opérateurs)
  5. Chiffrement de bout en bout
- **Approche technique :** Modèle blockchain/crypto-incitation ; les nœuds sont des serveurs/desktops toujours allumés — PAS mobile-natif [Données]

### Tarification

- **Modèle :** Pay-per-use [Données]
- **Tarifs Storj :** 0,004 $/Go/mois de stockage ; 0,007 $/Go d'egress (en date de fév. 2025) [Données]
- **Filecoin :** ~2,50 $/Tio/mois pour l'archivage [Données]
- **Plan gratuit :** Storj offre un palier d'essai gratuit [Données]

### Position sur le Marché

- **Client cible :** Développeurs, entreprises nécessitant un stockage S3 redondant bon marché ; PAS les consommateurs, PAS le mobile, PAS l'Afrique/l'Algérie [Données]
- **Différenciateur clé :** Le stockage distribué mondialement le moins cher à grande échelle ; modèle d'incitation crypto-natif [Données]

### Signaux de Traction

- Storj a des milliers d'opérateurs de nœuds indépendants mondialement [Données]
- Aucun déploiement ou marketing spécifique à l'Afrique trouvé [Hypothèse]
- Pas d'app mobile Android grand public pour les utilisateurs finaux [Données]
- Pas de positionnement de conformité réglementaire algérienne [Hypothèse]

### Forces

- Coût extrêmement bas (78 % moins cher qu'AWS S3 pour l'archivage) [Données]
- Déjà éprouvé à l'échelle mondialement [Données]
- API compatible S3 développeur-friendly [Données]

### Faiblesses (relatives à MobiCloud)

- Nécessite un wallet/des tokens crypto — friction UX massive pour les institutions algériennes [Opinion]
- Pas d'app Android mobile-native pour les consommateurs [Données]
- Les opérateurs de nœuds sont des serveurs toujours allumés, pas des appareils mobiles — modèle économique différent [Données]
- Non conforme à la loi algérienne de résidence des données (nœuds en territoire non algérien) [Hypothèse]
- Zéro présence locale, zéro capacité de vente B2G en Algérie [Hypothèse]
- Exposition réglementaire crypto en Algérie (crypto fortement restreinte) [Données — l'Algérie a interdit les transactions crypto]

### Niveau de Menace pour MobiCloud : **Faible**

- **Pourquoi :** L'interdiction légale de la crypto en Algérie à elle seule élimine Storj/Filecoin comme concurrents dans le SAM B2G Algérie. Leur architecture (nœuds desktop/serveur toujours allumés, API développeur, incitations par tokens) est fondamentalement différente du modèle P2P mobile-natif de MobiCloud.

---

## AventureCloudz (Algérie — Djezzy + Algeria Venture + Taubyte)

- **Site web :** Référencé via les communiqués de presse Djezzy/Algeria Venture ; plateforme sur aventurecloudz.dz [Estimation]
- **Fondé/Lancé :** 30 avril 2025 [Données]
- **Siège :** Algérie [Données]
- **Financement :** Soutenu par Djezzy (filiale Veon, opérateur télécom algérien majeur) + Algeria Venture (accélérateur gouvernemental) [Données]

### Produit

- **Offre centrale :** Plateforme cloud orientée développeur pour les startups et entreprises algériennes — IaaS, PaaS, outils de développement IA ; hébergée exclusivement sur l'infrastructure Djezzy Cloud sur sol algérien [Données]
- **Approche technique :** Hébergement cloud traditionnel (data center Djezzy) + couche de plateforme développeur de Taubyte ; PAS de P2P, PAS de mobile-natif distribué [Données]

### Tarification

- Non divulguée publiquement [Données]
- **Plan gratuit :** Sandbox développeur probablement incluse [Hypothèse]

### Position sur le Marché

- **Client cible :** Développeurs logiciels, startups, entreprises tech algériennes [Données]
- **Différenciateur clé :** Seule plateforme cloud développeur nativement intégrée à l'infrastructure télécom algérienne [Données]

### Signaux de Traction

- Soutenu par un opérateur télécom algérien dominant (Djezzy a 20M+ abonnés) [Données]
- Endossement gouvernemental via le partenariat Algeria Venture [Données]
- Aucun nombre d'utilisateurs public ou revenu divulgué [Données]

### Forces

- Puissance de distribution télécom (relations institutionnelles/entreprise existantes de Djezzy) [Données]
- Légitimité gouvernementale via Algeria Venture [Données]
- Souverain par conception — territoire algérien [Données]

### Faiblesses

- Focus développeur/startup — NE cible PAS le stockage pour hôpitaux/universités/ministères directement [Données]
- Pas de produit de stockage P2P mobile-natif [Données]
- Très récent — pas d'historique [Données]

### Niveau de Menace pour MobiCloud : **Faible**

- **Pourquoi :** Produit différent (plateforme cloud développeur vs. stockage P2P institutionnel). Pourrait devenir pertinent si Djezzy décidait de lancer un produit de stockage grand public/institutionnel, mais aucune preuve de tels plans.

---

## Résumé du Paysage Concurrentiel

### Concentration du Marché

L'espace du stockage mobile P2P distribué pour les consommateurs et institutions en Afrique est effectivement **vacant** [Opinion]. Le marché mondial a :
- Stockage distribué grand public européen : Hivenet (Série A, suisse, app Android existe)
- Stockage souverain entreprise européen : Cubbit (19,7M$, Italie, S3/entreprise uniquement)
- Cloud souverain traditionnel algérien : AYRADE (IPO 2026, data center centralisé)
- IaaS pan-africain : UniCloud Africa (nouvel entrant, pas d'Algérie)
- Stockage crypto-décentralisé : Storj, Filecoin (développeur/entreprise, pas de mobile, pas d'Afrique)

Aucun concurrent unique n'occupe l'intersection de : (1) P2P mobile-natif, (2) souveraineté des données sur territoire algérien, (3) dynamique de vente institutionnelle B2G.

### Gaps dans le Marché (Ce qu'Aucun Concurrent ne Fait Bien)

1. **Stockage P2P mobile-natif qui exploite les appareils Android des utilisateurs finaux comme couche de stockage** — zéro concurrent fait cela [Opinion]
2. **Stockage P2P/distribué conforme au territoire algérien** — AYRADE est de l'IaaS centralisé, pas du P2P [Données]
3. **Stockage offline-capable / réseau local** (transferts WiFi LAN sans internet) — aucun concurrent n'offre cela [Hypothèse basée sur la revue produit]
4. **Stockage à prix grand public avec conformité institutionnelle B2G** — le marché est bifurqué entre apps grand public (Hivenet, faible confiance) et deals entreprise (Cubbit, pas de mobile) [Opinion]
5. **Transport chiffré basé relay sur WebSocket pour P2P cross-network sur mobile** — aucun concurrent public n'a productisé cela pour le contexte institutionnel algérien [Opinion]

### Opportunité de Positionnement de MobiCloud

MobiCloud occupe une **position structurellement unique** :

1. **Moat légal** : La Loi 11-25 (juillet 2025) + Décrets 25-320/321 + 26-07 + ARPCE Décision 48 de l'Algérie imposent le stockage des données sur territoire algérien pour les institutions publiques. Aucun hyperscaler n'a d'infrastructure sur territoire algérien. AYRADE l'a mais est de l'IaaS centralisé. Le serveur relay de MobiCloud peut être hébergé en Algérie, avec les données restant sur les appareils algériens — une interprétation légalement défendable de la résidence des données. [Données + Opinion]

2. **Zéro capex pour les institutions** : Les appareils Android existants du personnel deviennent les nœuds de stockage. Pas d'acquisition de racks serveur. Pour un hôpital public ou une université avec 200 appareils Android et un budget IT contraint, le modèle de MobiCloud pourrait coûter dramatiquement moins que la colocation AYRADE. [Opinion]

3. **Story grand public pour plus tard** : Hivenet prouve qu'il y a un appétit pour le stockage mobile distribué (segment grand public), mais Hivenet ne peut pas entrer en Algérie légalement sans nœuds algériens. MobiCloud commence en B2G, construit la distribution, puis capture le segment grand public depuis une position souveraine. [Opinion]

### Évaluation du Moat Concurrentiel

| Type de Moat | Force | Notes |
|---|---|---|
| Légal/Réglementaire | **Fort** | La Loi 11-25 est une barrière dure pour les concurrents non algériens [Données] |
| Technologie | **Moyen** | Le relay P2P + la topologie super-peer est non triviale mais réplicable [Opinion] |
| Effets de Réseau | **Moyen** | Plus d'appareils = plus de stockage = coût plus faible par institution [Opinion] |
| Connaissance du Marché Local | **Fort** | Construit en Algérie, UX arabe/français, compréhension des cycles de marchés publics [Hypothèse] |
| Premier Arrivant en Algérie | **Fort** | Aucun produit comparable n'existe aujourd'hui [Opinion] |
| Distribution | **Faible (aujourd'hui)** | Pas encore de relations de vente institutionnelle ; doit construire vs. les 10K clients d'AYRADE [Données] |

---

## Lacunes de Données

- **Hivenet** : Nombre total d'utilisateurs enregistrés non divulgué ; MAU de l'app Android inconnus ; s'ils ont des clients entreprise/B2G [Inconnu]
- **Cubbit** : Tarification exacte par To pour DS3 Cloud non publique ; aucune information sur d'éventuels plans d'expansion Afrique/MENA [Inconnu]
- **AYRADE** : Tarification par VM/To non publique ; s'ils ont signé des contrats avec des ministères ou universités spécifiquement (vs. banques/énergie) n'est pas clair [Inconnu]
- **AventureCloudz** : Adoption utilisateur, tarification, s'ils ont des clients institutionnels non-développeurs [Inconnu]
- **UniCloud Africa** : Détails de financement, tarification exacte, probabilité du timing d'expansion en Algérie [Inconnu]
- **Marché** : Aucune donnée fiable sur combien d'institutions publiques algériennes ont signé des contrats cloud de quelque type que ce soit — les 600-700 cibles institutionnelles est une estimation, pas un univers adressable mesuré [Estimation selon le brief Wave 1]
- **Application réglementaire** : Si l'ARPCE Décision 48 est activement appliquée (contrats refusés aux fournisseurs non conformes) ou actuellement aspirationnelle — cela affecte matériellement la force du moat [Inconnu]

---

*Sources utilisées : Hivenet.com, Cubbit.io, Crunchbase, Tracxn, G2, Trustpilot, Gartner Peer Insights, TechAfrica News, Ecofin Agency, DealRoom, AlgeriaTech.news, Ecofinagency.com, DevTeam.Space, Villpress, Algerianewsgate.com — juin 2026.*
