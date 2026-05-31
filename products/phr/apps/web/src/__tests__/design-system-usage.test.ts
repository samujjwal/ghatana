import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function sourceFilesUnder(relativeDir: string): string[] {
  const root = resolve(__dirname, '..', relativeDir);
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = resolve(root, entry.name);
    if (entry.isDirectory()) {
      return sourceFilesUnder(`${relativeDir}/${entry.name}`);
    }
    return entry.name.endsWith('.tsx') || entry.name.endsWith('.ts') ? [entryPath] : [];
  });
}

const auditPageSource = readFileSync(
  resolve(__dirname, '../pages/AuditPage.tsx'),
  'utf8',
);

describe('PHR design-system usage', () => {
  it('keeps the audit policy page on design-system primitives', () => {
    expect(auditPageSource).not.toMatch(/<button\b/);
    expect(auditPageSource).not.toMatch(/<input\b/);
    expect(auditPageSource).not.toMatch(/<table\b/);
    expect(auditPageSource).not.toMatch(/fixed inset-0|shadow-xl|bg-white rounded/);
  });

  it('keeps PHR pages, components, and layout off raw form/table/button primitives', () => {
    const sources = [
      ...sourceFilesUnder('pages'),
      ...sourceFilesUnder('components'),
      ...sourceFilesUnder('layout'),
    ];

    const offenders = sources.flatMap((filePath) => {
      const source = readFileSync(filePath, 'utf8');
      return /<button\b|<input\b|<select\b|<table\b/.test(source) ? [filePath] : [];
    });

    expect(offenders).toEqual([]);
  });
});
