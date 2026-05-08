# DMOS Documentation Recovery Coverage

## Purpose

This report documents the historical canonical sources recovered from commit `7432d84601747ed3e095555c11a5f9471f0f8595` and where their substantive content is captured in the self-contained `00–11` DMOS documentation set.

## Recovered Historical Sources

| Source | Status in current repo | Recovered Content |
|---|---|---|
| `digital-marketting.md` | Deleted/root doc no longer present at current head | Market thesis, end-to-end lifecycle, product modules, agents, UX, data model, architecture, differentiators, MVP, roadmap, epics |
| `digital-marketing-product-architecture.md` | Deleted/root doc no longer present at current head | Vision, lifecycle, agent architecture, platform architecture, data model, integrations, governance |
| `digital-marketing-product-architecture-v2.md` | Deleted/root doc no longer present at current head | Executive verdict, MVP narrowing, durable workflow/command/outbox architecture, consent-first measurement, governance, testing, roadmap |
| `digital-marketing-product-architecture-canonical.md` | Deleted/root doc no longer present at current head | Consolidated canonical architecture and implementation contract |

## Coverage Map

| Historical Topic | New Canonical Location |
|---|---|
| Strategic positioning and promise | `00-VISION.md`, `07-MARKET_AND_POSITIONING.md` |
| Full growth lifecycle | `00-VISION.md`, `03-UX_WORKFLOWS.md`, `10-DESIGN.md` |
| MVP narrowing and exclusions | `00-VISION.md`, `06-IMPLEMENTATION_PLAN.md`, `08-PRODUCT_REQUIREMENTS.md` |
| Self-marketing engine | `03-UX_WORKFLOWS.md`, `08-PRODUCT_REQUIREMENTS.md`, `09-FEATURE_CATALOG.md` |
| Proposal/SOW/contract loop | `02-API_CONTRACTS.md`, `03-UX_WORKFLOWS.md`, `08-PRODUCT_REQUIREMENTS.md`, `10-DESIGN.md`, `11-DATA_MODEL.md` |
| Market research and strategy engine | `07-MARKET_AND_POSITIONING.md`, `08-PRODUCT_REQUIREMENTS.md`, `09-FEATURE_CATALOG.md` |
| Campaign planning and execution | `01-ARCHITECTURE.md`, `03-UX_WORKFLOWS.md`, `09-FEATURE_CATALOG.md` |
| Creative and content engine | `03-UX_WORKFLOWS.md`, `08-PRODUCT_REQUIREMENTS.md`, `10-DESIGN.md` |
| Integration categories | `01-ARCHITECTURE.md`, `02-API_CONTRACTS.md`, `05-OPERATIONS.md`, `11-DATA_MODEL.md` |
| Analytics, attribution, experimentation | `04-TESTING.md`, `08-PRODUCT_REQUIREMENTS.md`, `09-FEATURE_CATALOG.md`, `11-DATA_MODEL.md` |
| Consent-first measurement | `02-API_CONTRACTS.md`, `04-TESTING.md`, `08-PRODUCT_REQUIREMENTS.md`, `11-DATA_MODEL.md` |
| Agent taxonomy and agent contract | `01-ARCHITECTURE.md`, `10-DESIGN.md` |
| Human involvement model | `01-ARCHITECTURE.md`, `03-UX_WORKFLOWS.md` |
| Durable workflow, command, outbox/inbox, DLQ | `01-ARCHITECTURE.md`, `02-API_CONTRACTS.md`, `04-TESTING.md`, `08-PRODUCT_REQUIREMENTS.md` |
| Kill switches | `01-ARCHITECTURE.md`, `05-OPERATIONS.md`, `08-PRODUCT_REQUIREMENTS.md` |
| Domain/bounded contexts | `01-ARCHITECTURE.md`, `11-DATA_MODEL.md` |
| Contact/identity and PII-safe lead model | `11-DATA_MODEL.md`, `08-PRODUCT_REQUIREMENTS.md` |
| Governance/compliance model | `01-ARCHITECTURE.md`, `05-OPERATIONS.md`, `08-PRODUCT_REQUIREMENTS.md`, `11-DATA_MODEL.md` |
| Testing and quality gates | `04-TESTING.md`, `06-IMPLEMENTATION_PLAN.md` |
| Delivery roadmap and epics | `06-IMPLEMENTATION_PLAN.md` |
| Risks and mitigations | `05-OPERATIONS.md`, `07-MARKET_AND_POSITIONING.md`, `10-DESIGN.md` |
| Repo/platform alignment and Kernel purity | `01-ARCHITECTURE.md`, `04-TESTING.md` |

## Remaining Caveat

The recovered documents had some market-stat citations and URLs. Because live web browsing is disabled in this environment, those market numbers should be treated as historical source content until externally revalidated. The docs capture the strategic implication but avoid depending on unverified current figures.
