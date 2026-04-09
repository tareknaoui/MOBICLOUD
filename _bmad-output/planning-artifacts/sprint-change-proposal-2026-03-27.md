# Sprint Change Proposal

**Date :** 2026-03-27
**Projet :** MobiCloud

## Section 1: Résumé du Problème (Issue Summary)
Ce rapport de changement de sprint a été initié à la suite de la détection de deux failles majeures dans les étapes de préparation (Implementation Readiness) :
1. **Rupture de Traçabilité Structurelle :** Déconnexion critique entre les 62 exigences granulaires du PRD (FR-01.1 à FR-10.4) et le document `epics.md` qui restait bloqué sur l'ancien standard de 11 exigences haut niveau (FR1 à FR11).
2. **Vulnérabilités de Scalabilité (Correctifs v2.1) :** Les Epics ne reflétaient pas l'architecture prévue pour supporter 100 000 utilisateurs, omettant des défenses critiques comme la Preuve de Stockage (PoR), le partitionnement de la DHT (Catalogues), l'interdiction du routage de données multi-sauts (Single-Hop) pour économiser la batterie, le recalibrage anti-Sybil (Hashcash ~1s) et le facteur correctif d'Hystérésis (15%).

## Section 2: Analyse d'Impact (Impact Analysis)
- **PRD & Architecture :** Ces documents étaient déjà à jour et intégraient correctement les règles d'architecture et exigences v2.1. Aucun ajustement structurel nécessaire sur ces deux artefacts.
- **Epics & Stories (`epics.md`) :** Fort impact. L'ensemble des Epics 1, 2, 4, 5 et 7 nécessitaient des mises à jour ou des ajouts directs de User Stories.
- **Impact Technique :** Fort impact conceptuel mais faible impact sur la base de code existante si cette révision se fait AVANT l'implémentation. Les ajouts empêcheront l'effondrement OOM (Out-of-Memory) ou l'épuisement de batterie.

## Section 3: Approche Recommandée (Recommended Approach)
**Restauration par Ajustement Direct (Option 1).**
L'approche retenue consiste à mettre à jour dynamiquement `epics.md` (Effectué). Il n'a pas été nécessaire de réécrire le PRD ni l'Architecture, ni de repenser le MVP de l'application, car seul le Backlog (Epics) était désynchronisé de la réalité de conception.

## Section 4: Propositions de Changement Détaillées (Changes Implemented)
Tous les changements ont été validés itérativement par le Scrum Master et le Product Owner :
- **Traceability Map :** Remplacement complet de la nomenclature FR1-FR11 par l'arborescence complète FR-01 à FR-10.
- **Epic 1 :** Mise à jour de la Story 1.2 (Calibrage Hashcash à ~1s) et ajout de la Story 1.5 (Backoff Progressif).
- **Epic 2 :** Remaniement de la Story 2.1 (DHT Partitionnée stricte) et ajout de la Story 2.5 (Garbage Collector et TTL du catalogue).
- **Epic 4 :** Ajout de la Story 4.4 interdisant de manière logicielle et au niveau TCP le routage multi-sauts des fragments de données.
- **Epic 5 :** Correction critique de la formule mathématique Bully de la Story 5.1 (remplacement d'une augmentation additive de 15 par un seuil multiplicatif relatif `1.15`).
- **Epic 7 :** Ajout de la Story 7.3 imposant la "Proof of Retrievability" (PoR) aléatoire via challenger.

## Section 5: Plan de Passation (Implementation Handoff)
- **Portée du Changement :** **Moderée / Majeure.** Bien qu'il s'agisse de correctifs de backlog, l'omission de ces éléments de code aurait condamné le projet lors des stress-tests.
- **Passation PM/Architecture :** Terminé. Les fondations sont saines.
- **Passation Développement :** L'Agent Développeur (Amelia / Barry) doit utiliser exclusivement le nouveau document `epics.md` mis à jour ce 2026-03-27 pour l'implémentation des algorithmes P2P. La couverture est désormais de 100%.
