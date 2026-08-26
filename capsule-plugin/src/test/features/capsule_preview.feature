@preview
Feature: Capsule preview dry-run (CAP-PREVIEW US-2)

  As a capsule-gradle producer
  I want to run the pipeline in preview-only mode
  So that I can quickly validate slide layouts without the full video pipeline cost

  Scenario: Preview disabled by default preserves full pipeline behavior
    Given a Capsule preview Gradle project with the capsule plugin applied
    When I run the capsule build with the default preview config
    Then the preview build succeeds
    And the preview output does not mention "PREVIEW"

  Scenario: Preview enabled via DSL block is recognized without error
    Given a Capsule preview Gradle project with the capsule plugin applied
    When I run the capsule build with previewOnly enabled via DSL
    Then the preview build succeeds

  Scenario: Preview enabled via gradle property is recognized without error
    Given a Capsule preview Gradle project with the capsule plugin applied
    When I run the capsule build with previewOnly enabled via gradle property
    Then the preview build succeeds
