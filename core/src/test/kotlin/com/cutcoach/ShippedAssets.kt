package com.cutcoach

import java.io.File

/**
 * The protocol and exercise library as they actually ship, for the tests that
 * insist on reading the real files rather than a fixture.
 *
 * That insistence is deliberate and predates this module: a typo in
 * `protocol.json` fails on the desktop in seconds instead of on the phone at
 * 05:35. Moving the tests here did not change what they should read, only
 * where it is.
 *
 * The files stay in the app module because that is where Android requires
 * assets to live. Reaching across is the honest option — a second copy here
 * would be the same data in two places, which is the exact failure the "one
 * source of truth" rule exists to prevent, and it would drift the first time
 * someone edited one of them.
 */
internal object ShippedAssets {

    /** The app module's asset directory, relative to this module's own. */
    private val dir = File("../app/src/main/assets")

    fun text(name: String): String {
        val file = File(dir, name)
        check(file.exists()) {
            "Missing shipped asset: ${file.absolutePath}. The tests in :core read " +
                "the real files from :app; if the assets moved, this is the one " +
                "place that needs to know."
        }
        return file.readText()
    }

    fun file(name: String): File = File(dir, name)
}
