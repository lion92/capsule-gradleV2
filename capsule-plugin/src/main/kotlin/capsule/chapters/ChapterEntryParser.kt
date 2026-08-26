package capsule.chapters

/**
 * Pure object — parses slide segment JSON and builds chapter entries.
 * No I/O, no Gradle dependencies, fully unit-testable.
 */
object ChapterEntryParser {

    data class SlideSegmentData(val title: String, val startMs: Long, val endMs: Long)

    fun parseSlideSegments(json: String): List<SlideSegmentData> {
        if (json.isBlank() || json.trim() == "[]") return emptyList()
        val trimmed = json.trim().removeSurrounding("[", "]")
        if (trimmed.isBlank()) return emptyList()
        val segments = mutableListOf<SlideSegmentData>()
        val entries = trimmed.split(Regex("\\}\\s*,\\s*\\{"))
        for (entry in entries) {
            val cleaned = entry.trim().removePrefix("{").removeSuffix("}")
            val title = extractJsonString(cleaned, "title")
            val startMs = extractJsonLong(cleaned, "start_ms")
            val endMs = extractJsonLong(cleaned, "end_ms")
            if (title.isNotBlank()) {
                segments.add(SlideSegmentData(title, startMs, endMs))
            }
        }
        return segments
    }

    fun buildChapterEntries(
        segments: List<SlideSegmentData>,
        introText: String,
        outroText: String
    ): List<ChapterEntry> {
        val chapters = mutableListOf<ChapterEntry>()
        val introLabel = introText.ifBlank { "Introduction" }
        chapters.add(ChapterEntry(introLabel, 0, 5000))
        for (segment in segments) {
            chapters.add(ChapterEntry(segment.title, segment.startMs, segment.endMs))
        }
        val outroLabel = outroText.ifBlank { "Conclusion" }
        val outroStart = if (segments.isNotEmpty()) segments.last().endMs else 5000L
        chapters.add(ChapterEntry(outroLabel, outroStart, outroStart + 5000))
        return chapters
    }

    private fun extractJsonString(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }

    private fun extractJsonLong(json: String, key: String): Long {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
