# Documentation Consolidation and Repository Cleanup Plan

> Date: 2026-03-22
> Scope: Entire repository
> Goal: Reduce documentation sprawl, preserve critical detail, define canonical document sets, and remove unnecessary files safely

---

## Executive Summary

This plan converts the repository scan into a practical cleanup program for documentation, scripts, generated artifacts, and temporary files.

The target end state is:

- one monorepo-level documentation spine
- one canonical document set per active product
- one `README.md` per package/module by default
- all historical material archived with provenance
- all temporary or generated local-only files removed from version control

This is not a plan to delete documentation aggressively. It is a plan to:

- keep what is authoritative
- merge what is redundant
- archive what is historical
- delete what is temporary, generated, stale, or duplicative

---

## Scan Baseline

Repository scan observations used for this plan:

- Approximate tracked file count in recursive scan: `21,200`
- Doc-like files: `2,045`
- Files under `/docs/` paths: `1,498`
- Doc-like files outside `/docs/`: `547`
- Files under `archive/` or `archived/`: `324`
- Obvious residue files (`.stub`, `.backup`, `.DS_Store`, `README_OLD.md`, `README_NEW.md`): `34`

Largest documentation concentrations:

- `products/dcmaar`: `471` doc-like files
- `products/yappc`: `408`
- `products/app-platform`: `269`
- `products/software-org`: `206`
- `products/tutorputor`: `96`
- `products/phr`: `68`
- `products/virtual-org`: `65`

High-duplication filename patterns:

- `README.md`: `82`
- `DESIGN_ARCHITECTURE.md`: `62`
- `USER_MANUAL.md`: `59`
- `TECHNICAL_REFERENCE.md`: `59`
- `OPERATIONS.md`: `59`
- `KNOWN_ISSUES_TROUBLESHOOTING.md`: `59`
- `CODING.md`: `59`
- `TESTING.md`: `58`
- `FUTURE_BACKLOG.md`: `57`

Representative source-of-truth or index documents reviewed during planning:

- `README.md`
- `DEVELOPER_GUIDE.md`
- `docs/ONBOARDING.md`
- `docs/kernel-platform-dev/README.md`
- `products/app-platform/docs/README.md`
- `products/phr/docs/README.md`
- `products/dcmaar/docs/README.md`
- `products/yappc/docs/README.md`
- `products/tutorputor/docs/README.md`
- `products/virtual-org/docs/README.md`

---

## Principles

1. Code outranks docs. If documentation conflicts with code, code wins.
2. Every live doc must have a clear owner, scope, status, and authority level.
3. Active docs should explain the present state, not accumulate every previous state.
4. Historical material should be archived, not mixed with live planning.
5. Package-level docs should default to a single `README.md` unless the package is independently operated or published.
6. Status trackers and implementation plans must be singular per scope.
7. Local helper scripts, generated reports, and backup files should not remain in the repo unless they are intentional artifacts.

---

## Canonical Documentation Model

### 1. Monorepo-Level Canonical Set

Keep a minimal live set under `docs/`:

- `docs/README.md`
- `docs/MONOREPO_VISION.md`
- `docs/MONOREPO_ARCHITECTURE.md`
- `docs/GOVERNANCE.md`
- `docs/ROADMAP.md`
- `docs/STATUS.md`
- `docs/adr/`
- `docs/platform-libraries/`
- `docs/archive/`

Rules:

- only one active monorepo roadmap
- only one active monorepo status tracker
- audits, deep research, and remediation writeups move to archive unless they remain directly actionable

### 2. Product-Level Canonical Set

Each active product should converge toward:

- `docs/README.md`
- `docs/VISION.md`
- `docs/MARKET_ANALYSIS.md` when market/regulatory context matters
- `docs/ARCHITECTURE.md`
- `docs/DESIGN_AND_DIAGRAMS.md`
- `docs/FEATURES_AND_REQUIREMENTS.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/TEST_STRATEGY_AND_CASES.md`
- `docs/OPERATIONS.md`
- `docs/USER_GUIDE.md` or `docs/MANUAL.md` only if externally used
- `docs/adr/`
- `docs/archive/`

