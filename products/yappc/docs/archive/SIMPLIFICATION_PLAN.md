# YAPPC Codebase Simplification Plan

**Version:** 1.0  
**Date:** 2026-03-23  
**Status:** Draft for Review  

## Executive Summary

The YAPPC codebase has grown to **~5,000+ Java files** and **3,815+ TypeScript files** with significant structural complexity. This plan outlines a systematic approach to simplify the structure while preserving all features, improving maintainability, and ensuring future extensibility.

### Key Metrics
| Metric | Current | Target | Reduction |
|--------|---------|--------|-----------|
| Java Agent Classes | ~270 | ~30 | **89%** |
| Core Gradle Modules | 18 | 8 | **56%** |
| Frontend Libraries | 35 | 20 | **43%** |
| Input/Output Records | ~540 | ~20 | **96%** |
| Lines of Boilerplate | ~15,000 | ~2,000 | **87%** |

---

## 1. Problem Analysis

### 1.1 Current Structure Overview

```
products/yappc/
├── backend/               # 520 items
│   ├── api/              # 343 items - HTTP API layer
│   ├── auth/             # 31 items
│   ├── persistence/      # 104 items
│   └── deployment/       # 25 items
├── core/                  # 2,249 items - HEAVIEST AREA
│   ├── agents/           # 539 items - 270+ Agent classes
│   │   ├── code-specialists/      # 197 items
│   │   ├── architecture-specialists/ # 63 items
│   │   ├── testing-specialists/   # 78 items
│   │   ├── runtime/      # 74 items
│   │   ├── workflow/      # 96 items
│   │   └── common/        # 3 items
│   ├── scaffold/         # 879 items - Scaffolding engine
│   │   ├── api/          # 71 items
│   │   ├── core/         # 249 items
│   │   ├── engine/       # (consolidated)
│   │   └── templates/    # (consolidated)
│   ├── refactorer/       # 358 items
│   │   ├── api/          # 116 items
│   │   └── engine/       # 111 items
│   ├── ai/               # 143 items
│   ├── domain/           # 84 items
│   ├── lifecycle/        # 111 items
│   └── ...
├── services/              # 95 items
│   ├── platform/         # Combined domain+infrastructure
│   └── lifecycle/        # Absorbed AI and scaffold
├── frontend/              # 3,478 items
│   ├── libs/             # 35 libraries
│   ├── apps/web/         # Web application
│   └── apps/api/         # API layer
└── libs/java/            # Shared libraries
```

### 1.2 Critical Issues Identified

#### Issue 1: Agent Class Explosion (HIGH PRIORITY)
**Problem:** 270+ Agent classes, each with:
- 1 Agent class (extends YAPPCAgentBase)
- 1 Input record
- 1 Output record
- 1 Generator class
- ~100 lines of boilerplate per agent = **~27,000 lines of boilerplate**

**Example Pattern (Repeated 270x):**
```java
// JavaExpertAgent.java - 115 lines
public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {
    // Same structure repeated...
}

// JavaExpertInput.java - 27 lines  
public record JavaExpertInput(@NotNull String codeContext, @NotNull String question, ...) {}

// JavaExpertOutput.java - ~30 lines
public record JavaExpertOutput(String id, String recommendation, ...) {}

// JavaExpertGenerator.java - ~50 lines
public static class JavaExpertGenerator implements OutputGenerator<...> {
    // Same generate() pattern
}
```

**Impact:**
- Massive code volume to maintain
- Any change to base pattern requires 270 edits
- Testing overhead multiplied
- Cognitive load for developers

#### Issue 2: Excessive Module Granularity (MEDIUM-HIGH)
**Problem:** 18 core modules with tight coupling

**Current Modules:**
1. `domain` - Value objects
2. `spi` - Plugin interfaces
3. `framework` - Entry points
4. `ai` - AI integration
5. `knowledge-graph` - Graph store
6. `agents/runtime` - Agent execution
7. `agents/workflow` - Multi-agent pipelines
8. `agents/common` - Shared I/O
9. `agents/code-specialists` - Code agents
10. `agents/architecture-specialists` - Architecture agents
11. `agents/testing-specialists` - Test agents
12. `scaffold/api` - Scaffolding contracts
13. `scaffold/core` - Scaffolding engine
14. `scaffold/packs` - Built-in packs
15. `refactorer/api` - Refactoring API
16. `refactorer/engine` - AST engine
17. `lifecycle` - Session management
18. `cli-tools` - CLI commands

