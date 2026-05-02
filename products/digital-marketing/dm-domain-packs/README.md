# dm-domain-packs

**Package:** `com.ghatana.digitalmarketing.pack`

DMOS domain packs module. Provides boundary policy rules, compliance rule packs, and plugin startup bindings for the Digital Marketing Operating System.

## Contents

### Boundary Policy

- **`DigitalMarketingBoundaryPolicyStore`** — Implements `BoundaryPolicyStore` SPI. Returns 9 DMOS boundary policy rules (DM-BP-001 through DM-BP-999) controlling access to workspaces, contacts, audiences, campaigns, budgets, content, and connectors.

### Compliance Rule Sets

- **`DmComplianceRuleSetIds`** — Constants for all 7 DMOS compliance rule set identifiers.
- **`DigitalMarketingComplianceRulePack`** — Factory supplying `ComplianceRule` lists for each rule set.

### Plugin Startup Bindings

- **`DigitalMarketingPluginBindings`** — Registers all 7 DMOS compliance rule packs with the `CompliancePlugin` at startup.

## Compliance Rule Sets

| Rule Set ID | Description |
|---|---|
| `DM_MARKETING_INTEGRITY` | Truthfulness, misleading-claim detection, brand safety |
| `DM_CONSENT_LIFECYCLE` | Opt-in/opt-out state, GDPR/CCPA consent, double opt-in |
| `DM_AUDIT_TRACEABILITY` | Audit entry presence for high-risk operations |
| `DM_CAMPAIGN_PREFLIGHT` | Campaign readiness validation before launch |
| `DM_CLAIMS_DISCLOSURES` | Legal-language presence, prohibited claims |
| `DM_EMAIL_COMPLIANCE` | CAN-SPAM, GDPR, SPF/DKIM, unsubscribe |
| `DM_CONNECTOR_EXECUTION_SAFETY` | Credential validity, PII masking, rate limits |

## Startup Usage

```java
DigitalMarketingPluginBindings bindings = new DigitalMarketingPluginBindings(compliancePlugin);
bindings.registerAll(); // call during kernel extension onInitialize()
```

## Dependencies

- `products:digital-marketing:dm-core-contracts`
- `platform-kernel:kernel-core` — `BoundaryPolicyStore`, `BoundaryPolicyRule`
- `platform-plugins:plugin-compliance`
- `platform-plugins:plugin-consent`
- `platform-plugins:plugin-human-approval`
- `platform-plugins:plugin-risk-management`
- `platform-plugins:plugin-audit-trail`
