package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.jellyfin.ApiResult
import com.everettpenne.baliset.data.jellyfin.DownloadInfo
import com.everettpenne.baliset.data.jellyfin.DownloadStatus
import com.everettpenne.baliset.data.jellyfin.JfItem
import com.everettpenne.baliset.data.jellyfin.PublicInfo
import com.everettpenne.baliset.data.jellyfin.SavedItem
import com.everettpenne.baliset.data.jellyfin.toTrack
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.AlbumArt
import com.everettpenne.baliset.ui.components.GlassTextField
import com.everettpenne.baliset.ui.components.TrackRow
import com.everettpenne.baliset.ui.theme.Baliset
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassCenteredTopBar
import com.everettpenne.glass.GlassDropdownMenu
import com.everettpenne.glass.GlassEmptyState
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.GlassMenuItem
import com.everettpenne.glass.GlassProgressBar
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassSearchField
import com.everettpenne.glass.GlassSectionHeader
import com.everettpenne.glass.IosType
import com.everettpenne.glass.contentBottomPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class JfTab { Albums, Songs, Downloads }

/** Jellyfin, or the downloads that do not need it. */
@Composable
fun JellyfinScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val jellyfin = graph.jellyfin
    val nav = LocalNav.current
    val session by jellyfin.sessions.session.collectAsState()
    val saved by jellyfin.downloads.saved.collectAsState()
    val states by jellyfin.downloads.state.collectAsState()
    var offline by remember { mutableStateOf(false) }

    when {
        session != null -> JellyfinMain(session = session!!, onBack = { nav?.back() })
        offline -> DownloadsOnlyScreen(
            saved = saved,
            states = states,
            onPlay = { savedItem ->
                savedItem.toTrack(jellyfin.downloads, jellyfin.urls)?.let { Playback.play(listOf(it)) }
            },
            onDelete = { jellyfin.downloads.remove(it.item.id) },
            onBack = { offline = false },
        )
        else -> JellyfinLogin(
            savedCount = saved.size,
            onBack = { nav?.back() },
            onDownloads = { offline = true },
        )
    }
}

// ---- signing in -------------------------------------------------------------------------------------------------------------

