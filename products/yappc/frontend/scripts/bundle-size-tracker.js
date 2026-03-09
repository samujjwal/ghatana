#!/usr/bin/env node
/**
 * Bundle Size Tracking Script
 * 
 * Analyzes bundle sizes and compares against thresholds.
 * Fails CI if bundles exceed limits.
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Bundle size thresholds (in KB)
const THRESHOLDS = {
  'apps/web': {
    total: 500, // 500 KB total
    js: 350,    // 350 KB JavaScript
    css: 100,   // 100 KB CSS
    chunks: {
      main: 200,  // Main bundle
      vendor: 250, // Vendor bundle
    },
  },
  'libs/canvas': {
    total: 150, // 150 KB for Canvas library
    js: 150,
  },
};

// Color codes for terminal output
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m',
};

/**
 * Get bundle sizes for a project
 */
function getBundleSizes(projectPath) {
  const distPath = path.join(projectPath, '.next'); // Or 'dist' for non-Next.js
  
  if (!fs.existsSync(distPath)) {
    console.log(`${colors.yellow}⚠️  No build found for ${projectPath}${colors.reset}`);
    return null;
  }
  
  const sizes = {
    total: 0,
    js: 0,
    css: 0,
    chunks: {},
  };
  
  // Recursively get file sizes
  function walkDir(dir) {
    const files = fs.readdirSync(dir);
    
    files.forEach((file) => {
      const filePath = path.join(dir, file);
      const stat = fs.statSync(filePath);
      
      if (stat.isDirectory()) {
        walkDir(filePath);
      } else {
        const sizeKB = stat.size / 1024;
        sizes.total += sizeKB;
        
        if (file.endsWith('.js')) {
          sizes.js += sizeKB;
          
          // Track individual chunks
          if (file.includes('main')) {
            sizes.chunks.main = (sizes.chunks.main || 0) + sizeKB;
          } else if (file.includes('vendor') || file.includes('node_modules')) {
            sizes.chunks.vendor = (sizes.chunks.vendor || 0) + sizeKB;
          }
        } else if (file.endsWith('.css')) {
          sizes.css += sizeKB;
        }
      }
    });
  }
  
  walkDir(distPath);
  
  return sizes;
}

/**
 * Format size with color
 */
function formatSize(size, threshold, label) {
  const formatted = size.toFixed(2);
  const percentage = ((size / threshold) * 100).toFixed(1);
  
  let color = colors.green;
  let icon = '✅';
  
  if (size > threshold) {
    color = colors.red;
    icon = '❌';
  } else if (size > threshold * 0.9) {
    color = colors.yellow;
    icon = '⚠️ ';
  }
  
  return `${icon} ${label}: ${color}${formatted} KB${colors.reset} / ${threshold} KB (${percentage}%)`;
}

/**
 * Check bundle sizes against thresholds
 */
function checkBundleSizes() {
  console.log(`\n${colors.cyan}📦 Bundle Size Analysis${colors.reset}\n`);
  console.log(`${'='.repeat(60)  }\n`);
  
  let hasFailures = false;
  const results = [];
  
  for (const [project, thresholds] of Object.entries(THRESHOLDS)) {
    console.log(`${colors.blue}${project}${colors.reset}\n`);
    
    const sizes = getBundleSizes(project);
    
    if (!sizes) {
      continue;
    }
    
    // Check total size
    console.log(formatSize(sizes.total, thresholds.total, 'Total    '));
    if (sizes.total > thresholds.total) {
      hasFailures = true;
      results.push({
        project,
        metric: 'total',
        size: sizes.total,
        threshold: thresholds.total,
        exceeded: sizes.total - thresholds.total,
      });
    }
    
    // Check JS size
    if (thresholds.js) {
      console.log(formatSize(sizes.js, thresholds.js, 'JavaScript'));
      if (sizes.js > thresholds.js) {
        hasFailures = true;
        results.push({
          project,
          metric: 'js',
          size: sizes.js,
          threshold: thresholds.js,
          exceeded: sizes.js - thresholds.js,
        });
      }
    }
    
    // Check CSS size
    if (thresholds.css) {
      console.log(formatSize(sizes.css, thresholds.css, 'CSS       '));
      if (sizes.css > thresholds.css) {
        hasFailures = true;
        results.push({
          project,
          metric: 'css',
          size: sizes.css,
          threshold: thresholds.css,
          exceeded: sizes.css - thresholds.css,
        });
      }
    }
    
    // Check chunk sizes
    if (thresholds.chunks) {
      console.log('');
      for (const [chunk, threshold] of Object.entries(thresholds.chunks)) {
        const chunkSize = sizes.chunks[chunk] || 0;
        console.log(formatSize(chunkSize, threshold, `  ${chunk.padEnd(10)}`));
        if (chunkSize > threshold) {
          hasFailures = true;
          results.push({
            project,
            metric: `chunks.${chunk}`,
            size: chunkSize,
            threshold,
            exceeded: chunkSize - threshold,
          });
        }
      }
    }
    
    console.log(`\n${  '-'.repeat(60)  }\n`);
  }
  
  // Summary
  if (hasFailures) {
    console.log(`${colors.red}❌ Bundle size check failed!${colors.reset}\n`);
    console.log('The following bundles exceeded their thresholds:\n');
    
    results.forEach((result) => {
      console.log(
        `  • ${result.project} (${result.metric}): ` +
        `${colors.red}+${result.exceeded.toFixed(2)} KB${colors.reset} over limit`
      );
    });
    
    console.log('\nPlease reduce bundle size by:');
    console.log('  • Lazy loading components');
    console.log('  • Tree-shaking unused code');
    console.log('  • Code splitting');
    console.log('  • Removing unused dependencies');
    console.log('  • Optimizing images and assets\n');
    
    process.exit(1);
  } else {
    console.log(`${colors.green}✅ All bundles within size limits!${colors.reset}\n`);
  }
}

