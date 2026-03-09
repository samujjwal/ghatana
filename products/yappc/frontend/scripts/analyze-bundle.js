#!/usr/bin/env node

/**
 * Bundle size analyzer script
 * 
 * This script analyzes the bundle size of the web app and generates a report.
 * It uses webpack-bundle-analyzer to visualize the bundle size.
 * 
 * Usage:
 * 1. Build the web app with stats: `pnpm build:web --stats`
 * 2. Run this script: `node scripts/analyze-bundle.js`
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const { BundleAnalyzerPlugin } = require('webpack-bundle-analyzer');

// Configuration
const statsFilePath = path.join(__dirname, '../apps/web/dist/stats.json');
const reportDir = path.join(__dirname, '../reports/bundle-analysis');

// Create reports directory if it doesn't exist
if (!fs.existsSync(reportDir)) {
  fs.mkdirSync(reportDir, { recursive: true });
}

// Check if stats file exists
if (!fs.existsSync(statsFilePath)) {
  console.error('Stats file not found. Please build the web app with stats first:');
  console.error('pnpm build:web --stats');
  process.exit(1);
}

console.log('Analyzing bundle size...');

// Generate report
try {
  const stats = JSON.parse(fs.readFileSync(statsFilePath, 'utf8'));
  
  // Use webpack-bundle-analyzer to generate a static report
  const analyzer = new BundleAnalyzerPlugin({
    analyzerMode: 'static',
    reportFilename: path.join(reportDir, 'report.html'),
    openAnalyzer: false,
    generateStatsFile: true,
    statsFilename: path.join(reportDir, 'stats.json'),
  });
  
  // Generate report
  analyzer.apply({
    hooks: {
      done: {
        tap: (name, callback) => callback(stats),
      },
    },
  });
  
  console.log(`Bundle analysis report generated at: ${path.join(reportDir, 'report.html')}`);
  
  // Print summary of largest bundles
  console.log('\nLargest bundles:');
  const assets = stats.assets || [];
  assets.sort((a, b) => b.size - a.size);
  
  assets.slice(0, 10).forEach((asset, index) => {
    const sizeInKB = (asset.size / 1024).toFixed(2);
    console.log(`${index + 1}. ${asset.name}: ${sizeInKB} KB`);
  });
  
  // Open the report in the default browser
  const openCommand = process.platform === 'win32' ? 'start' : 
                      process.platform === 'darwin' ? 'open' : 'xdg-open';
  
  console.log('\nOpening report in browser...');
  execSync(`${openCommand} ${path.join(reportDir, 'report.html')}`);
  
} catch (error) {
  console.error('Error analyzing bundle:', error);
  process.exit(1);
}
