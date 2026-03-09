#!/usr/bin/env node

/**
 * Build Verification Script
 * 
 * This script verifies the production build for the YAPPC App Creator.
 * It checks for common issues and ensures the build meets quality standards.
 */

const path = require('path');
const fs = require('fs-extra');
const chalk = require('chalk');
const { execSync } = require('child_process');
const lighthouse = require('lighthouse');
const chromeLauncher = require('chrome-launcher');
const { gzipSync } = require('zlib');
const { compress } = require('brotli');
const filesize = require('filesize');
const handler = require('serve-handler');
const http = require('http');

// Performance budgets
const PERFORMANCE_BUDGETS = {
  maxJsSize: 250 * 1024, // 250kb
  maxCssSize: 50 * 1024, // 50kb
  maxFirstContentfulPaint: 2000, // 2s
  maxLargestContentfulPaint: 2500, // 2.5s
  maxTotalBlockingTime: 300, // 300ms
  maxCumulativeLayoutShift: 0.1, // 0.1
  minPerformanceScore: 90, // 90/100
  minAccessibilityScore: 95, // 95/100
  minBestPracticesScore: 95, // 95/100
  minSeoScore: 90, // 90/100
};

// Build directory
const buildDir = path.join(process.cwd(), 'apps/web/dist');

// Check if build directory exists
if (!fs.existsSync(buildDir)) {
  console.error(chalk.red('Build directory not found. Run build script first.'));
  process.exit(1);
}

// Start verification
console.log(chalk.bold('Starting build verification...'));

// Check build info
const buildInfoPath = path.join(buildDir, 'build-info.json');
if (fs.existsSync(buildInfoPath)) {
  const buildInfo = require(buildInfoPath);
  console.log(chalk.bold('Build info:'));
  console.log(`Version: ${buildInfo.version}`);
  console.log(`Build ID: ${buildInfo.buildId}`);
  console.log(`Build Time: ${buildInfo.buildTime}`);
  console.log(`Environment: ${buildInfo.environment}`);
  console.log();
} else {
  console.warn(chalk.yellow('Build info file not found.'));
}

// Check asset manifest
const manifestPath = path.join(buildDir, 'asset-manifest.json');
if (fs.existsSync(manifestPath)) {
  console.log(chalk.green('✓ Asset manifest found'));
} else {
  console.error(chalk.red('✗ Asset manifest not found'));
}

// Check service worker
const serviceWorkerPath = path.join(buildDir, 'service-worker.js');
if (fs.existsSync(serviceWorkerPath)) {
  console.log(chalk.green('✓ Service worker found'));
} else {
  console.warn(chalk.yellow('⚠ Service worker not found'));
}

// Check index.html
const indexPath = path.join(buildDir, 'index.html');
if (fs.existsSync(indexPath)) {
  console.log(chalk.green('✓ Index HTML found'));
  
  // Check for viewport meta tag
  const indexContent = fs.readFileSync(indexPath, 'utf8');
  if (indexContent.includes('<meta name="viewport"')) {
    console.log(chalk.green('✓ Viewport meta tag found'));
  } else {
    console.warn(chalk.yellow('⚠ Viewport meta tag not found'));
  }
  
  // Check for theme color meta tag
  if (indexContent.includes('<meta name="theme-color"')) {
    console.log(chalk.green('✓ Theme color meta tag found'));
  } else {
    console.warn(chalk.yellow('⚠ Theme color meta tag not found'));
  }
  
  // Check for description meta tag
  if (indexContent.includes('<meta name="description"')) {
    console.log(chalk.green('✓ Description meta tag found'));
  } else {
    console.warn(chalk.yellow('⚠ Description meta tag not found'));
  }
} else {
  console.error(chalk.red('✗ Index HTML not found'));
}

// Check for favicon
const faviconPath = path.join(buildDir, 'favicon.ico');
if (fs.existsSync(faviconPath)) {
  console.log(chalk.green('✓ Favicon found'));
} else {
  console.warn(chalk.yellow('⚠ Favicon not found'));
}

// Check for robots.txt
const robotsPath = path.join(buildDir, 'robots.txt');
if (fs.existsSync(robotsPath)) {
  console.log(chalk.green('✓ Robots.txt found'));
} else {
  console.warn(chalk.yellow('⚠ Robots.txt not found'));
}

// Check for manifest.json
const webManifestPath = path.join(buildDir, 'manifest.json');
if (fs.existsSync(webManifestPath)) {
  console.log(chalk.green('✓ Web manifest found'));
} else {
  console.warn(chalk.yellow('⚠ Web manifest not found'));
}

// Check asset sizes
console.log(chalk.bold('\nChecking asset sizes...'));

const jsFiles = [];
const cssFiles = [];
let totalJsSize = 0;
let totalCssSize = 0;

