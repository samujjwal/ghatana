#!/usr/bin/env node

import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = process.cwd();
const patterns = [
  { name: 'InMemory', regex: /\bInMemory[A-Z]\w*/g, severity: 'P0' },
  { name: 'Noop', regex: /\bNoop[A-Z]\w*/g, severity: 'P0' },
  { name: 'Fallback', regex: /\bFallback[A-Z]\w*/g, severity: 'P0' },
  { name: 'Sample', regex: /\bSample[A-Z]\w*/g, severity: 'P0' },
  { name: 'Local', regex: /\bLocal[A-Z]\w*/g, severity: 'P0' },
  { name: 'Test', regex: /\bTest[A-Z]\w*/g, severity: 'P0' },
  { name: 'Mock', regex: /\bMock[A-Z]\w*/g, severity: 'P0' },
  { name: 'Stub', regex: /\bStub[A-Z]\w*/g, severity: 'P0' },
  { name: 'Fake', regex: /\bFake[A-Z]\w*/g, severity: 'P0' },
  { name: 'Dummy', regex: /\bDummy[A-Z]\w*/g, severity: 'P0' },
  { name: 'Placeholder', regex: /\bPlaceholder[A-Z]\w*/g, severity: 'P0' },
  { name: 'Deterministic', regex: /\bDeterministic[A-Z]\w*/g, severity: 'P0' },
  { name: 'Simulator', regex: /\bSimulator[A-Z]\w*/g, severity: 'P0' },
  { name: 'Demo', regex: /\bDemo[A-Z]\w*/g, severity: 'P0' },
  { name: 'Example', regex: /\bExample[A-Z]\w*/g, severity: 'P0' },
];

const excludedDirs = [
  'node_modules',
  '.git',
  'build',
  'dist',
  'target',
  'out',
  '.gradle',
  '.idea',
  '.vscode',
  '__tests__',
  'test',
  'tests',
  'spec',
  'specs',
  '.kernel',
  'coverage',
  '.nyc_output',
  '.next',
  '.turbo',
];

const excludedFiles = [
  '*.test.ts',
  '*.test.tsx',
  '*.test.js',
  '*.test.mjs',
  '*.test.java',
  '*Test.java',
  '*Test.kt',
  '*Test.groovy',
  '*.spec.ts',
  '*.spec.tsx',
  '*.spec.js',
  '*.spec.mjs',
  '*.spec.java',
  '*Spec.java',
  '*Spec.kt',
  '*Spec.groovy',
  '*.mock.ts',
  '*.mock.tsx',
  '*.mock.js',
  '*.mock.mjs',
  '*.mock.java',
  '*Mock.java',
  '*.stub.ts',
  '*.stub.tsx',
  '*.stub.js',
  '*.stub.mjs',
  '*.stub.java',
  '*Stub.java',
];

function isExcludedDir(dirName) {
  return excludedDirs.includes(dirName) || dirName.startsWith('.');
}

function isExcludedFile(fileName) {
  return excludedFiles.some(pattern => {
    const regex = new RegExp(pattern.replace('*', '.*'));
    return regex.test(fileName);
  });
}

function shouldScanFile(filePath) {
  const ext = path.extname(filePath);
  const allowedExtensions = ['.java', '.kt', '.groovy', '.ts', '.tsx', '.js', '.mjs'];
  
  if (!allowedExtensions.includes(ext)) {
    return false;
  }

  const fileName = path.basename(filePath);
  if (isExcludedFile(fileName)) {
    return false;
  }

  return true;
}

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const findings = [];

  for (const pattern of patterns) {
    const matches = content.match(pattern.regex);
    if (matches) {
      const uniqueMatches = [...new Set(matches)];
      for (const match of uniqueMatches) {
        // Check if the match is in a test file or test directory
        const isTestContext = filePath.includes('/test/') || 
                           filePath.includes('/tests/') ||
                           filePath.includes('/__tests__/') ||
                           filePath.includes('.test.') ||
                           filePath.includes('.spec.');
        
        // Skip if it's in a test context
        if (isTestContext) {
          continue;
        }

        // Check if the match is in a commented line
        const lines = content.split('\n');
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.includes(match)) {
            // Check if the line is a comment
            const trimmed = line.trim();
            if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
              continue;
            }
            
            // Check if the match is in a string literal (basic check)
            const stringLiteralMatch = line.match(/["']([^"']*\bInMemory[^"']*)["']/);
            if (stringLiteralMatch) {
              continue;
            }

            findings.push({
              pattern: pattern.name,
              match,
              line: i + 1,
              severity: pattern.severity,
            });
          }
        }
      }
    }
  }

  return findings;
}

function scanDirectory(dir, results = []) {
  const entries = readdirSync(dir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);

    if (entry.isDirectory()) {
      if (!isExcludedDir(entry.name)) {
        scanDirectory(fullPath, results);
      }
    } else if (entry.isFile() && shouldScanFile(fullPath)) {
      try {
        const findings = scanFile(fullPath);
        if (findings.length > 0) {
          results.push({
            file: path.relative(repoRoot, fullPath),
            findings,
          });
        }
      } catch (error) {
        console.warn(`Failed to scan ${fullPath}: ${error.message}`);
      }
    }
  }

  return results;
}

function main() {
  console.log('Scanning for production-unsafe patterns...');
  console.log('Patterns:', patterns.map(p => p.name).join(', '));
  console.log('');

  const results = scanDirectory(repoRoot);

  if (results.length === 0) {
    console.log('✓ No production-unsafe patterns found');
    process.exit(0);
  }

  console.log(`Found ${results.length} files with production-unsafe patterns:\n`);

  let totalFindings = 0;
  let p0Count = 0;

  for (const result of results) {
    console.log(`📄 ${result.file}`);
    for (const finding of result.findings) {
      console.log(`  [${finding.severity}] Line ${finding.line}: ${finding.pattern} → ${finding.match}`);
      totalFindings++;
      if (finding.severity === 'P0') {
        p0Count++;
      }
    }
    console.log('');
  }

  console.log(`\nSummary: ${totalFindings} findings (${p0Count} P0) in ${results.length} files`);

  if (p0Count > 0) {
    console.error('\n❌ P0 violations found - production-unsafe patterns detected');
    process.exit(1);
  }

  console.log('\n⚠️  Non-P0 findings found - review recommended');
  process.exit(0);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
