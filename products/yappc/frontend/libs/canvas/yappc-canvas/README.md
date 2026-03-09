# YAPPC Canvas

YAPPC-specific canvas implementation using the generic `@ghatana/canvas` core.

## Overview

This package contains all YAPPC-specific canvas configurations, actions, and behaviors. It uses the generic canvas core from `@ghatana/canvas` and configures it for YAPPC's specific needs:

- **5 Semantic Layers**: architecture, design, component, implementation, detail
- **7 Lifecycle Phases**: INTENT, SHAPE, VALIDATE, GENERATE, RUN, OBSERVE, IMPROVE
- **9 Persona Roles**: product_owner, architect, developer, qa_engineer, devops_engineer, security_engineer, ux_designer, data_engineer, business_analyst
- **110+ Context-Aware Actions**: Layer, phase, and role-specific actions

## Installation

```bash
# From YAPPC product root
npm install @yappc/canvas
```

## Usage

### Basic Setup

```typescript
import { initializeYAPPCCanvas } from '@yappc/canvas';
import { IntegratedCanvasChrome } from '@ghatana/canvas';

// Initialize YAPPC canvas once at app startup
initializeYAPPCCanvas();

function App() {
  return (
    <IntegratedCanvasChrome projectName="My YAPPC Project">
      <YourCanvasContent />
    </IntegratedCanvasChrome>
  );
}
```

### Using YAPPC Types

```typescript
import { 
  YAPPCLayer, 
  YAPPCPhase, 
  YAPPCRole,
  YAPPCActionContext 
} from '@yappc/canvas';

// Use YAPPC-specific types in your code
const currentLayer: YAPPCLayer = 'architecture';
const currentPhase: YAPPCPhase = 'SHAPE';
const activeRoles: YAPPCRole[] = ['architect', 'developer'];
```

### Accessing YAPPC Actions

```typescript
import { 
  ARCHITECTURE_ACTIONS,
  INTENT_ACTIONS,
  ARCHITECT_ACTIONS 
} from '@yappc/canvas';

// Use YAPPC-specific action definitions
console.log('Architecture actions:', ARCHITECTURE_ACTIONS);
console.log('Intent phase actions:', INTENT_ACTIONS);
console.log('Architect role actions:', ARCHITECT_ACTIONS);
```

## Architecture

```
@yappc/canvas (YAPPC Product)
├── yappc/              # YAPPC Configuration
│   ├── yappc-config.ts
│   ├── yappc-actions.ts
│   └── yappc-canvas.ts
├── actions/            # Action Definitions
│   ├── layer-actions.ts
│   ├── phase-actions.ts
│   └── role-actions.ts
└── handlers/           # Action Handlers
    └── canvas-handlers.ts

Dependencies:
└── @ghatana/canvas     # Generic Canvas Core
```

## Features

### Semantic Layers
- **Architecture** (0.1x - 0.5x): System design, services, databases
- **Design** (0.5x - 1.0x): UI components, screens, wireframes
- **Component** (1.0x - 2.0x): Component details, state, events
- **Implementation** (2.0x - 5.0x): Code blocks, functions, classes
- **Detail** (5.0x+): Line-by-line code, debugging

### Lifecycle Phases
- **INTENT**: Ideation, requirements, vision
- **SHAPE**: Architecture, design, structure
- **VALIDATE**: Testing, verification
- **GENERATE**: Code generation, scaffolding
- **RUN**: Execution, deployment
- **OBSERVE**: Monitoring, metrics
- **IMPROVE**: Optimization, refactoring

### Persona Roles
- **Product Owner**: Product vision and requirements
- **Architect**: System architecture and design
- **Developer**: Code implementation
- **QA Engineer**: Quality assurance and testing
- **DevOps Engineer**: Deployment and operations
- **Security Engineer**: Security and compliance
- **UX Designer**: User experience design
- **Data Engineer**: Data pipelines and analytics
- **Business Analyst**: Business analysis

## Development

```bash
# Build
npm run build

# Watch mode
npm run dev

# Clean
npm run clean
```

## Related Packages

- `@ghatana/canvas` - Generic canvas core (shared library)
- `@yappc/app-creator` - YAPPC application creator

## Documentation

See the main YAPPC documentation for more details:
- [YAPPC Canvas Guide](../../docs/canvas/)
- [Architecture Documentation](../../docs/architecture/)

## License

As per Ghatana project license
