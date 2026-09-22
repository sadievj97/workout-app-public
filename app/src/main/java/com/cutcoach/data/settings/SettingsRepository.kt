package com.cutcoach.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/** What the owner made of one figure. */
enum class FormVerdict {
    UNREVIEWED, LOOKS_RIGHT, NEEDS_FIXING;

    companion object {
        fun parse(value: String?): FormVerdict =
            entries.firstOrNull { it.name == value } ?: UNREVIEWED
    }
}

data class FormReview(
    val verdict: FormVerdict = FormVerdict.UNREVIEWED,
    val note: String = "",
)

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
        val REMINDERS_ON = booleanPreferencesKey("reminders_on")
        fun reminder(momentId: String) = booleanPreferencesKey("reminder_$momentId")
        fun videoUrl(exerciseId: String) = stringPreferencesKey("video_url_$exerciseId")
        fun videoStart(exerciseId: String) = stringPreferencesKey("video_start_$exerciseId")
        fun videoEnd(exerciseId: String) = stringPreferencesKey("video_end_$exerciseId")
        fun formVerdict(exerciseId: String) = stringPreferencesKey("form_verdict_$exerciseId")
        fun formNote(exerciseId: String) = stringPreferencesKey("form_note_$exerciseId")
    }

    /**
     * What the owner made of each figure: unreviewed, right, or needs fixing,
     * plus a note saying what is wrong.
     *
     * An animation is a drawing of a movement made by someone who cannot see
     * the owner do it, so it stays labelled unreviewed until they say
     * otherwise.
     */
    val formReviews: Flow<Map<String, FormReview>> = context.settingsStore.data.map { prefs ->
        prefs.asMap()
            .keys
            .mapNotNull { key -> key.name.removePrefixOrNull("form_verdict_") }
            .associateWith { exerciseId ->
                FormReview(
                    verdict = FormVerdict.parse(prefs[Keys.formVerdict(exerciseId)]),
                    note = prefs[Keys.formNote(exerciseId)].orEmpty(),
                )
            }
    }

    suspend fun setFormVerdict(exerciseId: String, verdict: FormVerdict) {
        context.settingsStore.edit { prefs ->
            if (verdict == FormVerdict.UNREVIEWED) {
                prefs.remove(Keys.formVerdict(exerciseId))
            } else {
                prefs[Keys.formVerdict(exerciseId)] = verdict.name
            }
        }
    }

    suspend fun setFormNote(exerciseId: String, note: String) {
        context.settingsStore.edit { prefs ->
            if (note.isBlank()) {
                prefs.remove(Keys.formNote(exerciseId))
            } else {
                prefs[Keys.formNote(exerciseId)] = note.trim()
            }
        }
    }

    private fun String.removePrefixOrNull(prefix: String): String? =
        if (startsWith(prefix)) removePrefix(prefix) else null

    // ---- Evening reminders -------------------------------------------------

    /**
     * The master switch. On by default: the evening half of the timeline is the
     * part the owner will not open the app to read, which is the whole reason
     * these exist.
     */
    val remindersOn: Flow<Boolean> =
        context.settingsStore.data.map { it[Keys.REMINDERS_ON] ?: true }

    suspend fun setRemindersOn(on: Boolean) {
        context.settingsStore.edit { it[Keys.REMINDERS_ON] = on }
    }

    /**
     * Per-moment overrides, keyed by the timeline's written time.
     *
     * A moment absent from this map has never been touched, and falls back to
     * the protocol's own default rather than to off.
     */
    val reminderChoices: Flow<Map<String, Boolean>> = context.settingsStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("reminder_") }
            .mapKeys { (key, _) -> key.name.removePrefix("reminder_") }
            .mapValues { (_, value) -> value as? Boolean ?: false }
    }

    suspend fun setReminder(momentId: String, on: Boolean) {
        context.settingsStore.edit { it[Keys.reminder(momentId)] = on }
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

    /** The segment worth looping, as seconds. Null means the exercise library's own. */
    fun videoSegment(exerciseId: String): Flow<Pair<Float, Float>?> =
        context.settingsStore.data.map { prefs ->
            val start = prefs[Keys.videoStart(exerciseId)]?.toFloatOrNull()
            val end = prefs[Keys.videoEnd(exerciseId)]?.toFloatOrNull()
            if (start == null && end == null) null else (start ?: 0f) to (end ?: 0f)
        }

    suspend fun setVideoSegment(exerciseId: String, start: Float?, end: Float?) {
        context.settingsStore.edit { prefs ->
            if (start == null) {
                prefs.remove(Keys.videoStart(exerciseId))
            } else {
                prefs[Keys.videoStart(exerciseId)] = start.toString()
            }
            if (end == null) {
                prefs.remove(Keys.videoEnd(exerciseId))
            } else {
                prefs[Keys.videoEnd(exerciseId)] = end.toString()
            }
        }
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
