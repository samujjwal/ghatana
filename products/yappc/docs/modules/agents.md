# Agent System Documentation

## Overview

YAPPC's agent system provides AI-powered development capabilities through specialized agents.

## Architecture

```
agents/
├── runtime/                 # Agent execution runtime
├── code-specialists/        # Code analysis and generation
├── architecture-specialists/ # Design and patterns
└── testing-specialists/     # Test generation and validation
```

## Agent Types

### Code Specialists

- **CodeAnalysisAgent:** Analyzes code quality and complexity
- **CodeGenerationAgent:** Generates code from specifications
- **RefactoringAgent:** Suggests and applies refactorings

### Architecture Specialists

- **ArchitectureAnalysisAgent:** Analyzes system architecture
- **PatternDetectionAgent:** Detects design patterns
- **DesignAgent:** Suggests architectural improvements

### Testing Specialists

- **TestGenerationAgent:** Generates unit tests
- **TestValidationAgent:** Validates test coverage
- **CoverageAgent:** Analyzes test coverage

## Usage

```java
// Create agent
CodeAnalysisAgent agent = new CodeAnalysisAgent();

// Analyze code
Promise<AnalysisResult> result = agent.analyze(sourceCode);

// Process result
result.whenResult(analysis -> {
    System.out.println("Complexity: " + analysis.getComplexity());
});
```

## Configuration

Agents are configured via `agent-catalog.yaml`:

```yaml
agents:
  - id: code-analysis
    type: DETERMINISTIC
    specialist: code
    config:
      maxComplexity: 10
```

---

See [CORE_ARCHITECTURE.md](../CORE_ARCHITECTURE.md) for dependency rules.
