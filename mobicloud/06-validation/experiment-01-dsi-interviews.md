# Expérience 1 — Entretiens DSI

**Phase :** 8 — Validation
**Projet :** mobicloud
**Date :** 2026-06-21
**Statut :** Différée depuis Phase 3.7 — priorité maximale dès Mois 1

---

## Objectif

Valider ou invalider les deux hypothèses les plus critiques de la stratégie B2G :

- **H1 :** Les DSI algériens ont une urgence de conformité réelle (Law 11-25 / ARPCE) et cherchent activement une solution.
- **H3 :** Les institutions ont un budget de 500K–2M DZD/an pour résoudre ce problème en dehors du cycle BOMOP.

Ces deux hypothèses soutiennent l'intégralité du modèle économique B2G. Si l'une des deux est fausse, le plan commercial doit être révisé avant toute dépense commerciale.

---

## Participants Cibles

**Nombre :** 5 entretiens minimum — 8 idéaux.

**Profil :**
- Titre : DSI, Directeur des Systèmes d'Information, Responsable Informatique, RSSI
- Institution : université publique, hôpital régional, ministère ou agence gouvernementale
- Localisation : Alger, Oran, Constantine, Annaba (wilaya avec densité institutionnelle)
- Niveau technique : technicien de formation ou non — les deux sont utiles

**Anti-profil à éviter :**
- Entreprises privées (pas concernées par la Law 11-25 de la même façon)
- Institutions qui ont déjà résolu le problème (Nextcloud déployé, CERIST intégré) — peu utiles pour comprendre la demande latente

**Comment les recruter :**
1. LinkedIn — recherche "DSI université Algérie", "responsable informatique hôpital algérien", "RSSI ministère"
2. Réseau personnel du fondateur (contacts académiques, anciens collègues)
3. Contacts obtenus via les professeurs encadrants du PFE (les universités sont la cible principale)

**Message d'invitation :** Voir section "Script de Prise de Contact" ci-dessous.

---

## Format de l'Entretien

**Durée :** 25–35 minutes
**Format :** Visio ou téléphone (pas de déplacement pour les premiers entretiens — optimiser la cadence)
**Enregistrement :** Demander l'accord explicite ; sinon, prendre des notes détaillées pendant l'entretien

**Structure :**

```
0–3 min   : Contexte et accord sur l'enregistrement
3–12 min  : Situation actuelle (questions ouvertes — ne pas mentionner MobiCloud)
12–22 min : Exploration du problème (approfondir les douleurs mentionnées)
22–30 min : Solutions envisagées (ce qu'ils ont déjà essayé ou envisagent)
30–35 min : Présentation de MobiCloud (seulement en fin — après avoir tout écouté)
```

---

## Script d'Entretien

### 0–3 min : Introduction

> *"Bonjour [Prénom], merci de prendre le temps. Je suis [Prénom], je développe une solution de stockage pour les institutions algériennes. Avant de vous parler de ce que je construis, j'aimerais comprendre comment vous gérez la question du stockage et de la conformité dans votre institution — 30 minutes de votre expérience valent plus pour moi que n'importe quelle étude de marché. Ça vous convient si j'enregistre pour ne pas rater de détails ?"*

---

### 3–12 min : Situation actuelle

*Écouter. Ne pas interrompre. Ne pas mentionner MobiCloud ni la Law 11-25 en premier.*

1. **"Pouvez-vous me décrire comment vos équipes stockent et partagent leurs fichiers de travail aujourd'hui ?"**
   - Observer si Google Drive / OneDrive sont mentionnés spontanément.
   - Si oui → noter sans réagir. Si non → "Est-ce qu'ils utilisent des outils cloud ?"

2. **"Qui utilise ces outils — le personnel administratif, les enseignants, les deux ?"**

3. **"Depuis quand utilisez-vous ces solutions ?"**

