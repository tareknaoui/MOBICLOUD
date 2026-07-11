# Paysage Concurrentiel

**Phase :** 3 — Recherche de Marché (Synthèse)
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (les données Hivenet/Cubbit sont bonnes ; les données AYRADE sont limitées par l'opacité)

---

## Vue d'Ensemble Concurrentielle

Le paysage concurrentiel de MobiCloud se divise en deux couches de menace distinctes :

**Couche 1 — Analogues produit (mauvais marché) :** Hivenet et Cubbit résolvent des problèmes techniques similaires (stockage distribué et chiffré) mais sur des marchés complètement différents (consommateurs européens, entreprises UE). Ils posent une faible menace directe en Algérie mais fournissent le benchmark concurrentiel par rapport auquel le produit de MobiCloud sera évalué par des acheteurs avertis.

**Couche 2 — Incumbents du marché (bon marché, mauvais produit) :** AYRADE et CERIST servent les institutions algériennes et détiennent les relations institutionnelles. Ils n'offrent pas de produit mobile-natif. C'est le gap de MobiCloud — mais aussi son plus grand risque, car une expansion d'AYRADE dans l'accès mobile-natif fermerait entièrement le gap.

**Concentration du marché :** Fragmenté à l'échelle mondiale ; localement dominé par AYRADE. L'intersection spécifique de *mobile-natif + souveraineté algérienne + B2G* est **inoccupée**. [Opinion, soutenu par toute la recherche concurrentielle]

---

## Matrice de Comparaison Concurrentielle

| | **MobiCloud** | **Hivenet** | **Cubbit** | **AYRADE** | **Nextcloud** | **Statu Quo** |
|---|---|---|---|---|---|---|
| **Localisation données** | Sur appareils utilisateurs + relay algérien | Serveurs Hivenet (UE) | Défini par client (UE/UK) | Data centers algériens | Auto-hébergé (n'importe où) | USB/cloud étranger |
| **Mobile-natif** | ✅ Android-first | ✅ App Android | ❌ Pas d'app grand public | ❌ Pas d'app mobile | ⚠️ Client mobile uniquement | N/A |
| **Architecture P2P** | ✅ Vrai P2P (erasure coded) | ✅ Nœuds distribués | ❌ Centralisé sur infra client | ❌ DC centralisé | ❌ Serveur-client | N/A |
| **Conformité algérienne** | ✅ (quand relay migré) | ❌ Serveurs UE | ❌ UE/UK uniquement | ✅ Native | ✅ Si auto-hébergé en DZ | ❌ Cloud étranger |
| **Facturation DZD** | ✅ (requise pour B2G) | ❌ EUR uniquement | ❌ EUR uniquement | ✅ DZD | ✅ Logiciel libre | N/A |
| **Pas de serveur requis** | ✅ (le relay ne fait que router) | ❌ Nœuds sur leur infra | ❌ Nécessite infra serveur | ❌ Nécessite DC | ❌ Nécessite serveur | — |
| **B2G institutionnel** | ✅ Cible | ❌ Non positionné | ✅ Mais UE uniquement | ✅ Incumbent | ⚠️ Possible mais DIY | — |
| **Prix (mensuel)** | 200-500 DZD (~2 $) | 8-17 €/mois | Entreprise sur devis | Devis uniquement DZD | Gratuit + coût support | ~0 (USB) |
| **Financement** | 0 $ (académique) | 12M€ Série A | 19,7M$ total | Société cotée | Open source | — |
| **Présence algérienne** | ✅ Locale | ❌ Aucune | ❌ Aucune | ✅ Dominante | ❌ Aucune connue | Omniprésente |

---

## Profils Concurrents Individuels

### AYRADE (Algérie — le critique)
- **Stade :** Post-IPO (juin 2026), société d'infrastructure en croissance
- **Revenu :** 3M€ (2025), croissance de 117 % en glissement annuel [Données, documents investisseurs]
- **Clients :** 10 000+ institutions [Données]
- **Produit :** IaaS centralisé — 2 data centers, cloud traditionnel basé serveur. Pas d'app mobile, pas de P2P, pas de client Android.
- **Force :** Détient les relations institutionnelles. Légalement conforme par défaut. Facturation DZD. Confiance gouvernementale.
- **Faiblesse :** L'architecture centralisée nécessite une infrastructure serveur coûteuse que les clients doivent provisionner ; pas d'offre mobile-native ; pas de résilience P2P.
- **Niveau de menace : Moyen — et à double tranchant.** AYRADE est simultanément :
  - *Le concurrent à éviter :* S'ils lancent une app mobile compagnon, le gap de MobiCloud se ferme.
  - *Le partenaire à poursuivre :* Les 10 000 clients institutionnels d'AYRADE pourraient devenir le canal de distribution de MobiCloud. Un partenariat où MobiCloud fournit un accès P2P mobile-natif au-dessus de la couche serveur d'AYRADE est mutuellement bénéfique — AYRADE obtient une fonctionnalité produit, MobiCloud obtient un accès client instantané.

### Hivenet (Suisse — analogue produit le plus proche)
- **Financement :** 12M€ Série A [Données, Crunchbase]
- **Produit :** Stockage distribué via nœuds-appareils, app Android, chiffré E2E. Focalisé grand public.
- **Prix :** 0,01 €/Go, 5 To = ~16,50 €/mois
- **Faiblesses documentées [Données, avis Trustpilot/Play Store] :**
  - Échecs d'upload silencieux (le fichier semble sauvegardé, ne l'est pas)
  - « Fichier introuvable » à la tentative de téléchargement
  - Incohérence multi-appareils
  - Performance d'upload lente
  - Des utilisateurs le décrivant comme une « arnaque » après des événements de perte de données
- **Présence algérienne :** Zéro. Facturation EUR uniquement — structurellement inaccessible à la plupart des utilisateurs algériens.
- **Niveau de menace : Moyen (similarité produit) / Faible (marché).** Hivenet valide le concept technique. Leurs échecs sont exactement ce que l'erasure coding + la preuve cryptographique de récupération de MobiCloud adressent.

### Cubbit (Italie — benchmark entreprise)
- **Financement :** 19,7M$ total [Données, Crunchbase]
- **Produit :** Stockage souverain géo-distribué B2B (DS3 Composer), compatible S3. 400+ clients entreprise européens dont Leonardo (défense).
- **Pas d'app grand public.** Pas de présence en Afrique. Prix entreprise sur mesure.
- **Niveau de menace : Faible maintenant / Moyen (Année 3+)** s'ils s'associent à un opérateur de data center algérien (ex : AYRADE). C'est le scénario à surveiller.

### Nextcloud (Open Source — auto-hébergé)
- **Produit :** Serveur de fichiers auto-hébergé avec client mobile. Satisfait réellement les exigences de souveraineté nationale.
- **Pourquoi les institutions ne l'utilisent pas :** Nécessite un serveur, un administrateur serveur, une maintenance continue. La plupart des institutions algériennes manquent d'une équipe d'infrastructure IT capable de le faire tourner de façon fiable. [Données, recherche voix du client — les avis Nextcloud citent systématiquement « trop technique » comme barrière principale]
- **Support entreprise :** 68–205 €/utilisateur/an (min 100 utilisateurs)
- **Niveau de menace : Moyen.** Une institution avec une équipe IT compétente et un serveur existant pourrait choisir Nextcloud plutôt que MobiCloud. Le pitch de MobiCloud contre Nextcloud : pas de serveur requis, mobile-natif, fonctionne sur les téléphones que les employés possèdent déjà.

### CERIST / Cloud d'État
- Réseau national de recherche et de technologie. Sert 80+ institutions. Centralisé, opéré par le gouvernement.
- Les institutions hors du réseau CERIST n'ont aucune alternative conforme pour le stockage de fichiers.
- Niveau de menace : Faible (contrainte de marché, pas concurrent produit — il crée le marché cible de MobiCloud).

---

## Carte de Positionnement

```
                        MOBILE-NATIF
                              ↑
                         MobiCloud
                       (position cible)
                              |
CENTRALISÉ ←————————————————|————————————————→ DISTRIBUÉ/P2P
                              |
      AYRADE    CERIST        |      Hivenet (pas Algérie)
      Nextcloud               |      Cubbit (pas mobile)
                              |
                              ↓
                      DÉPENDANT DU SERVEUR
```

La position de MobiCloud — mobile-natif + distribué/P2P — est inoccupée sur le marché algérien. Les produits les plus proches (Hivenet, Cubbit) sont distribués/P2P mais pas en Algérie, et pas mobile-natifs au sens institutionnel. AYRADE est en Algérie mais centralisé et dépendant du serveur.

---

## Analyse de Vulnérabilité — Où Gagner

| Concurrent | Faiblesse Exploitable | Attaque de MobiCloud |
|---|---|---|
| AYRADE | Pas de produit mobile. Les clients ont besoin d'un accès mobile de terrain. | Partenariat plutôt que concurrence — offrir une couche mobile sur leur infrastructure. Le premier client de référence est un client AYRADE. |
| Hivenet | Échecs de fiabilité documentés (upload silencieux, « fichier introuvable »). Inaccessible aux utilisateurs algériens (facturation EUR). | Démontrer la fiabilité de l'erasure coding en comparaison directe. La facturation DZD est une victoire immédiate. |
| Nextcloud | Nécessite serveur + équipe technique. La plupart des institutions algériennes ne peuvent pas le faire tourner. | « Pas de serveur requis. Fonctionne sur vos téléphones Android existants. » |
| Statu quo (USB/Google Drive) | Google Drive est non conforme (Loi 11-25). Les clés USB tombent en panne. | Cadrage conformité : « Google Drive est désormais illégal pour vos données. MobiCloud ne l'est pas. » |

---

## Évaluation du Risque Plateforme

| Plateforme | Risque | Calendrier | Probabilité | Mitigation |
|---|---|---|---|---|
| AYRADE lançant une app mobile | Élevé (ils ont les clients, la conformité, la confiance) | 12-24 mois | Moyenne | S'associer à AYRADE avant qu'ils ne le construisent en interne |
| Data center algérien Google/Microsoft | Élevé si ça arrive | 3-5 ans (meilleure estimation) | Faible-Moyenne | Utiliser la fenêtre de 18-36 mois agressivement |
| Gouvernement algérien imposant CERIST | Moyen | Inconnu | Faible | Le gouvernement n'impose typiquement pas aux acteurs privés |
| Partenariat Cubbit-AYRADE | Moyen | 18-36 mois | Faible | Premier arrivant sur les relations institutionnelles |

**Risque le plus urgent : AYRADE** — le risque plateforme qui pourrait fermer la fenêtre de MobiCloud n'est pas un hyperscaler ; c'est l'incumbent algérien.

---

## Analyse des Coûts de Switching

**Institutions B2G (coût de switching ÉLEVÉ une fois déployé) :**
- Migration des données : extraire et re-chiffrer des fichiers distribués est complexe
- Ré-audit de conformité : changer de fournisseur nécessite une nouvelle évaluation de sécurité sous le Décret 26-07
- Reformation du personnel : personnel IT institutionnel formé aux protocoles MobiCloud
- **C'est un moat :** une fois qu'une institution déploie MobiCloud, le switching est coûteux. Gagner le premier contrat ; la rétention suit.

**Utilisateurs B2C (coût de switching FAIBLE) :**
- Si le cluster se casse (critère d'arrêt fragilité du cluster), les utilisateurs désinstallent simplement
- Aucun mécanisme de verrouillage n'existe sans un uptime et une fiabilité soutenus
- **C'est un risque :** la rétention B2C dépend entièrement du produit fonctionnant de façon fiable en conditions réelles — jamais testé.

---

## Recommandations Stratégiques

1. **Poursuivre le partenariat AYRADE avant de concurrencer.** Les 10 000 clients institutionnels d'AYRADE + la couche P2P mobile de MobiCloud = un produit qu'AYRADE n'a pas et dont les institutions ont besoin. Le pitch de partenariat : « Nous fournissons une sauvegarde distribuée mobile-native pour vos clients existants. Vous fournissez la crédibilité de conformité et la distribution. Nous partageons le revenu. » Cela évite le cycle de vente institutionnel solo de 12-24 mois.

2. **Concurrencer la complexité de Nextcloud, pas ses fonctionnalités.** Le pitch est la simplicité : « Pas de serveur. Pas d'équipe IT. Juste les téléphones que vos employés possèdent déjà. »

3. **Utiliser les échecs de Hivenet comme points de preuve.** Leurs problèmes de fiabilité documentés sont publics (Trustpilot, Play Store). Une démo montrant la récupération de fichiers quand un téléphone passe hors ligne vaut plus que n'importe quel slide.

4. **Ne pas concurrencer sur le prix avec le statu quo (USB).** Le cadrage conformité (« Google Drive est désormais illégal pour les données de votre institution ») crée une urgence à laquelle les clés USB ne peuvent pas répondre.

---

## Drapeaux

**Drapeaux Rouges :**
- AYRADE est le plus grand risque unique. S'ils lancent un produit mobile compagnon, le gap institutionnel B2G de MobiCloud se ferme. Ce risque n'est ni surveillé ni mitigé.

**Drapeaux Jaunes :**
- Cubbit sert déjà un client défense (Leonardo, 14 Md€). S'ils s'associent à un hébergeur algérien, ils entrent sur le marché immédiatement avec une crédibilité entreprise.
- Toute l'intelligence concurrentielle sur AYRADE est limitée — ils ne sont pas transparents sur les prix, la roadmap ou les plans produit.

## Lacunes de Données
- La roadmap produit et les plans mobiles d'AYRADE (inconnus — ils sont discrets là-dessus)
- Les plans d'expansion en Afrique de Cubbit (non disponibles publiquement)
- Les déploiements institutionnels algériens confirmés de Nextcloud (aucun trouvé, mais peuvent exister)
- Le taux de churn et la part de marché réelle de Hivenet (non publics)

## Sources
- Recherche brute Wave 2 : `01-discovery/raw/direct-competitors.md`, `01-discovery/raw/indirect-competitors-gtm.md`
- Avis Hivenet Trustpilot / Google Play Store — Tier 3
- Site web et Crunchbase de Cubbit — Tier 2
- Documents investisseurs et presse AYRADE — Tier 2
