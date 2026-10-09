package dev.abdus.apps.immich.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.content.ContentResolver
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.net.Uri
import dev.abdus.apps.immich.api.ImmichClientProvider
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichRepository
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ImmichRandomAssetProvider : DocumentsProvider() {
    companion object {
        private const val TAG = "ImmichRandomAssetProvider"
        private const val ROOT_ID = "dev.abdus.apps.immich.documents"
        private const val DEFAULT_LIMIT = 6
        private const val MAX_LIMIT = 200
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_ICON
        )
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        for (column in columns) {
            row.add(
                when (column) {
                    Root.COLUMN_ROOT_ID -> ROOT_ID
                    Root.COLUMN_DOCUMENT_ID -> ROOT_ID
                    Root.COLUMN_TITLE -> "Immich Random"
                    Root.COLUMN_FLAGS ->
                        Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_SUPPORTS_RECENTS
                    Root.COLUMN_MIME_TYPES -> "image/*"
                    Root.COLUMN_SUMMARY -> "Random assets from Immich"
                    Root.COLUMN_ICON -> dev.abdus.apps.immich.R.drawable.ic_launcher_foreground
                    else -> null
                }
            )
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
        )
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        for (column in columns) {
            row.add(
                when (column) {
                    Document.COLUMN_DOCUMENT_ID -> documentId
                    Document.COLUMN_DISPLAY_NAME ->
                        if (documentId == ROOT_ID) {
                            "Immich Random"
                        } else {
                            parseDisplayName(documentId) ?: parseAssetId(documentId)
                        }
                    Document.COLUMN_MIME_TYPE ->
                        if (documentId == ROOT_ID) Document.MIME_TYPE_DIR else "image/*"
                    Document.COLUMN_FLAGS ->
                        if (documentId == ROOT_ID) {
                            Document.FLAG_DIR_PREFERS_GRID or Document.FLAG_DIR_PREFERS_LAST_MODIFIED
                        } else {
                            Document.FLAG_SUPPORTS_THUMBNAIL
                        }
                    Document.COLUMN_SIZE -> null
                    else -> null
                }
            )
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return queryChildDocumentsInternal(parentDocumentId, projection, null)
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        return queryChildDocumentsInternal(parentDocumentId, projection, queryArgs)
    }

    private fun queryChildDocumentsInternal(
        parentDocumentId: String,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val columns = projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SUMMARY,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
        )
        val cursor = MatrixCursor(columns)
        if (parentDocumentId != ROOT_ID) return cursor
        val context = context ?: return cursor

        val limit = parseLimit(queryArgs)
        val prefs = AppPreferences(context)
        val config = prefs.current()
        val client = ImmichClientProvider.fromConfig(config) ?: return cursor
        val repository = ImmichRepository(client)
        val albumIds = config.selectedAlbumIds.toList().ifEmpty { null }
        val tagIds = config.selectedTagIds.toList().ifEmpty { null }
        val createdAfterIso: String? = config.filterPresetDaysBack?.let { days ->
            try {
                LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }

        val assets = runBlocking {
            repository.fetchRandomAssets(
                albumIds = albumIds,
                tagIds = tagIds,
                favoritesOnly = config.favoritesOnly,
                createdAfter = createdAfterIso,
                createdBefore = null,
                size = limit
            )
        }

        assets.forEach { asset ->
            val row = cursor.newRow()
            for (column in columns) {
                row.add(
                    when (column) {
                        Document.COLUMN_DOCUMENT_ID ->
                            buildDocumentId(asset.id, asset.originalFileName)
                        Document.COLUMN_DISPLAY_NAME -> asset.originalFileName ?: asset.id
                        Document.COLUMN_SUMMARY -> asset.albumId
                        Document.COLUMN_MIME_TYPE -> "image/*"
                        Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_THUMBNAIL
                        Document.COLUMN_SIZE -> null
                        else -> null
                    }
                )
            }
        }
        return cursor
    }

    override fun queryRecentDocuments(
        rootId: String,
        projection: Array<out String>?
    ): Cursor {
        if (rootId != ROOT_ID) return MatrixCursor(projection ?: emptyArray())
        return queryChildDocumentsInternal(ROOT_ID, projection, null)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        if (!mode.contains("r")) return null
        val context = context ?: return null
        val assetId = parseAssetId(documentId) ?: return null
        val fileStore = ImmichAssetFileStore(context)
        val file = fileStore.safeGetOrDownload(assetId, thumbnail = false) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?
    ): android.content.res.AssetFileDescriptor? {
        val context = context ?: return null
        val assetId = parseAssetId(documentId) ?: return null
        val fileStore = ImmichAssetFileStore(context)
        val file = fileStore.safeGetOrDownload(assetId, thumbnail = true) ?: return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return android.content.res.AssetFileDescriptor(pfd, 0, android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    private fun parseAssetId(documentId: String): String? {
        if (documentId == ROOT_ID) return null
        val raw = documentId.substringAfter("$ROOT_ID/", missingDelimiterValue = "")
        val path = raw.substringBefore("?")
        return path.ifBlank { null }
    }

    private fun parseLimit(args: Bundle?): Int {
        if (args == null) return DEFAULT_LIMIT
        val limit = if (args.containsKey(ContentResolver.QUERY_ARG_LIMIT)) {
            args.getInt(ContentResolver.QUERY_ARG_LIMIT)
        } else {
            DEFAULT_LIMIT
        }
        return limit.coerceIn(1, MAX_LIMIT)
    }

    private fun parseDisplayName(documentId: String): String? {
        val query = documentId.substringAfter("?", missingDelimiterValue = "")
        if (query.isBlank()) return null
        val params = query.split("&")
        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.firstOrNull() == "original_filename") {
                return parts.getOrNull(1)?.let { Uri.decode(it) }
            }
        }
        return null
    }

    private fun buildDocumentId(assetId: String, originalFileName: String?): String {
        val encodedName = originalFileName?.let { Uri.encode(it) }
        val suffix = if (encodedName.isNullOrBlank()) {
            ""
        } else {
            "?original_filename=$encodedName"
        }
        return "$ROOT_ID/$assetId$suffix"
    }

    override fun getDocumentType(documentId: String): String {
        return if (documentId == ROOT_ID) {
            Document.MIME_TYPE_DIR
        } else {
            "image/*"
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return parentDocumentId == ROOT_ID && documentId.startsWith("$ROOT_ID/")
    }
}
