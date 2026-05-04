## TODOs: Kernel / Platform Plugin Boundary Hardening

### P0 — Immediate boundary fixes

1. **Remove product/domain-specific constants from `DefaultBoundaryPolicyResolver`.**
   Replace hardcoded examples like `product-health`, `healthcare-pack`, `patient.records`, `trade.records`, and `sebon` with generic policy evaluation. These currently create product bleed inside kernel-core. 

2. **Introduce a generic `BoundaryPolicyStore` abstraction.**
   Kernel should load boundary rules from product/domain policy packs instead of owning the rules directly.

3. **Introduce a generic `PolicyEvaluator`.**
   Kernel should evaluate `PolicyRequest → PolicyDecision`; it should not know whether the resource is a patient record, trade record, order, lab result, or anything product-specific.

4. **Move PHR-specific boundary rules to `products/phr/policy-packs`.**
   Examples: patient record access, emergency access, clinical data write restrictions, FHIR/HL7/HIE access rules.

5. **Move Finance-specific boundary rules to `products/finance/policy-packs`.**
   Examples: trade record access, order mutation rules, risk approval, market surveillance, SEBON/MiFID/SOX/PCI rules.

6. **Update capability matrix wording for `plugin-consent`.**
   Change “Patient consent lifecycle” to generic “consent, authorization grant, delegation, revocation, and purpose-bound access lifecycle.” 

7. **Clarify that Finance must not own the platform ledger implementation.**
   Finance may consume the ledger plugin or provide finance-specific ledger mappings, but the platform ledger plugin must remain product-independent.

8. **Add CI scan for forbidden product concepts in `platform-kernel/**`.**
   Block terms like `patient`, `FHIR`, `HL7`, `trade`, `order`, `portfolio`, `SEBON`, `HIPAA`, `SOX`, `phr`, `finance`, `yappc`, `aep`, and `data-cloud`, except in approved examples/tests.

9. **Add ArchUnit rule: kernel must not depend on products.**
   Enforce that `platform-kernel/**` cannot import or depend on `products/**`.

10. **Add ArchUnit rule: platform plugins must not depend on product implementations.**
    Enforce that `platform-plugins/**` cannot import `products/phr`, `products/finance`, `products/yappc`, `products/data-cloud`, or `products/aep`.

---

### P1 — Platform plugin cleanup

11. **Rename or reframe `plugin-ledger`.**
    Prefer `plugin-ledger` or `plugin-accounting-ledger`; “billing” sounds product/use-case-specific.

12. **Ensure `plugin-compliance` is only a rule evaluation engine.**
    HIPAA, SOX, PCI-DSS, GDPR, SEBON, Nepal Directive 2081, etc. should be external/versioned rule packs, not hardcoded plugin behavior.

13. **Ensure `plugin-risk-management` is a generic risk/decision scoring framework.**
    Finance-specific risk models must live in Finance rule/model packs.

14. **Ensure `plugin-fraud-detection` is a generic anomaly/abuse/fraud signal engine.**
    Product-specific fraud rules/signals must be supplied externally.

15. **Add plugin configuration schema support.**
    Every plugin should declare required config keys, optional config keys, secrets, tenant overrides, default values, and validation rules.

16. **Add plugin capability/version negotiation.**
    Products should declare compatible plugin versions/ranges instead of assuming version `1.0.0`.

17. **Add plugin contract tests proving product independence.**
    Each platform plugin should have tests showing it works without PHR or Finance classes, schemas, or constants.

18. **Add plugin observability contract.**
    Standardize plugin metrics, traces, logs, health checks, tenant IDs, correlation IDs, and failure codes.

19. **Add plugin isolation tests.**
    Verify no plugin depends on another plugin’s implementation, only on declared interfaces/contracts.

20. **Add plugin sandbox fixtures using neutral examples.**
    Use `resource.alpha`, `account.default`, `policy.example`, etc. in platform plugin tests, not patient/trade examples.

---

### P2 — Product/domain pack model

21. **Create `products/phr/domain-pack.yaml`.**
    Include PHR-owned resources, schemas, policies, retention rules, compliance profiles, consent profiles, and adapters.

22. **Create `products/finance/domain-pack.yaml`.**
    Include Finance-owned resources, schemas, policies, risk models, compliance profiles, account mappings, and market adapters.

