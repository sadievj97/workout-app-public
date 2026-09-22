package com.cutcoach.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

/**
 * Reading what a model actually sends back.
 *
 * Models fence JSON in code blocks and add a polite sentence in front of it
 * however firmly the prompt says not to. A parser that only accepted perfect
 * output would reject answers that were substantively right, so these are the
 * imperfect shapes that still have to work — and the ones that must not.
 */
class ClaudeParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `clean JSON parses`() {
        val obj = extractJsonObject("""{"forExercise":"pike_push_up","sets":3}""")
        assertEquals("pike_push_up", obj!!["forExercise"].toString().trim('"'))
    }

    @Test
    fun `JSON inside a code fence parses`() {
        val text = """
            Here is the substitution:

            ```json
            {"replaces": "push_up", "withExerciseId": "pike_push_up", "sets": 4}
            ```
        """.trimIndent()
        val obj = extractJsonObject(text)
        assertEquals("pike_push_up", obj!!["withExerciseId"].toString().trim('"'))
    }

    @Test
    fun `JSON after a preamble parses`() {
        val text = "Sure. {\"replaces\": \"push_up\", \"sets\": 4} Hope that helps."
        assertEquals(4, extractJsonObject(text)!!["sets"].toString().toInt())
    }

    @Test
    fun `prose with no JSON at all gives nothing rather than half an object`() {
        assertNull(extractJsonObject("I would suggest doing pike push-ups instead."))
        assertNull(extractJsonObject(""))
        assertNull(extractJsonObject("{ this is not json"))
    }

    @Test
    fun `a nested object survives, because the outermost braces are used`() {
        // lastIndexOf('}') has to find the closing brace of the whole object,
        // not of the change nested inside it.
        val text = """
            {"sleep":"a","sleepingHeartRate":"b","steps":"c","weight":"d","strength":"e",
             "oneChange":{"kind":"steps","stepsChange":1500,"reason":"flat"}}
        """.trimIndent()
        val answer = json.decodeFromString<WeeklyReviewAnswer>(extractJsonObject(text)!!.toString())
        assertEquals("steps", answer.oneChange?.kind)
        assertEquals(1500, answer.oneChange?.stepsChange)
    }

    @Test
    fun `a review missing a section is rejected rather than shown half empty`() {
        // The prose fields have no defaults on purpose. A review with a blank
        // "steps" paragraph would look like a finished answer that had nothing
        // to say, when in fact the model never answered.
        val partial = """{"sleep":"fine","oneChange":null}"""
        val parsed = runCatching {
            json.decodeFromString<WeeklyReviewAnswer>(extractJsonObject(partial)!!.toString())
        }.getOrNull()

        assertNull("A half-written review parsed successfully", parsed)
    }

    @Test
    fun `a missing optional field does not fail the parse`() {
        // The model omitting oneChange is how it says "nothing needs changing".
        val answer = json.decodeFromString<WeeklyReviewAnswer>(
            """{"sleep":"a","sleepingHeartRate":"b","steps":"c","weight":"d","strength":"e"}""",
        )
        assertNull(answer.oneChange)
    }

    // ---- The pain short-circuit -------------------------------------------

    @Test
    fun `words that mean pain are caught before anything is sent`() {
        listOf(
            "my knee hurts",
            "sharp pain in the shoulder",
            "I felt dizzy during the warm-up",
            "there is a pinching feeling",
            "chest feels tight",
        ).forEach {
            assertTrue(it, soundsLikePain(it))
        }
    }

    @Test
    fun `ordinary reasons for a swap are not treated as pain`() {
        listOf(
            "no pull-up bar in the hotel",
            "no floor space this morning",
            "the chair is the wrong height",
            "legs are sore from yesterday",
        ).forEach {
            assertFalse(it, soundsLikePain(it))
        }
    }

    @Test
    fun `soreness is deliberately not pain`() {
        // Ordinary training soreness is not what pain_stop is about, and
        // treating it as such would stop training every Tuesday.
        assertFalse(soundsLikePain("quads are sore"))
        assertNull(painKindFor("quads are sore"))
    }

    @Test
    fun `the client is not configured until both values are present`() {
        // Empty in a normal build, because local.properties has no proxy in it
        // and the key is deliberately not a field at all.
        assertFalse(ClaudeClient.isConfigured)
    }
}
