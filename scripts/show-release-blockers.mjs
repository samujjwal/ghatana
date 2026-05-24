#!/usr/bin/env node
/**
 * Show Exact Current Release Blockers
 * Displays the current state of all release gates and blockers
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const PRODUCT_REGISTRY = resolve('config/canonical-product-registry.json');
const EVIDENCE_DIR = resolve('.kernel/evidence');

function getProductRegistry() {
  if (!existsSync(PRODUCT_REGISTRY)) {
    console.error('❌ Product registry not found:', PRODUCT_REGISTRY);
    process.exit(1);
  }
  return JSON.parse(readFileSync(PRODUCT_REGISTRY, 'utf-8'));
}

function getBlockersForProduct(product) {
  const blockers = [];
  
  if (product.lifecycleReadiness) {
    const readiness = product.lifecycleReadiness;
    
    // Check if lifecycle execution is blocked
    if (product.lifecycleExecutionAllowed === false) {
      blockers.push({
        type: 'lifecycle-execution',
        severity: 'blocking',
        reason: 'Lifecycle execution not allowed',
        reasonCodes: readiness.reasonCodes || [],
        requiredGates: readiness.requiredGates || []
      });
    }
    
    // Check if status is blocked
    if (readiness.status === 'blocked') {
      blockers.push({
        type: 'readiness',
        severity: 'blocking',
        reason: readiness.reasonCodes?.join(', ') || 'Readiness blocked',
        requiredGates: readiness.requiredGates || []
      });
    }
    
    // Check blockerGateAdapterMatrix
    if (readiness.blockerGateAdapterMatrix) {
      readiness.blockerGateAdapterMatrix.blockers.forEach(blocker => {
        blockers.push({
          type: 'gate-blocker',
          severity: 'blocking',
          reason: blocker,
          gates: readiness.blockerGateAdapterMatrix.gates.map(g => g.gateId)
        });
      });
    }
  }
  
  return blockers;
}

function main() {
  console.log('Current Release Blockers Report');
  console.log('================================\n');
  
  const registry = getProductRegistry();
  const products = registry.registry;
  
  let totalBlockers = 0;
  let totalProducts = 0;
  let blockedProducts = 0;
  
  for (const [productId, product] of Object.entries(products)) {
    totalProducts++;
    const blockers = getBlockersForProduct(product);
    
    if (blockers.length > 0) {
      blockedProducts++;
      totalBlockers += blockers.length;
      
      console.log(`📦 ${product.name} (${productId})`);
      console.log(`   Status: ${product.lifecycleReadiness?.status || 'unknown'}`);
      console.log(`   Lifecycle Execution: ${product.lifecycleExecutionAllowed ? 'allowed' : 'blocked'}`);
      console.log(`   Blockers (${blockers.length}):`);
      
      blockers.forEach((blocker, idx) => {
        console.log(`     ${idx + 1}. [${blocker.severity.toUpperCase()}] ${blocker.type}`);
        console.log(`        Reason: ${blocker.reason}`);
        if (blocker.requiredGates && blocker.requiredGates.length > 0) {
          console.log(`        Required Gates: ${blocker.requiredGates.join(', ')}`);
        }
        if (blocker.gates && blocker.gates.length > 0) {
          console.log(`        Gates: ${blocker.gates.join(', ')}`);
        }
      });
      console.log();
    }
  }
  
  console.log('Summary:');
  console.log(`  Total Products: ${totalProducts}`);
  console.log(`  Blocked Products: ${blockedProducts}`);
  console.log(`  Total Blockers: ${totalBlockers}`);
  
  if (totalBlockers === 0) {
    console.log('\n✅ No release blockers detected');
  } else {
    console.log(`\n⚠️  ${totalBlockers} release blocker(s) detected`);
  }
}

main();
