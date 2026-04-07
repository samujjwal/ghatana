# YAPPC Documentation

**AI-Native Product Development Platform**

## Quick Links

- [Start Here Architecture](START_HERE_ARCHITECTURE.md) - Current module topology, deployable surfaces, and contributor entry points
- [Module Catalog](MODULE_CATALOG.md) - Authoritative active/retired module inventory
- [Development Guide](DEVELOPMENT.md) - Contributing, coding standards, and review expectations
- [Developer Onboarding](onboarding/developer.md) - Local build, test, and run workflow
- [API Checklist](api/API_CHECKLIST.md) - API ownership and contract delivery guidance
- [OpenAPI Contract](api/openapi.yaml) - Current HTTP contract under test
- [Testing Guide](TESTING.md) - Unit, integration, and browser test guidance
- [Deployment Guide](guides/DEPLOYMENT_GUIDE.md) - Runtime and deployment expectations
- [Docker Local Runtime](deployment/README_DOCKER.md) - Local support services and containers

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
