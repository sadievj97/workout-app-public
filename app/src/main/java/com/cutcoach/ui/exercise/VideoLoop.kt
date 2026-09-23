package com.cutcoach.ui.exercise

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cutcoach.ui.theme.Ink

/**
 * A few seconds of a demo video, muted and looping, inside the cue card.
 *
 * It plays through YouTube's own embedded player. Nothing is downloaded,
 * cached or re-hosted: the clip stays where its owner put it, which keeps the
 * app clear of other people's video and keeps the repo clean.
 *
 * Everything else in the app works offline. This is the one piece that does
 * not, so it fails back to the figure rather than failing loudly.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoLoop(
    videoId: String,
    startSeconds: Float,
    endSeconds: Float,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView: WebView? = null

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webView = this
                setBackgroundColor(Ink.value.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Without this the clip waits for a tap, which defeats a loop
                // you are supposed to glance at between sets.
                settings.mediaPlaybackRequiresUserGesture = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onVideoError(code: String) {
                            post { onUnavailable() }
                        }
                    },
                    "Android",
                )

                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    playerHtml(videoId, startSeconds, endSeconds),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )

    DisposableEffect(videoId, startSeconds, endSeconds) {
        onDispose {
            webView?.apply {
                loadUrl("about:blank")
                destroy()
            }
        }
    }
}

/**
 * The player page.
 *
 * The loop is a seek back to the start whenever playback passes the end,
 * rather than YouTube's own loop, because the segment is usually a few seconds
 * out of a much longer video.
 */
private fun playerHtml(videoId: String, start: Float, end: Float): String = """
<!DOCTYPE html>
<html>
  <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
  <body style="margin:0;padding:0;background:#101B2D;overflow:hidden">
    <div id="player"></div>
    <script src="https://www.youtube.com/iframe_api"></script>
    <script>
      var player, timer;
      var START = $start;
      var END = $end;

      function onYouTubeIframeAPIReady() {
        player = new YT.Player('player', {
          height: '100%',
          width: '100%',
          videoId: '$videoId',
          playerVars: {
            autoplay: 1, controls: 0, disablekb: 1, fs: 0,
            modestbranding: 1, rel: 0, playsinline: 1,
            start: Math.floor(START)
          },
          events: {
            onReady: onReady,
            onStateChange: onStateChange,
            onError: onError
          }
        });
      }

      function onReady(event) {
        event.target.mute();
        event.target.seekTo(START, true);
        event.target.playVideo();
        timer = setInterval(function () {
          try {
            if (END > START && player.getCurrentTime() >= END) {
              player.seekTo(START, true);
            }
          } catch (e) { }
        }, 250);
      }

      function onStateChange(event) {
        if (event.data === YT.PlayerState.ENDED) {
          player.seekTo(START, true);
          player.playVideo();
        }
      }

      function onError(event) {
        if (window.Android) { window.Android.onVideoError(String(event.data)); }
      }
    </script>
  </body>
</html>
""".trimIndent()

/**
 * Pulls the video id out of whatever YouTube link was pasted in.
 *
 * Returns null for anything that is not recognisably a YouTube video, which
 * the cue card treats the same as having no link at all.
 */
fun youTubeVideoId(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null

    val patterns = listOf(
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/live/([A-Za-z0-9_-]{11})"""),
    )
    return patterns.firstNotNullOfOrNull { it.find(trimmed)?.groupValues?.get(1) }
}

/**
 * Whether there is any usable network right now.
 *
 * Checked before trying to embed, so a session at 05:35 with the phone in
 * airplane mode shows the figure straight away rather than a blank box that
 * eventually gives up.
 */
fun Context.hasNetwork(): Boolean = try {
    val manager = getSystemService(ConnectivityManager::class.java)
    val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (_: Exception) {
    false
}
