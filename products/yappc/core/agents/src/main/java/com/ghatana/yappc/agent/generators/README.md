# LLM-Powered Agent Generators

This package provides infrastructure for wiring YAPPC specialist agents to LLM providers (OpenAI, Anthropic) using specialized prompt templates.

## Architecture

```
┌─────────────────────┐
│ Specialist Agent    │
├─────────────────────┤
│ - IntakeAgent       │──┐
│ - DesignAgent       │  │
│ - PlanUnitsAgent    │  │
└─────────────────────┘  │
                         │ uses
                         ↓
┌─────────────────────────────────────┐
│ LLMPoweredGenerator<I, O>           │
├─────────────────────────────────────┤
│ - promptTemplate: AgentPromptTemplate│
│ - llmGateway: LLMGateway            │
│ - contextBuilder: (I, Context) -> Map│
│ - responseParser: (JsonNode) -> O   │
│ - llmConfig: LLMConfig              │
├─────────────────────────────────────┤
│ + generate(StepRequest<I>)          │
│   → StepResult<O>                   │
│ + estimateCost(): Double            │
└─────────────────────────────────────┘
                         │
                         │ calls
                         ↓
┌─────────────────────────────────────┐
│ Multi-Provider LLM Gateway          │
├─────────────────────────────────────┤
│ - OpenAI (primary)                  │
│ - Anthropic (fallback)              │
├─────────────────────────────────────┤
│ + complete(prompt, config, context) │
│   → Promise<LLMResponse>            │
└─────────────────────────────────────┘
```

## Key Classes

### LLMPoweredGenerator<I, O>
Generic base class for LLM-powered OutputGenerators.

**Responsibilities**:
- Render prompt templates with input context
- Send prompts to LLM gateway
- Parse JSON responses
- Track costs and metrics
- Handle errors gracefully

**Key Methods**:
- `generate(StepRequest<I>, AgentContext)` → `Promise<StepResult<O>>`
- `estimateCost(StepRequest<I>, AgentContext)` → `Promise<Double>`

**Builder Pattern**:
```java
LLMPoweredGenerator.<IntakeInput, IntakeOutput>builder()
    .llmGateway(gateway)
    .promptTemplate(template)
    .contextBuilder((input, context) -> Map.of("requirements", input.requirements()))
    .responseParser(LLMGeneratorFactory::parseIntakeOutput)
    .name("LLM-IntakeGenerator")
    .version("1.0.0")
    .llmConfig(config)
    .build();
```

### LLMGeneratorFactory
Factory for creating agent-specific LLM generators.

**Available Generators** (12 total):

**Architecture Phase (4 generators)**:
- `createIntakeGenerator(gateway, config)` - Requirements extraction
- `createDesignGenerator(gateway, config)` - Architecture design
- `createPlanUnitsGenerator(gateway, config)` - Work breakdown
- `createScaffoldGenerator(gateway, config)` - Code scaffolding

**Implementation Phase (3 generators)**:
- `createImplementGenerator(gateway, config)` - Unit implementation
- `createBuildGenerator(gateway, config)` - Build execution
- `createReviewGenerator(gateway, config)` - Code review

**Testing Phase (1 generator)**:
- `createGenerateTestsGenerator(gateway, config)` - Test suite generation

**Operations Phase (4 generators)**:
- `createDeployStagingGenerator(gateway, config)` - Staging deployment
- `createMonitorGenerator(gateway, config)` - Production monitoring
- `createIncidentResponseGenerator(gateway, config)` - Incident handling
- `createCanaryGenerator(gateway, config)` - Canary deployment

**Each generator**:
1. Retrieves prompt from YAPPCPromptTemplates
2. Configures context builder for input type
3. Provides JSON parser for output type
4. Returns ready-to-use OutputGenerator

## Usage

### 1. Create LLM Gateway

**Option A: Ollama (Local, Recommended)**

```java
import com.ghatana.ai.llm.*;

// Create Ollama service (cost-free, private)
LLMConfiguration ollamaConfig = LLMConfiguration.builder()
    .baseUrl("http://localhost:11434")
    .model("llama3")
    .temperature(0.7)
    .maxTokens(4000)
    .build();

OllamaCompletionService ollamaService = new OllamaCompletionService(
    ollamaConfig, httpClient, metrics);

DefaultLLMGateway gateway = DefaultLLMGateway.builder()
    .addProvider("ollama", ollamaService)
    .defaultProvider("ollama")
    .metrics(metrics)
    .build();
```

**Option B: Cloud Providers (OpenAI/Anthropic)**