4. **"Qu'est-ce qui vous a amené à choisir ces outils plutôt que d'autres ?"**

---

### 12–22 min : Exploration du problème

*L'objectif est de faire parler le DSI de ses vraies douleurs — pas de lui suggérer la nôtre.*

5. **"Qu'est-ce qui vous préoccupe dans votre infrastructure actuelle de stockage ?"**
   - Écouter s'il mentionne spontanément : conformité, coût, mobilité, perte de données, dépendance étrangère.

6. **"Est-ce que vous avez entendu parler de la Law 11-25 ?"**
   *(Si non : "C'est la loi algérienne de protection des données personnelles promulguée en juillet 2025 — est-ce qu'elle a un impact sur votre infrastructure ?").*
   - Observer la réaction : urgence, indifférence, méconnaissance.

7. **"Est-ce que votre direction générale ou votre tutelle vous a déjà posé des questions sur la localisation de vos données ?"**

8. **"Si vous deviez migrer hors de Google Drive demain — par obligation ou par choix — qu'est-ce qui vous poserait le plus de problèmes ?"**

9. **"Est-ce qu'il y a eu des incidents liés au stockage dans votre institution — perte de fichiers, accès compromis, audit négatif ?"**
   *(Si oui → approfondir : "Qu'est-ce qui s'est passé ? Comment vous avez géré ?")*

---

### 22–30 min : Solutions envisagées

10. **"Avez-vous déjà évalué des alternatives à votre solution actuelle ?"**
    - Nextcloud, CERIST, AYRADE, autre chose ?

11. **"Qu'est-ce qui vous a empêché de changer ?"**
    - Prix, complexité technique, manque de ressources IT, cycle d'approbation budgétaire ?

12. **"Si une solution conforme à la loi algérienne existait, ne nécessitait pas de serveur de votre côté, et coûtait moins de 3M DZD par an — est-ce que c'est le type de contrat que vous pourriez signer en gré à gré ?"**
    - *[Ne pas encore présenter MobiCloud — observer la réaction sur le principe.]*

---

### 30–35 min : Présentation de MobiCloud (seulement maintenant)

13. **"Je vais vous expliquer en 2 minutes ce que je construis, et j'aimerais votre réaction honnête."**

    > *"MobiCloud distribue les fichiers de votre institution sur les téléphones Android de vos membres — chiffrés, fragmentés, avec un relay hébergé en Algérie. Pas de serveur à gérer de votre côté. Les données ne quittent jamais le sol algérien. Conforme ARPCE Décision 48 et Law 11-25 par architecture."*

14. **"Quelle est votre première réaction ?"**

15. **"Qu'est-ce qui vous sembleraient être les principaux obstacles à déployer quelque chose comme ça chez vous ?"**

16. **"Si ça marchait comme décrit — est-ce que vous seriez intéressé par un pilot de 60 jours ?"**

---

### Clôture

> *"Merci beaucoup. Une dernière question : est-ce que vous connaissez d'autres DSI dans des institutions similaires qui pourraient avoir les mêmes préoccupations ? Je cherche des retours honnêtes, pas des leads commerciaux."*

---

## Script de Prise de Contact (LinkedIn / Email)

**Objet :** *"Gestion du stockage conforme Law 11-25 dans votre institution — 30 minutes de retour d'expérience ?"*

**Corps :**
> *"Bonjour [Prénom],*
>
> *Je développe une solution de stockage mobile destinée aux institutions algériennes, et j'essaie de comprendre comment les DSI gèrent en pratique la question de la conformité Law 11-25 — avant de valider ma solution.*
>
> *Ce n'est pas un rendez-vous commercial. Je cherche à comprendre votre réalité : quels outils vous utilisez, quelles contraintes vous rencontrez, ce qui vous manque. Votre retour d'expérience a plus de valeur pour moi que n'importe quelle étude de marché.*
>
> *Avez-vous 30 minutes disponibles cette semaine ou la suivante pour un échange en visio ?"*

