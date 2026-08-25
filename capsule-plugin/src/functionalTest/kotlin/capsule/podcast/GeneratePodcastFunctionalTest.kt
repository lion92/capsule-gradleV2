package capsule.podcast

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

/**
 * Functional tests for the `generateCapsulePodcast` Gradle task
 * (CAP-PODCAST US-2).
 *
 * Verifies:
 *  - task registration with the `generate` group
 *  - disabled-by-default no-op skip behavior (economy of ink)
 *  - enabled + NoOp ffmpeg → degraded (no MP3 produced, warns)
 *
 * The LLM mock routes on the prompt body: a podcast concatenation
 * request returns false (NoOp fallback). Zero network, zero ffmpeg.
 */
class GeneratePodcastFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private fun setupBuild(extraConfig: String = "") {
        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
            $extraConfig
        """.trimIndent())
    }

    @Test
    fun `generateCapsulePodcast task is registered in the generate group`() {
        setupBuild()
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("tasks", "--group", "generate")
            .withProjectDir(projectDir)
            .build()
        assertTrue(
            result.output.contains("generateCapsulePodcast"),
            "Expected generateCapsulePodcast in generate group, got: ${result.output}"
        )
    }

    @Test
    fun `disabled by default is a no-op skip`() {
        setupBuild()
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("generateCapsulePodcast", "-x", "generateCapsuleVideo")
            .withProjectDir(projectDir)
            .build()

        assertTrue(
            result.output.contains("skipped") || result.output.contains("podcastEnabled=false"),
            "Expected skip log when podcastEnabled defaults to false, got: ${result.output}"
        )
    }

    @Test
    fun `enabled with NoOp ffmpeg path degrades and produces no MP3`() {
        setupBuild("""
            capsule {
                outputDir = "capsules"
                podcastEnabled = true
                ffmpegExecutablePath = "noop"
            }
        """.trimIndent())
        // Pre-create per-slide MP3 files (produced by generateCapsuleVideo in real pipeline)
        val deckDir = projectDir.resolve("build/capsule/demo").also { it.mkdirs() }
        deckDir.resolve("slide-01.mp3").writeText("fake-mp3")
        deckDir.resolve("slide-02.mp3").writeText("fake-mp3")

        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("generateCapsulePodcast", "-x", "generateCapsuleVideo")
            .withProjectDir(projectDir)
            .build()

        assertTrue(
            result.output.contains("CAPSULE PODCAST"),
            "Expected CAPSULE PODCAST log, got: ${result.output}"
        )
        val mp3 = projectDir.resolve("build/capsule/demo-podcast.mp3")
        assertTrue(!mp3.exists(), "Expected no podcast MP3 (NoOp degraded), but found ${mp3.absolutePath}")
    }
}