```java
import com.ghatana.ai.llm.*;
import com.ghatana.ai.llm.gateway.LLMGenerator;

LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
    .create(eventloop, metrics)
    .addOpenAI(httpClient, System.getenv("OPENAI_API_KEY"), "gpt-4")
    .addAnthropic(httpClient, System.getenv("ANTHROPIC_API_KEY"), "claude-3-opus")
    .primaryProvider("openai")
    .fallbackOrder(List.of("openai", "anthropic"))
    .build();
```

### 2. Configure LLM Parameters

```java
import com.ghatana.ai.llm.gateway.LLMGenerator.LLMConfig;
import java.time.Duration;

LLMConfig config = LLMConfig.builder()
    .temperature(0.7)          // Creativity (0.0 - 1.0)
    .maxTokens(4000)           // Max response length
    .timeout(Duration.ofSeconds(30))
    .build();
```

### 3. Create LLM-Powered Generators

```java
import com.ghatana.yappc.sdlc.agent.generators.LLMGeneratorFactory;

// Architecture Phase
OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>> intakeGen = 
    LLMGeneratorFactory.createIntakeGenerator(gateway, config);

OutputGenerator<StepRequest<DesignInput>, StepResult<DesignOutput>> designGen = 
    LLMGeneratorFactory.createDesignGenerator(gateway, config);

OutputGenerator<StepRequest<PlanUnitsInput>, StepResult<PlanUnitsOutput>> planGen = 
    LLMGeneratorFactory.createPlanUnitsGenerator(gateway, config);

OutputGenerator<StepRequest<ScaffoldInput>, StepResult<ScaffoldOutput>> scaffoldGen = 
    LLMGeneratorFactory.createScaffoldGenerator(gateway, config);

// Implementation Phase
OutputGenerator<StepRequest<ImplementInput>, StepResult<ImplementOutput>> implementGen = 
    LLMGeneratorFactory.createImplementGenerator(gateway, config);

OutputGenerator<StepRequest<BuildInput>, StepResult<BuildOutput>> buildGen = 
    LLMGeneratorFactory.createBuildGenerator(gateway, config);

OutputGenerator<StepRequest<ReviewInput>, StepResult<ReviewOutput>> reviewGen = 
    LLMGeneratorFactory.createReviewGenerator(gateway, config);
```

### 4. Wire to Specialist Agents

```java
import com.ghatana.yappc.sdlc.agent.specialists.*;
import com.ghatana.yappc.sdlc.agent.storage.InMemoryMemoryStore;

MemoryStore memoryStore = new InMemoryMemoryStore();

IntakeSpecialistAgent intakeAgent = new IntakeSpecialistAgent(memoryStore, intakeGen);
DesignSpecialistAgent designAgent = new DesignSpecialistAgent(memoryStore, designGen);
PlanUnitsSpecialistAgent planAgent = new PlanUnitsSpecialistAgent(memoryStore, planGen);
```

### 5. Execute Agent Workflow

```java
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.agent.framework.api.AgentContext;

AgentContext context = AgentContext.builder()
    .agentId("yappc-001")
    .userId("user-123")
    .tenantId("tenant-456")
    .sessionId("session-789")
    .build();

// Step 1: Requirements Intake
IntakeInput intakeInput = new IntakeInput("Create REST API...", "user-story");
StepRequest<IntakeInput> intakeRequest = new StepRequest<>("intake-001", intakeInput);

intakeAgent.execute(intakeRequest, context)
    .then(intakeResult -> {
        IntakeOutput output = intakeResult.output();
        System.out.println("Functional Reqs: " + output.functionalRequirements());
        
        // Step 2: Architecture Design
        DesignInput designInput = new DesignInput(
            "intake-001", 
            "microservices,scalable"
        );
        return designAgent.execute(new StepRequest<>("design-001", designInput), context);
    })
    .then(designResult -> {
        DesignOutput output = designResult.output();
        System.out.println("Architecture: " + output.architectureId());
        
        // Step 3: Plan Work Units
        PlanUnitsInput planInput = new PlanUnitsInput(
            "design-001",
            output.architectureId()
        );
        return planAgent.execute(new StepRequest<>("plan-001", planInput), context);
    })
    .whenComplete(() -> System.out.println("Workflow complete!"));
| ScaffoldGenerator | `architecture.scaffold` | Generate code scaffolding |
| ImplementGenerator | `implementation.implement` | Implement code units |
| BuildGenerator | `implementation.build` | Configure and execute builds |
| ReviewGenerator | `implementation.review` | Conduct automated code reviews |
```

## Prompt Templates

All generators use prompts from `YAPPCPromptTemplates`:

