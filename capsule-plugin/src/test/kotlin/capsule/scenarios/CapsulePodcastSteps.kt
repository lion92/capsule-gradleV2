package capsule.scenarios

import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions
import java.io.File

/**
 * BDD steps for `capsule_podcast.feature` (CAP-PODCAST US-2).
 *
 * Self-contained GradleRunner scenarios (pattern `CapsuleAudioPostSteps`):
 * each scenario sets up an isolated temp project with the capsule plugin,
 * a fake deck + per-slide MP3 files, then runs `generateCapsulePodcast`
 * with the appropriate `podcast*` DSL config and asserts the podcast log +
 * MP3 output.
 *
 * Step texts are prefixed with "podcast" (and use unique sentence shapes) to
 * avoid `DuplicateStepDefinitionException` with the shared `capsule.scenarios`
 * glue (bug S-088 — glue is classpath-wide, step texts must be unique).
 *
 * Uses a NoOp concatenator (no real FFmpeg — `ffmpegExecutablePath = "noop"`
 * routes through `NoOpPodcastConcatenator` which returns `false`, keeping no
 * podcast). The scenarios validate the wiring + economy-of-ink guard + factory
 * dispatch, not the concat itself (covered by unit tests for
 * `PodcastConcatenatorImpl` / `PodcastConcatCommand` from US-1).
 */
class CapsulePodcastSteps {

    private var projectDir: File? = null
    private var buildOutput: String = ""

    @Given("a Capsule podcast Gradle project with the capsule plugin applied")
    fun aCapsulePodcastGradleProjectWithTheCapsulePluginApplied() {
        projectDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("cucumber-podcast-${System.currentTimeMillis()}-${System.nanoTime()}")
            .also { it.mkdirs() }
        projectDir!!.resolve("settings.gradle").writeText("")
        writeBuildGradle("")
    }

    @And("a demo deck with per-slide MP3 files present for podcast")
    fun aDemoDeckWithPerSlideMp3FilesPresentForPodcast() {
        val deckDir = projectDir!!.resolve("build/capsule/demo").also { it.mkdirs() }
        // Per-slide TTS MP3 files (produced by synthesizeTtsForScript in real pipeline).
        // Empty placeholder MP3s — the NoOp concatenator returns false without reading them.
        deckDir.resolve("slide-01.mp3").writeText("fake-mp3")
        deckDir.resolve("slide-02.mp3").writeText("fake-mp3")
    }

    @And("a demo deck is present but has no per-slide MP3 files for podcast")
    fun aDemoDeckIsPresentButHasNoPerSlideMp3FilesForPodcast() {
        projectDir!!.resolve("build/capsule/demo").also { it.mkdirs() }
    }

    private fun writeBuildGradle(capsuleBlock: String) {
        projectDir!!.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
            $capsuleBlock
        """.trimIndent())
    }

    @When("I generate the capsule podcast with the default podcast config")
    fun iGenerateTheCapsulePodcastWithTheDefaultPodcastConfig() {
        writeBuildGradle("""
            capsule {
                outputDir = "capsules"
            }
        """.trimIndent())
        runPodcastBuild()
    }

    @When("I generate the capsule podcast with podcast enabled and a NoOp ffmpeg path")
    fun iGenerateTheCapsulePodcastWithPodcastEnabledAndANoOpFfmpegPath() {
        writeBuildGradle("""
            capsule {
                outputDir = "capsules"
                podcastEnabled = true
                ffmpegExecutablePath = "noop"
            }
        """.trimIndent())
        runPodcastBuild()
    }

    private fun runPodcastBuild() {
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("generateCapsulePodcast", "-x", "generateCapsuleVideo")
            .withProjectDir(projectDir!!)
            .build()
        buildOutput = result.output
    }

    @Then("the podcast build succeeds")
    fun thePodcastBuildSucceeds() {
        // If we reach here, the build succeeded (GradleRunner.build() throws on failure)
    }

    @And("the podcast output mentions {string}")
    fun thePodcastOutputMentions(text: String) {
        Assertions.assertTrue(
            buildOutput.contains(text),
            "Expected podcast build output to mention '$text', got: ${buildOutput.take(2000)}",
        )
    }

    @And("no podcast MP3 file is produced")
    fun noPodcastMp3FileIsProduced() {
        val mp3 = projectDir!!.resolve("build/capsule/demo-podcast.mp3")
        Assertions.assertFalse(
            mp3.exists(),
            "Expected no podcast MP3 (NoOp degraded), but found ${mp3.absolutePath}",
        )
    }
}