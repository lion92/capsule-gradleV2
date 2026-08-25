package capsule.i18n

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.MissingResourceException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapsuleMessagesTest {

    @ParameterizedTest
    @ValueSource(strings = [
        "task.group.generate",
        "task.group.collect",
        "task.group.deploy",
        "task.group.distribute",
        "task.group.transform",
        "task.group.capsule",
        "task.generateCapsuleScript.description",
        "task.generateCapsule.description",
        "task.generateCapsuleVideo.description",
        "task.generateCapsuleVideoAllLanguages.description",
        "task.deployCapsule.description",
        "task.distributeCapsuleVideo.description",
        "task.collectCapsuleContext.description",
        "task.transformCapsuleContext.description",
        "task.collectCapsuleRetrieve.description",
        "task.scaffoldCapsuleContext.description",
        "task.capsuleAiSmokeTest.description",
        "task.collectCapsuleAugmentedContext.description",
        "task.generateCapsuleContent.description",
        "task.extractSpeakerNotes.description",
        "task.translateAndExtractSpeakerNotes.description",
        "task.translateAndGenerateCapsuleVideos.description",
        "task.generateCapsuleContentAndVideos.description",
        "task.validateCapsuleVideoDuration.description",
        "task.generateCapsuleTranscript.description",
        "task.generateCapsulePodcast.description"
    ])
    fun `all task keys resolve in English`(key: String) {
        val value = CapsuleMessages.get(key, "en")
        assertNotNull(value)
        assertTrue(value.isNotBlank(), "Key '$key' has blank English value")
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "task.group.generate",
        "task.group.collect",
        "task.group.deploy",
        "task.group.distribute",
        "task.group.transform",
        "task.group.capsule",
        "task.generateCapsuleScript.description",
        "task.generateCapsule.description",
        "task.generateCapsuleVideo.description",
        "task.generateCapsuleVideoAllLanguages.description",
        "task.deployCapsule.description",
        "task.distributeCapsuleVideo.description",
        "task.collectCapsuleContext.description",
        "task.transformCapsuleContext.description",
        "task.collectCapsuleRetrieve.description",
        "task.scaffoldCapsuleContext.description",
        "task.capsuleAiSmokeTest.description",
        "task.collectCapsuleAugmentedContext.description",
        "task.generateCapsuleContent.description",
        "task.extractSpeakerNotes.description",
        "task.translateAndExtractSpeakerNotes.description",
        "task.translateAndGenerateCapsuleVideos.description",
        "task.generateCapsuleContentAndVideos.description",
        "task.validateCapsuleVideoDuration.description",
        "task.generateCapsuleTranscript.description",
        "task.generateCapsulePodcast.description"
    ])
    fun `all task keys resolve in French`(key: String) {
        val value = CapsuleMessages.get(key, "fr")
        assertNotNull(value)
        assertTrue(value.isNotBlank(), "Key '$key' has blank French value")
    }

    @ParameterizedTest
    @ValueSource(strings = ["en", "fr", "zh", "hi", "es", "ar", "bn", "pt", "ru", "ur"])
    fun `resolve task description in all supported languages`(code: String) {
        val value = CapsuleMessages.get("task.generateCapsuleVideo.description", code)
        assertNotNull(value)
        assertTrue(value.isNotBlank(), "task.generateCapsuleVideo.description should not be blank for '$code'")
    }

    @Test
    fun `missing key throws MissingResourceException`() {
        assertFailsWith<MissingResourceException> {
            CapsuleMessages.get("task.nonexistent.key", "en")
        }
    }

    @Test
    fun `fallback to English for unsupported language`() {
        val en = CapsuleMessages.get("task.generateCapsuleVideo.description", "en")
        val fallback = CapsuleMessages.get("task.generateCapsuleVideo.description", "xx")
        assertEquals(en, fallback)
    }

    @Test
    fun `forLanguage returns non-null bundle for all supported languages`() {
        for (code in listOf("en", "fr", "zh", "hi", "es", "ar", "bn", "pt", "ru", "ur")) {
            val bundle = CapsuleMessages.forLanguage(code)
            assertNotNull(bundle, "Bundle should not be null for '$code'")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "task.group.generate",
        "task.group.collect",
        "task.group.deploy",
        "task.group.distribute",
        "task.group.transform",
        "task.group.capsule"
    ])
    fun `group keys resolve to non-blank values in English`(key: String) {
        val value = CapsuleMessages.get(key, "en")
        assertTrue(value.isNotBlank(), "Group key '$key' should not be blank")
    }
}