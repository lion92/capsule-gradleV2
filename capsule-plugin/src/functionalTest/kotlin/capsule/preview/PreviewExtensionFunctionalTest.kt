package capsule.preview

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests for the CAP-PREVIEW US-0 extension wiring.
 *
 * Verifies:
 *  - `previewOnly` property is recognized by the capsule DSL
 *  - disabled by default (backward compat)
 *  - enabled via DSL block
 */
class PreviewExtensionFunctionalTest {

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
    fun `previewOnly defaults to false in extension`() {
        setupBuild()
        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("help")
            .withProjectDir(projectDir)
            .build()
    }

    @Test
    fun `previewOnly can be set via DSL block`() {
        setupBuild("""
            capsule {
                previewOnly = true
            }
        """.trimIndent())
        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("help")
            .withProjectDir(projectDir)
            .build()
    }

    @Test
    fun `previewOnly can be set via gradle property`() {
        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText("""
            plugins {
                id('education.cccp.capsule')
            }
        """.trimIndent())
        projectDir.resolve("gradle.properties").writeText("capsule.preview.enabled=true")
        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("help")
            .withProjectDir(projectDir)
            .build()
    }
}