// Find all JS and CSS files
fs.readdirSync(buildDir, { recursive: true })
  .filter(file => /\.(js|css)$/.test(file))
  .forEach(file => {
    const filePath = path.join(buildDir, file);
    const stats = fs.statSync(filePath);
    const fileContent = fs.readFileSync(filePath);
    const gzipSize = gzipSync(fileContent).length;
    const brotliSize = compress(fileContent).length;
    
    const fileInfo = {
      name: file,
      size: stats.size,
      gzipSize,
      brotliSize,
    };
    
    if (file.endsWith('.js')) {
      jsFiles.push(fileInfo);
      totalJsSize += gzipSize;
    } else if (file.endsWith('.css')) {
      cssFiles.push(fileInfo);
      totalCssSize += gzipSize;
    }
  });

// Print JS file sizes
console.log(chalk.bold('JavaScript files:'));
jsFiles.forEach(file => {
  console.log(
    `  ${chalk.cyan(file.name)}: ${filesize(file.size)} (${filesize(file.gzipSize)} gzipped, ${filesize(
      file.brotliSize
    )} brotli)`
  );
});
console.log(chalk.bold(`Total JS size: ${filesize(totalJsSize)} gzipped`));

// Check JS budget
if (totalJsSize > PERFORMANCE_BUDGETS.maxJsSize) {
  console.warn(
    chalk.yellow(
      `⚠ JavaScript size exceeds budget: ${filesize(totalJsSize)} > ${filesize(
        PERFORMANCE_BUDGETS.maxJsSize
      )}`
    )
  );
} else {
  console.log(
    chalk.green(
      `✓ JavaScript size within budget: ${filesize(totalJsSize)} <= ${filesize(
        PERFORMANCE_BUDGETS.maxJsSize
      )}`
    )
  );
}

// Print CSS file sizes
console.log(chalk.bold('\nCSS files:'));
cssFiles.forEach(file => {
  console.log(
    `  ${chalk.cyan(file.name)}: ${filesize(file.size)} (${filesize(file.gzipSize)} gzipped, ${filesize(
      file.brotliSize
    )} brotli)`
  );
});
console.log(chalk.bold(`Total CSS size: ${filesize(totalCssSize)} gzipped`));

// Check CSS budget
if (totalCssSize > PERFORMANCE_BUDGETS.maxCssSize) {
  console.warn(
    chalk.yellow(
      `⚠ CSS size exceeds budget: ${filesize(totalCssSize)} > ${filesize(
        PERFORMANCE_BUDGETS.maxCssSize
      )}`
    )
  );
} else {
  console.log(
    chalk.green(
      `✓ CSS size within budget: ${filesize(totalCssSize)} <= ${filesize(
        PERFORMANCE_BUDGETS.maxCssSize
      )}`
    )
  );
}

// Run Lighthouse audit
console.log(chalk.bold('\nRunning Lighthouse audit...'));

// Start a local server
const server = http.createServer((req, res) => {
  return handler(req, res, {
    public: buildDir,
    rewrites: [{ source: '**', destination: '/index.html' }],
  });
});