| Generator | Prompt ID | Purpose |
|-----------|-----------|---------|
| IntakeGenerator | `architecture.intake` | Extract structured requirements |
| DesignGenerator | `architecture.design` | Create architecture design |
| PlanUnitsGenerator | `architecture.plan_units` | Break down into work units |
| ScaffoldGenerator | `architecture.scaffold` | Generate code scaffolding |
| ImplementGenerator | `implementation.implement` | Implement code units |
| BuildGenerator | `implementation.build` | Configure and execute builds |
| ReviewGenerator | `implementation.review` | Conduct automated code reviews |
| GenerateTestsGenerator | `testing.generate_tests` | Generate comprehensive test suites |
| DeployStagingGenerator | `operations.deploy_staging` | Deploy to staging environment |
| MonitorGenerator | `operations.monitor` | Setup production monitoring |
| IncidentResponseGenerator | `operations.incident_response` | Handle production incidents |
| CanaryGenerator | `operations.canary` | Execute canary deployments |

Each prompt template:
- Defines system role and task
- Specifies expected JSON output format
- Includes examples and constraints
- Supports variable substitution

## JSON Response Parsing

All parsers follow this pattern:

1. **Extract JSON**: Handle markdown code blocks (```json...```)
2. **Parse**: Convert to JsonNode using Jackson
3. **Validate**: Check required fields
4. **Convert**: Map to typed output (IntakeOutput, DesignOutput, etc.)
5. **Handle Errors**: Return StepResult.failed() on parse errors

- `ScaffoldOutput`: scaffoldId, generatedFiles, filesByType, metadata
- `ImplementOutput`: implementationId, unitName, implementedFiles, metrics, metadata
- `BuildOutput`: buildId, success, artifacts, buildMetrics, metadata
- `ReviewOutput`: reviewId, approved, findings, qualityMetrics, metadata
**Example Output Types**:
- `IntakeOutput`: functionalReqs, nonFunctionalReqs, constraints, metadata
- `DesignOutput`: architectureId, components, patterns, metadata
- `PlanUnitsOutput`: planId, implementationUnits, dependencies, estimatedEffort, metadata

## Cost Estimation

LLMPoweredGenerator tracks costs using token estimation:
- **Input**: ~1 token per 4 characters (~$0.03 per 1K tokens)
- **Output**: ~1 token per 4 characters (~$0.06 per 1K tokens)

```java
Promise<Double> estimatedCost = generator.estimateCost(request, context);
// Returns: 0.05 (5 cents)
```

## Error Handling

Errors are handled at multiple levels:

1. **LLM Gateway**: Retries, fallback providers
2. **Response Parsing**: JSON validation, schema checking
3. **Generator**: Wraps errors in StepResult.failed()

All errors include descriptive messages and preserve context for debugging.

## Environment Variables

Required for multi-provider setup:

```bash
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."  # Optional for fallback
```

## Testing

```bash
# Compile
./gradlew :products:yappc:core:sdlc-agents:compileJava

# Run tests
./gradlew :products:yappc:core:sdlc-agents:test
```## Next Steps

**Bootstrap Integration** (P1 - Highest Value):
Update `YAPPCAgentBootstrap` to:
1. Accept LLMGateway parameter
2. Create all 12 LLM-powered generators using factory
3. Wire to all specialist agents
4. Replace all rule-based stub generators
5. Enable complete end-to-end LLM-powered SDLC workflow

**Integration Testing** (P1):
- Test each generator with real LLM calls
- Test multi-agent workflows (Architecture → Implementation → Testing → Operations)
- Verify error handling and fallback mechanisms
- Validate cost estimation accuracy

**Performance Benchmarking** (P2):
- Measure average response times per generator
- Track token usage and costs per operation
- Test concurrent execution capabilities
- Identify optimization opportunities

**Production Hardening** (P2):
- Add retry logic for transient failures
- Implement rate limiting for cost control
- Add caching for repeated prompts
- Enhanced error messages and debugging

**Completed** (12/12 total - 100% coverage):

**Architecture Phase**:
- ✅ IntakeGenerator
- ✅ DesignGenerator
- ✅ PlanUnitsGenerator
- ✅ ScaffoldGenerator

**Implementation Phase**:
- ✅ ImplementGenerator
- ✅ BuildGenerator
- ✅ ReviewGenerator

**Testing Phase**:
- ✅ GenerateTestsGenerator

**Operations Phase**:
- ✅ DeployStagingGenerator
- ✅ MonitorGenerator
- ✅ IncidentResponseGenerator
- ✅ CanaryGenerator

**Build Status**: ✅ SUCCESSFUL - All 12 generators compile without errors
4. Replace rule-based generators

## References

- **Prompts**: [YAPPCPromptTemplates.java](../prompts/YAPPCPromptTemplates.java)
- **LLM Gateway**: `libs/java/ai-integration`
- **Specialist Agents**: [specialists/](../specialists/)
- **Tracker**: [GAA_IMPLEMENTATION_TRACKER.md](../../../../../../GAA_IMPLEMENTATION_TRACKER.md)
