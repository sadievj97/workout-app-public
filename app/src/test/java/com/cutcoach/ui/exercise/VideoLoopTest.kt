package com.cutcoach.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pulling a video id out of a pasted link.
 *
 * The owner will paste whatever YouTube gave them, and the share sheet, the
 * address bar and a Shorts link all look different. Getting this wrong means a
 * cue card silently shows the figure instead of the clip.
 */
class VideoLoopTest {

    @Test
    fun `reads the usual YouTube link shapes`() {
        val expected = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$expected",
            "https://youtube.com/watch?v=$expected&t=42s",
            "https://m.youtube.com/watch?app=desktop&v=$expected",
            "https://youtu.be/$expected",
            "https://youtu.be/$expected?t=42",
            "https://www.youtube.com/embed/$expected",
            "https://www.youtube.com/shorts/$expected",
            "https://www.youtube.com/live/$expected",
            "  https://youtu.be/$expected  ",
        ).forEach { url ->
            assertEquals("Failed on $url", expected, youTubeVideoId(url))
        }
    }

    @Test
    fun `anything that is not a YouTube video gives nothing`() {
        listOf(
            "",
            "   ",
            "not a url",
            "https://example.com/some-video",
            "https://vimeo.com/123456789",
            // A channel, not a video.
            "https://www.youtube.com/@someone",
        ).forEach { url ->
            assertNull("Should not have matched: $url", youTubeVideoId(url))
        }
    }

    @Test
    fun `an id is exactly eleven characters`() {
        // Ten or twelve is a typo, and embedding it would fail silently.
        assertNull(youTubeVideoId("https://youtu.be/tooShort1"))
        assertEquals("abcdefghijk", youTubeVideoId("https://youtu.be/abcdefghijk"))
    }
}
