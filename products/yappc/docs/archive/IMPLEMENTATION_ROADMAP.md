# YAPPC Simplification Implementation Roadmap

**Version:** 1.0  
**Created:** 2026-03-23  
**Status:** Ready for Implementation  

---

## Quick Reference: Priority Matrix

| Task | Impact | Effort | Priority | Phase |
|------|--------|--------|----------|-------|
| Agent framework prototype | High | Medium | P0 | Phase 1 |
| Migrate 10 pilot agents | High | Medium | P0 | Phase 1 |
| Consolidate foundation modules | Medium | Low | P1 | Phase 2 |
| Consolidate intelligence modules | Medium | Low | P1 | Phase 2 |
| Consolidate agents module | High | High | P1 | Phase 2 |
| Frontend library consolidation | Medium | Medium | P2 | Phase 3 |
| Boilerplate elimination | High | High | P2 | Phase 4 |

---

## Phase 1: Agent Framework Foundation (Weeks 1-4)

### Week 1: Design & Architecture

#### Day 1-2: Finalize Agent Framework API Design
**Owner:** YAPPC Architecture Team  
**Deliverables:**
- [ ] API specification document
- [ ] Interface definitions
- [ ] Dependency injection strategy

```java
// Key interfaces to define:
public interface AgentRegistry {
    Promise<AgentResult> execute(String agentId, JsonNode input, AgentContext ctx);
    Optional<AgentSpec> getSpec(String agentId);
    List<AgentSpec> listAgents(Set<String> tags);
}

public interface AgentSpec {
    String getId();
    String getName();
    JsonSchema getInputSchema();
    JsonSchema getOutputSchema();
    Set<String> getTags();
    Promise<AgentResult> execute(JsonNode input, AgentContext ctx);
}
```

#### Day 3-4: Schema System Design
**Owner:** YAPPC Architecture Team  
**Deliverables:**
- [ ] JSON Schema specification
- [ ] Schema validation framework
- [ ] Schema-to-Java type mapping

#### Day 5: Review & Approval
**Owner:** YAPPC Tech Lead  
**Deliverables:**
- [ ] Architecture review meeting
- [ ] Approved design document
- [ ] Go/no-go decision for implementation

### Week 2: Core Framework Implementation

#### Day 1-2: GenericAgentRegistry Implementation
**Owner:** Backend Team  
**Tasks:**
- [ ] Implement `GenericAgentRegistry` class
- [ ] Create agent discovery mechanism
- [ ] Build YAML parser for agent definitions

```java
@Component
public class GenericAgentRegistry implements AgentRegistry {
    private final Map<String, AgentSpec> agents = new ConcurrentHashMap<>();
    private final AgentLoader loader;
    
    @Inject
    public GenericAgentRegistry(AgentLoader loader) {
        this.loader = loader;
        loadAgents();
    }
    
    private void loadAgents() {
        loader.loadAll().forEach(spec -> agents.put(spec.getId(), spec));
    }
    
    @Override
    public Promise<AgentResult> execute(String agentId, JsonNode input, AgentContext ctx) {
        AgentSpec spec = agents.get(agentId);
        if (spec == null) {
            return Promise.ofException(new AgentNotFoundException(agentId));
        }
        return spec.execute(input, ctx);
    }
}
```

#### Day 3-4: AgentExecutor & Middleware
**Owner:** Backend Team  
**Tasks:**
- [ ] Implement `AgentExecutor` with middleware chain
- [ ] Create built-in middleware (logging, metrics, validation, caching)
- [ ] Build error handling framework

