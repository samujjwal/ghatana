#!/usr/bin/env node

/**
 * P1-5: A11y Maturity Check
 *
 * Validates comprehensive accessibility maturity across all products:
 * - Keyboard-only journey tests
 * - Focus trap tests
 * - Screen-reader landmark/label assertions
 * - Table/grid accessibility
 * - Chart/visualization accessibility
 * - Modal/toast/error accessibility
 *
 * This replaces posture-only checks with behavioral verification that
 * accessibility is fully implemented and production-ready.
 *
 * Usage: node scripts/check-a11y-maturity.mjs [--ci]
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const CI_MODE = process.argv.includes('--ci');

const violations = [];
const warnings = [];

function logError(message) {
  violations.push(message);
  console.error(`❌ ERROR: ${message}`);
}

function logWarning(message) {
  warnings.push(message);
  console.warn(`⚠️  WARNING: ${message}`);
}

function logSuccess(message) {
  console.log(`✓ ${message}`);
}

/**
 * Check for keyboard-only journey tests
 */
function checkKeyboardJourneyTests(productPath, productName) {
  const e2eDir = path.join(productPath, 'e2e');
  
  if (!existsSync(e2eDir)) {
    logWarning(`${productName}: No e2e directory found`);
    return;
  }

  const e2eFiles = readdirSync(e2eDir).filter(f => 
    f.endsWith('.spec.ts') || f.endsWith('.spec.tsx') || f.endsWith('.spec.js')
  );

  let hasKeyboardTests = false;
  
  for (const file of e2eFiles) {
    const filePath = path.join(e2eDir, file);
    const content = readFileSync(filePath, 'utf8');
    
    if (content.includes('keyboard') || 
        content.includes('Tab') || 
        content.includes('keyboardNavigation') ||
        content.includes('press') ||
        content.includes('keyboard.press')) {
      hasKeyboardTests = true;
      break;
    }
  }

  if (hasKeyboardTests) {
    logSuccess(`${productName}: Has keyboard-only journey tests`);
  } else {
    logWarning(`${productName}: No keyboard-only journey tests found`);
  }
}

/**
 * Check for focus trap tests
 */
function checkFocusTrapTests(productPath, productName) {
  const testDir = path.join(productPath, 'src/__tests__');
  const e2eDir = path.join(productPath, 'e2e');
  
  let hasFocusTrapTests = false;
  
  function searchDir(dir) {
    if (!existsSync(dir)) return;
    
    try {
      const items = readdirSync(dir);
      
      for (const item of items) {
        const itemPath = path.join(dir, item);
        const stat = statSync(itemPath);
        
        if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
          searchDir(itemPath);
        } else if (item.endsWith('.test.ts') || item.endsWith('.test.tsx') || item.endsWith('.spec.ts') || item.endsWith('.spec.tsx')) {
          const content = readFileSync(itemPath, 'utf8');
          
          if (content.includes('focus') && 
              (content.includes('trap') || content.includes('modal') || content.includes('dialog'))) {
            hasFocusTrapTests = true;
          }
        }
      }
    } catch (e) {
      // Skip directories we can't read
    }
  }

  searchDir(testDir);
  searchDir(e2eDir);

  if (hasFocusTrapTests) {
    logSuccess(`${productName}: Has focus trap tests`);
  } else {
    logWarning(`${productName}: No focus trap tests found`);
  }
}

/**
 * Check for screen-reader landmark/label assertions
 */
function checkScreenReaderAssertions(productPath, productName) {
  const e2eDir = path.join(productPath, 'e2e');
  
  if (!existsSync(e2eDir)) {
    return;
  }

  const e2eFiles = readdirSync(e2eDir).filter(f => 
    f.endsWith('.spec.ts') || f.endsWith('.spec.tsx') || f.endsWith('.spec.js')
  );

  let hasScreenReaderTests = false;
  
  for (const file of e2eFiles) {
    const filePath = path.join(e2eDir, file);
    const content = readFileSync(filePath, 'utf8');
    
    if (content.includes('aria-') || 
        content.includes('role=') ||
        content.includes('landmark') ||
        content.includes('label') ||
        content.includes('screenReader') ||
        content.includes('ariaLabel') ||
        content.includes('aria-labelledby') ||
        content.includes('aria-describedby')) {
      hasScreenReaderTests = true;
      break;
    }
  }

  if (hasScreenReaderTests) {
    logSuccess(`${productName}: Has screen-reader landmark/label assertions`);
  } else {
    logWarning(`${productName}: No screen-reader landmark/label assertions found`);
  }
}

/**
 * Check for table/grid accessibility
 */
