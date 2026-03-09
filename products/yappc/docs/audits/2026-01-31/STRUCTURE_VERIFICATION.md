# YAPPC Structure Verification & Cleanup Plan

## Current State vs. Planned State

### ✅ COMPLETED (Matches Plan)

1. **Frontend Renamed**
   - ✅ `app-creator/` → `frontend/`
   - Location: `products/yappc/frontend/`
   - Contains: apps/, libs/, packages/

2. **Tools Directory Created**
   - ✅ `tools/` directory exists
   - Contains: vscode-extension/, scripts/
   - ✅ Already reorganized

3. **Infrastructure Directory Created**
   - ✅ `infrastructure/` directory exists
   - Contains: docker/, kubernetes/, helm/

4. **Core Module Structure**
   - ✅ `core/` contains Java modules
   - Subdirs: knowledge-graph/, framework/, scaffold/, sdlc-agents/, etc.
   - ✅ knowledge-graph/ moved to core/

5. **Backend Structure**
   - ✅ `backend/` contains Java services
   - Subdirs: api/, compliance/ (services/)

---

## ❌ ISSUES: Unclear Folders Still At Root Level

| Folder | Files | Type | Purpose | Action |
|--------|-------|------|---------|--------|
| `ai/` | 2 | Gradle module | AI module (build.gradle.kts + src/) | **UNCLEAR - needs analysis** |
| `api/` | 0 | Empty (src/ only) | Should be empty | **DELETE or consolidate** |
| `canvas-ai-service/` | 3 | Gradle module | Canvas AI service | **Unclear - is this ai/?** |
| `config/` | 9 | Config files | Agent/workflow configs | **Should be in docs/config/ or backend/** |
| `domain/` | 3 | Gradle module | Domain models | **Should consolidate with core/domain** |
| `lifecycle/` | 11 | Gradle module | Lifecycle management | **Should be in core/lifecycle** |
| `integration-docs/` | 1 | Markdown | Integration documentation | **Should merge into docs/integration/** |

---

## 📋 Detailed Analysis & Recommendations

### 1. **`ai/` folder**
**Contains:**
- `build.gradle.kts` - Gradle module definition
- `src/` - Java source code
- `README.md` - Documentation

**Question:** Is this a core AI platform module or a service?
**Current:** It's a Gradle module at root (not part of core/)
**Decision:** Should move to `core/ai/` if it's platform code

---

### 2. **`api/` folder**
**Contains:**
- `src/` (empty structure)

**Status:** Appears to be a placeholder or leftover
**Decision:** DELETE (not referenced in build config)

---

### 3. **`canvas-ai-service/` folder**
**Contains:**
- `build.gradle.kts` - Gradle module
- `src/` - Java source code  
- `test-grpc-client.cjs` - Test file
- `README.md` - Documentation

**Question:** Duplicate of `ai/` or separate service?
**Current:** Gradle module at root
**Decision:** Clarify purpose or consolidate with `ai/`

---

### 4. **`config/` folder**
**Contains:**
- `domains.yaml` - Domain configuration
- `personas.yaml` - Persona definitions
- `agents/`, `lifecycle/`, `schemas/`, `tasks/`, `workflows/` - Config subdirs
- `README.md` - Documentation

**Purpose:** Configuration for agents, workflows, domains
**Current Location:** Root level
**Decision:** Move to appropriate location based on ownership:
- If backend-owned: `backend/config/`
- If shared: `docs/config/`
- If core platform: `core/config/`

---

### 5. **`domain/` folder**
**Contains:**
- `build.gradle.kts` - Gradle module
- `src/` - Java source code
- `service/`, `task/` - Subdirectories

**Purpose:** Domain models and entities
**Current:** Gradle module at root (separate from core/)
**Decision:** Move to `core/domain/`

---

### 6. **`lifecycle/` folder**
**Contains:**
- `build.gradle.kts` - Gradle module
- `src/` - Java source code
- Multiple `.md` documentation files
- `Dockerfile` - Docker configuration

**Purpose:** Lifecycle management module
**Current:** Gradle module at root (separate from core/)
**Decision:** Move to `core/lifecycle/`

---

### 7. **`integration-docs/` folder**
**Contains:**
- `README.md` - Integration documentation

**Purpose:** Integration guides and documentation
**Current:** Separate folder from main docs/
**Decision:** Move to `docs/integration/` (consolidate with docs/)

---

## 🔧 Cleanup Actions Recommended

### Phase 1: Low-Risk Changes (Safe to Do Now)

**1. Delete empty `api/` folder**
```bash
rm -rf api/
```
Impact: None (not referenced)

**2. Consolidate docs**
```bash
# Move integration docs into docs folder
mkdir -p docs/integration
mv integration-docs/README.md docs/integration/
rm -rf integration-docs/
```
Impact: Low (improves documentation organization)

---

### Phase 2: Java Module Consolidation (Requires Investigation)

**Before proceeding, clarify:**
1. What does `ai/` module do? Is it core platform code?
2. What does `canvas-ai-service/` do? Is it separate from `ai/`?
3. Are `domain/`, `lifecycle/` application code or platform code?

**If they are platform/core modules:**
```bash
# Consolidate under core/
mv ai/ core/ai/
mv canvas-ai-service/ core/canvas-ai/
mv domain/ core/domain/
mv lifecycle/ core/lifecycle/

# Update settings.gradle.kts:
# include 'core:ai'
# include 'core:canvas-ai'
# include 'core:domain'
# include 'core:lifecycle'
```

**If they are configuration/service modules:**
```bash
# Move config under backend or docs
mv config/ backend/config/  # or docs/config/
```

---

### Phase 3: Verify Structure

**After cleanup, verify:**
```bash
# Show remaining root-level folders
ls -d */ | grep -v "^\."

