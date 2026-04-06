# Agent Implementation Guide

**Version:** 2.0.0  
**Last Updated:** 2026-04-05  
**Scope:** Practical guide for implementing agents in the Ghatana platform

---

## 1. Quick Start

### 1.1 Minimal Agent Implementation

```java
package com.ghatana.myproduct.agents;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * @doc.type class
 * @doc.purpose Minimal example agent demonstrating the TypedAgent contract
 * @doc.layer example
 * @doc.pattern Deterministic Agent
 */
public class EchoAgent implements TypedAgent<String, String> {

    @Override
    @NotNull
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("echo")
            .name("Echo Agent")
            .type(AgentType.DETERMINISTIC)
            .determinism(DeterminismGuarantee.FULL)
            .criticality(Criticality.LOW)
            .autonomyLevel(AutonomyLevel.AUTONOMOUS)
            .build();
    }

    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull AgentConfig config) {
        // One-time setup
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<AgentResult<String>> process(@NotNull AgentContext ctx, @NotNull String input) {
        return Promise.of(AgentResult.success(input, descriptor().getAgentId()));
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.HEALTHY);
    }

    @Override
    @NotNull
    public Promise<Void> shutdown() {
        return Promise.complete();
    }
}
```

### 1.2 Registering Your Agent

```java
@Service
public class MyAgentRegistration {
    
    @Inject
    public void register(CatalogAgentDispatcher dispatcher) {
        // Tier-J registration: native Java implementation
        dispatcher.registerJavaAgent("echo", new EchoAgent());
    }
}
```

---

## 2. Agent Types Deep Dive

### 2.1 Deterministic Agent (DSLA Pattern)

```java
/**
 * Deterministic agent for policy validation.
 * Same input always produces same output.
 */
public class PolicyValidationAgent implements TypedAgent<PolicyRequest, PolicyDecision> {
    
    private final RuleEngine ruleEngine;
    
    public PolicyValidationAgent(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("policy-validator")
            .type(AgentType.DETERMINISTIC)
            .determinism(DeterminismGuarantee.FULL)
            .autonomyLevel(AutonomyLevel.AUTONOMOUS)
            .build();
    }
    
    @Override
    public Promise<AgentResult<PolicyDecision>> process(AgentContext ctx, PolicyRequest request) {
        Instant start = Instant.now();
        
        // 1. Canonicalize input (deterministic normalization)
        PolicyContext context = canonicalize(request);
        
        // 2. Match rules (deterministic ordering)
        List<RuleMatch> matches = ruleEngine.match(context);
        
        // 3. Apply invariants (hard gates)
        InvariantResult invariantCheck = checkInvariants(context, matches);
        if (!invariantCheck.isValid()) {
            return Promise.of(AgentResult.<PolicyDecision>builder()
                .status(AgentResultStatus.REJECTED)
                .explanation("Invariant violation: " + invariantCheck.getReason())
                .agentId(descriptor().getAgentId())
                .processingTime(Duration.between(start, Instant.now()))
                .build());
        }
        
        // 4. Deterministic decision
        PolicyDecision decision = evaluate(matches);
        
        return Promise.of(AgentResult.success(
            decision, 
            descriptor().getAgentId(),
            Duration.between(start, Instant.now())
        ));
    }
}
```

### 2.2 Probabilistic Agent (NDSLA Pattern)

```java
/**
 * Probabilistic agent for sentiment analysis using ML model.
 */
public class SentimentAnalysisAgent implements TypedAgent<TextDocument, SentimentResult> {
    
    private final MLModel sentimentModel;
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("sentiment-analyzer")
            .type(AgentType.PROBABILISTIC)
            .subtype(ProbabilisticSubtype.ML_MODEL.name())
            .determinism(DeterminismGuarantee.NONE)
            .autonomyLevel(AutonomyLevel.SEMI_AUTONOMOUS)
            .build();
    }
    
    @Override
    public Promise<AgentResult<SentimentResult>> process(AgentContext ctx, TextDocument input) {
        Instant start = Instant.now();
        
        // Model inference (non-deterministic due to model weights/sampling)
        ModelOutput output = sentimentModel.predict(input.getText());
        
        SentimentResult result = new SentimentResult(
            output.getLabel(),
            output.getConfidence()
        );
        
        return Promise.of(AgentResult.<SentimentResult>builder()
            .output(result)
            .confidence(output.getConfidence())
            .status(output.getConfidence() > 0.8 ? AgentResultStatus.SUCCESS : AgentResultStatus.UNCERTAIN)
            .explanation("ML model prediction with confidence " + output.getConfidence())
            .agentId(descriptor().getAgentId())
            .processingTime(Duration.between(start, Instant.now()))
            .build());
    }
}
```

