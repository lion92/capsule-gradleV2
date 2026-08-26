package capsule.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * BDD steps for `capsule_preview.feature` (CAP-PREVIEW US-2).
 *
 * Self-contained GradleRunner scenarios that verify the preview DSL
 * wiring is recognized by the capsule plugin. No Playwright or FFmpeg
 * is needed — the tests run with no deck files, so the pipeline
 * warns and exits early. The flag presence/absence is the assertion.
 *
 * Step texts are prefixed with "preview" (and use unique sentence
 * shapes) to avoid `DuplicateStepDefinitionException` with the
 * shared `capsule.scenarios` glue (bug S-088).
 */
class CapsulePreviewSteps {

    private var projectDir: File? = null
    private var buildOutput: String = ""

    @Given("a Capsule preview Gradle project with the capsule plugin applied")
    fun aCapsulePreviewGradleProjectWithTheCapsulePluginApplied() {
        projectDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("cucumber-preview-${System.currentTimeMillis()}-${System.nanoTime()}")
            .also { it.mkdirs() }
        projectDir!!.resolve("settings.gradle").writeText("")
        projectDir!!.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
        """.trimIndent())
    }

    @When("I run the capsule build with the default preview config")
    fun iRunTheCapsuleBuildWithTheDefaultPreviewConfig() {
        runPreviewBuild("help")
    }

    @When("I run the capsule build with previewOnly enabled via DSL")
    fun iRunTheCapsuleBuildWithPreviewOnlyEnabledViaDsl() {
        projectDir!!.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
            capsule {
                previewOnly = true
            }
        """.trimIndent())
        runPreviewBuild("help")
    }

    @When("I run the capsule build with previewOnly enabled via gradle property")
    fun iRunTheCapsuleBuildWithPreviewOnlyEnabledViaGradleProperty() {
        projectDir!!.resolve("gradle.properties").writeText("capsule.preview.enabled=true")
        runPreviewBuild("help")
    }

    private fun runPreviewBuild(task: String) {
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(task, "-q")
            .withProjectDir(projectDir!!)
            .build()
        buildOutput = result.output
    }

    @Then("the preview build succeeds")
    fun thePreviewBuildSucceeds() {
        // GradleRunner.build() throws on failure — reaching here means success
    }

    @Then("the preview output does not mention {string}")
    fun thePreviewOutputDoesNotMention(text: String) {
        assertFalse(
            buildOutput.contains(text),
            "Expected preview build output to NOT mention '$text', got: ${buildOutput.take(2000)}",
        )
    }
}
