#!/usr/bin/env node

/**
 * T-003: Check PHR web i18n.
 * Scans for raw web UI strings that should use i18n.
 */

import { readFileSync, readdirSync } from 'fs';
import { resolve, join } from 'path';

const PHR_WEB_SRC_DIR = resolve(process.cwd(), 'products/phr/apps/web/src');

// Files to exclude (test files, types, config)
const EXCLUDE_PATTERNS = [
  '**/__tests__/**',
  '**/*.test.ts',
  '**/*.test.tsx',
  '**/*.spec.ts',
  '**/*.spec.tsx',
  '**/types.ts',
  '**/phrRouteContracts.ts',
  '**/phrRouteElements.tsx',
  '**/demoData.ts',
  '**/i18n/**',
  '**/locales/**',
];

function matchesExclude(relativePath) {
  return EXCLUDE_PATTERNS.some((pattern) => {
    const normalizedPattern = pattern.replaceAll('\\', '/');
    if (normalizedPattern === '**/types.ts') return relativePath.endsWith('/types.ts') || relativePath === 'types.ts';
    if (normalizedPattern === '**/phrRouteContracts.ts') return relativePath.endsWith('/phrRouteContracts.ts') || relativePath === 'phrRouteContracts.ts';
    if (normalizedPattern === '**/phrRouteElements.tsx') return relativePath.endsWith('/phrRouteElements.tsx') || relativePath === 'phrRouteElements.tsx';
    if (normalizedPattern === '**/demoData.ts') return relativePath.endsWith('/demoData.ts') || relativePath === 'demoData.ts';
    if (normalizedPattern === '**/__tests__/**') return relativePath.includes('/__tests__/');
    if (normalizedPattern === '**/i18n/**') return relativePath.includes('/i18n/');
    if (normalizedPattern === '**/locales/**') return relativePath.includes('/locales/');
    if (normalizedPattern === '**/*.test.ts') return relativePath.endsWith('.test.ts');
    if (normalizedPattern === '**/*.test.tsx') return relativePath.endsWith('.test.tsx');
    if (normalizedPattern === '**/*.spec.ts') return relativePath.endsWith('.spec.ts');
    if (normalizedPattern === '**/*.spec.tsx') return relativePath.endsWith('.spec.tsx');
    return false;
  });
}

function findTsxFiles(dir = PHR_WEB_SRC_DIR, prefix = '') {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const relativePath = prefix ? `${prefix}/${entry.name}` : entry.name;
    const absolutePath = join(dir, entry.name);
    if (matchesExclude(relativePath)) {
      continue;
    }
    if (entry.isDirectory()) {
      files.push(...findTsxFiles(absolutePath, relativePath));
      continue;
    }
    if (entry.isFile() && entry.name.endsWith('.tsx')) {
      files.push(absolutePath);
    }
  }
  return files;
}

function checkFileForRawStrings(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const issues = [];

  // Skip files that already use i18n extensively
  if (content.includes('t(') && content.split('t(').length > 5) {
    return issues;
  }

  // Check for JSX text content
  const lines = content.split('\n');
  lines.forEach((line, lineIndex) => {
    const textMatches = line.matchAll(/>([^<{}]+)</g);
    for (const match of textMatches) {
      const text = match[1].trim();
    // Skip empty strings, whitespace-only, numbers, and single words that might be variable names
      if (
        text.length > 2
        && !/^[a-zA-Z0-9_]+$/.test(text)
        && !/^\d+$/.test(text)
        && !/^[)=:;|&.[\]-]+$/.test(text)
        && !text.startsWith('): Promise')
      ) {
      // Skip if it's inside a t() call (simple heuristic)
        const beforeMatch = line.substring(0, match.index).slice(-50);
        if (!beforeMatch.includes('t(')) {
        issues.push({
          type: 'jsx-text',
            line: lineIndex + 1,
          text: text.substring(0, 50),
        });
      }
    }
  }
  });

  // Check for hardcoded button/label/title attributes
  const attrMatches = content.matchAll(/(title|label|placeholder|aria-label)=["']([^"']+)["']/g);
  for (const match of attrMatches) {
    const value = match[2];
    if (value.length > 2 && !/^[a-zA-Z0-9_\-]+$/.test(value)) {
      issues.push({
        type: 'attribute',
        attr: match[1],
        line: content.substring(0, match.index).split('\n').length,
        text: value.substring(0, 50),
      });
    }
  }

  return issues;
}

async function main() {
  const files = findTsxFiles();
  let totalIssues = 0;

  for (const file of files) {
    const issues = checkFileForRawStrings(file);
    if (issues.length > 0) {
      console.log(`\n${file}:`);
      for (const issue of issues) {
        console.log(`  Line ${issue.line}: ${issue.type === 'jsx-text' ? 'Text content' : issue.attr + ' attribute'}: "${issue.text}"`);
        totalIssues++;
      }
    }
  }

  if (totalIssues > 0) {
    console.error(`\nFAIL: Found ${totalIssues} potential raw strings that should use i18n`);
    process.exit(1);
  }

  console.log('PASS: No raw UI strings found (or files already use i18n)');
}

main().catch(err => {
  console.error('ERROR:', err);
  process.exit(1);
});
