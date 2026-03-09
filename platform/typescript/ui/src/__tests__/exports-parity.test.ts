import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

function extractExportTargets(source: string): string[] {
  const pattern = /export \* from ['"](\.\/[^'\"]+)['"];?/g;
  const result = new Set<string>();
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(source)) !== null) {
    result.add(match[1]);
  }
  return Array.from(result).sort();
}

describe('ui index export parity', () => {
  it('src/index.ts and dist/ui/src/index.d.ts export the same modules', () => {
    const srcPath = resolve(__dirname, '../index.ts');
    const distPath = resolve(__dirname, '../../dist/ui/src/index.d.ts');

    if (!existsSync(distPath)) {
      // If the dist types are not present (e.g., in a clean dev checkout),
      // skip this check rather than failing tests.
      // eslint-disable-next-line no-console
      console.warn('[ui] dist index.d.ts not found; skipping export parity test');
      return;
    }

    const srcExports = extractExportTargets(readFileSync(srcPath, 'utf8'));
    const distExports = extractExportTargets(readFileSync(distPath, 'utf8'));

    expect(distExports).toEqual(srcExports);
  });
});
