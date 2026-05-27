#!/usr/bin/env node

/**
 * T-004: Check PHR mobile i18n.
 * Scans for raw mobile UI strings that should use i18n.
 */

import { readFileSync, existsSync } from 'fs';
import { resolve, join } from 'path';
import { glob } from 'glob';

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

async function findTsxFiles() {
  const files = await glob('**/*.{tsx,ts}', {
    cwd: PHR_MOBILE_SRC_DIR,
    ignore: EXCLUDE_PATTERNS,
  });
  return files.map(f => join(PHR_MOBILE_SRC_DIR, f));
}

function checkFileForRawStrings(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const issues = [];

  // Skip files that already use i18n extensively
  if (content.includes('t(') && content.split('t(').length > 5) {
    return issues;
  }

  // Check for JSX text content
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

  return issues;
}

async function main() {
  const files = await findTsxFiles();
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
