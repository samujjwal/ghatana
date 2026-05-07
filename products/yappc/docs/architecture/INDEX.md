# YAPPC – Architecture Docs

This directory groups all **design & architecture** documentation for YAPPC.

## Core sources

High-level roadmap and architectural corrections are defined in the YAPPC product docs:

- `products/yappc/docs/`

That roadmap describes:

- Backend alignment with core/http-server and core/observability.
- Documentation and `@doc.*` coverage requirements.
- Frontend state consolidation and design system usage.
- Testing and documentation phases.

## Planned sub-documents

Examples of focused docs to live here:

- `BACKEND_ARCHITECTURE.md` – Service boundaries, HTTP/API design, observability.
- `FRONTEND_ARCHITECTURE.md` – App-creator structure, canvas, state management.
- `STATE_MANAGEMENT_ARCHITECTURE.md` – StateManager usage across apps.
- `INTEGRATIONS_AND_DEPENDENCIES.md` – How YAPPC integrates with core libs, Virtual-Org, AEP.

`README.md` (in this docs directory) provides the high-level overview; this folder is the home for deeper YAPPC architecture docs.

## Current reference documents

- `YAPPC_CANONICAL_MODELS.md` - Active contributor reference for the product, artifact, lifecycle, builder, preview trust, and governance trace models.
- `YAPPC_STRUCTURE_SIMPLIFICATION_PLAN.md` - Comprehensive review of the current product structure, boundary problems, and the phased simplification plan.
