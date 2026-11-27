package fr.refusbot.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Partage volontaire d'un fichier exporté (historique, réglages, règles) via FileProvider. */
object ShareHelper {

    /** Écrit [content] dans le cache privé et renvoie l'Intent de partage (choix de l'appli par l'utilisateur). */
    suspend fun shareText(context: Context, fileName: String, mimeType: String, content: String, title: String): Intent =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            // On ne garde que le dernier export de chaque type.
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(fileName, uri)
            Intent.createChooser(send, title)
        }
}
