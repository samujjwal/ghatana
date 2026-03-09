# YAPPC Multi-Agent System (MAS) Design — Master Index

> **Version**: 1.0.0 | **Status**: Complete Design Document  
> **Primary Stack**: Gradle/pnpm monorepo · React+Vite · Tauri · Fastify+Prisma · Java+ActiveJ · PostgreSQL  
> **Canonical Lifecycle**: Discover → Define → Design → Plan → Build → Debug → Test → Release → Deploy → Operate → Improve

---

## Document Structure

This design is split across five files for practical editability:

| File | Sections | Content |
|------|----------|---------|
| `MAS_SECTION_A_B.md` | A, B | Current-state reconciliation, canonical taxonomy |
| `MAS_SECTION_C_CATALOG.md` | C | Full agent catalog (227 agents) |
| `MAS_SECTION_D_E.md` | D, E | Lifecycle crosswalk matrix, logical agent graphs |
| `MAS_SECTION_F_G.md` | F, G | Execution model, knowledge & memory plane |
| `MAS_SECTION_H_I.md` | H, I | Security/governance/compliance plane, evaluation flywheel |
| `MAS_SECTION_J_K.md` | J, K | YAPPC integration hooks, migration plan |

---

## Executive Summary

### What exists today (3 surfaces, not unified)

| Surface | What it is | Agent count | Runtime? |
|---------|-----------|-------------|---------|
| YAML Catalog (`config/agents/`) | 154 agents defined, 14 phases, 3-level hierarchy | 154 | No (config only) |
| Java Runtime (`agent-framework`, `agent-resilience`, `agent-registry`) | TypedAgent, ResilientTypedAgent, CheckpointedTypedAgent, CatalogLoader, DataCloudAgentRegistry | ~15–20 | Yes |
| UI Lifecycle (`lifecycle/stages.yaml`) | 8 stages: intent→context→plan→execute→verify→observe→learn→institutionalize | N/A | User journey |

### Critical existing defects (fix before expansion)

| # | Defect | Fix |
|---|--------|-----|
| D1 | `quality-guard` dangling reference in `registry.yaml` — no definition file | Create `quality-guard-agent.yaml` |
| D2 | `operations-orchestrator-agent.yaml` declares `level: 1` but registered as `level_2_experts` | Change to `level: 2` |
| D3 | YAPPC catalog not registered in `CatalogRegistry` ServiceLoader | Create `YappcAgentCatalog.java` + service file |
| D4 | No `AgentDispatcher` — bridge between catalog and runtime is missing | Implement `AgentDispatcher` (Phase 1) |
| D5 | No debug phase — debugging capability in `capabilities.yaml` but no orchestrator or agents | Create debug phase (Phase 2) |

### Target state (after full migration)

- **227 agents** (117 existing + 80 proposed + 30 gap-fill)
- **11 lifecycle orchestrators** covering all SDLC phases
- **All 154 existing YAML agents** become invocable via 3-tier dispatch (Tier-J / Tier-S / Tier-L)
- **Complete SDLC loop**: Discover → … → Improve → feeds next Discover
- **Evaluation flywheel**: Every run contributes to continuous improvement
- **Full SGC plane**: Policy-as-code, zero-trust secrets, immutable audit log, SBOM, SLSA

---

## Agent Count Breakdown by Status

| Status | Count | Definition |
|--------|-------|-----------|
| `existing` [E] | 154 | Defined in `config/agents/registry.yaml` |
| `proposed` [P] | 50 | New agents designed; not yet in catalog |
| `gap-fill` [G] | 23 | Critical absences or broken references |
| **Total** | **227** | |

## Agent Count Breakdown by Layer

| Layer | Name | Count |
|-------|------|-------|
| 0 | Global Orchestrator | 1 |
| 1 | Lifecycle Orchestrators | 11 |
| 2 | Capability Coordinators | 16 |
| 3 | Micro-Agents | 179 |
| 4 | Guardrail Agents | 11 |
| 5 | Integration Agents | 9 |
| **Total** | | **227** |

---

## Migration Timeline (10-week plan)

| Phase | Weeks | Deliverable |
|-------|-------|------------|
| 0 — Critical Fixes | 1 | 5 defects fixed; catalog valid |
| 1 — AgentDispatcher | 1–2 | All 154 YAML agents invocable |
| 2 — Debug Phase | 3 | 12 debug agents; debug workflow |
| 3 — Quality & Release | 3–4 | Quality gate; SBOM/SLSA |
| 4 — Integration Agents | 4–5 | Repo, DB, observability, secrets bridges |
| 5 — Stack Micro-Agents | 5–7 | Full-stack YAPPC code generation |
| 6 — Semantic Memory | 7–8 | pgvector + knowledge plane live |
| 7 — Evaluation Flywheel | 8–9 | CI-gated eval; human corrections → golden set |
| 8 — Full Lifecycle | 9–10 | Improve orchestrator; end-to-end SDLC demo |

---

## Key Design Decisions

1. **Catalog-first**: YAML is the single source of truth. Runtime is downstream.
2. **Three execution tiers**: Tier-J (Java), Tier-S (delegation chain), Tier-L (LLM prompt) — not binary.
3. **11 canonical phases** map explicitly to 8 UI stages and 14 config phases (crosswalk in Section D).
4. **Debug is a first-class phase** — not a sub-state of Build or Test.
5. **Guardrail plane is transparent** — policy-guard, cost-governor, audit-trail intercept every action without agent coordination.
6. **Plugin-first for stacks** — new stacks attach as plugins; core orchestrators never change.
7. **No false runtime parity claims** — existing coverage is accurately documented; gaps are explicitly labeled.