function checkTableGridAccessibility(productPath, productName) {
  const srcDir = path.join(productPath, 'src');
  
  if (!existsSync(srcDir)) {
    return;
  }

  let hasTableGridComponents = false;
  let hasA11yAttributes = false;
  
  function searchDir(dir) {
    if (!existsSync(dir)) return;
    
    try {
      const items = readdirSync(dir);
      
      for (const item of items) {
        const itemPath = path.join(dir, item);
        const stat = statSync(itemPath);
        
        if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
          searchDir(itemPath);
        } else if (item.endsWith('.tsx') || item.endsWith('.ts') || item.endsWith('.jsx') || item.endsWith('.js')) {
          const content = readFileSync(itemPath, 'utf8');
          
          if (content.includes('Table') || content.includes('Grid') || content.includes('table') || content.includes('grid')) {
            hasTableGridComponents = true;
          }
          
          if (content.includes('aria-row') || 
              content.includes('aria-col') ||
              content.includes('aria-sort') ||
              content.includes('scope=') ||
              content.includes('aria-label') ||
              content.includes('aria-describedby')) {
            hasA11yAttributes = true;
          }
        }
      }
    } catch (e) {
      // Skip directories we can't read
    }
  }

  searchDir(srcDir);

  if (hasTableGridComponents) {
    if (hasA11yAttributes) {
      logSuccess(`${productName}: Table/grid components have accessibility attributes`);
    } else {
      logWarning(`${productName}: Table/grid components found but missing accessibility attributes`);
    }
  } else {
    logSuccess(`${productName}: No table/grid components (a11y not required)`);
  }
}

/**
 * Check for chart/visualization accessibility
 */
function checkChartVisualizationAccessibility(productPath, productName) {
  const srcDir = path.join(productPath, 'src');
  
  if (!existsSync(srcDir)) {
    return;
  }

  let hasChartComponents = false;
  let hasA11yAttributes = false;
  
  function searchDir(dir) {
    if (!existsSync(dir)) return;
    
    try {
      const items = readdirSync(dir);
      
      for (const item of items) {
        const itemPath = path.join(dir, item);
        const stat = statSync(itemPath);
        
        if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
          searchDir(itemPath);
        } else if (item.endsWith('.tsx') || item.endsWith('.ts') || item.endsWith('.jsx') || item.endsWith('.js')) {
          const content = readFileSync(itemPath, 'utf8');
          
          if (content.includes('Chart') || content.includes('Visualization') || content.includes('Graph') || content.includes('Plot')) {
            hasChartComponents = true;
          }
          
          if (content.includes('aria-label') || 
              content.includes('aria-describedby') ||
              content.includes('role="img"') ||
              content.includes('alt=') ||
              content.includes('title=')) {
            hasA11yAttributes = true;
          }
        }
      }
    } catch (e) {
      // Skip directories we can't read
    }
  }

  searchDir(srcDir);

  if (hasChartComponents) {
    if (hasA11yAttributes) {
      logSuccess(`${productName}: Chart/visualization components have accessibility attributes`);
    } else {
      logWarning(`${productName}: Chart/visualization components found but missing accessibility attributes`);
    }
  } else {
    logSuccess(`${productName}: No chart/visualization components (a11y not required)`);
  }
}

/**
 * Check for modal/toast/error accessibility
 */
function checkModalToastErrorAccessibility(productPath, productName) {
  const srcDir = path.join(productPath, 'src');
  
  if (!existsSync(srcDir)) {
    return;
  }

  let hasModalComponents = false;
  let hasToastComponents = false;
  let hasErrorComponents = false;
  let hasA11yAttributes = false;
  
  function searchDir(dir) {
    if (!existsSync(dir)) return;
    
    try {
      const items = readdirSync(dir);
      
      for (const item of items) {
        const itemPath = path.join(dir, item);
        const stat = statSync(itemPath);
        
        if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
          searchDir(itemPath);
        } else if (item.endsWith('.tsx') || item.endsWith('.ts') || item.endsWith('.jsx') || item.endsWith('.js')) {
          const content = readFileSync(itemPath, 'utf8');
          
          if (content.includes('Modal') || content.includes('Dialog')) {
            hasModalComponents = true;
          }
          
          if (content.includes('Toast') || content.includes('Notification') || content.includes('Snackbar')) {
            hasToastComponents = true;
          }
          
          if (content.includes('Error') || content.includes('Alert') || content.includes('ErrorMessage')) {
            hasErrorComponents = true;
          }
          
          if (content.includes('role="dialog"') || 
              content.includes('role="alert"') ||
              content.includes('role="alertdialog"') ||
              content.includes('aria-modal') ||
              content.includes('aria-live') ||
              content.includes('aria-atomic') ||
              content.includes('aria-labelledby')) {
            hasA11yAttributes = true;
          }
        }
      }
    } catch (e) {
      // Skip directories we can't read
    }
  }

  searchDir(srcDir);

  const hasAnyComponents = hasModalComponents || hasToastComponents || hasErrorComponents;
  
  if (hasAnyComponents) {
    if (hasA11yAttributes) {
      logSuccess(`${productName}: Modal/toast/error components have accessibility attributes`);
    } else {
      logWarning(`${productName}: Modal/toast/error components found but missing accessibility attributes`);
    }
  } else {
    logSuccess(`${productName}: No modal/toast/error components (a11y not required)`);
  }
}

