/**
 * Import Boundary Test — delivery/ui must not import backend internals.
 *
 * The UI boundary rule: all source files under delivery/ui/src/ must only
 * import from:
 *   - intra-UI paths (src/...)
 *   - Node.js built-in modules (node:*)
 *   - npm packages (no path prefix)
 *
 * They must NEVER import from:
 *   - delivery/launcher (Java HTTP server, settings, bootstrap)
 *   - planes (action, intelligence, data, event, governance planes)
 *   - extensions/plugins (postgres, kafka, etc.)
 *   - build/generated (raw build outputs)
 *
 * Acceptance: Any violation of this rule fails CI with an actionable message.
 */
import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

const UI_SRC = path.resolve(__dirname, '../../');

// Patterns that identify a backend-internal import path
const BACKEND_PATTERNS: Array<{ pattern: RegExp; label: string }> = [
  {
    pattern: /['"].*\bdelivery[\\/]launcher\b/,
    label: 'launcher internals',
  },
  {
    pattern: /['"].*\bplanes[\\/]/,
    label: 'Data Cloud plane internals',
  },
  {
    pattern: /['"].*\bextensions[\\/]plugins\b/,
    label: 'extension plugin code',
  },
  {
    pattern: /['"].*\bbuild[\\/]generated\b/,
    label: 'raw build/generated output',
  },
];

/**
 * Recursively collect all .ts/.tsx source files under a directory,
 * excluding test files, mocks, and generated stubs.
 */
function collectSourceFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir)) {
    const fullPath = path.join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      // Skip test-only directories and node_modules
      if (['__tests__', 'node_modules', 'dist', '.vite'].includes(entry)) continue;
      results.push(...collectSourceFiles(fullPath));
    } else if (/\.(ts|tsx)$/.test(entry) && !entry.endsWith('.d.ts')) {
      results.push(fullPath);
    }
  }
  return results;
}

describe('import boundary: delivery/ui must not import backend internals', () => {
  const sourceFiles = collectSourceFiles(UI_SRC);

  it('finds TypeScript source files to scan', () => {
    expect(sourceFiles.length).toBeGreaterThan(0);
  });

  it('has no source file importing launcher internals, planes, or extension plugins', () => {
    const violations: string[] = [];

    for (const filePath of sourceFiles) {
      const content = readFileSync(filePath, 'utf8');
      const relativePath = path.relative(UI_SRC, filePath);

      for (const { pattern, label } of BACKEND_PATTERNS) {
        const lines = content.split('\n');
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.trimStart().startsWith('//') || line.trimStart().startsWith('*')) continue;
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
      `Backend boundary violations found in delivery/ui source:\n${violations.join('\n\n')}` +
        '\n\nFix: use generated API clients (lib/api/*) instead of direct backend imports.'
    ).toHaveLength(0);
  });

  it('does not import from Java launcher bootstrap packages', () => {
    // Secondary check: ensure no import paths contain Java package structures
    const javaPackagePattern = /from\s+['"].*com\.ghatana\./;
    const violations: string[] = [];

    for (const filePath of sourceFiles) {
      const content = readFileSync(filePath, 'utf8');
      if (javaPackagePattern.test(content)) {
        violations.push(path.relative(UI_SRC, filePath));
      }
    }

    expect(violations, `Java package imports found in UI source files: ${violations.join(', ')}`).toHaveLength(0);
  });
});
