# Changelog

All notable changes to `platform/java/kernel` will be documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.0.0] — 2026-03-25

### Deprecated
- `com.ghatana.kernel.util.JsonUtils` — **all methods deprecated for removal**.
  The class now delegates to `com.ghatana.platform.core.util.JsonUtils`.
  Migrate all usages to `core/util/JsonUtils` before the next major release.
  Closes audit finding CONS-005.

### Migration
```java
// Before
import com.ghatana.kernel.util.JsonUtils;

// After
import com.ghatana.platform.core.util.JsonUtils;
```

### Notes
- The deprecation preserves the original unchecked API contract: both
  `toJson` and `fromJson` still throw `RuntimeException` (wrapping the checked
  `JsonProcessingException` from the core implementation).
