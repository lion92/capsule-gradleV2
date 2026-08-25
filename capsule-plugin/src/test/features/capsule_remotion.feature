@anim @remotion
Feature: Capsule Remotion capture strategy (CAP-ANIM)

  As a capsule-gradle producer
  I want to render the deck frame by frame through Remotion when animations matter
  So that I get entrance transitions, drift and cross-fades instead of a frozen image per slide

  Scenario: Remotion capture strategy falls back to NoOp when node is unavailable
    Given a Capsule remotion Gradle project with the capsule plugin applied
    And a demo deck and script are present for remotion capture
    When I generate the capsule video with remotion capture strategy and a missing node binary
    Then the remotion build succeeds
    And the remotion output mentions "remotion strategy"
    And a remotion WebM file is produced

  Scenario: Remotion capture strategy with strictMode fails when node is unavailable
    Given a Capsule remotion Gradle project with the capsule plugin applied
    And a demo deck and script are present for remotion capture
    When I generate the capsule video with remotion capture strategy, a missing node binary and strictMode enabled
    Then the remotion build fails
    And the remotion failure message contains "remotion is not available"

  Scenario: Remotion config DSL accepts fps and concurrency
    Given a Capsule remotion Gradle project with the capsule plugin applied
    And a demo deck and script are present for remotion capture
    When I generate the capsule video with remotion capture strategy, a missing node binary, fps 60 and concurrency 8
    Then the remotion build succeeds
    And the remotion output mentions "remotion strategy"