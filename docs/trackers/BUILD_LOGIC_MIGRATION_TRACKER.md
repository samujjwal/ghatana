# Build Logic Migration Tracker

## Snapshot (2026-04-12)

- Total files using migration-related convention plugins: `65` (initial baseline)
- Legacy convention users remaining: `0` (plugin-ID migration complete)
- Build-logic convention users: `65+` (all legacy IDs replaced in `build.gradle.kts` files)
- Fallback-dependent source-bearing modules remaining: `84`
- YAPPC standalone smoke check (`cd products/yappc && ../../gradlew --no-daemon help`): PASS
- YAPPC standalone migrated-batch validation (`cd products/yappc && ../../gradlew --no-daemon :products:yappc:core:agents:runtime:check :products:yappc:core:knowledge-graph:check :products:yappc:core:services-lifecycle:check :products:yappc:infrastructure:datacloud:check`): PASS
- Tracker source: plugin inventory scan + `.github/CODEOWNERS`

Current blocker for Phase 3:

- Root fallback removal is not yet safe because additional Java/Kotlin modules still rely on implicit root fallback conventions (they have sources but no explicit `java-module`/`java-application`/`protobuf-module` plugin IDs).
- Root fallback detector was corrected to treat `finance-domain-module` as migrated; previously those finance modules were still being double-configured via legacy fallback.
- YAPPC standalone validation must use `:products:yappc:*` alias task paths, not direct `:core:*` paths, because standalone settings intentionally expose both path families to the same project directories.
- Latest deterministic broad-build blockers fixed in this pass:
  - `platform/contracts/src/test/java/com/ghatana/contracts/serialization/ContractSerializationTest.java`
  - `platform-kernel/kernel-core` OpenAPI contract coverage
  - `shared-services/auth-gateway` Spring-based contract/E2E test compilation failures
  - `shared-services/user-profile-service` Spring-based contract/E2E test compilation failures
  - `products/flashit` root Spotless symlink traversal failures
  - `platform/java/audit/build.gradle.kts` whitespace-only Spotless failure
  - `platform/java/database/build.gradle.kts` whitespace-only Spotless failure
  - `platform/java/config/build.gradle.kts` whitespace-only Spotless failure

## Status Legend

- `DONE`: migrated to `build-logic` plugin IDs and validated at module scope.
- `PLANNED`: queued for migration wave.
- `BLOCKED`: pending parity feature or external blocker.

## Wave A - Foundations (platform/shared/integration/templates)

