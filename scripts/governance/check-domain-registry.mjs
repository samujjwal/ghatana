#!/usr/bin/env node
/**
 * check-domain-registry.mjs
 *
 * Governance wrapper: validates config/domain-registry.json for required shape,
 * vocabulary, and evidence. Delegates to ../validate-domain-registry.mjs and
 * adds additional kernel-lifecycle requirements.
 *
 * Exits non-zero on any violation.
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

/** Canonical classification vocabulary for domain-registry.json */
export const DOMAIN_STATUS_VOCABULARY = new Set([
  'existing-executable',
  'existing-partial',
  'declared-only',
  'target-architecture',
  'anti-pattern',
]);

/** Additional human-readable names accepted in DOMAIN_WORKSTREAM_MAP.md */
const HUMAN_READABLE_CLASSIFICATIONS = new Set([
  'existing-and-executable',
  'existing-but-partial',
]);

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function resolvePath(relative) {
  return path.join(repoRoot, relative);
}

/**
 * Run domain-registry governance checks.
 * Returns an array of issue strings (empty = pass).
 */
export function runDomainRegistryChecks(options = {}) {
  const issues = [];
  const registryPath = resolvePath('config/domain-registry.json');

  if (!existsSync(registryPath)) {
    issues.push('MISSING: config/domain-registry.json does not exist. Create it with all 17 domains.');
    return issues;
  }

  let registry;
  try {
    registry = readJson(registryPath);
  } catch (err) {
    issues.push(`PARSE_ERROR: config/domain-registry.json is not valid JSON: ${String(err)}`);
    return issues;
  }

  const domains = registry.domains;
  if (!Array.isArray(domains) || domains.length === 0) {
    issues.push('MISSING: domain-registry.json must have a non-empty "domains" array.');
    return issues;
  }

  for (const domain of domains) {
    const id = domain.id ?? '<unknown>';

    // Must have id
    if (typeof domain.id !== 'string' || domain.id.trim().length === 0) {
      issues.push(`[${id}] Missing required field: id`);
    }

    // Must have classification or status (accept both field names)
    const classificationValue = domain.classification ?? domain.status;
    if (classificationValue === undefined || classificationValue === null) {
      issues.push(`[${id}] Missing required field: classification (must be one of ${[...DOMAIN_STATUS_VOCABULARY].join(', ')})`);
    } else if (!DOMAIN_STATUS_VOCABULARY.has(classificationValue) && !HUMAN_READABLE_CLASSIFICATIONS.has(classificationValue)) {
      issues.push(`[${id}] Invalid classification '${classificationValue}'. Must be one of: ${[...DOMAIN_STATUS_VOCABULARY].join(', ')}`);
    }

    // Primary locations must be listed (can be empty array but field must exist)
    if (!Array.isArray(domain.primaryLocations)) {
      issues.push(`[${id}] Missing required field: primaryLocations (must be an array)`);
    } else {
      // Verify listed locations exist (skip if skipLocationCheck option set)
      if (options.checkLocations !== false) {
        for (const loc of domain.primaryLocations) {
          if (typeof loc === 'string' && loc.trim() && !loc.startsWith('#')) {
            const fullPath = resolvePath(loc);
            if (!existsSync(fullPath)) {
              issues.push(`[${id}] primaryLocation does not exist: ${loc}`);
            }
          }
        }
      }
    }

    // existing-executable domains must have evidence and validation checks
    if (classificationValue === 'existing-executable' || classificationValue === 'existing-and-executable') {
      if (!Array.isArray(domain.currentStateEvidence) || domain.currentStateEvidence.length === 0) {
        issues.push(`[${id}] existing-executable domain requires currentStateEvidence with at least one entry`);
      }
      if (!Array.isArray(domain.requiredChecks) && !Array.isArray(domain.validationChecks)) {
        issues.push(`[${id}] existing-executable domain requires requiredChecks or validationChecks with at least one command`);
      }
    }

    // Platform domains must not claim product-specific behavior
    if (domain.owner === 'platform' && Array.isArray(domain.allowedResponsibilities)) {
      for (const resp of domain.allowedResponsibilities) {
        if (typeof resp === 'string') {
          const lower = resp.toLowerCase();
          // Check for product names bleeding into platform responsibilities
          const productNames = ['digital-marketing', 'flashit', 'phr', 'finance', 'yappc', 'data-cloud', 'tutorputor'];
          for (const product of productNames) {
            if (lower.includes(product) && lower.includes('specific')) {
              issues.push(`[${id}] Platform domain must not claim product-specific behavior: "${resp}"`);
            }
          }
        }
      }
    }
  }

  return issues;
}

// CLI entrypoint
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  // Also delegate to the existing validate-domain-registry.mjs for full validation
  const validateModule = path.join(repoRoot, 'scripts', 'validate-domain-registry.mjs');
  const { validateDomainRegistryFiles } = await import(validateModule);
  const domainRegistryPath = path.join(repoRoot, 'config', 'domain-registry.json');

  let baseIssues = [];
  try {
    baseIssues = validateDomainRegistryFiles({ registryPath: domainRegistryPath });
  } catch (err) {
    baseIssues = [`FATAL: ${String(err)}`];
  }

  const governanceIssues = runDomainRegistryChecks({ checkLocations: false });
  const allIssues = [...baseIssues, ...governanceIssues];

  if (allIssues.length > 0) {
    console.error('FAIL: domain registry governance checks found issues:');
    for (const issue of allIssues) {
      const msg = typeof issue === 'object' ? JSON.stringify(issue) : String(issue);
      console.error(` - ${msg}`);
    }
    process.exit(1);
  } else {
    console.log('OK: domain registry governance checks passed.');
  }
}