**Impact:**
- Complex dependency graph
- Build performance overhead
- Cross-module refactoring difficulty
- Mental model complexity

#### Issue 3: Frontend Library Proliferation (MEDIUM)
**Problem:** 35 frontend libraries with potential overlap

**Current Libraries:**
- UI & Components: `@yappc/ui`, `@yappc/canvas`, `@yappc/code-editor`, `@yappc/diagram`
- State & Data: `@yappc/store`, `@yappc/crdt`, `@yappc/api`
- AI: `@yappc/ai-core`, `@yappc/ai-ui`, `@yappc/agents`
- Dev Tools: `@yappc/design-tokens`, `@yappc/testing`, `@yappc/auth`
- Plus 26 more specialized libraries

**Impact:**
- Dependency management complexity
- Version synchronization challenges
- Build time overhead
- Potential circular dependencies

#### Issue 4: Duplicated Patterns (MEDIUM)
**Problem:** Same patterns repeated across specialists

**Examples:**
- Input validation logic duplicated per agent
- Generator boilerplate nearly identical
- Error handling patterns repeated
- Memory store access patterns duplicated

---

## 2. Simplification Strategy

### 2.1 Guiding Principles

1. **Convention over Configuration** - Sensible defaults, minimal boilerplate
2. **Composition over Inheritance** - Flexible agent composition
3. **Single Source of Truth** - Schema-driven agent definitions
4. **Progressive Disclosure** - Simple cases easy, complex cases possible
5. **Backward Compatibility** - No breaking changes during transition

### 2.2 Core Strategies

#### Strategy A: Generic Agent Framework (Eliminates ~240 Agent Classes)

**Current Pattern (270 classes):**
```java
public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {
    public JavaExpertAgent(MemoryStore store, OutputGenerator<...> generator) {
        super("JavaExpertAgent", "expert.java", contract, generator);
    }
}
```

**New Pattern (30 generic adapters):**
```java
// Single GenericAgent class handles all agents
@Component
public class GenericAgentRegistry {
    
    private final Map<String, AgentDefinition> definitions;
    private final AgentExecutor executor;
    
    public <I, O> Promise<StepResult<O>> execute(
            String agentId, 
            I input, 
            AgentContext context) {
        AgentDefinition def = definitions.get(agentId);
        return executor.execute(def, input, context);
    }
}

// Agents defined by configuration, not classes
@AgentDefinition(
    id = "expert.java",
    inputSchema = "schemas/java-expert-input.json",
    outputSchema = "schemas/java-expert-output.json",
    generator = JavaExpertGenerator.class,
    tags = {"java", "architecture", "code-review"}
)
public interface JavaExpertAgent {} // Marker interface only
```

#### Strategy B: Unified Input/Output Schema System (Eliminates ~520 Record Classes)

**Current Pattern:**
```java
public record JavaExpertInput(@NotNull String codeContext, @NotNull String question, ...) {}
public record JavaExpertOutput(String id, String recommendation, ...) {}
// Repeated 270 times...
```

**New Pattern:**
```java
// Single GenericInput class with schema validation
public class AgentInput {
    private final JsonNode data;
    private final Schema schema;
    
    public <T> T get(String path, Class<T> type) { ... }
    public <T> T get(String path, TypeReference<T> type) { ... }
    public ValidationResult validate() { ... }
}

// Schema-driven with type-safe accessors
// JSON Schema defines structure:
// {
//   "type": "object",
//   "properties": {
//     "codeContext": { "type": "string", "minLength": 1 },
//     "question": { "type": "string" }
//   }
// }
```

#### Strategy C: Module Consolidation (18 → 8 Core Modules)

**Proposed New Structure:**

