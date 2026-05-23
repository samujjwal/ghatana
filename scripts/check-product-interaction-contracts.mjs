#!/usr/bin/env node

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __filename = fileURLToPath(import.meta.url);
const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const registryPath = join(repoRoot, 'config', 'canonical-product-registry.json');
const evidencePath = join(repoRoot, '.kernel', 'evidence', 'product-interaction-coverage-matrix.json');
const registryDocument = JSON.parse(readFileSync(registryPath, 'utf8'));
const registry = registryDocument.registry ?? registryDocument;
const stableGeneratedAt = 'generated-on-demand';

const VALID_MODES = new Set([
  'request-response',
  'event-publish',
  'event-subscribe',
  'shared-evidence',
  'provider-capability',
]);

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

export function checkProductInteractionContracts(registryInput, options = {}) {
  const registry = registryInput.registry ?? registryInput;
  const productIds = new Set(Object.keys(registry));
  const errors = [];
  const providedContracts = new Map();
  const consumedContracts = [];
  const contractDeclarations = [];
  const schemaDigests = new Map();
  const sourceRefs = options.sourceRefs ?? collectInteractionSourceRefs(options.repoRoot ?? repoRoot);
  const loadKernelProduct =
    options.loadKernelProduct ??
    ((productId) => {
      const product = registry[productId];
      const lifecycleConfigPath = product?.lifecycleConfigPath;
      if (typeof lifecycleConfigPath !== 'string' || lifecycleConfigPath.length === 0) {
        return undefined;
      }
      if (!lifecycleConfigPath.endsWith('kernel-product.yaml')) {
        return undefined;
      }
      const absolutePath = join(repoRoot, lifecycleConfigPath);
      return {
        path: lifecycleConfigPath,
        config: YAML.parse(readFileSync(absolutePath, 'utf8')),
      };
    });

  for (const productId of productIds) {
    const loaded = loadKernelProduct(productId);
    if (loaded === undefined) {
      continue;
    }
    const interactions = loaded.config?.interactions;
    if (interactions === undefined) {
      continue;
    }
    for (const bucket of ['publishes', 'consumes', 'provides']) {
      const contracts = asArray(interactions[bucket]);
      contracts.forEach((contract, index) => validateContract(errors, productIds, productId, bucket, contract, index, {
        validateSchemaRefs: options.validateSchemaRefs !== false,
        schemaDigests,
        readSchemaFile: options.readSchemaFile,
      }));
      contracts.forEach((contract, index) => {
        if (typeof contract?.contractId === 'string') {
          contractDeclarations.push({ productId, bucket, contract, index });
        }
      });
      if (bucket === 'publishes' || bucket === 'provides') {
        for (const contract of contracts) {
          if (typeof contract?.contractId === 'string') {
            providedContracts.set(contract.contractId, { productId, contract });
          }
        }
      }
      if (bucket === 'consumes') {
        for (const contract of contracts) {
          consumedContracts.push({ productId, contract });
        }
      }
    }
  }

  for (const consumed of consumedContracts) {
    const contractId = consumed.contract?.contractId;
    if (typeof contractId !== 'string') {
      continue;
    }
    const provider = providedContracts.get(contractId);
    if (provider === undefined) {
      errors.push(`${consumed.productId}.interactions.consumes references ${contractId}, but no product publishes or provides it`);
      continue;
    }
    if (provider.contract.providerProductId !== consumed.contract.providerProductId) {
      errors.push(`${consumed.productId}.interactions.consumes ${contractId} expects provider ${consumed.contract.providerProductId}, but provider contract declares ${provider.contract.providerProductId}`);
    }
    validateContractSchemaParity(errors, provider.productId, provider.contract, consumed.productId, consumed.contract);
  }

  if (options.validateBridgeHandlers !== false) {
    validateBridgeHandlers(errors);
  }

  const coverageMatrix = buildInteractionCoverageMatrix(contractDeclarations, sourceRefs);
  for (const row of coverageMatrix) {
    if (row.handler.required && row.handler.status !== 'covered') {
      errors.push(`${row.declaringProductId}.interactions.${row.declarationKind} ${row.contractId} is missing ProductInteractionHandler coverage`);
    }
    if (row.tests.status !== 'covered') {
      errors.push(`${row.declaringProductId}.interactions.${row.declarationKind} ${row.contractId} is missing test coverage`);
    }
  }

  return {
    errors,
    consumedContractCount: consumedContracts.length,
    coverageMatrix,
    coverageSummary: summarizeCoverageMatrix(coverageMatrix),
  };
}

function collectJavaFiles(directory) {
  if (!existsSync(directory)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(directory)) {
    const absolutePath = join(directory, entry);
    const stat = statSync(absolutePath);
    if (stat.isDirectory()) {
      if (entry === 'build') {
        continue;
      }
      files.push(...collectJavaFiles(absolutePath));
    } else if (stat.isFile() && entry.endsWith('.java')) {
      files.push(absolutePath);
    }
  }
  return files;
}

