package capsule.chapters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterEntryParserTest {

    @Test
    fun `parseSlideSegments with empty array returns empty list`() {
        assertTrue(ChapterEntryParser.parseSlideSegments("[]").isEmpty())
    }

    @Test
    fun `parseSlideSegments with blank string returns empty list`() {
        assertTrue(ChapterEntryParser.parseSlideSegments("").isEmpty())
    }

    @Test
    fun `parseSlideSegments with single segment`() {
        val json = """[{"title": "Slide 1", "start_ms": 0, "end_ms": 5000}]"""
        val segments = ChapterEntryParser.parseSlideSegments(json)
        assertEquals(1, segments.size)
        assertEquals("Slide 1", segments[0].title)
        assertEquals(0L, segments[0].startMs)
        assertEquals(5000L, segments[0].endMs)
    }

    @Test
    fun `parseSlideSegments with multiple segments`() {
        val json = """[{"title": "Intro", "start_ms": 0, "end_ms": 3000}, {"title": "Main", "start_ms": 3000, "end_ms": 10000}]"""
        val segments = ChapterEntryParser.parseSlideSegments(json)
        assertEquals(2, segments.size)
        assertEquals("Intro", segments[0].title)
        assertEquals(0L, segments[0].startMs)
        assertEquals(3000L, segments[0].endMs)
        assertEquals("Main", segments[1].title)
        assertEquals(3000L, segments[1].startMs)
        assertEquals(10000L, segments[1].endMs)
    }

    @Test
    fun `buildChapterEntries with no segments creates intro and outro`() {
        val chapters = ChapterEntryParser.buildChapterEntries(emptyList(), "Welcome", "Goodbye")
        assertEquals(2, chapters.size)
        assertEquals("Welcome", chapters[0].title)
        assertEquals(0L, chapters[0].startMs)
        assertEquals(5000L, chapters[0].endMs)
        assertEquals("Goodbye", chapters[1].title)
        assertEquals(5000L, chapters[1].startMs)
        assertEquals(10000L, chapters[1].endMs)
    }

    @Test
    fun `buildChapterEntries with segments creates intro plus segments plus outro`() {
        val segments = listOf(
            ChapterEntryParser.SlideSegmentData("Slide A", 5000, 15000),
            ChapterEntryParser.SlideSegmentData("Slide B", 15000, 25000)
        )
        val chapters = ChapterEntryParser.buildChapterEntries(segments, "Start", "End")
        assertEquals(4, chapters.size)
        assertEquals("Start", chapters[0].title)
        assertEquals("Slide A", chapters[1].title)
        assertEquals("Slide B", chapters[2].title)
        assertEquals("End", chapters[3].title)
        assertEquals(25000L, chapters[3].startMs)
        assertEquals(30000L, chapters[3].endMs)
    }

    @Test
    fun `buildChapterEntries uses default labels when intro and outro are blank`() {
        val chapters = ChapterEntryParser.buildChapterEntries(emptyList(), "", "")
        assertEquals("Introduction", chapters[0].title)
        assertEquals("Conclusion", chapters[1].title)
    }

    @Test
    fun `buildChapterEntries preserves custom intro and outro text`() {
        val chapters = ChapterEntryParser.buildChapterEntries(emptyList(), "Bienvenue", "Au revoir")
        assertEquals("Bienvenue", chapters[0].title)
        assertEquals("Au revoir", chapters[1].title)
    }

    @Test
    fun `buildChapterEntries outro starts at last segment end`() {
        val segments = listOf(
            ChapterEntryParser.SlideSegmentData("A", 0, 10000),
            ChapterEntryParser.SlideSegmentData("B", 10000, 20000)
        )
        val chapters = ChapterEntryParser.buildChapterEntries(segments, "I", "O")
        assertEquals(20000L, chapters.last().startMs, "Outro should start at last segment end")
        assertEquals(25000L, chapters.last().endMs, "Outro should end at last segment end + 5000")
    }

    @Test
    fun `parseSlideSegments with three segments`() {
        val json = """[{"title": "A", "start_ms": 0, "end_ms": 1000}, {"title": "B", "start_ms": 1000, "end_ms": 2000}, {"title": "C", "start_ms": 2000, "end_ms": 3000}]"""
        val segments = ChapterEntryParser.parseSlideSegments(json)
        assertEquals(3, segments.size)
        assertEquals("A", segments[0].title)
        assertEquals("B", segments[1].title)
        assertEquals("C", segments[2].title)
    }
}
