# Product-on-Kernel Example: Building a Domain Product

This directory shows a complete, end-to-end example of how a fictional product — **Nexus**, a logistics and supply-chain platform — is built on top of the Ghatana kernel without modifying it. Every concept shown here applies to any domain: healthcare, finance, e-commerce, manufacturing, government, etc.

## What the Example Covers

| File | What it shows |
|---|---|
| `nexus-domain-pack-manifest.yaml` | Domain pack registration — capabilities, scopes, regulatory frameworks |
| `nexus-boundary-policy-store.java` | Product boundary rules, composed generically with `DefaultBoundaryPolicyResolver` |
| `nexus-compliance-rule-pack.java` | Product compliance rules, loaded by the generic `CompliancePlugin` |
| `nexus-plugin-bindings.yaml` | Product-to-plugin binding: which platform plugins Nexus activates and with what config |
| `nexus-schema-registry.yaml` | Product data schemas (shipments, carriers, manifests, etc.) |
| `nexus-policy-packs/logistics-boundary-policy.yaml` | Product-specific boundary access control rules |
| `nexus-compatibility-test.java` | Proves Nexus packs compose with the generic kernel resolver without kernel modification |

## Key Principles Demonstrated

1. **Zero kernel changes**: Nexus adds capabilities entirely through configuration and product-layer Java classes. Nothing in `platform-kernel` or `platform-plugins` is modified.
2. **Policy-driven access control**: All cross-scope access rules are declared in `NexusBoundaryPolicyStore`, evaluated by the generic `DefaultBoundaryPolicyResolver`.
3. **Plugin configuration not extension**: Nexus configures platform plugins (consent, risk, compliance, audit) via binding manifests; it does not fork or subclass them.
4. **Domain vocabulary isolation**: Nexus terms (shipment, carrier, manifest, customs) appear only in `products/nexus/`, never in platform code.
5. **Compliance as data**: Regulatory frameworks (IATA, IMO, customs regulations) are declared in YAML; the generic `CompliancePlugin` evaluates them.