```java
public class AgentExecutor {
    private final List<AgentMiddleware> middlewares;
    
    public Promise<AgentResult> execute(AgentSpec spec, JsonNode input, AgentContext ctx) {
        Chain chain = new Chain(middlewares, spec::execute);
        return chain.proceed(spec, input, ctx);
    }
}

// Middleware examples:
public class ValidationMiddleware implements AgentMiddleware {
    @Override
    public Promise<AgentResult> invoke(AgentSpec spec, JsonNode input, 
                                       AgentContext ctx, Chain chain) {
        ValidationResult result = spec.getInputSchema().validate(input);
        if (!result.isValid()) {
            return Promise.ofException(new ValidationException(result));
        }
        return chain.proceed(spec, input, ctx);
    }
}

public class LoggingMiddleware implements AgentMiddleware {
    @Override
    public Promise<AgentResult> invoke(AgentSpec spec, JsonNode input, 
                                       AgentContext ctx, Chain chain) {
        log.info("Executing agent: {}", spec.getId());
        long start = System.currentTimeMillis();
        
        return chain.proceed(spec, input, ctx)
            .map(result -> {
                long duration = System.currentTimeMillis() - start;
                log.info("Agent {} completed in {}ms", spec.getId(), duration);
                return result;
            })
            .mapException(e -> {
                log.error("Agent {} failed: {}", spec.getId(), e.getMessage());
                return e;
            });
    }
}
```

#### Day 5: Testing & Refinement
**Owner:** QA Team  
**Tasks:**
- [ ] Unit tests for registry
- [ ] Unit tests for executor
- [ ] Integration tests for middleware chain
- [ ] Performance benchmarks

### Week 3: Generator Consolidation

#### Day 1-2: LLM Generator
**Owner:** AI Team  
**Tasks:**
- [ ] Create unified `LLMGenerator` class
- [ ] Build prompt template system
- [ ] Integrate with existing LLM providers

```java
@Component
public class LLMGenerator implements AgentGenerator {
    private final LlmProvider llm;
    private final TemplateEngine templates;
    
    @Override
    public Promise<AgentResult> generate(AgentInput input, AgentContext ctx) {
        String templateName = input.getSpec().getConfig("prompt_template");
        String prompt = templates.render(templateName, input.getData());
        
        LlmRequest request = LlmRequest.builder()
            .prompt(prompt)
            .model(input.getSpec().getConfig("model", "gpt-4"))
            .temperature(input.getSpec().getConfig("temperature", 0.7))
            .build();
        
        return llm.generate(request)
            .map(response -> parseResponse(response, input));
    }
    
    private AgentResult parseResponse(LlmResponse response, AgentInput input) {
        // Parse and validate LLM response
        JsonNode output = JsonUtils.parse(response.getText());
        return AgentResult.success(output);
    }
}
```

#### Day 3-4: Rule-Based & Template Generators
**Owner:** Backend Team  
**Tasks:**
- [ ] Create `RuleBasedGenerator` for deterministic agents
- [ ] Create `TemplateGenerator` for scaffolding agents
- [ ] Build generator composition framework

#### Day 5: Testing
**Owner:** QA Team  
**Tasks:**
- [ ] Test all generator types
- [ ] Validate backward compatibility
- [ ] Performance benchmarks

### Week 4: Migration Tooling

#### Day 1-2: Migration Script
**Owner:** DevOps Team  
**Tasks:**
- [ ] Create `AgentMigrationTool` class
- [ ] Implement Java-to-YAML converter
- [ ] Build schema extractor from records

```java
public class AgentMigrationTool {
    public void migrateAgent(String agentClassName) {
        // 1. Analyze existing agent class
        AgentAnalysis analysis = analyzeAgent(agentClassName);
        
        // 2. Generate YAML definition
        String yaml = generateYaml(analysis);
        writeToFile(analysis.getAgentId() + ".yaml", yaml);
        
        // 3. Extract JSON Schema from Input/Output records
        JsonSchema inputSchema = extractSchema(analysis.getInputClass());
        JsonSchema outputSchema = extractSchema(analysis.getOutputClass());
        writeToFile("schemas/" + analysis.getAgentId() + "-input.json", inputSchema);
        writeToFile("schemas/" + analysis.getAgentId() + "-output.json", outputSchema);
        
        // 4. Generate compatibility layer (optional)
        if (analysis.hasCustomLogic()) {
            generateAdapter(analysis);
        }
    }
}
```

#### Day 3-4: Validation Tools
**Owner:** QA Team  
**Tasks:**
- [ ] Create agent parity checker
- [ ] Build migration completeness verifier
- [ ] Implement regression test suite

