# YAPPC Code Organization Review & Recommendations

**Date:** January 30, 2026  
**Reviewer:** AI Code Assistant  
**Scope:** `/products/yappc` structure analysis

---

## 📊 Current Structure Analysis

### Root-Level Folders (24 items)

```
products/yappc/
├── ai/                          # ⚠️ Unclear - AI what?
├── api/                         # ⚠️ Unclear - Separate from backend/api?
├── app-creator/                 # ✅ Clear - Frontend monorepo
├── backend/                     # ⚠️ Contains api/ and compliance/
│   ├── api/                     # ✅ Java backend API
│   └── compliance/              # ⚠️ What is this?
├── canvas-ai-service/           # ⚠️ Duplicate with ai/?
├── config/                      # ⚠️ Config for what?
├── core/                        # ✅ Core Java modules
│   ├── ai-requirements/         # Domain logic
│   ├── cli-tools/
│   ├── framework/
│   ├── refactorer-consolidated/
│   ├── scaffold/
│   ├── sdlc-agents/
│   ├── yappc-client-api/
│   └── yappc-plugin-spi/
├── docs/                        # ✅ Documentation
├── domain/                      # ⚠️ Separate from core?
├── helm/                        # ✅ K8s Helm charts
├── infrastructure/              # ⚠️ Infrastructure as code?
├── integration-docs/            # ⚠️ Duplicate with docs/?
├── k8s/                         # ⚠️ Duplicate with helm/?
├── knowledge-graph/             # ⚠️ Should be in core/?
├── libs/                        # ⚠️ Only has java/ and yappc-canvas/
│   ├── java/yappc-domain/
│   └── yappc-canvas/            # TypeScript canvas library
├── lifecycle/                   # ⚠️ What is this?
├── scripts/                     # ✅ Build/dev scripts
└── vscode-extension/            # ✅ VS Code extension

.archive/                        # ✅ Archived code
```

---

## 🚨 Critical Issues Identified

### 1. **Flat, Unclear Structure (High Severity)**

**Problem:** 24 root-level folders with unclear purposes and relationships.

**Impact:**
- Difficult to navigate for new developers
- No clear separation between frontend/backend/infrastructure
- Hard to understand what each folder contains

**Examples:**
- `ai/` vs `canvas-ai-service/` - Which is which?
- `api/` vs `backend/api/` - Duplicate API folders?
- `docs/` vs `integration-docs/` - Two doc folders?
- `helm/` vs `k8s/` - Both for Kubernetes?
- `domain/` vs `libs/java/yappc-domain/` vs `core/ai-requirements/domain/` - Three domain folders?

---

### 2. **Inconsistent Module Organization (Medium Severity)**

**Problem:** Java modules in `core/`, shared libs in `libs/`, but `knowledge-graph/` at root level.

**Impact:**
- No consistent pattern for where modules should go
- Violates separation of concerns
- Makes Gradle build structure confusing

**Current Java Module Locations:**
```
core/
├── ai-requirements/    ← Domain module
├── framework/          ← Infrastructure module
├── refactorer-consolidated/ ← Tool module
└── scaffold/           ← Tool module

knowledge-graph/        ← ❌ Domain module at root (should be in core/)

libs/java/
└── yappc-domain/       ← ❌ Shared domain (why not core/domain?)

backend/api/            ← ❌ Application module separate from core
```

---

### 3. **Frontend Monorepo Confusion (Low-Medium Severity)**

**Problem:** `app-creator/` has internal `apps/`, `libs/`, `packages/` structure, but YAPPC root also has `libs/`.

**Current:**
```
products/yappc/
├── app-creator/           # TypeScript monorepo
│   ├── apps/              # Web, desktop, mobile
│   ├── libs/              # @ghatana/* packages
│   └── packages/          # Internal packages
├── libs/                  # ❌ YAPPC libs folder
│   ├── java/              # ❌ Java libs
│   └── yappc-canvas/      # ❌ TypeScript canvas lib
└── vscode-extension/      # ❌ TypeScript VS Code extension
```

**Issues:**
- `libs/yappc-canvas/` should be in `app-creator/libs/`
- `vscode-extension/` should be in `app-creator/apps/` or separate `tools/`
- Two different `libs/` folders with different purposes

---

### 4. **Deployment/Infrastructure Scattered (Medium Severity)**

**Problem:** Infrastructure code spread across multiple folders.