// Run Lighthouse
async function runLighthouse() {
  let chrome;
  try {
    // Start server
    await new Promise(resolve => server.listen(3000, resolve));
    console.log('Server started on http://localhost:3000');
    
    // Launch Chrome
    chrome = await chromeLauncher.launch({
      chromeFlags: ['--headless', '--disable-gpu', '--no-sandbox'],
    });
    
    // Run Lighthouse
    const results = await lighthouse('http://localhost:3000', {
      port: chrome.port,
      output: 'json',
      logLevel: 'error',
      onlyCategories: ['performance', 'accessibility', 'best-practices', 'seo'],
    });
    
    // Process results
    const { lhr } = results;
    
    console.log(chalk.bold('\nLighthouse scores:'));
    console.log(
      `Performance: ${formatScore(lhr.categories.performance.score)} ${getScoreIcon(
        lhr.categories.performance.score,
        PERFORMANCE_BUDGETS.minPerformanceScore / 100
      )}`
    );
    console.log(
      `Accessibility: ${formatScore(lhr.categories.accessibility.score)} ${getScoreIcon(
        lhr.categories.accessibility.score,
        PERFORMANCE_BUDGETS.minAccessibilityScore / 100
      )}`
    );
    console.log(
      `Best Practices: ${formatScore(lhr.categories['best-practices'].score)} ${getScoreIcon(
        lhr.categories['best-practices'].score,
        PERFORMANCE_BUDGETS.minBestPracticesScore / 100
      )}`
    );
    console.log(
      `SEO: ${formatScore(lhr.categories.seo.score)} ${getScoreIcon(
        lhr.categories.seo.score,
        PERFORMANCE_BUDGETS.minSeoScore / 100
      )}`
    );
    
    console.log(chalk.bold('\nCore Web Vitals:'));
    const fcp = lhr.audits['first-contentful-paint'].numericValue;
    const lcp = lhr.audits['largest-contentful-paint'].numericValue;
    const tbt = lhr.audits['total-blocking-time'].numericValue;
    const cls = lhr.audits['cumulative-layout-shift'].numericValue;
    
    console.log(
      `First Contentful Paint: ${(fcp / 1000).toFixed(2)}s ${getMetricIcon(
        fcp,
        PERFORMANCE_BUDGETS.maxFirstContentfulPaint
      )}`
    );
    console.log(
      `Largest Contentful Paint: ${(lcp / 1000).toFixed(2)}s ${getMetricIcon(
        lcp,
        PERFORMANCE_BUDGETS.maxLargestContentfulPaint
      )}`
    );
    console.log(
      `Total Blocking Time: ${tbt.toFixed(0)}ms ${getMetricIcon(
        tbt,
        PERFORMANCE_BUDGETS.maxTotalBlockingTime
      )}`
    );
    console.log(
      `Cumulative Layout Shift: ${cls.toFixed(3)} ${getMetricIcon(
        cls,
        PERFORMANCE_BUDGETS.maxCumulativeLayoutShift,
        false
      )}`
    );
    
    // Save Lighthouse report
    fs.writeFileSync(
      path.join(process.cwd(), 'lighthouse-report.json'),
      JSON.stringify(lhr, null, 2)
    );
    console.log(chalk.green('\n✓ Lighthouse report saved to lighthouse-report.json'));
    
    // Check if all scores meet minimum requirements
    const passesAllScores =
      lhr.categories.performance.score >= PERFORMANCE_BUDGETS.minPerformanceScore / 100 &&
      lhr.categories.accessibility.score >= PERFORMANCE_BUDGETS.minAccessibilityScore / 100 &&
      lhr.categories['best-practices'].score >= PERFORMANCE_BUDGETS.minBestPracticesScore / 100 &&
      lhr.categories.seo.score >= PERFORMANCE_BUDGETS.minSeoScore / 100;
    
    if (passesAllScores) {
      console.log(chalk.green('\n✓ All Lighthouse scores meet minimum requirements'));
    } else {
      console.warn(
        chalk.yellow('\n⚠ Some Lighthouse scores do not meet minimum requirements')
      );
    }
    
    return passesAllScores;
  } catch (error) {
    console.error(chalk.red('Error running Lighthouse:'), error);
    return false;
  } finally {
    // Cleanup
    if (chrome) {
      await chrome.kill();
    }
    server.close();
  }
}

// Format Lighthouse score
function formatScore(score) {
  return `${(score * 100).toFixed(0)}/100`;
}

// Get icon for score
function getScoreIcon(score, minScore) {
  return score >= minScore ? chalk.green('✓') : chalk.yellow('⚠');
}

// Get icon for metric
function getMetricIcon(value, threshold, higherIsBetter = false) {
  if (higherIsBetter) {
    return value >= threshold ? chalk.green('✓') : chalk.yellow('⚠');
  } else {
    return value <= threshold ? chalk.green('✓') : chalk.yellow('⚠');
  }
}

// Run verification
async function runVerification() {
  try {
    // Run Lighthouse audit
    const lighthousePassed = await runLighthouse();
    
    console.log(chalk.bold('\nVerification summary:'));
    
    // Check JS size
    const jsSizePassed = totalJsSize <= PERFORMANCE_BUDGETS.maxJsSize;
    console.log(
      `JavaScript size: ${jsSizePassed ? chalk.green('✓') : chalk.yellow('⚠')}`
    );
    
    // Check CSS size
    const cssSizePassed = totalCssSize <= PERFORMANCE_BUDGETS.maxCssSize;
    console.log(`CSS size: ${cssSizePassed ? chalk.green('✓') : chalk.yellow('⚠')}`);
    
    // Check Lighthouse scores
    console.log(
      `Lighthouse scores: ${lighthousePassed ? chalk.green('✓') : chalk.yellow('⚠')}`
    );
    
    // Overall result
    const allPassed = jsSizePassed && cssSizePassed && lighthousePassed;
    
    if (allPassed) {
      console.log(chalk.green('\n✓ Build verification passed!'));
    } else {
      console.warn(
        chalk.yellow('\n⚠ Build verification completed with warnings.')
      );
    }
    
    // Generate verification report
    const report = {
      timestamp: new Date().toISOString(),
      jsSizePassed,
      totalJsSize,
      jsThreshold: PERFORMANCE_BUDGETS.maxJsSize,
      cssSizePassed,
      totalCssSize,
      cssThreshold: PERFORMANCE_BUDGETS.maxCssSize,
      lighthousePassed,
      allPassed,
    };
    
    fs.writeFileSync(
      path.join(process.cwd(), 'build-verification-report.json'),
      JSON.stringify(report, null, 2)
    );
    console.log(
      chalk.green('✓ Verification report saved to build-verification-report.json')
    );
    
    process.exit(allPassed ? 0 : 1);
  } catch (error) {
    console.error(chalk.red('Error during verification:'), error);
    process.exit(1);
  }
}

runVerification();
