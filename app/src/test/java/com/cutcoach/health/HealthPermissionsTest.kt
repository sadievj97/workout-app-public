package com.cutcoach.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The permission strings, checked against the manifest.
 *
 * This is the one class of mistake in Health Connect that costs a whole
 * evening: a permission that is requested in code but not declared in the
 * manifest, or spelled slightly wrong, does not fail to build. It fails at
 * runtime by quietly returning nothing, which looks exactly like a phone that
 * has no data.
 *
 * Every string below was copied from the official documentation, not recalled.
 */
class HealthPermissionsTest {

    private val manifest: String by lazy { File("src/main/AndroidManifest.xml").readText() }

    /** The read permissions the protocol needs, exactly as documented. */
    private val readPermissions = listOf(
        "android.permission.health.READ_STEPS",
        "android.permission.health.READ_SLEEP",
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_RESTING_HEART_RATE",
        "android.permission.health.READ_HEART_RATE_VARIABILITY",
        "android.permission.health.READ_EXERCISE",
        "android.permission.health.READ_WEIGHT",
    )

    private val extraPermissions = listOf(
        "android.permission.health.READ_HEALTH_DATA_HISTORY",
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
    )

    @Test
    fun `every permission the app needs is declared in the manifest`() {
        (readPermissions + extraPermissions).forEach { permission ->
            assertTrue(
                "Not declared in the manifest: $permission",
                manifest.contains("""android:name="$permission""""),
            )
        }
    }

    @Test
    fun `the manifest matches the constants the code uses`() {
        assertTrue(
            manifest.contains(HealthConnectManager.PERMISSION_READ_HISTORY),
        )
        assertTrue(
            manifest.contains(HealthConnectManager.PERMISSION_READ_IN_BACKGROUND),
        )
    }

    @Test
    fun `nothing anywhere asks to write health data`() {
        // The app reads what the watch recorded and never writes back. Making
        // that a test rather than an intention means a stray WRITE permission
        // cannot creep in later without someone noticing.
        assertFalse(
            "The manifest requests a health WRITE permission",
            manifest.contains("android.permission.health.WRITE_"),
        )
    }

    @Test
    fun `the rationale activity is declared both ways`() {
        // Health Connect will not show the permission sheet at all without
        // these, and the Android 14 route is a different declaration from the
        // Android 13 one. Missing either means the flow never starts, on that
        // version only.
        assertTrue(
            "Missing the pre-Android 14 rationale intent filter",
            manifest.contains("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"),
        )
        assertTrue(
            "Missing the Android 14+ permission usage alias",
            manifest.contains("android.intent.action.VIEW_PERMISSION_USAGE"),
        )
        assertTrue(
            "The alias needs the health permissions category",
            manifest.contains("android.intent.category.HEALTH_PERMISSIONS"),
        )
        assertTrue(
            "The alias must be protected by START_VIEW_PERMISSION_USAGE",
            manifest.contains("android.permission.START_VIEW_PERMISSION_USAGE"),
        )
    }

    @Test
    fun `the manifest can see whether Health Connect is installed`() {
        // Without this queries entry, the availability check reports
        // unavailable on a phone that has it.
        assertTrue(
            "Missing the Health Connect package query",
            manifest.contains("com.google.android.apps.healthdata"),
        )
    }
}
