# YAPPC Documentation Archive

> **Status:** Historical | **Product:** YAPPC | **Last Updated:** 2026-03-27

This directory contains archived documentation for the YAPPC product.

## Current Implementation Status (2026-03-27)

The YAPPC artifact-compiler has been significantly enhanced with the following completed work:

**Phase 1 (Governance Layer - Complete):**
- Fixed package.json exports for source-providers, compile-back, builder subpaths
- Exported scanner API publicly from inventory
- Added SkippedArtifactSchema to inventory types
- Fixed binary checksum to use SHA-256
- Fixed npm/pnpm package boundary detection
- Made scan output ordering deterministic
- Added ZIP path containment guard against zip-slip attacks
- Fixed GitLab provider temp root lifecycle
- Failed closed on GitHub tree truncation
- Added SourceLocatorSchema, credentialRef, diagnostics to provider types

**Phase 2 (Graph & Synthesis - Complete):**
- Fixed graph query schema to allow URN IDs
- Added graph validation utility (validateGraph.ts)
- Added relative import, extension, index, alias support to symbol resolver
- Made pipeline return complete SemanticProductModel container
- Exposed extractor instances and capabilities (runtime registry)

**Phase 3 (Model Enhancements - Complete):**
- Added rawFragmentRef, checksum, risk, relatedGraphNodeIds to residual schema
- Added graphNodeIds, sourceRefs, residualIslandIds to model base
- Added model diff/version metadata schemas

**Phase 4 (Database - Partial):**
- Added V12 migration for snapshot columns (content_checksum, snapshot_id, version_id, is_tombstone)

**Phase 5 (Patch Lifecycle - Partial):**
- Added full patch lifecycle types (ModelChange, ChangePlan, FilePatch, ReviewBundle, ValidationResult, RollbackMetadata)
- Added validation and review bundle to patch coordinator
- Enforced residual overlap blocking in patch coordinator
- Added rollback metadata to patch types

**Remaining Work:**
- Phase 4: Backend Java controller/service/repository changes (requires backend context)
- Phase 5: React patch emitter AST/range-based diffs (complex TypeScript work)
- Phase 6: UX/workflow integration (frontend/backend)
- Tests for completed phases
- Additional cleanup tasks

For the latest active documentation, see the parent directory or [../README.md](../README.md).

## Contents

- `summaries/` - Historical implementation summaries
- `audits/` - Audit reports and verification documents
