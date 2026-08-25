package capsule

import capsule.podcast.NoOpPodcastConcatenator
import capsule.podcast.PodcastConcatCommand
import capsule.podcast.PodcastConcatenator
import capsule.podcast.PodcastConcatenatorImpl
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * TDD unit tests for CAP-PODCAST US-1 — `PodcastConcatenator`
 * (interface) + `PodcastConcatCommand` (pure object) +
 * `PodcastConcatenatorImpl` (FFmpeg concat demuxer) +
 * `NoOpPodcastConcatenator` (test fallback) +
 * `CapsuleManager.resolvePodcastConcatenator` factory (8ème).
 *
 * Pattern de référence : [capsule.audio.AudioPostProcessor] +
 * [capsule.audio.AudioPostCommand] (CAP-AUDIO US-2) and
 * [VideoFormatConverter] (CAP-MP4 US-2).
 *
 * `PodcastConcatCommand` is a pure object — `buildConcatListContent`
 * and `buildConcatArgv` have no I/O, fully testable.
 * `PodcastConcatenatorImpl` wraps FFmpeg concat demuxer
 * (`-f concat -safe 0 -i <listfile> -c copy <output>`).
 * `NoOpPodcastConcatenator` is the test fallback (returns `false` —
 * caller keeps no podcast, degraded mode backward compat).
 */
class PodcastConcatenatorTest {

    // ─── PodcastConcatCommand.buildConcatListContent (pure, no I/O) ─

    @Test
    fun `buildConcatListContent formats one file directive per mp3 with absolute paths`() {
        val mp3s = listOf(File("/tmp/deck/slide-01.mp3"), File("/tmp/deck/slide-02.mp3"))
        val content = PodcastConcatCommand.buildConcatListContent(mp3s)
        assertEquals("file '/tmp/deck/slide-01.mp3'\nfile '/tmp/deck/slide-02.mp3'\n", content)
    }

    @Test
    fun `buildConcatListContent empty list returns empty string`() {
        assertEquals("", PodcastConcatCommand.buildConcatListContent(emptyList()))
    }

    @Test
    fun `buildConcatListContent single file ends with newline`() {
        val content = PodcastConcatCommand.buildConcatListContent(listOf(File("/a/b.mp3")))
        assertEquals("file '/a/b.mp3'\n", content)
        assertTrue(content.endsWith("\n"))
    }

    // ─── PodcastConcatCommand.buildConcatArgv (pure, no I/O) ────────

    @Test
    fun `buildConcatArgv uses concat demuxer with safe 0 and stream copy`() {
        val listFile = File("/tmp/concat-list.txt")
        val output = File("/tmp/podcast.mp3")
        val argv = PodcastConcatCommand.buildConcatArgv(listFile, output, "ffmpeg")
        assertEquals("ffmpeg", argv.first(), "argv should start with ffmpeg path")
        assertTrue(argv.contains("-f"), "should set concat demuxer format")
        assertTrue(argv.contains("concat"), "should use concat demuxer")
        assertTrue(argv.contains("-safe"), "should set -safe 0")
        assertTrue(argv.contains("0"), "should set -safe 0")
        assertTrue(argv.contains("-i"), "should pass list file as input")
        assertTrue(argv.contains(listFile.absolutePath), "should reference the list file path")
        assertTrue(argv.contains("-c"), "should set codec")
        assertTrue(argv.contains("copy"), "should stream-copy (no re-encode)")
        assertTrue(argv.contains(output.absolutePath), "should reference output file")
    }

    @Test
    fun `buildConcatArgv injects the ffmpeg path provided`() {
        val listFile = File("/tmp/concat-list.txt")
        val output = File("/tmp/podcast.mp3")
        val argv = PodcastConcatCommand.buildConcatArgv(listFile, output, "/custom/ffmpeg")
        assertEquals("/custom/ffmpeg", argv.first())
    }

    @Test
    fun `buildConcatArgv places output file last`() {
        val listFile = File("/tmp/concat-list.txt")
        val output = File("/tmp/podcast.mp3")
        val argv = PodcastConcatCommand.buildConcatArgv(listFile, output, "ffmpeg")
        assertEquals(output.absolutePath, argv.last(), "output file should be the last argv element")
    }

    // ─── PodcastConcatenatorImpl (without real ffmpeg) ────────────