```
core/
├── foundation/           # Merged: domain + spi + framework
│   ├── src/main/java/
│   │   ├── domain/      # Value objects, events
│   │   ├── spi/         # Plugin interfaces
│   │   └── framework/   # Entry points
│   └── build.gradle.kts
├── intelligence/       # Merged: ai + knowledge-graph
│   ├── src/main/java/
│   │   ├── ai/         # AI integration, LLM orchestration
│   │   └── knowledge/   # Graph store
│   └── build.gradle.kts
├── agents/            # Merged: agents/* (consolidated)
│   ├── src/main/java/
│   │   ├── runtime/     # Execution engine
│   │   ├── workflow/    # Multi-agent orchestration
│   │   └── specialists/ # All agent types (code, arch, testing)
│   └── build.gradle.kts
├── composition/       # Merged: scaffold + refactorer
│   ├── src/main/java/
│   │   ├── scaffold/    # Project scaffolding
│   │   └── refactor/    # Code refactoring
│   └── build.gradle.kts
├── lifecycle/         # (keep as-is, already reasonable)
│   └── build.gradle.kts
└── cli/               # Merged: cli-tools + admin tools
    └── build.gradle.kts
```

#### Strategy D: Frontend Library Consolidation (35 → 20 Libraries)

**Consolidation Plan:**

| Current | Consolidated Into | Rationale |
|---------|-------------------|-----------|
| `@yappc/canvas` + `@yappc/diagram` | `@yappc/visual` | Both visual composition tools |
| `@yappc/ai-core` + `@yappc/ai-ui` | `@yappc/ai` | AI functionality belongs together |
| `@yappc/config` + `@yappc/config-hooks` | `@yappc/config` | Configuration management |
| `@yappc/navigation-ui` + `@yappc/initialization-ui` | `@yappc/onboarding` | User onboarding flows |
| `@yappc/messaging` + `@yappc/notifications` | `@yappc/communications` | Communication systems |
| `@yappc/development-ui` + `@yappc/ide` | `@yappc/devtools` | Developer tools |
| `@yappc/collab` + `@yappc/realtime` | `@yappc/collaboration` | Real-time features |

---

## 3. Detailed Implementation Plan

### Phase 1: Foundation - Schema-Driven Agent Framework (Weeks 1-4)

#### Week 1: Design & Prototype
**Deliverables:**
- [ ] Design `GenericAgent` and `AgentDefinition` API
- [ ] Create JSON Schema specification for agent inputs/outputs
- [ ] Prototype schema validation framework
- [ ] Define backward compatibility layer

**Key Decisions:**
```java
// Option A: Annotation-driven
@AgentDefinition(id = "expert.java", ...)
public interface JavaExpertAgent {}

// Option B: Configuration-driven (YAML)
# agents/java-expert.yaml
agent:
  id: expert.java
  input_schema: schemas/java-expert-input.json
  generator: com.ghatana.generators.JavaExpertGenerator

// Option C: Programmatic registration
registry.register(AgentSpec.builder()
    .id("expert.java")
    .inputSchema(loadSchema("java-expert-input.json"))
    .generator(new JavaExpertGenerator())
    .build());
```

**Recommendation:** Option B (YAML) + Option C for programmatic cases

#### Week 2: Core Framework Implementation
**Deliverables:**
- [ ] Implement `GenericAgentRegistry`
- [ ] Implement `AgentExecutor` with middleware support
- [ ] Create schema validation integration
- [ ] Build code generation for type-safe accessors

```java
// Core framework classes
public class GenericAgentRegistry {
    private final Map<String, AgentSpec> specs;
    private final AgentExecutor executor;
    
    public Promise<AgentResult> execute(String agentId, JsonNode input, Context ctx) {
        AgentSpec spec = specs.get(agentId);
        ValidationResult validation = spec.validate(input);
        if (!validation.isValid()) {
            return Promise.ofException(new ValidationException(validation));
        }
        return executor.execute(spec, input, ctx);
    }
}

public class AgentExecutor {
    private final List<AgentMiddleware> middlewares;
    
    public Promise<AgentResult> execute(AgentSpec spec, JsonNode input, Context ctx) {
        Chain chain = new Chain(middlewares, spec.getGenerator());
        return chain.execute(spec, input, ctx);
    }
}
```

#### Week 3: Generator Consolidation
**Deliverables:**
- [ ] Audit all 270 generators for common patterns
- [ ] Create `AbstractTemplateGenerator` base class
- [ ] Implement `LLMGenerator` for AI-powered agents
- [ ] Implement `RuleBasedGenerator` for deterministic agents

**Generator Taxonomy (from analysis):**
| Pattern | Count | Consolidation Strategy |
|---------|-------|----------------------|
| LLM-based | ~180 | Single `LLMGenerator` with prompt templates |
| Rule-based | ~60 | `RuleBasedGenerator` with strategy pattern |
| Template-based | ~20 | `TemplateGenerator` with engine integration |
| Hybrid | ~10 | Compose multiple generators |

