/**
 * Check script for Kernel-YAPPC boundary enforcement.
 *
 * Ensures YAPPC does not directly import from platform packages.
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const YAPPC_PATH = join(__dirname, '..', 'products', 'yappc');
const PLATFORM_PATHS = [
  join(__dirname, '..', 'platform', 'java'),
  join(__dirname, '..', 'platform', 'typescript'),
];

const VIOLATING_PATTERNS = [
  /from ['"]@ghatana\/platform-java/,
  /from ['"]@ghatana\/platform-typescript/,
  /from ['"]@ghatana\/kernel-(?!product-contracts\b)/,
];

function isFile(path) {
  try {
    return statSync(path).isFile();
  } catch {
    return false;
  }
}

function isDirectory(path) {
  try {
    return statSync(path).isDirectory();
  } catch {
    return false;
  }
}

function findFiles(dir, extensions = ['.ts', '.js', '.tsx', '.jsx']) {
  const files = [];

  if (!isDirectory(dir)) {
    return files;
  }

  const entries = readdirSync(dir);

  for (const entry of entries) {
    const fullPath = join(dir, entry);

    if (isDirectory(fullPath)) {
      // Skip node_modules and test directories
      if (entry === 'node_modules' || entry === '__tests__' || entry === 'dist') {
        continue;
      }
      files.push(...findFiles(fullPath, extensions));
    } else if (isFile(fullPath)) {
      const ext = entry.slice(entry.lastIndexOf('.'));
      if (extensions.includes(ext)) {
        files.push(fullPath);
      }
    }
  }

  return files;
}

function checkFileForViolations(filePath, violations) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const lineNum = i + 1;

      for (const pattern of VIOLATING_PATTERNS) {
        if (pattern.test(line)) {
          violations.push({
            file: filePath,
            line: lineNum,
            content: line.trim(),
            pattern: pattern.source,
          });
        }
      }
    }
  } catch (error) {
    console.error(`Error reading ${filePath}:`, error.message);
  }
}

function main() {
  if (!isDirectory(YAPPC_PATH)) {
    console.error(`YAPPC directory not found at ${YAPPC_PATH}`);
    process.exit(1);
  }

  console.log('Checking YAPPC for platform boundary violations...');

  const files = findFiles(YAPPC_PATH);
  console.log(`Found ${files.length} files to check.`);

  const violations = [];

  for (const file of files) {
    checkFileForViolations(file, violations);
  }

  if (violations.length > 0) {
    console.error('Found platform boundary violations:');
    for (const violation of violations) {
      console.error(
        `  - ${violation.file}:${violation.line}: ${violation.content}`
      );
      console.error(`    Pattern: ${violation.pattern}`);
    }
    console.error('\nYAPPC should not directly import from platform packages.');
    console.error('Use provider contracts and interfaces instead.');
    process.exit(1);
  }

  console.log('No platform boundary violations found.');
}

main();
