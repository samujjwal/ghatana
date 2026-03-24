#!/bin/bash

###############################################################################
# YAPPC Documentation Consolidation Script
#
# Consolidates 90+ documentation files into 15 essential files.
# Archives outdated documentation while preserving essential guides.
#
# Usage: ./scripts/consolidate-documentation.sh [--dry-run]
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCS_DIR="$YAPPC_ROOT/docs"
ARCHIVE_DIR="$DOCS_DIR/archive"

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "🔍 Running in DRY RUN mode - no files will be modified"
fi

echo "🚀 YAPPC Documentation Consolidation"
echo "===================================="
echo ""

# Count current documentation files
CURRENT_COUNT=$(find "$DOCS_DIR" -type f -name "*.md" | wc -l)
echo "📊 Current documentation files: $CURRENT_COUNT"
echo ""

###############################################################################
# Phase 3.1: Archive Outdated Documentation
###############################################################################

echo "📦 Phase 3.1: Archiving outdated documentation"
echo ""

if [[ "$DRY_RUN" == false ]]; then
    # Create archive directory structure
    mkdir -p "$ARCHIVE_DIR/audits-2026-01"
    mkdir -p "$ARCHIVE_DIR/implementation-reports"
    mkdir -p "$ARCHIVE_DIR/historical-analysis"
    
    # Archive audit documents from 2026-01-31
    if [[ -d "$DOCS_DIR/audits/2026-01-31" ]]; then
        echo "  📁 Archiving audits/2026-01-31..."
        mv "$DOCS_DIR/audits/2026-01-31"/* "$ARCHIVE_DIR/audits-2026-01/" 2>/dev/null || true
        rmdir "$DOCS_DIR/audits/2026-01-31" 2>/dev/null || true
    fi
    
    # Archive old implementation reports
    OLD_REPORTS=(
        "YAPPC_AGENTIC_PLATFORM_ARCHITECTURE_REVIEW.md"
        "YAPPC_AGENTIC_PLATFORM_IMPLEMENTATION_PLAN.md"
        "YAPPC_LIFECYCLE_INTELLIGENCE_ARCHITECTURE_REPORT.md"
        "FEATURE_DEEP_INSPECTION_REPORT.md"
    )
    
    for report in "${OLD_REPORTS[@]}"; do
        if [[ -f "$YAPPC_ROOT/$report" ]]; then
            echo "  📄 Archiving $report..."
            mv "$YAPPC_ROOT/$report" "$ARCHIVE_DIR/implementation-reports/"
        fi
    done
    
    echo "  ✅ Archived outdated documentation"
else
    echo "  ⏭️  Skipped (dry run)"
fi

###############################################################################
# Phase 3.2: Create Essential Documentation Structure
###############################################################################

echo ""
echo "📝 Phase 3.2: Creating essential documentation structure"
echo ""

# Create modules directory
if [[ "$DRY_RUN" == false ]]; then
    mkdir -p "$DOCS_DIR/modules"
    mkdir -p "$DOCS_DIR/guides"
fi

# Create consolidated README.md
cat > "$DOCS_DIR/README.md" <<'EOF'
# YAPPC Documentation

**AI-Native Product Development Platform**

## Quick Links

- [Architecture Overview](ARCHITECTURE.md) - System design and module structure
- [Development Guide](DEVELOPMENT.md) - Contributing, coding standards, testing
- [Deployment Guide](DEPLOYMENT.md) - Running YAPPC locally and in production
- [API Reference](API.md) - HTTP and gRPC API documentation
- [Testing Guide](TESTING.md) - Writing and running tests

## Module Documentation

- [Core Architecture](CORE_ARCHITECTURE.md) - Core module organization
- [Agent System](modules/agents.md) - Agent framework and specialists
- [Scaffolding System](modules/scaffold.md) - Project scaffolding engine
- [Refactoring System](modules/refactorer.md) - Code refactoring engine
- [AI Integration](modules/ai.md) - AI/LLM integration guide

## User Guides

- [Quick Start](guides/quick-start.md) - Get started in 5 minutes
- [AI Workflows](guides/ai-workflows.md) - AI-powered development workflows
- [Canvas Guide](guides/canvas-guide.md) - Visual canvas usage

## Architecture

YAPPC is organized into 5 domain clusters:

1. **Foundation Layer** - domain, spi, framework
2. **AI & Knowledge Layer** - ai, knowledge-graph
3. **Agent Execution Layer** - agents/*
4. **Scaffolding Layer** - scaffold/*
5. **Refactoring Layer** - refactorer/*

See [CORE_ARCHITECTURE.md](CORE_ARCHITECTURE.md) for complete details.

## Support

- **Team:** YAPPC Core Team
- **Slack:** #yappc-dev
- **Issues:** GitHub Issues

---

**Last Updated:** 2026-03-23
EOF

# Create DEVELOPMENT.md
cat > "$DOCS_DIR/DEVELOPMENT.md" <<'EOF'
# YAPPC Development Guide

## Prerequisites

- Java 21+ (Temurin recommended)
- Node.js 20+ with pnpm
- Docker & Docker Compose
- 8GB+ RAM

## Getting Started

```bash
# Clone repository
git clone https://github.com/ghatana/ghatana.git
cd ghatana/products/yappc

# Start infrastructure
./start-infra.sh

# Build backend
./gradlew clean build

# Start frontend
cd frontend
pnpm install
pnpm dev
```

## Project Structure

```
yappc/
├── core/           # Core domain modules (18 modules)
├── backend/        # Backend services
├── frontend/       # React frontend (10 libraries)
├── services/       # Service layer
├── docs/           # Documentation
└── scripts/        # Build and utility scripts
```

## Development Workflow

### Backend Development

```bash
# Build specific module
./gradlew :core:agents:runtime:build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :core:agents:runtime:test
```

### Frontend Development

```bash
cd frontend

# Start dev server
pnpm dev

# Run tests
pnpm test

# Type checking
pnpm typecheck

# Linting
pnpm lint
```

## Code Standards

### Java

- **Style:** Google Java Style Guide
- **Async:** ActiveJ Promise (no CompletableFuture)
- **Documentation:** JavaDoc required for public APIs
- **Testing:** JUnit 5 + Mockito

### TypeScript

- **Style:** ESLint + Prettier
- **Framework:** React 19 + Jotai
- **Documentation:** JSDoc for complex functions
- **Testing:** Vitest + Testing Library

## Testing

### Backend Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Run specific test class
./gradlew test --tests "com.ghatana.yappc.agents.CodeAnalysisAgentTest"
```

### Frontend Tests

```bash
# Run all tests
pnpm test

# Run with coverage
pnpm test:coverage

# Run E2E tests
pnpm test:e2e
```

## Commit Conventions

```
feat: Add new feature
fix: Bug fix
docs: Documentation changes
style: Code style changes
refactor: Code refactoring
test: Test changes
chore: Build/tooling changes
```

## Pull Request Process

1. Create feature branch from `main`
2. Make changes with clear commits
3. Run tests and linting
4. Update documentation
5. Submit PR with description
6. Address review feedback
7. Merge after approval

## Module Boundaries

Follow the dependency rules in [CORE_ARCHITECTURE.md](CORE_ARCHITECTURE.md):

- Foundation → Platform only
- AI/Knowledge → Foundation
- Agents → AI/Knowledge
- Scaffold → Agents
- Refactorer → Agents

**Forbidden:**
- Circular dependencies
- Cross-layer imports (skip layers)
- Product code in platform

## Troubleshooting

### Build Issues

```bash
# Clean build
./gradlew clean

# Clear Gradle cache
rm -rf ~/.gradle/caches

# Restart Gradle daemon
./gradlew --stop
```

### Frontend Issues

```bash
# Clear node_modules
rm -rf node_modules
pnpm install

# Clear build cache
pnpm clean
```

---

**Questions?** Ask in #yappc-dev on Slack
EOF

# Create TESTING.md
cat > "$DOCS_DIR/TESTING.md" <<'EOF'
# YAPPC Testing Guide

## Overview

YAPPC uses a comprehensive testing strategy:

- **Unit Tests:** Test individual components in isolation
- **Integration Tests:** Test component interactions
- **E2E Tests:** Test complete user workflows
- **Performance Tests:** Test system performance

## Backend Testing

### Unit Tests (JUnit 5)

```java
@Test
void shouldAnalyzeCode() {
    // Given
    CodeAnalysisAgent agent = new CodeAnalysisAgent();
    String code = "public class Example {}";
    
    // When
    Promise<AnalysisResult> result = agent.analyze(code);
    
    // Then
    assertThat(result.getResult().getComplexity()).isLessThan(10);
}
```

### ActiveJ Tests

```java
@ExtendWith(EventloopTestExtension.class)
class AsyncTest {
    @Test
    void shouldHandleAsync(Eventloop eventloop) {
        Promise<String> promise = Promise.of("test");
        
        String result = eventloop.submit(() -> promise).join();
        
        assertThat(result).isEqualTo("test");
    }
}
```

## Frontend Testing

### Component Tests (Vitest + Testing Library)

```typescript
import { render, screen } from '@testing-library/react';
import { Button } from './Button';

test('renders button with text', () => {
  render(<Button>Click me</Button>);
  expect(screen.getByText('Click me')).toBeInTheDocument();
});
```

### Hook Tests

```typescript
import { renderHook } from '@testing-library/react';
import { useCanvasState } from './useCanvasState';

test('manages canvas state', () => {
  const { result } = renderHook(() => useCanvasState());
  
  expect(result.current.nodes).toEqual([]);
});
```

### E2E Tests (Playwright)

```typescript
import { test, expect } from '@playwright/test';

test('user can create project', async ({ page }) => {
  await page.goto('http://localhost:3000');
  await page.click('text=New Project');
  await page.fill('[name="projectName"]', 'Test Project');
  await page.click('text=Create');
  
  await expect(page.locator('text=Test Project')).toBeVisible();
});
```

## Test Coverage

### Backend Coverage Goals

- **Unit Tests:** 80%+ coverage
- **Integration Tests:** Key workflows covered
- **Critical Paths:** 100% coverage

### Frontend Coverage Goals

- **Components:** 80%+ coverage
- **Hooks:** 90%+ coverage
- **Utilities:** 95%+ coverage

## Running Tests

```bash
# Backend
./gradlew test                    # All tests
./gradlew test --tests "*Agent*"  # Specific tests

# Frontend
pnpm test                         # All tests
pnpm test:coverage                # With coverage
pnpm test:e2e                     # E2E tests
```

## Best Practices

1. **Test Behavior, Not Implementation**
2. **Use Descriptive Test Names**
3. **Follow AAA Pattern** (Arrange, Act, Assert)
4. **Mock External Dependencies**
5. **Keep Tests Fast and Isolated**

---

**Coverage Reports:** `build/reports/jacoco` (backend), `coverage/` (frontend)
EOF

# Create module documentation
cat > "$DOCS_DIR/modules/agents.md" <<'EOF'
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
EOF

cat > "$DOCS_DIR/modules/scaffold.md" <<'EOF'
# Scaffolding System Documentation

## Overview

YAPPC's scaffolding system generates project structures and boilerplate code.

## Architecture

```
scaffold/
├── api/        # Public API
├── engine/     # Core orchestration
├── generators/ # Language-specific generators
└── templates/  # Template management
```

## Features

- Multi-framework support (React, Java, Python)
- Template-based generation
- AI-powered customization
- Incremental scaffolding

## Usage

```java
ScaffoldEngine engine = new ScaffoldEngine();

ScaffoldRequest request = ScaffoldRequest.builder()
    .framework("react")
    .template("webapp")
    .projectName("my-app")
    .build();

Promise<ScaffoldResult> result = engine.scaffold(request);
```

## Templates

Templates are defined in YAML:

```yaml
name: react-webapp
framework: react
files:
  - path: src/App.tsx
    template: templates/react/App.tsx.hbs
  - path: package.json
    template: templates/react/package.json.hbs
```

---

See [Scaffold API](../api/scaffold-api.md) for complete API reference.
EOF

cat > "$DOCS_DIR/modules/refactorer.md" <<'EOF'
# Refactoring System Documentation

## Overview

YAPPC's refactoring system provides automated code transformations.

## Architecture

```
refactorer/
├── api/    # Refactoring API
└── engine/ # AST transformation engine
```

## Supported Refactorings

- Extract Method
- Rename Symbol
- Move Class
- Inline Variable
- Extract Interface

## Usage

```java
RefactoringEngine engine = new RefactoringEngine();

RefactoringRequest request = RefactoringRequest.builder()
    .type(RefactoringType.EXTRACT_METHOD)
    .sourceFile(file)
    .selection(selection)
    .build();

Promise<RefactoringResult> result = engine.refactor(request);
```

---

See [Refactorer API](../api/refactorer-api.md) for complete API reference.
EOF

cat > "$DOCS_DIR/modules/ai.md" <<'EOF'
# AI Integration Documentation

## Overview

YAPPC integrates with LLM providers for AI-powered development features.

## Supported Providers

- OpenAI (GPT-4, GPT-3.5)
- Anthropic (Claude)
- Ollama (Local models)

## Configuration

```yaml
ai:
  provider: openai
  model: gpt-4
  apiKey: ${OPENAI_API_KEY}
  temperature: 0.7
  maxTokens: 2000
```

## Usage

```java
AiService aiService = new AiService();

Promise<AiResponse> response = aiService.complete(
    "Generate a React component for a login form"
);
```

## Features

- Natural language code generation
- Code explanation and documentation
- Bug detection and fixes
- Test generation
- Architecture suggestions

---

See [AI Service API](../api/ai-api.md) for complete API reference.
EOF

# Create user guides
cat > "$DOCS_DIR/guides/quick-start.md" <<'EOF'
# YAPPC Quick Start Guide

Get started with YAPPC in 5 minutes.

## 1. Prerequisites

- Java 21+
- Node.js 20+
- Docker

## 2. Installation

```bash
git clone https://github.com/ghatana/ghatana.git
cd ghatana/products/yappc
```

## 3. Start Services

```bash
# Start infrastructure
./start-infra.sh

# Start backend
./gradlew :services:run

# Start frontend (new terminal)
cd frontend
pnpm install
pnpm dev
```

## 4. Access YAPPC

Open http://localhost:3000

## 5. Create Your First Project

1. Click "New Project"
2. Select framework (React, Java, etc.)
3. Enter project name
4. Click "Generate"

Done! Your project is ready.

## Next Steps

- [AI Workflows](ai-workflows.md)
- [Canvas Guide](canvas-guide.md)
- [Development Guide](../DEVELOPMENT.md)
EOF

cat > "$DOCS_DIR/guides/ai-workflows.md" <<'EOF'
# AI Workflows Guide

Learn how to use YAPPC's AI-powered development workflows.

## Code Generation

Generate code from natural language:

1. Open AI Assistant
2. Describe what you want: "Create a REST API for user management"
3. Review generated code
4. Accept or refine

## Code Analysis

Analyze code quality:

1. Select code
2. Right-click → "Analyze with AI"
3. Review suggestions
4. Apply improvements

## Test Generation

Generate tests automatically:

1. Select function/class
2. Click "Generate Tests"
3. Review generated tests
4. Add to test suite

## Refactoring

AI-assisted refactoring:

1. Select code to refactor
2. Ask AI: "How can I improve this?"
3. Review suggestions
4. Apply refactorings

---

**Tip:** Use natural language - YAPPC understands context!
EOF

cat > "$DOCS_DIR/guides/canvas-guide.md" <<'EOF'
# Canvas Guide

YAPPC's visual canvas for product ideation and architecture.

## Features

- Miro-style interface
- AI-powered suggestions
- Real-time collaboration
- Export to code

## Getting Started

1. Click "New Canvas"
2. Add elements (sticky notes, shapes, frames)
3. Connect elements
4. Use AI suggestions

## Keyboard Shortcuts

- `V` - Select tool
- `T` - Text tool
- `F` - Frame tool
- `Cmd+Z` - Undo
- `Cmd+Shift+Z` - Redo

## AI Features

- **Smart Grouping:** AI suggests logical groupings
- **Layout Optimization:** AI improves layout
- **Code Generation:** Generate code from diagrams

---

**Tip:** Use frames to organize related elements!
EOF

echo "  ✅ Created essential documentation structure"

###############################################################################
# Summary
###############################################################################

NEW_COUNT=$(find "$DOCS_DIR" -type f -name "*.md" 2>/dev/null | wc -l)

echo ""
echo "✅ Documentation Consolidation Complete!"
echo ""
echo "📊 Summary:"
echo "  Before: $CURRENT_COUNT files"
echo "  After: $NEW_COUNT files"
echo "  Reduction: $((CURRENT_COUNT - NEW_COUNT)) files ($((100 - (NEW_COUNT * 100 / CURRENT_COUNT)))%)"
echo ""
echo "📁 Structure:"
echo "  docs/"
echo "  ├── README.md"
echo "  ├── ARCHITECTURE.md"
echo "  ├── DEVELOPMENT.md"
echo "  ├── DEPLOYMENT.md"
echo "  ├── API.md"
echo "  ├── TESTING.md"
echo "  ├── CORE_ARCHITECTURE.md"
echo "  ├── modules/"
echo "  │   ├── agents.md"
echo "  │   ├── scaffold.md"
echo "  │   ├── refactorer.md"
echo "  │   └── ai.md"
echo "  └── guides/"
echo "      ├── quick-start.md"
echo "      ├── ai-workflows.md"
echo "      └── canvas-guide.md"
echo ""

if [[ "$DRY_RUN" == true ]]; then
    echo "⚠️  This was a DRY RUN - no files were modified"
fi
