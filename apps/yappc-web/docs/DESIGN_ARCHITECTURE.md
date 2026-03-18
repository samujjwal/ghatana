# YAPPC App-Creator Web – Design & Architecture

## Purpose & Scope

The YAPPC app-creator web app is the **primary frontend surface** for designing and managing applications in YAPPC. It aims to:

- Provide a rich canvas and editor experience.
- Integrate deeply with YAPPC backend services.
- Demonstrate best-practice usage of `@ghatana/ui`, StateManager, and shared libs.

This document is aligned with the broader YAPPC roadmap and architecture docs:

- See `products/yappc/docs/` and `docs/vision-and-requirements/YAPPC_VISION_AND_REQUIREMENTS.md`

## Functional Requirements

### Canvas & Layout

- Provide a canvas to build and edit application screens.
- Manage layout, components, and interactions visually.
- Support undo/redo and history operations.

### State Management

- Consolidate state management through StateManager:
  - Replace ad-hoc atom patterns in canvas and other feature libs.
  - Ensure persistence and stable behavior for complex editing workflows.

### Design System Integration

- Use `@ghatana/ui` components directly for all UI elements.
- Rely on shared tokens and themes for styling.

### Backend Integration

- Connect to YAPPC backend services for:
  - Saving and loading app definitions.
  - AI-assisted requirements and design flows.
  - Refactoring and cross-component operations.

## Non-Functional Requirements

### Performance & UX

- Provide a responsive editing experience on reasonably sized pages.
- Keep page load and interaction latency within targets defined by the YAPPC roadmap.

### Reliability & Testing

- Maintain strong unit and integration test coverage.
- Ensure that state persistence and undo/redo are well-tested.

## Architectural Overview

### Layering

- The web app is a **product frontend** on top of:
  - Shared frontend libs (StateManager, `@ghatana/ui`, etc.).
  - YAPPC backend services.

### Major Components

- Canvas and layout editor modules.
- State management layer via StateManager.
- Integration layer for backend communication.

## Module Boundaries & Dependencies

### Owns

- App-creator UI components and feature modules.

### Depends On

- YAPPC backend services.
- Shared `@ghatana/*` libraries.

## Future Directions

- Fully align state management and design system usage with shared platform patterns.
- Provide detailed architecture, state, and integration docs under `docs/architecture/`.
- Expand user and technical documentation under `docs/usage/`.
