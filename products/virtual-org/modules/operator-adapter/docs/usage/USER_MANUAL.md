# Virtual-Org – Core Operator Adapter – User Manual

## 1. Audience

This manual is for backend engineers who use the core Operator Adapter library to connect Virtual‑Org agents to the operator framework.

## 2. Basic Usage

1. Add the Operator Adapter library as a dependency.
2. Decide which Virtual‑Org agents will be exposed as operators.
3. Configure adapters to map operator requests to agent calls and agent responses to operator outputs.
4. Register adapters with the operator runtime and ensure observability hooks are configured.

## 3. Best Practices

- Keep adapter logic focused on mapping and contract handling; let agents and operators own business logic.
- Use configuration for environment-specific settings and observability options.

This manual is self-contained and explains how to use the core Operator Adapter library in typical scenarios.
