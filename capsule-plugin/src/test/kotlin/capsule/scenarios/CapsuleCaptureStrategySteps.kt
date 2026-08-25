package capsule.scenarios

import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions
import java.io.File

/**
 * BDD steps for `capsule_capture_strategy.feature` (CAP-CR3-3-5).
 *
 * All step definitions are prefixed with "capture strategy" signatures to
 * avoid `DuplicateStepDefinitionException` with the shared
 * `capsule.scenarios` glue (bug S-088 — glue is classpath-wide, step texts
 * must be unique).
 */
class CapsuleCaptureStrategySteps {

    private var projectDir: File? = null
    private var buildOutput: String = ""
    private var buildFailure: Throwable? = null

    @Given("a Capsule capture strategy Gradle project with the capsule plugin applied")
    fun aCapsuleCaptureStrategyGradleProjectWithTheCapsulePluginApplied() {
        projectDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("cucumber-capture-strategy-${System.currentTimeMillis()}")
            .also { it.mkdirs() }
        projectDir!!.resolve("settings.gradle").writeText("")
        writeBuildGradle("")
    }

    private fun writeBuildGradle(capsuleBlock: String) {
        projectDir!!.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
            $capsuleBlock
        """.trimIndent())
    }

    @And("a demo deck and script are present for capture strategy")
    fun aDemoDeckAndScriptArePresentForCaptureStrategy() {
        val scriptDir = projectDir!!.resolve("build/capsule").also { it.mkdirs() }
        scriptDir.resolve("demo-script.txt").writeText("""
            === CAPSULE SCRIPT : demo ===
            --- SLIDE 1 : Title ---
            Note content for capture strategy test.
        """.trimIndent())

        val decksDir = projectDir!!.resolve("build/docs/asciidocRevealJs").also { it.mkdirs() }
        decksDir.resolve("demo-deck.html").writeText("""
            <html><body>
            <div class="reveal">
              <div class="slides">
                <section data-capsule-slide="1"><h2>Title</h2></section>
              </div>
            </div>
            </body></html>
        """.trimIndent())
    }

    @When("I generate the capsule video with the default capture strategy")
    fun iGenerateTheCapsuleVideoWithTheDefaultCaptureStrategy() {
        writeBuildGradle("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
            }
        """.trimIndent())
        runCaptureStrategyBuild()
    }

    @When("I generate the capsule video with capture strategy {string}")
    fun iGenerateTheCapsuleVideoWithCaptureStrategy(strategy: String) {
        writeBuildGradle("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("$strategy")
            }
        """.trimIndent())
        runCaptureStrategyBuild()
    }

    @When("I generate the capsule video with capture strategy {string} and strictMode enabled")
    fun iGenerateTheCapsuleVideoWithCaptureStrategyAndStrictMode(strategy: String) {
        writeBuildGradle("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("$strategy")
                strictMode = true
                manimExecutablePath = "noop"
                ffmpegExecutablePath = "noop"
            }
        """.trimIndent())
        runCaptureStrategyBuild()
    }

    private fun runCaptureStrategyBuild() {
        try {
            val result = GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("generateCapsuleVideo")
                .withProjectDir(projectDir!!)
                .build()
            buildOutput = result.output
        } catch (e: UnexpectedBuildFailure) {
            buildOutput = e.message ?: ""
            buildFailure = e
        }
    }

    @Then("the capture strategy build succeeds")
    fun theCaptureStrategyBuildSucceeds() {
        Assertions.assertNull(buildFailure, "Build was expected to succeed but failed: $buildOutput")
    }

    @Then("the capture strategy build fails")
    fun theCaptureStrategyBuildFails() {
        Assertions.assertNotNull(buildFailure, "Build was expected to fail but succeeded: $buildOutput")
    }

    @And("the capture strategy failure message contains {string}")
    fun theCaptureStrategyFailureMessageContains(text: String) {
        Assertions.assertNotNull(buildFailure, "Build was expected to fail but succeeded")
        Assertions.assertTrue(
            buildOutput.contains(text),
            "Expected failure message to contain '$text', got: $buildOutput"
        )
    }

    @And("the capture strategy output mentions {string}")
    fun theCaptureStrategyOutputMentions(text: String) {
        Assertions.assertTrue(
            buildOutput.contains(text),
            "Expected build output to mention '$text', got: $buildOutput"
        )
    }

    @And("a capture strategy WebM file is produced")
    fun aCaptureStrategyWebmFileIsProduced() {
        val capFile = projectDir!!.resolve("build/capsules/demo.webm")
        Assertions.assertTrue(
            capFile.exists(),
            "Expected WebM file at ${capFile.absolutePath}, but it was not produced"
        )
    }
}