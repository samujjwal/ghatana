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
