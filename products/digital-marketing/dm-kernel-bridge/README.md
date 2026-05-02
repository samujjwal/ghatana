# dm-kernel-bridge

**Package:** `com.ghatana.digitalmarketing.bridge`

DMOS Kernel Bridge module. Provides the production `AbstractKernelBridge` extension and the product-level adapter interface for DMOS kernel operations.

## Contents

- **`DigitalMarketingKernelAdapter`** — Product-level interface for DMOS kernel operations (authorization, consent, approval, audit). Application services depend on this interface, not on kernel ports directly.
- **`DigitalMarketingKernelAdapterImpl`** — Production implementation extending `AbstractKernelBridge`. Provides:
  - Authorization checks via `BridgeAuthorizationService`
  - Consent verification via `ConsentPlugin`
  - Approval request creation via `HumanApprovalPlugin`
  - Audit recording via `AuditTrailPlugin` with context enrichment

## Usage

```java
DigitalMarketingKernelAdapterImpl adapter = new DigitalMarketingKernelAdapterImpl(
    authService, auditEmitter, healthIndicator,
    consentPlugin, approvalPlugin, auditTrailPlugin
);
adapter.start();

// Authorization check
Promise<Boolean> allowed = adapter.isAuthorized(ctx, "campaigns/c-1", "launch");

// Consent verification
Promise<Boolean> consented = adapter.verifyConsent(ctx, "contact-1", "marketing-email");

// Approval request
Promise<String> requestId = adapter.requestApproval(ctx, "LaunchCampaign", "campaign-1", "Q4 launch");

// Audit recording
Promise<String> entryId = adapter.recordAudit(ctx, "campaign-1", "launch", Map.of("channel", "email"));
```

## Dependencies

- `products:digital-marketing:dm-core-contracts` — `DmOperationContext` and typed value objects
- `products:digital-marketing:dm-domain-packs` — compliance rule set constants
- `platform-kernel:kernel-core` — `AbstractKernelBridge`, bridge ports
- `platform-plugins:plugin-compliance`, `plugin-consent`, `plugin-human-approval`, `plugin-risk-management`, `plugin-audit-trail`
