# Changelog

All notable changes to `platform/java/observability` will be documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.0.0] — 2026-03-25

### Added
- `README.md` — module documentation covering all exported classes, feature
  status table (production-ready vs disabled/experimental), usage examples,
  and the upgrade path for re-enabling `ObservabilityLauncher` once
  ActiveJ DI stabilises. Closes audit finding MED-004.

### Fixed
- `build.gradle.kts` — added `testCompileOnly(libs.lombok)` and
  `testAnnotationProcessor(libs.lombok)` for test Lombok support.
  Closes audit finding HIGH-002.

### Known issues
- `ObservabilityLauncher` (ActiveJ Launcher-based bootstrap) is disabled
  pending stable `activej-inject` API in a post-6.0-rc2 release.
- `@Monitored` AOP aspect is prototype-only; do not use in production until
  histogram accuracy is verified with integration tests.
