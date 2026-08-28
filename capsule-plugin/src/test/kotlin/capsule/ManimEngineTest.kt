package capsule

import java.io.File
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ManimEngineTest {

    // ─── NoOp engine ─────────────────────────────────────────────

    @Test
    fun `noop engine is available and named noop`() {
        val engine = NoOpManimEngine()
        assertTrue(engine.isAvailable())
        assertEquals("noop", engine.name())
    }

    @Test
    fun `noop engine render creates placeholder file`() {
        val engine = NoOpManimEngine()
        val tmpDir = File.createTempFile("manim-test", "")
        tmpDir.delete()
        tmpDir.mkdirs()
        tmpDir.deleteOnExit()

        val scriptFile = File.createTempFile("manim-scene", ".py", tmpDir)
        scriptFile.deleteOnExit()

        val outputDir = tmpDir.resolve("media")
        val result = engine.render("MyScene", scriptFile, outputDir)

        assertTrue(result.exists())
        assertTrue(result.readText().contains("MANIM PLACEHOLDER"))
    }

    @Test
    fun `noop engine probeDuration returns zero`() {
        val engine = NoOpManimEngine()
        val dummyFile = File.createTempFile("video", ".mp4")
        dummyFile.deleteOnExit()
        assertEquals(0.0, engine.probeDuration(dummyFile))
    }

    // ─── ManimEngineImpl (sans vrai manim) ──────────────────────

    @Test
    fun `manim engine reports unavailable when manim not installed`() {
        val config = ManimConfig(executablePath = "/nonexistent/path/manim")
        val engine = ManimEngineImpl(config)
        assertEquals(false, engine.isAvailable())
    }

    @Test
    fun `manim engine throws ManimException when not available and render called`() {
        val config = ManimConfig(executablePath = "/nonexistent/path/manim")
        val engine = ManimEngineImpl(config)
        val tmpFile = File.createTempFile("capsule-test", ".py")
        tmpFile.deleteOnExit()

        assertFailsWith<ManimException> {
            engine.render("MyScene", tmpFile, tmpFile.parentFile)
        }
    }

    @Test
    fun `manim engine name returns manim`() {
        val engine = ManimEngineImpl(ManimConfig())
        assertEquals("manim", engine.name())
    }

    /**
     * Le chemin est explicitement introuvable.
     *
     * Ces deux cas s'appuyaient sur `ManimConfig()` par défaut — donc sur le
     * binaire `manim` du PATH — en supposant « aucun manim dans l'environnement
     * de test ». Sur une machine où Manim *est* installé (celle qui produit les
     * capsules, précisément), la sonde rendait vrai et le test tombait sans que
     * rien ne soit cassé. Ce qui est vérifié ici, c'est qu'un chemin absent
     * donne « indisponible », pas ce que la machine a d'installé.
     */
    @Test
    fun `manim engine keeps its quality config and reports a missing binary unavailable`() {
        val engine = ManimEngineImpl(ManimConfig(executablePath = "/nonexistent/path/manim", quality = "h"))
        assertEquals(false, engine.isAvailable())
    }

    @Test
    fun `manim engine default config keeps the manim name`() {
        assertEquals("manim", ManimEngineImpl(ManimConfig()).name())
        assertEquals("manim", ManimConfig().executablePath)
    }

    @Test
    fun `manim engine with noop executablePath still uses ManimConfig`() {
        // NoOpManimEngine is used when executablePath is "noop", but ManimEngineImpl
        // should also correctly handle a "noop" config (it won't be available)
        val config = ManimConfig(executablePath = "noop")
        // Note: in practice, NoOpManimEngine is returned by resolveManimEngine()
        // when executablePath == "noop". But ManimEngineImpl with "noop" path
        // should simply report as unavailable (no binary named "noop" on PATH)
        val engine = ManimEngineImpl(config)
        assertEquals(false, engine.isAvailable(), "ManimEngineImpl with 'noop' path should report unavailable")
    }

    // ─── CAP-28 US-2 — Coverage gaps (probeDuration delegation) ───

    @Test
    fun `manim engine probeDuration delegates to MediaProbeUtil and returns zero for missing file`() {
        val engine = ManimEngineImpl(ManimConfig(executablePath = "/nonexistent/path/manim"))
        val missingFile = File("/nonexistent/video.mp4")
        assertEquals(0.0, engine.probeDuration(missingFile))
    }

    @Test
    fun `manim engine probeDuration returns zero for nonexistent file path`() {
        val engine = ManimEngineImpl(ManimConfig())
        val nonexistent = File("/tmp/capsule-test-nonexistent-${System.currentTimeMillis()}.mp4")
        assertEquals(0.0, engine.probeDuration(nonexistent))
    }

    // ─── Localisation du rendu (régression) ─────────────────────

    /**
     * Fabrique un faux binaire `manim` : il honore `--media_dir`, écrit son
     * MP4 dans le dossier de *résolution* que le vrai Manim utilise, et vérifie
     * au passage qu'on lui a bien passé `--media_dir`.
     */
    private fun fakeManim(dir: File, resolutionDir: String, verbose: Boolean = false): File {
        val script = File(dir, "fake-manim").apply {
            writeText(
                """
                #!/bin/sh
                if [ "${'$'}1" = "--version" ]; then echo "Manim Community v0.18.0"; exit 0; fi
                media_dir=""
                scene=""
                script_path=""
                while [ ${'$'}# -gt 0 ]; do
                  case "${'$'}1" in
                    --media_dir) media_dir="${'$'}2"; shift 2 ;;
                    -q*) shift ;;
                    *.py) script_path="${'$'}1"; shift ;;
                    *) scene="${'$'}1"; shift ;;
                  esac
                done
                if [ -z "${'$'}media_dir" ]; then echo "pas de --media_dir" 1>&2; exit 2; fi
                stem=`basename "${'$'}script_path" .py`
                out="${'$'}media_dir/videos/${'$'}stem/$resolutionDir"
                mkdir -p "${'$'}out"
                echo "faux rendu" > "${'$'}out/${'$'}scene.mp4"
                ${if (verbose) "i=0; while [ ${'$'}i -lt 20000 ]; do echo rendu-image-${'$'}i; i=`expr ${'$'}i + 1`; done" else "echo 'rendu termine'"}
                exit 0
                """.trimIndent()
            )
            setExecutable(true)
        }
        return script
    }

    /**
     * Le dossier feuille porte la *résolution*, jamais la lettre de qualité :
     * `-ql` donne `480p15`, `-qm` `720p30`. Le chemin était construit comme
     * `"${'$'}{config.quality}p60"`, donc `lp60` — un dossier que Manim ne crée
     * jamais. Tout rendu réel échouait sur « Expected output not found » alors
     * que le fichier était là, un dossier à côté.
     */
    @Test
    fun `render finds the mp4 in the resolution directory manim actually uses`(@TempDir tempDir: File) {
        val manim = fakeManim(tempDir, "480p15")
        val scriptsDir = File(tempDir, "scripts").apply { mkdirs() }
        val scriptPath = File(scriptsDir, "MaScene.py").apply { writeText("# scene") }
        val outputDir = File(tempDir, "out")

        val engine = ManimEngineImpl(ManimConfig(executablePath = manim.absolutePath, quality = "l"))
        val rendered = engine.render("MaScene", scriptPath, outputDir)

        assertTrue(rendered.isFile, "le MP4 rendu doit être retrouvé : ${'$'}{rendered.absolutePath}")
        assertEquals("MaScene.mp4", rendered.name)
    }

    @Test
    fun `render also finds the mp4 under another quality directory`(@TempDir tempDir: File) {
        val manim = fakeManim(tempDir, "1080p60")
        val scriptsDir = File(tempDir, "scripts").apply { mkdirs() }
        val scriptPath = File(scriptsDir, "Autre.py").apply { writeText("# scene") }
        val outputDir = File(tempDir, "out")

        val engine = ManimEngineImpl(ManimConfig(executablePath = manim.absolutePath, quality = "h"))

        assertEquals("Autre.mp4", engine.render("Autre", scriptPath, outputDir).name)
    }

    /**
     * Manim est bavard : une ligne par image rendue. L'ancien appel attendait
     * la fin sans jamais vider le tube fusionné de 64 Kio, donc se figeait.
     * Le test échoue par le délai de garde de la suite si la régression revient.
     */
    @Test
    fun `render survives a very verbose manim`(@TempDir tempDir: File) {
        val manim = fakeManim(tempDir, "480p15", verbose = true)
        val scriptsDir = File(tempDir, "scripts").apply { mkdirs() }
        val scriptPath = File(scriptsDir, "Bavarde.py").apply { writeText("# scene") }

        val engine = ManimEngineImpl(ManimConfig(executablePath = manim.absolutePath, quality = "l"))

        assertEquals("Bavarde.mp4", engine.render("Bavarde", scriptPath, File(tempDir, "out")).name)
    }

    @Test
    fun `render reports a manim that produced nothing`(@TempDir tempDir: File) {
        val silent = File(tempDir, "silent-manim").apply {
            writeText("#!/bin/sh\nexit 0\n")
            setExecutable(true)
        }
        val scriptPath = File(tempDir, "Absente.py").apply { writeText("# scene") }

        val engine = ManimEngineImpl(ManimConfig(executablePath = silent.absolutePath, quality = "l"))
        val failure = assertFailsWith<ManimException> {
            engine.render("Absente", scriptPath, File(tempDir, "out"))
        }

        assertTrue(failure.message!!.contains("Absente"), failure.message!!)
    }

    @Test
    fun `render reports a manim that failed, with its output`(@TempDir tempDir: File) {
        val broken = File(tempDir, "broken-manim").apply {
            writeText("#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then exit 0; fi\necho 'ValueError: scene introuvable' 1>&2\nexit 1\n")
            setExecutable(true)
        }
        val scriptPath = File(tempDir, "Cassee.py").apply { writeText("# scene") }

        val engine = ManimEngineImpl(ManimConfig(executablePath = broken.absolutePath, quality = "l"))
        val failure = assertFailsWith<ManimException> {
            engine.render("Cassee", scriptPath, File(tempDir, "out"))
        }

        assertTrue(failure.message!!.contains("exited with code 1"), failure.message!!)
        assertTrue(
            failure.message!!.contains("scene introuvable"),
            "le diagnostic de manim doit remonter, pas seulement le code : ${failure.message}"
        )
    }
}
