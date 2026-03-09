# TUTORPUTOR_MODULE_INVENTORY

Last audit update: 2026-03-08 (America/Los_Angeles)

## Scope scanned
- `products/tutorputor/**`
- Shared TS workspace packages consumed by Tutorputor
- CI/workflow files affecting Tutorputor verification

## Node/TS product modules
| Module | Path | Role | Primary automation scripts | Current gate status |
|---|---|---|---|---|
| @ghatana/tutorputor-api-gateway | apps/api-gateway | API entrypoint delegating to platform | build, test | PASS (targeted `build` + `test`) |
| @ghatana/tutorputor-admin | apps/tutorputor-admin | Admin frontend | lint, typecheck, build, test | FAIL |
| @ghatana/tutorputor-mobile | apps/tutorputor-mobile | Mobile app | lint, type-check, test | FAIL |
| @ghatana/tutorputor-web | apps/tutorputor-web | Learner/author web app | lint, type-check, build, test, test:e2e | FAIL |
| @ghatana/tutorputor-contracts | contracts | Shared TS contracts | build, test | PASS |
| @ghatana/tutorputor-assessments | libs/assessments | Assessment logic | typecheck, test | FAIL |
| @ghatana/tutorputor-learning-engine | libs/learning-engine | Learning runtime logic | lint, build, test | FAIL |
| @ghatana/tutorputor-learning-kernel | libs/learning-kernel | Kernel abstractions | lint, build, test | FAIL |
| @ghatana/tutorputor-learning-path | libs/learning-path | Pathways logic | typecheck, test | FAIL |
| @ghatana/tutorputor-physics-simulation | libs/physics-simulation | Physics simulation lib | lint, typecheck, build, test | FAIL |
| @ghatana/tutorputor-sim-renderer | libs/sim-renderer | Renderer library | type-check, build | FAIL |
| @ghatana/tutorputor-ai-proxy | services/tutorputor-ai-proxy | AI proxy API | type-check, build, test | FAIL |
| @ghatana/tutorputor-db | services/tutorputor-db | Prisma schema/migrations | type-check, prisma:migrate:deploy, test | PARTIAL (type-check PASS, migrate FAIL in env) |
| @ghatana/tutorputor-domain-loader | services/tutorputor-domain-loader | Domain/template loading | typecheck, build, test | FAIL |
| @ghatana/tutorputor-kernel-registry | services/tutorputor-kernel-registry | Kernel registry service | lint, build, test | FAIL |
| @ghatana/tutorputor-lti-service | services/tutorputor-lti | LTI integration service | build, test | FAIL |
| @ghatana/tutorputor-payments | services/tutorputor-payments | Billing/payments | lint, build, test | FAIL |
| @ghatana/tutorputor-platform | services/tutorputor-platform | Consolidated backend platform | lint, type-check, build, test | FAIL (broad), with targeted PASS on new worker/studio tests |
| @ghatana/tutorputor-sim-author | services/tutorputor-sim-author | Simulation authoring backend | build, test | FAIL |
| @ghatana/tutorputor-sim-nl | services/tutorputor-sim-nl | NL simulation service | lint, build, test | FAIL |
| @ghatana/tutorputor-sim-runtime | services/tutorputor-sim-runtime | Runtime engine | build, test | FAIL |
| @ghatana/tutorputor-sim-sdk | services/tutorputor-sim-sdk | SDK | lint, build, test | FAIL |
| @ghatana/tutorputor-simulation | services/tutorputor-simulation | Simulation backend | build, test | FAIL |
| @ghatana/tutorputor-vr-labs | services/tutorputor-vr | VR service | lint, build, test | FAIL |

## JVM/Gradle modules in Tutorputor product
| Module | Path | Role | Verification status |
|---|---|---|---|
| content-studio-agents | libs/content-studio-agents | Content generation agents and queue | UNVERIFIED (wrapper download blocked: `services.gradle.org` DNS) |
| tutorputor-ai-agents | services/tutorputor-ai-agents | AI agents gRPC services | UNVERIFIED (same Gradle network block) |
| tutorputor-content-studio-grpc | services/tutorputor-content-studio-grpc | gRPC module | UNVERIFIED |
| content-explorer | apps/content-explorer | JVM app module | UNVERIFIED |

## Shared TS workspace packages used by Tutorputor
| Shared package | Consumed by |
|---|---|
| @ghatana/api | services/tutorputor-sim-author, services/tutorputor-sim-runtime |
| @ghatana/charts | apps/tutorputor-admin, apps/tutorputor-web |
| @ghatana/realtime | apps/tutorputor-web |
| @ghatana/theme | apps/tutorputor-web, libs/sim-renderer |
| @ghatana/tokens | apps/tutorputor-web, libs/sim-renderer |
| @ghatana/ui | apps/tutorputor-admin, apps/tutorputor-web, services/tutorputor-platform |
| @ghatana/utils | libs/sim-renderer |

## CI/config surfaces affecting Tutorputor
- `.github/workflows/tutorputor-ci.yml`
- `.github/workflows/e2e-tests.yml`
- `.github/workflows/visual-regression.yml`
- `.github/workflows/performance-budgets.yml`
- `products/tutorputor/pnpm-workspace.yaml`
- `products/tutorputor/settings.gradle.kts`
- `products/tutorputor/scripts/check-no-legacy-references.sh`
