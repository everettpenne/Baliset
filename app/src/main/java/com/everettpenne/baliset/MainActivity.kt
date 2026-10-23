package com.everettpenne.baliset

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.everettpenne.baliset.ui.BalisetRoot

class MainActivity : ComponentActivity() {
    /** An audio file handed over by another app (Sietch, a download); the root plays it once and opens the player. */
    private var openUri by mutableStateOf<Uri?>(null)
    /** Debug only: a folder handed in with `am start`, so the folder library can be exercised without the system picker. */
    private var debugFolder by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) {
            openUri = intent?.data
            if (BuildConfig.DEBUG) debugFolder = intent?.getStringExtra("baliset.folder")
        }
        setContent { BalisetRoot(openUri, debugFolder) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openUri = intent.data
        if (BuildConfig.DEBUG) debugFolder = intent.getStringExtra("baliset.folder")
    }
}
