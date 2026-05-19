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
export const PHASE_PLAN_STATUSES = new Set([
  'not-started',
  'in-progress',
  'pilot-ready',
  'executable',
  'blocked',
]);
export const PILOT_RELEVANCE_VALUES = new Set([
  'digital-marketing',
  'phr',
  'both',
  'future-products',
  'platform-provider-only',
]);
export const OPENING_PILOT_PRODUCT_IDS = ['digital-marketing', 'phr'];

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

function validateIntegerArray(domain, field, issues, { minItems = 0 } = {}) {
  const value = domain[field];
  if (!Array.isArray(value)) {
    issues.push(formatIssue(domain.id ?? '<unknown>', field, 'must be an array', `Set ${field} to an array of integers.`));
    return;
  }
  if (value.length < minItems) {
    issues.push(formatIssue(domain.id ?? '<unknown>', field, `must include at least ${minItems} item(s)`, `Add at least ${minItems} ${field} entry.`));
  }
  value.forEach((entry, index) => {
    if (typeof entry !== 'number' || !Number.isInteger(entry)) {
      issues.push(formatIssue(domain.id ?? '<unknown>', `${field}[${index}]`, 'must be an integer', `Replace ${field}[${index}] with an integer.`));
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
    validateStringArray(domain, 'minimumValidationCommands', issues, { minItems: 1 });
    validateStringArray(domain, 'fullRegressionCommands', issues, { minItems: 1 });
    validateStringArray(domain, 'ownedContracts', issues);
    validateStringArray(domain, 'forbiddenOwnership', issues);
    validateStringArray(domain, 'pilotDependency', issues);

    if (!PHASE_PLAN_STATUSES.has(domain?.phasePlanStatus)) {
      issues.push(formatIssue(domainId, 'phasePlanStatus', `unknown phase plan status '${String(domain?.phasePlanStatus)}'`, 'Use one of not-started, in-progress, pilot-ready, executable, or blocked.'));
    }
    if (!PILOT_RELEVANCE_VALUES.has(domain?.pilotRelevance)) {
      issues.push(formatIssue(domainId, 'pilotRelevance', `unknown pilot relevance '${String(domain?.pilotRelevance)}'`, 'Use one of digital-marketing, phr, both, future-products, or platform-provider-only.'));
    }
    if (!Array.isArray(domain?.currentBlockingGaps)) {
      issues.push(formatIssue(domainId, 'currentBlockingGaps', 'must be an array', 'Set currentBlockingGaps to an array of owner/expiry/classification/remediationPhase objects.'));
    } else {
      domain.currentBlockingGaps.forEach((gap, index) => {
        if (!isNonEmptyString(gap?.owner)) {
          issues.push(formatIssue(domainId, `currentBlockingGaps[${index}].owner`, 'must be a non-empty string', 'Set gap owner to the accountable team.'));
        }
        if (!isNonEmptyString(gap?.expiry) || !/^\d{4}-\d{2}-\d{2}$/.test(gap.expiry)) {
          issues.push(formatIssue(domainId, `currentBlockingGaps[${index}].expiry`, 'must be an ISO date string (YYYY-MM-DD)', 'Set gap expiry to an explicit review date.'));
        }
        if (!DOMAIN_CLASSIFICATIONS.has(gap?.classification)) {
          issues.push(formatIssue(domainId, `currentBlockingGaps[${index}].classification`, `unknown classification '${String(gap?.classification)}'`, 'Use a domain registry classification.'));
        }
        if (typeof gap?.remediationPhase !== 'number' || !Number.isInteger(gap.remediationPhase)) {
          issues.push(formatIssue(domainId, `currentBlockingGaps[${index}].remediationPhase`, 'must be an integer', 'Set remediationPhase to the phase that owns closing the gap.'));
        }
      });
    }

    // Validate boundaryPolicy
    if (domain.boundaryPolicy) {
      if (!domain.boundaryPolicy.mayImport || !Array.isArray(domain.boundaryPolicy.mayImport)) {
        issues.push(formatIssue(domainId, 'boundaryPolicy.mayImport', 'must be an array', 'Set boundaryPolicy.mayImport to an array of import patterns.'));
      }
      if (!domain.boundaryPolicy.mustNotImport || !Array.isArray(domain.boundaryPolicy.mustNotImport)) {
        issues.push(formatIssue(domainId, 'boundaryPolicy.mustNotImport', 'must be an array', 'Set boundaryPolicy.mustNotImport to an array of forbidden import patterns.'));
      }
      if (!domain.boundaryPolicy.mayOwn || !Array.isArray(domain.boundaryPolicy.mayOwn)) {
        issues.push(formatIssue(domainId, 'boundaryPolicy.mayOwn', 'must be an array', 'Set boundaryPolicy.mayOwn to an array of ownership patterns.'));
      }
      if (!domain.boundaryPolicy.mustNotOwn || !Array.isArray(domain.boundaryPolicy.mustNotOwn)) {
        issues.push(formatIssue(domainId, 'boundaryPolicy.mustNotOwn', 'must be an array', 'Set boundaryPolicy.mustNotOwn to an array of forbidden ownership patterns.'));
      }
    } else {
      issues.push(formatIssue(domainId, 'boundaryPolicy', 'must be present', 'Add boundaryPolicy with mayImport, mustNotImport, mayOwn, and mustNotOwn fields.'));
    }

    // Validate sourceOfTruth
    if (!isNonEmptyString(domain.sourceOfTruth)) {
      issues.push(formatIssue(domainId, 'sourceOfTruth', 'must be a non-empty string', 'Set sourceOfTruth to the canonical source document or directory.'));
    }

    // Validate independentExecutionChecks
    if (!Array.isArray(domain.independentExecutionChecks) || domain.independentExecutionChecks.length === 0) {
      issues.push(formatIssue(domainId, 'independentExecutionChecks', 'must be a non-empty array', 'Add at least one independent execution check command.'));
    }

    // Validate fullRegressionChecks
    if (!Array.isArray(domain.fullRegressionChecks) || domain.fullRegressionChecks.length === 0) {
      issues.push(formatIssue(domainId, 'fullRegressionChecks', 'must be a non-empty array', 'Add at least one full regression check command.'));
    }

    // Validate journey field (required for all domains)
    validateStringArray(domain, 'journey', issues, { minItems: 1 });

    // Validate phase, exitCriteria, blockingGaps, evidenceRequired, phaseOwner (required for all domains)
    validateIntegerArray(domain, 'phase', issues, { minItems: 1 });
    validateStringArray(domain, 'exitCriteria', issues, { minItems: 1 });
    validateStringArray(domain, 'blockingGaps', issues);
    validateStringArray(domain, 'evidenceRequired', issues, { minItems: 1 });
    if (!isNonEmptyString(domain.phaseOwner)) {
      issues.push(formatIssue(domainId, 'phaseOwner', 'must be a non-empty string', 'Set phaseOwner to the owning team or individual for this phase.'));
    }

    // Validate reasonCodes for non-executable classifications
    if (domain.classification !== 'existing-executable') {
      if (!Array.isArray(domain.reasonCodes) || domain.reasonCodes.length === 0) {
        issues.push(formatIssue(domainId, 'reasonCodes', `required for classification '${domain.classification}'`, 'Add reasonCodes explaining why this domain is not fully executable.'));
      }
    }

    for (const productId of domain.productAssociations ?? []) {
      if (isNonEmptyString(productId) && !productIds.has(productId)) {
        issues.push(formatIssue(domainId, 'productAssociations', `unknown product association '${productId}'`, `Replace '${productId}' with a product id from config/canonical-product-registry.json.`));
      }
    }

    if (domain.pilotRelevance === 'both') {
      for (const productId of OPENING_PILOT_PRODUCT_IDS) {
        if (!domain.productAssociations?.includes(productId)) {
          issues.push(formatIssue(domainId, 'productAssociations', `pilot relevance "both" requires product association '${productId}'`, `Add '${productId}' to productAssociations or choose narrower pilotRelevance.`));
        }
      }
    } else if (OPENING_PILOT_PRODUCT_IDS.includes(domain.pilotRelevance) && !domain.productAssociations?.includes(domain.pilotRelevance)) {
      issues.push(formatIssue(domainId, 'productAssociations', `pilot relevance '${domain.pilotRelevance}' requires matching product association`, `Add '${domain.pilotRelevance}' to productAssociations or choose a different pilotRelevance.`));
    }

    for (const productId of domain.pilotDependency ?? []) {
      if (!OPENING_PILOT_PRODUCT_IDS.includes(productId)) {
        issues.push(formatIssue(domainId, 'pilotDependency', `unknown opening pilot dependency '${productId}'`, 'Use digital-marketing or phr.'));
      }
      if (!domain.productAssociations?.includes(productId)) {
        issues.push(formatIssue(domainId, 'pilotDependency', `pilot dependency '${productId}' must also be listed in productAssociations`, `Add '${productId}' to productAssociations or remove it from pilotDependency.`));
      }
    }

    for (const command of domain.minimumValidationCommands ?? []) {
      if (!domain.requiredChecks?.includes(command) && !domain.independentExecutionChecks?.includes(command)) {
        issues.push(formatIssue(domainId, 'minimumValidationCommands', `command '${command}' is not listed in requiredChecks or independentExecutionChecks`, 'Keep minimum validation commands anchored to executable domain checks.'));
      }
    }

    for (const command of domain.fullRegressionCommands ?? []) {
      if (!domain.fullRegressionChecks?.includes(command)) {
        issues.push(formatIssue(domainId, 'fullRegressionCommands', `command '${command}' is not listed in fullRegressionChecks`, 'Keep full regression commands anchored to executable domain checks.'));
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