    @Test
    fun `PodcastConcatenatorImpl with bogus path reports unavailable`() {
        val concat = PodcastConcatenatorImpl(ffmpegPath = "/nonexistent/ffmpeg/path_xyz")
        assertFalse(concat.isAvailable(), "Should report unavailable for nonexistent ffmpeg")
    }

    @Test
    fun `PodcastConcatenatorImpl name returns ffmpeg-podcast-concat`() {
        assertEquals("ffmpeg-podcast-concat", PodcastConcatenatorImpl().name())
    }

    @Test
    fun `PodcastConcatenatorImpl concatenate returns false when ffmpeg unavailable (degraded)`() {
        val concat = PodcastConcatenatorImpl(ffmpegPath = "/nonexistent/ffmpeg/path_xyz")
        val mp3s = listOf(File("/tmp/a.mp3"), File("/tmp/b.mp3"))
        val output = File.createTempFile("podcast", ".mp3")
        try {
            val result = concat.concatenate(mp3s, output)
            assertFalse(result, "Should return false when ffmpeg unavailable (degraded)")
        } finally {
            output.delete()
        }
    }

    @Test
    fun `PodcastConcatenatorImpl concatenate returns false when no mp3 inputs (degraded)`() {
        val concat = PodcastConcatenatorImpl(ffmpegPath = "/nonexistent/ffmpeg/path_xyz")
        val output = File.createTempFile("podcast", ".mp3")
        try {
            val result = concat.concatenate(emptyList(), output)
            assertFalse(result, "Should return false when no mp3 inputs (degraded)")
        } finally {
            output.delete()
        }
    }

    // ─── NoOpPodcastConcatenator ─────────────────────────────────

    @Test
    fun `NoOpPodcastConcatenator is available and named noop-podcast-concat`() {
        val concat = NoOpPodcastConcatenator()
        assertTrue(concat.isAvailable(), "NoOp is always available (fallback)")
        assertEquals("noop-podcast-concat", concat.name())
    }

    @Test
    fun `NoOpPodcastConcatenator concatenate returns false (degraded, no podcast)`() {
        val concat = NoOpPodcastConcatenator()
        val mp3s = listOf(File("/tmp/a.mp3"))
        val output = File.createTempFile("podcast", ".mp3")
        try {
            val result = concat.concatenate(mp3s, output)
            assertFalse(result, "NoOp should return false (no real concat, keep no podcast)")
        } finally {
            output.delete()
        }
    }

    // ─── resolvePodcastConcatenator factory ──────────────────────

    @Test
    fun `resolvePodcastConcatenator with ffmpegPath noop returns NoOp`() {
        val resolved = CapsuleManager.resolvePodcastConcatenator(ffmpegPath = "noop", strict = false)
        assertTrue(resolved is NoOpPodcastConcatenator, "noop path should return NoOp")
        assertEquals("noop-podcast-concat", resolved.name())
    }

    @Test
    fun `resolvePodcastConcatenator unavailable and non-strict falls back to NoOp`() {
        val resolved = CapsuleManager.resolvePodcastConcatenator(
            ffmpegPath = "/nonexistent/ffmpeg/path_xyz",
            strict = false
        )
        assertTrue(resolved is NoOpPodcastConcatenator, "unavailable + non-strict → NoOp fallback")
    }

    @Test
    fun `resolvePodcastConcatenator unavailable and strict throws IllegalStateException`() {
        val ex = assertFailsWith<IllegalStateException> {
            CapsuleManager.resolvePodcastConcatenator(
                ffmpegPath = "/nonexistent/ffmpeg/path_xyz",
                strict = true
            )
        }
        assertTrue(ex.message!!.contains("ffmpeg"), "error should name ffmpeg")
        assertTrue(ex.message!!.contains("/nonexistent/ffmpeg/path_xyz"), "error should mention the path")
        assertTrue(ex.message!!.contains("strictMode"), "error should suggest disabling strictMode")
    }

    @Test
    fun `resolvePodcastConcatenator named ffmpeg-podcast-concat when impl returned`() {
        val impl = PodcastConcatenatorImpl("ffmpeg")
        if (impl.isAvailable()) {
            val resolved = CapsuleManager.resolvePodcastConcatenator("ffmpeg", strict = false)
            assertEquals("ffmpeg-podcast-concat", resolved.name())
        }
    }
}