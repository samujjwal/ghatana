# Monorepo Governance Automation

**Status:** Phase 2 Implementation  
**Created:** March 17, 2026  
**Purpose:** Automated governance checks for CI/CD pipeline

---

## Overview

This directory contains automated governance checks that run in CI/CD to ensure:

- Dependency version convergence
- License compliance
- SBOM generation
- Security audit compliance
- Naming convention adherence

---

## Workflows

### 1. Governance Checks (`governance-checks.yml`)

**Triggers:** Push to main, pull requests, manual dispatch  
**Jobs:**

- SBOM Generation (CycloneDX + SPDX)
- License Compliance Check
- Dependency Convergence Analysis
- Naming Convention Validation
- Security Audit

**Artifacts:**

- `sbom-reports/` - CycloneDX and SPDX SBOMs
- `license-report/` - License analysis JSON
- `dependency-convergence-report/` - Dependency version report
- `governance-summary/` - Consolidated summary markdown

### 2. Dependency Convergence (`dependency-convergence.yml`)

**Triggers:** Push to main, pull requests  
**Jobs:**

- Check version alignment
- Detect duplicate dependencies
- Verify peer dependency compatibility
- Monitor bundle size

---

## Usage

### Running Checks Locally

```bash
# Check dependency alignment
node scripts/align-dependencies.js --check-only

# Check for specific dependency versions
pnpm list react --depth=0
pnpm list typescript --depth=0

# Check licenses
npx license-checker --onlyAllow 'MIT;Apache-2.0;BSD;ISC'

# Generate SBOM locally
npx @cyclonedx/cyclonedx-npm --output-file sbom.json
```

### Updating Dependencies

```bash
# Run alignment script (dry-run first)
node scripts/align-dependencies.js --dry-run

# Apply changes
node scripts/align-dependencies.js

# Install updated dependencies
pnpm install

# Verify build
pnpm build

# Run tests
pnpm test
```

---

## Target Dependency Versions

See `scripts/align-dependencies.js` for the canonical list of target versions.

Key dependencies:

- **React:** ^19.2.4
- **TypeScript:** ^5.9.3
- **Vite:** ^7.3.1
- **Jotai:** ^2.17.0
- **Tailwind CSS:** ^4.1.18

---

## License Policy

### Allowed Licenses

- MIT
- Apache-2.0
- BSD (all variants)
- ISC
- CC0-1.0
- Unlicense

### Forbidden Licenses

- GPL (all variants)
- AGPL
- LGPL
- SSPL
- EPL
- MPL
- CDDL

---

## Reports

All reports are generated as artifacts and can be downloaded from GitHub Actions:

1. **SBOM Reports**
   - `sbom.json` - CycloneDX format
   - `spbom.spdx.json` - SPDX format

2. **License Report**
   - `licenses.json` - All detected licenses

3. **Dependency Report**
   - `dependency-report.json` - Full dependency tree

4. **Governance Summary**
   - `governance-summary.md` - Consolidated status

---

## Integration with Development Workflow

### Pre-Commit Hooks

Add to `.husky/pre-commit`:

```bash
#!/bin/sh
. "$(dirname "$0")/_/husky.sh"

# Check dependency alignment
node scripts/align-dependencies.js --check-only || exit 1

# Run linting
pnpm lint || exit 1
```

### PR Requirements

Branch protection rules should require:

- ✅ Required Checks passing on every pull request
- ✅ Product release gates only for release branches, tags, or manual promotion
- ✅ Product-specific advisory workflows should not be required globally
- ✅ Strict release gates must publish evidence artifacts before promotion

---

## Troubleshooting

### Check Fails: Dependency Alignment

```bash
# See what needs to be aligned
node scripts/align-dependencies.js --check-only --verbose

# Apply fixes
node scripts/align-dependencies.js
pnpm install
```

### Check Fails: License Compliance

```bash
# See all licenses
npx license-checker --json

# Identify forbidden licenses
npx license-checker --onlyAllow 'GPL;AGPL;LGPL;SSPL'
```

### Check Fails: Duplicate Dependencies

```bash
# Find duplicates
pnpm list --depth=0 | sort | uniq -d

# Check why duplicates exist
pnpm why <package-name>
```

---

## Maintenance

### Updating Target Versions

1. Edit `scripts/align-dependencies.js`
2. Update `TARGET_VERSIONS` object
3. Run `node scripts/align-dependencies.js`
4. Test: `pnpm install && pnpm build && pnpm test`
5. Commit changes

### Adding New Checks

1. Create new workflow file in `.github/workflows/`
2. Add job to `governance-checks.yml` needs
3. Update this documentation
4. Test with `act` or push to branch

---

## Related Documentation

- [Library Dependency Matrix](../../docs/LIBRARY_DEPENDENCY_MATRIX.md)
- [Version Convergence Report](../../docs/VERSION_CONVERGENCE_REPORT.md)
- [Import Pattern Analysis](../../docs/IMPORT_PATTERN_ANALYSIS.md)
- [Governance Implementation Plan](../../docs/GOVERNANCE_IMPLEMENTATION_PLAN.md)

---

**Last Updated:** March 17, 2026
