#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function formatIssue(field, issue, remediation) {
  return { field, issue, remediation };
}

export function validateDuplicationExceptions(document, options = {}) {
  const today = options.today ?? new Date().toISOString().slice(0, 10);
  const issues = [];

  if (!document || typeof document !== 'object') {
    return [formatIssue('document', 'must be an object', 'Load config/duplication-exceptions.json as an object.')];
  }
  if (typeof document.version !== 'string' || !/^\d+\.\d+\.\d+$/.test(document.version)) {
    issues.push(formatIssue('version', 'must be a semver string', 'Set version to a string like 1.0.0.'));
  }
  if (!Array.isArray(document.exceptions)) {
    issues.push(formatIssue('exceptions', 'must be an array', 'Set exceptions to an array.'));
    return issues;
  }

  document.exceptions.forEach((entry, index) => {
    const prefix = `exceptions[${index}]`;
    const requiredFields = ['id', 'owner', 'duplicateType', 'paths', 'reason', 'riskLevel', 'expiryDate', 'removalPlan', 'validationCheck'];
    for (const field of requiredFields) {
      if (!(field in entry)) {
        issues.push(formatIssue(`${prefix}.${field}`, 'is required', `Add ${field} to ${prefix}.`));
      }
    }

    if (!Array.isArray(entry.paths) || entry.paths.length < 2) {
      issues.push(formatIssue(`${prefix}.paths`, 'must contain at least two paths', `List the duplicate paths in ${prefix}.paths.`));
    }
    if (typeof entry.owner !== 'string' || entry.owner.trim().length === 0) {
      issues.push(formatIssue(`${prefix}.owner`, 'must be a non-empty string', `Set ${prefix}.owner to the accountable team or owner.`));
    }
    if (typeof entry.removalPlan !== 'string' || entry.removalPlan.trim().length === 0) {
      issues.push(formatIssue(`${prefix}.removalPlan`, 'must be a non-empty string', `Set ${prefix}.removalPlan to an actionable remediation plan.`));
    }
    if (typeof entry.validationCheck !== 'string' || entry.validationCheck.trim().length === 0) {
      issues.push(formatIssue(`${prefix}.validationCheck`, 'must be a non-empty string', `Set ${prefix}.validationCheck to the enforcement command or test.`));
    }
    if (typeof entry.expiryDate !== 'string' || Number.isNaN(Date.parse(entry.expiryDate))) {
      issues.push(formatIssue(`${prefix}.expiryDate`, 'must be a valid ISO date', `Set ${prefix}.expiryDate to a valid YYYY-MM-DD date.`));
    } else if (entry.expiryDate < today) {
      issues.push(formatIssue(`${prefix}.expiryDate`, `exception expired on ${entry.expiryDate}`, `Remove ${prefix} or renew it with current evidence before ${today}.`));
    }

    if (entry.riskLevel === 'high' && !/active remediation/i.test(entry.removalPlan ?? '')) {
      issues.push(formatIssue(`${prefix}.removalPlan`, 'high-risk duplicates require active remediation language', `Update ${prefix}.removalPlan to explicitly state active remediation.`));
    }
  });

  return issues;
}

export function checkDuplicationExceptions() {
  return validateDuplicationExceptions(readJson('config/duplication-exceptions.json'));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const issues = checkDuplicationExceptions();

  if (issues.length === 0) {
    console.log('OK: duplication exception checks passed.');
    process.exit(0);
  }

  console.error('FAIL: duplication exception checks found issues:');
  for (const issue of issues) {
    console.error(` - ${issue.field}: ${issue.issue}. Remediation: ${issue.remediation}`);
  }
  process.exit(1);
}