23. **Create `products/phr/policy-packs/healthcare-boundary-policy.yaml`.**
    Move patient record, emergency access, clinical write, consent, and audit rules there.

24. **Create `products/finance/policy-packs/trading-boundary-policy.yaml`.**
    Move trade/order/risk/compliance/approval rules there.

25. **Create a product schema pack registry.**
    PHR schemas like patient records, medications, labs, imaging, billing claims, and emergency access logs should be registered as product schemas, not kernel schemas.

26. **Create a finance schema pack registry.**
    Finance schemas like orders, trades, risk metrics, portfolio positions, market data, and surveillance events should be product-owned.

27. **Add domain pack validation in CI.**
    Validate every product pack has owner, version, resources, policies, schemas, retention rules, and compatibility metadata.

28. **Add product-to-plugin binding manifests.**
    Products should explicitly bind to generic plugins and supply product-specific mappings.

29. **Add product policy compatibility tests.**
    Verify PHR and Finance policy packs load into the generic kernel policy evaluator without kernel changes.

30. **Add product contract tests for PHR and Finance.**
    Ensure both products consume kernel/plugin contracts cleanly without pushing domain semantics back into platform code.

---

### P3 — Bridge extension hardening

31. **Create `AbstractKernelBridge<TAdapter, TClient>`.**
    Data-Cloud, AEP, and YAPPC bridges currently follow the same pattern; standardize it.

32. **Add bridge health checks.**
    Data-Cloud bridge should validate storage/query connectivity; AEP bridge should validate runtime/event availability; YAPPC bridge should validate plugin registry readiness.

33. **Add bridge permission checks.**
    Registering an adapter into kernel context should not imply every product can call every method.

34. **Add bridge audit hooks.**
    Cross-product bridge calls should emit audit events with tenant, principal, source module, target adapter, action, and correlation ID.

35. **Keep bridge capabilities generic.**
    Data-Cloud bridge should expose generic storage/query/stream ports, not internal Data-Cloud concepts. AEP bridge should expose generic event/agent runtime ports, not product-specific pipelines.

36. **Move `phr-fhir-interop` out of platform bridge classification.**
    FHIR interop is PHR/domain-specific and should be product-owned unless generalized into a healthcare domain pack.

---

### P4 — Documentation updates

37. **Update the original kernel analysis report with boundary-safe wording.**

38. **Add a “Kernel Purity Rules” document.**
    Define what may and may not live in `platform-kernel`.

39. **Add a “Platform Plugin Purity Rules” document.**
    Define what may and may not live in `platform-plugins`.

40. **Add a “Product-on-Kernel Development Guide.”**
    Show how a product supplies domain packs, policy packs, schema packs, adapter implementations, and plugin bindings.

41. **Update `CAPABILITY_MATRIX.md`.**
    Clearly separate platform capabilities from product usage examples. Avoid implying platform plugins are patient-, healthcare-, finance-, or product-specific. 

42. **Update architecture diagrams.**
    Show kernel → platform plugins → product domain packs → products as separate layers.

43. **Document PHR and Finance as reference consumers, not platform defaults.**

44. **Add examples under `docs/examples/product-on-kernel/**`.**
    Product examples should not live inside kernel source or platform plugin defaults.

---

### P5 — Long-term platform improvements

45. **Add a centralized platform capability registry.**

46. **Add semantic version compatibility checks for modules/plugins.**

47. **Add tenant-aware policy overrides.**

48. **Add externalized policy-as-code support.**

49. **Add schema evolution and compatibility validation.**

50. **Add a generic event bus contract to the kernel.**

51. **Add replayable, tenant-isolated event streams.**

52. **Add product-owned AI/ML model binding packs.**

53. **Add product-owned compliance evidence packs.**

54. **Add product-owned retention and deletion policy packs.**

55. **Add product-owned regionalization/localization policy packs.**

56. **Add golden boundary tests for every new product.**

57. **Add a “no product bleed” pre-merge checklist.**

58. **Add automated reporting that flags product concepts appearing in platform/kernel code.**

59. **Add platform plugin certification levels.**
    Example: boundary-clean, tenant-safe, observable, audited, versioned, production-ready.

60. **Add product onboarding scaffolder.**
    Generate clean product structure with separate product code, domain packs, schema packs, policy packs, adapters, and plugin bindings.
