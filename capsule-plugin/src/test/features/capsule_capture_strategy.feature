@capture @strategy
Feature: Capsule capture strategy selection (CAP-CR3-3-5)

  As a capsule-gradle producer
  I want to select between Playwright real-time recording and screenshot-based capture
  So that I can trade off reliability/speed against recording fidelity

  Scenario: Capture strategy PLAYWRIGHT is the default and produces a WebM
    Given a Capsule capture strategy Gradle project with the capsule plugin applied
    And a demo deck and script are present for capture strategy
    When I generate the capsule video with the default capture strategy
    Then the capture strategy build succeeds
    And the capture strategy output mentions "playwright strategy"
    And a capture strategy WebM file is produced

  Scenario: Capture strategy SCREENSHOT produces a WebM via screenshot path
    Given a Capsule capture strategy Gradle project with the capsule plugin applied
    And a demo deck and script are present for capture strategy
    When I generate the capsule video with capture strategy "screenshot"
    Then the capture strategy build succeeds
    And the capture strategy output mentions "screenshot strategy"
    And a capture strategy WebM file is produced

  Scenario: Capture strategy SCREENSHOT with strictMode fails when ffmpeg is unavailable
    Given a Capsule capture strategy Gradle project with the capsule plugin applied
    And a demo deck and script are present for capture strategy
    When I generate the capsule video with capture strategy "screenshot" and strictMode enabled
    Then the capture strategy build fails
    And the capture strategy failure message contains "screenshot is not available"