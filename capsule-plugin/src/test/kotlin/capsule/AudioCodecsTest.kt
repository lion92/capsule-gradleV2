package capsule

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD unit tests for [AudioCodecs] — CAP-ANIM US-1.
 *
 * The capture engines do not all emit the same container: the browser-driven
 * ones write WebM, Remotion writes MP4. [AudioCodecs.forContainer] picks the
 * audio codec a muxer will accept — Vorbis into an MP4 (or AAC into a WebM)
 * produces a file most players refuse.
 *
 * Pure object — no I/O, no Gradle, no Jackson.
 */
class AudioCodecsTest {

    @Test
    fun `libvorbis is the codec for the WebM container`() {
        assertEquals(AudioCodecs.WEBM, AudioCodecs.forContainer("webm"))
    }

    @Test
    fun `aac is the codec for the MP4 container`() {
        assertEquals(AudioCodecs.MP4, AudioCodecs.forContainer("mp4"))
    }

    @Test
    fun `m4v and mov map to aac like mp4`() {
        assertEquals(AudioCodecs.MP4, AudioCodecs.forContainer("m4v"))
        assertEquals(AudioCodecs.MP4, AudioCodecs.forContainer("mov"))
    }

    @Test
    fun `container lookup is case-insensitive`() {
        assertEquals(AudioCodecs.WEBM, AudioCodecs.forContainer("WEBM"))
        assertEquals(AudioCodecs.MP4, AudioCodecs.forContainer("MP4"))
        assertEquals(AudioCodecs.MP4, AudioCodecs.forContainer("MoV"))
    }

    @Test
    fun `a leading dot on the container is tolerated`() {
        assertEquals(AudioCodecs.WEBM, AudioCodecs.forContainer(".webm"))
        assertEquals(AudioCodecs.MP4, AudioCodecs.forContainer(".mp4"))
    }

    @Test
    fun `unknown containers fall back to libvorbis (WebM is the native capsule format)`() {
        assertEquals(AudioCodecs.WEBM, AudioCodecs.forContainer("avi"))
        assertEquals(AudioCodecs.WEBM, AudioCodecs.forContainer("mkv"))
        assertEquals(AudioCodecs.WEBM, AudioCodecs.forContainer(""))
        assertEquals(AudioCodecs.WEBM, AudioCodecs.forContainer("nonsense"))
    }

    @Test
    fun `WEBM and MP4 constants are the ffmpeg codec names`() {
        assertEquals("libvorbis", AudioCodecs.WEBM)
        assertEquals("aac", AudioCodecs.MP4)
    }
}