package com.mobicloud.domain.usecase.m08_m09_erasure_coding

/**
 * Choix du nombre de fragments de parité `m` à partir d'une disponibilité cible
 * (réponse à la remarque des reviewers sur les seuils 0.5 / 0.8 arbitraires).
 *
 * Modèle : un fichier codé RS(k, m) est reconstructible tant qu'au moins `k` des `k + m`
 * hôtes sont joignables. Le score de fiabilité `p_i ∈ [0, 1]` de chaque hôte est pris comme
 * estimation de sa probabilité d'être joignable, et les hôtes sont supposés indépendants.
 * La disponibilité du fichier est alors la queue d'une loi de Poisson-binomiale :
 *
 *     A(k, m) = P( X ≥ k ),   X = Σ_{i=1..k+m} Bernoulli(p_i)
 *
 * (qui se réduit à Σ_{j≥k} C(n, j) pʲ (1−p)ⁿ⁻ʲ quand tous les scores valent p).
 * On retient le plus petit `m ≥ 1` tel que `A(k, m) ≥ cible`. `A` est croissante en `m`
 * (ajouter un hôte ne peut pas diminuer P(X ≥ k)), donc le premier `m` qui atteint la cible
 * est le minimum.
 *
 * Le surcoût de stockage `(k + m) / k` est borné par [maxParity] : par défaut `m ≤ 2k`,
 * soit au plus ×3, le coût d'une réplication triple. Si la cible est hors d'atteinte avec
 * les hôtes présents ou sous ce plafond, on prend le plus grand `m` possible et on renvoie
 * la disponibilité estimée réellement obtenue.
 */
object ParityPolicy {

    /** Disponibilité cible par défaut : « trois neuf ». */
    const val DEFAULT_TARGET_AVAILABILITY = 0.999

    /** Plafond par défaut : surcoût de stockage au plus égal à une réplication ×3. */
    fun defaultMaxParity(k: Int): Int = 2 * k

    data class Decision(
        /** Nombre de fragments de parité retenu (≥ 1). */
        val parity: Int,
        /** Disponibilité estimée du fichier avec ces `k + parity` hôtes. */
        val estimatedAvailability: Double,
        /** Vrai si la cible est atteinte, faux si la décision est un meilleur effort. */
        val targetMet: Boolean
    )

    /**
     * Probabilité qu'au moins [k] des hôtes de scores [scores] soient joignables
     * (Poisson-binomiale, programmation dynamique en O(n·k)).
     */
    fun availability(scores: List<Double>, k: Int): Double {
        require(k >= 0) { "k doit être >= 0, reçu : $k" }
        if (k == 0) return 1.0
        if (scores.size < k) return 0.0
        // dist[j] = P(exactement j hôtes joignables), j < k ; dist[k] = P(au moins k).
        val dist = DoubleArray(k + 1)
        dist[0] = 1.0
        for (raw in scores) {
            val p = raw.coerceIn(0.0, 1.0)
            dist[k] += dist[k - 1] * p          // franchit le seuil k (absorbant)
            for (j in k - 1 downTo 1) {
                dist[j] = dist[j] * (1 - p) + dist[j - 1] * p
            }
            dist[0] *= (1 - p)
        }
        return dist[k].coerceIn(0.0, 1.0)
    }

    /**
     * Plus petit `m` dans `[1, maxParity]` tel que les `k + m` premiers scores de [rankedScores]
     * (triés du plus fiable au moins fiable) atteignent [target].
     *
     * @param rankedScores scores des hôtes candidats dans l'ordre où ils seront choisis ;
     *   il en faut au moins `k + 1`.
     */
    fun minimalParity(
        rankedScores: List<Double>,
        k: Int,
        target: Double = DEFAULT_TARGET_AVAILABILITY,
        maxParity: Int = defaultMaxParity(k)
    ): Decision {
        require(k >= 1) { "k doit être >= 1, reçu : $k" }
        require(target in 0.0..1.0) { "la cible doit être dans [0, 1], reçu : $target" }
        require(rankedScores.size > k) {
            "il faut au moins k + 1 = ${k + 1} hôtes, reçu : ${rankedScores.size}"
        }
        val upper = minOf(maxParity.coerceAtLeast(1), rankedScores.size - k)
        var best = Decision(parity = 1, estimatedAvailability = 0.0, targetMet = false)
        for (m in 1..upper) {
            val a = availability(rankedScores.subList(0, k + m), k)
            best = Decision(parity = m, estimatedAvailability = a, targetMet = a >= target)
            if (best.targetMet) return best
        }
        return best
    }
}
