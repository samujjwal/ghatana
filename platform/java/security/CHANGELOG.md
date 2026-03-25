# Changelog

All notable changes to `platform/java/security` will be documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.0.0] — 2026-03-25

### Fixed
- `build.gradle.kts` — added `testCompileOnly(libs.lombok)` and
  `testAnnotationProcessor(libs.lombok)` which were missing, causing Lombok
  annotations to be silently ignored in test classes and producing confusing
  NPEs at test runtime. Closes audit finding HIGH-002.

### Notes
- All four Lombok dependency configurations are now present: `compileOnly`,
  `annotationProcessor`, `testCompileOnly`, `testAnnotationProcessor`.