```java
// Consolidated LLM Generator
public class LLMGenerator implements AgentGenerator {
    private final LlmProvider llm;
    private final PromptTemplate template;
    
    @Override
    public Promise<AgentResult> generate(AgentInput input, Context ctx) {
        String prompt = template.render(input);
        return llm.generate(prompt)
            .map(response -> parseResponse(response, input));
    }
}

// Usage in YAML
agent:
  id: expert.java
  generator:
    type: llm
    prompt_template: prompts/java-expert.txt
    model: gpt-4
    temperature: 0.7
```

#### Week 4: Migration Tooling & Testing
**Deliverables:**
- [ ] Create migration script: `java-to-yaml` converter
- [ ] Generate schema files from existing Input/Output records
- [ ] Build compatibility tests (old vs new implementations)
- [ ] Create developer migration guide

**Migration Script:**
```bash
# Convert existing agent classes to YAML definitions
./scripts/migrate-agent.sh \
  --source core/agents/code-specialists/src/main/java \
  --output core/agents/src/main/resources/agents/ \
  --dry-run

# Output: agents/java-expert.yaml, schemas/java-expert-input.json
```

### Phase 2: Module Consolidation (Weeks 5-8)

#### Week 5: Foundation Module Merge
**Tasks:**
- [ ] Merge `domain` + `spi` + `framework` → `foundation`
- [ ] Consolidate build.gradle.kts dependencies
- [ ] Update import statements across codebase
- [ ] Verify no circular dependencies introduced

**Build Configuration:**
```kotlin
// core/foundation/build.gradle.kts
plugins {
    id("java-library")
    id("com.ghatana.publish")
}

dependencies {
    // Platform dependencies
    api(platform(project(":platform:java:domain")))
    api(platform(project(":platform:java:plugin")))
    
    // Internal exports
    api(project(":products:yappc:libs:java:yappc-domain"))
    
    // Implementation details
    implementation(libs.activej.promise)
    implementation(libs.jackson.databind)
    
    // Schema validation
    implementation(libs.networknt.validator)
}
```

#### Week 6: Intelligence Module Merge
**Tasks:**
- [ ] Merge `ai` + `knowledge-graph` → `intelligence`
- [ ] Consolidate LLM providers and model management
- [ ] Merge knowledge graph operations with AI context
- [ ] Create unified AI context manager

#### Week 7: Agents Module Consolidation
**Tasks:**
- [ ] Merge all `agents/*` submodules into single `agents` module
- [ ] Organize by capability, not hierarchy:
  ```
  agents/src/main/java/com/ghatana/yappc/agents/
  ├── runtime/           # Execution framework
  ├── workflow/         # Multi-agent orchestration
  ├── capabilities/     # Agent implementations
  │   ├── code/        # Code specialists (was code-specialists)
  │   ├── architecture/# Architecture specialists
  │   └── testing/     # Testing specialists
  └── registry/        # Agent registration & discovery
  ```
- [ ] Migrate all YAML agent definitions
- [ ] Update tests for new structure

#### Week 8: Composition Module Merge
**Tasks:**
- [ ] Merge `scaffold/*` + `refactorer/*` → `composition`
- [ ] Unified project transformation API
- [ ] Shared template engine
- [ ] Common AST operations

### Phase 3: Frontend Consolidation (Weeks 9-11)

#### Week 9: Library Merge - Part 1
**Merges:**
- `@yappc/canvas` + `@yappc/diagram` → `@yappc/visual`
- `@yappc/ai-core` + `@yappc/ai-ui` → `@yappc/ai`
- `@yappc/config` + `@yappc/config-hooks` → `@yappc/config`

**Tasks:**
- [ ] Merge source code
- [ ] Consolidate package.json dependencies
- [ ] Update exports in index.ts
- [ ] Migrate imports in consuming code

#### Week 10: Library Merge - Part 2
**Merges:**
- `@yappc/collab` + `@yappc/realtime` → `@yappc/collaboration`
- `@yappc/messaging` + `@yappc/notifications` → `@yappc/communications`
- `@yappc/development-ui` + `@yappc/ide` → `@yappc/devtools`

#### Week 11: Cleanup & Validation
**Tasks:**
- [ ] Remove deprecated library directories
- [ ] Update pnpm-workspace.yaml
- [ ] Consolidate build configurations
- [ ] Run full test suite

