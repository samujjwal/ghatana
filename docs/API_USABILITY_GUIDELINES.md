# API Usability Guidelines for Product Migration

## Guiding Principles

1. **Developer Experience First** - APIs should be intuitive and require minimal boilerplate
2. **Consistent Patterns** - Use same patterns across all products
3. **ActiveJ Promise** - All async operations use `io.activej.promise.Promise`
4. **Multi-tenant by Default** - All APIs require tenant context
5. **Type Safety** - Strong typing with Java 21 features (records, sealed interfaces)

---

## Platform APIs (Cross-Cutting)

### Validation API Example
```java
// Simple usage
ValidationService validation = new DefaultValidationService();
Promise<ValidationResult> result = validation.validateEvent(event, context);

// With custom validators
ValidationService validation = ValidationService.builder()
    .addValidator(new EmailValidator())
    .addValidator(new NotNullValidator())
    .build();
```

### Auth API Example
```java
// Simple authentication
AuthService auth = new DefaultAuthService();
Promise<UserPrincipal> user = auth.authenticate(token);

// Authorization check
boolean canAccess = auth.checkPermission(user, "collection:read");
```

---

## Product APIs (Domain-Specific)

### AEP Product APIs

#### Agent API (Easy to Use)
```java
// Simple agent execution
Agent agent = new CodeGenerationAgent(llmGateway);
Promise<GeneratedCode> result = agent.execute(task, context);

// Pipeline of agents
Agent pipeline = AgentPipeline.builder()
    .add(new AnalysisAgent())
    .add(new CodeGenAgent())
    .add(new ValidationAgent())
    .build();
Promise<FinalResult> result = pipeline.execute(input, context);
```

#### Operator API (Easy to Use)
```java
// Simple operator execution
Operator operator = OperatorRegistry.get("data-transformer");
Promise<TransformedData> result = operator.execute(data, context);

// Custom operator
Operator custom = new CustomOperator()
    .withInputSchema(schema)
    .withOutputSchema(outputSchema)
    .withHandler((input, ctx) -> transform(input));
```

#### AI API (Easy to Use)
```java
// Simple LLM call
LLMService llm = new DefaultLLMService();
Promise<String> response = llm.complete("Generate code for: " + requirement);

// With cost tracking
LLMService llm = LLMService.builder()
    .withModel("gpt-4")
    .withCostTracker(new BudgetTracker(10.0))
    .build();
```

### Data-Cloud Product APIs

#### Collection API (Already Good)
```java
// Simple CRUD operations
CollectionService collections = new DefaultCollectionService();
Promise<Collection> created = collections.create(tenantId, collection);
Promise<List<Collection>> list = collections.list(tenantId);

// Query operations
QueryService queries = new DefaultQueryService();
Promise<QueryResult> results = queries.execute(tenantId, collectionName, query);
```

#### Event API (Easy to Use)
```java
// Simple event publishing
EventService events = new DefaultEventService();
Promise<EventId> published = events.publish(tenantId, event);

// Event subscription
events.subscribe(tenantId, eventType, (event, ctx) -> {
    // Handle event
    return Promise.ofComplete();
});
```

#### Storage API (Easy to Use)
```java
// Simple storage operations
StorageService storage = new DefaultStorageService();
Promise<String> stored = storage.store(tenantId, data);
Promise<byte[]> retrieved = storage.retrieve(tenantId, id);

// Multi-tier storage
StorageService storage = StorageService.builder()
    .withMemoryCache()
    .withRedisCache()
    .withS3Persistence()
    .build();
```

---

## API Design Patterns

### 1. Builder Pattern for Configuration
```java
Service service = Service.builder()
    .withConfig(key, value)
    .withTenantId(tenantId)
    .withMetrics(metrics)
    .build();
```

### 2. Factory Pattern for Creation
```java
Agent agent = AgentFactory.create("code-generator", config);
Storage storage = StorageFactory.create("multi-tier", config);
```

### 3. Strategy Pattern for Pluggability
```java
ValidationService validation = new ValidationService(
    new RuleBasedValidator(),
    new LLMValidator()
);
```

### 4. Context Pattern for Execution
```java
AgentContext context = AgentContext.builder()
    .tenantId(tenantId)
    .userId(userId)
    .budget(10.0)
    .build();
```

---

## Error Handling

### Consistent Error Types
```java
// Domain-specific exceptions
public class ValidationException extends Exception { }
public class AgentExecutionException extends Exception { }
public class StorageException extends Exception { }

// With Promise
Promise<Result> result = service.execute(input)
    .mapException(ex -> new DomainException("Operation failed", ex));
```

### Error Recovery
```java
Promise<Result> result = service.execute(input)
    .recover(ex -> {
        if (ex instanceof ValidationException) {
            return handleValidationError(ex);
        }
        return Promise.ofException(ex);
    });
```

---

## Testing Support

### Mock Implementations
```java
// Easy testing with mocks
AgentService agents = new MockAgentService();
ValidationService validation = new NoopValidationService();
StorageService storage = new InMemoryStorageService();
```

### Test Utilities
```java
// Test context builder
AgentContext testContext = AgentContextTestBuilder.builder()
    .tenantId("test-tenant")
    .budget(Double.MAX_VALUE)
    .build();

// Test data builders
Event testEvent = EventTestBuilder.builder()
    .type("test-event")
    .payload("{}")
    .build();
```

---

## Migration Checklist

For each migrated module:

1. **API Simplicity** - Can common operations be done in 1-2 lines?
2. **Builder Support** - Is there a builder for complex configuration?
3. **Error Handling** - Are exceptions domain-specific and meaningful?
4. **Testing Support** - Are mock implementations available?
5. **Documentation** - Are examples provided in Javadoc?
6. **Multi-tenant** - Is tenant context required by default?
7. **Type Safety** - Are records and sealed interfaces used?
8. **ActiveJ Promise** - Are all async operations using Promise?

---

## Examples of Good APIs

### Simple and Intuitive
```java
// One line for common operations
Promise<Collection> collection = collections.create(tenantId, name);

// Builder for complex configuration
Agent agent = Agent.builder()
    .name("code-gen")
    .withLLM("gpt-4")
    .withMemory(redis)
    .build();
```

### Consistent Across Products
```java
// Same pattern in AEP and Data-Cloud
Service service = new DefaultService();
Promise<Result> result = service.execute(input, context);
```

### Type Safe
```java
// Record for immutable data
public record Collection(String id, String name, Schema schema) {}

// Sealed interface for variants
sealed interface StorageResult permits Success, Failure {}
```

---

## Implementation Priority

1. **Phase 1**: Platform APIs (validation, auth, observability)
2. **Phase 2**: AEP Product APIs (agents, operators, AI)
3. **Phase 3**: Data-Cloud Product APIs (events, storage)
4. **Phase 4**: Cross-cutting utilities (testing, metrics)

Each phase should include:
- API design review
- Implementation with ActiveJ Promise
- Comprehensive unit tests
- Documentation with examples
- Migration guide for existing code
