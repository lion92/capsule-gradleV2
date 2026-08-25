package capsule

import org.gradle.api.logging.Logging
import java.io.File

/**
 * Materialises the Remotion composition bundled with the plugin (CAP-ANIM).
 *
 * The composition is shipped as plugin resources so consumers get animated
 * capsules without authoring React. It is written into the project directory on
 * first use and refreshed whenever the plugin ships a newer copy; the Node
 * dependency tree is installed once, next to it.
 */
object RemotionTemplate {

    private val logger = Logging.getLogger(RemotionTemplate::class.java)

    /** Resource paths, relative to `capsule/remotion/` in the plugin jar. */
    internal val FILES: List<String> = listOf(
        "package.json",
        "render.mjs",
        "src/index.js",
        "src/Root.jsx",
        "src/Capsule.jsx",
    )

    private const val RESOURCE_ROOT = "capsule/remotion"

    /**
     * Copies the composition into [projectDir] and makes sure its dependencies
     * are installed.
     *
     * Files are rewritten on every call: they are plugin-owned, and a stale copy
     * from an older plugin version is a silent source of rendering differences.
     */
    fun materialiseInto(projectDir: File) {
        projectDir.mkdirs()
        FILES.forEach { relative ->
            val target = File(projectDir, relative)
            target.parentFile.mkdirs()
            val bytes = readResource("$RESOURCE_ROOT/$relative")
            if (!target.exists() || !target.readBytes().contentEquals(bytes)) {
                target.writeBytes(bytes)
            }
        }
        ensureDependencies(projectDir)
    }

    private fun readResource(path: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: throw IllegalStateException(
                "Remotion template resource '$path' missing from the plugin jar"
            )

    /**
     * Installs the Node dependency tree when it is absent.
     *
     * `npm install` needs network the first time. When it is unavailable, point
     * the build at an existing tree by symlinking `node_modules` into the
     * project directory rather than letting the render fail deep inside the
     * bundler with an opaque message.
     */
    private fun ensureDependencies(projectDir: File) {
        val nodeModules = File(projectDir, "node_modules")
        if (nodeModules.exists()) return

        logger.lifecycle("  Remotion: installing the composition dependencies (first run)")
        val logFile = File(projectDir, "npm-install.log")
        val exitCode = try {
            ProcessBuilder("npm", "install", "--no-audit", "--no-fund", "--loglevel", "error")
                .directory(projectDir)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
                .start()
                .waitFor()
        } catch (e: Exception) {
            throw CapturingException(
                "Remotion needs npm to install its dependencies, and npm could not be run " +
                    "(${e.message}). Install Node/npm, or provide ${nodeModules.absolutePath} yourself."
            )
        }
        if (exitCode != 0) {
            val tail = logFile.takeIf { it.exists() }
                ?.readLines()
                ?.takeLast(15)
                ?.joinToString(System.lineSeparator())
                .orEmpty()
            throw CapturingException(
                "Remotion dependency install failed (npm exit $exitCode): $tail"
            )
        }
        logFile.delete()
    }
}
