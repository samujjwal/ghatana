# YAPPC Lifecycle Platform

**Version:** 1.0.0  
**Status:** Production-Ready Foundation

## Overview

YAPPC (Yet Another Platform Product Creator) is a comprehensive, AI-native product development lifecycle system implementing 8 phases:

```
Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve
```

## Architecture

### Core Principles

1. **AI-Native**: Every phase leverages AI assistance through `libs/java/ai-integration`
2. **ActiveJ Promise-Based**: All async operations use `Promise<T>` for composability
3. **Immutable Domain Models**: Java records with builder patterns
4. **Event-Driven**: All phase completions emit events to `data-cloud/event`
5. **Multi-Tenant**: Full tenant isolation with `tenantId` in all specs
6. **Observable**: Comprehensive metrics and audit trails
7. **Pluggable**: Extensible via `libs/java/plugin-framework`

### Module Structure

```
products/yappc/lifecycle/
├── src/main/java/com/ghatana/yappc/
│   ├── domain/              # Domain models (60+ records)
│   │   ├── intent/          # Intent phase models
│   │   ├── shape/           # Shape phase models
│   │   ├── validate/        # Validation models
│   │   ├── generate/        # Generation models
│   │   ├── run/             # Run phase models
│   │   ├── observe/         # Observation models
│   │   ├── learn/           # Learning models
│   │   └── evolve/          # Evolution models
│   ├── services/            # Service implementations (8 phases)
│   │   ├── intent/          # IntentServiceImpl
│   │   ├── shape/           # ShapeServiceImpl
│   │   ├── validate/        # ValidationServiceImpl
│   │   ├── generate/        # GenerationServiceImpl
│   │   ├── run/             # RunServiceImpl
│   │   ├── observe/         # ObserveServiceImpl
│   │   ├── learn/           # LearningServiceImpl
│   │   └── evolve/          # EvolutionServiceImpl
│   ├── operators/           # Phase operators
│   │   └── PhaseOperator.java
│   └── storage/             # Data-cloud integration
│       ├── YappcArtifactRepository.java
│       └── PhaseEventPublisher.java
└── src/main/resources/
    └── pipelines/           # YAML pipeline definitions
        ├── yappc-full-lifecycle.yaml
        ├── yappc-intent-to-generate.yaml
        └── yappc-observe-learn-evolve.yaml
```

## Dependencies

### Ghatana Modules

- `libs/java/ai-integration` - LLM, embeddings, structured output
- `libs/java/observability` - Metrics, traces, logs
- `libs/java/audit` - Audit logging
- `libs/java/security` - Security validation
- `libs/java/governance` - Policy enforcement
- `libs/java/plugin-framework` - Extensibility
- `libs/java/operator` - UnifiedOperator base
- `products/data-cloud/core` - Artifact storage
- `products/data-cloud/event` - Event streaming
- `products/agentic-event-processor` - Pipeline execution

### External Dependencies

- ActiveJ 6.0-beta2 (Promise, Eventloop, Inject)
- Protobuf 3.25.1
- Jakarta Validation 3.0.2
- SLF4J 2.0.9

## Usage

### 1. Intent Capture

```java
IntentService intentService = new IntentServiceImpl(aiService, auditLogger, metrics);

IntentInput input = IntentInput.of("Build a task management app for teams");

Promise<IntentSpec> intentPromise = intentService.capture(input);
```

### 2. Shape Derivation

```java
ShapeService shapeService = new ShapeServiceImpl(aiService, auditLogger, metrics);

Promise<ShapeSpec> shapePromise = shapeService.derive(intentSpec);
```

### 3. Validation

```java
ValidationService validationService = new ValidationServiceImpl(
    securityValidator, policyEngine, auditLogger, metrics);

Promise<ValidationResult> validationPromise = validationService.validate(shapeSpec);
```

### 4. Generation

```java
GenerationService generationService = new GenerationServiceImpl(
    aiService, auditLogger, metrics);

ValidatedSpec validatedSpec = ValidatedSpec.of(shapeSpec, validationResult);

Promise<GeneratedArtifacts> artifactsPromise = generationService.generate(validatedSpec);
```

