package capsule.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * BDD steps for `capsule_chapters.feature` (CAP-CHAPITRE).
 *
 * Self-contained GradleRunner scenarios that verify the chapters DSL
 * wiring is recognized by the capsule plugin. The task writes files
 * to build/capsule/chapters/ — the assertions check file presence.
 *
 * Step texts are prefixed with "chapters" to avoid
 * DuplicateStepDefinitionException with the shared glue (bug S-088).
 */
class CapsuleChaptersSteps {

    private var projectDir: File? = null
    private var buildOutput: String = ""

    @Given("a Capsule chapters Gradle project with the capsule plugin applied")
    fun aCapsuleChaptersGradleProjectWithTheCapsulePluginApplied() {
        projectDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("cucumber-chapters-${System.currentTimeMillis()}-${System.nanoTime()}")
            .also { it.mkdirs() }
        projectDir!!.resolve("settings.gradle").writeText("")
        projectDir!!.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
        """.trimIndent())
    }

    @When("I run the chapters build with default config")
    fun iRunTheChaptersBuildWithDefaultConfig() {
        runChaptersBuild("help")
    }

    @When("I run the chapters build with chapters enabled via DSL")
    fun iRunTheChaptersBuildWithChaptersEnabledViaDsl() {
        projectDir!!.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
            capsule {
                chaptersEnabled = true
                chaptersIntroText = 'Welcome to the Course'
                chaptersOutroText = 'Thank you for watching'
            }
        """.trimIndent())
        runChaptersBuild("generateCapsuleChapters")
    }

    @When("I run the chapters build with chapters enabled via gradle property and custom text")
    fun iRunTheChaptersBuildWithChaptersEnabledViaGradlePropertyAndCustomText() {
        projectDir!!.resolve("gradle.properties").writeText("""
            capsule.chapters.enabled=true
            capsule.chapters.introText=Bienvenue
            capsule.chapters.outroText=Au revoir
        """.trimIndent())
        runChaptersBuild("generateCapsuleChapters")
    }

    private fun runChaptersBuild(task: String) {
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(task, "-q")
            .withProjectDir(projectDir!!)
            .build()
        buildOutput = result.output
    }

    @Then("the chapters build succeeds")
    fun theChaptersBuildSucceeds() {
        // GradleRunner.build() throws on failure — reaching here means success
    }

    @Then("the chapters output directory does not exist")
    fun theChaptersOutputDirectoryDoesNotExist() {
        val chaptersDir = File(projectDir!!, "build/capsule/chapters")
        assertFalse(
            chaptersDir.exists(),
            "Expected chapters output directory to NOT exist when chapters are disabled, " +
                "but it does at: ${chaptersDir.absolutePath}",
        )
    }

    @Then("a chapters.json file is generated")
    fun aChaptersJsonFileIsGenerated() {
        val chaptersFile = File(projectDir!!, "build/capsule/chapters/chapters.json")
        assertTrue(
            chaptersFile.exists(),
            "Expected chapters.json to be generated at: ${chaptersFile.absolutePath}",
        )
        val content = chaptersFile.readText().trim()
        assertTrue(content.startsWith("["), "chapters.json should be a JSON array, got: ${content.take(100)}")
        assertTrue(content.endsWith("]"), "chapters.json should be a JSON array, got: ${content.takeLast(100)}")
    }

    @Then("an intro.html card is generated")
    fun anIntroHtmlCardIsGenerated() {
        val introFile = File(projectDir!!, "build/capsule/chapters/intro.html")
        assertTrue(
            introFile.exists(),
            "Expected intro.html to be generated at: ${introFile.absolutePath}",
        )
        val content = introFile.readText()
        assertTrue(content.contains("Welcome to the Course"), "intro.html should contain the intro text")
        assertTrue(content.contains("card--intro"), "intro.html should have intro CSS class")
    }

    @Then("an outro.html card is generated")
    fun anOutroHtmlCardIsGenerated() {
        val outroFile = File(projectDir!!, "build/capsule/chapters/outro.html")
        assertTrue(
            outroFile.exists(),
            "Expected outro.html to be generated at: ${outroFile.absolutePath}",
        )
        val content = outroFile.readText()
        assertTrue(content.contains("Thank you for watching"), "outro.html should contain the outro text")
        assertTrue(content.contains("card--outro"), "outro.html should have outro CSS class")
    }

    @Then("a chapters.json file is generated with correct entries")
    fun aChaptersJsonFileIsGeneratedWithCorrectEntries() {
        val chaptersFile = File(projectDir!!, "build/capsule/chapters/chapters.json")
        assertTrue(
            chaptersFile.exists(),
            "Expected chapters.json to be generated at: ${chaptersFile.absolutePath}",
        )
        val content = chaptersFile.readText()
        assertTrue(content.contains("\"Bienvenue\""), "chapters.json should contain intro text 'Bienvenue'")
        assertTrue(content.contains("\"Au revoir\""), "chapters.json should contain outro text 'Au revoir'")
    }
}
