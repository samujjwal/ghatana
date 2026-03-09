# Ghatana Remaining Migration Plan

This plan details the migration of remaining modules from `ghatana/libs/java` to the new modular structure in `ghatana-new`.

## 1. Platform Modules (True Shared Infrastructure)

These modules are foundational and shared across all products.

| Source Module (`ghatana/libs/java`) | Target Module (`ghatana-new/platform/java`) | Description |
| ----------------------------------- | ------------------------------------------- | ----------- |
| `domain-models` | `domain` | Core domain types, shared entities. |
| `http-client` | `http` | HTTP Client abstractions and adapters. Merge into existing http module. |
| `activej-runtime` | `runtime` | Application launchers, Eventloop managers. |
| `activej-websocket` | `http` | WebSocket support. Merge into existing http module. |
| `audit` | `observability` | Audit logging and tracing. Merge into observability. |
| `observability-http` | `observability` | HTTP tracing/metrics. Merge into observability. |
| `observability-clickhouse` | `observability` | ClickHouse exporters. Merge into observability. |
| `plugin-framework` | `plugin` | Plugin system core. |
| `config-runtime` | `config` | Configuration loading and management. |
| `architecture-tests` | `testing` | ArchUnit tests. Merge into testing. |
| `platform-architecture-tests` | `testing` | More architecture tests. Merge into testing. |

## 2. Product: AEP (Autonomous Event Processing)

Modules specific to the AEP product.

| Source Module (`ghatana/libs/java`) | Target Module (`ghatana-new/products/aep/platform/java`) | Description |
| ----------------------------------- | -------------------------------------------------------- | ----------- |
| `operator` | `operators` | Stream processing operators. |
| `operator-catalog` | `operators` | Standard operator implementation library. |
| `agent-api` | `agents` | Agent interfaces. |
| `agent-core` | `agents` | Core agent logic. |
| `agent-framework` | `agents` | Agent orchestration framework. |
| `agent-runtime` | `agents` | Runtime for executing agents. |
| `event-cloud` | `events` | Event Cloud core. |
| `event-cloud-contract` | `events` | Event Cloud contracts/interfaces. |
| `event-cloud-factory` | `events` | Factories for Event Cloud components. |
| `event-runtime` | `events` | Event runtime execution. |
| `event-spi` | `events` | Service Provider Interfaces for events. |
| `workflow-api` | `workflow` | Workflow definitions and execution context. |

## 3. Product: Data Cloud

Modules specific to Data Cloud.

| Source Module (`ghatana/libs/java`) | Target Module (`ghatana-new/products/data-cloud/platform/java`) | Description |
| ----------------------------------- | --------------------------------------------------------------- | ----------- |
| `governance` | `governance` | Data governance and policies. |
| `ingestion` | `ingestion` | Data ingestion pipelines. |
| `state` | `storage` | State management. |

## 4. Product: Shared Services

Cross-product capabilities that are not core platform.

| Source Module (`ghatana/libs/java`) | Target Module (`ghatana-new/products/shared-services/platform/java`) | Description |
| ----------------------------------- | -------------------------------------------------------------------- | ----------- |
| `ai-integration` | `ai` | AI Integration services. |
| `ai-platform` | `ai` | AI Platform core. |
| `connectors` | `connectors` | External system connectors. |

## 5. Product: Security Gateway

Security Gateway product modules.

| Source Module (`ghatana/libs/java`) | Target Module (`ghatana-new/products/security-gateway/platform/java`) | Description |
| ----------------------------------- | --------------------------------------------------------------------- | ----------- |
| `auth-platform` | `auth` | Security Gateway specific auth logic. |
| `security` | `auth` | **Note:** Contains `SecurityGateway.java`. Moving to product auth module. |

## 6. Product: Flashit

Flashit product modules.

| Source Module (`ghatana/libs/java`) | Target Module (`ghatana-new/products/flashit/platform/java`) | Description |
| ----------------------------------- | ------------------------------------------------------------ | ----------- |
| `context-policy` | `context` | Context policy management. |

---

## Detailed File Migration List

### Platform: Domain
**Target:** `ghatana-new/platform/java/domain`
- `ghatana/libs/java/domain-models/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/domain-models/src/test/**/*` -> `src/test/**/*`

### Platform: Runtime
**Target:** `ghatana-new/platform/java/runtime`
- `ghatana/libs/java/activej-runtime/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/activej-runtime/src/test/**/*` -> `src/test/**/*`

