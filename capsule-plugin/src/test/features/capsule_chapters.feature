@chapters
Feature: Capsule chapter markers (CAP-CHAPITRE)

  As a capsule-gradle producer
  I want to generate chapter metadata and intro/outro cards
  So that my video has Matroska chapter markers for navigation

  Scenario: Chapters disabled by default produces no chapter files
    Given a Capsule chapters Gradle project with the capsule plugin applied
    When I run the chapters build with default config
    Then the chapters build succeeds
    And the chapters output directory does not exist

  Scenario: Chapters enabled via DSL produces chapter metadata and cards
    Given a Capsule chapters Gradle project with the capsule plugin applied
    When I run the chapters build with chapters enabled via DSL
    Then the chapters build succeeds
    And a chapters.json file is generated
    And an intro.html card is generated
    And an outro.html card is generated

  Scenario: Chapters enabled via gradle property with custom text
    Given a Capsule chapters Gradle project with the capsule plugin applied
    When I run the chapters build with chapters enabled via gradle property and custom text
    Then the chapters build succeeds
    And a chapters.json file is generated with correct entries