```java
public class AgentParityChecker {
    public ParityReport checkParity(String agentId) {
        // 1. Load old and new agent implementations
        OldAgent oldAgent = loadOldAgent(agentId);
        AgentSpec newAgent = loadNewAgent(agentId);
        
        // 2. Run test cases through both
        List<TestCase> tests = loadTestCases(agentId);
        List<ParityResult> results = new ArrayList<>();
        
        for (TestCase test : tests) {
            AgentResult oldResult = oldAgent.execute(test.getInput());
            AgentResult newResult = await(newAgent.execute(test.getInput(), test.getContext()));
            
            results.add(compareResults(oldResult, newResult));
        }
        
        return new ParityReport(agentId, results);
    }
}
```

#### Day 5: Documentation
**Owner:** Tech Writing  
**Tasks:**
- [ ] Migration guide for developers
- [ ] Framework usage documentation
- [ ] Troubleshooting guide

---

## Phase 2: Module Consolidation (Weeks 5-8)

### Week 5: Foundation Module

**Goal:** Merge `domain` + `spi` + `framework` → `foundation`

#### Day 1: Preparation
- [ ] Create new `foundation` module structure
- [ ] Set up build.gradle.kts
- [ ] Configure package structure

#### Day 2-3: Code Migration
- [ ] Move `domain` sources to `foundation/src/main/java/domain/`
- [ ] Move `spi` sources to `foundation/src/main/java/spi/`
- [ ] Move `framework` sources to `foundation/src/main/java/framework/`
- [ ] Update package declarations

#### Day 4: Dependency Updates
- [ ] Update imports in all consuming modules
- [ ] Fix circular dependencies
- [ ] Consolidate common dependencies

#### Day 5: Testing & Validation
- [ ] Run full test suite
- [ ] Verify no compilation errors
- [ ] Performance comparison

### Week 6: Intelligence Module

**Goal:** Merge `ai` + `knowledge-graph` → `intelligence`

#### Day 1-2: Structural Setup
- [ ] Create `intelligence` module
- [ ] Plan integration points between AI and knowledge graph
- [ ] Design unified context management

#### Day 3-4: Integration
- [ ] Merge AI services with knowledge graph operations
- [ ] Create unified AI context manager
- [ ] Integrate LLM context with graph queries

#### Day 5: Testing
- [ ] Test AI operations with knowledge graph
- [ ] Validate context propagation
- [ ] Performance benchmarks

### Week 7: Agents Module Consolidation

**Goal:** Merge all `agents/*` into single `agents` module

#### Day 1-2: Structure Design
```
core/agents/src/main/java/com/ghatana/yappc/agents/
├── runtime/              # From agents/runtime
│   ├── AgentRuntime.java
│   ├── AgentContext.java
│   └── ...
├── workflow/             # From agents/workflow
│   ├── WorkflowEngine.java
│   ├── MultiAgentOrchestrator.java
│   └── ...
├── capabilities/         # All specialist agents
│   ├── code/            # From code-specialists
│   │   ├── JavaExpert.yaml
│   │   ├── CodeReviewer.yaml
│   │   └── ...
│   ├── architecture/    # From architecture-specialists
│   │   ├── CloudPilot.yaml
│   │   └── ...
│   └── testing/         # From testing-specialists
│       ├── TestGenerator.yaml
│       └── ...
├── registry/            # New - agent discovery
│   ├── AgentRegistry.java
│   └── AgentLoader.java
└── generators/          # Consolidated generators
    ├── LLMGenerator.java
    ├── RuleBasedGenerator.java
    └── TemplateGenerator.java
```

#### Day 3-4: Source Migration
- [ ] Move all agent sources
- [ ] Convert Java agent classes to YAML definitions
- [ ] Migrate generators
- [ ] Update imports

#### Day 5: Testing
- [ ] Run agent test suite
- [ ] Verify all agents functional
- [ ] Performance validation

### Week 8: Composition Module

**Goal:** Merge `scaffold/*` + `refactorer/*` → `composition`

#### Day 1-3: Module Creation
- [ ] Create `composition` module
- [ ] Merge scaffold sources
- [ ] Merge refactorer sources
- [ ] Create unified transformation API

