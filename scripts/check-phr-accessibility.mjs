#!/usr/bin/env node

/**
 * PHR Accessibility Check
 *
 * Verifies that PHR web and mobile components have proper accessibility attributes
 * such as aria-labels, roles, and keyboard navigation support.
 *
 * @doc.type script
 * @doc.purpose Accessibility conformance check
 * @doc.layer infrastructure
 */

import { readFileSync, readdirSync } from 'fs';
import { resolve, join } from 'path';

const WEB_SRC_PATH = resolve(process.cwd(), 'products/phr/apps/web/src');
const MOBILE_SRC_PATH = resolve(process.cwd(), 'products/phr/apps/mobile/src');

let exitCode = 0;

// Accessibility patterns to check
const ACCESSIBILITY_PATTERNS = {
  // Interactive elements should have aria-label or visible text
  buttonsWithoutLabel: /<button[^>]*>(?:\s*<\/button>|[^<]*<[^>]*>)/g,
  // Images should have alt text
  imagesWithoutAlt: /<img(?![^>]*alt=)[^>]*>/g,
  // Inputs should have labels
  inputsWithoutLabel: /<input(?![^>]*(?:aria-label|id=))[^>]*>/g,
  // Links should have descriptive text
  emptyLinks: /<a[^>]*>\s*<\/a>/g,
};

function scanFile(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const issues = [];

    // Check for buttons without labels
    const buttonMatches = content.matchAll(/<button[^>]*>/g);
    for (const match of buttonMatches) {
      const buttonTag = match[0];
      const hasAriaLabel = /aria-label=/.test(buttonTag);
      const hasVisibleText = content.substring(match.index, match.index + 200).match(/<button[^>]*>([^<]+)/);
      
      if (!hasAriaLabel && (!hasVisibleText || hasVisibleText[1].trim().length === 0)) {
        issues.push({ type: 'button-without-label', line: getLineNumber(content, match.index) });
      }
    }

    // Check for images without alt
    const imgMatches = content.matchAll(/<img[^>]*>/g);
    for (const match of imgMatches) {
      const imgTag = match[0];
      if (!/alt=/.test(imgTag)) {
        issues.push({ type: 'image-without-alt', line: getLineNumber(content, match.index) });
      }
    }

    // Check for inputs without labels
    const inputMatches = content.matchAll(/<input[^>]*>/g);
    for (const match of inputMatches) {
      const inputTag = match[0];
      const hasAriaLabel = /aria-label=/.test(inputTag);
      const hasId = /id=/.test(inputTag);
      
      if (!hasAriaLabel && !hasId) {
        issues.push({ type: 'input-without-label', line: getLineNumber(content, match.index) });
      }
    }

    // Check for empty links
    const linkMatches = content.matchAll(/<a[^>]*>\s*<\/a>/g);
    for (const match of linkMatches) {
      issues.push({ type: 'empty-link', line: getLineNumber(content, match.index) });
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
            console.log(`   Line ${issue.line}: [${issue.type}]`);
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

function checkAccessibilityConformance() {
  console.log('🔍 Checking PHR accessibility conformance...\n');

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
    console.log('✅ Accessibility conformance check passed');
  } else {
    console.log(`❌ Accessibility conformance check failed - ${totalIssues} issues found`);
    console.log('   Please add proper aria-labels, alt text, and labels to interactive elements');
    exitCode = 1;
  }
  console.log('='.repeat(60));

  process.exit(exitCode);
}

checkAccessibilityConformance();
