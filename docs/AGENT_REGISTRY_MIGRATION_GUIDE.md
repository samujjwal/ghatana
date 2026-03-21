# Agent Registry Migration Guide

**Date:** 2026-03-21  
**Phase:** Phase 4 — Centralize Registry APIs & Runtime Operations  
**Status:** Active

---

## Overview

The AEP runtime is the **single** registry and runtime admin surface for all agent operations.
Products no longer own or expose their own registry endpoints. This guide explains the
cutover path for each product.

---

## Architecture After Migration

```
┌─────────────────────────────────────────────────┐
│                  AEP Server                      │
│  ┌──────────────────────────────────────────┐   │
│  │  AgentController (/api/v1/agents/*)      │   │
│  │  ┌──────────────────────────────────┐    │   │
│  │  │  AepCentralRegistryService       │    │   │
│  │  │  ├── AepCentralCatalogService    │    │   │
│  │  │  ├── AgentMaterializer           │    │   │
│  │  │  │   └── AgentLogicProviderReg.  │    │   │
│  │  │  └── Live agent instances        │    │   │
│  │  └──────────────────────────────────┘    │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
         ▲           ▲           ▲
         │           │           │
   ┌─────┘     ┌─────┘     ┌─────┘
   │           │           │
 YAPPC    Data Cloud    Other Products
(provider) (provider)   (providers)
```

## Unified Endpoint: `/api/v1/agents`

| Operation | Method | Path | Notes |
|-----------|--------|------|-------|
| List agents | GET | `/api/v1/agents` | Catalog + live agents |
| Get agent | GET | `/api/v1/agents/:agentId` | Catalog entry + status |
| Execute agent | POST | `/api/v1/agents/:agentId/execute` | Delegates to live instance |
| Health check | GET | `/api/v1/agents/:agentId/health` | Live agent health |
| Delete agent | DELETE | `/api/v1/agents/:agentId` | Deregister + shutdown |
| Memory | GET | `/api/v1/agents/:agentId/memory` | Episodic/semantic/procedural |
| History | GET | `/api/v1/agents/:agentId/history` | Execution history |
| Policies | GET | `/api/v1/agents/:agentId/policies` | Learned policies |

---

## Per-Product Cutover

### YAPPC

**Endpoints to retire:**

| Old Path | New Path | Notes |
|----------|----------|-------|
| `/api/agents` (GET) | `/api/v1/agents` | Use AEP registry |
| `/api/agents/execute` (POST) | `/api/v1/agents/:agentId/execute` | Use AEP execution |
| `/api/agents/execute/batch` (POST) | `/api/v1/agents/:agentId/execute` (batch body) | Via AEP |
| `/api/agents/role/:role` (GET) | `/api/v1/agents?capability=:role` | Capability-based query |
| `/api/agents/:id/health` (GET) | `/api/v1/agents/:agentId/health` | AEP health check |

**Code to remove in Phase 5:**
- `YAPPCAgentRegistry` — replaced by `AepCentralRegistryService`
- `YappcAgentCatalog` — replaced by `AepCentralCatalogService`
- `YappcAgentRegistryAdapter` — no longer needed
- `WorkflowAgentController` (agent listing/health portions) — redirect to AEP

**What stays in YAPPC:**
- `YappcAgentLogicProvider` — remains as the SPI implementation
- `YAPPCAgentBase` — domain agent base class
- Specialist agent classes — business logic unchanged

### Data Cloud

**Endpoints to retire:**

| Old Path | New Path | Notes |
|----------|----------|-------|
| `/api/v1/agents` (GET) | `/api/v1/agents` (AEP) | AEP is sole read surface |
| `/api/v1/agents` (POST) | Internal only | Registration via catalog + provider SPI |
| `/api/v1/agents/:agentId` (GET) | `/api/v1/agents/:agentId` (AEP) | AEP is sole surface |
| `/api/v1/agents/:agentId` (DELETE) | `/api/v1/agents/:agentId` (AEP) | Via AEP |

**Code to remove in Phase 6:**
- `AgentRegistryHandler` — agent CRUD moves to AEP
- `DataCloudAgentRegistry` — persistence layer may stay, but API surface retires

**What stays in Data Cloud:**
- `DataCloudAgentLogicProvider` — remains as the SPI implementation
- `RegistryEventPublisher` — event sourcing for audit

### Other Products

- **Virtual Org:** No agent registry endpoints; uses AEP indirectly. No action needed.
- **Finance:** No agent registry endpoints. No action needed.
- **App Platform:** Uses AEP platform SDK/DLQ. Ensure SDK points to AEP centralized endpoints.
- **Tutorputor:** No agent registry endpoints. No action needed.

---

## Collection Reconciliation

DataCloud currently stores agents in two collections:
- `dc_agents` — DataCloud-registered agents
- `agent-registry` — AEP-registered agents

**Action:** Merge into single `agents` collection accessed only through `AepCentralRegistryService`.

---

## Timeline

| Phase | Action | Status |
|-------|--------|--------|
| Phase 4 | Centralized service + deprecation markers | ✅ Done |
| Phase 5 | YAPPC cutover (remove local registry) | Pending |
| Phase 6 | Data Cloud + other products cutover | Pending |
| Phase 7 | Remove deprecated classes + code | Pending |