### 2.3 Hybrid Agent (Two-Tier Pattern)

```java
/**
 * Hybrid agent: deterministic fast-path + probabilistic fallback.
 */
public class HybridClassificationAgent implements TypedAgent<Document, Classification> {
    
    private final TypedAgent<Document, Classification> fastPath;
    private final TypedAgent<Document, Classification> slowPath;
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("hybrid-classifier")
            .type(AgentType.HYBRID)
            .determinism(DeterminismGuarantee.CONFIG_SCOPED)
            .build();
    }
    
    @Override
    public Promise<AgentResult<Classification>> process(AgentContext ctx, Document input) {
        // Try deterministic fast path first
        return fastPath.process(ctx, input)
            .then(result -> {
                if (result.isSuccess() && result.getConfidence() > 0.9) {
                    // Fast path succeeded with high confidence
                    return Promise.of(result);
                }
                // Escalate to probabilistic slow path
                return slowPath.process(ctx, input);
            });
    }
}
```

### 2.4 Composite Agent (Ensemble Pattern)

```java
/**
 * Composite agent: fan-out to multiple sub-agents and aggregate.
 */
public class ConsensusValidationAgent implements TypedAgent<Request, ValidationResult> {
    
    private final List<TypedAgent<Request, ValidationResult>> validators;
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("consensus-validator")
            .type(AgentType.COMPOSITE)
            .build();
    }
    
    @Override
    public Promise<AgentResult<ValidationResult>> process(AgentContext ctx, Request input) {
        // Fan-out to all validators in parallel
        Promise<AgentResult<ValidationResult>>[] promises = validators.stream()
            .map(v -> v.process(ctx, input))
            .toArray(Promise[]::new);
        
        return Promises.toList(promises)
            .map(results -> {
                // Majority vote aggregation
                long validCount = results.stream()
                    .filter(r -> r.isSuccess())
                    .count();
                
                boolean isValid = validCount > validators.size() / 2;
                double confidence = (double) validCount / validators.size();
                
                return AgentResult.<ValidationResult>builder()
                    .output(new ValidationResult(isValid, confidence))
                    .confidence(confidence)
                    .status(isValid ? AgentResultStatus.SUCCESS : AgentResultStatus.REJECTED)
                    .agentId(descriptor().getAgentId())
                    .build();
            });
    }
}
```

---

## 3. Memory Integration

### 3.1 Working Memory

```java
public class ContextAwareAgent implements TypedAgent<Query, Response> {
    
    @Override
    public Promise<AgentResult<Response>> process(AgentContext ctx, Query query) {
        // Access working memory from context
        WorkingMemoryStore workingMemory = ctx.getWorkingMemory();
        
        // Retrieve recent context
        List<MemoryItem> recentContext = workingMemory.retrieve(
            MemoryQuery.builder()
                .maxItems(5)
                .recencyBias(Recency.HIGH)
                .build()
        );
        
        // Process with context
        Response response = processWithContext(query, recentContext);
        
        // Store interaction in working memory
        workingMemory.store(MemoryItem.builder()
            .content(response)
            .timestamp(Instant.now())
            .provenance(ctx.getTraceId())
            .build());
        
        return Promise.of(AgentResult.success(response, descriptor().getAgentId()));
    }
}
```

### 3.2 Episodic Memory

