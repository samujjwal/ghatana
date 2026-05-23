#!/usr/bin/env node

import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = process.cwd();

// Dangerous patterns that should not be reachable in production
const DANGEROUS_PATTERNS = [
  {
    pattern: /InMemory[A-Z]\w*/g,
    name: 'InMemory',
    severity: 'P0',
    description: 'In-memory implementations are not production-safe for persistence, caching, or state',
    allowedContexts: ['test', 'Test', 'mock', 'Mock', 'stub', 'Stub', 'fixture', 'Fixture'],
  },
  {
    pattern: /NoOp[A-Z]\w*/g,
    name: 'NoOp',
    severity: 'P0',
    description: 'No-op implementations bypass critical functionality',
    allowedContexts: ['test', 'Test', 'mock', 'Mock', 'stub', 'Stub', 'fixture', 'Fixture'],
  },
  {
    pattern: /Fallback[A-Z]\w*/g,
    name: 'Fallback',
    severity: 'P1',
    description: 'Fallback implementations may degrade production behavior',
    allowedContexts: ['test', 'Test', 'mock', 'Mock', 'stub', 'Stub'],
  },
  {
    pattern: /Sample[A-Z]\w*/g,
    name: 'Sample',
    severity: 'P1',
    description: 'Sample implementations are for demonstration only',
    allowedContexts: ['test', 'Test', 'example', 'Example', 'demo', 'Demo', 'sample', 'Sample'],
  },
  {
    pattern: /Local[A-Z]\w*Service/g,
    name: 'LocalService',
    severity: 'P1',
    description: 'Local-only services are not production-safe',
    allowedContexts: ['test', 'Test', 'dev', 'Dev', 'local', 'Local'],
  },
  {
    pattern: /Test[A-Z]\w*/g,
    name: 'Test',
    severity: 'P0',
    description: 'Test implementations should not be used in production',
    allowedContexts: ['test', 'Test', 'mock', 'Mock', 'stub', 'Stub', 'fixture', 'Fixture'],
  },
];

// File extensions to scan
const SCAN_EXTENSIONS = ['.java', '.kt', '.ts', '.tsx', '.js', '.jsx'];

// Directories to exclude from scanning
const EXCLUDE_DIRS = [
  'build',
  'dist',
  'out',
  'target',
  'node_modules',
  '.gradle',
  '.git',
  '.kernel',
  'bin',
  'coverage',
  '.next',
  '.nuxt',
  '__tests__',
  'test',
  'tests',
];

function isExcludedDir(dirPath) {
  const parts = dirPath.split(path.sep);
  return parts.some(part => EXCLUDE_DIRS.includes(part));
}

function shouldScanFile(filePath) {
  const ext = path.extname(filePath);
  if (!SCAN_EXTENSIONS.includes(ext)) {
    return false;
  }
  
  // Skip test files
  if (filePath.includes('__tests__') || 
      filePath.includes('/test/') || 
      filePath.includes('/tests/') ||
      filePath.includes('.test.') ||
      filePath.includes('.spec.')) {
    return false;
  }
  
  return true;
}

function scanFile(filePath) {
  const violations = [];
  
  try {
    const content = readFileSync(filePath, 'utf8');
    const lines = content.split('\n');
    
    for (const dangerousPattern of DANGEROUS_PATTERNS) {
      const matches = content.match(dangerousPattern.pattern);
      if (!matches) {
        continue;
      }
      
      // Find line numbers for each match
      for (const match of matches) {
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.includes(match)) {
            // Check if in allowed context
            const isInAllowedContext = dangerousPattern.allowedContexts.some(
              ctx => line.includes(ctx) || filePath.includes(ctx)
            );
            
            if (!isInAllowedContext) {
              violations.push({
                pattern: dangerousPattern.name,
                match,
                line: i + 1,
                column: line.indexOf(match) + 1,
                severity: dangerousPattern.severity,
                description: dangerousPattern.description,
                filePath,
              });
            }
          }
        }
      }
    }
  } catch (error) {
    // Skip files that can't be read
  }
  
  return violations;
}

function scanDirectory(dirPath, violations = []) {
  try {
    const entries = readdirSync(dirPath);
    
    for (const entry of entries) {
      const fullPath = path.join(dirPath, entry);
      const stat = statSync(fullPath);
      
      if (stat.isDirectory()) {
        if (!isExcludedDir(fullPath)) {
          scanDirectory(fullPath, violations);
        }
      } else if (stat.isFile() && shouldScanFile(fullPath)) {
        const fileViolations = scanFile(fullPath);
        violations.push(...fileViolations);
      }
    }
  } catch (error) {
    // Skip directories that can't be read
  }
  
  return violations;
}

function groupViolationsByPattern(violations) {
  const grouped = {};
  
  for (const violation of violations) {
    if (!grouped[violation.pattern]) {
      grouped[violation.pattern] = [];
    }
    grouped[violation.pattern].push(violation);
  }
  
  return grouped;
}

function generateReport(violations) {
  const grouped = groupViolationsByPattern(violations);
  const p0Count = violations.filter(v => v.severity === 'P0').length;
  const p1Count = violations.filter(v => v.severity === 'P1').length;
  
  const report = {
    generatedAt: new Date().toISOString(),
    summary: {
      totalViolations: violations.length,
      p0Violations: p0Count,
      p1Violations: p1Count,
      patternsFound: Object.keys(grouped).length,
    },
    violationsByPattern: {},
    allViolations: violations,
  };
  
  for (const [pattern, patternViolations] of Object.entries(grouped)) {
    report.violationsByPattern[pattern] = {
      count: patternViolations.length,
      severity: patternViolations[0].severity,
      description: patternViolations[0].description,
      files: [...new Set(patternViolations.map(v => v.filePath))],
      locations: patternViolations.map(v => ({
        file: v.filePath,
        line: v.line,
        column: v.column,
        match: v.match,
      })),
    };
  }
  
  return report;
}

export function checkProductionFallbacks({ scanPath = repoRoot } = {}) {
  const violations = scanDirectory(scanPath);
  const report = generateReport(violations);
  
  return {
    status: report.summary.p0Violations === 0 ? 'passed' : 'failed',
    report,
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = checkProductionFallbacks();
  
  console.log(JSON.stringify(result.report, null, 2));
  
  if (result.status === 'failed') {
    console.error(`\nFound ${result.report.summary.p0Violations} P0 violations in production code`);
    process.exit(1);
  }
  
  console.log('\nNo production-reachable dangerous patterns found');
  process.exit(0);
}
