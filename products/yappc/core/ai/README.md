# YAPPC AI Module

## Overview

This module consolidates all AI capabilities for YAPPC, providing a unified interface for AI-powered features including agents, vector search, canvas generation, and workflow orchestration.

## Architecture

### Core Components

1. **VectorSearchService** - Vector-based semantic search using shared AI libraries
2. **BaseAgent** - Base class for AI agents with LLM integration
3. **CanvasService** - Canvas generation and management
4. **WorkflowOrchestrator** - Multi-step AI workflow coordination

### Module Structure

```
ai/
├── src/main/java/com/ghatana/yappc/ai/
│   ├── agent/
│   │   └── BaseAgent.java
│   ├── vector/
│   │   └── VectorSearchService.java
│   ├── canvas/
│   │   └── CanvasService.java
│   └── workflow/
│       └── WorkflowOrchestrator.java
└── src/test/java/com/ghatana/yappc/ai/
    ├── agent/
    │   └── BaseAgentTest.java
    ├── vector/
    │   └── VectorSearchServiceTest.java
    └── canvas/
        └── CanvasServiceTest.java
```

## Key Features

### Vector Search
- Semantic search using embeddings
- Document management (add, update, delete)
- Configurable filtering and ranking

### AI Agents
- Base agent framework for custom agents
- LLM integration via shared libraries
- Context-aware execution

### Canvas Generation
- Create and manage canvas configurations
- AI-powered canvas generation from prompts
- Unified canvas architecture support

### Workflow Orchestration
- Multi-step workflow execution
- State management and error handling
- Workflow pause/resume/cancel operations

## Dependencies

- **libs/ai-integration** - Shared AI services
- **libs/agent-api** - Agent framework
- **libs/observability** - Metrics and monitoring
- **data-cloud:plugins:vector** - Vector storage
- **ActiveJ** - Async/Promise support

## Usage

### Vector Search

```java
VectorSearchService vectorSearch = new VectorSearchService(
    vectorStoreClient,
    embeddingService
);

Promise<List<SearchResult>> results = vectorSearch.search(
    "security vulnerabilities",
    5,
    Map.of("severity", "HIGH")
);
```

### AI Agents

```java
BaseAgent agent = new MyCustomAgent(llmService);
Promise<String> response = agent.execute("Analyze this code");
```

### Canvas Service

```java
CanvasService canvasService = new CanvasService();
Promise<Map<String, Object>> canvas = canvasService.createCanvas(
    workspaceId,
    "My Canvas",
    config
);
```

### Workflow Orchestration

```java
WorkflowOrchestrator orchestrator = new WorkflowOrchestrator();
Promise<Map<String, Object>> result = orchestrator.executeWorkflow(
    workflowId,
    steps
);
```

## Testing

Run tests with:

```bash
./gradlew :products:yappc:ai:test
```

## Migration Notes

This module consolidates code previously scattered across:
- `domain/src/main/java/.../agent/`
- `domain/src/main/java/.../vector/`
- `canvas-ai-service/`

All imports should be updated to use `com.ghatana.yappc.ai.*` packages.

## Future Enhancements

- [ ] Multi-model agent support
- [ ] Advanced workflow scheduling
- [ ] Vector store optimization
- [ ] Agent memory management
- [ ] Workflow versioning and rollback
