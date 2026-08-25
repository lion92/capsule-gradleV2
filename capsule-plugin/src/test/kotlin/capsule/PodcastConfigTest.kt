package capsule

import capsule.podcast.PodcastConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * TDD unit tests for CAP-PODCAST US-1 — `PodcastConfig` data class
 * (domain `capsule.podcast`, 13ème section `CapsuleConfig`).
 *
 * 2 fields: `enabled` (Boolean=false), `outputFile` (String="").
 * All defaults disabled/empty to preserve backward compatibility —
 * existing configs without a `podcast` section keep the no-podcast
 * behavior.
 *
 * Pattern de référence: [TranscriptConfig] (CAP-TRANSCRIPT US-1),
 * [AudioPostConfig] (CAP-AUDIO US-1).
 */
class PodcastConfigTest {

    @Test
    fun `default config has podcast disabled and empty output file`() {
        val config = PodcastConfig()
        assertFalse(config.enabled, "podcast.enabled should default to false")
        assertEquals("", config.outputFile, "podcast.outputFile should default to empty")
    }

    @Test
    fun `podcast config holds enabled flag and output file path`() {
        val config = PodcastConfig(enabled = true, outputFile = "/out/capsule.mp3")
        assertEquals(true, config.enabled)
        assertEquals("/out/capsule.mp3", config.outputFile)
    }

    @Test
    fun `podcast config is a data class with equals by content`() {
        val a = PodcastConfig(enabled = true, outputFile = "/o/p.mp3")
        val b = PodcastConfig(enabled = true, outputFile = "/o/p.mp3")
        assertEquals(a, b, "data class equals by content")
        assertEquals(a.hashCode(), b.hashCode())
    }
}