**Current:**
```
├── config/            # ❌ What config?
├── helm/              # K8s Helm charts
├── k8s/               # K8s raw manifests
├── infrastructure/    # ❌ Terraform? Ansible?
└── docker-compose.yml # ❌ At root
```

---

### 5. **Documentation Fragmentation (Low Severity)**

**Problem:** Documentation split across folders.

**Current:**
```
├── docs/              # General docs
├── integration-docs/  # Integration docs
└── core/*/docs/       # Module-specific docs
```

---

## ✅ Recommended Structure (Clean & Clear)

### Proposed Organization

```
products/yappc/
│
├── README.md                      # Quick start guide
├── ARCHITECTURE.md                # System architecture
├── build.gradle.kts               # Root Gradle config
├── settings.gradle.kts            # Gradle modules
├── docker-compose.yml             # Local development
├── Makefile                       # Build shortcuts
│
├── core/                          # ✅ Java Core Platform
│   ├── domain/                    # Domain models & contracts
│   │   ├── build.gradle.kts
│   │   └── src/main/java/
│   │
│   ├── ai-requirements/           # AI requirements module
│   │   ├── ai/
│   │   ├── api/
│   │   ├── domain/
│   │   └── application/
│   │
│   ├── framework/                 # Core framework
│   │   ├── framework-api/
│   │   ├── framework-core/
│   │   └── integration-test/
│   │
│   ├── knowledge-graph/           # ← MOVE from root
│   │   └── ...
│   │
│   ├── refactorer/                # Code refactoring
│   │   ├── refactorer-core/
│   │   ├── refactorer-engine/
│   │   ├── refactorer-languages/
│   │   ├── refactorer-api/
│   │   ├── refactorer-adapters/
│   │   └── refactorer-infra/
│   │
│   ├── scaffold/                  # Project scaffolding
│   │   ├── cli/
│   │   ├── core/
│   │   ├── adapters/
│   │   ├── packs/
│   │   └── schemas/
│   │
│   ├── sdlc-agents/               # SDLC workflow agents
│   ├── yappc-client-api/          # Unified client library
│   └── yappc-plugin-spi/          # Plugin system
│
├── backend/                       # ✅ Backend Services
│   ├── api/                       # Main HTTP API (Java)
│   │   └── src/main/java/
│   │
│   └── services/                  # ← RENAME compliance/ to services/
│       └── compliance/            # Compliance service
│
├── frontend/                      # ✅ Frontend Applications (RENAME app-creator)
│   ├── README.md
│   ├── package.json               # pnpm workspace root
│   ├── pnpm-workspace.yaml
│   │
│   ├── apps/                      # Frontend applications
│   │   ├── web/                   # Web app (React)
│   │   ├── desktop/               # Desktop app (Tauri)
│   │   └── mobile/                # Mobile app (Capacitor)
│   │
│   ├── libs/                      # Shared libraries
│   │   ├── ui/                    # UI components
│   │   ├── canvas/                # ← MOVE libs/yappc-canvas here
│   │   ├── api/                   # API client
│   │   ├── state/                 # State management
│   │   ├── testing/               # Testing utilities
│   │   └── ...
│   │
│   └── packages/                  # Internal packages
│       └── ...
│
├── tools/                         # ✅ Developer Tools (NEW)
│   ├── vscode-extension/          # ← MOVE from root
│   ├── cli/                       # CLI tools
│   └── scripts/                   # ← MOVE scripts/ here
│
├── infrastructure/                # ✅ Infrastructure as Code
│   ├── docker/                    # Docker configs
│   │   ├── Dockerfile
│   │   └── docker-compose.yml     # ← MOVE from root
│   │
│   ├── kubernetes/                # K8s manifests
│   │   ├── base/                  # ← MERGE k8s/ here
│   │   └── overlays/
│   │
│   ├── helm/                      # Helm charts
│   │   └── yappc/
│   │
│   └── terraform/                 # Terraform configs (if any)
│
├── docs/                          # ✅ Documentation
│   ├── architecture/              # Architecture docs
│   ├── api/                       # API documentation
│   ├── integration/               # ← MERGE integration-docs/
│   ├── development/               # Developer guides
│   └── deployment/                # Deployment guides
│
└── .archive/                      # ✅ Archived code (keep as is)
    └── ...
```

---

## 📋 Detailed Recommendations

### Priority 1: Critical Structure Changes (Do First)

#### 1.1 Consolidate Java Modules Under `core/`

