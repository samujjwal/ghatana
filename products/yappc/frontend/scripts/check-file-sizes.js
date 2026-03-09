#!/usr/bin/env node
/**
 * Check File Sizes
 * 
 * Ensures all TypeScript files comply with size guidelines.
 * Guidelines: Target ≤400 lines, Maximum ≤600 lines
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const MAX_LINES = 600;
const TARGET_LINES = 400;
const WARN_LINES = 500;

// Directories to check
const DIRS_TO_CHECK = ['libs', 'apps'];

// Files to exclude
const EXCLUDE_PATTERNS = [
  /node_modules/,
  /dist/,
  /\.test\./,
  /\.spec\./,
  /__tests__/,
  /\.d\.ts$/,
];

function shouldExclude(filePath) {
  return EXCLUDE_PATTERNS.some(pattern => pattern.test(filePath));
}

function countLines(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    return content.split('\n').length;
  } catch (error) {
    console.error(`Error reading ${filePath}:`, error.message);
    return 0;
  }
}

function findTypeScriptFiles(dir) {
  const files = [];
  
  function walk(currentPath) {
    if (!fs.existsSync(currentPath)) return;
    
    const entries = fs.readdirSync(currentPath, { withFileTypes: true });
    
    for (const entry of entries) {
      const fullPath = path.join(currentPath, entry.name);
      
      if (shouldExclude(fullPath)) continue;
      
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.isFile() && /\.(ts|tsx)$/.test(entry.name)) {
        files.push(fullPath);
      }
    }
  }
  
  walk(dir);
  return files;
}

function main() {
  console.log('🔍 Checking TypeScript file sizes...\n');
  
  const allFiles = [];
  for (const dir of DIRS_TO_CHECK) {
    const dirPath = path.join(process.cwd(), dir);
    if (fs.existsSync(dirPath)) {
      allFiles.push(...findTypeScriptFiles(dirPath));
    }
  }
  
  const results = {
    excellent: [],  // ≤400 lines
    good: [],       // 401-500 lines
    warning: [],    // 501-600 lines
    error: [],      // >600 lines
  };
  
  for (const file of allFiles) {
    const lines = countLines(file);
    const relativePath = path.relative(process.cwd(), file);
    
    if (lines > MAX_LINES) {
      results.error.push({ file: relativePath, lines });
    } else if (lines > WARN_LINES) {
      results.warning.push({ file: relativePath, lines });
    } else if (lines > TARGET_LINES) {
      results.good.push({ file: relativePath, lines });
    } else {
      results.excellent.push({ file: relativePath, lines });
    }
  }
  
  // Print summary
  console.log('📊 Summary:');
  console.log(`   ✅ Excellent (≤${TARGET_LINES} lines): ${results.excellent.length}`);
  console.log(`   ✓  Good (${TARGET_LINES + 1}-${WARN_LINES} lines): ${results.good.length}`);
  console.log(`   ⚠️  Warning (${WARN_LINES + 1}-${MAX_LINES} lines): ${results.warning.length}`);
  console.log(`   ❌ Error (>${MAX_LINES} lines): ${results.error.length}`);
  console.log();
  
  // Print warnings
  if (results.warning.length > 0) {
    console.log('⚠️  Files approaching size limit:');
    results.warning
      .sort((a, b) => b.lines - a.lines)
      .forEach(({ file, lines }) => {
        console.log(`   ${lines.toString().padStart(4)} lines: ${file}`);
      });
    console.log();
  }
  
  // Print errors
  if (results.error.length > 0) {
    console.log('❌ Files exceeding size limit:');
    results.error
      .sort((a, b) => b.lines - a.lines)
      .forEach(({ file, lines }) => {
        console.log(`   ${lines.toString().padStart(4)} lines: ${file}`);
      });
    console.log();
  }
  
  // Calculate compliance
  const totalFiles = allFiles.length;
  const compliantFiles = totalFiles - results.error.length;
  const compliance = ((compliantFiles / totalFiles) * 100).toFixed(1);
  
  console.log(`📈 Compliance: ${compliance}% (${compliantFiles}/${totalFiles} files)`);
  console.log();
  
  // Exit with error if any files exceed limit
  if (results.error.length > 0) {
    console.log('💡 Tip: Consider refactoring large files into smaller, focused modules.');
    console.log('   See REFACTORING_PROGRESS.md for guidelines and examples.');
    console.log();
    process.exit(1);
  }
  
  console.log('✅ All files comply with size guidelines!');
  process.exit(0);
}

main();