function collectSourceFiles(directory, extensions) {
  if (!existsSync(directory)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(directory)) {
    const absolutePath = join(directory, entry);
    const stat = statSync(absolutePath);
    if (stat.isDirectory()) {
      if (['build', 'dist', 'node_modules', '.gradle', '.turbo'].includes(entry)) {
        continue;
      }
      files.push(...collectSourceFiles(absolutePath, extensions));
    } else if (stat.isFile() && extensions.some((extension) => entry.endsWith(extension))) {
      files.push(absolutePath);
    }
  }
  return files;
}

function collectInteractionSourceRefs(root) {
  const extensions = ['.java', '.kt', '.ts', '.tsx', '.js', '.jsx', '.mjs', '.yaml', '.yml', '.json'];
  const files = [
    ...collectSourceFiles(join(root, 'products'), extensions),
    ...collectSourceFiles(join(root, 'integration-tests'), extensions),
    ...collectSourceFiles(join(root, 'platform-kernel'), extensions),
  ];

  return files.map((filePath) => {
    const normalizedPath = filePath.replace(root, '').replace(/^[/\\]/, '').replaceAll('\\', '/');
    const source = readFileSync(filePath, 'utf8');
    return {
      path: normalizedPath,
      source,
      isTest: /(?:^|\/)(?:src\/test|__tests__|tests?|integration-tests)(?:\/|$)|\.test\./.test(normalizedPath),
      isHandler: source.includes('ProductInteractionHandler'),
    };
  });
}

function schemaRefsForContract(contract) {
  return [
    ['request', contract.requestSchemaRef, contract.requestSchemaSha256],
    ['response', contract.responseSchemaRef, contract.responseSchemaSha256],
    ['event', contract.eventSchemaRef, contract.eventSchemaSha256],
  ]
    .filter(([, ref]) => typeof ref === 'string' && ref.length > 0)
    .map(([kind, ref, sha256]) => ({ kind, ref, sha256 }));
}

