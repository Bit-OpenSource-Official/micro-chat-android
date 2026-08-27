package ru.e6atb.chat

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.net.URLConnection

class LocalFileProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String = URLConnection.guessContentTypeFromName(fileName(uri)) ?: "application/octet-stream"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode.contains('w')) throw FileNotFoundException("read only")
        val file = resolve(uri)?.takeIf { it.isFile } ?: throw FileNotFoundException("not found")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val file = resolve(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).also { cursor ->
            cursor.addRow(Array<Any?>(columns.size) { index -> when (columns[index]) {
                OpenableColumns.DISPLAY_NAME -> file?.name ?: fileName(uri)
                OpenableColumns.SIZE -> file?.length() ?: 0L
                else -> null
            } })
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    private fun resolve(uri: Uri): File? = try {
        val currentContext = context ?: return null
        val base = currentContext.getExternalFilesDir(null) ?: currentContext.filesDir ?: return null
        val root = base.canonicalFile
        val target = File(root, fileName(uri)).canonicalFile
        if (target.path == root.path || target.path.startsWith(root.path + File.separator)) target else null
    } catch (_: Exception) { null }
    private fun fileName(uri: Uri?): String = uri?.lastPathSegment?.takeIf { it.isNotEmpty() }?.replace('/', '_')?.replace('\\', '_') ?: "file"
}
