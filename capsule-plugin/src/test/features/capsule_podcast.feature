@podcast
Feature: Capsule podcast extraction (CAP-PODCAST US-2)

  As a capsule-gradle producer
  I want to concatenate per-slide TTS MP3 files into a single podcast MP3
  So that stagiaires can revise on the go (audio-only mobile consumption)

  Scenario: Podcast disabled by default is a no-op skip (economy of ink)
    Given a Capsule podcast Gradle project with the capsule plugin applied
    And a demo deck with per-slide MP3 files present for podcast
    When I generate the capsule podcast with the default podcast config
    Then the podcast build succeeds
    And the podcast output mentions "skipped"
    And no podcast MP3 file is produced

  Scenario: Podcast enabled with slide MP3s produces a podcast MP3 via NoOp ffmpeg degraded
    Given a Capsule podcast Gradle project with the capsule plugin applied
    And a demo deck with per-slide MP3 files present for podcast
    When I generate the capsule podcast with podcast enabled and a NoOp ffmpeg path
    Then the podcast build succeeds
    And the podcast output mentions "CAPSULE PODCAST"
    And the podcast output mentions "degraded"
    And no podcast MP3 file is produced

  Scenario: Podcast enabled with no slide MP3s warns and skips gracefully
    Given a Capsule podcast Gradle project with the capsule plugin applied
    And a demo deck is present but has no per-slide MP3 files for podcast
    When I generate the capsule podcast with podcast enabled and a NoOp ffmpeg path
    Then the podcast build succeeds
    And the podcast output mentions "no per-slide"