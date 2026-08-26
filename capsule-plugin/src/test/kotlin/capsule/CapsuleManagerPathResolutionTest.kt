package capsule

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for CapsuleManager.resolveScriptDir / resolveDeckDir (CAP-CR3-4).
 *
 * Tests the configurable fallback to slider's build output directory.
 */
class CapsuleManagerPathResolutionTest {

    @TempDir
    lateinit var tempDir: File

    // ─── resolveScriptDir ─────────────────────────────────────────

    @Test
    fun `resolveScriptDir returns build dir when it contains script files`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        val buildDir = File(project.layout.buildDirectory.get().asFile, "capsule")
        buildDir.mkdirs()
        File(buildDir, "example-script.txt").writeText("slide 1")

        val result = CapsuleManager.resolveScriptDir(project, ext)
        assertEquals(buildDir, result)
    }

    @Test
    fun `resolveScriptDir falls back to sliderBuildDir when configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        // Set sliderBuildDir to a temp directory
        val sliderRoot = File(tempDir, "slider-parent")
        val sliderCapsule = File(sliderRoot, "slider/build/capsule")
        sliderCapsule.mkdirs()
        File(sliderCapsule, "slide1-script.txt").writeText("content")
        ext.sliderBuildDir.set(sliderRoot.absolutePath)

        val result = CapsuleManager.resolveScriptDir(project, ext)
        assertEquals(sliderCapsule, result)
    }

    @Test
    fun `resolveScriptDir returns build dir when sliderBuildDir not configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        // sliderBuildDir is empty by default
        val buildDir = File(project.layout.buildDirectory.get().asFile, "capsule")
        buildDir.mkdirs()

        val result = CapsuleManager.resolveScriptDir(project, ext)
        assertEquals(buildDir, result)
    }

    @Test
    fun `resolveScriptDir returns build dir when sliderBuildDir does not exist`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        ext.sliderBuildDir.set("/nonexistent/path")
        val buildDir = File(project.layout.buildDirectory.get().asFile, "capsule")
        buildDir.mkdirs()

        val result = CapsuleManager.resolveScriptDir(project, ext)
        assertEquals(buildDir, result)
    }

    // ─── resolveDeckDir ───────────────────────────────────────────

    @Test
    fun `resolveDeckDir returns build dir when it exists`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        val buildDir = File(project.layout.buildDirectory.get().asFile, "docs/asciidocRevealJs")
        buildDir.mkdirs()

        val result = CapsuleManager.resolveDeckDir(project, ext)
        assertEquals(buildDir, result)
    }

    @Test
    fun `resolveDeckDir falls back to sliderBuildDir when configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        val sliderRoot = File(tempDir, "slider-parent")
        val sliderDocs = File(sliderRoot, "slider/build/docs/asciidocRevealJs")
        sliderDocs.mkdirs()
        ext.sliderBuildDir.set(sliderRoot.absolutePath)

        val result = CapsuleManager.resolveDeckDir(project, ext)
        assertEquals(sliderDocs, result)
    }

    @Test
    fun `resolveDeckDir returns build dir when sliderBuildDir not configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        val buildDir = File(project.layout.buildDirectory.get().asFile, "docs/asciidocRevealJs")
        buildDir.mkdirs()

        val result = CapsuleManager.resolveDeckDir(project, ext)
        assertEquals(buildDir, result)
    }

    @Test
    fun `resolveDeckDir returns build dir when sliderBuildDir does not exist`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        ext.sliderBuildDir.set("/nonexistent/path")
        val buildDir = File(project.layout.buildDirectory.get().asFile, "docs/asciidocRevealJs")
        buildDir.mkdirs()

        val result = CapsuleManager.resolveDeckDir(project, ext)
        assertEquals(buildDir, result)
    }
}
