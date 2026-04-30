# Owner: YAPPC Infrastructure — Data-Cloud Integration

**Team:** YAPPC Backend / Infrastructure Team
**Slack:** #yappc-infra
**Parent ownership:** `products/yappc/OWNER.md`
**Last Updated:** 2026-04-29

## Module Purpose

Java adapter layer integrating YAPPC runtime with the Data-Cloud platform. Provides:

- `SecurityServiceAdapter` — vulnerability scanning, dependency audit, security reporting
- `ConfidenceScoringService` — heuristic confidence scoring for AI-generated artifacts
- Cache, resilience, and mapping layers between YAPPC domain models and Data-Cloud SPI
- Health and tracing instrumentation for integration flows

## Ownership Map

| Area | Path | Owned By |
|------|------|---------|
| Security adapters | `src/main/.../adapter/` | YAPPC Infrastructure |
| AI confidence scoring | `src/main/.../ai/scoring/` | YAPPC AI Team |
| Cache / resilience | `src/main/.../cache/` | YAPPC Infrastructure |
| Data-Cloud mappers | `src/main/.../datacloud/` | YAPPC Infrastructure |

## Dependency Rules

- Depends on `products:data-cloud:spi` (SPI only — not implementation).
- Depends on `products:yappc:libs:java:yappc-domain` for domain model mapping.
- Must not import product-specific business logic from `core/yappc-services`.
- Platform modules (`platform:java:observability`, `platform:java:cache`) are the canonical infra abstractions.

## Production Stability Notes

- `SecurityServiceAdapter.generateSbom()` returns a minimal CycloneDX stub. Full SBOM generation
  requires the CycloneDX Gradle plugin to be applied at build time (`org.cyclonedx.bom`).
  The build task `cyclonedxBom` must be invoked in CI to produce a real artifact.
- `ConfidenceScoringService` uses heuristic scoring — not calibrated against an evaluation dataset.
  Treat scores as advisory signals, not hard gates, until evaluation fixtures are established.
- Coverage verification is temporarily disabled (`jacocoTestCoverageVerification`). Re-enable once
  test coverage reaches the module minimum threshold.
