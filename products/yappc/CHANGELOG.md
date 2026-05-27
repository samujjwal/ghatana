# YAPPC Changelog

All notable YAPPC product changes are summarized here. Entries should describe release-relevant behavior, contracts, evidence, or operational impact; raw merge commits and placeholder commit messages do not belong in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`ade7462`](https://github.com/samujjwal/ghatana/commit/ade746204919f9e006690c30371a82028f77ed93)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`2103f84`](https://github.com/samujjwal/ghatana/commit/2103f84ea84043fb22febf30b5e2fe0d4c4e4c05)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`9e37000`](https://github.com/samujjwal/ghatana/commit/9e370000b856df0a93f05731a452972c368f083b)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`6f1692b`](https://github.com/samujjwal/ghatana/commit/6f1692bdd665ecb6cd4e4c0086fa29fdc0ccb6f8)

### Added

- Kernel ProductUnitIntent API handoff with typed export/validation against Kernel public contract values.
- Data Cloud-backed Kernel lifecycle truth ingestion, typed `kernel_lifecycle_truth` record handling, production local-provider guards, and degraded truth semantics.
- Evidence-backed lifecycle phase packets covering degraded dependencies, canonical action metadata, idempotent phase actions, activity fidelity, and backend-provided transition readiness.
- Product-family control-plane APIs and UI for assets, release readiness, promotions, documentation truth, reuse recommendations, and Kernel lifecycle visibility.
- Admin observability, feature flag, A/B testing, and prompt-version routes backed by canonical backend APIs and route/OpenAPI/client parity checks.
- End-to-end lifecycle matrix coverage metadata for workspace, project, intent, shape, validate, generate, Kernel handoff, run, observe, learn, evolve, product-family, admin, i18n, a11y, and performance journeys.
- YAPPC release evidence scorecard JSON plus schema checks for backend, frontend, contract, E2E, a11y, performance, security, privacy, governance, release-gate, and startup-diagnostic dimensions.

### Changed

- Phase cockpit, lifecycle, run, observe, learn, and evolve surfaces now render backend packet truth, shared empty/loading/error states, localized action keys, and degraded dependency recovery details.
- README, quick-start, Kernel visibility, and production-readiness docs now point to current `frontend` paths and evidence-backed readiness claims.
- Test suites are grouped by unit, integration, contract, E2E, a11y, performance, security, privacy, governance, and release evidence checks.
- Generated ProductUnitIntent and degraded PhasePacket contract tests now use deterministic golden files.

### Fixed

- Data Cloud, evidence, governance, and runtime truth failures now surface explicit degraded/blocking states instead of silently returning empty results.
- Production configuration rejects local/mock/fake/demo Kernel lifecycle truth sources.
- Backend route authorization, manifest, OpenAPI, generated client, frontend route/action, privacy, security, and governance parity are covered by executable checks.
