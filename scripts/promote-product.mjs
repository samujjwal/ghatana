#!/usr/bin/env node

import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { readFileSync, writeFileSync } from 'node:fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Import from source for development
const { ProductPromotionPlan } = await import(join(__dirname, '../platform/typescript/kernel-release/src/ProductPromotionPlan.js'));

async function main() {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.log('Usage: promote-product.mjs <command> [options]');
    console.log('');
    console.log('Commands:');
    console.log('  plan <productId> <sourceEnv> <targetEnv> - Create promotion plan');
    console.log('  execute <planId> - Execute promotion plan');
    console.log('  approve <planId> - Approve promotion plan');
    console.log('  reject <planId> <reason> - Reject promotion plan');
    console.log('  status <planId> - Get promotion plan status');
    return;
  }

  const command = args[0];

  try {
    switch (command) {
      case 'plan':
        await createPromotionPlan(args[1], args[2], args[3]);
        break;
      case 'execute':
        await executePromotion(args[1]);
        break;
      case 'approve':
        await approvePromotion(args[1]);
        break;
      case 'reject':
        await rejectPromotion(args[1], args[2]);
        break;
      case 'status':
        await getPromotionStatus(args[1]);
        break;
      default:
        console.error(`Unknown command: ${command}`);
        process.exit(1);
    }
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

async function createPromotionPlan(productId, sourceEnv, targetEnv) {
  if (!productId || !sourceEnv || !targetEnv) {
    console.error('Error: productId, sourceEnv, and targetEnv are required');
    process.exit(1);
  }

  const promotion = new ProductPromotionPlan();
  const plan = await promotion.create(productId, sourceEnv, targetEnv);

  const outputPath = join(__dirname, '../config/promotions', `${plan.planId}.json`);
  writeFileSync(outputPath, JSON.stringify(plan, null, 2));

  console.log(`Promotion plan created: ${plan.planId}`);
  console.log(`Product: ${productId}`);
  console.log(`Source: ${sourceEnv} → Target: ${targetEnv}`);
  console.log(`Plan saved to: ${outputPath}`);
}

async function executePromotion(planId) {
  if (!planId) {
    console.error('Error: planId is required');
    process.exit(1);
  }

  const promotion = new ProductPromotionPlan();
  await promotion.execute(planId);

  console.log(`Promotion executed: ${planId}`);
}

async function approvePromotion(planId) {
  if (!planId) {
    console.error('Error: planId is required');
    process.exit(1);
  }

  const promotion = new ProductPromotionPlan();
  await promotion.approve(planId, 'cli-user');

  console.log(`Promotion approved: ${planId}`);
}

async function rejectPromotion(planId, reason) {
  if (!planId || !reason) {
    console.error('Error: planId and reason are required');
    process.exit(1);
  }

  const promotion = new ProductPromotionPlan();
  await promotion.reject(planId, reason);

  console.log(`Promotion rejected: ${planId}`);
  console.log(`Reason: ${reason}`);
}

async function getPromotionStatus(planId) {
  if (!planId) {
    console.error('Error: planId is required');
    process.exit(1);
  }

  const promotion = new ProductPromotionPlan();
  const status = await promotion.getStatus(planId);

  console.log(`Promotion Status: ${planId}`);
  console.log(`Status: ${status.status}`);
  console.log(`Source: ${status.sourceEnvironment}`);
  console.log(`Target: ${status.targetEnvironment}`);
  console.log(`Created: ${status.createdAt}`);
}

main();
