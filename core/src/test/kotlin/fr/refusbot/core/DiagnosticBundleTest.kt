package fr.refusbot.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Archive de tous les diagnostics (demande du 27/11) : un seul fichier à partager. */
class DiagnosticBundleTest {

    private fun read(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                result[e.name] = zip.readBytes()
            }
        }
        return result
    }

    @Test
    fun `tous les fichiers sont dans l'archive avec leur contenu`() {
        val out = ByteArrayOutputStream()
        DiagnosticBundle.write(out, listOf(
            DiagnosticBundle.Entry("diagnostic-com.opera.browser-20261127-120000.json", "{\"a\":1}".toByteArray()),
            DiagnosticBundle.Entry("diagnostic-org.mozilla.firefox-20261127-120800.json", "{\"b\":2}".toByteArray()),
            DiagnosticBundle.Entry("historique.json", "[]".toByteArray()),
        ))
        val files = read(out.toByteArray())
        assertEquals(
            listOf("diagnostic-com.opera.browser-20261127-120000.json", "diagnostic-org.mozilla.firefox-20261127-120800.json", "historique.json"),
            files.keys.toList(),
        )
        assertArrayEquals("{\"b\":2}".toByteArray(), files.getValue("diagnostic-org.mozilla.firefox-20261127-120800.json"))
    }

    @Test
    fun `jamais de dossier ni de remontee dans les noms, doublons renommes`() {
        val out = ByteArrayOutputStream()
        DiagnosticBundle.write(out, listOf(
            DiagnosticBundle.Entry("../../etc/passwd", byteArrayOf(1)),
            DiagnosticBundle.Entry("a/b/passwd", byteArrayOf(2)),
            DiagnosticBundle.Entry("", byteArrayOf(3)),
        ))
        assertEquals(listOf("passwd", "passwd-2"), read(out.toByteArray()).keys.toList())
        assertNull(DiagnosticBundle.safeName("..."))
        assertEquals("diag_1_.json", DiagnosticBundle.safeName("diag 1?.json"))
    }
}
