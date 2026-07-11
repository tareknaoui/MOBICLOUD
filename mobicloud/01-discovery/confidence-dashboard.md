# Tableau de Bord de Confiance

**Phase :** 3 — Synthèse de Recherche
**Projet :** mobicloud
**Date :** 2026-06-21
**Objectif :** Méta-couche — indique au fondateur où la recherche repose sur un sol solide vs. de la glace mince. À lire avant de prendre des décisions basées sur les 4 autres documents de discovery.

---

## Vue d'Ensemble

La recherche s'est appuyée sur 6 agents à travers 3 waves (Wave 4 sautée — marché mono-pays). La qualité des sources est la plus forte pour les conclusions réglementaires (documents gouvernementaux Tier 1), modérée pour l'intelligence concurrentielle (presse/Crunchbase Tier 2), et la plus faible pour la validation de la demande grand public (proxys Tier 3 d'Afrique du Sud/Nigeria — pas l'Algérie). La thèse B2G repose sur un sol légal solide ; la thèse B2C repose sur l'inférence.

---

## Tableau de Confiance au Niveau des Affirmations

| Affirmation | Tier Source | Sources Corroborantes | Confiance | Âge des Données |
|---|---|---|---|---|
| ARPCE Décision 48 impose aux opérateurs cloud d'héberger sur territoire algérien | 1 | 2 (ARPCE + guide juridique CMS) | **Élevée** | 2017 (opérative) |
| La Loi 11-25 crée une responsabilité pénale pour le transfert transfrontalier non autorisé de données | 1 | 3 (DPA, APA News, CookieYes) | **Élevée** | Juillet 2025 |
| Le Décret 26-07 impose des unités de cybersécurité dans toutes les institutions publiques | 1 | 2 (TechAfrica News, journal officiel) | **Élevée** | Janvier 2026 |
| Aucun hyperscaler n'a d'infrastructure sur territoire algérien en date de juin 2026 | 2 | 2 (recherche Wave 1 + recherche GTM Wave 2) | **Élevée** | Juin 2026 |
| AYRADE a 10 000+ clients institutionnels et a fait son IPO en juin 2026 | 2 | 2 (documents investisseurs + presse) | **Élevée** | Juin 2026 |
| Le revenu d'AYRADE a crû de 117 % en glissement annuel | 2 | 1 (documents investisseurs) | **Moyenne** | Données 2025 |
| Hivenet a des échecs de fiabilité documentés (upload silencieux, « fichier introuvable ») | 3 | Multiples (avis Trustpilot + Play Store) | **Élevée** (pour l'affirmation ; source Tier 3) | 2025-2026 |
| Hivenet a levé 12M€ Série A | 2 | 1 (Crunchbase) | **Moyenne** | 2024-2025 |
| Marché cloud public Afrique 15,55 Md$ (2025), 23,3 % TCAC | 2 | 2 (Statista + Grand View Research) | **Moyenne** | 2025 |
| Total services IT Algérie ~1,9 Md$ | 2 | 1 (proxy IDC) | **Moyenne** | 2025 |
| Marché du stockage cloud Algérie 80M–150M $ | Dérivé | 0 (estimation calculée) | **Faible** | Dérivé |
| SAM B2G : 1,2M–3,0M $ ARR au plafond de pénétration | Dérivé | 0 (calcul fondateur depuis Wave 1) | **Faible** | Dérivé |
| SOM Année 3 : 200K–400K $ | Dérivé | 0 (trajectoire de sociétés comparables) | **Faible** | Projeté |
| Les startups algériennes ont levé 4,1 Md$ en 2025 | 2 | 1 (presse tech africaine) | **Moyenne** | 2025 |
| Le fonds Algerie Telecom de 11M$ cible la cybersécurité/IA | 2 | 1 (communiqué de presse) | **Moyenne** | 2025 |
| Cycle de vente B2G : 12–24 mois | 2 | 2 (US Commercial Service + recherche marchés publics Algérie) | **Moyenne** | 2024-2025 |
| TikTok a 21,1M d'utilisateurs algériens | 2 | 1 (DataReportal 2025) | **Moyenne** | 2025 |
| CAC grand public quasi-nul via TikTok/WhatsApp organique | 3 | 1 (inféré de la densité des canaux) | **Faible** | 2025 |
| WTP B2C : 200–500 DZD/mois | Dérivé | 0 (ancres Spotify/Coursera comme proxy) | **Faible** | 2025 |
| Aucune citation verbatim d'utilisateurs algériens grand public obtenue | N/A | N/A | **Lacune vérifiée** | Juin 2026 |
| L'article IEEE confirme le gap de recherche de MobiCloud | 1 | 1 (MDPI / IEEE Xplore) | **Élevée** | 2024-2025 |
| La facturation DZD élimine la plupart des concurrents étrangers (pas d'accès carte EUR/USD) | 2 | 2 (données comportement de paiement + recherche plateforme) | **Moyenne** | 2025 |

---

## Conclusions à Plus Haute Confiance

Ces affirmations sont soutenues par des sources Tier 1 avec de multiples signaux corroborants. Construire la stratégie sur celles-ci.

1. **Le moat légal est réel et récent.** ARPCE Décision 48 + Loi 11-25 + Décret 26-07 interdisent collectivement aux institutions publiques algériennes d'utiliser un stockage cloud étranger non conforme. Ce sont des instruments d'application, pas des déclarations politiques. Les dispositions de responsabilité pénale (1-5 ans de prison) rendent le risque réel pour les directeurs d'institutions. [Multiples sources Tier 1, 2017-2026]

2. **Aucun hyperscaler ne concurrencera en Algérie pendant 18–36 mois.** Aucune infrastructure sur territoire algérien annoncée par Google, Microsoft ou AWS en date de juin 2026. La fenêtre est réelle. [Tier 2, recoupé sur de multiples sources]

3. **AYRADE est à la fois la menace primaire et l'opportunité de partenariat primaire.** Ils sont réels, financés, en croissance, et détiennent les relations clients institutionnelles dont MobiCloud a besoin. Leur gap produit (pas d'offre mobile-native) est le point d'entrée. [Tier 2, bien documenté]

4. **Les échecs documentés de Hivenet sont exactement le problème que l'erasure coding de MobiCloud résout.** Les échecs d'upload silencieux et les erreurs « fichier introuvable » sont le résultat de l'absence d'intégrité de fragments cryptographiquement vérifiable. RS(2,1) + Android Keystore adresse directement ceci. [Source Tier 3 pour les avis, mais les patterns sont cohérents sur de multiples évaluateurs indépendants]

5. **L'approche technique de MobiCloud a une validation académique.** L'article IEEE confirme que le gap mobile-natif + connectivité intermittente que MobiCloud comble est une véritable contribution de recherche, pas une fonctionnalité incrémentale. [Tier 1, 2024-2025]

---

## Conclusions à Plus Faible Confiance

Ces affirmations sont des estimations, des proxys ou des sources uniques. Ne pas prendre de décisions irréversibles basées sur celles-ci sans validation supplémentaire.

1. **Toutes les données de demande grand public B2C proviennent d'Afrique du Sud et du Nigeria, pas de l'Algérie.** La fréquence de perte de fichiers, les niveaux de WTP et les comportements d'adoption sont extrapolés de marchés comparables. L'Algérie peut être significativement différente.

2. **Les chiffres SAM et SOM sont des estimations calculées, pas de la recherche de marché.** Le plafond SAM B2G de 1,2M–3,0M $ est dérivé du nombre d'institutions × ACV estimée. Aucun benchmark SaaS B2G Algérie publié n'existe pour valider la fourchette d'ACV.

3. **La WTP grand public à 200-500 DZD/mois est une estimation proxy.** Les prix de Spotify et Coursera sont utilisés comme ancres. Les utilisateurs peuvent ne pas percevoir la sauvegarde de fichiers comme ayant une valeur similaire aux abonnements de divertissement ou d'éducation. Cela nécessite une expérience de consentement à payer directe.

4. **Le cycle de vente B2G de 12–24 mois** est modélisé à partir de recherches générales sur les marchés publics algériens. Le cycle réel pour un pilot gré à gré sous le seuil d'appel d'offres peut être plus court (3–6 mois) si un champion interne est sécurisé. Ou plus long si l'institution n'a pas d'urgence de conformité.

5. **La facturation DZD comme moat structurel.** C'est exact aujourd'hui mais cela pourrait changer si l'infrastructure de paiement algérienne se modernise ou si des concurrents étrangers lancent des paiements libellés en DZD.

---

## Inconnues Critiques

Choses que la recherche n'a pas pu répondre et qui pourraient changer matériellement la stratégie :

| Inconnue | Pourquoi C'est Important | Comment le Découvrir |
|---|---|---|
| AYRADE planifie-t-il un produit mobile ? | Si oui, le gap B2G de MobiCloud se ferme. | Suivre les communications investisseurs d'AYRADE ; contacter AYRADE pour un appel exploratoire de partenariat. |
| Le relay peut-il être hébergé sur infrastructure algérienne à un coût viable ? | La migration du relay est un prérequis pour toutes les ventes B2G. | Obtenir des devis auprès d'hébergeurs algériens locaux (Algerie Telecom, CERIST commercial, Djezzy). |
| Que pense un vrai DSI algérien du pitch de MobiCloud ? | Toute la validation B2G est inférée, pas testée. | 5 entretiens DSI (université, hôpital, ministère — au moins un de chaque). |
| Les vrais utilisateurs gardent-ils l'app après la semaine 1 ? | Le critère d'arrêt. | Déployer sur 1 groupe de résidence (10+ utilisateurs) pendant 30 jours et mesurer le taux actif quotidien. |
| Les institutions algériennes peuvent-elles payer par virement bancaire DZD, pas par carte de crédit ? | Affecte le mécanisme de collecte de revenu, pas seulement le prix. | Demander à une startup algérienne ayant vendu aux institutions comment elles gèrent le paiement. |
| Quel est le prix d'AYRADE pour des services comparables ? | Nécessaire pour ancrer le prix institutionnel de MobiCloud. | Demander un devis à AYRADE en tant qu'acheteur potentiel. |

---

## Recommandations — Ce qu'il Faut Vérifier en Premier

**Semaine 1 (avant toute autre chose) :**
1. Obtenir un devis d'un hébergeur algérien pour les coûts de serveur relay (Algerie Telecom, OVH Algeria si disponible, data center local). Cela détermine si la migration du relay est économiquement viable.
2. Trouver un contact à l'intérieur d'un département IT d'université algérienne (LinkedIn : rechercher « DSI Algérie université »). Demander une conversation de 20 minutes, pas un appel commercial.

**Semaines 2-4 :**
3. Lancer le test de groupe de résidence de 30 jours (10+ utilisateurs, vrais téléphones, vrais fichiers). Mesurer la rétention. Cela teste directement le critère d'arrêt.
4. Contacter AYRADE pour un appel exploratoire de partenariat. Le cadrer comme « nous avons construit une couche de sauvegarde distribuée mobile-native qui pourrait compléter l'infrastructure d'AYRADE pour l'accès mobile de terrain ».

**Mois 2 :**
5. Demander 5 entretiens DSI formels en utilisant le protocole de customer discovery (Phase 3.7). Demander spécifiquement : « Êtes-vous sous pression de conformité de la Loi 11-25 ? » et « Que faudrait-il pour que vous déployiez une solution de stockage mobile ? »

---

## Drapeaux

**Drapeaux Rouges :**
- La thèse B2C n'a aucune donnée verbatim d'utilisateurs algériens. Elle est entièrement construite sur des marchés proxy. Ne pas investir dans le marketing grand public avant qu'un test d'utilisateurs réels de 30 jours ne confirme la rétention.
- Toutes les estimations SAM/SOM/financières sont à faible confiance. Ne pas les présenter comme des prévisions ; les présenter comme des hypothèses à valider.

**Drapeaux Jaunes :**
- La roadmap d'AYRADE est inconnue. La fenêtre de partenariat peut être plus courte que 18-36 mois s'ils construisent déjà l'accès mobile en interne.

## Sources
- Les 6 fichiers de recherche brute Wave 1-3 dans `01-discovery/raw/`
- Voir les fichiers de discovery individuels pour les citations de sources par affirmation
