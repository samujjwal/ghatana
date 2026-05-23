#!/usr/bin/env node

/**
 * P0-07: Governance check for gradlew portability
 *
 * Ensures that all root scripts use cross-platform gradlew invocation:
 * - Shell scripts: ./gradlew (not bare gradlew)
 * - Windows batch: gradlew.bat (not bare gradlew)
 * - Node scripts: platform-specific logic or ./gradlew
 *
 * Bare gradlew usage is rejected as it's not portable across platforms.
 */

import { readFileSync, readdirSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const ROOT_DIR = join(__dirname, '..');
const SCRIPTS_DIR = join(ROOT_DIR, 'scripts');

const ALLOWLIST = [
  // Gradle wrapper scripts themselves
  'gradlew',
  'gradlew.bat',
  // Product-local gradlew files
  'products/dcmaar/apps/agent-react-native/android/gradlew',
  'products/dcmaar/apps/agent-react-native/android/gradlew.bat',
  // Config files that reference gradlew
  'eslint.config.js',
  // The check script itself
  'check-gradlew-portability.mjs',
  // Scripts with file existence checks
  'test-tiered.sh',
];

function checkFile(filePath) {
  const relativePath = relative(ROOT_DIR, filePath);
  
  // Skip allowlisted files
  if (ALLOWLIST.some(allowed => relativePath.endsWith(allowed))) {
    return { file: relativePath, issues: [] };
  }

  const content = readFileSync(filePath, 'utf-8');
  const issues = [];

  // Check for bare gradlew in shell scripts
  if (filePath.endsWith('.sh')) {
    const lines = content.split('\n');
    lines.forEach((line, index) => {
      // Match bare gradlew (not ./gradlew, not in comments, not in strings with ./)
      const bareGradlewMatch = line.match(/(?<!\.\/)gradlew(?!\.bat)/g);
      if (bareGradlewMatch) {
        // Filter out comments and strings that might contain ./gradlew
        const trimmed = line.trim();
        // Skip file existence checks like [ -f "gradlew" ]
        if (trimmed.includes('[ -f ') || trimmed.includes('[ !-f ') || trimmed.includes('test -f ')) {
          return;
        }
        if (!trimmed.startsWith('#') && !trimmed.includes('./gradlew')) {
          issues.push({
            line: index + 1,
            message: `Bare 'gradlew' usage found. Use './gradlew' for portability.`,
            code: line.trim()
          });
        }
      }
    });
  }

  // Check for bare gradlew in batch files
  if (filePath.endsWith('.bat')) {
    const lines = content.split('\n');
    lines.forEach((line, index) => {
      // Match bare gradlew (not gradlew.bat)
      const bareGradlewMatch = line.match(/(?<!\.bat)gradlew(?!\.bat)/g);
      if (bareGradlewMatch) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('REM') && !trimmed.startsWith('::') && !trimmed.includes('gradlew.bat')) {
          issues.push({
            line: index + 1,
            message: `Bare 'gradlew' usage found. Use 'gradlew.bat' for Windows portability.`,
            code: line.trim()
          });
        }
      }
    });
  }

  // Check for bare gradlew in Node scripts
  if (filePath.endsWith('.mjs') || filePath.endsWith('.js')) {
    const lines = content.split('\n');
    lines.forEach((line, index) => {
      // Match bare gradlew in string literals (not ./gradlew, not gradlew.bat)
      const bareGradlewMatch = line.match(/['"`]gradlew(?!\.bat)['"`]/g);
      if (bareGradlewMatch) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('//') && !trimmed.includes('./gradlew') && !trimmed.includes('gradlew.bat')) {
          issues.push({
            line: index + 1,
            message: `Bare 'gradlew' string found. Use './gradlew' or platform-specific logic.`,
            code: line.trim()
          });
        }
      }
    });
  }

  return { file: relativePath, issues };
}

function scanDirectory(dir, results = []) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules, .git, and other common exclusions
      if (['node_modules', '.git', 'build', 'dist', 'target'].includes(entry.name)) {
        continue;
      }
      // Only scan scripts directory and root
      if (dir === SCRIPTS_DIR || dir === ROOT_DIR) {
        scanDirectory(fullPath, results);
      }
    } else if (entry.isFile()) {
      if (entry.name.endsWith('.sh') || entry.name.endsWith('.bat') || 
          entry.name.endsWith('.mjs') || entry.name.endsWith('.js')) {
        results.push(checkFile(fullPath));
      }
    }
  }
  
  return results;
}

function main() {
  console.log('Checking gradlew portability in root scripts...\n');
  
  const results = scanDirectory(SCRIPTS_DIR);
  const rootResults = scanDirectory(ROOT_DIR);
  
  // Filter out duplicates from root scan
  const allResults = [...results];
  rootResults.forEach(rootResult => {
    if (!results.some(r => r.file === rootResult.file)) {
      allResults.push(rootResult);
    }
  });
  
  const filesWithIssues = allResults.filter(r => r.issues.length > 0);
  
  if (filesWithIssues.length === 0) {
    console.log('✓ All scripts use portable gradlew invocation');
    console.log('✓ Shell scripts use ./gradlew');
    console.log('✓ Batch files use gradlew.bat');
    console.log('✓ Node scripts use platform-specific logic or ./gradlew');
    process.exit(0);
  }
  
  console.log(`✗ Found ${filesWithIssues.length} file(s) with non-portable gradlew usage:\n`);
  
  filesWithIssues.forEach(({ file, issues }) => {
    console.log(`  ${file}:`);
    issues.forEach(issue => {
      console.log(`    Line ${issue.line}: ${issue.message}`);
      console.log(`      ${issue.code}`);
    });
    console.log('');
  });
  
  console.log('Fix guidelines:');
  console.log('  - Shell scripts: Use ./gradlew instead of gradlew');
  console.log('  - Batch files: Use gradlew.bat instead of gradlew');
  console.log('  - Node scripts: Use platform-specific logic or ./gradlew');
  console.log('    Example: const gradleCommand = process.platform === "win32" ? "gradlew.bat" : "./gradlew"');
  
  process.exit(1);
}

main();
