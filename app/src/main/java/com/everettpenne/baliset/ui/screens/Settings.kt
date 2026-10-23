package com.everettpenne.baliset.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.everettpenne.baliset.BuildConfig
import com.everettpenne.baliset.folderName
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.theme.Baliset
import com.everettpenne.glass.GlassAlert
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassDropdownMenu
import com.everettpenne.glass.GlassGroup
import com.everettpenne.glass.GlassListRow
import com.everettpenne.glass.GlassRowDivider
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.GlassTopBar
import com.everettpenne.glass.IosType
import com.everettpenne.updater.LocalUpdater
import com.everettpenne.updater.collectStatus
import com.everettpenne.glass.contentBottomPadding
import java.text.DateFormat
import java.util.Date

/**
 * Settings, grouped the way the rest of the suite is: the library (where it comes from, rescan, remove), the app (updates, privacy)
 * and a plain statement of what Baliset does with the phone. Nothing here is a required setup step after the first run.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val snapshot by graph.library.snapshot.collectAsState()
    val scan by graph.library.scan.collectAsState()
    val source by graph.settings.source.collectAsState()
    val folder by graph.settings.folder.collectAsState()
    val folderLabel by graph.settings.folderName.collectAsState()
    val updater = LocalUpdater.current
    val updateStatus = if (updater != null) updater.collectStatus() else ""
    var removing by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            graph.settings.setFolder(uri.toString(), folderName(context, uri.toString()))
            graph.library.scanFolder(uri.toString())
        }
    }

    fun choosePhone() {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) { graph.settings.setSource(com.everettpenne.baliset.data.LibrarySnapshot.SOURCE_MEDIA); graph.library.scanMedia() }
        else nav?.welcome()
    }

    GlassScreen(bars = {
        GlassTopBar("Settings", subtitle = "Baliset ${BuildConfig.VERSION_NAME}", onBack = { nav?.back() })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = contentBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item("library") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Library", style = IosType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                    GlassGroup {
                        GlassListRow(
                            "Phone library",
                            subtitle = "Android's media index",
                            onClick = { choosePhone() },
                            leading = { Icon(Icons.Rounded.PhoneAndroid, null, tint = Baliset.accent, modifier = Modifier.size(22.dp)) },
                            trailing = { if (source == com.everettpenne.baliset.data.LibrarySnapshot.SOURCE_MEDIA) Icon(Icons.Rounded.Check, "Selected", tint = Baliset.accent, modifier = Modifier.size(20.dp)) },
                        )
                        GlassRowDivider()
                        GlassListRow(
                            "Folder",
                            subtitle = folderLabel ?: "Choose a folder Baliset may read",
                            onClick = {
                                when {
                                    folder == null -> folderPicker.launch(null)
                                    source == com.everettpenne.baliset.data.LibrarySnapshot.SOURCE_FOLDER -> folderPicker.launch(null)
                                    else -> { graph.settings.setSource(com.everettpenne.baliset.data.LibrarySnapshot.SOURCE_FOLDER); graph.library.scanFolder(folder!!) }
                                }
                            },
                            leading = { Icon(Icons.Rounded.Folder, null, tint = Baliset.accent, modifier = Modifier.size(22.dp)) },
                            trailing = {
                                if (source == com.everettpenne.baliset.data.LibrarySnapshot.SOURCE_FOLDER) Icon(Icons.Rounded.Check, "Selected", tint = Baliset.accent, modifier = Modifier.size(20.dp))
                                else Icon(Icons.Rounded.Folder, "Choose", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            },
                        )
                        GlassRowDivider()
                        GlassListRow(
                            "Rescan now",
                            subtitle = when {
                                scan.running -> scan.phase.ifBlank { "Working" }
                                snapshot.scannedAt > 0 -> "Last scan " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snapshot.scannedAt))
                                else -> "Not scanned yet"
                            },
                            onClick = {
                                if (source == com.everettpenne.baliset.data.LibrarySnapshot.SOURCE_FOLDER && folder != null) graph.library.scanFolder(folder!!)
                                else if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) graph.library.scanMedia()
                                else nav?.welcome()
                            },
                            leading = { Icon(Icons.Rounded.Refresh, null, tint = Baliset.accent, modifier = Modifier.size(22.dp)) },
                        )
                        GlassRowDivider()
                        GlassListRow(
                            "Remove library",
                            subtitle = "Forget the list and its artwork; files stay on the phone",
                            onClick = { removing = true },
                            leading = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp)) },
                            titleColor = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        if (snapshot.tracks.isEmpty()) "No music yet."
                        else "${snapshot.tracks.size} songs · saved on this phone",
                        style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            item("about") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", style = IosType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                    GlassGroup {
                        GlassListRow(
                            "Version", subtitle = BuildConfig.VERSION_NAME,
                            leading = { Icon(Icons.Rounded.LibraryMusic, null, tint = Baliset.accent, modifier = Modifier.size(22.dp)) },
                        )
                        GlassRowDivider()
                        GlassListRow(
                            "Check for updates", subtitle = updateStatus.ifBlank { "GitHub releases" },
                            onClick = { updater?.onSettingsRowClick() },
                            leading = { Icon(Icons.Rounded.SystemUpdate, null, tint = Baliset.accent, modifier = Modifier.size(22.dp)) },
                        )
                        GlassRowDivider()
                        GlassListRow(
                            "Privacy", subtitle = "What Baliset reads, and what it never does",
                            onClick = { privacy = true },
                            leading = { Icon(Icons.Rounded.Shield, null, tint = Baliset.accent, modifier = Modifier.size(22.dp)) },
                        )
                    }
                    Text(
                        "No account, no analytics, no ads. The only network request is to GitHub, and only when you check for an update.",
                        style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    if (removing) GlassAlert(
        "Remove library?", "Baliset forgets the scanned list and its cached artwork. The music files themselves stay where they are.",
        "Remove", { graph.library.clear(); removing = false }, { removing = false }, destructive = true,
    )

    if (privacy) GlassSheet(onDismiss = { privacy = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Privacy", style = IosType.title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            listOf(
                "Baliset reads song titles, artists, albums, lengths and cover art — nothing else — from files you point it at.",
                "Your library list, playlists, favourites and play counts live in the app's private storage and never leave the phone.",
                "There is no account and no server. The only request Baliset can make is to GitHub, when you tap Check for updates.",
                "Uninstalling or clearing Baliset's storage removes the list and playlists; the music is untouched.",
            ).forEach { line ->
                Text(line, style = IosType.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            GlassButton("Done", { privacy = false }, Modifier.fillMaxWidth(), prominent = true)
        }
    }
}
