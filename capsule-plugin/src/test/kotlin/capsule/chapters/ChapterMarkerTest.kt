package capsule.chapters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.Test

class ChapterMarkerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `ChapterMetadataCommand buildJson produces valid JSON array`() {
        val entries = listOf(
            ChapterEntry("Intro", 0, 5000),
            ChapterEntry("Segment 1", 5000, 30000),
            ChapterEntry("Outro", 30000, 35000)
        )
        val json = ChapterMetadataCommand.buildJson(entries)
        assertTrue(json.startsWith("["), "JSON should start with [")
        assertTrue(json.trimEnd().endsWith("]"), "JSON should end with ]")
        assertTrue(json.contains("\"title\": \"Intro\""), "Should contain Intro title")
        assertTrue(json.contains("\"start_ms\": 0"), "Should contain start_ms 0")
        assertTrue(json.contains("\"end_ms\": 5000"), "Should contain end_ms 5000")
        assertTrue(json.contains("\"title\": \"Segment 1\""), "Should contain Segment 1")
        assertTrue(json.contains("\"title\": \"Outro\""), "Should contain Outro")
    }

    @Test
    fun `ChapterMetadataCommand buildJson escapes quotes in title`() {
        val entries = listOf(ChapterEntry("He said \"hello\"", 0, 1000))
        val json = ChapterMetadataCommand.buildJson(entries)
        assertTrue(json.contains("He said \\\"hello\\\""), "Quotes should be escaped")
    }

    @Test
    fun `ChapterMetadataCommand buildJson escapes newlines in title`() {
        val entries = listOf(ChapterEntry("Line1\nLine2", 0, 1000))
        val json = ChapterMetadataCommand.buildJson(entries)
        assertTrue(json.contains("Line1\\nLine2"), "Newlines should be escaped")
    }

    @Test
    fun `ChapterMetadataCommand buildJson empty list produces empty array`() {
        val json = ChapterMetadataCommand.buildJson(emptyList())
        assertEquals("[\n]\n", json, "Empty list should produce empty JSON array")
    }

    @Test
    fun `ChapterMetadataCommand buildJson single entry no trailing comma`() {
        val entries = listOf(ChapterEntry("Only", 0, 1000))
        val json = ChapterMetadataCommand.buildJson(entries)
        val lines = json.trim().lines()
        assertEquals(3, lines.size, "Single entry should have 3 lines: [ + entry + ]")
        assertFalse(lines[1].trimEnd().endsWith(","), "Single entry should not have trailing comma")
    }

    @Test
    fun `ChapterMetadataCommand buildJson multiple entries have commas between`() {
        val entries = listOf(
            ChapterEntry("A", 0, 1000),
            ChapterEntry("B", 1000, 2000),
            ChapterEntry("C", 2000, 3000)
        )
        val json = ChapterMetadataCommand.buildJson(entries)
        val lines = json.trim().lines()
        // line 0 = [, line 1 = entry A (with comma), line 2 = entry B (with comma), line 3 = entry C (no comma), line 4 = ]
        assertTrue(lines[1].trimEnd().endsWith(","), "Entry A should have trailing comma")
        assertTrue(lines[2].trimEnd().endsWith(","), "Entry B should have trailing comma")
        assertFalse(lines[3].trimEnd().endsWith(","), "Entry C should not have trailing comma")
    }

    @Test
    fun `ChapterMarkerImpl format writes JSON file`() {
        val impl = ChapterMarkerImpl("ffmpeg")
        val entries = listOf(ChapterEntry("Intro", 0, 5000), ChapterEntry("Outro", 5000, 10000))
        val outputFile = File(tempDir, "chapters.json")

        val result = impl.format(entries, outputFile)

        assertTrue(result, "format should return true")
        assertTrue(outputFile.exists(), "Output file should be created")
        val content = outputFile.readText()
        assertTrue(content.contains("\"title\": \"Intro\""), "File should contain Intro")
        assertTrue(content.contains("\"title\": \"Outro\""), "File should contain Outro")
    }

    @Test
    fun `ChapterMarkerImpl creates parent directories`() {
        val impl = ChapterMarkerImpl("ffmpeg")
        val outputFile = File(tempDir, "subdir/deep/chapters.json")

        impl.format(listOf(ChapterEntry("X", 0, 1000)), outputFile)

        assertTrue(outputFile.parentFile?.exists() == true, "Parent directories should be created")
        assertTrue(outputFile.exists(), "Output file should be created")
    }

    @Test
    fun `ChapterMarkerImpl isAvailable returns true`() {
        assertTrue(ChapterMarkerImpl("ffmpeg").isAvailable(), "Impl should always be available (disk write)")
    }

    @Test
    fun `ChapterMarkerImpl name contains ffmpeg path`() {
        assertEquals("ChapterMarkerImpl(ffmpeg=/usr/bin/ffmpeg)", ChapterMarkerImpl("/usr/bin/ffmpeg").name())
    }

    @Test
    fun `NoOpChapterMarker format writes empty array`() {
        val noop = NoOpChapterMarker()
        val outputFile = File(tempDir, "noop-chapters.json")

        val result = noop.format(listOf(ChapterEntry("X", 0, 1000)), outputFile)

        assertFalse(result, "NoOp should return false (degraded)")
        assertTrue(outputFile.exists(), "Output file should be created")
        assertEquals("[]", outputFile.readText().trim(), "File should contain empty JSON array")
    }

    @Test
    fun `NoOpChapterMarker isAvailable returns false`() {
        assertFalse(NoOpChapterMarker().isAvailable(), "NoOp should report unavailable")
    }

    @Test
    fun `NoOpChapterMarker name is NoOpChapterMarker`() {
        assertEquals("NoOpChapterMarker", NoOpChapterMarker().name())
    }

    @Test
    fun `ChapterEntry data class equals`() {
        assertEquals(ChapterEntry("A", 0, 1000), ChapterEntry("A", 0, 1000))
        assertTrue(ChapterEntry("A", 0, 1000).equals(ChapterEntry("A", 0, 1000)))
    }

    @Test
    fun `ChapterEntry data class not equals different title`() {
        assertFalse(ChapterEntry("A", 0, 1000).equals(ChapterEntry("B", 0, 1000)))
    }

    @Test
    fun `ChapterEntry data class not equals different timestamps`() {
        assertFalse(ChapterEntry("A", 0, 1000).equals(ChapterEntry("A", 0, 2000)))
    }

    @Test
    fun `ChapterMetadataCommand buildJson preserves ordering`() {
        val entries = listOf(
            ChapterEntry("First", 0, 1000),
            ChapterEntry("Second", 1000, 2000),
            ChapterEntry("Third", 2000, 3000)
        )
        val json = ChapterMetadataCommand.buildJson(entries)
        val firstIdx = json.indexOf("First")
        val secondIdx = json.indexOf("Second")
        val thirdIdx = json.indexOf("Third")
        assertTrue(firstIdx < secondIdx, "First should come before Second")
        assertTrue(secondIdx < thirdIdx, "Second should come before Third")
    }

    @Test
    fun `NoOpChapterMarker format with empty list writes empty array`() {
        val noop = NoOpChapterMarker()
        val outputFile = File(tempDir, "noop-empty.json")

        noop.format(emptyList(), outputFile)

        assertEquals("[]", outputFile.readText().trim(), "Empty list should produce empty JSON array")
    }

    @Test
    fun `ChapterMarkerImpl format with empty list writes empty array`() {
        val impl = ChapterMarkerImpl("ffmpeg")
        val outputFile = File(tempDir, "impl-empty.json")

        val result = impl.format(emptyList(), outputFile)

        assertTrue(result, "format should return true even for empty list")
        val content = outputFile.readText().replace(Regex("\\s"), "")
        assertEquals("[]", content, "Empty list should produce empty JSON array")
    }
}
