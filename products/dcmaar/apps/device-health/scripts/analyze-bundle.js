#!/usr/bin/env node

/**
 * Bundle Size Analyzer
 * Analyzes and reports on bundle sizes
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const DIST_DIR = path.join(__dirname, '../dist/chrome');
const MAX_BUNDLE_SIZE = 10 * 1024 * 1024; // 10MB
const MAX_CHUNK_SIZE = 1 * 1024 * 1024; // 1MB
const WARN_CHUNK_SIZE = 500 * 1024; // 500KB

/**
 * Get file size in bytes
 */
function getFileSize(filePath) {
  try {
    const stats = fs.statSync(filePath);
    return stats.size;
  } catch {
    return 0;
  }
}

/**
 * Format bytes to human readable
 */
function formatBytes(bytes) {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))  } ${  sizes[i]}`;
}

/**
 * Get all files recursively
 */
function getAllFiles(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  
  files.forEach(file => {
    const filePath = path.join(dir, file);
    if (fs.statSync(filePath).isDirectory()) {
      getAllFiles(filePath, fileList);
    } else {
      fileList.push(filePath);
    }
  });
  
  return fileList;
}

/**
 * Analyze bundle
 */
function analyzeBundle() {
  console.log('🔍 Analyzing bundle...\n');
  
  if (!fs.existsSync(DIST_DIR)) {
    console.error('❌ Distribution directory not found. Run build first.');
    process.exit(1);
  }
  
  const files = getAllFiles(DIST_DIR);
  const fileData = files.map(file => ({
    path: path.relative(DIST_DIR, file),
    size: getFileSize(file),
    type: path.extname(file),
  }));
  
  // Sort by size
  fileData.sort((a, b) => b.size - a.size);
  
  // Calculate total size
  const totalSize = fileData.reduce((sum, file) => sum + file.size, 0);
  
  // Group by type
  const byType = {};
  fileData.forEach(file => {
    if (!byType[file.type]) {
      byType[file.type] = { count: 0, size: 0 };
    }
    byType[file.type].count++;
    byType[file.type].size += file.size;
  });
  
  // Print summary
  console.log('📊 Bundle Summary');
  console.log('═'.repeat(60));
  console.log(`Total Size: ${formatBytes(totalSize)}`);
  console.log(`Total Files: ${fileData.length}`);
  console.log('');
  
  // Check total size
  if (totalSize > MAX_BUNDLE_SIZE) {
    console.log(`❌ Total bundle size exceeds limit (${formatBytes(MAX_BUNDLE_SIZE)})`);
  } else {
    console.log(`✅ Total bundle size within limit (${formatBytes(MAX_BUNDLE_SIZE)})`);
  }
  console.log('');
  
  // Print by type
  console.log('📁 Size by File Type');
  console.log('─'.repeat(60));
  Object.entries(byType)
    .sort((a, b) => b[1].size - a[1].size)
    .forEach(([type, data]) => {
      const percentage = ((data.size / totalSize) * 100).toFixed(1);
      console.log(`${type || 'no extension'}: ${formatBytes(data.size)} (${percentage}%) - ${data.count} files`);
    });
  console.log('');
  
  // Print largest files
  console.log('📦 Largest Files (Top 10)');
  console.log('─'.repeat(60));
  fileData.slice(0, 10).forEach((file, index) => {
    const percentage = ((file.size / totalSize) * 100).toFixed(1);
    const status = file.size > MAX_CHUNK_SIZE ? '❌' : 
                   file.size > WARN_CHUNK_SIZE ? '⚠️' : '✅';
    console.log(`${index + 1}. ${status} ${file.path}`);
    console.log(`   ${formatBytes(file.size)} (${percentage}%)`);
  });
  console.log('');
  
  // Check for large chunks
  const largeChunks = fileData.filter(f => f.size > MAX_CHUNK_SIZE);
  const warnChunks = fileData.filter(f => f.size > WARN_CHUNK_SIZE && f.size <= MAX_CHUNK_SIZE);
  
  if (largeChunks.length > 0) {
    console.log(`❌ ${largeChunks.length} file(s) exceed chunk size limit (${formatBytes(MAX_CHUNK_SIZE)})`);
    largeChunks.forEach(file => {
      console.log(`   - ${file.path}: ${formatBytes(file.size)}`);
    });
    console.log('');
  }
  
  if (warnChunks.length > 0) {
    console.log(`⚠️  ${warnChunks.length} file(s) are large (>${formatBytes(WARN_CHUNK_SIZE)})`);
    warnChunks.forEach(file => {
      console.log(`   - ${file.path}: ${formatBytes(file.size)}`);
    });
    console.log('');
  }
  
  // Recommendations
  console.log('💡 Recommendations');
  console.log('─'.repeat(60));
  
  if (largeChunks.length > 0) {
    console.log('• Consider code splitting for large files');
    console.log('• Use dynamic imports for heavy dependencies');
    console.log('• Enable tree-shaking for unused code');
  }
  
  if (warnChunks.length > 0) {
    console.log('• Review large files for optimization opportunities');
    console.log('• Consider lazy loading non-critical features');
  }
  
  const jsSize = byType['.js']?.size || 0;
  const cssSize = byType['.css']?.size || 0;
  
  if (jsSize > totalSize * 0.7) {
    console.log('• JavaScript makes up >70% of bundle - consider minification');
  }
  
  if (cssSize > totalSize * 0.3) {
    console.log('• CSS makes up >30% of bundle - consider purging unused styles');
  }
  
  console.log('');
  
  // Exit with error if bundle is too large
  if (totalSize > MAX_BUNDLE_SIZE || largeChunks.length > 0) {
    console.log('❌ Bundle analysis failed - size limits exceeded');
    process.exit(1);
  }
  
  console.log('✅ Bundle analysis passed');
}

// Run analysis
analyzeBundle();
