# DMOS Platform Alignment

This document maps DMOS architecture concepts to verified repository symbols.

## Verified Kernel and Platform Symbols

| Architecture Concept | Verified Symbol/Path | Status | Notes |
|---|---|---|---|
| Agent orchestrator entry point | com.ghatana.kernel.ai.AgentOrchestrator | Verified in architecture contract | Use public interface only via bridge/adapters |
| Event bus | com.ghatana.kernel.communication.KernelEventBus | Verified in architecture contract | Product code should consume through public ports |
| Bridge base class | com.ghatana.kernel.bridge.AbstractKernelBridge | Implemented in DMOS | Used by DigitalMarketingKernelAdapterImpl |
| Bridge context | com.ghatana.kernel.bridge.port.BridgeContext | Implemented in DMOS | Built from DmOperationContext |
| Bridge auth | com.ghatana.kernel.bridge.port.BridgeAuthorizationService | Implemented in DMOS | Adapter delegates authorization checks |
| Bridge audit | com.ghatana.kernel.bridge.port.BridgeAuditEmitter | Implemented in DMOS | Adapter emits bridge audit events |
| Bridge health | com.ghatana.kernel.bridge.port.BridgeHealthIndicator | Implemented in DMOS | Adapter reports bridge health |
| Boundary policy SPI | com.ghatana.kernel.policy.BoundaryPolicyStore | Implemented in DMOS | DigitalMarketingBoundaryPolicyStore |
| Compliance plugin | com.ghatana.plugin.compliance.CompliancePlugin | Implemented in DMOS | Rule sets registered at startup |
| Consent plugin | com.ghatana.plugin.consent.ConsentPlugin | Implemented in DMOS bridge usage | Production binding path still needs integration startup wiring test coverage |
| Approval plugin | com.ghatana.plugin.approval.HumanApprovalPlugin | Implemented in DMOS bridge usage | Approval requests bridged through adapter |
| Audit plugin | com.ghatana.plugin.audit.AuditTrailPlugin | Implemented in DMOS bridge usage | Action-level audit recording in adapter |
| Risk plugin | com.ghatana.plugin.risk.RiskManagementPlugin | Bound in manifest/config only | Direct runtime integration pending execution-phase work |

## Product Modules and Build Wiring

| Module | Purpose | Status |
|---|---|---|
| dm-core-contracts | Canonical IDs and operation context | Implemented |
| dm-domain-packs | Boundary policy, compliance rules, startup bindings | Implemented with validation tasks |
| dm-kernel-bridge | Bridge adapter for authorization/consent/approval/audit | Implemented |
| dm-domain | Domain model (campaign focus so far) | Partial (campaign slice) |
| dm-application | Application services and orchestration | Partial (campaign slice) |
| dm-api | HTTP APIs for campaign loop | Partial (campaign slice) |
| dm-integration-tests | Integration tests for current MVP slice | Implemented (limited scope) |

## Gaps and Required Follow-ups

1. Full startup integration for consent/approval/risk/audit plugin bindings should be verified in product startup wiring tests, not only pack-level unit tests.
2. R0 validation now exists in dm-domain-packs check wiring, but similar architecture gate coverage is still needed across all DMOS modules.
3. F1/F2 tasks outside the current campaign slice remain incomplete, including full consent lifecycle APIs, suppression enforcement, asset library, and CRM-lite breadth.
4. End-to-end, load, and performance suites are not yet complete for full plan coverage.

## Guardrails Applied

- Product-specific rule IDs use DM- prefix.
- Default-deny boundary policy rule exists as DM-BP-999.
- Reference consumer hygiene checks block PHR-/FIN- tokens in DMOS pack source.
- Validation tasks are wired to module check: validateDomainPackManifest, validatePolicyPack, validateComplianceRulePack, validateReferenceConsumerHygiene.
