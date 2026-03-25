# Changelog

All notable changes to `platform/java/core` will be documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.0.0] — 2026-03-25

### Added
- `ErrorResponseBuilder` — standardised HTTP error response factory in
  `com.ghatana.platform.core.exception`. Produces `Map<String, Object>` with
  fields `code`, `message`, `status`, `timestamp`, and optional `metadata`.
  Closes audit finding CONS-006.
- `ErrorResponseBuilderTest` — 7 unit tests covering construction from
  `PlatformException`, `ErrorCode`, immutability of `build()`, and
  `toJson()` output.

### Changed
- `JsonUtils.toJson` / `JsonUtils.fromJson` — checked `JsonProcessingException`
  is now wrapped in `RuntimeException` for all callers; callers that previously
  handled the checked exception should remove those catch blocks.

### Notes
- This module is the canonical dependency for exception handling and JSON
  serialisation across all platform services. Do not depend on
  `kernel/util/JsonUtils` — it is deprecated and will be removed.
