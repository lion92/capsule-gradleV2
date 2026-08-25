package capsule

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import capsule.test.PlaywrightTestAssumption.assumePlaywrightAvailable

/**
 * Functional tests for capture strategy (CAP-CR3-3 US-4).
 *
 * Verifies that `capsule.captureStrategy` DSL property is wired through
 * `pushConfigIntoExtension` and that `CapsuleVideoTask` resolves the
 * capture engine via `CaptureResolver`.
 *
 * Three scenarios:
 * 1. strategy=PLAYWRIGHT (default) — backward compat, existing behavior.
 * 2. strategy=SCREENSHOT + NoOp fallback (screenshot unavailable) — WebM produced.
 * 3. strategy=SCREENSHOT + strictMode + ffmpeg unavailable — build fails
 *    (screenshot requires both Playwright and ffmpeg; strict mode refuses NoOp).
 *
 * Note: the "screenshot unavailable + strict → fail" scenario is covered by
 * `CaptureResolverTest` (unit tests) because forcing `ScreenshotCaptureImpl.isAvailable()`
 * to return false requires either mocking or uninstalling Playwright, which is
 * environment-dependent. The functional test #3 verifies that strict mode
 * correctly rejects the NoOp fallback when ffmpeg is unavailable.
 */
class CaptureStrategyFunctionalTest {

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
            Note content.
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
    fun `strategy PLAYWRIGHT default preserves backward compat`() {
        assumePlaywrightAvailable()
        setupBuild("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
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
        assertTrue(capFile.exists(), "Video must be produced with default PLAYWRIGHT strategy")
    }

    @Test
    fun `strategy SCREENSHOT with NoOp fallback produces WebM`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("screenshot")
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
        assertTrue(capFile.exists(), "Video must be produced via NoOp fallback when screenshot unavailable")
    }

    @Test
    fun `strategy SCREENSHOT with strictMode fails when ffmpeg is unavailable`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("screenshot")
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
            exception.message!!.contains("screenshot is not available"),
            "Expected strict mode to reject screenshot when ffmpeg is unavailable, got: ${exception.message}"
        )
    }
}