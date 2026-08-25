package capsule.i18n

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertNotEquals

class MessagesFrTranslationTest {

    @ParameterizedTest
    @ValueSource(strings = [
        "task.group.generate",
        "task.group.collect",
        "task.group.deploy",
        "task.group.distribute",
        "task.group.transform",
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
    fun `should translate task description key to French`(key: String) {
        val en = CapsuleMessages.get(key, "en")
        val fr = CapsuleMessages.get(key, "fr")
        assertNotEquals(en, fr, "$key should be translated to French, not a copy of English")
    }
}