### Phase 4: Advanced Simplification (Weeks 12-14)

#### Week 12: Boilerplate Elimination
**Initiatives:**
- [ ] Implement annotation processor for agent boilerplate
- [ ] Generate type-safe input/output accessors from schemas
- [ ] Auto-generate documentation from agent definitions
- [ ] Create IDE plugin for agent development

**Annotation Processor:**
```java
@Agent("expert.java")
public interface JavaExpertAgent {
    @Input
    String getCodeContext();
    
    @Input
    String getQuestion();
    
    @Output
    String getRecommendation();
}

// Generated: JavaExpertInput, JavaExpertOutput, JavaExpertAgentImpl
```

#### Week 13: Testing Simplification
**Initiatives:**
- [ ] Unified test framework for all agents
- [ ] Schema-based test case generation
- [ ] Mock agent registry for testing
- [ ] Parameterized agent tests

#### Week 14: Documentation & Tooling
**Deliverables:**
- [ ] Architecture Decision Records (ADRs) for all changes
- [ ] Developer onboarding guide for new structure
- [ ] Agent development quick-start template
- [ ] Migration completion report

---

## 4. Reuse Strategy

### 4.1 Horizontal Reuse (Cross-Cutting Concerns)

| Component | Current State | Simplified State | Reuse Strategy |
|-----------|---------------|------------------|----------------|
| Validation | Duplicated per agent | Schema-based validation engine | Single validation service |
| Logging | Manual per agent | Agent middleware | Centralized logging middleware |
| Metrics | Manual instrumentation | Auto-instrumentation | Metrics collection middleware |
| Error Handling | Try-catch per agent | Centralized error handling | Error handler middleware |
| Caching | Ad-hoc implementations | Unified cache abstraction | Cache middleware with policies |

### 4.2 Vertical Reuse (Domain-Specific)

| Domain | Reusable Components |
|--------|---------------------|
| Code Analysis | AST parsers, symbol resolvers, dependency graphs |
| AI/LLM | Prompt templates, model adapters, context managers |
| Scaffolding | Template engines, file generators, project structures |
| Testing | Test case generators, coverage analyzers, mock frameworks |

### 4.3 Platform Reuse

Leverage existing platform modules:
- `platform:java:agent-core` - Agent framework base
- `platform:java:ai-integration` - LLM abstractions
- `platform:java:workflow` - Workflow orchestration
- `platform:java:observability` - Metrics and tracing

---

## 5. Future-Proofing & Extensibility

### 5.1 Plugin Architecture

```java
public interface AgentPlugin {
    String getPluginId();
    List<AgentDefinition> getAgentDefinitions();
    List<AgentMiddleware> getMiddlewares();
}

// Third-party agents can be added via plugin
public class CustomAgentPlugin implements AgentPlugin {
    @Override
    public List<AgentDefinition> getAgentDefinitions() {
        return List.of(
            AgentDefinition.builder()
                .id("custom.agent")
                .schema(loadSchema("custom-schema.json"))
                .generator(new CustomGenerator())
                .build()
        );
    }
}
```

### 5.2 Schema Evolution Strategy

```yaml
# Schema versioning for backward compatibility
schema:
  id: java-expert-input
  version: "2.0"
  compatibility:
    - version: "1.0"
      migration: automatic  # Auto-convert v1 to v2
    - version: "2.0"
      migration: none       # Current version
  
  fields:
    codeContext:
      type: string
      required: true
      since: "1.0"
    
    newField:
      type: string
      required: false
      since: "2.0"
      default: "default-value"
```

### 5.3 Configuration-Driven Behavior

```yaml
# agents/java-expert.yaml
agent:
  id: expert.java
  
  # Behavior configuration
  behavior:
    maxRetries: 3
    timeout: 30s
    cacheResults: true
    cacheTtl: 1h
  
  # Capabilities
  capabilities:
    - code-analysis
    - architecture-review
    - best-practices
  
  # Prompt configuration
  prompts:
    system: prompts/java-expert-system.txt
    user: prompts/java-expert-user.txt
  
  # Model configuration
  model:
    provider: openai
    model: gpt-4
    temperature: 0.7
    maxTokens: 2000
```

---

## 6. Risk Mitigation

