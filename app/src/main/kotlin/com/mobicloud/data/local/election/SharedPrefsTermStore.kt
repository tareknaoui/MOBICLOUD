package com.mobicloud.data.local.election

import android.content.Context
import com.mobicloud.domain.usecase.m10_election.TermState
import com.mobicloud.domain.usecase.m10_election.TermStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste les numéros de mandat (terms) dans le même SharedPreferences que le cooldown.
 * Sans persistance, un nœud redémarré oublierait le plus haut term vu et pourrait de
 * nouveau suivre un Super-Pair périmé.
 */
@Singleton
class SharedPrefsTermStore @Inject constructor(
    @ApplicationContext context: Context
) : TermStore {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun read(): TermState = TermState(
        clusterId = prefs.getString(KEY_CLUSTER_ID, "") ?: "",
        highestTerm = prefs.getLong(KEY_HIGHEST_TERM, 0L),
        ownTerm = prefs.getLong(KEY_OWN_TERM, 0L)
    )

    override fun write(state: TermState) {
        prefs.edit()
            .putString(KEY_CLUSTER_ID, state.clusterId)
            .putLong(KEY_HIGHEST_TERM, state.highestTerm)
            .putLong(KEY_OWN_TERM, state.ownTerm)
            .apply()
    }

    companion object {
        private const val PREFS_FILE = "mobicloud_election_state"
        private const val KEY_CLUSTER_ID = "term_cluster_id"
        private const val KEY_HIGHEST_TERM = "term_highest"
        private const val KEY_OWN_TERM = "term_own"
    }
}
