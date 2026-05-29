#!/usr/bin/env node

/**
 * Check for raw user-visible strings in PHR web/mobile code.
 *
 * This script scans PHR web and mobile source files for hardcoded strings
 * that should be internationalized. It identifies:
 * - JSX/TSX text content that is not wrapped in t() or i18n calls
 * - String literals in common UI patterns (buttons, labels, alerts)
 * - Hardcoded error messages and validation text
 *
 * @doc.type script
 * @doc.purpose Validate that PHR web/mobile use i18n for user-visible strings
 * @doc.layer governance
 */

import { readFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const ROOT = process.cwd();
const PHR_WEB_DIR = join(ROOT, 'products', 'phr', 'apps', 'web', 'src');
const PHR_MOBILE_DIR = join(ROOT, 'products', 'phr', 'apps', 'mobile', 'src');

// Patterns that indicate i18n usage (allowed)
const I18N_PATTERNS = [
  /\bt\(['"`]/,
  /\bi18n\.t\(['"`]/,
  /\buseTranslation\(\)/,
  /\bformatMessage\(['"`]/,
  /\bFormattedMessage\b/,
];

// Patterns that indicate raw user-visible strings (potential violations)
const RAW_STRING_PATTERNS = [
  // JSX text content
  />\s*[^<\s][^<{]{5,}\s*</,
  // Button/label text
  /<(button|label|a)[^>]*>\s*[^<\s][^<{]{3,}\s*</,
  // Alert/error text
  /(alert|error|success|warning):\s*['"`][^'"`]{5,}['"`]/,
  // Placeholder text
  /placeholder=\s*['"`][^'"`]{5,}['"`]/,
  // Aria-label
  /aria-label=\s*['"`][^'"`]{5,}['"`]/,
  // Title attribute
  /title=\s*['"`][^'"`]{5,}['"`]/,
];

// Files to exclude (test files, mocks, fixtures, i18n files themselves)
const EXCLUDE_PATTERNS = [
  /test/,
  /spec/,
  /mock/,
  /fixture/,
  /stub/,
  /__tests__/,
  /\.test\./,
  /\.spec\./,
  /i18n/,
  /locales/,
  /translations/,
];

function isExcluded(filePath) {
  return EXCLUDE_PATTERNS.some(pattern => pattern.test(filePath));
}

function hasI18nUsage(content) {
  return I18N_PATTERNS.some(pattern => pattern.test(content));
}

function scanFile(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    const violations = [];

    // Skip files that already use i18n
    if (hasI18nUsage(content)) {
      return [];
    }

    lines.forEach((line, index) => {
      RAW_STRING_PATTERNS.forEach(pattern => {
        if (pattern.test(line)) {
          violations.push({
            line: index + 1,
            pattern: pattern.source,
            content: line.trim(),
          });
        }
      });
    });

    return violations;
  } catch (error) {
    // Skip files that can't be read
    return [];
  }
}

function scanDirectory(dir, extensions = ['.tsx', '.ts', '.jsx', '.js']) {
  const violations = [];

  function scan(currentDir) {
    const entries = readdirSync(currentDir);

    for (const entry of entries) {
      const fullPath = join(currentDir, entry);
      const stat = statSync(fullPath);

      if (stat.isDirectory()) {
        // Skip node_modules and build directories
        if (!entry.includes('node_modules') && !entry.includes('build') && !entry.includes('dist')) {
          scan(fullPath);
        }
      } else if (stat.isFile()) {
        const ext = entry.substring(entry.lastIndexOf('.'));
        if (extensions.includes(ext) && !isExcluded(fullPath)) {
          const fileViolations = scanFile(fullPath);
          if (fileViolations.length > 0) {
            violations.push({ file: fullPath, violations: fileViolations });
          }
        }
      }
    }
  }

  scan(dir);
  return violations;
}

function main() {
  console.log('Checking for raw user-visible strings in PHR web/mobile...\n');

  let totalViolations = 0;

  // Scan web app
  if (existsSync(PHR_WEB_DIR)) {
    console.log('Scanning PHR web app...');
    const webViolations = scanDirectory(PHR_WEB_DIR);
    
    if (webViolations.length > 0) {
      console.log(`  ❌ Found ${webViolations.length} files with potential raw strings:\n`);
      webViolations.forEach(({ file, violations: fileViolations }) => {
        console.log(`    ${file}`);
        fileViolations.forEach(v => {
          console.log(`      Line ${v.line}: ${v.content}`);
        });
      });
      totalViolations += webViolations.reduce((sum, v) => sum + v.violations.length, 0);
    } else {
      console.log(`  ✅ No raw strings found`);
    }
  } else {
    console.log('  ⚠️  PHR web app directory not found');
  }

  console.log();

  // Scan mobile app
  if (existsSync(PHR_MOBILE_DIR)) {
    console.log('Scanning PHR mobile app...');
    const mobileViolations = scanDirectory(PHR_MOBILE_DIR);
    
    if (mobileViolations.length > 0) {
      console.log(`  ❌ Found ${mobileViolations.length} files with potential raw strings:\n`);
      mobileViolations.forEach(({ file, violations: fileViolations }) => {
        console.log(`    ${file}`);
        fileViolations.forEach(v => {
          console.log(`      Line ${v.line}: ${v.content}`);
        });
      });
      totalViolations += mobileViolations.reduce((sum, v) => sum + v.violations.length, 0);
    } else {
      console.log(`  ✅ No raw strings found`);
    }
  } else {
    console.log('  ⚠️  PHR mobile app directory not found');
  }

  console.log(`\n${totalViolations === 0 ? '✅' : '❌'} Total violations: ${totalViolations}`);

  if (totalViolations > 0) {
    console.log('\nError: User-visible strings should use i18n (t() or i18n.t()).');
    console.log('Please wrap raw strings in translation functions.');
    process.exit(1);
  }

  process.exit(0);
}

main();