### 6.1 Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Breaking changes in production | Low | High | Comprehensive test coverage, feature flags, gradual rollout |
| Performance degradation | Medium | Medium | Benchmark before/after, performance gates in CI |
| Developer resistance | Medium | Medium | Clear documentation, training sessions, IDE tooling |
| Schema validation overhead | Medium | Low | Optimize validator, cache schemas, async validation |
| Migration incomplete | Low | High | Automated verification, migration completeness checks |

### 6.2 Rollback Strategy

1. **Feature Flags:** All new agent framework behind `agent.v2.enabled` flag
2. **Blue-Green Deployment:** Maintain old and new implementations side-by-side
3. **Gradual Migration:** Migrate 10 agents → test → migrate 50 → test → migrate all
4. **Automated Verification:** Script to verify agent parity between old/new

---

## 7. Success Metrics

### 7.1 Quantitative Metrics

| Metric | Baseline | Target | Measurement |
|--------|----------|--------|-------------|
| Total Java files | ~5,000 | ~2,500 | `find . -name "*.java" \| wc -l` |
| Agent classes | 270 | 30 | `grep -r "extends YAPPCAgentBase" \| wc -l` |
| Input/Output records | 540 | 20 | `find . -name "*Input.java" -o -name "*Output.java" \| wc -l` |
| Core modules | 18 | 8 | Count in settings.gradle.kts |
| Frontend libraries | 35 | 20 | Count in pnpm-workspace.yaml |
| Build time | ~5 min | ~3 min | Gradle build scan |
| Test execution time | ~10 min | ~6 min | CI pipeline metrics |
| Lines of boilerplate | ~15,000 | ~2,000 | cloc analysis |

### 7.2 Qualitative Metrics

- **Developer onboarding time:** Measure time for new dev to create first agent
- **Code review velocity:** Average PR review time
- **Bug density:** Bugs per 1000 lines of agent code
- **Feature delivery time:** Time to add new agent capability

---

## 8. Implementation Timeline Summary

```
Month 1: Foundation (Weeks 1-4)
├── Week 1: Design & Prototype
├── Week 2: Core Framework
├── Week 3: Generator Consolidation
└── Week 4: Migration Tooling

Month 2: Consolidation (Weeks 5-8)
├── Week 5: Foundation Module
├── Week 6: Intelligence Module
├── Week 7: Agents Module
└── Week 8: Composition Module

Month 3: Frontend & Polish (Weeks 9-14)
├── Week 9: Frontend Merge Part 1
├── Week 10: Frontend Merge Part 2
├── Week 11: Cleanup & Validation
├── Week 12: Advanced Simplification
├── Week 13: Testing Simplification
└── Week 14: Documentation & Tooling
```

---

## 9. Next Steps

1. **Review & Approve:** Stakeholder review of this plan
2. **Proof of Concept:** Implement 3 agents with new framework to validate approach
3. **Team Alignment:** Schedule kickoff meeting with affected teams
4. **Infrastructure Setup:** Create feature branches and CI pipelines
5. **Begin Phase 1:** Start with schema-driven agent framework

---

## Appendix A: Current vs Proposed Directory Structure

### Current (Simplified)
```
core/
├── domain/                    # 74 files
├── spi/                       # 55 files
├── framework/                 # 25 files
├── ai/                        # 119 files
├── knowledge-graph/           # 13 files
├── agents/
│   ├── runtime/              # 60 files
│   ├── workflow/             # 59 files
│   ├── common/               # 0 files (just 3 items)
│   ├── code-specialists/     # 195 files
│   ├── architecture-specialists/ # 59 files
│   └── testing-specialists/  # 69 files
├── scaffold/
│   ├── api/                  # 71 files
│   ├── core/                 # 249 files
│   └── packs/                # 4 files
├── refactorer/
│   ├── api/                  # 116 files
│   └── engine/               # 111 files
├── lifecycle/                 # 83 files
└── cli-tools/                 # 6 files
```

### Proposed
```
core/
├── foundation/               # 154 files (domain+spi+framework)
│   ├── domain/
│   ├── spi/
│   └── framework/
├── intelligence/            # 132 files (ai+knowledge)
│   ├── ai/
│   └── knowledge/
├── agents/                  # 383 files (all agents consolidated)
│   ├── runtime/
│   ├── workflow/
│   └── capabilities/
│       ├── code/
│       ├── architecture/
│       └── testing/
├── composition/             # 531 files (scaffold+refactorer)
│   ├── scaffold/
│   └── refactor/
├── lifecycle/                 # 83 files (unchanged)
└── cli/                     # 6 files (renamed from cli-tools)
```

