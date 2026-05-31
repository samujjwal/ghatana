# YAPPC Changelog

All notable YAPPC product changes are summarized here. Entries should describe release-relevant behavior, contracts, evidence, or operational impact; raw merge commits and placeholder commit messages do not belong in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]
- **Changed**: daf fad fafdt [`8c024b4`](https://github.com/samujjwal/ghatana/commit/8c024b478ebcec4fbe5db75b854dcdfcc2775625)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`9058b77`](https://github.com/samujjwal/ghatana/commit/9058b7747f6056cbb9800376801c1946466d7529)
- **Changed**: fdas faf fahhjk dsaer [`72f02d6`](https://github.com/samujjwal/ghatana/commit/72f02d6d70c60c2efd32870d29537d1d23cc6b3a)
- **Changed**: daf fdaf fdasf fads fdas f [`f092de3`](https://github.com/samujjwal/ghatana/commit/f092de32cee8a6b2780d95d730622b85ffa93aa5)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`1d57ce0`](https://github.com/samujjwal/ghatana/commit/1d57ce05b7e60d146ad9c6a53afc61a0ba2f9bd3)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`b847cf4`](https://github.com/samujjwal/ghatana/commit/b847cf40509f134715162192f9ea64d94bad82b4)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`54b1f4c`](https://github.com/samujjwal/ghatana/commit/54b1f4cbeb76acd61f1a895485bd756c3dafcd3f)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`9beff40`](https://github.com/samujjwal/ghatana/commit/9beff409ebcc508f53dfe6f5d89ee3b3cf2f9501)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`a25c5fe`](https://github.com/samujjwal/ghatana/commit/a25c5fe75cc35d3f15c550a5c79417bfeed4fdb8)
- **Changed**: aa dd gg aa ff 4 [`1836f3f`](https://github.com/samujjwal/ghatana/commit/1836f3fb9ea24a295177ffb3cb52cc826f9148fc)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`7d8f07d`](https://github.com/samujjwal/ghatana/commit/7d8f07d5bf556c8e8e6300ecaf11920b6ea64d84)
- **Changed**: Merge branch 'main' of https://github.com/samujjwal/ghatana [`2a37e8d`](https://github.com/samujjwal/ghatana/commit/2a37e8d0702271a10d47f5707a2720fd657c7f54)
- **Changed**: dfa fda fda fda 7 [`597868b`](https://github.com/samujjwal/ghatana/commit/597868bfc2596e046a20f6bdc5733cfb01bcb4ff)
- **Changed**: dfa fda fda fda 6 [`8f988d4`](https://github.com/samujjwal/ghatana/commit/8f988d48343a0eb386f5631d0d6b3d2f7dad96be)
- **Changed**: dfa fda fda fda 5 [`495d21b`](https://github.com/samujjwal/ghatana/commit/495d21bcd79a5cdb8b6af3197ce981b086344895)
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