**Actions:**
```bash
# Move knowledge-graph to core
mv knowledge-graph/ core/knowledge-graph/

# Update settings.gradle.kts
include 'core:knowledge-graph'
```

**Impact:** ✅ All Java modules now consistently in `core/`

---

#### 1.2 Reorganize Frontend Structure

**Actions:**
```bash
# Rename app-creator to frontend (more intuitive)
mv app-creator/ frontend/

# Move canvas library into frontend/libs/
mv libs/yappc-canvas/ frontend/libs/canvas/

# Update pnpm-workspace.yaml in frontend/
```

**Impact:** ✅ Clear separation: `core/` = Java, `frontend/` = TypeScript

---

#### 1.3 Create `tools/` Directory

**Actions:**
```bash
# Create tools directory
mkdir -p tools/

# Move VS Code extension
mv vscode-extension/ tools/vscode-extension/

# Move scripts
mv scripts/ tools/scripts/

# Optional: Move cli-tools from core/ to tools/ (if it's not core platform code)
# mv core/cli-tools/ tools/cli/
```

**Impact:** ✅ Developer tools separated from core platform

---

### Priority 2: Infrastructure Consolidation

#### 2.1 Consolidate Deployment Configs

**Actions:**
```bash
# Create infrastructure directory structure
mkdir -p infrastructure/docker
mkdir -p infrastructure/kubernetes/base
mkdir -p infrastructure/kubernetes/overlays

# Move files
mv docker-compose.yml infrastructure/docker/
mv Dockerfile infrastructure/docker/
mv k8s/* infrastructure/kubernetes/base/
rmdir k8s/
mv helm/ infrastructure/helm/

# Update references in Makefile, README, etc.
```

**Impact:** ✅ All infrastructure code in one place

---

#### 2.2 Remove Unclear Root Folders

**Actions:**
```bash
# Investigate and move/delete these folders
# ai/ - What is this? Merge with canvas-ai-service?
# api/ - Empty or merge with backend/api/?
# config/ - Move to infrastructure/ or delete
# domain/ - Merge with core/domain/ or libs/java/yappc-domain/
# lifecycle/ - What is this? Archive or move to core/
```

**Impact:** ✅ Cleaner root directory

---

### Priority 3: Documentation Organization

#### 3.1 Consolidate Documentation

**Actions:**
```bash
# Merge integration-docs into docs/integration/
mv integration-docs/* docs/integration/
rmdir integration-docs/

# Organize docs by purpose
mkdir -p docs/architecture
mkdir -p docs/api
mkdir -p docs/development
mkdir -p docs/deployment

# Move existing docs to appropriate folders
```

**Impact:** ✅ Single docs/ directory with clear structure

---

## 📏 Organizational Principles (Going Forward)

### 1. **Clear Layer Separation**

```
core/          ← Platform foundation (Java)
backend/       ← Backend services (Java/Node)
frontend/      ← User interfaces (TypeScript/React)
tools/         ← Developer tools
infrastructure/← Deployment configs
docs/          ← Documentation
```

### 2. **Technology Grouping**

- **Java modules** → `core/` and `backend/`
- **TypeScript modules** → `frontend/` and `tools/`
- **DevOps configs** → `infrastructure/`

### 3. **Naming Conventions**

- **Folders:** kebab-case (e.g., `ai-requirements`, `knowledge-graph`)
- **Gradle modules:** kebab-case with colons (e.g., `:core:knowledge-graph`)
- **TypeScript packages:** scoped with @ghatana (e.g., `@ghatana/ui`)

### 4. **Module Placement Rules**

| Module Type | Location | Example |
|-------------|----------|---------|
| Core domain logic | `core/` | `core/knowledge-graph/` |
| Backend services | `backend/` | `backend/api/` |
| Frontend apps | `frontend/apps/` | `frontend/apps/web/` |
| Shared UI libs | `frontend/libs/` | `frontend/libs/ui/` |
| Dev tools | `tools/` | `tools/vscode-extension/` |
| Infrastructure | `infrastructure/` | `infrastructure/helm/` |

---

## 🎯 Benefits of Proposed Structure

### For Developers

✅ **Intuitive navigation** - Clear top-level folders  
✅ **Faster onboarding** - New devs understand structure quickly  
✅ **Less confusion** - No duplicate folders or unclear names  
✅ **Easier searching** - Predictable file locations

### For Build System

