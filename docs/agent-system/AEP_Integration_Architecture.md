# AEP Integration Architecture

**Version:** 2.0.0  
**Last Updated:** 2026-04-05  
**Scope:** Integration patterns between the Agent Framework and AEP (Agentic Event Processor)

---

## 1. Executive Overview

This document describes how the **Agent Framework** (`platform/java/agent-core`) integrates with **AEP** (`products/aep`) to create a production-grade agentic event processing system.

### Key Integration Points

| Component | Agent Framework | AEP Integration |
|-----------|----------------|-----------------|
| **Runtime** | `TypedAgent<I,O>` interface | `aep-agent-runtime` execution |
| **Registry** | `AgentDescriptor` metadata | `aep-registry` catalog |
| **Pipeline** | `AgentOperator` wrapper | `aep-engine` pipeline steps |
| **Dispatch** | Type-based routing | 3-tier execution (J/S/L) |
| **Safety** | Invariant checking | `GovernedAgentDispatcher` |
| **Learning** | Learning levels (L0-L5) | `aep-analytics` feedback loop |

---

## 2. Architectural Model

### 2.1 Three-Plane Architecture

AEP implements the **three-plane architecture** from the DSLA/NDSLA specifications:

```
┌─────────────────────────────────────────────────────────────────────┐
│ CONTROL PLANE (AEP Server + Registry)                               │
│ ├─ Agent catalog management                                         │
│ ├─ Pipeline definition & deployment                                 │
│ ├─ Policy governance                                                │
│ └─ Human-in-the-loop (HITL) workflows                               │
├─────────────────────────────────────────────────────────────────────┤
│ EXECUTION PLANE (AEP Engine + Orchestrator)                         │
│ ├─ Event intake & routing                                           │
│ ├─ Pipeline DAG execution                                           │
│ ├─ Agent invocation (3-tier dispatch)                               │
│ ├─ Checkpoint & recovery                                            │
│ └─ Backpressure & scaling                                           │
├─────────────────────────────────────────────────────────────────────┤
│ DATA & LEARNING PLANE (Event Cloud + Data Cloud)                    │
│ ├─ Event log (immutable)                                            │
│ ├─ Run ledger (execution traces)                                    │
│ ├─ Agent memory (episodic, semantic, procedural)                    │
│ ├─ Learning records (episodes, evaluations, policies)               │
│ └─ Audit & compliance                                               │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Agent Execution Flow

```
Event Ingestion
      │
      ▼
┌─────────────┐
│   Pattern   │◄─────────────────┐
│   Matching  │                  │
└──────┬──────┘                  │
       │                         │
       ▼                         │
┌─────────────┐     ┌──────────┐ │
│   Pipeline  │────►│  Agent   │─┘
│   Trigger   │     │  Step    │
└──────┬──────┘     └────┬─────┘
       │                 │
       ▼                 ▼
┌─────────────┐     ┌─────────────┐
│  Execution  │◄────│  3-Tier     │
│   Record    │     │  Dispatch   │
└──────┬──────┘     └──────┬──────┘
       │                   │
       │    ┌──────────────┼──────────────┐
       │    │              │              │
       │    ▼              ▼              ▼
       │ ┌──────┐    ┌──────────┐   ┌──────────┐
       └►│ Tier-J │    │ Tier-S   │   │ Tier-L   │
         │(Java)  │    │(Service) │   │ (LLM)    │
         └───────┘    └──────────┘   └──────────┘
```

---

## 3. Three-Tier Dispatch System

### 3.1 Tier-J: Java-Implemented Agents

**Resolution Order:** 1st (highest priority)

**Use Case:** Native Java agents with full framework integration.

**Implementation:**

```java
// Agent implementation
public class FraudDetectionAgent implements TypedAgent<Transaction, RiskScore> {
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("fraud-detector")
            .type(AgentType.HYBRID)
            .build();
    }
    
    @Override
    public Promise<AgentResult<RiskScore>> process(AgentContext ctx, Transaction txn) {
        // Implementation
    }
}