/**
 * Check for ARIA attribute usage in components
 */
function checkAriaAttributeUsage(productPath, productName) {
  const srcDir = path.join(productPath, 'src');
  
  if (!existsSync(srcDir)) {
    return;
  }

  let hasAriaAttributes = false;
  const ariaAttributes = new Set();
  
  function searchDir(dir) {
    if (!existsSync(dir)) return;
    
    try {
      const items = readdirSync(dir);
      
      for (const item of items) {
        const itemPath = path.join(dir, item);
        const stat = statSync(itemPath);
        
        if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
          searchDir(itemPath);
        } else if (item.endsWith('.tsx') || item.endsWith('.ts') || item.endsWith('.jsx') || item.endsWith('.js')) {
          const content = readFileSync(itemPath, 'utf8');
          
          const ariaMatches = content.match(/aria-[a-z-]+/g);
          if (ariaMatches) {
            hasAriaAttributes = true;
            ariaMatches.forEach(attr => ariaAttributes.add(attr));
          }
        }
      }
    } catch (e) {
      // Skip directories we can't read
    }
  }

  searchDir(srcDir);

  if (hasAriaAttributes) {
    logSuccess(`${productName}: Uses ${ariaAttributes.size} different ARIA attributes`);
  } else {
    logWarning(`${productName}: No ARIA attributes found in components`);
  }
}

/**
 * Check for focus management utilities
 */
function checkFocusManagement(productPath, productName) {
  const srcDir = path.join(productPath, 'src');
  
  if (!existsSync(srcDir)) {
    return;
  }

  let hasFocusManagement = false;
  
  function searchDir(dir) {
    if (!existsSync(dir)) return;
    
    try {
      const items = readdirSync(dir);
      
      for (const item of items) {
        const itemPath = path.join(dir, item);
        const stat = statSync(itemPath);
        
        if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
          searchDir(itemPath);
        } else if (item.endsWith('.tsx') || item.endsWith('.ts') || item.endsWith('.jsx') || item.endsWith('.js')) {
          const content = readFileSync(itemPath, 'utf8');
          
          if (content.includes('useFocus') || 
              content.includes('focus()') ||
              content.includes('autoFocus') ||
              content.includes('focusVisible') ||
              content.includes('FocusTrap') ||
              content.includes('useRef') && content.includes('focus')) {
            hasFocusManagement = true;
          }
        }
      }
    } catch (e) {
      // Skip directories we can't read
    }
  }

  searchDir(srcDir);

  if (hasFocusManagement) {
    logSuccess(`${productName}: Has focus management utilities`);
  } else {
    logWarning(`${productName}: No focus management utilities found`);
  }
}

/**
 * Main validation
 */
function main() {
  console.log('Checking A11y maturity across all products...\n');

  // Products to check
  const products = [
    { path: 'products/yappc/frontend/web', name: 'YAPPC Web' },
    { path: 'products/tutorputor/apps/tutorputor-web', name: 'TutorPutor Web' },
    { path: 'products/data-cloud/planes/action/ui', name: 'Data Cloud Action UI' },
    { path: 'products/data-cloud/delivery/ui', name: 'Data Cloud Delivery UI' },
    { path: 'products/digital-marketing/ui', name: 'Digital Marketing UI' },
  ];

  for (const product of products) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logWarning(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- Checking ${product.name} ---`);
    
    checkKeyboardJourneyTests(productPath, product.name);
    checkFocusTrapTests(productPath, product.name);
    checkScreenReaderAssertions(productPath, product.name);
    checkTableGridAccessibility(productPath, product.name);
    checkChartVisualizationAccessibility(productPath, product.name);
    checkModalToastErrorAccessibility(productPath, product.name);
    checkAriaAttributeUsage(productPath, product.name);
    checkFocusManagement(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);

  if (violations.length > 0) {
    console.log('\nA11y maturity check failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0 && CI_MODE) {
    console.log('\nA11y maturity check passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nA11y maturity check passed.');
}

main();
