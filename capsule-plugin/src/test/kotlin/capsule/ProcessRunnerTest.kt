package capsule

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ProcessRunner] — la brique qui remplace le motif
 * `ProcessBuilder(...).redirectErrorStream(true).start().waitFor()` répandu
 * dans le plugin.
 *
 * Le scénario qui compte est le dernier : une commande dont la sortie dépasse
 * le tube de 64 Kio. L'ancien motif s'y figeait pour toujours ; ces tests
 * échouent par le délai de garde de la suite si la régression revient.
 */
class ProcessRunnerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `succeeds and captures stdout`() {
        val result = ProcessRunner.run("sh", "-c", "echo bonjour")
        assertTrue(result.isSuccess)
        assertEquals("bonjour", result.output.trim())
    }

    @Test
    fun `merges stderr into the captured output`() {
        val result = ProcessRunner.run("sh", "-c", "echo sur-stderr 1>&2")
        assertTrue(result.isSuccess)
        assertTrue(result.output.contains("sur-stderr"), "stderr doit être fusionné : ${result.output}")
    }

    @Test
    fun `reports the exit code of a failing command`() {
        val result = ProcessRunner.run("sh", "-c", "echo boum 1>&2; exit 3")
        assertFalse(result.isSuccess)
        assertEquals(3, result.exitCode)
        assertTrue(result.output.contains("boum"))
    }

    @Test
    fun `writes stdin and closes it`() {
        val result = ProcessRunner.run(listOf("cat"), stdin = "texte de narration")
        assertTrue(result.isSuccess, "cat doit sortir quand son entrée est fermée")
        assertEquals("texte de narration", result.output.trim())
    }

    @Test
    fun `runs in the requested working directory`() {
        val marker = File(tempDir, "temoin.txt")
        val result = ProcessRunner.run(listOf("sh", "-c", "echo ici > temoin.txt"), workingDir = tempDir)
        assertTrue(result.isSuccess)
        assertTrue(marker.isFile, "le fichier doit être écrit dans workingDir, pas dans le CWD du démon")
    }

    @Test
    fun `keeps the log file when one is provided`() {
        val log = File(tempDir, "sous/rendu.log")
        ProcessRunner.run(listOf("sh", "-c", "echo trace"), logFile = log)
        assertTrue(log.isFile)
        assertTrue(log.readText().contains("trace"))
    }

    @Test
    fun `kills a command that overruns the timeout`() {
        val result = ProcessRunner.run(listOf("sleep", "120"), timeoutMinutes = 0)
        assertTrue(result.timedOut, "exitCode attendu ${ProcessRunner.TIMED_OUT}, obtenu ${result.exitCode}")
    }

    @Test
    fun `probe answers false for a missing binary`() {
        assertFalse(ProcessRunner.probe("/nexiste/pas/ffmpeg", "-version"))
    }

    @Test
    fun `probe answers true for a command that exits zero`() {
        assertTrue(ProcessRunner.probe("sh", "-c", "exit 0"))
    }

    /**
     * Le tube d'un `ProcessBuilder` fait 64 Kio. L'ancien motif — attendre la
     * fin sans jamais vider ce tube — se bloquait dès qu'une commande le
     * remplissait, ce que ffmpeg et manim font sans effort. On produit
     * délibérément plus d'un mégaoctet.
     */
    @Test
    fun `does not deadlock on an output larger than the pipe buffer`() {
        val result = ProcessRunner.run(
            listOf("sh", "-c", "i=0; while [ \$i -lt 20000 ]; do echo ligne-de-remplissage-numero-\$i; i=\$((i+1)); done"),
            timeoutMinutes = 2,
        )
        assertTrue(result.isSuccess, "la commande doit se terminer, pas se figer sur un tube plein")
        assertTrue(result.output.contains("ligne-de-remplissage-numero-19999"), "la fin de la sortie doit être conservée")
    }

    /**
     * La barre de progression de ffmpeg n'écrit que des retours chariot : le
     * journal peut dépasser la borne sans contenir un seul saut de ligne. Jeter
     * « la ligne partielle de tête » effaçait alors la totalité du diagnostic.
     */
    @Test
    fun `keeps the output of a command that never emits a newline`() {
        val result = ProcessRunner.run(
            listOf("sh", "-c", "i=0; while [ \$i -lt 20000 ]; do printf \"avancement-\$i\\r\"; i=\$((i+1)); done"),
            timeoutMinutes = 2,
        )
        assertTrue(result.isSuccess)
        assertTrue(result.output.isNotBlank(), "sans saut de ligne, la sortie ne doit pas être vidée")
        assertTrue(
            result.output.contains("avancement-19999"),
            "la fin doit survivre : ${result.output.takeLast(80)}"
        )
    }

    @Test
    fun `caps the captured output and keeps its tail`() {
        val result = ProcessRunner.run(
            listOf("sh", "-c", "i=0; while [ \$i -lt 20000 ]; do echo ligne-de-remplissage-numero-\$i; i=\$((i+1)); done"),
            timeoutMinutes = 2,
        )
        assertTrue(
            result.output.toByteArray().size <= ProcessRunner.MAX_CAPTURED_BYTES,
            "la sortie relue doit rester bornée, obtenu ${result.output.toByteArray().size} octets"
        )
        assertEquals(3, result.tail(3).lines().size)
    }
}
