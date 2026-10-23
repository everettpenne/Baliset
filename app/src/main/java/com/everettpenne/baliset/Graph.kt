package com.everettpenne.baliset

import android.content.Context
import com.everettpenne.baliset.data.Library
import com.everettpenne.baliset.data.Playlists
import com.everettpenne.baliset.data.Settings

/**
 * The app's few long-lived objects, made once in [BalisetApp] and reached from any screen. There is no framework here on purpose: one
 * library, one set of saved lists, one settings file, one playback session.
 */
class Graph(context: Context) {
    val settings = Settings(context)
    val library = Library(context)
    val playlists = Playlists(context)
}

val Context.graph: Graph
    get() = (applicationContext as BalisetApp).graph

/** The one place a folder's display name is read, so the welcome page and Settings say the same thing. */
fun folderName(context: Context, treeUri: String): String = runCatching {
    val uri = android.net.Uri.parse(treeUri)
    val document = android.provider.DocumentsContract.buildDocumentUriUsingTree(
        uri, android.provider.DocumentsContract.getTreeDocumentId(uri),
    )
    context.contentResolver.query(
        document, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull().orEmpty().ifBlank { "Folder" }