### Platform: HTTP (Consolidated)
**Target:** `ghatana-new/platform/java/http`
- `ghatana/libs/java/http-client/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/http-client/src/test/**/*` -> `src/test/**/*`
- `ghatana/libs/java/activej-websocket/src/main/**/*` -> `src/main/**/*` (If exists)

### Platform: Observability (Consolidated)
**Target:** `ghatana-new/platform/java/observability`
- `ghatana/libs/java/audit/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/observability-http/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/observability-http/src/test/**/*` -> `src/test/**/*`
- `ghatana/libs/java/observability-clickhouse/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/observability-clickhouse/src/test/**/*` -> `src/test/**/*`

### Platform: Plugin
**Target:** `ghatana-new/platform/java/plugin`
- `ghatana/libs/java/plugin-framework/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/plugin-framework/src/test/**/*` -> `src/test/**/*`

### Platform: Config
**Target:** `ghatana-new/platform/java/config`
- `ghatana/libs/java/config-runtime/src/main/**/*` -> `src/main/**/*`

### Product: AEP - Agents
**Target:** `ghatana-new/products/aep/platform/java/agents`
- `ghatana/libs/java/agent-api/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/agent-core/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/agent-framework/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/agent-framework/src/test/**/*` -> `src/test/**/*`
- `ghatana/libs/java/agent-runtime/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/agent-runtime/src/test/**/*` -> `src/test/**/*`

### Product: AEP - Operators
**Target:** `ghatana-new/products/aep/platform/java/operators`
- `ghatana/libs/java/operator/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/operator/src/test/**/*` -> `src/test/**/*`
- `ghatana/libs/java/operator-catalog/src/main/**/*` -> `src/main/**/*`

### Product: AEP - Events
**Target:** `ghatana-new/products/aep/platform/java/events`
- `ghatana/libs/java/event-cloud/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/event-cloud/src/test/**/*` -> `src/test/**/*`
- `ghatana/libs/java/event-cloud-contract/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/event-cloud-factory/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/event-runtime/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/event-spi/src/main/**/*` -> `src/main/**/*`

### Product: AEP - Workflow
**Target:** `ghatana-new/products/aep/platform/java/workflow`
- `ghatana/libs/java/workflow-api/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/workflow-api/src/test/**/*` -> `src/test/**/*`

### Product: Data Cloud - Governance
**Target:** `ghatana-new/products/data-cloud/platform/java/governance`
- `ghatana/libs/java/governance/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/governance/src/test/**/*` -> `src/test/**/*`

### Product: Data Cloud - Ingestion
**Target:** `ghatana-new/products/data-cloud/platform/java/ingestion`
- `ghatana/libs/java/ingestion/src/main/**/*` -> `src/main/**/*`

### Product: Data Cloud - Storage
**Target:** `ghatana-new/products/data-cloud/platform/java/storage`
- `ghatana/libs/java/state/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/state/src/test/**/*` -> `src/test/**/*`

### Product: Shared Services - AI
**Target:** `ghatana-new/products/shared-services/platform/java/ai`
- `ghatana/libs/java/ai-integration/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/ai-integration/src/test/**/*` -> `src/test/**/*`
- `ghatana/libs/java/ai-platform/**/src/main/**/*` -> `src/main/**/*` (Merge submodules if necessary)
- `ghatana/libs/java/ai-platform/**/src/test/**/*` -> `src/test/**/*`

### Product: Shared Services - Connectors
**Target:** `ghatana-new/products/shared-services/platform/java/connectors`
- `ghatana/libs/java/connectors/src/main/**/*` -> `src/main/**/*` (If it exists, listed in pending)

### Product: Security Gateway - Auth
**Target:** `ghatana-new/products/security-gateway/platform/java/auth`
- `ghatana/libs/java/auth-platform/src/main/**/*` -> `src/main/**/*` (Merge strict)
- `ghatana/libs/java/auth-platform/**/src/main/**/*` -> `src/main/**/*` (Handle subdirs like `oauth/core`)
- `ghatana/libs/java/auth-platform/**/src/test/**/*` -> `src/test/**/*`
- `ghatana/libs/java/security/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/security/src/test/**/*` -> `src/test/**/*`

### Product: Flashit - Context
**Target:** `ghatana-new/products/flashit/platform/java/context`
- `ghatana/libs/java/context-policy/src/main/**/*` -> `src/main/**/*`
- `ghatana/libs/java/context-policy/src/test/**/*` -> `src/test/**/*`
