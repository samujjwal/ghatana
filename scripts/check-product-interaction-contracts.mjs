#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __filename = fileURLToPath(import.meta.url);
const repoRoot = resolve(new URL('..', import.meta.url).pathname);
const registryPath = join(repoRoot, 'config', 'canonical-product-registry.json');
const registryDocument = JSON.parse(readFileSync(registryPath, 'utf8'));
const registry = registryDocument.registry ?? registryDocument;

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
      contracts.forEach((contract, index) => validateContract(errors, productIds, productId, bucket, contract, index));
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
  }

  if (options.validateBridgeHandlers !== false) {
    validateBridgeHandlers(errors);
  }

  return { errors, consumedContractCount: consumedContracts.length };
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

function validateContract(errors, productIds, productId, bucket, contract, index) {
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
    }
    if (typeof contract.responseSchemaRef !== 'string' || contract.responseSchemaRef.length === 0) {
      errors.push(`${path}.responseSchemaRef is required for request-response`);
    }
  }
  if ((contract.mode === 'event-publish' || contract.mode === 'event-subscribe')) {
    if (typeof contract.topic !== 'string' || contract.topic.length === 0) {
      errors.push(`${path}.topic is required for event interactions`);
    }
    if (typeof contract.eventSchemaRef !== 'string' || contract.eventSchemaRef.length === 0) {
      errors.push(`${path}.eventSchemaRef is required for event interactions`);
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

function main() {
  const result = checkProductInteractionContracts(registry);
  if (result.errors.length > 0) {
    console.error('Product interaction contract validation failed:');
    for (const error of result.errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log(`Product interaction contract validation passed for ${result.consumedContractCount} consumed contracts.`);
}

if (process.argv[1] && resolve(process.argv[1]) === __filename) {
  main();
}
