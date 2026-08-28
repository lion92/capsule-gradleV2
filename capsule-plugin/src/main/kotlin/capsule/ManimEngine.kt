package capsule

import java.io.File

interface ManimEngine {
    fun render(sceneName: String, scriptPath: File, outputDir: File): File
    fun isAvailable(): Boolean
    fun name(): String
    fun probeDuration(videoFile: File): Double
}

class ManimEngineImpl(
    private val config: ManimConfig = ManimConfig()
) : ManimEngine {

    // Sondé une fois : `render()` vérifie la disponibilité et le renderer
    // parallèle appelle `render()` une fois par diapo — sinon c'est un
    // `manim --version` de plus par diapo, lancés en parallèle.
    private var availabilityProbe: Boolean? = null

    override fun isAvailable(): Boolean =
        availabilityProbe ?: ProcessRunner.probe(config.executablePath, "--version")
            .also { availabilityProbe = it }

    override fun name(): String = "manim"

    override fun render(sceneName: String, scriptPath: File, outputDir: File): File {
        if (!isAvailable()) {
            throw ManimException("Manim executable not found at: ${config.executablePath}")
        }

        outputDir.mkdirs()

        // `--media_dir` est ce qui rend la sortie trouvable. Sans lui Manim pose
        // son arborescence `media/` relativement à son *répertoire courant* —
        // celui du démon Gradle, pas celui du script — et le fichier rendu
        // atterrit là où personne ne le cherche.
        val args = listOf(
            config.executablePath,
            "-q${config.quality}",
            "--media_dir", outputDir.absolutePath,
            scriptPath.absolutePath,
            sceneName,
        )

        // Manim est très bavard : barre de progression, une ligne par animation.
        // La sortie fusionnée part dans un journal, jamais dans un tube que
        // personne ne vide (le rendu se figeait au-delà de 64 Kio).
        val result = ProcessRunner.run(args, workingDir = scriptPath.parentFile)
        if (result.timedOut) {
            throw ManimException("Manim render of '$sceneName' timed out: ${result.tail()}")
        }
        if (!result.isSuccess) {
            throw ManimException("Manim exited with code ${result.exitCode}: ${result.tail()}")
        }

        return findRendered(sceneName, scriptPath, outputDir)
            ?: throw ManimException(
                "Manim reported success but no '$sceneName.mp4' was found under ${outputDir.absolutePath}"
            )
    }

    /**
     * Locates the MP4 Manim just wrote.
     *
     * The leaf directory is named after the *resolution*, not after the quality
     * flag: `-ql` gives `480p15`, `-qm` `720p30`, `-qh` `1080p60`. Building the
     * path as `"${config.quality}p60"` produced `lp60`, a directory Manim never
     * creates — every real render failed with "Expected output not found" while
     * the file sat one folder away. The tree is walked instead of guessed, the
     * most recent match winning when several qualities have been rendered.
     */
    private fun findRendered(sceneName: String, scriptPath: File, outputDir: File): File? {
        val roots = listOf(outputDir, scriptPath.parentFile.resolve("media"))
        return roots
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().maxDepth(MEDIA_TREE_DEPTH).toList() }
            .filter { it.isFile && it.name == "$sceneName.mp4" && it.length() > 0 }
            .maxByOrNull { it.lastModified() }
    }

    override fun probeDuration(videoFile: File): Double = MediaProbeUtil.probeDuration(videoFile)

    private companion object {
        /** `<media_dir>/videos/<script>/<resolution>/<Scene>.mp4` — quatre niveaux, plus une marge. */
        const val MEDIA_TREE_DEPTH: Int = 6
    }
}

class NoOpManimEngine : ManimEngine {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop"

    override fun render(sceneName: String, scriptPath: File, outputDir: File): File {
        outputDir.mkdirs()
        val placeholderFile = outputDir.resolve("${sceneName}.mp4")
        val placeholder = listOf(
            "# MANIM PLACEHOLDER (noop engine)",
            "# Scene: $sceneName",
            "# Script: ${scriptPath.name}"
        ).joinToString("\n")
        placeholderFile.writeText(placeholder)
        return placeholderFile
    }

    override fun probeDuration(videoFile: File): Double = 0.0
}

class ManimException(message: String) : RuntimeException(message)