Rules:

- product-specific delivery plans should not live at repo root
- product-specific audits should not remain in live root docs
- workflow, API, screen, and design documents can remain split only if they are truly used independently

### 3. Package and Module Documentation

Default shape:

- `README.md`

Allowed extension for complex reusable packages:

- `README.md`
- `DESIGN_ARCHITECTURE.md`

Avoid by default:

- separate `USER_MANUAL.md`, `OPERATIONS.md`, `TECHNICAL_REFERENCE.md`, `KNOWN_ISSUES_TROUBLESHOOTING.md`, `FUTURE_BACKLOG.md`, `CODING.md`, and `TESTING.md` for every small package

---

## Document Decision Rules

Apply these rules file by file.

### Keep Live

Keep a document live if all are true:

- it is still authoritative or actively used
- it has a clear owner
- it is referenced by a current index or README
- it represents the current model, not a prior state
- its scope is not duplicated by a stronger document

### Merge Then Archive

Merge a document into another file if:

- it overlaps heavily with a stronger canonical doc
- it is one of several trackers or plans for the same scope
- it is a section-level split that no longer needs to remain separate

After merge:

- archive the source document with a superseded note

### Archive

Archive a document if any of these are true:

- it is a prior implementation phase, audit, review, or session summary
- it is a migration-era completion report
- it is exploratory or brainstorming material
- it contains useful detail but should not drive current work

### Delete

Delete a file if any of these are true:

- temporary editor or system file
- generated local report not intended as a durable artifact
- backup variant of another file
- obsolete helper script with no remaining workflow ownership
- duplicate renamed variant such as `README_OLD.md` or `README_NEW.md`

---

## Priority Areas and Actions

### P0. Monorepo Root Cleanup

Current issues:

- root-level docs overlap between `README.md`, `DEVELOPER_GUIDE.md`, `docs/ONBOARDING.md`, and many root `docs/*.md` files
- stale references to `ghatana-new`
- local-only helper files exist at repo root

Actions:

1. Create canonical `docs/README.md`, `docs/MONOREPO_VISION.md`, `docs/MONOREPO_ARCHITECTURE.md`, `docs/GOVERNANCE.md`, `docs/ROADMAP.md`, and `docs/STATUS.md`.
2. Merge `DEVELOPER_GUIDE.md` and `docs/ONBOARDING.md` into one onboarding/developer guide.
3. Merge overlapping root execution docs:
   - `docs/CONSOLIDATED_IMPLEMENTATION_PLAN.md`
   - `docs/CONSOLIDATED_IMPLEMENTATION_PLAN_V2.md`
   - `docs/IMPLEMENTATION_PROGRESS_TRACKER.md`
   - `docs/TRUSTWORTHY_IMPLEMENTATION_TRACKER.md`
4. Move one-off audits and analysis docs from root `docs/` to `docs/archive/` unless they remain current controls.
5. Remove or relocate:
   - `.DS_Store`
   - `fix_generator.sh`
   - `test_script.sh`
   - `dependency-alignment-report.json`
   - `dependency-convergence-report.json`

Verification:

- no stale `ghatana-new` references in live docs
- no multiple active monorepo plans or status trackers
- root directory contains only durable operational files

### P1. `docs/` Tree Rationalization

Current issues:

- `docs/archive/` alone contains `100` markdown files
- `docs/kernel-platform-dev/` is relatively disciplined already
- root `docs/` contains overlapping audits, plans, and trackers

Actions:

1. Preserve `docs/kernel-platform-dev/` as a model for active-vs-archive separation.
2. Move all monorepo-wide historical reports into `docs/archive/<topic>/`.
3. Keep only one active remediation plan in `docs/execution-plans/`.
4. Keep `docs/adr/` as the durable decision log, not a second planning area.
5. Ensure every top-level live file in `docs/` is linked from `docs/README.md`.

