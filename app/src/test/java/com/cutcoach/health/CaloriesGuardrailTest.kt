package com.cutcoach.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app half of the brief's ninth rule test: watch calories are never shown
 * anywhere as food to eat back.
 *
 * The strongest form of that guardrail is not that the number is hidden, but
 * that it never enters the app at all — no calorie record type is read from
 * Health Connect, and no calorie permission is asked for, so there is nothing
 * to display by accident.
 *
 * Both of those are Android facts: a record-type list and a manifest. They
 * stayed here when the rules engine moved to `:core`, which has neither. The
 * engine's own half — that nothing it can say mentions eating calories back —
 * is RuleTest's `no recommendation mentions eating watch calories back`.
 */
class CaloriesGuardrailTest {

    @Test
    fun `no calories-burned record type is read from Health Connect`() {
        val readTypes = HealthConnectManager.RECORD_TYPES
            .map { it.type.simpleName.orEmpty() }

        assertTrue("No record types are declared at all", readTypes.isNotEmpty())
        assertTrue(
            "A calories-burned record type is being read: $readTypes",
            readTypes.none { it.contains("Calories", ignoreCase = true) },
        )
    }

    @Test
    fun `the manifest asks for no calories-burned permission`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse(
            "The manifest asks for a calories-burned permission",
            manifest.contains("READ_ACTIVE_CALORIES_BURNED") ||
                manifest.contains("READ_TOTAL_CALORIES_BURNED"),
        )
    }
}