/**
 * Generate bundle size report
 */
function generateReport() {
  const report = {
    timestamp: new Date().toISOString(),
    bundles: {},
  };
  
  for (const project of Object.keys(THRESHOLDS)) {
    const sizes = getBundleSizes(project);
    if (sizes) {
      report.bundles[project] = sizes;
    }
  }
  
  // Write report
  const reportPath = path.join(__dirname, '../test-results/bundle-sizes.json');
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`${colors.cyan}📄 Report saved to: ${reportPath}${colors.reset}\n`);
}

/**
 * Compare with baseline
 */
function compareWithBaseline() {
  const baselinePath = path.join(__dirname, '../test-results/bundle-sizes-baseline.json');
  const currentPath = path.join(__dirname, '../test-results/bundle-sizes.json');
  
  if (!fs.existsSync(baselinePath) || !fs.existsSync(currentPath)) {
    console.log(`${colors.yellow}⚠️  No baseline found for comparison${colors.reset}\n`);
    return;
  }
  
  const baseline = JSON.parse(fs.readFileSync(baselinePath, 'utf8'));
  const current = JSON.parse(fs.readFileSync(currentPath, 'utf8'));
  
  console.log(`\n${colors.cyan}📊 Bundle Size Comparison${colors.reset}\n`);
  console.log(`${'='.repeat(60)  }\n`);
  
  for (const [project, currentSizes] of Object.entries(current.bundles)) {
    const baselineSizes = baseline.bundles[project];
    
    if (!baselineSizes) continue;
    
    console.log(`${colors.blue}${project}${colors.reset}\n`);
    
    const diff = currentSizes.total - baselineSizes.total;
    const diffPercent = ((diff / baselineSizes.total) * 100).toFixed(1);
    
    let color = colors.green;
    let icon = '📉';
    if (diff > 0) {
      color = colors.red;
      icon = '📈';
    }
    
    console.log(
      `  ${icon} Total: ${color}${diff > 0 ? '+' : ''}${diff.toFixed(2)} KB${colors.reset} ` +
      `(${diff > 0 ? '+' : ''}${diffPercent}%)`
    );
    console.log(`     Baseline: ${baselineSizes.total.toFixed(2)} KB`);
    console.log(`     Current:  ${currentSizes.total.toFixed(2)} KB\n`);
  }
}

// Main execution
const command = process.argv[2] || 'check';

switch (command) {
  case 'check':
    checkBundleSizes();
    break;
    
  case 'report':
    generateReport();
    break;
    
  case 'compare':
    compareWithBaseline();
    break;
    
  default:
    console.log('Usage: node bundle-size-tracker.js [check|report|compare]');
    console.log('  check   - Check bundle sizes against thresholds (default)');
    console.log('  report  - Generate bundle size report');
    console.log('  compare - Compare with baseline');
    process.exit(1);
}