```java
public class LearningAgent implements TypedAgent<Event, Action> {
    
    private final EpisodicMemory episodicMemory;
    
    @Override
    public Promise<AgentResult<Action>> process(AgentContext ctx, Event event) {
        // Retrieve similar past episodes
        return episodicMemory.retrieveSimilar(event, 3)
            .map(similarEpisodes -> {
                // Learn from similar past experiences
                Action action = decideBasedOnHistory(event, similarEpisodes);
                
                // Record this episode
                recordEpisode(ctx, event, action);
                
                return AgentResult.success(action, descriptor().getAgentId());
            });
    }
    
    private void recordEpisode(AgentContext ctx, Event event, Action action) {
        Episode episode = Episode.builder()
            .id(ctx.getTraceId())
            .context(event)
            .action(action)
            .outcome(ctx.getOutcome())
            .timestamp(Instant.now())
            .build();
        
        episodicMemory.store(episode);
    }
}
```

### 3.3 Procedural Memory (Skill Retrieval)

```java
public class SkillBasedAgent implements TypedAgent<Task, TaskResult> {
    
    private final ProceduralMemory proceduralMemory;
    
    @Override
    public Promise<AgentResult<TaskResult>> process(AgentContext ctx, Task task) {
        // Look up relevant procedures/skills
        return proceduralMemory.findProcedures(task.getType())
            .map(procedures -> {
                if (procedures.isEmpty()) {
                    // No skill available - general reasoning
                    return handleNovelTask(task);
                }
                
                // Execute best matching procedure
                Procedure bestMatch = procedures.get(0);
                TaskResult result = bestMatch.execute(task);
                
                return AgentResult.success(result, descriptor().getAgentId());
            });
    }
}
```

---

## 4. Learning and Adaptation

### 4.1 Learning Loop Integration (L2+ Agents)

```java
/**
 * L2 agent with retrieval policy learning.
 */
public class AdaptiveRetrievalAgent implements TypedAgent<Query, Result> {
    
    private final LearningManager learningManager;
    private RetrievalStrategy currentStrategy;
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("adaptive-retriever")
            .type(AgentType.ADAPTIVE)
            .learningModel(LearningModel.builder()
                .learningLevel(LearningLevel.L2)
                .adaptationTargets(List.of("retrieval-ranking", "confidence-thresholds"))
                .build())
            .build();
    }
    
    @Override
    public Promise<AgentResult<Result>> process(AgentContext ctx, Query query) {
        // Use current learned strategy
        RetrievalResult result = currentStrategy.retrieve(query);
        
        // Record outcome for learning
        learningManager.recordOutcome(ctx.getTraceId(), result.isSuccessful());
        
        return Promise.of(AgentResult.success(result.getData(), descriptor().getAgentId()));
    }
    
    // Called by learning system periodically
    public void onPolicyUpdate(RetrievalStrategy newStrategy) {
        this.currentStrategy = newStrategy;
    }
}
```

### 4.2 Skill Induction (L3)

```java
/**
 * Agent that can induce skills from successful traces.
 */
public class SkillInductionAgent extends AbstractTypedAgent<Problem, Solution> {
    
    private final SkillInductor skillInductor;
    
    @Override
    public Promise<AgentResult<Solution>> process(AgentContext ctx, Problem problem) {
        return executeWithLearning(ctx, problem)
            .then(result -> {
                if (result.isSuccess()) {
                    // Trigger skill extraction on repeated success
                    skillInductor.considerForExtraction(
                        ctx.getTraceId(),
                        problem,
                        result.getOutput(),
                        result.getExecutionTrace()
                    );
                }
                return Promise.of(result);
            });
    }
}
```

---

## 5. Safety and Governance

### 5.1 Invariant Checking

