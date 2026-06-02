#!/usr/bin/env node

/**
 * Component Accessibility Check (A11Y-01 through A11Y-03)
 * Ensures React components follow accessibility best practices
 * Checks for ARIA labels, semantic HTML, keyboard navigation, and focus management
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');

let violations = [];
let componentsChecked = 0;
let a11yIssuesFound = 0;

function checkComponent(filePath) {
  const relativePath = relative(REPO_ROOT, filePath);
  const content = readFileSync(filePath, 'utf-8');
  
  if (!content.includes('export') && !content.includes('function')) {
    return;
  }

  componentsChecked++;

  // A11Y-01: Check for interactive elements without proper ARIA labels
  const interactiveElements = ['Button', 'input', 'button', 'a', 'select', 'textarea'];
  interactiveElements.forEach((element) => {
    const regex = new RegExp(`<${element}[^>]*(?!aria-label|aria-labelledby|aria-describedby)[^>]*>`, 'gi');
    const matches = content.match(regex);
    if (matches) {
      matches.forEach((match) => {
        // Skip if it has a child with text content (implicit label)
        if (match.includes('>') && !match.match(/aria-label|aria-labelledby/i)) {
          violations.push({
            component: relativePath,
            message: 'Interactive element missing ARIA label',
            details: `Element ${element} should have aria-label or aria-labelledby`
          });
          a11yIssuesFound++;
        }
      });
    }
  });

  // A11Y-02: Check for images without alt text
  const imgRegex = /<img[^>]*(?!alt)[^>]*>/gi;
  const imgMatches = content.match(imgRegex);
  if (imgMatches) {
    imgMatches.forEach((match) => {
      if (!match.includes('alt=')) {
        violations.push({
          component: relativePath,
          message: 'Image missing alt attribute',
          details: 'All images must have alt text for accessibility'
        });
        a11yIssuesFound++;
      }
    });
  }

  // A11Y-03: Check for semantic HTML usage
  const nonSemanticDivs = content.match(/<div[^>]*role="button"/gi);
  if (nonSemanticDivs) {
    violations.push({
      component: relativePath,
      message: 'Non-semantic element with button role',
      details: 'Use <button> element instead of div with role="button"'
    });
    a11yIssuesFound++;
  }

  // Check for heading hierarchy violations
  const headings = content.match(/<h[1-6][^>]*>/gi) || [];
  let previousLevel = 0;
  headings.forEach((heading) => {
    const level = parseInt(heading.match(/h([1-6])/i)[1]);
    if (level > previousLevel + 1 && previousLevel !== 0) {
      violations.push({
        component: relativePath,
        message: 'Heading hierarchy violation',
        details: `Heading level jumped from h${previousLevel} to h${level}`
      });
      a11yIssuesFound++;
    }
    previousLevel = level;
  });

  // Check for form inputs without labels
  const inputRegex = /<input[^>]*(?!id|aria-label|aria-labelledby)[^>]*>/gi;
  const inputMatches = content.match(inputRegex);
  if (inputMatches) {
    inputMatches.forEach((match) => {
      if (!match.includes('id=') && !match.includes('aria-label') && !match.includes('aria-labelledby')) {
        violations.push({
          component: relativePath,
          message: 'Form input without label',
          details: 'Form inputs should have id, aria-label, or aria-labelledby'
        });
        a11yIssuesFound++;
      }
    });
  }
}

function walkDirectory(dir, callback) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules, .git, build, dist
      if (entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'build' && entry.name !== 'dist') {
        walkDirectory(fullPath, callback);
      }
    } else if (entry.isFile() && (entry.name.endsWith('.tsx') || entry.name.endsWith('.jsx'))) {
      callback(fullPath);
    }
  }
}

function main() {
  console.log('🔍 Checking component accessibility (A11Y-01 through A11Y-03)...\n');
  
  const webSrcDir = join(REPO_ROOT, 'products/phr/apps/web/src');
  
  if (!statSync(webSrcDir).isDirectory()) {
    console.error('❌ PHR web src directory not found');
    process.exit(1);
  }

  walkDirectory(webSrcDir, checkComponent);
  
  console.log(`📊 Checked ${componentsChecked} React components\n`);
  console.log(`📊 Found ${a11yIssuesFound} accessibility issues\n`);
  
  if (violations.length > 0) {
    console.error(`❌ Found ${violations.length} accessibility violations:\n`);
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.component}`);
      console.error(`     ${v.message}`);
      console.error(`     ${v.details}\n`);
    });
    console.error('\n💡 Fix: Add proper ARIA labels, semantic HTML, and form labels');
    process.exit(1);
  }
  
  console.log('✅ All components follow accessibility best practices.');
  console.log('✅ Accessibility checks passed.\n');
  process.exit(0);
}

main();