// Registration in AEP
@Service
public class AepAgentModule {
    @Inject
    public void registerAgents(CatalogAgentDispatcher dispatcher) {
        dispatcher.registerJavaAgent("fraud-detector", new FraudDetectionAgent());
    }
}
```

**Advantages:**
- Full type safety
- Deterministic execution (if designed)
- Best performance
- Deep framework integration

### 3.2 Tier-S: Service-Orchestrated Agents

**Resolution Order:** 2nd

**Use Case:** Pipeline-based agents with delegation chains.

**Catalog Definition:**

```yaml
# In agent catalog
agents:
  - id: enrichment-agent
    generator:
      type: PIPELINE
      steps:
        - type: TRANSFORM
          config:
            mapping: standardize_fields
        - type: ENRICH
          config:
            source: customer_db
        - type: VALIDATE
          config:
            schema: enriched_event
    delegation:
      can_delegate_to:
        - fraud-detector  # Tier-J agent
        - risk-calculator # Another Tier-S agent
```

**Execution:**

```java
// Pipeline execution with delegation
ServiceOrchestrationPlan orchestration = new ServiceOrchestrationPlan();

orchestration.execute(
    catalogEntry,      // Pipeline definition
    input,             // Input event
    ctx,               // Execution context
    dispatcher         // For sub-agent dispatch
);
```

**Advantages:**
- Declarative definition
- Composable from existing agents
- Visual pipeline builder support
- No code deployment required

### 3.3 Tier-L: LLM-Executed Agents

**Resolution Order:** 3rd

**Use Case:** Prompt-based LLM execution.

**Catalog Definition:**

```yaml
agents:
  - id: sentiment-analyzer
    generator:
      type: LLM
      model: gpt-4
      prompt_template: |
        Analyze the sentiment of the following text.
        Text: {{input.text}}
        
        Return JSON:
        {
          "sentiment": "positive|negative|neutral",
          "confidence": 0.0-1.0
        }
      output_schema: sentiment_result
```

**Execution:**

```java
LlmExecutionPlan llmPlan = new LlmExecutionPlan(llmProvider);

llmPlan.execute(
    catalogEntry,   // LLM agent definition
    input,          // Input with template variables
    ctx             // Execution context
);
```

**Advantages:**
- No code required
- Natural language capabilities
- Quick iteration
- Flexible output schemas

---

## 4. Agent → Pipeline Bridge

### 4.1 AgentOperatorFactory

The `AgentOperatorFactory` bridges agents with the AEP pipeline engine:

```java
public class AgentOperatorFactory {
    
    public OperatorTree createOperatorTree(TypedAgent<?, ?> agent, AgentConfig config) {
        AgentType type = agent.descriptor().getType();
        
        return switch (type) {
            case DETERMINISTIC -> createDeterministicTree(agent, config);
            case PROBABILISTIC -> createProbabilisticTree(agent, config);
            case STREAM_PROCESSOR -> createStreamProcessorTree(agent, config);
            case PLANNING -> createPlanningTree(agent, config);
            case HYBRID -> createHybridTree(agent, config);
            // ... etc
        };
    }
}
```

### 4.2 Operator Tree Structure

```java
// Single agent as operator
OperatorTree tree = OperatorTree.builder()
    .name("fraud-detection-tree")
    .operators(List.of(
        AgentOperator.builder()
            .name("fraud-detector")
            .agentType(AgentType.HYBRID)
            .agent(fraudDetectionAgent)
            .preProcessor(input -> normalize(input))
            .postProcessor(result -> formatForDownstream(result))
            .build()
    ))
    .build();

// Execution
Promise<AgentResult<Map<String, Object>>> result = tree.execute(ctx, input);
```

### 4.3 Pipeline Integration

```java
// Create pipeline with agent steps
Pipeline pipeline = Pipeline.builder()
    .step(TransformStep.of("normalize"))
    .step(AgentStep.of("fraud-detector"))  // Calls agent via dispatcher
    .step(ConditionalStep.of(
        condition -> condition.get("risk_score") > 0.8,
        AgentStep.of("alert-generator"),
        AgentStep.of("audit-logger")
    ))
    .build();

