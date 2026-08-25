package capsule

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * Functional tests for the Remotion capture strategy (CAP-ANIM US-2).
 *
 * Verifies that `capsule.captureStrategy("remotion")` is wired through
 * `pushConfigIntoExtension` and that `CapsuleVideoTask` resolves the
 * capture engine via `CaptureResolver`.
 *
 * Two scenarios:
 * 1. strategy=REMOTION + missing node binary + non-strict — NoOp fallback,
 *    WebM produced (the NoOp capture writes a placeholder WebM).
 * 2. strategy=REMOTION + missing node binary + strictMode — build fails
 *    with "remotion is not available".
 *
 * The real Remotion render (Node + npm + Chromium) is covered by
 * dogfooding/CI, not by functional tests — it needs network and binaries
 * a test runner cannot assume.
 */
class RemotionCaptureFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    private fun setupBuild(extraConfig: String = "") {
        settingsFile.writeText("")
        buildFile.writeText("""
            plugins {
                id('education.cccp.capsule')
            }
            $extraConfig
        """.trimIndent())
    }

    private fun writeScriptAndDeck() {
        val scriptDir = projectDir.resolve("build/capsule").also { it.mkdirs() }
        scriptDir.resolve("test-script.txt").writeText("""
            === CAPSULE SCRIPT : test ===
            --- SLIDE 1 : Title ---
            Note content for the remotion functional test.
        """.trimIndent())

        val decksDir = projectDir.resolve("build/docs/asciidocRevealJs").also { it.mkdirs() }
        decksDir.resolve("test-deck.html").writeText("""
            <html><body>
            <div class="reveal">
              <div class="slides">
                <section data-capsule-slide="1"><h2>Title</h2></section>
              </div>
            </div>
            </body></html>
        """.trimIndent())
    }

    @Test
    fun `strategy REMOTION with NoOp fallback produces WebM when node is unavailable`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("remotion")
                remotionNodeExecutablePath = "this-node-binary-does-not-exist-functional-remotion"
            }
        """.trimIndent())
        writeScriptAndDeck()

        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("generateCapsuleVideo")
            .withProjectDir(projectDir)
            .build()

        val capFile = projectDir.resolve("build/capsules/test.webm")
        assertTrue(capFile.exists(), "Video must be produced via NoOp fallback when remotion/node is unavailable")
        assertTrue(result.output.contains("remotion strategy"), "Output should mention the remotion strategy")
    }

    @Test
    fun `strategy REMOTION with strictMode fails when node is unavailable`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("remotion")
                remotionNodeExecutablePath = "this-node-binary-does-not-exist-functional-remotion-strict"
                strictMode = true
                manimExecutablePath = "noop"
                ffmpegExecutablePath = "noop"
            }
        """.trimIndent())
        writeScriptAndDeck()

        val exception = assertThrows<UnexpectedBuildFailure> {
            GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("generateCapsuleVideo")
                .withProjectDir(projectDir)
                .build()
        }

        assertTrue(
            exception.message!!.contains("remotion is not available"),
            "Expected strict mode to reject remotion when node is unavailable, got: ${exception.message}"
        )
    }
}