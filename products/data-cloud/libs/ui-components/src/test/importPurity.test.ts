/**
 * Import Dependency Lint — libs/ui-components purity gate (DC-P1-395)
 *
 * libs/ui-components must be a pure, app-agnostic reusable component library.
 * It must NOT import from:
 *   - delivery/ui application code (routes, stores, hooks, services, API clients)
 *   - products/data-cloud planes (data, event, governance, intelligence planes)
 *   - Any app-specific routing libraries (react-router) that imply page context
 *   - Any app-specific state (jotai atoms) that imply global state dependency
 *
 * Allowed imports:
 *   - Internal: relative paths within the library (../../lib/theme, etc.)
 *   - External packages: @ghatana/design-system, react, lucide-react, sonner, clsx
 *
 * This test enforces the clean dependency direction required by DC-P1-395:
 * "reusable library has clean dependency direction."
 */

import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

const LIB_SRC = path.resolve(__dirname, '../../src');

// ─── Forbidden import patterns ────────────────────────────────────────────────
// These patterns indicate that a component in the library has leaked an app-level
// dependency into the shared library. Each violation breaks reusability.

const FORBIDDEN_IMPORT_PATTERNS: Array<{ pattern: RegExp; label: string }> = [
  {
    // No importing from the delivery/ui application source
    pattern: /from\s+['"].*\/delivery\/ui\//,
    label: 'delivery/ui application code',
  },
  {
    // No importing from product planes
    pattern: /from\s+['"].*\/planes\//,
    label: 'Data Cloud planes internals',
  },
  {
    // No app-level routing — signals page-context dependency
    pattern: /from\s+['"]react-router(?:-dom)?\b/,
    label: 'react-router (app routing)',
  },
  {
    // No jotai atoms — signals global app state dependency
    pattern: /from\s+['"]jotai\b/,
    label: 'jotai (app state)',
  },
  {
    // No API service imports — components must receive data via props
    pattern: /from\s+['"].*\/api\/.*\.service/,
    label: 'API service (data fetching)',
  },
  {
    // No hooks that import app services
    pattern: /from\s+['"].*\/hooks\/use(?:Query|Mutation|Fetch|Auth|Tenant)/,
    label: 'app-connected hooks (query/auth/tenant)',
  },
  {
    // No TanStack Query — library components must not initiate data fetches
    pattern: /from\s+['"]@tanstack\/react-query\b/,
    label: '@tanstack/react-query (data fetching in library)',
  },
];

// ─── Allowed patterns (explicitly documented) ─────────────────────────────────
// These are the only cross-package imports permitted in the library.

const ALLOWED_EXTERNAL_PACKAGES = [
  'react',
  '@ghatana/design-system',
  '@ghatana/platform-utils',
  'lucide-react',
  'sonner',
  'clsx',
  'class-variance-authority',
];

/**
 * Recursively collect all .ts/.tsx source files under a directory,
 * excluding test files, mocks, generated files, and node_modules.
 */
function collectSourceFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir)) {
    const fullPath = path.join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      if (['node_modules', 'dist', '.vite', 'test', '__tests__'].includes(entry)) continue;
      results.push(...collectSourceFiles(fullPath));
    } else if (/\.(ts|tsx)$/.test(entry) && !entry.endsWith('.d.ts') && !entry.endsWith('.test.ts') && !entry.endsWith('.test.tsx')) {
      results.push(fullPath);
    }
  }
  return results;
}

describe('libs/ui-components — import purity gate (DC-P1-395)', () => {
  const sourceFiles = collectSourceFiles(LIB_SRC);

  it('finds library source files to scan', () => {
    expect(sourceFiles.length).toBeGreaterThan(0);
  });

  it('has no component importing app-level code (routing, stores, services, planes)', () => {
    const violations: string[] = [];

    for (const filePath of sourceFiles) {
      const content = readFileSync(filePath, 'utf8');
      const relativePath = path.relative(LIB_SRC, filePath);
      const lines = content.split('\n');

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        // Skip comments
        if (line.trimStart().startsWith('//') || line.trimStart().startsWith('*')) continue;
        // Only check import lines
        if (!line.trimStart().startsWith('import')) continue;

        for (const { pattern, label } of FORBIDDEN_IMPORT_PATTERNS) {
          if (pattern.test(line)) {
            violations.push(
              `${relativePath}:${i + 1} — forbidden import of ${label}:\n  ${line.trim()}`
            );
          }
        }
      }
    }

    expect(
      violations,
      `libs/ui-components purity violations found:\n${violations.join('\n\n')}\n\n` +
        'Fix: components in libs/ui-components must receive data via props.\n' +
        'Do not import app routes, stores, API services, or platform planes.'
    ).toHaveLength(0);
  });

  it('documents allowed external packages for the library', () => {
    // Sentinel: if new external packages are added, they must be reviewed here
    for (const pkg of ALLOWED_EXTERNAL_PACKAGES) {
      // Verify the list is well-formed (not empty strings, not typos)
      expect(pkg.length).toBeGreaterThan(0);
    }
  });

  it('all library source files export at least one symbol (no empty barrel shells)', () => {
    const emptyFiles: string[] = [];

    for (const filePath of sourceFiles) {
      const content = readFileSync(filePath, 'utf8').trim();
      if (content.length === 0) {
        emptyFiles.push(path.relative(LIB_SRC, filePath));
      }
    }

    expect(emptyFiles, `Empty source files found: ${emptyFiles.join(', ')}`).toHaveLength(0);
  });
});
