package fr.refusbot.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Chaque image de l'appli doit être complète et lisible. Une image JPEG tronquée
 * (cyrava_emblem.jpg, 19/11/2026) compilait sans erreur mais faisait planter l'appli à l'ouverture.
 */
class AppImagesTest {
    private val resDir = listOf("../app/src/main/res", "app/src/main/res").map(::File).firstOrNull { it.isDirectory }
        ?: error("dossier res introuvable")

    private val images = resDir.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
        .toList()

    @Test
    fun `chaque image est complete et lisible`() {
        for (f in images) {
            val bytes = f.readBytes()
            when (f.extension.lowercase()) {
                "png" -> {
                    val iend = byteArrayOf(0x49, 0x45, 0x4E, 0x44) // « IEND »
                    assertTrue("${f.name} : fin de PNG absente (fichier tronqué)", bytes.size > 12 &&
                        bytes.copyOfRange(bytes.size - 8, bytes.size - 4).contentEquals(iend))
                    assertNotNull("${f.name} : PNG illisible", ImageIO.read(f))
                }
                "jpg", "jpeg" -> {
                    assertTrue("${f.name} : fin de JPEG absente (fichier tronqué)", bytes.size > 4 &&
                        bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte())
                    assertNotNull("${f.name} : JPEG illisible", ImageIO.read(f))
                }
                else -> assertTrue("${f.name} : fichier vide", bytes.isNotEmpty())
            }
        }
    }

    @Test
    fun `l'emblème et l'icone CYRAVA sont presents`() {
        val names = images.map { it.name }.toSet()
        assertTrue("cyrava_emblem.png absent", "cyrava_emblem.png" in names)
        assertTrue("ic_launcher_cyrava.png absent", "ic_launcher_cyrava.png" in names)
    }
}
