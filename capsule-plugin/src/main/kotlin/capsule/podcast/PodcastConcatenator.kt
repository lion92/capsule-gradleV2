package capsule.podcast

import java.io.File

/**
 * Concatenates per-slide MP3 files into a single podcast MP3
 * (CAP-PODCAST US-1).
 *
 * The podcast is the audio-only version of a capsule: the TTS MP3
 * files produced by `synthesizeTtsForScript` (one per slide) are
 * concatenated losslessly via the FFmpeg concat demuxer
 * (`-f concat -safe 0 -i <listfile> -c copy <output>`). No re-encoding,
 * no quality loss, fast even for long capsules.
 *
 * Design mirrors [capsule.audio.AudioPostProcessor] and
 * [capsule.VideoFormatConverter]:
 * - [PodcastConcatenatorImpl] uses ffmpeg concat demuxer.
 * - [NoOpPodcastConcatenator] is the test/no-ffmpeg fallback (returns `false`).
 * - Factory: [capsule.CapsuleManager.resolvePodcastConcatenator]
 *   (8ème `resolve*`, pattern `resolveAudioPostProcessor` — ffmpegPath="noop"
 *   → NoOp, unavailable + non-strict → NoOp, unavailable + strict → throw
 *   via [capsule.StrictModeGuard], available → Impl).
 *
 * Economy of ink (AGENT.adoc): [concatenate] returns `false` when
 * ffmpeg is unavailable or the MP3 list is empty (degraded — no
 * podcast produced) rather than throwing. The caller decides whether
 * to apply the result.
 */
interface PodcastConcatenator {

    /**
     * Concatenates [mp3Files] (in order) into [outputFile].
     *
     * @return `true` if the podcast MP3 was produced successfully,
     *         `false` otherwise (NoOp fallback, ffmpeg failure, empty
     *         MP3 list, ffmpeg unavailable). On `false`, no podcast is
     *         produced (degraded mode, backward compatibility).
     * @throws RuntimeException on unrecoverable ffmpeg errors
     *         (pattern [capsule.audio.AudioPostException]).
     */
    fun concatenate(mp3Files: List<File>, outputFile: File): Boolean

    /** Returns true if the service is available (e.g. ffmpeg found). */
    fun isAvailable(): Boolean

    /** Returns the service name for logging. */
    fun name(): String
}

/**
 * Pure builder for the FFmpeg concat demuxer list file and argv of
 * [PodcastConcatenatorImpl] (CAP-PODCAST US-1).
 *
 * Pure object — no I/O, no Gradle dependencies, fully testable.
 *
 * [buildConcatListContent] assembles the concat demuxer list file
 * content: one `file '<absolute-path>'` directive per MP3, separated
 * by newlines, terminated by a trailing newline. FFmpeg's concat
 * demuxer reads this list and concatenates the referenced files in
 * order, stream-copying (no re-encode).
 *
 * [buildConcatArgv] assembles the full FFmpeg command line:
 * `ffmpeg -y -f concat -safe 0 -i <listfile> -c copy <output>`.
 */
object PodcastConcatCommand {

    /**
     * Builds the FFmpeg concat demuxer list file content for [mp3Files].
     *
     * Each line is `file '<absolute-path>'`. The content ends with a
     * trailing newline. An empty list returns an empty string (the
     * caller is responsible for skipping the concat in that case).
     *
     * @param mp3Files the MP3 files to concatenate, in order
     * @return the concat list content (one `file '...'` directive per line)
     */
    fun buildConcatListContent(mp3Files: List<File>): String =
        if (mp3Files.isEmpty()) ""
        else mp3Files.joinToString(separator = "\n", postfix = "\n") { "file '${it.absoluteFile}'" }

    /**
     * Builds the full FFmpeg argv for concat demuxer concatenation.
     *
     * @param listFile   the temporary concat list file (one `file '...'`
     *                   directive per MP3, produced by [buildConcatListContent])
     * @param outputFile the output podcast MP3 file
     * @param ffmpegPath the ffmpeg executable path
     * @return the argv list (first element is the ffmpeg path, last is
     *         the output file)
     */
    fun buildConcatArgv(listFile: File, outputFile: File, ffmpegPath: String): List<String> =
        listOf(
            ffmpegPath, "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", listFile.absolutePath,
            "-c", "copy",
            outputFile.absolutePath
        )
}