package capsule

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [FileReplace] — la bascule `.tmp` → montage final.
 *
 * Les trois passes de post-production (mixage audio, gravure des sous-titres,
 * post-production sonore) écrivaient `tmp.renameTo(final)` en jetant la valeur
 * de retour, puis annonçaient le succès dans le journal. Ces tests fixent le
 * contrat : le remplacement rend vrai *et* la cible porte le nouveau contenu,
 * ou il rend faux.
 */
class FileReplaceTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `replaces the target content`() {
        val source = File(tempDir, "montage.tmp.mp4").apply { writeText("nouvelle version") }
        val target = File(tempDir, "montage.mp4").apply { writeText("ancienne version") }

        assertTrue(FileReplace.moveOver(source, target))
        assertEquals("nouvelle version", target.readText())
        assertFalse(source.exists(), "la source doit avoir disparu")
    }

    @Test
    fun `creates the target when it does not exist yet`() {
        val source = File(tempDir, "montage.tmp.mp4").apply { writeText("premier jet") }
        val target = File(tempDir, "montage.mp4")

        assertTrue(FileReplace.moveOver(source, target))
        assertEquals("premier jet", target.readText())
    }

    @Test
    fun `answers false when the source is missing`() {
        val source = File(tempDir, "jamais-ecrit.tmp.mp4")
        val target = File(tempDir, "montage.mp4").apply { writeText("intact") }

        assertFalse(FileReplace.moveOver(source, target))
        assertEquals("intact", target.readText(), "un échec ne doit pas abîmer la cible")
    }

    @Test
    fun `answers false when the source is a directory`() {
        val source = File(tempDir, "un-dossier").apply { mkdirs() }
        val target = File(tempDir, "montage.mp4").apply { writeText("intact") }

        assertFalse(FileReplace.moveOver(source, target))
        assertEquals("intact", target.readText())
    }

    /**
     * Le repli copie+efface crée les dossiers manquants : la bascule ne doit pas
     * échouer parce que le dossier de destination n'a encore jamais servi.
     */
    @Test
    fun `creates the missing parent directories of the target`() {
        val source = File(tempDir, "montage.tmp.mp4").apply { writeText("nouvelle version") }
        val target = File(tempDir, "absent/montage.mp4")

        assertTrue(FileReplace.moveOver(source, target))
        assertEquals("nouvelle version", target.readText())
    }

    @Test
    fun `answers false when the target path is a non-empty directory`() {
        val source = File(tempDir, "montage.tmp.mp4").apply { writeText("nouvelle version") }
        val target = File(tempDir, "occupe").apply { mkdirs() }
        File(target, "dedans.txt").writeText("x")

        assertFalse(FileReplace.moveOver(source, target))
        assertTrue(source.exists(), "la source doit rester disponible après un échec")
    }
}
