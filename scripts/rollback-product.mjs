#!/usr/bin/env node

import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { readFileSync, writeFileSync } from 'node:fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Import from source for development
const { ProductRollbackPlan } = await import(join(__dirname, '../platform/typescript/kernel-release/src/ProductRollbackPlan.js'));

async function main() {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.log('Usage: rollback-product.mjs <command> [options]');
    console.log('');
    console.log('Commands:');
    console.log('  plan <productId> <version> <reason> - Create rollback plan');
    console.log('  execute <planId> - Execute rollback plan');
    console.log('  approve <planId> - Approve rollback plan');
    console.log('  cancel <planId> - Cancel rollback plan');
    console.log('  status <planId> - Get rollback plan status');
    return;
  }

  const command = args[0];

  try {
    switch (command) {
      case 'plan':
        await createRollbackPlan(args[1], args[2], args[3]);
        break;
      case 'execute':
        await executeRollback(args[1]);
        break;
      case 'approve':
        await approveRollback(args[1]);
        break;
      case 'cancel':
        await cancelRollback(args[1]);
        break;
      case 'status':
        await getRollbackStatus(args[1]);
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

async function createRollbackPlan(productId, version, reason) {
  if (!productId || !version || !reason) {
    console.error('Error: productId, version, and reason are required');
    process.exit(1);
  }

  const rollback = new ProductRollbackPlan();
  const plan = await rollback.create(productId, version, reason);

  const outputPath = join(__dirname, '../config/rollbacks', `${plan.planId}.json`);
  writeFileSync(outputPath, JSON.stringify(plan, null, 2));

  console.log(`Rollback plan created: ${plan.planId}`);
  console.log(`Product: ${productId}`);
  console.log(`Rollback to version: ${version}`);
  console.log(`Reason: ${reason}`);
  console.log(`Plan saved to: ${outputPath}`);
}

async function executeRollback(planId) {
  if (!planId) {
    console.error('Error: planId is required');
    process.exit(1);
  }

  const rollback = new ProductRollbackPlan();
  await rollback.execute(planId);

  console.log(`Rollback executed: ${planId}`);
}

async function approveRollback(planId) {
  if (!planId) {
    console.error('Error: planId is required');
    process.exit(1);
  }

  const rollback = new ProductRollbackPlan();
  await rollback.approve(planId, 'cli-user');

  console.log(`Rollback approved: ${planId}`);
}

async function cancelRollback(planId) {
  if (!planId) {
    console.error('Error: planId is required');
    process.exit(1);
  }

  const rollback = new ProductRollbackPlan();
  await rollback.cancel(planId);

  console.log(`Rollback cancelled: ${planId}`);
}

async function getRollbackStatus(planId) {
  if (!planId) {
    console.error('Error: planId is required');
    process.exit(1);
  }

  const rollback = new ProductRollbackPlan();
  const status = await rollback.getStatus(planId);

  console.log(`Rollback Status: ${planId}`);
  console.log(`Status: ${status.status}`);
  console.log(`Target Version: ${status.targetVersion}`);
  console.log(`Reason: ${status.reason}`);
  console.log(`Created: ${status.createdAt}`);
}

main();
