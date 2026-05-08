package com.mobicloud.data.local.election

import android.content.Context
import com.mobicloud.domain.usecase.m10_election.CooldownStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste le timestamp de fin de cooldown post-abdication dans un SharedPreferences
 * dédié. Survit à un kill de l'app — sans cette persistance, un attaquant pouvait
 * abdiquer puis kill+restart pour effacer la pénalité de 5 min.
 */
@Singleton
class SharedPrefsCooldownStore @Inject constructor(
    @ApplicationContext context: Context
) : CooldownStore {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun read(): Long = prefs.getLong(KEY_COOLDOWN_UNTIL_MS, 0L)

    override fun write(value: Long) {
        prefs.edit().putLong(KEY_COOLDOWN_UNTIL_MS, value).apply()
    }

    companion object {
        private const val PREFS_FILE = "mobicloud_election_state"
        private const val KEY_COOLDOWN_UNTIL_MS = "cooldown_until_ms"
    }
}
