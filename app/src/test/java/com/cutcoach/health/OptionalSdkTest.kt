package com.cutcoach.health

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * If the optional Samsung SDK is present, the code that uses it must actually
 * be compiled.
 *
 * This exists because it was not, and nothing said so. The Samsung sources
 * moved into their own source root, `app/build.gradle.kts` added that root with
 * `java.srcDir`, and the folder contains only `.kt` files — so the Java source
 * set was handed a directory with no Java in it and compiled exactly nothing.
 * There is nothing wrong with an empty Java source root, so the build stayed
 * green. The classes were simply absent, the reflective lookup in MainActivity
 * found nothing, and the app reported "not built with the Samsung SDK" on a
 * phone that had it. A feature turned off by a one-word mistake in a build
 * file, reported as working.
 *
 * Conditional on purpose. A clone without the .aar is a supported and ordinary
 * state — that is the whole point of the source root — so the absence of the
 * classes is only a failure when the SDK is there to justify them.
 */
class OptionalSdkTest {

    /** Unit tests run from the module directory, so this is `app/libs`. */
    private val libs = File("libs")

    private val sdkPresent: Boolean
        get() = libs.listFiles().orEmpty().any { it.extension == "aar" }

    private fun classOrNull(name: String): Class<*>? =
        runCatching { Class.forName(name) }.getOrNull()

    @Test
    fun `when the samsung aar is present its source is compiled`() {
        if (!sdkPresent) return

        listOf(
            "com.cutcoach.samsung.SamsungEnergyScores",
            "com.cutcoach.samsung.EnergyScoreProbe",
        ).forEach { name ->
            assertNotNull(
                "$name is missing although the SDK is in app/libs. The source root " +
                    "is not being compiled — check that build.gradle.kts uses " +
                    "kotlin.srcDir and not java.srcDir for src/samsung/java.",
                classOrNull(name),
            )
        }
    }

    @Test
    fun `the registration MainActivity reflects on still exists`() {
        if (!sdkPresent) return

        // MainActivity calls this by name, through reflection, so no compiler
        // checks it. Renaming the method would break Energy Score in a way that
        // builds, installs and runs, and reports itself as "SDK not present".
        val holder = classOrNull("com.cutcoach.samsung.SamsungEnergyScores")
        assertNotNull("SamsungEnergyScores is not compiled", holder)

        val companion = holder!!.getField("Companion").get(null)
        val register = runCatching {
            companion.javaClass.getMethod("register", android.content.Context::class.java)
        }.getOrNull()

        assertNotNull(
            "SamsungEnergyScores.Companion.register(Context) is gone. MainActivity " +
                "looks it up by this exact name and signature and swallows the " +
                "failure, so losing it disables Energy Score silently.",
            register,
        )
    }

    @Test
    fun `nothing outside the samsung source root imports the SDK`() {
        // What makes the source root droppable. One import of the Samsung SDK
        // from anywhere else and a clone without the .aar stops compiling,
        // which is the failure this whole arrangement exists to prevent.
        val offenders = File("src/main/java")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("import com.samsung.") }
            .map { it.name }
            .toList()

        assertTrue(
            "These files import the Samsung SDK from the main source set, so a " +
                "clone without app/libs/*.aar will not compile: $offenders",
            offenders.isEmpty(),
        )
    }
}
