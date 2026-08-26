package capsule.scenarios

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["src/test/features/capsule_chapters.feature"],
    glue = ["capsule.scenarios"],
    plugin = ["pretty", "html:build/reports/tests/cucumberTestChapters.html"],
    monochrome = true
)
class CapsuleChaptersCucumberRunner
