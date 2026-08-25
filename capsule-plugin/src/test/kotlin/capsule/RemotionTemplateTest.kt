package capsule

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * TDD unit tests for [RemotionTemplate] — CAP-ANIM US-1.
 *
 * The template is shipped as plugin resources and materialised into the
 * project directory on first use. These tests exercise the copy logic
 * (resource discovery + content-equality skip + target tree creation)
 * without ever calling `npm install` — the dependency install path is
 * covered by dogfooding/CI, not by unit tests.
 *
 * Pattern mirrors [capsule.audio.AudioPostCommandTest] (pure argv builder)
 * and [ScreenshotPlannerTest] (pure plan + argv builders).
 */
class RemotionTemplateTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `FILES lists exactly the shipped composition`() {
        assertEquals(
            listOf("package.json", "render.mjs", "src/index.js", "src/Root.jsx", "src/Capsule.jsx"),
            RemotionTemplate.FILES,
        )
    }

    @Test
    fun `every listed resource is present on the classpath`() {
        RemotionTemplate.FILES.forEach { relative ->
            val path = "capsule/remotion/$relative"
            val res = javaClass.classLoader.getResourceAsStream(path)
            assertTrue(res != null, "Missing template resource: $path")
            res!!.use { assertTrue(it.readBytes().isNotEmpty(), "Empty resource: $path") }
        }
    }

    @Test
    fun `materialiseInto creates every file under the project dir`() {
        RemotionTemplate.materialiseInto(tempDir)
        RemotionTemplate.FILES.forEach { relative ->
            val target = File(tempDir, relative)
            assertTrue(target.exists(), "Template file not materialised: $relative")
            assertTrue(target.length() > 0, "Template file copied empty: $relative")
        }
    }

    @Test
    fun `materialiseInto creates nested parent directories`() {
        RemotionTemplate.materialiseInto(tempDir)
        assertTrue(File(tempDir, "src").isDirectory)
        assertTrue(File(tempDir, "src/Capsule.jsx").exists())
    }

    @Test
    fun `materialiseInto is idempotent — calling twice leaves the same bytes`() {
        // Pre-create node_modules so ensureDependencies is a no-op: npm
        // install would otherwise normalise package.json on the first run
        // and the second pass would see different bytes.
        File(tempDir, "node_modules").mkdirs()
        RemotionTemplate.materialiseInto(tempDir)
        val firstPass = RemotionTemplate.FILES.associateWith { File(tempDir, it).readBytes() }
        RemotionTemplate.materialiseInto(tempDir)
        RemotionTemplate.FILES.forEach { relative ->
            val second = File(tempDir, relative).readBytes()
            assertTrue(firstPass[relative]!!.contentEquals(second), "Re-materialise changed $relative (first=${firstPass[relative]!!.size}B second=${second.size}B)")
        }
    }

    @Test
    fun `materialiseInto into a non-existent directory creates it`() {
        val nested = File(tempDir, "deep/nested/remotion")
        RemotionTemplate.materialiseInto(nested)
        assertTrue(File(nested, "package.json").exists())
    }

    @Test
    fun `a missing template resource resolves to null on the classpath`() {
        // The plugin ships every file in FILES; the missing-resource branch
        // of readResource turns a null getResourceAsStream into a
        // CapturingException. We assert the precondition here: a path
        // nothing ships resolves to null, which is what the production
        // code branches on.
        val missing = javaClass.classLoader.getResourceAsStream("capsule/remotion/does-not-exist.json")
        assertTrue(missing == null, "A non-existent resource should resolve to null")
    }
}