```java
public class GovernedAgent extends AbstractTypedAgent<Request, Response> {
    
    private final InvariantChecker invariantChecker;
    
    @Override
    public Promise<AgentResult<Response>> process(AgentContext ctx, Request request) {
        // Pre-execution invariants
        List<Invariant> preconditions = List.of(
            new RateLimitInvariant(ctx.getTenantId(), 100),
            new AuthorizationInvariant(ctx.getUserId(), request.getResource()),
            new SchemaInvariant(request)
        );
        
        InvariantResult check = invariantChecker.check(preconditions);
        if (!check.isValid()) {
            return Promise.of(AgentResult.<Response>builder()
                .status(AgentResultStatus.DENIED)
                .explanation("Invariant violation: " + check.getViolations())
                .agentId(descriptor().getAgentId())
                .build());
        }
        
        // Execute with post-condition checking
        return execute(ctx, request)
            .map(result -> {
                List<Invariant> postconditions = List.of(
                    new OutputSchemaInvariant(result),
                    new SensitiveDataInvariant(result)
                );
                
                InvariantResult postCheck = invariantChecker.check(postconditions);
                if (!postCheck.isValid()) {
                    // Rollback if needed
                    rollback(request);
                    return AgentResult.<Response>builder()
                        .status(AgentResultStatus.FAILED)
                        .explanation("Post-condition violation: " + postCheck.getViolations())
                        .agentId(descriptor().getAgentId())
                        .build();
                }
                
                return result;
            });
    }
}
```

### 5.2 Human-in-the-Loop Integration

```java
public class HITLAgent extends AbstractTypedAgent<CriticalRequest, CriticalResponse> {
    
    private final HitlService hitlService;
    
    @Override
    public Promise<AgentResult<CriticalResponse>> process(AgentContext ctx, CriticalRequest request) {
        // Check if this requires human approval
        if (requiresApproval(request)) {
            // Submit for human review
            return hitlService.submitForApproval(ctx, request)
                .then(approval -> {
                    if (approval.isApproved()) {
                        return executeWithAuditTrail(ctx, request, approval);
                    } else {
                        return Promise.of(AgentResult.<CriticalResponse>builder()
                            .status(AgentResultStatus.REJECTED)
                            .explanation("Rejected by human reviewer: " + approval.getReason())
                            .agentId(descriptor().getAgentId())
                            .build());
                    }
                });
        }
        
        // Auto-execute low-risk requests
        return execute(ctx, request);
    }
}
```

---

## 6. Testing Agents

### 6.1 Unit Testing

```java
@DisplayName("EchoAgent Tests")
class EchoAgentTest extends EventloopTestBase {
    
    private EchoAgent agent;
    
    @BeforeEach
    void setUp() {
        agent = new EchoAgent();
    }
    
    @Test
    void shouldEchoInput() {
        AgentContext ctx = createTestContext();
        
        AgentResult<String> result = runPromise(() -> 
            agent.process(ctx, "hello")
        );
        
        assertThat(result.getOutput()).isEqualTo("hello");
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
    }
    
    @Test
    void shouldBeDeterministic() {
        // Determinism test: same input → same output
        AgentContext ctx = createTestContext();
        
        AgentResult<String> result1 = runPromise(() -> agent.process(ctx, "test"));
        AgentResult<String> result2 = runPromise(() -> agent.process(ctx, "test"));
        
        assertThat(result1.getOutput()).isEqualTo(result2.getOutput());
    }
}
```

### 6.2 Integration Testing with AEP

```java
@DisplayName("Agent Pipeline Integration")
class AgentPipelineTest extends AepIntegrationTestBase {
    
    @Test
    void shouldExecuteAgentThroughPipeline() {
        // Register agent
        dispatcher.registerJavaAgent("test-validator", new ValidationAgent());
        
        // Create pipeline using the agent
        Pipeline pipeline = Pipeline.builder()
            .addStep(AgentStep.of("test-validator"))
            .build();
        
        // Execute
        PipelineResult result = runPromise(() -> 
            pipelineEngine.execute(pipeline, testEvent)
        );
        
        assertThat(result.isSuccess()).isTrue();
    }
}
```

---

## 7. Best Practices

### 7.1 Do's

✅ **Do implement proper lifecycle methods**
- `initialize()` for one-time setup
- `shutdown()` for resource cleanup
- `healthCheck()` for operational visibility

✅ **Do use structured results**
- Always return `AgentResult` with status
- Include confidence scores for probabilistic agents
- Provide explanations for rejections/failures

