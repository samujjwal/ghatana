#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateJsonSchemaLite } from './lib/validate-json-schema-lite.mjs';

export const DOMAIN_CLASSIFICATIONS = new Set([
  'existing-executable',
  'existing-partial',
  'declared-only',
  'target-architecture',
  'anti-pattern',
]);

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function formatIssue(domainId, field, issue, remediation) {
  return { domainId, field, issue, remediation };
}

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function domainIdFromSchemaPath(document, schemaPath) {
  const match = /^#\/domains\/(\d+)(?:\/|$)/.exec(schemaPath);
  if (!match) {
    return '<registry>';
  }
  const domainIndex = Number(match[1]);
  return document?.domains?.[domainIndex]?.id ?? '<unknown>';
}

function fieldFromSchemaPath(schemaPath) {
  if (!schemaPath.startsWith('#/')) {
    return schemaPath;
  }
  const segments = schemaPath
    .slice(2)
    .split('/')
    .filter((segment) => segment !== 'domains' && !/^\d+$/.test(segment));
  return segments.length > 0 ? segments.join('.') : 'document';
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function validateStringArray(domain, field, issues, { minItems = 0 } = {}) {
  const value = domain[field];
  if (!Array.isArray(value)) {
    issues.push(formatIssue(domain.id ?? '<unknown>', field, 'must be an array', `Set ${field} to an array of strings.`));
    return;
  }
  if (value.length < minItems) {
    issues.push(formatIssue(domain.id ?? '<unknown>', field, `must include at least ${minItems} item(s)`, `Add at least ${minItems} ${field} entry.`));
  }
  value.forEach((entry, index) => {
    if (!isNonEmptyString(entry)) {
      issues.push(formatIssue(domain.id ?? '<unknown>', `${field}[${index}]`, 'must be a non-empty string', `Replace ${field}[${index}] with a non-empty string.`));
    }
  });
}

export function validateDomainRegistryDocument(document, options = {}) {
  const issues = [];
  const productIds = options.productIds ?? new Set();
  const root = options.repoRoot ?? repoRoot;
  const pathExists = options.pathExists ?? ((relativePath) => existsSync(path.join(root, relativePath)));
  const schema = options.schema ?? readJson(path.join(root, 'config/domain-registry.schema.json'));

  if (!document || typeof document !== 'object') {
    return [formatIssue('<registry>', 'document', 'must be an object', 'Load a JSON object for the registry document.')];
  }

  const schemaErrors = validateJsonSchemaLite(schema, document);
  for (const schemaError of schemaErrors) {
    issues.push(
      formatIssue(
        domainIdFromSchemaPath(document, schemaError.path),
        fieldFromSchemaPath(schemaError.path),
        `schema violation: ${schemaError.message}`,
        'Update config/domain-registry.json to satisfy config/domain-registry.schema.json.',
      ),
    );
  }

  if (!isNonEmptyString(document.version) || !/^\d+\.\d+\.\d+$/.test(document.version)) {
    issues.push(formatIssue('<registry>', 'version', 'must be a semver string', 'Set version to a string like 1.0.0.'));
  }

  if (!Array.isArray(document.domains)) {
    issues.push(formatIssue('<registry>', 'domains', 'must be an array', 'Set domains to an array of domain records.'));
    return issues;
  }

  for (const domain of document.domains) {
    const domainId = isNonEmptyString(domain?.id) ? domain.id : '<unknown>';

    if (!isNonEmptyString(domain?.id)) {
      issues.push(formatIssue(domainId, 'id', 'must be a non-empty string', 'Set id to the canonical kebab-case domain identifier.'));
    }
    if (!isNonEmptyString(domain?.name)) {
      issues.push(formatIssue(domainId, 'name', 'must be a non-empty string', 'Set name to a human-readable domain name.'));
    }
    if (!isNonEmptyString(domain?.ownerLayer)) {
      issues.push(formatIssue(domainId, 'ownerLayer', 'must be a non-empty string', 'Set ownerLayer to the owning layer.'));
    }
    if (!DOMAIN_CLASSIFICATIONS.has(domain?.classification)) {
      issues.push(formatIssue(domainId, 'classification', `unknown classification '${String(domain?.classification)}'`, 'Use one of existing-executable, existing-partial, declared-only, target-architecture, or anti-pattern.'));
    }

    validateStringArray(domain, 'primaryLocations', issues, { minItems: 1 });
    validateStringArray(domain, 'secondaryLocations', issues);
    validateStringArray(domain, 'allowedConsumers', issues);
    validateStringArray(domain, 'forbiddenDependencies', issues);
    validateStringArray(domain, 'requiredChecks', issues);
    validateStringArray(domain, 'productAssociations', issues);
    validateStringArray(domain, 'currentStateEvidence', issues);

    for (const productId of domain.productAssociations ?? []) {
      if (isNonEmptyString(productId) && !productIds.has(productId)) {
        issues.push(formatIssue(domainId, 'productAssociations', `unknown product association '${productId}'`, `Replace '${productId}' with a product id from config/canonical-product-registry.json.`));
      }
    }

    if (domain.classification !== 'target-architecture') {
      for (const locationField of ['primaryLocations', 'secondaryLocations']) {
        for (const location of domain[locationField] ?? []) {
          if (isNonEmptyString(location) && !pathExists(location)) {
            issues.push(formatIssue(domainId, locationField, `referenced path '${location}' does not exist`, `Create '${location}' or update ${locationField} to an existing repo path.`));
          }
        }
      }
    }

    if (domain.classification === 'existing-executable') {
      if ((domain.currentStateEvidence ?? []).length === 0) {
        issues.push(formatIssue(domainId, 'currentStateEvidence', 'existing-executable domains require evidence', 'Add at least one evidence reference proving the domain is executable today.'));
      }
      if ((domain.requiredChecks ?? []).length === 0) {
        issues.push(formatIssue(domainId, 'requiredChecks', 'existing-executable domains require validation commands', 'Add at least one executable validation command.'));
      }
    }
  }

  return issues;
}

export function loadCanonicalProductIds(root = repoRoot) {
  const registry = readJson(path.join(root, 'config/canonical-product-registry.json'));
  return new Set(Object.keys(registry.registry ?? {}));
}

export function validateDomainRegistryFiles(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const document = options.document ?? readJson(path.join(root, 'config/domain-registry.json'));
  const schema = options.schema ?? readJson(path.join(root, 'config/domain-registry.schema.json'));
  const productIds = options.productIds ?? loadCanonicalProductIds(root);
  return validateDomainRegistryDocument(document, {
    repoRoot: root,
    schema,
    productIds,
    pathExists: options.pathExists,
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const issues = validateDomainRegistryFiles();

  if (issues.length === 0) {
    console.log('OK: domain registry validation passed.');
    process.exit(0);
  }

  console.error('FAIL: domain registry validation found issues:');
  for (const issue of issues) {
    console.error(` - [${issue.domainId}] ${issue.field}: ${issue.issue}. Remediation: ${issue.remediation}`);
  }
  process.exit(1);
}