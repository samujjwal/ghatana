import path from 'node:path';
import { createRequire } from 'node:module';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);
const {
  shouldScanFile,
  findViolationsInContent,
  hasLocalCnImplementation,
} = require('../check-duplication-boundaries.js') as {
  shouldScanFile: (filePath: string) => boolean;
  findViolationsInContent: (
    relativePath: string,
    content: string,
  ) => Array<{ kind: string; detail: string }>;
  hasLocalCnImplementation: (content: string) => boolean;
};

describe('check-duplication-boundaries', () => {
  it('flags deprecated compat imports in active code', () => {
    const violations = findViolationsInContent(
      'apps/web/src/example.ts',
      "import { x } from '@yappc/base-ui';\n",
    );

    expect(violations).toEqual([
      expect.objectContaining({
        kind: 'deprecated-import',
        detail: '@yappc/base-ui',
      }),
    ]);
  });

  it('flags duplicated local cn implementations', () => {
    const source = [
      "import { clsx, type ClassValue } from 'clsx';",
      "import { twMerge } from 'tailwind-merge';",
      '',
      'export function cn(...inputs: ClassValue[]): string {',
      '  return twMerge(clsx(inputs));',
      '}',
    ].join('\n');

    const violations = findViolationsInContent(
      'libs/yappc-ui/src/components/utils/cn.ts',
      source,
    );

    expect(violations).toEqual([
      expect.objectContaining({
        kind: 'duplicate-cn',
      }),
    ]);
    expect(hasLocalCnImplementation(source)).toBe(true);
  });

  it('does not flag cn wrappers that delegate to the canonical package', () => {
    const source = [
      "import { cn as mergeCn } from '@ghatana/platform-utils';",
      '',
      'export function cn(...inputs) {',
      '  return mergeCn(...inputs);',
      '}',
    ].join('\n');

    expect(
      findViolationsInContent('web/src/lib/utils.ts', source),
    ).toHaveLength(0);
    expect(hasLocalCnImplementation(source)).toBe(false);
  });

  it('skips test and generated files', () => {
    expect(
      shouldScanFile(
        path.join('/repo', 'products/yappc/frontend/apps/web/src/example.test.ts'),
      ),
    ).toBe(false);
    expect(
      shouldScanFile(
        path.join('/repo', 'products/yappc/frontend/libs/foo/generated/client.ts'),
      ),
    ).toBe(false);
    expect(
      shouldScanFile(
        path.join('/repo', 'products/yappc/frontend/apps/web/src/example.ts'),
      ),
    ).toBe(true);
  });
});