package com.cutcoach.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * The handful of things the owner can change on the phone.
 *
 * protocol.json stays the source of truth: everything here is an override that
 * sits on top of it, and clearing an override falls back to the protocol value.
 * That way the file still says what the program is, and the phone says where
 * this particular setup differs.
 *
 * Dates are deliberately not editable here. Moving the program start date after
 * the fact would silently renumber every logged session, so it is changed in
 * protocol.json, consciously.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val REST_SECONDS = intPreferencesKey("rest_seconds")
        val REHAB_LIST = stringPreferencesKey("rehab_exercises")
        fun videoUrl(exerciseId: String) = stringPreferencesKey("video_url_$exerciseId")
    }

    /** Null when no override is set, meaning "use the protocol's value". */
    val restSecondsOverride: Flow<Int?> =
        context.settingsStore.data.map { it[Keys.REST_SECONDS] }

    suspend fun setRestSeconds(seconds: Int?) {
        context.settingsStore.edit { prefs ->
            if (seconds == null) prefs.remove(Keys.REST_SECONDS) else prefs[Keys.REST_SECONDS] = seconds
        }
    }

    /**
     * The rehab exercises, one per line.
     *
     * These are typed in from the owner's rehab program and only ever
     * displayed. No recommendation in any phase may generate, change, or
     * substitute them.
     */
    val rehabExercises: Flow<List<String>> = context.settingsStore.data.map { prefs ->
        prefs[Keys.REHAB_LIST]
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    suspend fun setRehabExercises(text: String) {
        context.settingsStore.edit { it[Keys.REHAB_LIST] = text }
    }

    val rehabExercisesRaw: Flow<String> =
        context.settingsStore.data.map { it[Keys.REHAB_LIST] ?: "" }

    /** A demo link for one exercise, overriding whatever exercises.json holds. */
    fun videoUrl(exerciseId: String): Flow<String> =
        context.settingsStore.data.map { it[Keys.videoUrl(exerciseId)] ?: "" }

    val allVideoUrls: Flow<Map<String, String>> = context.settingsStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("video_url_") }
            .mapKeys { (key, _) -> key.name.removePrefix("video_url_") }
            .mapValues { (_, value) -> value as? String ?: "" }
            .filterValues { it.isNotBlank() }
    }

    suspend fun setVideoUrl(exerciseId: String, url: String) {
        context.settingsStore.edit { prefs ->
            val key = Keys.videoUrl(exerciseId)
            if (url.isBlank()) prefs.remove(key) else prefs[key] = url.trim()
        }
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
