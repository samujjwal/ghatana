# YAPPC – Design & Architecture

## Purpose & Scope

YAPPC is the platform’s **application creator and quality engine**. It combines:

- A visual **app-creator** experience (canvas, layout, components, state, deployment).
- Backend services for requirements, refactoring, knowledge graph, and compliance.
- Shared libraries (`@ghatana/*`) that other products can reuse.

This document summarizes the design from the broader YAPPC product docs:

- See `products/yappc/docs/` and `docs/vision-and-requirements/YAPPC_VISION_AND_REQUIREMENTS.md`

## Functional Requirements

### Backend

- Align all YAPPC backend services with **core/http-server** and **core/observability** abstractions:
  - Controllers must use ResponseBuilder and platform error handling.
  - Metrics collected via shared observability abstractions.
- Provide services for:
  - AI requirements management.
  - Refactoring and code transformation.
  - Knowledge graph storage and queries.
- Ensure full `@doc.*` coverage on public classes:
  - `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`.

### Frontend (App-Creator)

- Provide a canvas-driven app builder:
  - Canvas editing, history, and layout management.
  - Components and design system integration.
- Consolidate state management via **StateManager**:
  - Replace ad-hoc atom patterns in canvas/state libs with StateManager APIs.
- Use design system components from `@ghatana/ui` directly:
  - Eliminate unnecessary `@yappc/ui` re-exports.

### Documentation & Testing

- Provide dedicated guides (per roadmap):
  - `STATE_MANAGEMENT_GUIDE.md` for frontend state.
  - `HTTP_ABSTRACTION_GUIDE.md` for backend HTTP patterns.
  - `TESTING_GUIDE.md` for backend/frontend/E2E testing.
- Achieve planned coverage targets for backend, frontend, and E2E.

## Non-Functional Requirements

### Architecture & Dependencies

- Enforce **correct layering**:
  - YAPPC as an application on top of shared libraries and frameworks (Virtual-Org, AEP).
  - No circular dependencies with shared libs.
- Reuse shared components/state/hooks from `@ghatana/*` wherever possible.

### Quality & Compliance

- Backend:
  - Full HTTP abstraction compliance.
  - Observability via shared telemetry abstractions.
- Frontend:
  - Sensible performance targets (page load, interaction latency) as defined in roadmap.
  - Design system compliance and accessibility.

## Detailed Feature Areas

> Status is taken from the roadmap (Phase 2–4 tasks).

### Phase 2 – Backend Implementation

- HTTP abstraction verification in all controllers.
- `@doc.*` tag completion for key modules.
- Observability audits and build optimization.

### Phase 3 – Frontend Implementation

- Canvas state migration to StateManager.
- Consolidation of `libs/state` utilities.
- Component imports updated to use `@ghatana/ui` directly.
- Mobile app state migration and AI requirements UI integration.

### Phase 4 – Testing & Documentation

- Comprehensive backend, frontend, and E2E testing.
- Creation of state management, HTTP abstraction, and testing guides.
- Final review and sign-off with release notes.

## Architectural Overview

### Layering

- YAPPC **uses**:
  - Shared libraries (`@ghatana/*` for UI, state, tokens, etc.).
  - Virtual-Org and AEP for organizational/event behavior where applicable.
- YAPPC **exposes**:
  - App-creator UI.
  - Backend services and shared packages that other products can reuse.

### Major Components

- Backend services:
  - Requirements, refactorer, KG, and framework modules.
- Frontend app-creator:
  - Canvas, page-builder, state libraries, design system integration.

## Module Boundaries & Dependencies

### Owns

- Product-specific backend services and APIs.
- App-creator frontend and supporting frontend libraries.
- Documentation and testing patterns specific to YAPPC.

### Depends On

- Core platform modules (HTTP server, observability, config).
- Shared TypeScript libraries for UI, state, and tokens.
- Frameworks such as Virtual-Org/AEP when modeling organizational flows.

## Future Directions

- Complete consolidation of state management and design system usage.
- Tighten integration with Virtual-Org and AEP where needed.
- Maintain high-quality documentation and testing as YAPPC evolves.
