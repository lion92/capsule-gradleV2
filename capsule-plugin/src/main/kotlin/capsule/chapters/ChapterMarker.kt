package capsule.chapters

import java.io.File

/**
 * Formats chapter metadata (timestamps + titles) to JSON for Matroska/WebM.
 *
 * Factory pattern (9th):
 * - noop → [NoOpChapterMarker] (writes empty file)
 * - available → [ChapterMarkerImpl]
 * - unavailable + strict → throw
 */
interface ChapterMarker {
    fun format(chapters: List<ChapterEntry>, outputFile: File): Boolean
    fun isAvailable(): Boolean
    fun name(): String
}

/**
 * One chapter segment with title, start and end timestamps in milliseconds.
 */
data class ChapterEntry(
    val title: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Pure command object — builds JSON content from chapter entries.
 * No I/O, no Gradle dependencies, fully unit-testable.
 */
object ChapterMetadataCommand {

    fun buildJson(chapters: List<ChapterEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("[")
        chapters.forEachIndexed { index, entry ->
            sb.append("  {")
            sb.append("\"title\": \"${escapeJson(entry.title)}\", ")
            sb.append("\"start_ms\": ${entry.startMs}, ")
            sb.append("\"end_ms\": ${entry.endMs}")
            sb.append("}")
            if (index < chapters.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("]")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * Real implementation — writes chapter metadata JSON to disk.
 */
class ChapterMarkerImpl(private val ffmpegPath: String) : ChapterMarker {
    override fun format(chapters: List<ChapterEntry>, outputFile: File): Boolean {
        val json = ChapterMetadataCommand.buildJson(chapters)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json)
        return true
    }

    override fun isAvailable(): Boolean = true

    override fun name(): String = "ChapterMarkerImpl(ffmpeg=$ffmpegPath)"
}

/**
 * No-op implementation — writes empty JSON array.
 * Used when chapters are disabled or ffmpeg is unavailable.
 */
class NoOpChapterMarker : ChapterMarker {
    override fun format(chapters: List<ChapterEntry>, outputFile: File): Boolean {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("[]")
        return false
    }

    override fun isAvailable(): Boolean = false

    override fun name(): String = "NoOpChapterMarker"
}
