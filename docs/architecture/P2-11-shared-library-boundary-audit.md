# P2-11: Shared-Library Boundary Audit

**Date:** 2026-05-19  
**Status:** Audit complete, remediation pending

## Summary

This audit identified product-specific semantics in shared platform libraries that should be moved to their respective product areas to maintain clean architectural boundaries.

## Findings

### 1. Product-Specific Rule Libraries in Platform PAC Module

**Location:** `platform/java/policy-as-code/src/main/java/com/ghatana/platform/pac/library/`

#### NepalHealthcareRuleLibrary.java
- **Product:** PHR (Personal Health Record)
- **Issue:** Nepal healthcare-specific governance rules in shared platform module
- **Policy Names:**
  - `nepal_healthcare.patient_data_access`
  - `nepal_healthcare.patient_data_export`
  - `nepal_healthcare.retention_check`
  - `nepal_healthcare.consent_required`
  - `nepal_healthcare.emergency_override`
- **Remediation:** Move to `products/phr/governance/` or equivalent PHR-specific location

#### FinanceSoxRuleLibrary.java
- **Product:** Finance
- **Issue:** SOX financial controls in shared platform module
- **Policy Names:**
  - `sox.financial_record_access`
  - `sox.transaction_approval`
  - `sox.segregation_of_duties`
  - `sox.audit_trail_required`
  - `sox.large_transaction_review`
- **Remediation:** Move to `products/finance/governance/` or equivalent Finance-specific location

## Architectural Principle

From `coding-instructions.md`:
> **Keep boundaries explicit**: Domain logic must not silently leak into transport, UI, persistence, or infra glue.

Product-specific governance rules are domain logic that belongs in the owning product area, not in a shared platform package. The platform/policy-as-code module should provide:
- Generic policy evaluation engine (InMemoryPolicyEngine, OPA integration)
- Base RuleLibrary interface
- Testing utilities for policy evaluation

Product-specific rule implementations should live in:
- `products/{product}/governance/` for product-specific policy implementations
- Or a shared `platform/contracts/policy-libraries/` if truly cross-product (but these are not)

## Remediation Plan

1. **Create product-specific governance modules:**
   - `products/phr/governance/` for NepalHealthcareRuleLibrary
   - `products/finance/governance/` for FinanceSoxRuleLibrary

2. **Update dependencies:**
   - PHR product depends on platform/policy-as-code + its own governance module
   - Finance product depends on platform/policy-as-code + its own governance module

3. **Deprecate platform rule libraries:**
   - Add `@Deprecated` annotations to NepalHealthcareRuleLibrary and FinanceSoxRuleLibrary
   - Add migration notes in javadoc
   - Plan removal in next major version

4. **Update tests:**
   - Move NepalHealthcareRuleLibraryTest to PHR product
   - Move FinanceSoxRuleLibraryTest to Finance product

## Impact

- **Low:** These are rule libraries, not core infrastructure
- **Medium:** May require updates to product build configurations
- **Timeline:** Can be done in next major version cycle

## Related Tasks

- P2-12: Add CI release gates for production readiness