// Execute
PipelineExecutionResult result = pipelineEngine.execute(pipeline, event);
```

---

## 5. Safety and Governance Integration

### 5.1 GovernedAgentDispatcher

Wraps the dispatcher with safety checks:

```java
public class GovernedAgentDispatcher implements AgentDispatcher {
    
    private final AgentDispatcher delegate;
    private final InvariantChecker invariantChecker;
    private final TraceLedger traceLedger;
    
    @Override
    public <I, O> Promise<AgentResult<O>> dispatch(
            String agentId, I input, AgentContext ctx) {
        
        // Build invariant context
        InvariantContext invCtx = InvariantContext.builder()
            .agentId(agentId)
            .tenantId(ctx.getTenantId())
            .costUsd(extractCost(ctx))
            .depth(extractDelegationDepth(ctx))
            .build();
        
        // Check invariants before dispatch
        List<InvariantViolation> violations = invariantChecker.check(invCtx);
        
        if (hasCriticalViolations(violations)) {
            // Record denial
            traceLedger.append(TraceEvent.builder()
                .type(TraceEventType.ACTION_DENIED)
                .explanation("Invariant violation: " + violations)
                .build());
            
            return Promise.of(AgentResult.<O>builder()
                .status(AgentResultStatus.DENIED)
                .explanation("Dispatch blocked by invariant violation")
                .build());
        }
        
        // Proceed with dispatch
        return delegate.dispatch(agentId, input, ctx)
            .whenResult(result -> recordCompletion(result));
    }
}
```

### 5.2 Invariant Types

| Invariant | Check | Action on Violation |
|-----------|-------|---------------------|
| Rate Limit | Calls per minute per tenant | Reject with `TOO_MANY_REQUESTS` |
| Cost Budget | Accumulated cost per trace | Reject if exceeds cap |
| Delegation Depth | Nested agent calls | Reject if exceeds max depth |
| Authorization | User permissions | Reject if unauthorized |
| Schema | Input/output format | Reject if invalid |
| Policy | Business rules | Reject if violated |

### 5.3 HITL Integration

```java
// Automatic HITL escalation based on agent properties
public class HitlEscalationRouter {
    
    public boolean requiresHumanApproval(AgentDescriptor descriptor, AgentResult<?> result) {
        // Criticality check
        if (descriptor.getCriticality() == Criticality.MISSION_CRITICAL) {
            return true;
        }
        
        // Confidence check
        if (result.getConfidence() < descriptor.getConfidenceThreshold()) {
            return true;
        }
        
        // Risk check
        RiskProfile risk = descriptor.getRiskProfile();
        if (risk.getImpactLevel() == ImpactLevel.HIGH && result.isUncertain()) {
            return true;
        }
        
        return false;
    }
}
```

---

## 6. Memory and Learning Integration

### 6.1 Agent Memory in AEP

```java
public class AepAgentContext implements AgentContext {
    
    private final WorkingMemory workingMemory;
    private final EpisodicMemoryClient episodicMemory;
    private final SemanticMemoryClient semanticMemory;
    
    @Override
    public Promise<List<MemoryItem>> retrieveRelevant(Object query, int maxItems) {
        // Hybrid retrieval: working + episodic + semantic
        List<MemoryItem> working = workingMemory.retrieveRecent(maxItems / 3);
        
        return episodicMemory.retrieveSimilar(query, maxItems / 3)
            .then(episodes -> 
                semanticMemory.retrieveRelevant(query, maxItems / 3)
                    .map(semantic -> combineAndRank(working, episodes, semantic))
            );
    }
}
```

### 6.2 Learning Loop

```
┌─────────────────────────────────────────────────────────────┐
│ AEP LEARNING LOOP                                           │
├─────────────────────────────────────────────────────────────┤
│ 1. EPISODE COLLECTION                                       │
│    └─ Every agent execution records:                      │
│       • Input context                                       │
│       • Action taken                                        │
│       • Outcome achieved                                    │
│       • Confidence level                                    │
│                                                             │
│ 2. OUTCOME EVALUATION                                       │
│    └─ Compare actual vs expected outcome                    │
│       • Success/failure classification                      │
│       • Metric extraction                                   │
│       • Quality scoring                                     │
│                                                             │
│ 3. SKILL EXTRACTION (L3+)                                   │
│    └─ Mine successful traces for patterns                   │
│       • Procedure candidates                                │
│       • Rule induction                                      │
│       • Retrieval policy updates                            │
│                                                             │
│ 4. PROMOTION PIPELINE                                       │
│    └─ Governance-controlled promotion:                      │
│       • Validation against test suite                       │
│       • Invariant checking                                  │
│       • Human review (if required)                          │
│       • Canary deployment                                   │
│                                                             │
│ 5. POLICY ACTIVATION                                        │
│    └─ Deploy to production:                                 │
│       • Version tagging                                     │
│       • Rollback capability                                 │
│       • Drift monitoring                                    │
└─────────────────────────────────────────────────────────────┘
```

### 6.3 Promotion Pipeline Implementation

```java
@Service
public class LearningPromotionService {
    
