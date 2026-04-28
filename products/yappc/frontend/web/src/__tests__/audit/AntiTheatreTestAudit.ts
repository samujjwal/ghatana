/**
 * Anti-Theatre Test Audit
 *
 * Audit script to identify and report object-literal tests that don't actually test anything meaningful.
 * These are tests that use hardcoded object literals instead of real assertions, providing false confidence.
 *
 * Usage: npx tsx __tests__/audit/AntiTheatreTestAudit.ts
 *
 * @doc.type script
 * @doc.purpose Anti-theatre audit for object-literal tests
 * @doc.layer quality-engineering
 */

import * as fs from 'fs';
import * as path from 'path';
import * as glob from 'glob';

interface TestIssue {
  file: string;
  line: number;
  type: 'hardcoded-assertion' | 'mock-return' | 'shallow-comparison' | 'no-assertion';
  description: string;
  severity: 'error' | 'warning' | 'info';
}

interface AuditResult {
  totalFiles: number;
  issues: TestIssue[];
  summary: {
    errors: number;
    warnings: number;
    info: number;
  };
}

// Patterns that indicate anti-theatre tests
const ANTI_THEATRE_PATTERNS = [
  {
    pattern: /expect\([^)]+\)\.toEqual\(\{[^}]*\}\)/g,
    type: 'hardcoded-assertion' as const,
    description: 'Hardcoded object literal in assertion - may not test actual behavior',
    severity: 'warning' as const,
  },
  {
    pattern: /expect\([^)]+\)\.toBe\([^)]+\)/g,
    type: 'shallow-comparison' as const,
    description: 'Shallow comparison - verify this tests meaningful behavior',
    severity: 'info' as const,
  },
  {
    pattern: /mockReturnValue\(\{[^}]*\}\)/g,
    type: 'mock-return' as const,
    description: 'Mock returning hardcoded object - may not test real integration',
    severity: 'warning' as const,
  },
  {
    pattern: /describe\([^)]+\)\s*\(\s*\(\)\s*=>\s*\{\s*\}\s*\)/g,
    type: 'no-assertion' as const,
    description: 'Empty test block with no assertions',
    severity: 'error' as const,
  },
];

function auditFile(filePath: string): TestIssue[] {
  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const issues: TestIssue[] = [];

  lines.forEach((line, index) => {
    ANTI_THEATRE_PATTERNS.forEach(({ pattern, type, description, severity }) => {
      const matches = line.matchAll(pattern);
      for (const match of matches) {
        issues.push({
          file: filePath,
          line: index + 1,
          type,
          description,
          severity,
        });
      }
    });
  });

  return issues;
}

function runAudit(testDir: string): AuditResult {
  const testFiles = glob.sync('**/*.test.{ts,tsx,js,jsx}', {
    cwd: testDir,
    absolute: true,
  });

  const allIssues: TestIssue[] = [];

  testFiles.forEach((file) => {
    const issues = auditFile(file);
    allIssues.push(...issues);
  });

  const summary = {
    errors: allIssues.filter((i) => i.severity === 'error').length,
    warnings: allIssues.filter((i) => i.severity === 'warning').length,
    info: allIssues.filter((i) => i.severity === 'info').length,
  };

  return {
    totalFiles: testFiles.length,
    issues: allIssues,
    summary,
  };
}

function generateReport(result: AuditResult): string {
  const lines: string[] = [];

  lines.push('# Anti-Theatre Test Audit Report');
  lines.push('');
  lines.push(`## Summary`);
  lines.push(`- Total test files scanned: ${result.totalFiles}`);
  lines.push(`- Total issues found: ${result.issues.length}`);
  lines.push(`  - Errors: ${result.summary.errors}`);
  lines.push(`  - Warnings: ${result.summary.warnings}`);
  lines.push(`  - Info: ${result.summary.info}`);
  lines.push('');

  if (result.issues.length > 0) {
    lines.push('## Issues');
    lines.push('');

    // Group by file
    const issuesByFile = result.issues.reduce((acc, issue) => {
      if (!acc[issue.file]) {
        acc[issue.file] = [];
      }
      acc[issue.file].push(issue);
      return acc;
    }, {} as Record<string, TestIssue[]>);

    Object.entries(issuesByFile).forEach(([file, issues]) => {
      lines.push(`### ${file}`);
      lines.push('');
      issues.forEach((issue) => {
        lines.push(`- Line ${issue.line}: [${issue.severity.toUpperCase()}] ${issue.type}`);
        lines.push(`  ${issue.description}`);
      });
      lines.push('');
    });
  } else {
    lines.push('✅ No anti-theatre test issues found!');
  }

  lines.push('');
  lines.push('## Recommendations');
  lines.push('');
  lines.push('1. Review all warnings and errors manually');
  lines.push('2. Replace hardcoded assertions with actual test data or fixtures');
  lines.push('3. Ensure mocks return realistic data that matches production');
  lines.push('4. Add meaningful assertions that verify actual behavior');
  lines.push('5. Delete empty test blocks or add proper assertions');

  return lines.join('\n');
}

function main() {
  const testDir = path.join(__dirname, '../../__tests__');
  const result = runAudit(testDir);
  const report = generateReport(result);

  console.log(report);

  // Write report to file
  const reportPath = path.join(__dirname, 'ANTI_THEATRE_AUDIT_REPORT.md');
  fs.writeFileSync(reportPath, report);

  // Exit with error code if issues found
  if (result.summary.errors > 0) {
    process.exit(1);
  }
}

// Run if executed directly
if (require.main === module) {
  main();
}

export { auditFile, runAudit, generateReport };