@Composable
private fun JellyfinLogin(savedCount: Int, onBack: () -> Unit, onDownloads: () -> Unit) {
    val graph = LocalContext.current.graph
    val jellyfin = graph.jellyfin
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Pair<String, PublicInfo>?>(null) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect() {
        if (busy || address.isBlank()) return
        busy = true; error = null
        scope.launch {
            when (val result = jellyfin.api.connect(address)) {
                is ApiResult.Ok -> found = result.value
                is ApiResult.Failed -> error = result.reason.message
            }
            busy = false
        }
    }

    fun signIn() {
        val (url, info) = found ?: return
        if (busy || user.isBlank()) return
        busy = true; error = null
        scope.launch {
            val result = jellyfin.api.login(url, info.serverName, user.trim(), pass)
            withContext(Dispatchers.Main) {
                when (result) {
                    is ApiResult.Ok -> jellyfin.sessions.signIn(result.value)
                    is ApiResult.Failed -> error = result.reason.message
                }
                busy = false
            }
        }
    }

    GlassScreen(bars = {
        GlassCenteredTopBar(
            title = if (found == null) "Jellyfin" else "Sign in",
            onBack = if (found != null) ({ found = null; error = null }) else onBack,
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .padding(top = padding.calculateTopPadding() + 24.dp, start = 22.dp, end = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Dns, null, tint = Baliset.accent, modifier = Modifier.size(56.dp))
            val info = found?.second
            if (info == null) {
                Text("Your Jellyfin server", style = IosType.title2.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Enter its address, such as jellyfin.example.com or 192.168.1.20:8096. Baliset tries https first, then plain http for a server at home.",
                    style = IosType.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GlassTextField(
                    address, { address = it }, "Server address",
                    leading = Icons.Rounded.Link, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, onSubmit = ::connect,
                )
                GlassButton(
                    if (busy) "Connecting…" else "Connect", ::connect, Modifier.fillMaxWidth(),
                    enabled = !busy && address.isNotBlank(), prominent = true,
                )
            } else {
                Text(info.serverName.ifBlank { "Jellyfin" }, style = IosType.title2.copy(fontWeight = FontWeight.Bold))
                Text(
                    found!!.first + (if (info.version.isNotEmpty()) " · Jellyfin ${info.version}" else ""),
                    style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (found!!.first.startsWith("http://")) {
                    Text(
                        "This address is not encrypted. Your password and what you play can be seen by anyone on the network between here and the server.",
                        style = IosType.caption1, color = Baliset.accent,
                    )
                }
                GlassTextField(user, { user = it }, "Username", leading = Icons.Rounded.Person, imeAction = ImeAction.Next)
                GlassTextField(pass, { pass = it }, "Password", leading = Icons.Rounded.Lock, password = true, imeAction = ImeAction.Go, onSubmit = ::signIn)
                GlassButton(
                    if (busy) "Signing in…" else "Sign in", ::signIn, Modifier.fillMaxWidth(),
                    enabled = !busy && user.isNotBlank(), prominent = true,
                )
            }
            error?.let { Text(it, style = IosType.subheadline, color = MaterialTheme.colorScheme.error) }
            if (savedCount > 0) {
                Spacer(Modifier.height(8.dp))
                GlassButton("Downloads ($savedCount)", onDownloads, Modifier.fillMaxWidth())
            }
        }
    }
}

// ---- browsing ---------------------------------------------------------------------------------------------------------------

@Composable
private fun JellyfinMain(session: com.everettpenne.baliset.data.jellyfin.Session, onBack: () -> Unit) {
    val context = LocalContext.current
    val jellyfin = context.graph.jellyfin
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val saved by jellyfin.downloads.saved.collectAsState()
    val states by jellyfin.downloads.state.collectAsState()
    val now by Playback.state.collectAsState()

    var tab by remember { mutableStateOf(JfTab.Albums) }
    var libraries by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var library by remember { mutableStateOf<JfItem?>(null) }
    var albums by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var songs by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var selected by remember { mutableStateOf<JfItem?>(null) }
    var selectedTracks by remember { mutableStateOf<List<JfItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true; error = null
        when (val result = jellyfin.api.musicLibraries()) {
            is ApiResult.Ok -> {
                libraries = result.value
                val first = result.value.firstOrNull()
                if (first == null) {
                    error = "No music library on this server."
                } else {
                    library = first
                    when (val a = jellyfin.api.items(parentId = first.id, types = "MusicAlbum", limit = 300)) {
                        is ApiResult.Ok -> albums = a.value.items
                        is ApiResult.Failed -> error = a.reason.message
                    }
                    when (val s = jellyfin.api.items(parentId = first.id, types = "Audio", limit = 800)) {
                        is ApiResult.Ok -> songs = s.value.items
                        is ApiResult.Failed -> if (error == null) error = s.reason.message
                    }
                }
            }
            is ApiResult.Failed -> error = result.reason.message
        }
        loading = false
    }

    LaunchedEffect(albums) {
        albums.forEach { album -> jellyfin.ensureArt("jf:${album.id}", jellyfin.urls.poster(album, 500)) }
    }
    LaunchedEffect(songs) {
        songs.mapNotNull { it.albumId }.distinct().forEach { albumId ->
            val item = songs.firstOrNull { it.albumId == albumId } ?: return@forEach
            jellyfin.ensureArt("jf:$albumId", jellyfin.urls.poster(item, 500))
        }
    }
    LaunchedEffect(selected) {
        val album = selected ?: return@LaunchedEffect
        loading = true; error = null; selectedTracks = emptyList()
        jellyfin.ensureArt("jf:${album.id}", jellyfin.urls.poster(album, 500))
        when (val result = jellyfin.api.albumTracks(album.id)) {
            is ApiResult.Ok -> selectedTracks = result.value
            is ApiResult.Failed -> error = result.reason.message
        }
        loading = false
    }

    val filteredAlbums = remember(albums, query) {
        if (query.isBlank()) albums else albums.filter {
            it.name.contains(query, true) || (it.musicArtist?.contains(query, true) == true)
        }
    }
    val filteredSongs = remember(songs, query) {
        if (query.isBlank()) songs else songs.filter {
            it.name.contains(query, true) || (it.musicArtist?.contains(query, true) == true) || (it.album?.contains(query, true) == true)
        }
    }
    val songTracks = remember(filteredSongs) { filteredSongs.map { it.toTrack(jellyfin.urls) } }
    val albumTracks = remember(selectedTracks) { selectedTracks.map { it.toTrack(jellyfin.urls) } }

    fun playAlbum(start: Int = 0) {
        if (albumTracks.isNotEmpty()) Playback.play(albumTracks, start)
    }
    fun download(item: JfItem) {
        if (!jellyfin.requestDownload(item)) error = "Baliset is not signed in, or the server does not allow downloads."
    }

    GlassScreen(bars = {
        GlassCenteredTopBar(
            title = selected?.name ?: "Jellyfin",
            onBack = { if (selected != null) selected = null else onBack() },
            trailing = {
                Box {
                    GlassIconButton(Icons.Rounded.MoreVert, "Server actions", { menu = true })
                    GlassDropdownMenu(menu, { menu = false }) {
                        GlassMenuItem("Refresh", { menu = false; reload++ })
                        GlassMenuItem("Downloads", { menu = false; tab = JfTab.Downloads })
                        GlassMenuItem("Sign out", { menu = false; jellyfin.sessions.signOut() })
                    }
                }
            },
        )
    }) { padding ->
        if (selected != null) {
            AlbumDetail(
                album = selected!!,
                tracks = albumTracks,
                items = selectedTracks,
                nowId = now.track?.id,
                saved = saved,
                states = states,
                loading = loading,
                error = error,
                onPlay = { playAlbum(it) },
                onDownload = { download(it) },
                onDownloadAlbum = { selectedTracks.forEach { download(it) } },
                padding = padding,
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JfTabButton("Albums", tab == JfTab.Albums, Modifier.weight(1f)) { tab = JfTab.Albums }
                    JfTabButton("Songs", tab == JfTab.Songs, Modifier.weight(1f)) { tab = JfTab.Songs }
                    JfTabButton("Downloads", tab == JfTab.Downloads, Modifier.weight(1f)) { tab = JfTab.Downloads }
                }
                GlassSearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), placeholder = "Search this server")
                if (loading && albums.isEmpty() && songs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { GlassProgressBar(null) }
                }
                error?.let {
                    Text(it, style = IosType.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                when (tab) {
                    JfTab.Albums -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentBottomPadding() + 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(filteredAlbums.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { album ->
                                    AlbumCard(album, Modifier.weight(1f)) { selected = album }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (filteredAlbums.isEmpty() && !loading) {
                            item { GlassEmptyState(Icons.Rounded.Album, "No albums", if (query.isBlank()) "This server's music library is empty." else "Nothing matches \"$query\".") }
                        }
                    }
                    JfTab.Songs -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = contentBottomPadding() + 8.dp),
                    ) {
                        items(filteredSongs, key = { it.id }) { song ->
                            val index = filteredSongs.indexOf(song)
                            val track = songTracks.getOrNull(index) ?: return@items
                            TrackRow(
                                track = track,
                                playing = now.track?.id == track.id,
                                onClick = { Playback.play(songTracks, index) },
                                onLongClick = {},
                                subtitle = song.musicArtist,
                                trailing = { DownloadControl(song, saved, states) { download(song) } },
                            )
                        }
                        if (filteredSongs.isEmpty() && !loading) {
                            item { GlassEmptyState(Icons.Rounded.MusicNote, "No songs", if (query.isBlank()) "This server's music library is empty." else "Nothing matches \"$query\".") }
                        }
                    }
                    JfTab.Downloads -> DownloadsList(
                        saved = saved,
                        states = states,
                        nowId = now.track?.id,
                        onPlay = { savedItem -> savedItem.toTrack(jellyfin.downloads, jellyfin.urls)?.let { Playback.play(listOf(it)) } },
                        onDelete = { jellyfin.downloads.remove(it.item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JfTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassButton(text, onClick, modifier, prominent = selected)
}

@Composable
private fun AlbumCard(album: JfItem, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AlbumArt("jf:${album.id}", Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp, iconSize = 30.dp)
        Text(
            album.name,
            style = IosType.callout.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.musicArtist.orEmpty(),
            style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumDetail(
    album: JfItem,
    tracks: List<com.everettpenne.baliset.data.Track>,
    items: List<JfItem>,
    nowId: String?,
    saved: List<SavedItem>,
    states: Map<String, DownloadInfo>,
    loading: Boolean,
    error: String?,
    onPlay: (Int) -> Unit,
    onDownload: (JfItem) -> Unit,
    onDownloadAlbum: () -> Unit,
    padding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 6.dp, bottom = contentBottomPadding() + 8.dp),
    ) {
        item("header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AlbumArt("jf:${album.id}", Modifier.fillMaxWidth(0.62f).aspectRatio(1f), corner = 18.dp, iconSize = 40.dp)
                Spacer(Modifier.height(18.dp))
                Text(album.name, style = IosType.title2.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    album.musicArtist.orEmpty(),
                    style = IosType.subheadline, color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    listOfNotNull(
                        album.year?.toString(),
                        "${tracks.size} song" + (if (tracks.size == 1) "" else "s"),
                    ).joinToString(" · "),
                    style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton({ onPlay(0) }, Modifier.weight(1f), prominent = true, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("Play", maxLines = 1)
                    }
                    GlassButton({ onDownloadAlbum() }, Modifier.weight(1f), enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp)); Text("Download", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (loading) item("loading") { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { GlassProgressBar(null) } }
        error?.let { message -> item("error") { Text(message, style = IosType.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) } }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            val item = items.getOrNull(index)
            TrackRow(
                track = track,
                playing = nowId == track.id,
                onClick = { onPlay(index) },
                onLongClick = {},
                showArtwork = false,
                trackNumber = item?.index ?: (index + 1),
                subtitle = null,
                trailing = {
                    DownloadControl(item = item, saved = saved, states = states) {
                        item?.let(onDownload)
                    }
                },
            )
        }
    }
}

@Composable
private fun DownloadControl(
    item: JfItem?,
    saved: List<SavedItem>,
    states: Map<String, DownloadInfo>,
    onDownload: () -> Unit,
) {
    // The item is only needed to start a download; the state is keyed by the saved list on the row's id.
    val state = item?.let { states[it.id] }
    when {
        state?.status == DownloadStatus.DONE -> Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = Baliset.accent, modifier = Modifier.size(22.dp))
        state?.status == DownloadStatus.ACTIVE -> {
            val fraction = state.fraction
            if (fraction != null) Text("${(fraction * 100).toInt()}%", style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text("Saving", style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state?.status == DownloadStatus.PAUSED -> Text("Waiting", style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        item != null && saved.any { it.item.id == item.id } && state == null -> Text("Waiting", style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Icon(
            Icons.Rounded.Download,
            if (state?.status == DownloadStatus.FAILED) "Retry download" else "Download",
            tint = if (state?.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp).clickable(onClick = onDownload),
        )
    }
}

@Composable
private fun DownloadsList(
    saved: List<SavedItem>,
    states: Map<String, DownloadInfo>,
    nowId: String?,
    onPlay: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = contentBottomPadding() + 8.dp),
    ) {
        if (saved.isEmpty()) {
            item { GlassEmptyState(Icons.Rounded.Download, "No downloads", "Download an album from Jellyfin to listen without the server.") }
        }
        items(saved, key = { it.item.id }) { savedItem ->
            val state = states[savedItem.item.id]
            val done = state?.status == DownloadStatus.DONE
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AlbumArt("jf:${savedItem.item.albumId ?: savedItem.item.id}", Modifier.size(46.dp), corner = 10.dp, iconSize = 18.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        savedItem.item.name,
                        style = IosType.callout.copy(fontWeight = if (nowId == "jf:${savedItem.item.id}") FontWeight.SemiBold else FontWeight.Normal),
                        color = if (nowId == "jf:${savedItem.item.id}") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        savedItem.item.musicArtist ?: savedItem.item.album.orEmpty(),
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val problem = state?.problem
                    when {
                        problem != null -> Text(problem, style = IosType.caption1, color = MaterialTheme.colorScheme.error)
                        state?.status == DownloadStatus.ACTIVE -> {
                            val fraction = state.fraction
                            if (fraction != null) GlassProgressBar(fraction) else Text("Downloading…", style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state?.status == DownloadStatus.PAUSED -> Text("Waiting for a connection", style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        done -> Text("Downloaded", style = IosType.caption1, color = Baliset.accent)
                    }
                }
                if (done) GlassIconButton(Icons.Rounded.PlayArrow, "Play download", { onPlay(savedItem) })
                GlassIconButton(Icons.Rounded.DeleteOutline, "Delete download", { onDelete(savedItem) })
            }
        }
    }
}

@Composable
private fun DownloadsOnlyScreen(
    saved: List<SavedItem>,
    states: Map<String, DownloadInfo>,
    onPlay: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onBack: () -> Unit,
) {
    val now by Playback.state.collectAsState()
    GlassScreen(bars = { GlassCenteredTopBar("Downloads", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            DownloadsList(saved, states, now.track?.id, onPlay, onDelete)
        }
    }
}
