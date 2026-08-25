package capsule

/**
 * Audio codec matching a video container.
 *
 * The capture engines do not all produce the same container — the browser-driven
 * ones write WebM, Remotion writes MP4 — and muxing Vorbis into an MP4 (or AAC
 * into a WebM) produces a file most players refuse.
 */
object AudioCodecs {

    const val WEBM: String = "libvorbis"
    const val MP4: String = "aac"

    /** Codec for [containerExtension], with or without a leading dot. */
    fun forContainer(containerExtension: String): String =
        when (containerExtension.removePrefix(".").lowercase()) {
            "mp4", "m4v", "mov" -> MP4
            else -> WEBM
        }
}