    public Promise<PromotionResult> promoteSkill(SkillCandidate candidate) {
        // Step 1: Validation
        return validationService.validate(candidate)
            .then(validation -> {
                if (!validation.isValid()) {
                    return Promise.of(PromotionResult.rejected(validation.getErrors()));
                }
                
                // Step 2: Regression testing
                return regressionTestService.test(candidate)
                    .then(testResults -> {
                        if (!testResults.isPassing()) {
                            return Promise.of(PromotionResult.rejected("Regression tests failed"));
                        }
                        
                        // Step 3: Human review if required
                        if (candidate.requiresHumanReview()) {
                            return hitlService.submitForReview(candidate)
                                .then(approval -> {
                                    if (!approval.isApproved()) {
                                        return Promise.of(PromotionResult.rejected("Human review rejected"));
                                    }
                                    return activateSkill(candidate);
                                });
                        }
                        
                        return activateSkill(candidate);
                    });
            });
    }
    
    private Promise<PromotionResult> activateSkill(SkillCandidate candidate) {
        // Version and activate
        SkillVersion version = skillRegistry.register(candidate);
        skillActivator.activate(version);
        
        // Enable rollback
        rollbackRegistry.register(version);
        
        return Promise.of(PromotionResult.promoted(version));
    }
}
```

---

## 7. Deployment Patterns

### 7.1 Single-Tenant Agent Deployment

```yaml
# aep-deployment.yaml
deployment:
  name: fraud-detection-pipeline
  tenant: acme-corp
  
  agents:
    - id: fraud-detector
      type: java
      class: com.ghatana.fraud.FraudDetectionAgent
      replicas: 3
      resources:
        memory: 512Mi
        cpu: 500m
        
    - id: alert-generator
      type: pipeline
      definition: alert-pipeline.yaml
      
  pipelines:
    - name: transaction-processing
      trigger:
        source: kafka
        topic: transactions
      steps:
        - agent: fraud-detector
        - condition:
            if: risk_score > 0.8
            then: alert-generator
```

### 7.2 Multi-Tenant Agent Isolation

```java
@Service
public class TenantIsolatedAgentRegistry {
    
    private final Map<TenantId, CatalogRegistry> tenantRegistries;
    
    public TypedAgent<?, ?> resolveAgent(TenantId tenantId, String agentId) {
        // Tenant-scoped lookup
        CatalogRegistry registry = tenantRegistries.get(tenantId);
        
        // Tenant-specific configuration
        AgentConfig config = loadTenantConfig(tenantId, agentId);
        
        // Create tenant-isolated instance
        return registry.resolve(agentId, config);
    }
    
    private AgentConfig loadTenantConfig(TenantId tenantId, String agentId) {
        // Load tenant-specific overrides
        // - Custom thresholds
        // - Tenant-specific models
        // - Policy variations
        return configStore.get(tenantId, agentId);
    }
}
```

### 7.3 Canary Agent Deployment

```java
@Service
public class CanaryAgentDeployment {
    
