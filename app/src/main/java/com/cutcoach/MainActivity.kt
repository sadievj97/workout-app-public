package com.cutcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cutcoach.ui.CutCoachApp

/**
 * The only activity. Android launches this class; everything else is Compose.
 *
 * `enableEdgeToEdge` lets the navy background run under the status bar so the
 * screen has no hard edge at 05:35; [CutCoachApp] then insets the content so
 * nothing hides behind it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CutCoachApp() }
    }
}
