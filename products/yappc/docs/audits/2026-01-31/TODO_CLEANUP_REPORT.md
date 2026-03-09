# YAPPC TODO/FIXME Cleanup Report

**Date:** 2026-01-28
**Status:** Analysis Complete
**Total TODOs/FIXMEs:** 249 (excluding archived code)

---

## Summary

This report catalogs all TODO and FIXME comments in the YAPPC codebase to guide cleanup efforts.

### Categories

| Category | Count | Priority |
|----------|-------|----------|
| AI/LLM Integration | ~15 | HIGH |
| EventCloud Integration | ~8 | HIGH |
| Refactorer Implementation | ~25 | MEDIUM |
| Testing Gaps | ~30 | MEDIUM |
| Documentation | ~20 | LOW |
| Placeholder Implementations | ~40 | HIGH |
| Pattern/Policy Learning | ~12 | MEDIUM |
| CI/CD & Tooling | ~15 | LOW |
| UI/UX Components | ~35 | MEDIUM |
| API/GraphQL | ~10 | MEDIUM |
| Misc/Uncategorized | ~49 | LOW |

---

## Critical TODOs (Immediate Action Required)

### 1. AI-Requirements API

**File:** `core/ai-requirements/api/src/main/java/com/ghatana/requirements/api/graphql/resolver/MutationResolver.java`
```java
// import graphql.kickstart.tools.GraphQLMutationResolver; // TODO: Add GraphQL library
```
**Action:** Add GraphQL dependency or remove commented code

**File:** `core/ai-requirements/api/src/main/java/com/ghatana/requirements/api/rest/WorkspaceController.java`
```java
// TODO: Implement deleteWorkspace in WorkspaceService
```
**Action:** Implement deleteWorkspace method

**File:** `core/ai-requirements/api/src/main/java/com/ghatana/requirements/api/rest/ProjectController.java`
```java
// TODO: Implement full project update with name/description
```
**Action:** Complete project update implementation

### 2. SDLC Agents

**File:** `core/sdlc-agents/src/main/java/com/ghatana/yappc/sdlc/agent/specialists/IntakeSpecialistAgent.java`
```java
// TODO: Use NLP/LLM to extract entities, constraints, quality attributes
```
**Action:** Integrate with LLM service for requirement extraction

**File:** `core/sdlc-agents/src/main/java/com/ghatana/yappc/sdlc/implementation/PublishStep.java`
```java
String version = "1.0.0"; // TODO: Implement semantic versioning
```
**Action:** Implement semantic versioning logic

**File:** `core/sdlc-agents/src/main/java/com/ghatana/yappc/sdlc/implementation/ScaffoldStep.java`
```java
// TODO: Implement business logic
```
**Action:** Complete scaffold step implementation

### 3. EventCloud Integration

**File:** `core/sdlc-agents/src/main/java/com/ghatana/yappc/sdlc/agent/YAPPCAgentBase.java`
```java
// TODO: Integrate with EventCloudHelper
```
**Action:** Connect to EventCloud for event-driven operations

**File:** `core/sdlc-agents/src/main/java/com/ghatana/yappc/sdlc/agent/coordinator/PlatformDeliveryCoordinator.java`
```java
// TODO: Integrate with EventCloudHelper
// TODO: Store pattern for future estimation
// TODO: Store successful execution pattern as policy
// TODO: Extract failure patterns for prevention
```
**Action:** Implement EventCloud integration for pattern learning

### 4. Refactorer Implementation

**File:** `core/refactorer-consolidated/refactorer-api/src/main/java/com/ghatana/refactorer/server/grpc/PolyfixGrpcService.java`
```java
// TODO: Integrate with actual orchestrator service
// TODO: Integrate with actual progress streaming
```
**Action:** Connect to real orchestrator service

**File:** `core/refactorer-consolidated/refactorer-languages/src/main/java/com/ghatana/refactorer/languages/JavaLanguageService.java`
```java
// TODO: Add more sophisticated Java code analysis using JavaParser or similar
// TODO: Implement Java-specific fix planning based on diagnostic codes
```
**Action:** Enhance Java language service with JavaParser

**File:** `core/refactorer-consolidated/refactorer-languages/src/main/java/com/ghatana/refactorer/languages/PythonLanguageService.java`
```java
// TODO: Add more sophisticated Python code analysis using a Python parser
// TODO: Implement Python-specific fix planning based on diagnostic codes
```
**Action:** Enhance Python language service

**File:** `core/refactorer-consolidated/refactorer-languages/src/main/java/com/ghatana/refactorer/languages/tsjs/TypeScriptJavaScriptLanguageService.java`
```java
// TODO: Implement TypeScript/JavaScript-specific fix planning based on diagnostic codes
```
**Action:** Implement TS/JS fix planning

### 5. Framework Core

**File:** `core/framework/framework-core/src/main/java/com/ghatana/yappc/framework/core/plugin/DefaultPluginContextFactory.java`
```java
// TODO: Implement project context management
// TODO: Implement service registry integration
```
**Action:** Complete plugin context factory implementation

### 6. Scaffold CLI

**File:** `core/scaffold/cli/src/main/java/com/ghatana/yappc/cli/InitCommand.java`
```java
// TODO: Week 1, Day 2 - Implement actual workspace creation
```
**Action:** Implement workspace creation logic

**File:** `core/scaffold/cli/src/main/java/com/ghatana/yappc/cli/GraphCommand.java`
```java
// TODO: Write to file
```
**Action:** Implement file output for graph command

### 7. Architecture Phase

**File:** `core/sdlc-agents/src/main/java/com/ghatana/yappc/sdlc/agent/leads/ArchitecturePhaseLeadAgent.java`
```java
// TODO: Store pattern for similar requirements
// TODO: Integrate with artifact storage
```
**Action:** Connect to artifact storage for pattern management

---

## TODO Cleanup Strategy

### Phase 1: Critical (This Week)
1. Remove or implement placeholder TODOs in AI-Requirements API
2. Complete EventCloud integration for SDLC agents
3. Implement missing CLI commands

### Phase 2: Important (Next 2 Weeks)
1. Enhance Refactorer language services with proper parsers
2. Complete Pattern/Policy learning implementation
3. Add GraphQL library dependency

### Phase 3: Nice-to-Have (Ongoing)
1. Add issue references to remaining TODOs
2. Convert investigation TODOs to ADRs
3. Remove outdated TODOs

---

## Recommendations

1. **Enforce TODO Standards:** Add CI check requiring issue references for new TODOs
2. **TODO Budget:** Limit new TODOs to 5 per sprint
3. **Quarterly Review:** Schedule TODO cleanup sprints
4. **Automated Tracking:** Use tools like `todo-cli` or GitHub issues integration

---

## Commands for TODO Analysis

```bash
# Count TODOs by file
grep -r "TODO\|FIXME" --include="*.java" --include="*.ts" --include="*.tsx" . | cut -d: -f1 | sort | uniq -c | sort -rn

# Find TODOs without issue references
grep -rE "TODO|FIXME" --include="*.java" . | grep -v "#[0-9]"

# Find oldest TODOs (if using git blame)
git blame -e -L "/TODO/",+1 -- '*.java'
```