**Ne pas mentionner :** "startup", "application", "prototype", "PFE". Mentionner : "solution", "institution algérienne", "conformité Law 11-25".

---

## Grille d'Analyse des Réponses

*Remplir après chaque entretien.*

| Question | Signal positif (H1 validée) | Signal négatif (H1 invalide) |
|---|---|---|
| Q1 — Outils actuels | Mentionne Google Drive / OneDrive spontanément | "On a déjà Nextcloud / CERIST / AYRADE" |
| Q6 — Law 11-25 | "Oui, on en a entendu parler et ça nous préoccupe" | "Non, jamais entendu" ou "Ça ne nous concerne pas" |
| Q7 — Pression hiérarchique | "Ma direction a déjà posé la question" | "Non, personne n'en parle" |
| Q8 — Migration forcée | "Le plus grand problème serait de trouver une alternative conforme" | "On n'a pas besoin de migrer" |
| Q12 — Gré à gré | "Oui, sous 3M DZD on peut signer sans appel d'offres" | "Non, tout passe par le BOMOP chez nous" |
| Q16 — Pilot | "Oui, je serais intéressé" | "Non, pas maintenant" |

---

## Critères de Succès / Pivot

**GO (H1 + H3 validées) :**
- 4/5 DSI mentionnent Google Drive ou un cloud étranger utilisé actuellement
- 4/5 connaissent la Law 11-25 ou réagissent avec urgence quand on la mentionne
- 3/5 confirment que le gré à gré sous 3M DZD est possible dans leur institution
- 3/5 expriment un intérêt concret pour un pilot

**PIVOT (H1 invalide) :**
- < 2/5 mentionnent une urgence de conformité
- Les DSI connaissent la loi mais ne ressentent pas de pression d'agir
- → Repositionner l'argument : pas "conformité" mais "économies sur abonnements cloud"

**PIVOT (H3 invalide) :**
- Les DSI sont intéressés mais "le budget passe obligatoirement par BOMOP même à 500K DZD"
- → Revoir le modèle : pilot gratuit + facturer uniquement la maintenance annuelle, ou viser les PME privées plutôt que le public

**STOP (recherche d'un autre problème) :**
- 5/5 DSI : "On a déjà résolu ce problème" ou "Ce n'est pas une priorité du tout"
- → La demande B2G n'existe pas comme hypothétisée — revenir à la Phase 2 (Brainstorm)

---

## Livrables Attendus

Après les 5 entretiens :

1. **Synthèse des verbatims** — les phrases exactes qui reviennent le plus souvent
2. **Verdict sur H1 et H3** — GO / PIVOT / STOP avec justification
3. **Liste des douleurs réelles** — ce que les DSI mentionnent spontanément (peut différer de nos hypothèses)
4. **Ajustements au pitch** — reformulations basées sur le vocabulaire réel des DSI
5. **2–3 contacts pour le pilot** — DSI intéressés par un pilot de 60 jours

---

## Drapeaux

**Drapeaux Rouges :**
- Ne pas présenter MobiCloud avant la question 13. Si on le fait avant, on biaise toutes les réponses précédentes — le DSI va confirmer nos hypothèses par politesse au lieu de nous dire la vérité.

**Drapeaux Jaunes :**
- Recruter des DSI d'universités en priorité — c'est le segment le plus accessible pour un fondateur issu de l'université, et les universités algériennes sont particulièrement exposées à la Law 11-25 (données étudiants, données de recherche).
- Éviter les entretiens pendant les périodes d'examen (mai–juin, janvier) — les DSI d'universités sont surchargés et les entretiens seront bâclés.

## Sources
- `06-validation/validation-plan.md` — cadre général des expériences
- `01-discovery/target-audience.md` — profil Karim DSI, critères de décision
- `02-strategy/go-to-market.md` — pitch DSI template (adapté ici pour un entretien de découverte)
