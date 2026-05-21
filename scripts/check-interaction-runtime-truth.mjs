#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registryPath = path.join(repoRoot, 'config', 'canonical-product-registry.json');
const interactionPhases = ['validate', 'test', 'deploy', 'verify'];

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function loadKernelProduct(root, productId, registryProduct) {
  const lifecycleConfigPath = registryProduct?.lifecycleConfigPath;
  if (typeof lifecycleConfigPath !== 'string' || !lifecycleConfigPath.endsWith('kernel-product.yaml')) {
    return undefined;
  }
  const absolutePath = path.join(root, lifecycleConfigPath);
  if (!existsSync(absolutePath)) {
    return undefined;
  }
  return YAML.parse(readFileSync(absolutePath, 'utf8'));
}

function explainPlan(root, productId, phase) {
  const child = spawnSync(
    process.execPath,
    ['scripts/kernel-product.mjs', 'product', productId, 'explain', phase, '--json'],
    { cwd: root, encoding: 'utf8' },
  );
  if (child.status !== 0) {
    throw new Error(`kernel explain failed for ${productId}/${phase}: ${child.stderr || child.stdout}`);
  }
  const output = JSON.parse(child.stdout);
  return output.plan ?? output;
}

export function checkInteractionRuntimeTruth(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const registryDocument = readJson(options.registryPath ?? registryPath);
  const registry = registryDocument.registry ?? registryDocument;
  const errors = [];
  let requiredEvidenceRefs = 0;
  let consumedContracts = 0;

  for (const [productId, registryProduct] of Object.entries(registry)) {
    const config = loadKernelProduct(root, productId, registryProduct);
    if (config?.interactions === undefined) {
      continue;
    }
    for (const bucket of ['publishes', 'consumes', 'provides']) {
      for (const contract of asArray(config.interactions[bucket])) {
        if (contract?.evidence?.required === true) {
          const refs = asArray(contract.evidence.evidenceRefs);
          if (refs.length === 0) {
            errors.push(`${productId}.interactions.${bucket}.${contract.contractId} requires evidence but declares no evidenceRefs`);
          }
          for (const ref of refs) {
            requiredEvidenceRefs += 1;
            if (typeof ref !== 'string' || !existsSync(path.join(root, ref))) {
              errors.push(`${productId}.interactions.${bucket}.${contract.contractId} evidence ref missing: ${ref}`);
            }
          }
        }
      }
    }

    const consumed = asArray(config.interactions.consumes);
    consumedContracts += consumed.length;
    if (consumed.length > 0 && options.skipPlanner !== true) {
      for (const phase of interactionPhases) {
        const plan = explainPlan(root, productId, phase);
        const preflights = asArray(plan.interactionPreflights);
        for (const contract of consumed) {
          const allowedPhases = asArray(contract?.policy?.allowedLifecyclePhases);
          if (allowedPhases.length > 0 && !allowedPhases.includes(phase)) {
            continue;
          }
          if (!preflights.some((preflight) => preflight.contractId === contract.contractId)) {
            errors.push(`${productId}/${phase} plan missing interaction preflight for ${contract.contractId}`);
          }
        }
      }
    }
  }

  const dataCloudProviderPath = path.join(
    root,
    'products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudProductInteractionEvidenceProvider.java',
  );
  if (!existsSync(dataCloudProviderPath)) {
    errors.push('Data Cloud product interaction evidence provider is missing');
  }

  return { errors, consumedContracts, requiredEvidenceRefs };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = checkInteractionRuntimeTruth();
  if (result.errors.length > 0) {
    console.error('Interaction runtime truth check failed:');
    for (const error of result.errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }
  console.log(
    `Interaction runtime truth check passed for ${result.consumedContracts} consumed contract(s) and ${result.requiredEvidenceRefs} evidence ref(s).`,
  );
}
