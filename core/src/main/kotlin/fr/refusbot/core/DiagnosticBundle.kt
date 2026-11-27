package fr.refusbot.core

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Archive .zip de tous les diagnostics, à partager en une fois (demande du 27/11 : partager les fichiers
 * un par un prenait trop de temps). Rien n'est envoyé : l'archive est créée sur le téléphone et c'est
 * l'utilisateur qui choisit de la partager, comme chaque diagnostic.
 */
object DiagnosticBundle {

    /**
     * Un fichier de l'archive : son nom et de quoi lire son contenu. Le contenu est recopié morceau par
     * morceau (jamais 30 diagnostics entiers en mémoire d'un coup).
     */
    class Entry(val name: String, val time: Long = 0L, val open: () -> InputStream) {
        constructor(name: String, bytes: ByteArray, time: Long = 0L) : this(name, time, { bytes.inputStream() })
    }

    /**
     * Écrit l'archive. Les noms sont réduits à un nom de fichier simple (aucun dossier, aucun « .. ») et
     * rendus uniques ; un fichier sans nom est ignoré.
     */
    fun write(out: OutputStream, entries: List<Entry>) {
        val used = HashSet<String>()
        ZipOutputStream(out).use { zip ->
            for (e in entries) {
                val name = uniqueName(safeName(e.name) ?: continue, used)
                val entry = ZipEntry(name)
                if (e.time > 0) entry.time = e.time
                zip.putNextEntry(entry)
                e.open().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Nom de fichier sans chemin ni caractère spécial ; null s'il ne reste rien. */
    fun safeName(name: String): String? {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')
        return base.takeIf { it.isNotEmpty() }
    }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        var n = 2
        while (true) {
            val candidate = name.substring(0, dot) + "-$n" + name.substring(dot)
            if (used.add(candidate)) return candidate
            n++
        }
    }
}