#### Day 4-5: Integration & Testing
- [ ] Integrate scaffolding with refactoring
- [ ] Test project transformation workflows
- [ ] Validate end-to-end scenarios

---

## Phase 3: Frontend Consolidation (Weeks 9-11)

### Week 9: Library Merges - Part 1

#### Tasks:
1. **Merge `@yappc/canvas` + `@yappc/diagram` → `@yappc/visual`**
   - [ ] Consolidate source code
   - [ ] Merge package.json dependencies
   - [ ] Update exports
   - [ ] Migrate consumers

2. **Merge `@yappc/ai-core` + `@yappc/ai-ui` → `@yappc/ai`**
   - [ ] Combine AI functionality
   - [ ] Unify type definitions
   - [ ] Update imports

3. **Merge `@yappc/config` + `@yappc/config-hooks` → `@yappc/config`**
   - [ ] Merge configuration utilities
   - [ ] Consolidate hooks

### Week 10: Library Merges - Part 2

#### Tasks:
1. **Merge `@yappc/collab` + `@yappc/realtime` → `@yappc/collaboration`**
2. **Merge `@yappc/messaging` + `@yappc/notifications` → `@yappc/communications`**
3. **Merge `@yappc/development-ui` + `@yappc/ide` → `@yappc/devtools`**
4. **Merge `@yappc/navigation-ui` + `@yappc/initialization-ui` → `@yappc/onboarding`**

### Week 11: Cleanup & Validation

#### Tasks:
- [ ] Remove deprecated library directories
- [ ] Update `pnpm-workspace.yaml`
- [ ] Consolidate build configurations
- [ ] Run full frontend test suite
- [ ] Bundle size comparison
- [ ] Build time comparison

---

## Phase 4: Advanced Simplification (Weeks 12-14)

### Week 12: Boilerplate Elimination

#### Day 1-3: Annotation Processor
**Goal:** Auto-generate boilerplate from minimal annotations

```java
// Developer writes:
@Agent(
    id = "expert.java",
    name = "Java Expert",
    description = "Expert Java engineer for architecture guidance"
)
public interface JavaExpertAgent {
    @Input(required = true, minLength = 1)
    String getCodeContext();
    
    @Input(required = true)
    String getQuestion();
    
    @Input
    Map<String, Object> getProjectMetadata();
    
    @Output
    String getRecommendation();
    
    @Output
    List<String> getSuggestions();
}

// Generated at compile time:
// - JavaExpertInput.java (record)
// - JavaExpertOutput.java (record)
// - JavaExpertAgentImpl.java (implementation)
// - java-expert-input.json (schema)
// - java-expert-output.json (schema)
// - java-expert.yaml (agent definition)
```

#### Day 4-5: IDE Integration
- [ ] IntelliJ plugin for agent development
- [ ] Code completion for agent definitions
- [ ] Schema validation in IDE

### Week 13: Testing Simplification

#### Tasks:
1. **Unified Test Framework**
   - [ ] Create `AgentTestRunner`
   - [ ] Schema-based test case generation
   - [ ] Parameterized agent tests

```java
@AgentTest(agentId = "expert.java")
public class JavaExpertAgentTest {
    
    @TestCase(name = "simple-analysis")
    public JsonNode simpleAnalysis() {
        return JsonUtils.obj()
            .put("codeContext", "public class Test {}")
            .put("question", "Review this code")
            .build();
    }
    
    @TestCase(name = "complex-analysis")
    public JsonNode complexAnalysis() {
        // Complex test case
    }
}
```

2. **Mock Agent Registry**
   - [ ] Create `MockAgentRegistry` for testing
   - [ ] Stub implementations for agents
   - [ ] Deterministic test scenarios

### Week 14: Documentation & Completion

#### Tasks:
1. **Architecture Decision Records**
   - [ ] ADR-001: Generic Agent Framework
   - [ ] ADR-002: Schema-Driven Development
   - [ ] ADR-003: Module Consolidation Strategy
   - [ ] ADR-004: Frontend Library Consolidation