✅ **Do handle errors gracefully**
- Never throw raw exceptions from `process()`
- Use `AgentResultStatus` for error categorization
- Include actionable error messages

✅ **Do implement proper determinism guarantees**
- Be honest about determinism level in descriptor
- Use `DeterminismGuarantee` accurately
- Document any non-deterministic dependencies

✅ **Do respect tenant isolation**
- Always use `ctx.getTenantId()` for tenant-scoped operations
- Never share state across tenants
- Use tenant-aware memory stores

### 7.2 Don'ts

❌ **Don't block the event loop**
- All I/O must be async via `Promise`
- Use `Promise.ofBlocking()` for blocking operations
- Never call `.getResult()` in production code

❌ **Don't use mutable static state**
- Agent instances may be shared
- Keep state in instance fields initialized in `initialize()`
- Use context for per-request state

❌ **Don't ignore confidence**
- Probabilistic agents must set confidence
- Use `UNCERTAIN` status for low-confidence results
- Consider HITL for borderline cases

❌ **Don't skip invariant checking**
- All externally committing actions need verification
- Check preconditions before execution
- Validate postconditions after execution

---

## 8. Migration from Legacy Agent

### 8.1 Step-by-Step Migration

```java
// BEFORE: Legacy Agent
public class OldAgent implements Agent {
    public Event process(Event input) {
        // Process and return
        return new Event(transform(input));
    }
}

// AFTER: Modern TypedAgent
public class NewAgent implements TypedAgent<SpecificInput, SpecificOutput> {
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("migrated-agent")
            .type(AgentType.DETERMINISTIC)
            .build();
    }
    
    @Override
    public Promise<AgentResult<SpecificOutput>> process(AgentContext ctx, SpecificInput input) {
        // Transform input
        SpecificOutput output = transform(input);
        
        // Return structured result
        return Promise.of(AgentResult.success(output, descriptor().getAgentId()));
    }
}
```

### 8.2 Migration Checklist

- [ ] Add generic type parameters `<I, O>`
- [ ] Implement `AgentDescriptor descriptor()`
- [ ] Change return type to `Promise<AgentResult<O>>`
- [ ] Add `AgentContext ctx` parameter
- [ ] Implement lifecycle methods (`initialize`, `shutdown`, `healthCheck`)
- [ ] Update registration to use `CatalogAgentDispatcher`
- [ ] Add `@doc.*` tags for documentation
- [ ] Write tests extending `EventloopTestBase`

---

## 9. Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `ClassCastException` | Type mismatch in generics | Verify `<I,O>` match actual types |
| Event loop blocking | Synchronous I/O in `process()` | Use `Promise.ofBlocking()` |
| Agent not found | Not registered with dispatcher | Call `dispatcher.registerJavaAgent()` |
| Tests hanging | `getResult()` in test | Use `runPromise(() -> ...)` |
| Confidence always 1.0 | Probabilistic agent not setting | Set `confidence` in `AgentResult` builder |

### Debugging Tips

1. **Enable trace logging**
   ```java
   // In test or dev
   ctx.setConfig("__traceEnabled", true);
   ```

2. **Check agent descriptor**
   ```java
   // Verify registration
   AgentDescriptor desc = agent.descriptor();
   log.info("Registered: {} ({})", desc.getAgentId(), desc.getType());
   ```

3. **Monitor promises**
   ```java
   return agent.process(ctx, input)
       .whenResult(r -> log.debug("Success: {}", r))
       .whenException(e -> log.error("Failed: {}", e));
   ```

---

## 10. References

- [Master Index](./README.md) - Navigation hub
- [Unified Self-Learning Agents Spec](./Unified_Self_Learning_Agents_Spec_Final.md) - Theory
- [AEP Integration Architecture](./AEP_Integration_Architecture.md) - Product integration
- [agent-spec.md](./agent-spec.md) - YAML contract reference
- [platform/java/agent-core](../../../platform/java/agent-core/) - Source code

---

**Next:** Read [AEP Integration Architecture](./AEP_Integration_Architecture.md) for deploying agents in AEP pipelines.
