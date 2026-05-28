#!/usr/bin/env node

/**
 * T-004: Check PHR mobile i18n.
 * Scans for raw mobile UI strings that should use i18n.
 */

import { readdirSync, readFileSync, statSync } from 'fs';
import { resolve, join } from 'path';

const PHR_MOBILE_SRC_DIR = resolve(process.cwd(), 'products/phr/apps/mobile/src');

// Patterns that indicate raw strings in React Native components
const RAW_STRING_PATTERNS = [
  // JSX text content (not inside t() calls)
  />([^<{}]+)</g,
  // Button/label/title attributes with hardcoded strings
  /(title|label|placeholder|accessibilityLabel)=["']([^"']+)["']/g,
  // Alert messages
  /(Alert\.alert)\(["']([^"']+)["']/g,
];

// Files to exclude (test files, types, config)
const EXCLUDE_PATTERNS = [
  '**/__tests__/**',
  '**/*.test.ts',
  '**/*.test.tsx',
  '**/*.spec.ts',
  '**/*.spec.tsx',
  '**/types.ts',
  '**/i18n/**',
  '**/locales/**',
];

function matchesExcludePattern(relativePath) {
  return EXCLUDE_PATTERNS.some((pattern) => {
    const normalizedPattern = pattern.replaceAll('**/', '').replaceAll('/**', '');
    if (pattern.includes('__tests__') && relativePath.includes('__tests__')) return true;
    if (pattern.includes('__mocks__') && relativePath.includes('__mocks__')) return true;
    if (pattern.includes('*.test.') && /\.test\.(ts|tsx)$/.test(relativePath)) return true;
    if (pattern.includes('*.spec.') && /\.spec\.(ts|tsx)$/.test(relativePath)) return true;
    return relativePath.includes(normalizedPattern.replaceAll('*', ''));
  });
}

function findTsxFiles() {
  const files = [];

  function walk(dir) {
    for (const entry of readdirSync(dir)) {
      const absolutePath = join(dir, entry);
      const relativePath = absolutePath.replace(PHR_MOBILE_SRC_DIR, '').replace(/^[/\\]/, '').replaceAll('\\', '/');
      const stat = statSync(absolutePath);

      if (stat.isDirectory()) {
        if (!matchesExcludePattern(relativePath)) {
          walk(absolutePath);
        }
        continue;
      }

      if (
        (absolutePath.endsWith('.tsx') || absolutePath.endsWith('.ts')) &&
        !absolutePath.endsWith('.d.ts') &&
        !matchesExcludePattern(relativePath)
      ) {
        files.push(absolutePath);
      }
    }
  }

  walk(PHR_MOBILE_SRC_DIR);
  return files;
}

function checkFileForRawStrings(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const issues = [];
  const isTsx = filePath.endsWith('.tsx');

  // Skip files that already use i18n extensively
  if (content.includes('t(') && content.split('t(').length > 5) {
    return issues;
  }

  // Check for JSX text content
  if (isTsx) {
    const textMatches = content.matchAll(/>([^<{}]+)</g);
    for (const match of textMatches) {
      const text = match[1].trim();
      // Skip empty strings, whitespace-only, numbers, and single words that might be variable names
      if (text.length > 2 && !/^[a-zA-Z0-9_]+$/.test(text) && !/^\d+$/.test(text)) {
        // Skip if it's inside a t() call (simple heuristic)
        const beforeMatch = content.substring(0, match.index).slice(-50);
        if (!beforeMatch.includes('t(')) {
          issues.push({
            type: 'jsx-text',
            line: content.substring(0, match.index).split('\n').length,
            text: text.substring(0, 50),
          });
        }
      }
    }

    // Check for hardcoded accessibilityLabel attributes
    const attrMatches = content.matchAll(/(accessibilityLabel|title|label|placeholder)=["']([^"']+)["']/g);
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
  }

  return issues;
}

function main() {
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

main();
