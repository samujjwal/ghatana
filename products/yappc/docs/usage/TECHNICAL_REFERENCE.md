# YAPPC – Technical Reference

## 1. Overview

YAPPC is an application creator and quality engine that combines backend services and a rich frontend app-creator.

## 2. Main Components

- **Backend services** – HTTP APIs for requirements, refactoring, knowledge graph, and framework modules.
- **App-creator frontend** – Canvas, page builder, and supporting libraries.
- **Shared libraries** – Extracted into `@ghatana/*` packages for reuse across products.

## 3. Typical Integrations

- Backend services:
  - Expose HTTP endpoints using platform HTTP abstractions.
  - Emit metrics via shared observability.
- Frontend:
  - Communicates with backend services for saving/loading app definitions and related operations.

Products and tools integrating with YAPPC should treat it as the primary source for application definitions, requirements, and related quality signals.

This reference is self-contained and summarizes the main technical surfaces of YAPPC.
