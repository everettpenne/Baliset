package com.everettpenne.baliset.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.everettpenne.baliset.R
import com.everettpenne.baliset.folderName
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.theme.Baliset
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassProgressBar
import com.everettpenne.glass.IosType
import com.everettpenne.glass.glassSurface
import com.everettpenne.glass.progressiveBlur

/**
 * The first page: one promise, two ways to give the app music, and a progress line while it looks.
 *
 * "Scan my phone" asks for the one audio permission and reads Android's media index; "Choose a folder" never asks for a permission and
 * uses the system folder picker, the same way Sietch does. Both are explained before anything is asked for, and the page says plainly
 * that nothing leaves the device.
 */
@Composable
fun WelcomeScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val scan by graph.library.scan.collectAsState()
    val snapshot by graph.library.snapshot.collectAsState()
    val folder by graph.settings.folder.collectAsState()
    var denied by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) graph.library.scanMedia()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            graph.settings.setFolder(uri.toString(), folderName(context, uri.toString()))
            graph.settings.setWelcomeSeen()
            graph.library.scanFolder(uri.toString())
        }
    }

    fun scanPhone() {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) graph.library.scanMedia() else permissionLauncher.launch(permission)
    }

    GlassScreen(bars = {}) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(112.dp).glassSurface(CircleShape, tint = Baliset.night.copy(alpha = .88f), blurRadius = 8f, lensHeight = 18f, lensAmount = 32f),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, modifier = Modifier.size(86.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Your music,\nprivately.", style = IosType.largeTitle.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Baliset plays the songs already on this phone. No account, no ads, no upload — just your library, with a player that fits in your hand.",
                style = IosType.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            if (scan.running) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(scan.phase.ifBlank { "Looking for music" }, style = IosType.headline, color = MaterialTheme.colorScheme.onBackground)
                    if (scan.total > 0) GlassProgressBar(scan.done.toFloat() / scan.total) else GlassProgressBar(null)
                    Text(
                        if (scan.total > 0) "${scan.done} of ${scan.total}" else "${scan.found} songs so far",
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(
                        onClick = { scanPhone() },
                        modifier = Modifier.fillMaxWidth(),
                        prominent = true,
                    ) {
                        androidx.compose.material3.Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp))
                        Text("Scan my phone", maxLines = 1)
                    }
                    GlassButton(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(20.dp))
                        Text(if (folder == null) "Choose a folder" else "Choose another folder", maxLines = 1)
                    }
                }
                if (folder == null && snapshot.tracks.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Apple Music library of AIFF files? Choose the folder — Android's media index does not list AIFF.",
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
                if (!denied && snapshot.tracks.isEmpty() && scan.found == 0 && scan.error == null && snapshot.scannedAt > 0 && graph.settings.source.value == com.everettpenne.baliset.data.LibrarySnapshot.SOURCE_MEDIA) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Android's index has no music on this phone. If your files are there, choose their folder — Baliset can read it without any permission.",
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
                if (denied) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "The audio permission was declined, so Baliset cannot see the phone's music. Choose a folder instead, or allow it later in Android Settings → Apps → Baliset.",
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
                if (snapshot.tracks.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    GlassButton(
                        "Start listening",
                        onClick = { graph.settings.setWelcomeSeen(); nav?.home() },
                        prominent = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (scan.error != null) {
                Spacer(Modifier.height(14.dp))
                Text(scan.error!!, style = IosType.footnote, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(
                    "Your files stay where they are. Only titles, artists and cover art are read.",
                    style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