### 5. Run Execution

```java
RunService runService = new RunServiceImpl(auditLogger, metrics);

RunSpec runSpec = RunSpec.builder()
    .id(UUID.randomUUID().toString())
    .artifactsRef(artifacts.id())
    .tasks(List.of(buildTask, testTask, deployTask))
    .environment("production")
    .build();

Promise<RunResult> runPromise = runService.execute(runSpec);
```

### 6. Observation

```java
ObserveService observeService = new ObserveServiceImpl(metrics, auditLogger);

Promise<Observation> observationPromise = observeService.collect(runResult);
```

### 7. Learning

```java
LearningService learningService = new LearningServiceImpl(
    aiService, auditLogger, metrics);

Promise<Insights> insightsPromise = learningService.analyze(observation);
```

### 8. Evolution

```java
EvolutionService evolutionService = new EvolutionServiceImpl(
    aiService, auditLogger, metrics);

Promise<EvolutionPlan> planPromise = evolutionService.propose(insights);
```

## Pipeline Execution

### Full Lifecycle

```yaml
# Use yappc-full-lifecycle.yaml
name: yappc-full-lifecycle
operators:
  - intent → shape → validate → generate → run → observe → learn → evolve
```

### Design Phase Only

```yaml
# Use yappc-intent-to-generate.yaml
name: yappc-intent-to-generate
operators:
  - intent → shape → validate → generate
```

### Continuous Improvement

```yaml
# Use yappc-observe-learn-evolve.yaml
name: yappc-observe-learn-evolve
operators:
  - observe → learn → evolve
```

## Testing

All services follow the same testing pattern using `EventloopTestBase`:

```java
@DisplayName("Intent Service Tests")
class IntentServiceTest extends EventloopTestBase {
    
    @Test
    void shouldCaptureIntentWithAI() {
        // GIVEN
        IntentService service = new IntentServiceImpl(aiProvider, eventStore, metrics);
        IntentInput input = IntentInput.of("Build a task management app");
        
        // WHEN
        IntentSpec result = runPromise(() -> service.capture(input));
        
        // THEN
        assertThat(result).isNotNull();
        assertThat(result.goals()).isNotEmpty();
    }
}
```

## Observability

### Metrics

All services emit metrics:

- `yappc.{phase}.{operation}` - Timer metrics for operations
- `yappc.{phase}.{operation}.error` - Counter for errors

### Audit Logs

All operations are logged with:
- Action name
- Timestamp
- Input/output references
- Actor information

### Events

All phase completions emit:
- `PhaseStartedEvent`
- `PhaseCompletedEvent`
- `PhaseFailedEvent`
- `LifecycleExecutionEvent`

## Security

- Multi-tenancy enforced at all layers
- JWT authentication required
- RBAC authorization
- Audit trail for compliance
- Policy-as-code validation

## Performance Targets

| Operation | Target | Status |
|-----------|--------|--------|
| Intent Capture | <2s | ⏳ |
| Shape Derivation | <3s | ⏳ |
| Validation | <500ms | ⏳ |
| Generation | <5s | ⏳ |
| Observation | <100ms | ⏳ |
| Learning | <2s | ⏳ |
| Evolution | <1s | ⏳ |

## Implementation Status

✅ **Completed:**
- Phase 0: Foundation & Contracts (Protobuf, domain models)
- Phase 1-4: All 8 service implementations
- Phase 5: Operator integration & pipeline definitions
- Phase 6: Storage & event publishing

⏳ **Pending:**
- HTTP API endpoints
- Plugin framework implementation
- Comprehensive test suite
- Performance benchmarks
- Production deployment

## Contributing

Follow Ghatana architecture principles:
1. Use ActiveJ Promise for all async operations
2. Extend EventloopTestBase for tests
3. Add JavaDoc with @doc.* tags
4. Emit metrics and audit logs
5. Follow GIVEN-WHEN-THEN test pattern

## License

Internal Ghatana Platform Component
