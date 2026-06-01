#!/usr/bin/env node

/**
 * KER-T03: Kernel policy registry validation for every policyId in PHR route contract.
 * 
 * This script validates that every policyId referenced in the PHR route contract
 * exists in the canonical policy registry. This ensures parity between route contracts
 * and policy evaluation logic.
 */

import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const routeContractPath = join(__dirname, '..', 'products', 'phr', 'config', 'phr-route-contract.json');
const policyRegistryPath = join(__dirname, '..', 'products', 'phr', 'config', 'policy-registry.json');
const policyEvaluatorPath = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'security', 'PhrPolicyEvaluator.java');

const routeContract = JSON.parse(readFileSync(routeContractPath, 'utf-8'));
const policyRegistry = JSON.parse(readFileSync(policyRegistryPath, 'utf-8'));
const policyEvaluatorSource = readFileSync(policyEvaluatorPath, 'utf-8');

console.log('Validating PHR route contract policyIds against policy registry...\n');

const registeredPolicies = new Set(Object.keys(policyRegistry.policies));
const unknownPolicies = new Set();
const routesWithoutPolicy = [];
const invalidPolicies = [];

for (const route of routeContract.routes) {
  const routeId = route.path || 'UNKNOWN';
  
  if (!route.policyId) {
    if (route.stability === 'stable') {
      routesWithoutPolicy.push(routeId);
    }
    continue;
  }
  
  if (!registeredPolicies.has(route.policyId)) {
    unknownPolicies.add(route.policyId);
  }
}

// Check for registered policies not used in route contract
const usedPolicies = new Set(
  routeContract.routes
    .filter(r => r.policyId)
    .map(r => r.policyId)
);
const unusedPolicies = [...registeredPolicies].filter(p => !usedPolicies.has(p));

const allowedCategories = new Set(['ui', 'system', 'phi', 'emergency', 'admin', 'hidden']);
for (const [policyId, policy] of Object.entries(policyRegistry.policies)) {
  if (!allowedCategories.has(policy.category)) {
    invalidPolicies.push(`${policyId}: unsupported category ${policy.category}`);
  }
  if (policy.evaluator !== 'PhrPolicyEvaluator.evaluateByPolicyId') {
    invalidPolicies.push(`${policyId}: evaluator must be PhrPolicyEvaluator.evaluateByPolicyId`);
  }
  if (policy.category === 'admin') {
    if (!policy.allowReasonCode || !policy.allowReasonMessage) {
      invalidPolicies.push(`${policyId}: admin policy requires allowReasonCode and allowReasonMessage`);
    }
  } else if (policy.allowReasonCode || policy.allowReasonMessage) {
    invalidPolicies.push(`${policyId}: non-admin policy must not define admin allow metadata`);
  }
}

const evaluatorUsesKernelRegistry =
  policyEvaluatorSource.includes('KernelPolicyPlugin<') &&
  policyEvaluatorSource.includes('createPolicyRegistry()') &&
  policyEvaluatorSource.includes('policy-registry.json') &&
  policyEvaluatorSource.includes('parseRegisteredPolicy(');

let errors = 0;
let warnings = 0;

if (unknownPolicies.size > 0) {
  console.error('❌ PolicyIds in route contract not registered:');
  for (const policyId of unknownPolicies) {
    console.error(`   - ${policyId}`);
    errors++;
  }
}

if (routesWithoutPolicy.length > 0) {
  console.warn('⚠️  Stable routes without policyId:');
  for (const routeId of routesWithoutPolicy) {
    console.warn(`   - ${routeId}`);
    warnings++;
  }
}

if (invalidPolicies.length > 0) {
  console.error('❌ Invalid policy registry entries:');
  for (const violation of invalidPolicies) {
    console.error(`   - ${violation}`);
    errors++;
  }
}

if (!evaluatorUsesKernelRegistry) {
  console.error('❌ PhrPolicyEvaluator is not wired to the Kernel policy registry dispatch path');
  errors++;
}

if (unusedPolicies.length > 0) {
  console.warn('⚠️  Registered policies not used in route contract:');
  for (const policyId of unusedPolicies) {
    console.warn(`   - ${policyId}`);
    warnings++;
  }
}

console.log(`\n${routeContract.routes.length} routes validated`);
console.log(`${registeredPolicies.size} policies registered`);
console.log(`${usedPolicies.size} policies used in route contract`);
console.log(`Errors: ${errors}`);
console.log(`Warnings: ${warnings}`);

if (errors > 0) {
  process.exit(1);
}

console.log('✅ Policy registry validation passed');