✅ **Consistent Gradle structure** - All Java in `core/` and `backend/`  
✅ **Cleaner pnpm workspace** - All TypeScript in `frontend/` and `tools/`  
✅ **Better IDE support** - IDEs can index structure correctly

### For Operations

✅ **All deployment configs in one place** - `infrastructure/`  
✅ **Clear Docker/K8s separation** - Easy to find what you need  
✅ **Easier CI/CD** - Predictable paths for automation

---

## 🔄 Migration Plan

### Phase 1: Preparation (Day 1)
1. ✅ Create this review document
2. ✅ Get team agreement on new structure
3. ✅ Document current dependencies (prevent breakage)

### Phase 2: Safe Moves (Day 2-3)
1. Move `knowledge-graph/` to `core/knowledge-graph/`
2. Rename `app-creator/` to `frontend/`
3. Move `libs/yappc-canvas/` to `frontend/libs/canvas/`
4. Update all import paths and build configs

### Phase 3: Consolidation (Day 4-5)
1. Create `infrastructure/` directory
2. Move Docker, K8s, Helm configs
3. Create `tools/` directory
4. Move VS Code extension and scripts
5. Update all references in CI/CD

### Phase 4: Cleanup (Day 6)
1. Consolidate documentation
2. Remove/merge unclear folders (`ai/`, `api/`, `config/`, etc.)
3. Update README with new structure
4. Verify all builds work

### Phase 5: Verification (Day 7)
1. Run full build: `./gradlew clean build`
2. Run frontend build: `cd frontend && pnpm build`
3. Test Docker compose: `docker-compose up`
4. Update team documentation

---

## 📊 Metrics

### Current State
- **Root-level folders:** 24
- **Unclear purposes:** ~8 folders
- **Duplicate concepts:** 4 pairs
- **Cognitive load:** HIGH

### Target State (After Refactor)
- **Root-level folders:** 8
- **Unclear purposes:** 0
- **Duplicate concepts:** 0
- **Cognitive load:** LOW

### Estimated Effort
- **Planning:** 2 hours
- **Execution:** 2-3 days
- **Verification:** 1 day
- **Total:** ~3-4 days

---

## 🚀 Quick Wins (Can Do Immediately)

### 1. Rename `app-creator/` to `frontend/`
```bash
git mv app-creator frontend
# Update package.json name
# Update all references in docs
```
**Time:** 30 minutes  
**Impact:** Immediate clarity improvement

---

### 2. Move `knowledge-graph/` to `core/`
```bash
git mv knowledge-graph core/knowledge-graph
# Update settings.gradle.kts
```
**Time:** 15 minutes  
**Impact:** Consistent Java module structure

---

### 3. Create `infrastructure/` directory
```bash
mkdir -p infrastructure/{docker,kubernetes,helm}
git mv docker-compose.yml infrastructure/docker/
git mv helm infrastructure/helm/yappc
git mv k8s infrastructure/kubernetes/base
```
**Time:** 30 minutes  
**Impact:** All deployment configs centralized

---

## ❓ Questions to Answer

Before proceeding with the restructure, clarify:

1. **What is `ai/` folder?** - Merge with `canvas-ai-service/`?
2. **What is `api/` at root?** - Empty? Duplicate of `backend/api/`?
3. **What is `domain/` at root?** - Merge with `core/domain/` or `libs/java/yappc-domain/`?
4. **What is `config/` folder?** - Application configs? Build configs?
5. **What is `lifecycle/` folder?** - Part of core? Archive it?
6. **What is `backend/compliance/`?** - Separate service? Part of API?

---

## 📚 References

- [Gradle Multi-Project Build Best Practices](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [pnpm Workspace Guide](https://pnpm.io/workspaces)
- [Monorepo Organization Patterns](https://monorepo.tools)
- [Clean Architecture Principles](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

## ✅ Conclusion

The current YAPPC structure has **grown organically** and needs **strategic reorganization** for:
- Better developer experience
- Clearer boundaries
- Easier maintenance
- Faster onboarding

**Recommended approach:** Implement the Priority 1 changes first (consolidate Java modules, reorganize frontend), then tackle infrastructure and cleanup.

**Estimated total effort:** 3-4 developer-days  
**Expected benefit:** 50%+ reduction in navigation time and cognitive load

---

**Next Steps:**
1. Review this document with the team
2. Get consensus on proposed structure
3. Create implementation branch
4. Execute migration in phases
5. Update all documentation
