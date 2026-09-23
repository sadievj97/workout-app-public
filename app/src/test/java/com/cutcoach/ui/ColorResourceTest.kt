package com.cutcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `res/values/colors.xml` must agree with `ui/theme/Color.kt`.
 *
 * The XML copies exist because the system draws the window background, the
 * status and navigation bars, and the launcher icon before any Compose code
 * runs — so those values cannot come from Kotlin. A duplicate that cannot be
 * removed is a duplicate that has to be checked.
 *
 * It had already drifted. The palette moved from navy to ink and the XML stayed
 * behind, so every launch flashed #101B2D before Compose drew #0B0A12 over it,
 * and the system bars kept the old colour for the whole session. Nothing failed;
 * the app just looked slightly wrong in a way that is easy to stop noticing.
 *
 * Both files are read as text rather than through the Android resource system,
 * because these are unit tests with no device and no aapt.
 */
class ColorResourceTest {

    private val colorsXml: String by lazy { File("src/main/res/values/colors.xml").readText() }
    private val colorKt: String by lazy {
        File("src/main/java/com/cutcoach/ui/theme/Color.kt").readText()
    }

    /** The XML resource name paired with the Kotlin `val` it copies. */
    private val pairs = listOf(
        "ink" to "Ink",
        "ink_lift" to "InkLift",
        "text_high" to "TextHigh",
        "ember" to "Ember",
        "sky" to "Sky",
    )

    /** `<color name="ink">#0B0A12</color>` → "0B0A12". */
    private fun fromXml(name: String): String? =
        Regex("""<color name="$name">#([0-9A-Fa-f]{6,8})</color>""")
            .find(colorsXml)?.groupValues?.get(1)?.uppercase()

    /** `val Ink = Color(0xFF0B0A12)` → "0B0A12", dropping the alpha byte. */
    private fun fromKotlin(name: String): String? =
        Regex("""val $name = Color\(0x([0-9A-Fa-f]{8})\)""")
            .find(colorKt)?.groupValues?.get(1)?.uppercase()?.removePrefix("FF")

    @Test
    fun `every colour resource matches its Kotlin definition`() {
        pairs.forEach { (xmlName, kotlinName) ->
            val xml = fromXml(xmlName)
            val kotlin = fromKotlin(kotlinName)

            assertTrue(
                "No <color name=\"$xmlName\"> in res/values/colors.xml",
                xml != null,
            )
            assertTrue(
                "No `val $kotlinName = Color(0x...)` in ui/theme/Color.kt",
                kotlin != null,
            )
            assertEquals(
                "$xmlName in colors.xml is #$xml but $kotlinName is #$kotlin. The " +
                    "window background and system bars come from the XML and are " +
                    "drawn before Compose, so a mismatch shows as a flash of the " +
                    "wrong colour at every launch.",
                kotlin,
                xml,
            )
        }
    }

    @Test
    fun `no resource file still carries a colour from the old palette`() {
        // The navy scheme, by its literal hexes. They survived the rename in
        // res/, which Kotlin's compiler has no way to notice.
        val retired = listOf("101B2D", "1D2B42", "D5DDE8", "F2A541", "7FB2E5", "2C3E5C")

        val offenders = File("src/main/res")
            .walkTopDown()
            .filter { it.extension == "xml" }
            .mapNotNull { file ->
                val hit = retired.firstOrNull { file.readText().contains(it, ignoreCase = true) }
                hit?.let { "${file.name} still uses #$it" }
            }
            .toList()

        assertTrue(
            "Resources left on the old palette: $offenders",
            offenders.isEmpty(),
        )
    }
}
