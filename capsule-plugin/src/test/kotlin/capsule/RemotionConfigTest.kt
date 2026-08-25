package capsule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD unit tests for [RemotionConfig.validate] — CAP-ANIM US-1.
 *
 * `validate()` is the guard the task runs before a render; it returns the
 * error messages rather than throwing, so the caller can surface every
 * problem at once.
 */
class RemotionConfigTest {

    @Test
    fun `defaults pass validation`() {
        assertTrue(RemotionConfig().validate().isEmpty())
    }

    @Test
    fun `blank projectDir is reported`() {
        val errors = RemotionConfig(projectDir = "  ").validate()
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("projectDir"))
    }

    @Test
    fun `blank nodeExecutablePath is reported`() {
        val errors = RemotionConfig(nodeExecutablePath = "").validate()
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("nodeExecutablePath"))
    }

    @Test
    fun `concurrency below one is reported`() {
        val errors = RemotionConfig(concurrency = 0).validate()
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("concurrency"))
    }

    @Test
    fun `fps outside 1 to 120 is reported`() {
        assertEquals(1, RemotionConfig(fps = 0).validate().size)
        assertEquals(1, RemotionConfig(fps = 121).validate().size)
    }

    @Test
    fun `fps at the boundaries is valid`() {
        assertTrue(RemotionConfig(fps = 1).validate().isEmpty())
        assertTrue(RemotionConfig(fps = 120).validate().isEmpty())
    }

    @Test
    fun `all errors are returned at once, not short-circuited`() {
        val errors = RemotionConfig(
            projectDir = "",
            nodeExecutablePath = "",
            concurrency = 0,
            fps = 200,
        ).validate()
        assertEquals(4, errors.size)
    }

    @Test
    fun `defaults match the documented backward-compat values`() {
        val config = RemotionConfig()
        assertEquals("capsule/remotion", config.projectDir)
        assertEquals("node", config.nodeExecutablePath)
        assertEquals(4, config.concurrency)
        assertEquals(30, config.fps)
    }
}