# Should see:
# backend/
# core/
# frontend/
# infrastructure/
# tools/
# docs/
# .archive/
```

---

## Final Target Structure

```
products/yappc/
│
├── backend/                   # Backend services
│   ├── api/                   # Main Java HTTP API
│   └── services/              # Services (compliance, etc.)
│
├── core/                      # Core platform modules
│   ├── ai/                    # AI platform code
│   ├── canvas-ai/             # Canvas AI service
│   ├── domain/                # Domain models
│   ├── framework/             # Core framework
│   ├── knowledge-graph/       # Knowledge graph
│   ├── lifecycle/             # Lifecycle management
│   ├── refactorer/            # Code refactoring
│   ├── scaffold/              # Project scaffolding
│   ├── sdlc-agents/           # SDLC agents
│   ├── yappc-client-api/      # Client API
│   └── yappc-plugin-spi/      # Plugin system
│
├── frontend/                  # Frontend applications
│   ├── apps/
│   ├── libs/
│   └── packages/
│
├── infrastructure/            # Infrastructure as code
│   ├── docker/
│   ├── kubernetes/
│   └── helm/
│
├── tools/                     # Developer tools
│   ├── scripts/
│   └── vscode-extension/
│
├── docs/                      # Documentation
│   ├── api/
│   ├── integration/
│   ├── deployment/
│   └── ...
│
├── libs/                      # (keep for compatibility)
│   └── java/
│
└── .archive/                  # Archived code
```

---

## Status: ⚠️ ACTIONABLE - NEEDS MIGRATION

**Finding:** All unclear modules ARE in Gradle build:
- ✅ `ai/` - Included as `:ai` in settings.gradle.kts
- ✅ `lifecycle/` - Included as `:lifecycle` in settings.gradle.kts
- ✅ `domain/` - Included as `:core:ai-requirements:domain`
- ✅ `canvas-ai-service/` - Appears to be a Gradle module (buildable)
- ❌ `api/` - Empty, not in build config

---

## Recommended Action Plan

### Phase 1: IMMEDIATE (Safe, No Functional Impact)

**1. Delete empty `api/` folder**
```bash
rm -rf api/
```
Status: SAFE - Not in build config, not referenced

**2. Consolidate integration docs**
```bash
mkdir -p docs/integration
mv integration-docs/README.md docs/integration/
rm -rf integration-docs/
```
Status: SAFE - Just documentation consolidation

---

### Phase 2: CONSOLIDATION (Requires Updates to Gradle)

These modules ARE in the build and need careful migration:

**Option A: Move all to `core/` (RECOMMENDED)**
```bash
# Move ai module
mv ai/ core/ai/

# Move lifecycle module
mv lifecycle/ core/lifecycle/

# Move canvas-ai-service
mv canvas-ai-service/ core/canvas-ai/

# Keep domain in core/ai-requirements/domain (already correct)
# Keep config for later decision
```

**Then update settings.gradle.kts:**
```kotlin
// OLD (root level)
include 'ai'
include 'lifecycle'
project(':ai').projectDir = file('ai')
project(':lifecycle').projectDir = file('lifecycle')

// NEW (under core/)
include 'core:ai'
include 'core:lifecycle'
include 'core:canvas-ai'
project(':core:ai').projectDir = file('core/ai')
project(':core:lifecycle').projectDir = file('core/lifecycle')
project(':core:canvas-ai').projectDir = file('core/canvas-ai')
```

**Option B: Keep at root (Current State)**
- Requires no changes
- But violates the planned structure (modules should be under `core/`)

---

### Phase 3: CONFIG FOLDER (Low Priority)

**Current:** `config/` contains YAML configs for domains, personas, workflows, etc.
**Decision Needed:**
- Move to `backend/config/` (if runtime config)
- Move to `docs/config/` (if reference documentation)
- Keep at root (if global configuration)

---

## Final Target After Migration

```
products/yappc/
├── backend/
│   ├── api/
│   └── services/
│
├── core/                    # ← All Java modules
│   ├── ai/                  # ← MOVE from root
│   ├── ai-requirements/
│   ├── canvas-ai/           # ← MOVE from root (canvas-ai-service)
│   ├── domain/              # ← Already here as core:ai-requirements:domain
│   ├── framework/
│   ├── knowledge-graph/
│   ├── lifecycle/           # ← MOVE from root
│   ├── refactorer-consolidated/
│   ├── scaffold/
│   ├── sdlc-agents/
│   ├── yappc-client-api/
│   └── yappc-plugin-spi/
│
├── frontend/
│   ├── apps/
│   ├── libs/
│   └── packages/
│
├── infrastructure/
│   ├── docker/
│   ├── kubernetes/
│   └── helm/
│
├── tools/
│   ├── scripts/
│   └── vscode-extension/
│
├── docs/
│   ├── api/
│   ├── integration/         # ← MOVE from integration-docs
│   ├── deployment/
│   └── config/              # ← MAYBE (if needed)
│
├── config/                  # ← Keep or move (decision needed)
│   ├── domains.yaml
│   ├── personas.yaml
│   └── ...
│
├── libs/
│   └── java/
│
└── .archive/
```

---

## Recommendation

**PROCEED WITH PHASE 1 + OPTION A:**

1. Delete empty `api/` folder (no risk)
2. Move `integration-docs/` → `docs/integration/` (safe documentation consolidation)
3. Move `ai/`, `lifecycle/`, `canvas-ai-service/` to `core/` (aligns with plan, requires Gradle updates)
4. Keep `config/` for now (clarify use case separately)

**Estimated Effort:** 15 minutes (including Gradle updates and verification)