    public Promise<AgentResult<?>> routeWithCanary(
            TenantId tenantId,
            String agentId,
            Object input,
            AgentContext ctx) {
        
        DeploymentState state = deploymentRegistry.getState(agentId);
        
        return switch (state.getDeploymentMode()) {
            case FULL_ROLLOUT -> 
                dispatchToVersion(tenantId, agentId, "v2", input, ctx);
                
            case CANARY -> {
                // Route percentage to new version
                if (shouldUseCanary(tenantId, state.getCanaryPercentage())) {
                    Promise<AgentResult<?>> canaryResult = 
                        dispatchToVersion(tenantId, agentId, "v2", input, ctx);
                    
                    // Also call stable version for comparison
                    Promise<AgentResult<?>> stableResult = 
                        dispatchToVersion(tenantId, agentId, "v1", input, ctx);
                    
                    // Record comparison for analysis
                    recordCanaryComparison(canaryResult, stableResult);
                    
                    yield canaryResult;
                } else {
                    yield dispatchToVersion(tenantId, agentId, "v1", input, ctx);
                }
            }
            
            case SHADOW -> {
                // Call stable version
                Promise<AgentResult<?>> stableResult = 
                    dispatchToVersion(tenantId, agentId, "v1", input, ctx);
                
                // Shadow call new version (don't block on result)
                dispatchToVersion(tenantId, agentId, "v2", input, ctx)
                    .whenResult(shadowResult -> 
                        recordShadowComparison(input, stableResult.getResult(), shadowResult)
                    );
                
                yield stableResult;
            }
        };
    }
}
```

---

## 8. Observability

### 8.1 Agent Metrics

```java
public class AgentMetrics {
    
    // Execution metrics
    private final Counter executionCounter;
    private final Histogram executionLatency;
    private final Gauge activeExecutions;
    
    // Quality metrics
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Histogram confidenceDistribution;
    
    // Learning metrics
    private final Counter promotionCounter;
    private final Counter rollbackCounter;
    private final Gauge skillCount;
    
    public void recordExecution(AgentResult<?> result, Duration latency) {
        executionCounter.increment();
        executionLatency.record(latency.toMillis());
        
        if (result.isSuccess()) {
            successCounter.increment();
        } else {
            failureCounter.increment();
        }
        
        confidenceDistribution.record(result.getConfidence());
    }
}
```

### 8.2 Distributed Tracing

```java
public class TracedAgent implements TypedAgent<I, O> {
    
    private final TypedAgent<I, O> delegate;
    private final Tracer tracer;
    
    @Override
    public Promise<AgentResult<O>> process(AgentContext ctx, I input) {
        Span span = tracer.spanBuilder("agent.process")
            .setAttribute("agent.id", delegate.descriptor().getAgentId())
            .setAttribute("agent.type", delegate.descriptor().getType().name())
            .setAttribute("tenant.id", ctx.getTenantId())
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            return delegate.process(ctx, input)
                .whenResult(result -> {
                    span.setAttribute("result.status", result.getStatus().name());
                    span.setAttribute("result.confidence", result.getConfidence());
                    span.setStatus(StatusCode.OK);
                })
                .whenException(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .whenComplete((r, e) -> span.end());
        }
    }
}
```

---

## 9. Configuration Reference

### 9.1 Agent Runtime Configuration

```yaml
aep:
  agent:
    runtime:
      # Dispatch configuration
      dispatch:
        tier_j_enabled: true
        tier_s_enabled: true
        tier_l_enabled: true
        default_timeout_ms: 30000
        
      # Safety configuration
      safety:
        max_delegation_depth: 5
        max_cost_usd_per_trace: 10.0
        rate_limit_per_tenant: 1000  # per minute
        
      # Learning configuration
      learning:
        enabled: true
        default_learning_level: L2
        auto_promotion_threshold: 0.95
        
      # Memory configuration
      memory:
        working_memory_ttl_seconds: 300
        episodic_retrieval_count: 5
        semantic_similarity_threshold: 0.8
```

### 9.2 Agent Catalog Entry

```yaml
agentSpecVersion: "1.0.0"

metadata:
  id: fraud-detector-v2
  name: Fraud Detection Agent v2
  version: 2.1.0
  status: active
  
