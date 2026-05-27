# YAPPC Documentation

**AI-Native Product Development Platform**

## Source of Truth

**Architecture:** `ARCHITECTURE.md` - Canonical system architecture and module structure  
**Routes:** `api/route-manifest.yaml` - Source of truth for all API routes  
**Security:** `SECURITY.md` - Authentication and authorization policy  
**API Contract:** `api/openapi.yaml` - OpenAPI specification (validated against route manifest)  
**API Reference:** `API_REFERENCE.md` - Generated route reference from OpenAPI and route manifest  
**Build:** `../Makefile` - Build automation and development commands  
**Module Catalog:** `MODULE_CATALOG.md` - Authoritative active/retired module inventory

> **Looking for everything?** → [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md) is the canonical entry point for all YAPPC docs.

## Quick Links

- [Start Here Architecture](START_HERE_ARCHITECTURE.md) - Current module topology, deployable surfaces, and contributor entry points
- [Lifecycle/Kernel/Data Cloud/AEP Diagrams](architecture/YAPPC_LIFECYCLE_KERNEL_DATA_CLOUD_AEP.md) - Current implementation boundaries and evidence loops
- [Module Catalog](MODULE_CATALOG.md) - Authoritative active/retired module inventory
- [Ownership Boundaries](OWNERSHIP_BOUNDARIES.md) - YAPPC vs Kernel vs Data Cloud vs AEP vs platform ownership
- [Data Cloud Collections](DATA_CLOUD_COLLECTIONS.md) - Canonical Data Cloud collection names and validation
- [Product-Family Feature Contract](PRODUCT_FAMILY_FEATURE_CONTRACT.md) - Purpose, ownership boundary, routes, data, permissions, and validation
- [Feature Flags and Entitlements](FEATURE_FLAGS_AND_ENTITLEMENTS.md) - Flag owners, defaults, behavior, and validation evidence
- [Prompt Operations](PROMPT_OPERATIONS.md) - Prompt evaluation, promotion, rollback, rebalancing, and quarantine runbook
- [Agent Operations](AGENT_OPERATIONS.md) - Agent execution state, lifecycle, failure handling, governance, and retention runbook
- [Kernel Handoff](KERNEL_HANDOFF.md) - CLI and API ProductUnitIntent handoff flows, validation, and Kernel boundary rules
- [Development Guide](DEVELOPMENT.md) - Contributing, coding standards, and review expectations
- [YAPPC-Only Check Guide](DEVELOPER_GUIDE.md) - Focused backend, frontend, contract, docs, and release-evidence checks by backlog category
- [Developer Onboarding](onboarding/developer.md) - Local build, test, and run workflow
- [API Ownership Matrix](api/API_OWNERSHIP_MATRIX.md) - API ownership and contract delivery guidance
- [OpenAPI Contract](api/openapi.yaml) - Current HTTP contract under test
- [API Reference](API_REFERENCE.md) - Generated route reference from OpenAPI and route manifest
- [Testing Guide](TESTING.md) - Unit, integration, and browser test guidance
- [Deployment Guide](guides/DEPLOYMENT_GUIDE.md) - Runtime and deployment expectations
- [Docker Local Runtime](deployment/README_DOCKER.md) - Local support services and containers
- [Degraded Dependency Troubleshooting](operations/DEGRADED_DEPENDENCY_TROUBLESHOOTING.md) - Data Cloud, AEP, and Kernel degraded-state diagnosis and remediation
- [ADR: Typed Phase Gate Context](adr/ADR-TYPED-PHASE-GATE-CONTEXT.md) - Accepted lifecycle gate boundary decision
- [ADR: Kernel Contract Registry Import](adr/ADR-KERNEL-CONTRACT-REGISTRY-IMPORT.md) - Accepted Kernel ProductUnit contract import decision

## Module Documentation

- [Core Architecture](CORE_ARCHITECTURE.md) - Core module organization
- [Agent Catalog](agent-list.md) - Agent framework and specialists
- [Scaffolding System](modules/scaffold.md) - Project scaffolding engine
- [Refactoring System](modules/refactorer.md) - Code refactoring engine
- [AI Integration](modules/ai.md) - AI/LLM integration guide

## User Guides

- [Quick Start](guides/quick-start.md) - Get started in 5 minutes
- [AI Workflows](guides/ai-workflows.md) - AI-powered development workflows
- [Canvas Guide](guides/canvas-guide.md) - Visual canvas usage

## Current Topology

Use [START_HERE_ARCHITECTURE.md](START_HERE_ARCHITECTURE.md) as the primary architectural overview. It aligns the docs surface, Gradle settings, and CI workflows around the current module model:

1. `services/` is the deployable application entrypoint.
2. `core/services-platform` and `core/services-lifecycle` are the canonical reusable HTTP/service libraries.
3. `core/yappc-*` modules own the current domain, API, infrastructure, shared, and agent implementation surfaces.
4. Capability families such as `core/agents/*`, `core/scaffold/*`, `core/refactorer/*`, `core/ai`, and `core/knowledge-graph` remain active where they still own real code.
5. Removed or compatibility-only names such as `backend:api`, `core:domain`, `core:framework`, and `core:lifecycle` are historical and must not be treated as primary architecture.

Historical migration and audit material belongs under [archive/](archive/) or the dated audit/implementation-plan directories.

## Support

- **Team:** YAPPC Core Team
- **Slack:** #yappc-dev
- **Issues:** GitHub Issues

---

**Last Updated:** 2026-04-07
