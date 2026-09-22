package com.cutcoach.claude

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.callLogStore: DataStore<Preferences> by preferencesDataStore("claude_calls")

/**
 * Every call to Claude: which feature, which prompt version, which model, and
 * what it cost.
 *
 * The brief asks for this, and it is worth more than the audit it was asked
 * for: it is the only way to notice that a feature has quietly started costing
 * ten times what it did, or that a prompt change made answers worse.
 *
 * What is *not* here is as deliberate: no prompt text and no answer text. Those
 * carry the health summaries, and a log of them on the phone would be a second
 * copy of the thing the app is careful about. The token counts say how much was
 * sent without saying what.
 */
object CallLog {

    private val KEY = stringPreferencesKey("records")
    private val json = Json { ignoreUnknownKeys = true }

    /** Enough to see a pattern, few enough that the file stays small. */
    private const val KEEP = 200

    suspend fun record(
        context: Context,
        feature: String,
        model: String,
        answer: ClaudeClient.Answer,
        rejected: Boolean,
    ) {
        val entry = CallRecord(
            feature = feature,
            promptVersion = Prompts.VERSION,
            model = model,
            inputTokens = answer.inputTokens,
            outputTokens = answer.outputTokens,
            rejected = rejected,
            at = System.currentTimeMillis(),
        )
        context.callLogStore.edit { prefs ->
            val existing = decode(prefs[KEY])
            prefs[KEY] = json.encodeToString(
                (listOf(entry) + existing).take(KEEP),
            )
        }
    }

    /** Newest first. */
    fun all(context: Context): Flow<List<CallRecord>> =
        context.callLogStore.data.map { decode(it[KEY]) }

    suspend fun clear(context: Context) {
        context.callLogStore.edit { it.remove(KEY) }
    }

    /** What the layer has cost so far, for the Settings screen. */
    suspend fun totals(context: Context): Totals {
        val records = all(context).first()
        return Totals(
            calls = records.size,
            rejected = records.count { it.rejected },
            inputTokens = records.sumOf { it.inputTokens },
            outputTokens = records.sumOf { it.outputTokens },
        )
    }

    data class Totals(
        val calls: Int,
        val rejected: Int,
        val inputTokens: Int,
        val outputTokens: Int,
    )

    private fun decode(raw: String?): List<CallRecord> =
        raw?.let { runCatching { json.decodeFromString<List<CallRecord>>(it) }.getOrNull() }
            ?: emptyList()
}
