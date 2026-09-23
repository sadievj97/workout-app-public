package com.cutcoach

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No `init` block may read a property declared below it.
 *
 * Kotlin runs property initialisers and init blocks in source order, so a
 * property declared after an init block does not exist yet while that block
 * runs. Reading it gives null, and because Kotlin believes the type is
 * non-null, the failure arrives as a NullPointerException from code the
 * compiler has already decided cannot produce one.
 *
 * The case that prompted this: SessionPlayerViewModel's `ladderSteps` map was
 * declared 150 lines below the init block that filled it. The block runs inside
 * `viewModelScope.launch`, which looks asynchronous and therefore safe — but
 * viewModelScope dispatches on Main.immediate, so a launch from the main thread
 * starts executing synchronously, right there in the constructor. Every session
 * with a rep or a hold in it crashed the moment Start was pressed. Cardio days
 * have neither and survived, which is why it went unnoticed.
 *
 * Deliberately crude: it reads source text, does not parse Kotlin, and only
 * looks at properties with an initialiser. A computed `get()` has no backing
 * field and is fine at any position. The point is to notice a pattern that
 * compiles cleanly and fails at runtime, not to prove anything.
 */
class InitOrderTest {

    private val sources: List<File> by lazy {
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
    }

    /** `private val foo = ...` or `var foo = ...`, but not `val foo: T get() = ...`. */
    private val storedProperty =
        Regex("""^\s{4}(?:private |internal |protected )?(?:val|var)\s+(\w+)\s*(?::[^=]+)?=""")

    @Test
    fun `no init block reads a property declared after it`() {
        val offenders = mutableListOf<String>()

        sources.forEach { file ->
            val lines = file.readLines()

            // Where each init block starts and ends, by brace depth from the
            // opening line. Good enough for this codebase's formatting.
            lines.forEachIndexed { index, line ->
                if (!line.trimEnd().endsWith("init {")) return@forEachIndexed

                var depth = 0
                var end = index
                for (i in index until lines.size) {
                    depth += lines[i].count { it == '{' } - lines[i].count { it == '}' }
                    if (depth == 0 && i > index) {
                        end = i
                        break
                    }
                }
                val body = lines.subList(index, end + 1).joinToString("\n")

                // Every stored property declared after this block ends.
                lines.drop(end + 1).forEach { later ->
                    val name = storedProperty.find(later)?.groupValues?.get(1)
                        ?: return@forEach
                    // A bare-word match, so `steps` does not match `stepsLeft`.
                    if (Regex("""\b$name\b""").containsMatchIn(body)) {
                        offenders += "${file.name}: init reads '$name', " +
                            "which is declared below it"
                    }
                }
            }
        }

        assertTrue(
            "An init block reads a property that does not exist yet. Move the " +
                "declaration above the init block.\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
