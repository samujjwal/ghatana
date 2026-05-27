# YAPPC-for-PHR Generation Workflow

**Document type:** Use Case  
**Layer:** Product  
**Last updated:** 2026-05-27  
**Audience:** Product engineers, Kernel developers  

---

## 1. Overview

YAPPC (Yet Another Product Page Creator) is a Kernel-native code generation tool that can accelerate PHR frontend development by generating route contracts, page components, and navigation elements from the canonical IA baseline (`phr-usecase-baseline.json`).

This document describes the workflow for using YAPPC to generate PHR web application artifacts.

---

## 2. Prerequisites

- `phr-usecase-baseline.json` must be up-to-date with the latest IA changes
- YAPPC must be configured with PHR-specific templates
- Generated files should be reviewed before commit (YAPPC is an accelerator, not a replacement for human review)

---

## 3. Generation Workflow

### Step 1: Update IA Baseline

1. Edit `products/phr/config/phr-usecase-baseline.json` to add or modify use cases
2. Run `pnpm generate:phr-ia-coverage-doc` to update the human-readable coverage matrix
3. Run `pnpm generate:phr-doc-code-mismatch` to identify any gaps

### Step 2: Invoke YAPPC Generation

```bash
# Generate PHR route contracts from IA baseline
pnpm kernel product generate phr-routes --input products/phr/config/phr-usecase-baseline.json

# Generate PHR page components
pnpm kernel product generate phr-pages --input products/phr/config/phr-usecase-baseline.json

# Generate PHR navigation elements
pnpm kernel product generate phr-navigation --input products/phr/config/phr-usecase-baseline.json
```

### Step 3: Review Generated Artifacts

YAPPC will generate or update the following files:

| File | Purpose | Review Checklist |
|------|---------|-----------------|
| `products/phr/apps/web/src/phrRouteContracts.ts` | Route contracts with entitlement metadata | Verify minimumRole, actions, cards, lifecycle |
| `products/phr/apps/web/src/phrRouteElements.tsx` | React Router route elements | Verify lazy loading, error boundaries |
| `products/phr/apps/web/src/pages/[NewPage].tsx` | New page components | Verify PHI redaction, error handling, i18n keys |
| `products/phr/config/phr-route-contract.json` | Canonical JSON for backend | Verify parity with web contracts |

### Step 4: Run Parity Tests

```bash
# Verify route entitlement parity between web and backend
pnpm test products/phr/apps/web/src/__tests__/route-entitlement-parity.test.ts

# Verify IA coverage
pnpm check:phr-ia-coverage
```

### Step 5: Commit Changes

1. Commit the updated IA baseline
2. Commit the generated artifacts
3. Update the PHR current-surface documentation: `pnpm generate:phr-current-surface`

---

## 4. Gap Visualization

YAPPC provides a gap visualization command that shows which IA items are not yet implemented:

```bash
pnpm kernel product visualize phr-gaps --input products/phr/config/phr-usecase-baseline.json
```

This generates a markdown table showing:

| IA Route | Status | Web Implementation | Backend API | Mobile Screen |
|----------|--------|-------------------|-------------|---------------|
| `/dashboard` | implemented | ✅ DashboardPage.tsx | ✅ PhrDashboardRoutes | ✅ DashboardScreen.tsx |
| `/telemedicine` | feature_flagged | ⚠️ FeatureFlagPage | ❌ Not implemented | ❌ Not implemented |

---

## 5. Customization Guidelines

### PHR-Specific Templates

YAPPC uses PHR-specific templates located in `products/phr/.yappc/templates/`:

- `route-contract.ts.template` - Route contract template with PHR-specific fields
- `page-component.tsx.template` - Page component with PHI redaction hooks
- `navigation-element.tsx.template` - Route element with lazy loading

### Extending Templates

To add a new field to generated route contracts:

1. Update the template in `products/phr/.yappc/templates/route-contract.ts.template`
2. Update the YAPPC schema in `products/phr/.yappc/schema.json`
3. Regenerate: `pnpm kernel product generate phr-routes --rebuild`

---

## 6. Integration with CI

The PHR CI pipeline includes YAPPC generation checks:

```yaml
# .github/workflows/phr-ci.yml
- name: Generate PHR artifacts
  run: |
    pnpm generate:phr-current-surface
    pnpm kernel product generate phr-routes --check-only

- name: Verify parity
  run: pnpm test products/phr/apps/web/src/__tests__/route-entitlement-parity.test.ts
```

The `--check-only` flag ensures that generated files are in sync with the IA baseline without overwriting manual changes.

---

## 7. Troubleshooting

### Issue: Generated route contracts conflict with manual changes

**Solution:** 
- Use `--check-only` to detect drift
- Manually merge changes if needed
- Consider updating the IA baseline instead of manual edits

### Issue: Gap visualization shows false negatives

**Solution:**
- Verify that `phr-usecase-baseline.json` is correctly formatted
- Check that backend route paths match the `backendApis` field in the baseline
- Run `pnpm generate:phr-doc-code-mismatch` for detailed diagnostics

### Issue: YAPPC templates not found

**Solution:**
- Ensure `products/phr/.yappc/templates/` directory exists
- Run `pnpm kernel product init phr` to initialize PHR-specific templates

---

## 8. Best Practices

1. **Always review generated code** - YAPPC is an accelerator, not a replacement for human review
2. **Keep IA baseline as single source of truth** - Avoid manual edits to generated files when possible
3. **Run parity tests before commit** - Ensure web and backend remain in sync
4. **Document manual overrides** - If you must manually edit generated files, add comments explaining why
5. **Regenerate after IA changes** - Any change to `phr-usecase-baseline.json` should trigger regeneration

---

## 9. Future Enhancements

- [ ] Add YAPPC support for generating backend route handlers from IA baseline
- [ ] Add YAPPC support for generating mobile screen components
- [ ] Add YAPPC support for generating test files for new pages
- [ ] Add YAPPC support for generating i18n keys for new features

---

*This workflow is part of the PHR acceleration strategy using Kernel-native tooling.*