function coverageSearchTokens(contract) {
  const schemaBasenames = schemaRefsForContract(contract)
    .map((schemaRef) => schemaRef.ref?.split('/').pop())
    .filter((token) => typeof token === 'string' && token.length > 0);
  const evidenceBasenames = Array.isArray(contract.evidence?.evidenceRefs)
    ? contract.evidence.evidenceRefs.map((ref) => ref.split('/').pop())
    : [];
  const contractTail = typeof contract.contractId === 'string'
    ? contract.contractId.replace(/^kernel:\/\/interactions\//, '')
    : undefined;
  const semanticAliases = [
    contractTail,
    contractTail?.replace(/\.v\d+$/, ''),
    contractTail?.replace(/\.v\d+$/, '').split('.').slice(1).join('-'),
    ...schemaBasenames.map((basename) => basename?.replace(/\.v\d+\.json$/, '')),
    ...evidenceBasenames.map((basename) => basename?.replace(/\.(?:ya?ml|json)$/, '')),
  ];

  return [
    contract.contractId,
    contract.topic,
    ...schemaBasenames,
    ...evidenceBasenames,
    ...semanticAliases,
  ].filter((token) => typeof token === 'string' && token.length > 0);
}

function matchingFiles(sourceRefs, tokens, predicate = () => true) {
  return sourceRefs
    .filter(predicate)
    .filter((sourceRef) => tokens.some((token) => sourceRef.source.includes(token)))
    .map((sourceRef) => sourceRef.path)
    .sort();
}

function buildInteractionCoverageMatrix(contractDeclarations, sourceRefs) {
  return contractDeclarations.flatMap(({ productId, bucket, contract, index }) => {
    const schemaRefs = schemaRefsForContract(contract);
    const tokens = coverageSearchTokens(contract);
    const handlerFiles = matchingFiles(
      sourceRefs,
      [contract.contractId],
      (sourceRef) => sourceRef.isHandler && !sourceRef.isTest,
    );
    const testFiles = matchingFiles(sourceRefs, tokens, (sourceRef) => sourceRef.isTest);
    const handlerRequired = bucket === 'provides' && contract.mode === 'request-response';
    const consumers = Array.isArray(contract.consumerProductIds) ? contract.consumerProductIds : [];

    return consumers.map((consumerProductId) => ({
      contractId: contract.contractId,
      schemaVersion: contract.schemaVersion,
      declaringProductId: productId,
      declarationKind: bucket,
      declarationIndex: index,
      providerProductId: contract.providerProductId,
      consumerProductId,
      mode: contract.mode,
      topic: contract.topic,
      schemaRefs,
      policy: {
        requiresAuth: contract.policy?.requiresAuth === true,
        requiresTenant: contract.policy?.requiresTenant === true,
        requiresConsent: contract.policy?.requiresConsent === true,
        piiClassification: contract.policy?.piiClassification ?? null,
        tenantScope: contract.policy?.tenantScope ?? null,
      },
      evidence: {
        required: contract.evidence?.required === true,
        manifestType: contract.evidence?.manifestType ?? null,
        evidenceRefs: Array.isArray(contract.evidence?.evidenceRefs)
          ? [...contract.evidence.evidenceRefs].sort()
          : [],
        retentionPolicyId: contract.evidence?.retentionPolicyId ?? null,
      },
      handler: {
        required: handlerRequired,
        status: handlerRequired ? (handlerFiles.length > 0 ? 'covered' : 'missing') : 'not-required',
        files: handlerFiles,
      },
      tests: {
        required: true,
        status: testFiles.length > 0 ? 'covered' : 'missing',
        files: testFiles,
      },
    }));
  }).sort((left, right) =>
    `${left.contractId}:${left.declaringProductId}:${left.consumerProductId}:${left.declarationKind}`
      .localeCompare(`${right.contractId}:${right.declaringProductId}:${right.consumerProductId}:${right.declarationKind}`),
  );
}

function summarizeCoverageMatrix(coverageMatrix) {
  const handlerRequired = coverageMatrix.filter((row) => row.handler.required).length;
  const handlerCovered = coverageMatrix.filter((row) => row.handler.required && row.handler.status === 'covered').length;
  const testsRequired = coverageMatrix.filter((row) => row.tests.required).length;
  const testsCovered = coverageMatrix.filter((row) => row.tests.required && row.tests.status === 'covered').length;
  return {
    interactionRows: coverageMatrix.length,
    handlerRequired,
    handlerCovered,
    testsRequired,
    testsCovered,
    passed: handlerRequired === handlerCovered && testsRequired === testsCovered,
  };
}

function validateBridgeHandlers(errors) {
  const handlerFiles = [
    ...collectJavaFiles(join(repoRoot, 'products', 'digital-marketing', 'dm-kernel-bridge', 'src', 'main', 'java')),
    ...collectJavaFiles(join(repoRoot, 'products', 'phr', 'src', 'main', 'java')),
  ];
  const handlerSources = handlerFiles.map((filePath) => readFileSync(filePath, 'utf8')).join('\n');
  const requiredHandlers = [
    'kernel://interactions/phr.consent-status.v1',
    'kernel://interactions/digital-marketing.notification-preference.v1',
  ];
  if (!handlerSources.includes('ProductInteractionHandler')) {
    errors.push('product bridge handler SPI is not implemented by any product bridge class');
  }
  for (const contractId of requiredHandlers) {
    if (!handlerSources.includes(contractId)) {
      errors.push(`product bridge handler missing for ${contractId}`);
    }
  }
}

function validateContract(errors, productIds, productId, bucket, contract, index, options = {}) {
  const path = `${productId}.interactions.${bucket}[${index}]`;
  if (typeof contract !== 'object' || contract === null) {
    errors.push(`${path} must be an object`);
    return;
  }

  for (const field of ['contractId', 'schemaVersion', 'providerProductId', 'mode']) {
    if (typeof contract[field] !== 'string' || contract[field].trim().length === 0) {
      errors.push(`${path}.${field} must be a non-empty string`);
    }
  }
  if (!VALID_MODES.has(contract.mode)) {
    errors.push(`${path}.mode must be one of ${[...VALID_MODES].join(', ')}`);
  }
  if (!productIds.has(contract.providerProductId)) {
    errors.push(`${path}.providerProductId references unknown product "${contract.providerProductId}"`);
  }
  if (!Array.isArray(contract.consumerProductIds) || contract.consumerProductIds.length === 0) {
    errors.push(`${path}.consumerProductIds must include at least one product`);
  } else {
    for (const consumerProductId of contract.consumerProductIds) {
      if (!productIds.has(consumerProductId)) {
        errors.push(`${path}.consumerProductIds references unknown product "${consumerProductId}"`);
      }
    }
  }
  if (contract.mode === 'request-response') {
    if (typeof contract.requestSchemaRef !== 'string' || contract.requestSchemaRef.length === 0) {
      errors.push(`${path}.requestSchemaRef is required for request-response`);
    } else {
      validateSchemaRef(errors, path, contract, 'requestSchemaRef', 'requestSchemaSha256', options);
    }
    if (typeof contract.responseSchemaRef !== 'string' || contract.responseSchemaRef.length === 0) {
      errors.push(`${path}.responseSchemaRef is required for request-response`);
    } else {
      validateSchemaRef(errors, path, contract, 'responseSchemaRef', 'responseSchemaSha256', options);
    }
  }
  if ((contract.mode === 'event-publish' || contract.mode === 'event-subscribe')) {
    if (typeof contract.topic !== 'string' || contract.topic.length === 0) {
      errors.push(`${path}.topic is required for event interactions`);
    }
    if (typeof contract.eventSchemaRef !== 'string' || contract.eventSchemaRef.length === 0) {
      errors.push(`${path}.eventSchemaRef is required for event interactions`);
    } else {
      validateSchemaRef(errors, path, contract, 'eventSchemaRef', 'eventSchemaSha256', options);
    }
  }
  if (contract.mode === 'provider-capability' && (typeof contract.capability !== 'string' || contract.capability.length === 0)) {
    errors.push(`${path}.capability is required for provider-capability`);
  }
  if (typeof contract.policy !== 'object' || contract.policy === null) {
    errors.push(`${path}.policy is required`);
  } else {
    if (typeof contract.policy.requiresAuth !== 'boolean') {
      errors.push(`${path}.policy.requiresAuth must be boolean`);
    }
    if (typeof contract.policy.requiresTenant !== 'boolean') {
      errors.push(`${path}.policy.requiresTenant must be boolean`);
    }
    if (contract.policy.requiresConsent === true && (typeof contract.policy.piiClassification !== 'string' || contract.policy.piiClassification.length === 0)) {
      errors.push(`${path}.policy.piiClassification is required when consent is required`);
    }
  }
  if (typeof contract.evidence !== 'object' || contract.evidence === null) {
    errors.push(`${path}.evidence is required`);
  } else {
    if (typeof contract.evidence.required !== 'boolean') {
      errors.push(`${path}.evidence.required must be boolean`);
    }
    if (contract.evidence.manifestType !== 'interaction-evidence') {
      errors.push(`${path}.evidence.manifestType must be interaction-evidence`);
    }
  }
}

function validateSchemaRef(errors, path, contract, refField, hashField, options) {
  if (options.validateSchemaRefs === false) {
    return;
  }
  const ref = contract[refField];
  const declaredHash = contract[hashField];
  if (typeof declaredHash !== 'string' || !/^[0-9a-f]{64}$/.test(declaredHash)) {
    errors.push(`${path}.${hashField} must be a lowercase SHA-256 digest for ${refField}`);
    return;
  }

  const readSchemaFile = options.readSchemaFile ?? ((relativePath) => {
    const absolutePath = join(repoRoot, relativePath);
    if (!existsSync(absolutePath)) {
      return undefined;
    }
    return readFileSync(absolutePath, 'utf8');
  });
  const content = readSchemaFile(ref);
  if (typeof content !== 'string') {
    errors.push(`${path}.${refField} references missing schema file ${ref}`);
    return;
  }

  try {
    JSON.parse(content);
  } catch (error) {
    errors.push(`${path}.${refField} references invalid JSON schema ${ref}: ${error.message}`);
    return;
  }

  const actualHash = sha256(content);
  options.schemaDigests?.set(ref, actualHash);
  if (actualHash !== declaredHash) {
    errors.push(`${path}.${hashField} is ${declaredHash}, but ${refField} hashes to ${actualHash}`);
  }
}

function validateContractSchemaParity(errors, providerProductId, providerContract, consumerProductId, consumerContract) {
  for (const field of ['schemaVersion', 'requestSchemaSha256', 'responseSchemaSha256', 'eventSchemaSha256']) {
    const providerValue = providerContract[field];
    const consumerValue = consumerContract[field];
    if (providerValue === undefined || consumerValue === undefined) {
      continue;
    }
    if (providerValue !== consumerValue) {
      errors.push(`${consumerProductId}.interactions.consumes ${consumerContract.contractId} ${field} (${consumerValue}) does not match provider ${providerProductId} (${providerValue})`);
    }
  }
}

function sha256(content) {
  return createHash('sha256').update(content, 'utf8').digest('hex');
}

function main() {
  const result = checkProductInteractionContracts(registry);
  if (result.errors.length > 0) {
    console.error('Product interaction contract validation failed:');
    for (const error of result.errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  mkdirSync(join(repoRoot, '.kernel', 'evidence'), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify({
    generatedAt: stableGeneratedAt,
    coverageSummary: result.coverageSummary,
    coverageMatrix: result.coverageMatrix,
  }, null, 2)}\n`, 'utf8');
  console.log(`Product interaction contract validation passed for ${result.consumedContractCount} consumed contracts.`);
  console.log(`Generated interaction coverage matrix: ${evidencePath.replace(`${repoRoot}\\`, '').replaceAll('\\', '/')}`);
}

if (process.argv[1] && resolve(process.argv[1]) === __filename) {
  main();
}
