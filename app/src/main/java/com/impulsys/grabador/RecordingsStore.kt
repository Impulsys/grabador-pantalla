package com.impulsys.grabador

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/** Lee del MediaStore las grabaciones guardadas por la app (Movies/Grabador). */
object RecordingsStore {

    fun query(context: Context): List<Recording> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA
        )

        val selection: String?
        val args: Array<String>?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf("%${Environment.DIRECTORY_MOVIES}/Grabador%")
        } else {
            selection = "${MediaStore.Video.Media.DATA} LIKE ?"
            args = arrayOf("%/Grabador/%")
        }

        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val result = ArrayList<Recording>()

        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                result.add(
                    Recording(
                        id = id,
                        uri = uri,
                        name = c.getString(nameCol) ?: "Grabación",
                        dateMs = c.getLong(dateCol) * 1000L,
                        sizeBytes = c.getLong(sizeCol),
                        durationMs = c.getLong(durCol),
                        dataPath = c.getString(dataCol)
                    )
                )
            }
        }
        return result
    }

    fun delete(context: Context, rec: Recording): Boolean {
        return try {
            context.contentResolver.delete(rec.uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