| Module build file | Owner | Plugin state | Status | Notes |
|---|---|---|---|---|
| `platform/java/agent-core/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Migrated on 2026-04-12; check passed. |
| `platform/java/ai-integration/build.gradle.kts` | `@ghatana/ai-team` | `java-module` | DONE | Migrated on 2026-04-12; check passed. |
| `platform/java/audit/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Migrated in follow-up root-blocker pass; module check passed. |
| `platform/java/database/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Migrated in follow-up root-blocker pass; module check passed. |
| `platform/java/config/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Migrated in follow-up root-blocker pass; module check passed. |
| `shared-services/incident-service/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Migrated on 2026-04-12; check passed. |
| `platform/java/testing/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Migrated in broad plugin-ID replacement wave. |
| `integration-tests/phr-finance-integration/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Migrated in broad plugin-ID replacement wave. |
| `templates/java-module/build.gradle.kts` | `@ghatana/platform-team` | `java-module` | DONE | Template now uses canonical plugin ID. |

## Wave B - High Traffic Products

### AEP (`@ghatana/aep-team`)

- `products/aep/aep-analytics/build.gradle.kts` - DONE
- `products/aep/aep-scaling/build.gradle.kts` - DONE
- `products/aep/aep-agent-runtime/build.gradle.kts` - DONE
- `products/aep/aep-security/build.gradle.kts` - DONE
- `products/aep/aep-central-runtime/build.gradle.kts` - DONE
- `products/aep/aep-operator-contracts/build.gradle.kts` - DONE
- `products/aep/aep-runtime-core/build.gradle.kts` - DONE
- `products/aep/aep-registry/build.gradle.kts` - DONE
- `products/aep/aep-engine/build.gradle.kts` - DONE
- `products/aep/aep-event-cloud/build.gradle.kts` - DONE
- `products/aep/aep-api/build.gradle.kts` - DONE

### Data Cloud (`@ghatana/data-team`)

- `products/data-cloud/agent-registry/build.gradle.kts` - DONE

### YAPPC (`@ghatana/yappc-team`)

- `products/yappc/build.gradle.kts` - DONE
- `products/yappc/platform/build.gradle.kts` - DONE
- `products/yappc/core/yappc-services/build.gradle.kts` - DONE
- `products/yappc/core/yappc-infrastructure/build.gradle.kts` - DONE
- `products/yappc/core/yappc-api/build.gradle.kts` - DONE
- `products/yappc/core/yappc-agents/build.gradle.kts` - DONE
- `products/yappc/core/yappc-domain-impl/build.gradle.kts` - DONE
- `products/yappc/core/yappc-shared/build.gradle.kts` - DONE
- `products/yappc/core/refactorer/api/build.gradle.kts` - DONE
- `products/yappc/core/agents/runtime/build.gradle.kts` - DONE (validated via standalone alias path batch)
- `products/yappc/core/knowledge-graph/build.gradle.kts` - DONE (validated via standalone alias path batch)
- `products/yappc/core/services-lifecycle/build.gradle.kts` - DONE (validated via standalone alias path batch)
- `products/yappc/infrastructure/datacloud/build.gradle.kts` - DONE (validated via standalone alias path batch)

## Wave C - Remaining Products and Domain Plugins

### Finance (`@ghatana/platform-team` fallback, finance owners)

All migrated to `finance-domain-module` after parity plugin creation:

- `products/finance/domains/pricing/build.gradle.kts`
- `products/finance/domains/market-data/build.gradle.kts`
- `products/finance/domains/oms/build.gradle.kts`
- `products/finance/domains/reference-data/build.gradle.kts`
- `products/finance/domains/corporate-actions/build.gradle.kts`
- `products/finance/domains/reconciliation/build.gradle.kts`
- `products/finance/domains/risk/build.gradle.kts`
- `products/finance/domains/rules/build.gradle.kts`
- `products/finance/domains/sanctions/build.gradle.kts`
- `products/finance/domains/compliance/build.gradle.kts`
- `products/finance/domains/surveillance/build.gradle.kts`
- `products/finance/domains/post-trade/build.gradle.kts`
- `products/finance/domains/ems/build.gradle.kts`
- `products/finance/domains/regulatory-reporting/build.gradle.kts`
- `products/finance/domains/pms/build.gradle.kts`
- `products/finance/domains/market-data-core/build.gradle.kts`

### Audio-Video (`@ghatana/av-team`)

- `products/audio-video/modules/vision/vision-service/build.gradle.kts` - DONE
- `products/audio-video/modules/intelligence/multimodal-service/build.gradle.kts` - DONE
- `products/audio-video/modules/speech/stt-service/build.gradle.kts` - DONE
- `products/audio-video/modules/speech/tts-service/build.gradle.kts` - DONE
- `products/audio-video/libs/java/common/build.gradle.kts` - DONE

### Software-Org (`@ghatana/software-org-team`)

- `products/software-org/engine/modules/integration/plugins/build.gradle.kts` - DONE
- `products/software-org/engine/modules/integration/github/build.gradle.kts` - DONE
- `products/software-org/engine/modules/integration/ci/build.gradle.kts` - DONE
- `products/software-org/engine/modules/integration/jira/build.gradle.kts` - DONE

### Tutorputor (`@ghatana/tutorputor-team`)

- `products/tutorputor/libs/content-studio-agents/build.gradle.kts` - DONE
- `products/tutorputor/services/tutorputor-content-generation/build.gradle.kts` - DONE

### Virtual Org (`@ghatana/virtual-org-team`)

- `products/virtual-org/engine/service/build.gradle.kts` - DONE

## Rollback Anchors

- Wave A pilot rollback SHA: `TBD (set when commit is created)`
- Current branch state contains uncommitted migration docs + plugin-id swaps.

## Next Concrete Batch

1. Convert the remaining `84` fallback-dependent modules to explicit plugin IDs (`java-module` / `java-application` / `protobuf-module`) so root fallback can be removed safely.
2. Remove root fallback block from `build.gradle.kts` once fallback-dependent count reaches zero.
3. Retire residual `buildSrc` references in workflows, Dockerfiles, and product docs.
4. Finish broader standalone `products/yappc` validation beyond the now-green smoke path and migrated alias-task batch.