identity:
  agentType: hybrid
  subtype: ml-rule-combo
  determinismGuarantee: config-scoped
  autonomyLevel: autonomous
  criticality: high
  
executionModel:
  invocationModes: [event, request]
  lifecycleStates: [created, ready, running, completed, failed]
  timeoutPolicy:
    softTimeoutMs: 5000
    hardTimeoutMs: 15000
  retryPolicy:
    enabled: true
    maxAttempts: 3
    
memoryModel:
  memoryTypes: [working, episodic, semantic]
  readStrategies: [exact-key, hybrid, policy-filtered]
  writePolicies:
    allowCreate: true
    allowUpdate: true
    allowDelete: false
    requiresProvenance: true
    
learningModel:
  learningLevel: L3
  adaptationTargets: [threshold-tuning, rule-weights]
  learningSources: [run-traces, human-feedback]
  driftControls:
    enabled: true
    monitors: [precision-drift, recall-drift]
    
governance:
  policyRefs:
    - policy.fraud-detection.v2
    - policy.ml-model-governance.v1
  approvals:
    requiredFor: [high-value-transaction, cross-border]
    approvers: [fraud-analyst, compliance-officer]
```

---

## 10. Migration Scenarios

### 10.1 From Standalone to AEP-Integrated

**Before:** Standalone agent service
```java
@RestController
public class StandaloneAgentController {
    @PostMapping("/analyze")
    public Response analyze(@RequestBody Input input) {
        return agent.process(input);
    }
}
```

**After:** AEP-integrated agent
```java
// Implement TypedAgent
public class AepIntegratedAgent implements TypedAgent<Input, Output> {
    @Override
    public Promise<AgentResult<Output>> process(AgentContext ctx, Input input) {
        // Same logic, wrapped in Promise
    }
}

// Register with AEP
@Service
public class AgentRegistration {
    @Inject
    public void register(CatalogAgentDispatcher dispatcher) {
        dispatcher.registerJavaAgent("my-agent", new AepIntegratedAgent());
    }
}

// Define in catalog
// Now accessible through AEP pipelines, HITL, learning loop, etc.
```

### 10.2 From Simple Pipeline to Learning-Enabled

**Step 1:** Add learning annotations
```java
public class MyAgent implements TypedAgent<I, O> {
    
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("learning-agent")
            .learningModel(LearningModel.builder()
                .learningLevel(LearningLevel.L2)
                .adaptationTargets(List.of("retrieval-policy"))
                .build())
            .build();
    }
}
```

**Step 2:** Add learning hooks
```java
@Override
public Promise<AgentResult<O>> process(AgentContext ctx, I input) {
    // Before: retrieve with current policy
    return learningService.getCurrentPolicy(ctx.getTenantId())
        .then(policy -> {
            List<MemoryItem> memories = policy.retrieveRelevant(input);
            O output = processWithMemories(input, memories);
            
            // After: record outcome
            learningService.recordOutcome(ctx.getTraceId(), output);
            
            return Promise.of(AgentResult.success(output, descriptor().getAgentId()));
        });
}
```

**Step 3:** Configure learning in catalog
```yaml
learningModel:
  learningLevel: L2
  learningSources: [run-traces]
  driftControls:
    enabled: true
    monitors: [retrieval-quality-drift]
```

---

## 11. References

- [Master Index](./README.md) - Documentation navigation
- [Agent Implementation Guide](./Agent_Implementation_Guide.md) - Building agents
- [Unified Self-Learning Agents Spec](./Unified_Self_Learning_Agents_Spec_Final.md) - Theory
- [agent-spec.md](./agent-spec.md) - YAML contract reference
- [AEP World-Class Report](../../products/aep/docs/AEP_WORLD_CLASS_AGENTIC_EVENT_PROCESSING_REPORT_2026-03-23.md)
- [AEP Operational Runbook](../../products/aep/docs/OPERATIONAL_RUNBOOK.md)

---

**Next:** Review [Unified_Self_Learning_Agents_Spec_Final.md](./Unified_Self_Learning_Agents_Spec_Final.md) for theoretical foundations.
