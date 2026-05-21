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
  const registryDocument = options.registryDocument ?? readJson(options.registryPath ?? registryPath);
  const registry = registryDocument.registry ?? registryDocument;
  const errors = [];
  let requiredEvidenceRefs = 0;
  let consumedContracts = 0;
  let eventContracts = 0;

  for (const [productId, registryProduct] of Object.entries(registry)) {
    const config = options.loadKernelProduct?.(productId, registryProduct) ?? loadKernelProduct(root, productId, registryProduct);
    if (config?.interactions === undefined) {
      continue;
    }
    for (const bucket of ['publishes', 'consumes', 'provides']) {
      for (const contract of asArray(config.interactions[bucket])) {
        if (contract?.mode === 'event-publish' || contract?.mode === 'event-subscribe') {
          eventContracts += 1;
          if (typeof contract.topic !== 'string' || contract.topic.trim().length === 0) {
            errors.push(`${productId}.interactions.${bucket}.${contract.contractId} event contract must declare topic`);
          }
        }
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

  const crossServiceBuildFile = path.join(
    root,
    'integration-tests/cross-service-workflow/build.gradle.kts',
  );
  if (!existsSync(crossServiceBuildFile)) {
    errors.push('cross-service-workflow build file is missing');
  } else {
    const buildSource = readFileSync(crossServiceBuildFile, 'utf8');
    if (!buildSource.includes('val productInteractionTest by sourceSets.creating')) {
      errors.push('cross-service-workflow is missing productInteractionTest source set');
    }
    if (!buildSource.includes('val runtimeInteractionTest by sourceSets.creating')) {
      errors.push('cross-service-workflow is missing runtimeInteractionTest source set');
    }
    if (!buildSource.includes('tasks.register<Test>("productInteractionTest")')) {
      errors.push('cross-service-workflow is missing productInteractionTest task registration');
    }
    if (!buildSource.includes('tasks.register<Test>("runtimeInteractionTest")')) {
      errors.push('cross-service-workflow is missing runtimeInteractionTest task registration');
    }
  }

  const runtimeTestPath = path.join(
    root,
    'integration-tests/cross-service-workflow/src/runtimeInteractionTest/java/com/ghatana/integration/crossservice/CrossProductInteractionRuntimeTest.java',
  );
  if (!existsSync(runtimeTestPath)) {
    errors.push('runtime interaction executable test is missing: CrossProductInteractionRuntimeTest');
  } else {
    const runtimeTestSource = readFileSync(runtimeTestPath, 'utf8');
    const requiredRuntimeReasonCodes = [
      'product_interaction.tenant_required',
      'product_interaction.contract_version_unsupported',
      'product_interaction.workspace_required',
      'product_interaction.policy_denied',
      'product_interaction.handler_unavailable',
      'product_interaction.timeout',
    ];
    for (const reasonCode of requiredRuntimeReasonCodes) {
      if (!runtimeTestSource.includes(reasonCode)) {
        errors.push(`runtime interaction test missing required negative assertion: ${reasonCode}`);
      }
    }
  }

  const productInteractionTestPath = path.join(
    root,
    'integration-tests/cross-service-workflow/src/productInteractionTest/java/com/ghatana/integration/crossservice/PhrDmosProductInteractionContractTest.java',
  );
  if (!existsSync(productInteractionTestPath)) {
    errors.push('product interaction executable test is missing: PhrDmosProductInteractionContractTest');
  } else {
    const productTestSource = readFileSync(productInteractionTestPath, 'utf8');
    const requiredProductAssertions = [
      'product_interaction.tenant_required',
      'product_interaction.workspace_required',
      'product_interaction.contract_version_unsupported',
      'product_interaction.policy_denied',
      'product_interaction.timeout',
      'FileProductInteractionEvidenceWriter',
      'correlationId',
      'workspaceId',
      'tenantId',
    ];
    for (const assertionToken of requiredProductAssertions) {
      if (!productTestSource.includes(assertionToken)) {
        errors.push(`product interaction test missing required runtime proof token: ${assertionToken}`);
      }
    }
  }

  if (options.validateEventBroker !== false) {
    const eventBrokerFiles = [
      'ProductInteractionEventBroker.java',
      'ProductInteractionEventEnvelope.java',
      'ProductInteractionEventHandler.java',
      'ProductInteractionEventOutcome.java',
      'ProductInteractionEventEvidenceWriter.java',
      'FileProductInteractionEventEvidenceWriter.java',
      'ProductInteractionEventPolicyEvaluator.java',
      'ProductInteractionEventBrokerMetrics.java',
    ];
    for (const fileName of eventBrokerFiles) {
      const filePath = path.join(root, 'platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/interaction', fileName);
      if (!existsSync(filePath)) {
        errors.push(`product interaction event broker runtime file is missing: ${fileName}`);
      }
    }
    const eventBrokerTestPath = path.join(
      root,
      'platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/interaction/ProductInteractionEventBrokerTest.java',
    );
    if (!existsSync(eventBrokerTestPath)) {
      errors.push('product interaction event broker test is missing: ProductInteractionEventBrokerTest');
    }
  }

  return { errors, consumedContracts, requiredEvidenceRefs, eventContracts };
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
    `Interaction runtime truth check passed for ${result.consumedContracts} consumed contract(s), ${result.eventContracts} event contract(s), and ${result.requiredEvidenceRefs} evidence ref(s).`,
  );
}