Verification:

- every top-level live doc in `docs/` has a unique purpose
- no duplicate “plan”, “tracker”, or “audit” files remain active for the same topic

### P2. `products/app-platform` Consolidation

Current issues:

- `267` doc-like files
- `103` in `docs/archive`
- `49` in `docs/epics`
- `35` in `docs/lld`
- additional prompts, research, review reports, sales-kit artifacts, PDFs, and specification variants

Actions:

1. Keep a canonical live set:
   - `README.md`
   - `DOCUMENT_AUTHORITY_MAP.md`
   - `adr/`
   - one architecture document
   - one design/diagram document
   - one implementation plan
   - one feature/epic index
   - one compliance/operations document
   - one market/research document if still strategically needed
2. Merge split architecture spec parts into a single architecture spine.
3. Merge epic and story material into one execution-facing index plus appendices.
4. Archive:
   - sales-kit documents
   - raw PDF source material
   - prompt-generation docs
   - review-report stacks under archive/reviews
   - duplicated TDD expansion documents
5. Keep legal and traceability appendices only if still actively referenced by the authoritative map.

Verification:

- one clear authority order remains
- no live document category has more than one primary file
- archive is clearly separated from implementation guidance

### P3. `products/phr` Consolidation

Current state:

- already the most coherent active product doc system
- `67` doc-like files with strong hierarchy and explicit reading order

Actions:

1. Use PHR as the reference model for the rest of the repo.
2. Keep its five-section organization.
3. Consider merging delivery plans and status material where overlap is high.
4. Preserve detailed test packs because they are structured and product-specific.

Verification:

- no loss of traceability
- no duplicated release-definition or delivery-plan logic across multiple files

### P4. `products/dcmaar` Consolidation

Current issues:

- `471` doc-like files
- repeated package/app/lib doc kits with the same 7-8 filenames
- `44` files already in archive paths

Actions:

1. Keep one product-level documentation set under `products/dcmaar/docs/`.
2. Replace repeated app/lib/module doc kits with:
   - one local `README.md`
   - optional `DESIGN_ARCHITECTURE.md` for major components only
3. Remove duplicated `USER_MANUAL.md`, `OPERATIONS.md`, `TECHNICAL_REFERENCE.md`, `KNOWN_ISSUES_TROUBLESHOOTING.md`, `FUTURE_BACKLOG.md`, `CODING.md`, `TESTING.md` where they are template-level repeats.
4. Move session notes, completion reports, and experiments into archive.
5. Keep security/browser-extension material only where it is genuinely product-driving.

Verification:

- dramatic reduction in repeated doc-kit files
- all surviving module docs have a clear audience

### P5. `products/yappc` Consolidation

Current issues:

- `408` doc-like files
- `41` in `docs/audits`
- `36` in `docs/archive`
- `37` in `frontend/docs`
- many implementation summaries, final summaries, audits, and status reports

Actions:

1. Keep a single product doc spine in `products/yappc/docs/`.
2. Move `audits/` and `archive/` to historical status unless explicitly active.
3. Merge repeated implementation summaries and roadmap/status writeups into one canonical plan and one status doc.
4. Collapse frontend documentation into:
   - architecture
   - development
   - testing
   - archive
5. Preserve only durable guidance and product architecture in live docs.

Verification:

- one YAPPC roadmap
- one YAPPC status tracker
- audits do not compete with live docs

### P6. `products/tutorputor`, `products/virtual-org`, `products/software-org`, `products/data-cloud`

Actions:

1. Tutorputor:
   - keep the main product docs set
   - collapse app-specific duplicate doc kits
   - archive service consolidation reports once merged
2. Virtual-Org:
   - fix broken references in `products/virtual-org/docs/README.md`
   - keep one product-level set plus minimal module READMEs
