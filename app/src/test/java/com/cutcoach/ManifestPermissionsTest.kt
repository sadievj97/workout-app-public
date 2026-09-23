package com.cutcoach

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Does the manifest declare the permissions the code actually uses?
 *
 * Written after the app died mid-session with
 * `SecurityException: Neither user nor current process has android.permission.VIBRATE`.
 * The buzz at the end of a rest timer had never been declared. Nothing caught
 * it: it compiles, it installs, and it only throws at the moment a timer
 * reaches zero — which is not a moment anyone is watching logcat.
 *
 * [com.cutcoach.health.HealthPermissionsTest] already does this for the Health
 * Connect strings. It listed those permissions by hand, so it could only ever
 * find a mistake in what someone had thought to list. This one goes the other
 * way: it looks for the API call in the source, and demands the permission that
 * call requires. A new use of a guarded API fails here rather than on the floor
 * at 05:35.
 */
class ManifestPermissionsTest {

    private val manifest: String by lazy { File("src/main/AndroidManifest.xml").readText() }

    private val sources: List<File> by lazy {
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
    }

    /**
     * A permission-guarded API, by the text that gives it away.
     *
     * Deliberately crude. A precise version would need to resolve types, and
     * the point is to notice, not to prove.
     */
    private data class Guarded(
        val permission: String,
        val why: String,
        val markers: List<String>,
    )

    private val guarded = listOf(
        Guarded(
            permission = "android.permission.VIBRATE",
            why = "the buzz at the end of a timer",
            markers = listOf("VibrationEffect", ".vibrate("),
        ),
        Guarded(
            permission = "android.permission.POST_NOTIFICATIONS",
            why = "the evening reminders",
            markers = listOf("NotificationManagerCompat", "NotificationCompat.Builder"),
        ),
        Guarded(
            permission = "android.permission.INTERNET",
            why = "the Claude proxy and the embedded video player",
            markers = listOf("HttpURLConnection", "WebView"),
        ),
    )

    @Test
    fun `a guarded API in the source has its permission in the manifest`() {
        guarded.forEach { api ->
            val users = sources.filter { file ->
                val text = file.readText()
                api.markers.any { text.contains(it) }
            }
            if (users.isEmpty()) return@forEach

            assertTrue(
                "${users.size} file(s) use ${api.permission} (${api.why}) — " +
                    "${users.joinToString { it.name }} — but the manifest does not " +
                    "declare it. This compiles and installs, and throws only when " +
                    "the call is reached.",
                manifest.contains("""android:name="${api.permission}""""),
            )
        }
    }

    @Test
    fun `the vibrate permission is declared, because a timer buzzes`() {
        // Kept separate and named for the bug it came from, so a future change
        // that drops the line from the manifest fails with the reason attached.
        assertTrue(
            "VIBRATE is missing. The session player crashed in exactly this way " +
                "once: every timer that reached zero took the app down, and the " +
                "relaunch landed on the same finished timer and did it again.",
            manifest.contains("""android:name="android.permission.VIBRATE""""),
        )
    }
}
