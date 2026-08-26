package capsule

import capsule.preview.PreviewConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * TDD unit tests for CAP-PREVIEW US-0 — `PreviewConfig` data class
 * (domain `capsule.preview`, 14th section `CapsuleConfig`).
 *
 * 1 field: `enabled` (Boolean=false).
 * Default disabled to preserve backward compatibility —
 * existing configs without a `preview` section keep the full-pipeline
 * behavior.
 *
 * Pattern: [PodcastConfig] (CAP-PODCAST US-1).
 */
class PreviewConfigTest {

    @Test
    fun `default config has preview disabled`() {
        val config = PreviewConfig()
        assertFalse(config.enabled, "preview.enabled should default to false")
    }

    @Test
    fun `preview config holds enabled flag`() {
        val config = PreviewConfig(enabled = true)
        assertEquals(true, config.enabled)
    }

    @Test
    fun `preview config is a data class with equals by content`() {
        val a = PreviewConfig(enabled = true)
        val b = PreviewConfig(enabled = true)
        assertEquals(a, b, "data class equals by content")
        assertEquals(a.hashCode(), b.hashCode())
    }
}