3. Software-Org:
   - separate live docs from session archives under client/web
   - keep only durable product and UI documentation live
4. Data-Cloud:
   - keep root README and a small product doc set
   - collapse UI package doc kit to minimal form

Verification:

- each product has one obvious entry point
- no dead links in product README files

### P7. `platform/` and Shared Package Docs

Current issues:

- multiple platform TypeScript packages carry nearly identical 8-file doc kits

Actions:

1. Keep detailed docs only for packages that are externally consumed, published, or operationally independent.
2. For small internal packages, collapse to `README.md`.
3. Keep `DESIGN_ARCHITECTURE.md` only for complex or reusable packages.
4. Remove repeated doc templates that do not add package-specific information.

Verification:

- package docs scale with package complexity
- platform docs are not more elaborate than the packages themselves

---

## Temporary, Generated, and Residual Files

Remove first-wave residue files after verification:

- `.DS_Store`
- `*.backup`
- `*.stub`
- `README_OLD.md`
- `README_NEW.md`
- local helper scripts with one-off edits
- local generated test reports such as `playwright-report.json`

Candidates already visible from scan:

- `.DS_Store`
- `products/audio-video/modules/intelligence/ai-voice/README_OLD.md`
- `products/audio-video/modules/intelligence/ai-voice/README_NEW.md`
- `products/audio-video/modules/speech/stt-service/src/main/java/com/ghatana/stt/core/pipeline/DefaultAdaptiveSTTEngine.java.backup`
- `products/dcmaar/apps/parent-dashboard/playwright-report.json`
- `fix_generator.sh`
- `test_script.sh`

Rule:

- if a file exists only to support a local session or manual patch, it should be deleted or moved out of the repo once its intent has been absorbed into code or docs

---

## Governance Changes Required

Add a documentation governance section to the live monorepo docs:

- every live doc declares:
  - owner
  - status
  - scope
  - intended audience
  - authority level
  - supersedes/superseded-by
  - last reviewed date
- every product `docs/README.md` must define authority order
- every archive folder must contain a short `README.md` stating that contents are historical
- no new “final report”, “complete summary”, or “tracker” files may be added without linking them to a canonical scope owner

---

## CI and Verification

Add automated checks for:

- broken markdown links
- stale references to `ghatana-new`
- orphan live docs not linked from an index
- duplicate active tracker names
- duplicate active implementation plan names
- forbidden temporary files in git
- archive docs incorrectly linked as current authority

Suggested lightweight checks:

- `rg -n 'ghatana-new' README.md docs products`
- `rg --files | rg '\.stub$|\.backup$|README_(OLD|NEW)\.md$|\.DS_Store$'`
- link checker over `README.md`, `docs/`, and `products/*/docs/`

---

## Execution Order

Recommended order:

1. Monorepo root and `docs/`
2. `products/app-platform`
3. `products/dcmaar`
4. `products/yappc`
5. `products/tutorputor`
6. `products/virtual-org`
7. `products/software-org`
8. `products/data-cloud`
9. `platform/` package docs
10. temp/generated/residue cleanup
11. CI and governance enforcement

---

## Definition of Done

The repository is considered documentation-clean when:

- every active scope has one canonical entry point
- every active scope has one canonical plan and one canonical status tracker at most
- all session reports, audits, and historical summaries are archived
- all package-level docs default to a minimal form
- broken links and stale root references are removed
- temporary and generated local-only files are gone from version control

---

## Appendix: Initial Keep/Archive/Delete Heuristic

Use this quick heuristic during triage:

- `KEEP`: current index, current architecture, current roadmap, current status, ADRs, product docs with active use
- `MERGE+ARCHIVE`: duplicate trackers, duplicate plans, split specs, repeated summaries, duplicate research docs
- `ARCHIVE`: old audits, migration completion reports, session summaries, review bundles, brainstorming material
- `DELETE`: temp scripts, editor artifacts, backup files, local generated reports, duplicate README variants
