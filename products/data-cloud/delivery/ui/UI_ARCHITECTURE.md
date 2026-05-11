# Data Cloud UI Architecture

**Status:** Canonical
**Owner:** UI Team
**Last reviewed:** 2026-05-10
**Supersedes:** `ARCHITECTURE.md`, `docs/DESIGN_ARCHITECTURE.md`
**Superseded by:** N/A

## Overview

The Data Cloud UI is a React-based web application that provides a unified interface for data operations, governance, AI/automation, and connector management. It follows the plane architecture pattern and integrates with the Data Cloud backend APIs.

## Architecture

### Technology Stack

- **Framework:** React 18+ with TypeScript
- **Build System:** Vite
- **Package Manager:** pnpm
- **Styling:** TailwindCSS + custom design system
- **State Management:** React Query (@tanstack/react-query) for server state
- **Routing:** React Router
- **Testing:** Vitest + Playwright

### Directory Structure

```
delivery/ui/
├── src/
│   ├── pages/           # Page components
│   ├── components/      # Reusable UI components
│   ├── hooks/           # Custom React hooks
│   ├── api/             # API client services
│   ├── contexts/        # React contexts
│   └── lib/             # Utility libraries
├── e2e/                 # End-to-end tests
└── scripts/             # Build and utility scripts
```

### Key Patterns

#### Surface-Based Gating

UI features are gated using runtime surface signals from the `/api/v1/surfaces` endpoint. Use `useSurfaceGate` hook to conditionally render features based on surface availability.

```typescript
import { useSurfaceGate } from '../hooks/useSurfaceGate';

const canWriteGovernance = useSurfaceGate(['governance.write'], 'active');
```

#### API Integration

All API calls use the centralized API services in `src/api/`. These services use React Query for caching and optimistic updates.

#### Error Handling

Use the canonical error envelope for all error states. The `ErrorEnvelope` class provides consistent error reporting across the UI.

## Design System

The Data Cloud UI uses a custom design system built on top of TailwindCSS. See `DESIGN_SYSTEM_USAGE.md` for detailed usage guidelines.

## Testing Strategy

See `UI_TESTING_STRATEGY.md` for comprehensive testing guidelines.

## User Manual

See `USER_MANUAL.md` for end-user documentation.
