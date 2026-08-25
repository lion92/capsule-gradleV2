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
 * BDD steps for `capsule_remotion.feature` (CAP-ANIM US-2).
 *
 * Every step text is prefixed with "remotion" to avoid
 * `DuplicateStepDefinitionException` with the shared `capsule.scenarios`
 * glue (bug S-088 — glue is classpath-wide, step texts must be unique).
 *
 * The scenarios drive `CaptureStrategy.REMOTION` through the NoOp fallback
 * (a `nodeExecutablePath` pointing at a binary that does not exist), so
 * they never spin a real Node/npm/Chromium render. The real render is
 * covered by dogfooding/CI; here we only assert the wiring, the dispatch
 * and the strict-mode failure.
 */
class CapsuleRemotionSteps {

    private var projectDir: File? = null
    private var buildOutput: String = ""
    private var buildFailure: Throwable? = null

    @Given("a Capsule remotion Gradle project with the capsule plugin applied")
    fun aCapsuleRemotionGradleProjectWithTheCapsulePluginApplied() {
        projectDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("cucumber-remotion-${System.currentTimeMillis()}")
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

    @And("a demo deck and script are present for remotion capture")
    fun aDemoDeckAndScriptArePresentForRemotionCapture() {
        val scriptDir = projectDir!!.resolve("build/capsule").also { it.mkdirs() }
        scriptDir.resolve("demo-script.txt").writeText("""
            === CAPSULE SCRIPT : demo ===
            --- SLIDE 1 : Title ---
            Note content for the remotion capture test.
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

    @When("I generate the capsule video with remotion capture strategy and a missing node binary")
    fun iGenerateTheCapsuleVideoWithRemotionCaptureStrategyAndMissingNode() {
        writeBuildGradle("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("remotion")
                remotionNodeExecutablePath = "this-node-binary-does-not-exist-remotion-cucumber"
            }
        """.trimIndent())
        runRemotionBuild()
    }

    @When("I generate the capsule video with remotion capture strategy, a missing node binary and strictMode enabled")
    fun iGenerateTheCapsuleVideoWithRemotionCaptureStrategyMissingNodeAndStrictMode() {
        writeBuildGradle("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("remotion")
                remotionNodeExecutablePath = "this-node-binary-does-not-exist-remotion-strict"
                strictMode = true
                manimExecutablePath = "noop"
                ffmpegExecutablePath = "noop"
            }
        """.trimIndent())
        runRemotionBuild()
    }

    @When("I generate the capsule video with remotion capture strategy, a missing node binary, fps {int} and concurrency {int}")
    fun iGenerateTheCapsuleVideoWithRemotionCaptureStrategyMissingNodeFpsAndConcurrency(fps: Int, concurrency: Int) {
        writeBuildGradle("""
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
                captureStrategy("remotion")
                remotionNodeExecutablePath = "this-node-binary-does-not-exist-remotion-config"
                remotionFps = $fps
                remotionConcurrency = $concurrency
            }
        """.trimIndent())
        runRemotionBuild()
    }

    private fun runRemotionBuild() {
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

    @Then("the remotion build succeeds")
    fun theRemotionBuildSucceeds() {
        Assertions.assertNull(buildFailure, "Remotion build was expected to succeed but failed: $buildOutput")
    }

    @Then("the remotion build fails")
    fun theRemotionBuildFails() {
        Assertions.assertNotNull(buildFailure, "Remotion build was expected to fail but succeeded: $buildOutput")
    }

    @And("the remotion failure message contains {string}")
    fun theRemotionFailureMessageContains(text: String) {
        Assertions.assertNotNull(buildFailure, "Remotion build was expected to fail but succeeded")
        Assertions.assertTrue(
            buildOutput.contains(text),
            "Expected remotion failure to contain '$text', got: $buildOutput"
        )
    }

    @And("the remotion output mentions {string}")
    fun theRemotionOutputMentions(text: String) {
        Assertions.assertTrue(
            buildOutput.contains(text),
            "Expected remotion output to mention '$text', got: $buildOutput"
        )
    }

    @And("a remotion WebM file is produced")
    fun aRemotionWebmFileIsProduced() {
        val capFile = projectDir!!.resolve("build/capsules/demo.webm")
        Assertions.assertTrue(
            capFile.exists(),
            "Expected WebM file at ${capFile.absolutePath}, but it was not produced"
        )
    }
}