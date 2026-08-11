package com.nikhil.yt.utils

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RingtoneHelper {
    suspend fun setRingtone(context: Context, audioFile: File, type: Int = RingtoneManager.TYPE_RINGTONE): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, audioFile.absolutePath)
                    put(MediaStore.MediaColumns.TITLE, audioFile.nameWithoutExtension)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
                    put(MediaStore.Audio.Media.IS_ALARM, true)
                    put(MediaStore.Audio.Media.IS_MUSIC, false)
                }

                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val newUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Failed to insert ringtone")
                    context.contentResolver.openOutputStream(newUri)?.use { out ->
                        audioFile.inputStream().copyTo(out)
                    }
                    newUri
                } else {
                    context.contentResolver.insert(MediaStore.Audio.Media.getContentUriForPath(audioFile.absolutePath), values)
                        ?: throw IllegalStateException("Failed to insert ringtone")
                }

                RingtoneManager.setActualDefaultRingtoneUri(context, type, uri)
                uri
            }
        }
}
