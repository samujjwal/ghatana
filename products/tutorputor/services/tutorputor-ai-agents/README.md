# TutorPutor AI Agents

AI agents for educational content generation using LLM and the Agentic Event Processor framework.

## Overview

This module provides Java-based AI agents that generate educational content for the TutorPutor platform:

- **ContentGenerationAgent**: Generates claims, examples, simulations, and animations
- **ValidationAgent**: Validates content for correctness, completeness, concreteness, conciseness
- **FactCheckingAgent**: Verifies facts against authoritative sources
- **EvidenceAnalysisAgent**: Analyzes learner evidence and suggests improvements

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ TypeScript Services (Tutorputor Platform content module)    │
│ - ContentOrchestrator                                       │
│ - Database operations (Prisma)                              │
└─────────────────────────────────────────────────────────────┘
                            ↓ gRPC
┌─────────────────────────────────────────────────────────────┐
│ gRPC Service Layer (Java)                                   │
│ - ContentGenerationServiceImpl                              │
│ - ValidationServiceImpl                                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ AI Agents (Java - extends BaseAgent)                        │
│ - ContentGenerationAgent                                    │
│ - ValidationAgent                                           │
│ - FactCheckingAgent                                         │
│ - EvidenceAnalysisAgent                                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Shared Infrastructure (from agentic-event-processor)        │
│ - BaseAgent (lifecycle, metrics, health)                    │
│ - LlmProvider (LangChain4j integration)                     │
│ - PatternSuggestionAgent (pattern learning)                 │
│ - EventCloud (event storage)                                │
└─────────────────────────────────────────────────────────────┘
```

## Key Features

### 1. **Extends Existing Agentic Framework**

- All agents extend `BaseAgent` for lifecycle management
- Reuses `LlmProvider` for LLM integration
- Leverages `PatternSuggestionAgent` for evidence analysis
- Integrates with EventCloud for event-driven processing

### 2. **gRPC Service Layer**

- Exposes agents via gRPC for TypeScript consumption
- Async/non-blocking operations using CompletableFuture
- Proper error handling and status codes
- Metrics and observability

### 3. **Prompt Engineering**

- `PromptTemplateEngine` for consistent prompt formats
- Version-controlled prompts
- Easy A/B testing of prompt strategies
- Structured response parsing

### 4. **Content Validation**

- Age-appropriateness checks
- Bloom's taxonomy alignment
- Toxicity detection
- Completeness verification

## Building

```bash
# From agentic-event-processor root
./gradlew :services:tutorputor-ai-agents:build

# Generate proto files
./gradlew :services:tutorputor-ai-agents:generateProto

# Run tests
./gradlew :services:tutorputor-ai-agents:test
```

## Running

```bash
# Start gRPC server
./gradlew :services:tutorputor-ai-agents:run

# Server listens on localhost:50051 by default
```

## Configuration

Environment variables:

```bash
# LLM Provider
OPENAI_API_KEY=sk-...
OLLAMA_BASE_URL=http://localhost:11434

# gRPC Server
GRPC_PORT=50051

# Observability
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

## Usage from TypeScript

```typescript
// Generate TypeScript gRPC client
import { ContentGenerationServiceClient } from "@tutorputor/contracts/v1/content_generation_grpc_pb";

const client = new ContentGenerationServiceClient("localhost:50051");

// Generate claims
const response = await client.generateClaims({
  requestId: uuidv4(),
  tenantId: "tenant-1",
  topic: "Projectile Motion",
  gradeLevel: GradeLevel.GRADE_9_12,
  domain: Domain.SCIENCE,
  maxClaims: 5,
});

console.log("Generated claims:", response.claims);
```

## Metrics

All agents emit metrics via Micrometer:

- `tutorputor.agent.claim_generation.duration` - Time to generate claims
- `tutorputor.agent.example_generation.duration` - Time to generate examples
- `tutorputor.agent.simulation_generation.duration` - Time to generate simulation
- `tutorputor.agent.llm.calls.success` - Successful LLM calls
- `tutorputor.agent.llm.calls.failures` - Failed LLM calls

## Testing

```bash
# Unit tests
./gradlew :services:tutorputor-ai-agents:test

# Integration tests (requires Ollama or OpenAI API key)
./gradlew :services:tutorputor-ai-agents:integrationTest
```

## Dependencies

- **agent-runtime**: Base agent framework
- **pattern-learning**: LLM integration and pattern analysis
- **LangChain4j**: LLM abstraction layer
- **gRPC**: Service layer
- **ActiveJ**: Async runtime
- **Micrometer**: Metrics

## Design Principles

1. **Reusability**: Leverage existing agentic framework
2. **Maintainability**: Clear separation of concerns (agent, prompt, validation)
3. **Performance**: Async operations, caching, circuit breaker
4. **Scalability**: Horizontal scaling via multiple agent instances
5. **Extensibility**: Plugin-based prompt templates
6. **Customizability**: Configurable via environment variables

## Next Steps

- [ ] Implement FactCheckingAgent
- [ ] Implement ValidationAgent
- [ ] Implement EvidenceAnalysisAgent
- [ ] Add integration tests
- [ ] Add prompt versioning system
- [ ] Add A/B testing framework for prompts
- [ ] Add caching layer for LLM responses
- [ ] Add rate limiting for LLM calls
