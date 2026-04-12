# @ghatana/platform-events

Canonical cross-cutting event taxonomy, AI visibility contracts, security, privacy, and observability types for the Ghatana platform.

## Purpose

This package provides the foundation for platform-wide observability and AI governance. It defines:

- **Event taxonomy**: Structured events for canvas, builder, and design-system operations
- **AI visibility contracts**: Types for making AI actions transparent and controllable
- **Security types**: Trust levels and sandboxing policies
- **Privacy types**: Data classification and compliance metadata
- **Observability types**: Tracing, metrics, and audit records

## Installation

```bash
pnpm add @ghatana/platform-events
```

## Usage

### Events

```typescript
import {
  createPlatformEvent,
  CanvasEvents,
  type PlatformEvent,
} from '@ghatana/platform-events';

const event = createPlatformEvent(
  CanvasEvents.NODE_CREATED,
  { nodeId: 'node-1', nodeType: 'button' },
  { source: 'canvas', actor: 'user', triggeredBy: 'explicit' }
);
```

### AI Visibility Contract

```typescript
import {
  createAIVisibilityContract,
  createCorrelationId,
} from '@ghatana/platform-events';

const contract = createAIVisibilityContract(
  'Applying layout suggestion',
  createCorrelationId('corr-123')
);
```

### Trust Levels

```typescript
import { getSandboxProfile, TRUST_LEVELS } from '@ghatana/platform-events/security';

const profile = getSandboxProfile('GENERATED_TRUSTED');
```

## Subpath Exports

- `@ghatana/platform-events` - Main exports (events, AI types, visibility)
- `@ghatana/platform-events/events` - Event types and namespaces
- `@ghatana/platform-events/ai` - AI visibility contracts and policy
- `@ghatana/platform-events/security` - Security types and trust levels
- `@ghatana/platform-events/privacy` - Privacy and data classification
- `@ghatana/platform-events/visibility` - User visibility contracts
- `@ghatana/platform-events/observability` - Tracing and metrics

## Event Taxonomy

### Canvas Events (23 required)

- `canvas.viewport.changed`
- `canvas.selection.changed`
- `canvas.node.created`, `canvas.node.updated`, `canvas.node.deleted`
- `canvas.edge.created`, `canvas.edge.updated`, `canvas.edge.deleted`
- `canvas.layout.applied`
- `canvas.import.completed`, `canvas.export.completed`
- `canvas.render.failed`
- `canvas.performance.sampled`
- `canvas.ai.suggestion.shown`, `canvas.ai.suggestion.accepted`, `canvas.ai.suggestion.rejected`
- `canvas.ai.action.applied`
- `canvas.ai.review.requested`, `canvas.ai.review.approved`, `canvas.ai.review.rejected`
- `canvas.ai.override.invoked`
- `canvas.collaboration.peer.joined`, `canvas.collaboration.peer.left`
- `canvas.collaboration.conflict.detected`, `canvas.collaboration.conflict.resolved`

### Builder Events (22 required)

- `builder.document.loaded`
- `builder.component.inserted`, `builder.component.moved`, `builder.component.resized`, `builder.component.configured`
- `builder.pattern.applied`
- `builder.preview.started`, `builder.preview.updated`, `builder.preview.failed`
- `builder.codegen.completed`, `builder.codegen.failed`
- `builder.import.started`, `builder.import.completed`, `builder.import.review_required`
- `builder.ai.suggestion.shown`, `builder.ai.suggestion.accepted`, `builder.ai.suggestion.rejected`
- `builder.ai.action.applied`
- `builder.review.requested`, `builder.review.completed`
- `builder.sync.started`, `builder.sync.completed`, `builder.sync.conflict`
- `builder.code.edited`, `builder.code.ownership.changed`

### Design System Events

- `ds.token.created`, `ds.token.updated`, `ds.token.deprecated`
- `ds.component.registered`, `ds.component.contract.validated`
- `ds.theme.changed`, `ds.preset.applied`
- `ds.governance.violation.detected`
- `ds.ai.suggestion.shown`, `ds.ai.fix.applied`
- `ds.audit.completed`

## Development

```bash
# Install dependencies
pnpm install

# Build
pnpm build

# Type check
pnpm type-check

# Test
pnpm test

# Test with coverage
pnpm test:coverage
```

## License

MIT