2. **Developer Documentation**
   - [ ] New developer onboarding guide
   - [ ] Agent development quick-start
   - [ ] Migration completion report

3. **Final Validation**
   - [ ] Full system test
   - [ ] Performance benchmarks
   - [ ] Code quality metrics
   - [ ] Security audit

---

## Resource Allocation

### Team Assignments

| Phase | Backend Team | Frontend Team | QA Team | DevOps |
|-------|---------------|---------------|---------|--------|
| Phase 1 | 3 engineers | 1 engineer | 2 QA | 1 DevOps |
| Phase 2 | 4 engineers | 1 engineer | 2 QA | 1 DevOps |
| Phase 3 | 1 engineer | 4 engineers | 2 QA | 1 DevOps |
| Phase 4 | 2 engineers | 2 engineers | 2 QA | 1 DevOps |

### Key Personnel

- **Technical Lead:** Overall architecture decisions
- **Agent Framework Lead:** Backend framework design
- **Frontend Lead:** UI consolidation strategy
- **QA Lead:** Testing strategy and validation
- **DevOps Lead:** CI/CD and migration tooling

---

## Dependencies & Blockers

### External Dependencies

| Dependency | Status | Risk |
|------------|--------|------|
| Platform module stability | Stable | Low |
| Gradle build system | Stable | Low |
| JSON Schema library | Available | Low |
| ActiveJ framework | Stable | Low |

### Internal Blockers

| Blocker | Mitigation | Timeline |
|---------|------------|----------|
| Data-Cloud integration | Phase out dependencies | Week 2 |
| Circular dependencies | Refactor imports | Week 1 |
| Test coverage gaps | Add tests before migration | Week 1 |

---

## Success Criteria Checklist

### Phase 1 Completion Criteria
- [ ] Agent framework can execute 10 migrated agents
- [ ] All 10 agents pass parity tests with original implementation
- [ ] Framework performance within 10% of baseline
- [ ] Migration tooling can convert any agent automatically

### Phase 2 Completion Criteria
- [ ] All modules compile successfully
- [ ] No circular dependencies
- [ ] All tests passing
- [ ] Module count reduced from 18 to 8

### Phase 3 Completion Criteria
- [ ] Frontend builds successfully
- [ ] Library count reduced from 35 to 20
- [ ] Bundle size reduced by at least 15%
- [ ] Build time reduced by at least 20%

### Phase 4 Completion Criteria
- [ ] Boilerplate reduced by at least 80%
- [ ] Developer onboarding time reduced by 50%
- [ ] All documentation complete
- [ ] Full system validation passed

---

## Communication Plan

### Weekly Updates
- **Monday:** Sprint planning and blocker review
- **Wednesday:** Mid-week progress check
- **Friday:** Demo and retrospective

### Stakeholder Communication
- **Weekly:** Email summary to engineering leadership
- **Bi-weekly:** Architecture review meeting
- **Monthly:** All-hands demo

### Documentation Updates
- Keep SIMPLIFICATION_PLAN.md updated with actual progress
- Update CORE_ARCHITECTURE.md after each phase
- Maintain migration guide for developers

---

## Appendix: Migration Command Reference

### Agent Migration

```bash
# Migrate single agent
./gradlew :products:yappc:core:agents:runMigration \
  --args="--agent=JavaExpertAgent --dry-run"

# Migrate all agents in a package
./gradlew :products:yappc:core:agents:runMigration \
  --args="--package=com.ghatana.yappc.agents.code --output=yaml"

# Validate migration
./gradlew :products:yappc:core:agents:runValidation \
  --args="--agent=expert.java --check-parity"
```

### Module Migration

```bash
# Consolidate modules (Phase 2)
./scripts/consolidate-modules.sh --phase=2 --dry-run

# Verify consolidation
./gradlew :products:yappc:core:foundation:build
```

### Frontend Library Migration

```bash
# Consolidate frontend libraries
pnpm run consolidate-libs -- --from=@yappc/canvas,@yappc/diagram --to=@yappc/visual

# Update imports
pnpm run migrate-imports -- --old=@yappc/canvas --new=@yappc/visual
```

---

*End of Implementation Roadmap*
