/**
 * Dependency Policy Enforcement for Ghatana Monorepo
 *
 * Enforces dependency convergence, allowed libraries, and version policies.
 *
 * @doc.type tooling
 * @doc.purpose Dependency governance and convergence
 * @doc.layer infrastructure
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ============================================================================
// Dependency Policies
// ============================================================================

export const DEPENDENCY_POLICY = {
  // React ecosystem - Single version enforcement
  react: {
    allowedVersions: ['^19.2.4'],
    enforced: true,
    packages: ['react', 'react-dom', '@types/react', '@types/react-dom'],
  },

  // TypeScript - Single version
  typescript: {
    allowedVersions: ['^5.9.3'],
    enforced: true,
    packages: ['typescript'],
  },

  // Build tools
  vite: {
    allowedVersions: ['^7.3.1'],
    enforced: true,
    packages: ['vite', '@vitejs/plugin-react'],
  },

  // Styling
  tailwindcss: {
    allowedVersions: ['^4.1.18'],
    enforced: true,
    packages: ['tailwindcss', '@tailwindcss/postcss'],
  },

  // State management
  state: {
    preferred: 'jotai',
    allowed: ['jotai', 'zustand'],
    deprecated: ['redux', 'mobx'],
    packages: {
      jotai: '^2.17.0',
      zustand: '^4.5.0',
    },
  },

  // Testing
  testing: {
    unit: 'vitest',
    e2e: '@playwright/test',
    packages: {
      vitest: '^4.0.18',
      '@playwright/test': '^1.58.1',
    },
  },

  // HTTP clients - Consolidate to one
  http: {
    preferred: 'native-fetch',
    allowed: ['native-fetch', 'axios'],
    deprecated: ['request', 'superagent', 'got'],
  },

  // Date handling - Single library
  dates: {
    preferred: 'date-fns',
    allowed: ['date-fns'],
    deprecated: ['moment', 'luxon', 'dayjs'],
  },

  // Validation
  validation: {
    preferred: 'zod',
    allowed: ['zod'],
    deprecated: ['yup', 'joi', 'ajv'],
  },
} as const;

// ============================================================================
// Forbidden Libraries
// ============================================================================

export const FORBIDDEN_LIBRARIES = [
  // Deprecated/legacy
  'jquery',
  'underscore',
  'moment', // Use date-fns
  'lodash', // Use native or @ghatana/utils

  // Security concerns
  'eval',
  'vm2', // Use isolated-vm or quickjs

  // Duplicative
  'classnames', // Use clsx (already in use)
  'styled-components', // Use Tailwind
  '@emotion/styled', // Use Tailwind
] as const;

// ============================================================================
// License Policy
// ============================================================================

export const LICENSE_POLICY = {
  allowed: [
    'MIT',
    'Apache-2.0',
    'BSD-2-Clause',
    'BSD-3-Clause',
    'ISC',
    '0BSD',
    'Python-2.0',
  ],
  forbidden: [
    'GPL-2.0',
    'GPL-3.0',
    'AGPL-3.0',
    'LGPL-2.1',
    'LGPL-3.0',
    'SSPL-1.0',
    'EPL-1.0',
    'MPL-2.0',
  ],
  review: ['CC-BY-4.0', 'ODC-By-1.0', 'Unlicense'],
} as const;

// ============================================================================
// Library Size Budgets (bytes)
// ============================================================================

export const SIZE_BUDGETS = {
  component: 10 * 1024, // 10KB per component
  page: 100 * 1024, // 100KB per page
  chunk: 500 * 1024, // 500KB per chunk
  total: 2 * 1024 * 1024, // 2MB total initial bundle
} as const;

// ============================================================================
// Enforcement Functions
// ============================================================================

export interface Violation {
  type:
    | 'version-mismatch'
    | 'forbidden-lib'
    | 'license-issue'
    | 'size-exceeded';
  package: string;
  current: string;
  expected?: string;
  location: string;
  severity: 'error' | 'warning';
  message: string;
}

export function checkVersionConvergence(
  packageName: string,
  version: string,
  location: string
): Violation | null {
  for (const [category, policy] of Object.entries(DEPENDENCY_POLICY)) {
    // Check if this is a versioned policy (has allowedVersions)
    if ('packages' in policy && Array.isArray(policy.packages)) {
      if (policy.packages.includes(packageName as any)) {
        // Type guard for versioned policies
        if (
          'allowedVersions' in policy &&
          Array.isArray(policy.allowedVersions)
        ) {
          const allowedVersions = policy.allowedVersions as readonly string[];
          const enforced = 'enforced' in policy ? policy.enforced : true;

          if (
            !allowedVersions.some((v: string) =>
              version.startsWith(v.replace('^', ''))
            )
          ) {
            return {
              type: 'version-mismatch',
              package: packageName,
              current: version,
              expected: allowedVersions.join(' or '),
              location,
              severity: enforced ? 'error' : 'warning',
              message: `Version ${version} of ${packageName} does not match policy. Expected: ${allowedVersions.join(' or ')}`,
            };
          }
        }
      }
    }
  }
  return null;
}

export function checkForbiddenLibrary(
  packageName: string,
  location: string
): Violation | null {
  if (FORBIDDEN_LIBRARIES.includes(packageName as any)) {
    return {
      type: 'forbidden-lib',
      package: packageName,
      current: 'installed',
      location,
      severity: 'error',
      message: `Library ${packageName} is forbidden. See DEPENDENCY_POLICY for alternatives.`,
    };
  }
  return null;
}

export function checkLicenseCompliance(
  packageName: string,
  license: string,
  location: string
): Violation | null {
  const normalizedLicense = license.toUpperCase().replace(/-/g, '-');

  if (LICENSE_POLICY.forbidden.includes(normalizedLicense as any)) {
    return {
      type: 'license-issue',
      package: packageName,
      current: license,
      location,
      severity: 'error',
      message: `Package ${packageName} has forbidden license: ${license}`,
    };
  }

  if (
    !LICENSE_POLICY.allowed.includes(normalizedLicense as any) &&
    !LICENSE_POLICY.review.includes(normalizedLicense as any)
  ) {
    return {
      type: 'license-issue',
      package: packageName,
      current: license,
      location,
      severity: 'warning',
      message: `Package ${packageName} has unreviewed license: ${license}`,
    };
  }

  return null;
}

// ============================================================================
// Workspace Scanning
// ============================================================================

export function scanWorkspace(workspaceRoot: string): Violation[] {
  const violations: Violation[] = [];
  const libsDir = join(workspaceRoot, 'libs');

  try {
    const entries = readdirSync(libsDir);

    for (const entry of entries) {
      const packageJsonPath = join(libsDir, entry, 'package.json');

      try {
        const content = readFileSync(packageJsonPath, 'utf-8');
        const pkg = JSON.parse(content);
        const location = `libs/${entry}`;

        // Check all dependencies
        const deps = {
          ...pkg.dependencies,
          ...pkg.devDependencies,
          ...pkg.peerDependencies,
        };

        for (const [name, version] of Object.entries(deps)) {
          const versionStr = String(version).replace('workspace:', '');

          // Check version convergence
          const versionViolation = checkVersionConvergence(
            name,
            versionStr,
            location
          );
          if (versionViolation) violations.push(versionViolation);

          // Check forbidden libraries
          const forbiddenViolation = checkForbiddenLibrary(name, location);
          if (forbiddenViolation) violations.push(forbiddenViolation);
        }
      } catch (e) {
        // package.json doesn't exist or is invalid
      }
    }
  } catch (e) {
    console.error('Error scanning workspace:', e);
  }

  return violations;
}

// ============================================================================
// Report Generation
// ============================================================================

export function generateReport(violations: Violation[]): string {
  const errors = violations.filter((v) => v.severity === 'error');
  const warnings = violations.filter((v) => v.severity === 'warning');

  let report = '\n📊 Dependency Policy Report\n';
  report += '========================\n\n';

  if (violations.length === 0) {
    report += '✅ All dependencies compliant with policy\n';
    return report;
  }

  report += `Errors: ${errors.length}\n`;
  report += `Warnings: ${warnings.length}\n\n`;

  if (errors.length > 0) {
    report += '❌ ERRORS:\n';
    for (const v of errors) {
      report += `  [${v.type}] ${v.package} at ${v.location}\n`;
      report += `    ${v.message}\n`;
      if (v.expected) {
        report += `    Current: ${v.current} | Expected: ${v.expected}\n`;
      }
      report += '\n';
    }
  }

  if (warnings.length > 0) {
    report += '⚠️  WARNINGS:\n';
    for (const v of warnings) {
      report += `  [${v.type}] ${v.package} at ${v.location}\n`;
      report += `    ${v.message}\n\n`;
    }
  }

  return report;
}

// ============================================================================
// CLI
// ============================================================================

export function main(): void {
  const workspaceRoot = process.cwd();

  console.log('🔍 Scanning workspace for dependency policy violations...\n');

  const violations = scanWorkspace(workspaceRoot);
  const report = generateReport(violations);

  console.log(report);

  const errors = violations.filter((v) => v.severity === 'error');
  if (errors.length > 0) {
    console.error(`\n❌ ${errors.length} policy violations found`);
    process.exit(1);
  }

  console.log('\n✅ All dependencies compliant');
}

// Run if called directly
if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