**Result: 18 modules → 6 modules**

---

## Appendix B: Example Agent Migration

### Before (4 files, ~200 lines)

```java
// JavaExpertAgent.java
public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {
    private final MemoryStore memoryStore;
    
    public JavaExpertAgent(MemoryStore store, OutputGenerator<...> gen) {
        super("JavaExpertAgent", "expert.java", contract, gen);
        this.memoryStore = store;
    }
    
    @Override
    protected MemoryStore getMemoryStore() { return memoryStore; }
    
    @Override
    public ValidationResult validateInput(JavaExpertInput input) {
        if (input.codeContext() == null || input.codeContext().isEmpty()) {
            return ValidationResult.fail("codeContext cannot be empty");
        }
        return ValidationResult.success();
    }
    
    @Override
    protected StepRequest<JavaExpertInput> perceive(...) { ... }
}

// JavaExpertInput.java
public record JavaExpertInput(@NotNull String codeContext, 
                              @NotNull String question, 
                              @NotNull Map<String, Object> projectMetadata) {
    public JavaExpertInput {
        if (codeContext == null || codeContext.isEmpty()) {
            throw new IllegalArgumentException("codeContext cannot be null or empty");
        }
        if (question == null || question.isEmpty()) {
            throw new IllegalArgumentException("question cannot be null or empty");
        }
        if (projectMetadata == null) {
            projectMetadata = Map.of();
        }
    }
}

// JavaExpertOutput.java
public record JavaExpertOutput(String id, String recommendation, 
                                List<String> suggestions, Map<String, Object> metadata) {}

// JavaExpertGenerator.java
public static class JavaExpertGenerator implements OutputGenerator<...> {
    @Override
    public Promise<StepResult<JavaExpertOutput>> generate(StepRequest<JavaExpertInput> input, 
                                                          AgentContext context) {
        // ~50 lines of boilerplate
    }
    
    @Override
    public Promise<Double> estimateCost(...) { ... }
}
```

### After (2 files, ~50 lines)

```yaml
# resources/agents/java-expert.yaml
agent:
  id: expert.java
  name: "Java Expert"
  description: "Expert Java engineer for architecture and implementation guidance"
  
  input_schema: schemas/java-expert-input.json
  output_schema: schemas/java-expert-output.json
  
  generator:
    type: llm
    prompt_template: prompts/java-expert.txt
    model: gpt-4
    temperature: 0.7
  
  tags: [java, architecture, code-review]
  capabilities: [code-analysis, best-practices, architecture-review]
  
  validation:
    - field: codeContext
      required: true
      minLength: 1
    - field: question
      required: true
      minLength: 1
```

```json
// resources/schemas/java-expert-input.json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["codeContext", "question"],
  "properties": {
    "codeContext": {
      "type": "string",
      "minLength": 1,
      "description": "The Java code context to analyze"
    },
    "question": {
      "type": "string", 
      "minLength": 1,
      "description": "The specific question about the code"
    },
    "projectMetadata": {
      "type": "object",
      "default": {}
    }
  }
}
```

```java
// JavaExpertGenerator.java (only if custom logic needed)
@Component
public class JavaExpertPostProcessor implements AgentPostProcessor {
    @Override
    public AgentResult process(AgentResult result, AgentContext ctx) {
        // Custom post-processing only
        return result.withEnhancedMetadata(...);
    }
}
```

**Result: 200 lines → 50 lines (75% reduction)**

---

## Appendix C: Dependency Matrix (Current vs Proposed)

### Current Dependencies
```
platform/* ←────── all modules depend on platform
deep tree with many cross-dependencies
```

### Proposed Dependencies
```
platform/* ←────── all modules depend on platform
     ↑
 [foundation]     # domain, spi, framework - base layer
     ↑
  [intelligence]   # ai, knowledge
     ↑
   [agents]        # runtime, workflow, all capabilities
     ↑
  [composition]    # scaffold, refactor
     ↑
   [lifecycle]     # orchestrates all
     ↑
    [cli]          # thin wrapper
```

**Benefits:**
- Clear dependency direction
- No circular dependencies possible
- Easy to understand layering
- Testable in isolation

---

*End of Simplification Plan*
