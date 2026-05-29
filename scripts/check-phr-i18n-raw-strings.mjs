#!/usr/bin/env node

/**
 * PHR i18n Raw String Check
 *
 * Verifies that PHR web and mobile code does not contain hardcoded user-facing
 * strings. All user-facing text should use the i18n system (t() function).
 *
 * @doc.type script
 * @doc.purpose i18n conformance check
 * @doc.layer infrastructure
 */

import { readFileSync, readdirSync } from 'fs';
import { resolve, join } from 'path';

const WEB_SRC_PATH = resolve(process.cwd(), 'products/phr/apps/web/src');
const MOBILE_SRC_PATH = resolve(process.cwd(), 'products/phr/apps/mobile/src');

let exitCode = 0;

// Patterns that indicate hardcoded strings (excluding technical strings)
const RAW_STRING_PATTERNS = [
  // JSX text content that looks like user-facing text
  />([^<]{5,})</g,
  // Button/label text without t()
  /(?:label|placeholder|title|aria-label)=["']([^"']{5,})["']/g,
  // Alert/confirm with hardcoded text
  /(?:alert|confirm)\(["']([^"']{5,})["']\)/g,
];

// Technical strings to ignore
const IGNORE_PATTERNS = [
  /className=/g,
  /data-testid=/g,
  /role=/g,
  /aria-/g,
  /id=/g,
  /key=/g,
  /type=/g,
  /href=/g,
  /src=/g,
  /alt=/g,
  /value=/g,
  /onChange=/g,
  /onClick=/g,
  /onSubmit=/g,
  /disabled/g,
  /required/g,
  /readOnly/g,
  /true|false/g,
  /\d+/g,
  /[A-Z_]+/g, // Constants
  /\/[a-z-]+/g, // Paths
  /\.\w+/g, // Properties
];

function isTechnicalString(str) {
  return IGNORE_PATTERNS.some(pattern => pattern.test(str)) ||
         str.startsWith('/') ||
         /^\d+$/.test(str) ||
         /^[A-Z_]+$/.test(str) ||
         str.includes('.') ||
         str.includes('-') && str.length < 10;
}

function scanFile(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const issues = [];

    // Check for hardcoded strings in JSX
    const jsxMatches = content.matchAll(/>([^<]{5,})</g);
    for (const match of jsxMatches) {
      const text = match[1].trim();
      if (text && !isTechnicalString(text) && !text.startsWith('{')) {
        issues.push({ type: 'jsx-text', text, line: getLineNumber(content, match.index) });
      }
    }

    // Check for hardcoded attribute values
    const attrMatches = content.matchAll(/(?:label|placeholder|title|aria-label)=["']([^"']{5,})["']/g);
    for (const match of attrMatches) {
      const text = match[1].trim();
      if (text && !isTechnicalString(text) && !text.startsWith('{')) {
        issues.push({ type: 'attribute', text, line: getLineNumber(content, match.index) });
      }
    }

    // Check for alert/confirm with hardcoded text
    const alertMatches = content.matchAll(/(?:alert|confirm)\(["']([^"']{5,})["']\)/g);
    for (const match of alertMatches) {
      const text = match[1].trim();
      if (text && !isTechnicalString(text)) {
        issues.push({ type: 'alert', text, line: getLineNumber(content, match.index) });
      }
    }

    return issues;
  } catch (error) {
    console.error(`❌ Failed to scan ${filePath}:`, error.message);
    return [];
  }
}

function getLineNumber(content, index) {
  return content.substring(0, index).split('\n').length;
}

function scanDirectory(dirPath, fileExtensions) {
  let totalIssues = 0;
  let scannedFiles = 0;

  function scanRecursive(currentPath) {
    const entries = readdirSync(currentPath, { withFileTypes: true });
    
    for (const entry of entries) {
      const fullPath = join(currentPath, entry.name);
      
      if (entry.isDirectory()) {
        // Skip node_modules, __tests__, dist, build
        if (!['node_modules', '__tests__', 'dist', 'build', '.next'].includes(entry.name)) {
          scanRecursive(fullPath);
        }
      } else if (entry.isFile() && fileExtensions.some(ext => entry.name.endsWith(ext))) {
        const issues = scanFile(fullPath);
        if (issues.length > 0) {
          console.log(`\n📄 ${fullPath}`);
          issues.forEach(issue => {
            console.log(`   Line ${issue.line}: [${issue.type}] "${issue.text}"`);
          });
          totalIssues += issues.length;
        }
        scannedFiles++;
      }
    }
  }

  scanRecursive(dirPath);
  return { totalIssues, scannedFiles };
}

function checkI18nConformance() {
  console.log('🔍 Checking PHR i18n conformance...\n');

  // Scan web source
  console.log('📱 Scanning web source...');
  const webResult = scanDirectory(WEB_SRC_PATH, ['.tsx', '.ts', '.jsx', '.js']);
  console.log(`   Scanned ${webResult.scannedFiles} files, found ${webResult.totalIssues} issues`);

  // Scan mobile source
  console.log('\n📱 Scanning mobile source...');
  const mobileResult = scanDirectory(MOBILE_SRC_PATH, ['.tsx', '.ts', '.jsx', '.js']);
  console.log(`   Scanned ${mobileResult.scannedFiles} files, found ${mobileResult.totalIssues} issues`);

  const totalIssues = webResult.totalIssues + mobileResult.totalIssues;

  console.log('\n' + '='.repeat(60));
  if (totalIssues === 0) {
    console.log('✅ i18n conformance check passed');
  } else {
    console.log(`❌ i18n conformance check failed - ${totalIssues} hardcoded strings found`);
    console.log('   Please use t() function for all user-facing text');
    exitCode = 1;
  }
  console.log('='.repeat(60));

  process.exit(exitCode);
}

checkI18nConformance();
