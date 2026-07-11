# Vérification Pré-Flight

**Phase :** 0.5 — Vérification Pré-Flight
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (recherche web, pas de sources primaires)

---

## Scan de l'Incumbent Dominant

**Hivenet** (Suisse, fondé en 2022) — App Android sur Google Play réalisant du stockage distribué via fragmentation en nœuds-appareils. Analogue structurel le plus proche trouvé. Distinctions : pas de focus Afrique, pas d'angle souveraineté/localisation, pas de fonctionnement en groupe local hors ligne, serveurs basés en UE. Financé mais pas dominant. [Données, Crunchbase / fiche Google Play]

**Cubbit** (Italien) — Stockage souverain géo-distribué B2B. Utilise la plateforme DS3 Composer ciblant les entreprises, pas le grand public Android. Aucune présence en Afrique. [Données, site web de l'entreprise / Crunchbase]

**Évaluation :** Aucune app de stockage P2P grand public, téléphone-à-téléphone, groupe de confiance, sans crypto, focalisée Afrique n'a été trouvée. La niche spécifique semble réellement inoccupée. [Opinion]

## Scan des Échecs Précédents

Aucun échec documenté d'une startup de stockage mobile P2P trouvé. Des projets académiques (Blackbox, MIT 2014) ont tenté le concept mais ne l'ont jamais commercialisé. L'absence de cimetière d'échecs signifie que le concept est sous-exploré au niveau grand public — ce qui valide le gap mais retire une source d'apprentissage sur ce qui ne fonctionne pas. [Données, recherche web]

## Scan Réglementaire / Légal

**Vent favorable fort :**
- Loi n° 11-25 (juillet 2025) — cadre algérien de protection des données modernisé. Introduit des exigences de DPO, des délais de notification de violation, des obligations de DPIA.
- Décret présidentiel n° 25-321 (déc. 2025) — Stratégie nationale de cybersécurité 2025–2029 pour les administrations publiques.
- Décret présidentiel n° 26-07 (jan. 2026) — cadre opérationnel de cybersécurité pour les institutions publiques.

La posture de souveraineté des données de l'Algérie est soutenue par une législation active, pas seulement par une rhétorique politique.

**Risque :**
- La loi algérienne de 2018 interdit les exports de données menaçant la « sécurité publique ». Le serveur relay sur Render (basé aux US) route les données à travers une infrastructure US. Même avec un chiffrement de bout en bout, cela peut entrer en conflit avec les exigences de marchés institutionnels. Le relay doit être hébergé dans le pays ou dans une juridiction équivalente-UE avant de poursuivre des contrats du secteur public.

## Verdict

Aucun signal instantanément disqualifiant. Procéder à l'intake.

**Risque structurel immédiat à traiter :** la localisation géographique du serveur relay avant les ventes B2G.

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun identifié au stade pré-flight.

**Drapeaux Jaunes :**
- Hivenet existe et a une app Android — la différenciation doit être explicite et défendable.
- Le serveur relay sur infrastructure US entre en conflit avec les exigences de localisation des données algériennes pour les clients institutionnels.
- Aucune startup de stockage mobile P2P n'a réussi à l'échelle grand public — absence de précédent, pas preuve de faisabilité.

## Sources
- [Hivenet sur Google Play](https://play.google.com/store/apps/details?id=com.hivenet.android.hivedisk) — Tier 2
- [Site web de Cubbit](https://www.cubbit.io/) — Tier 2
- [Digital Policy Alert — Algérie 2025](https://digitalpolicyalert.org/digest/dpa-digital-digest-algeria) — Tier 1
- [TechAfrica News — Cybersécurité Algérie 2026](https://techafricanews.com/2026/01/26/algeria-strengthens-cybersecurity-framework-to-protect-national-infrastructure/) — Tier 2
- [CMS Expert Guide — Protection des Données Algérie](https://cms.law/en/int/expert-guides/cms-expert-guide-to-data-protection-and-cyber-security-laws/algeria2) — Tier 1
