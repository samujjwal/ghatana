#!/usr/bin/env node

/**
 * Generate Route Entitlement Contracts from Shared Schema
 * 
 * Generates TypeScript type definitions from the shared product-route-entitlement schema.
 * This ensures frontend packages use the canonical contract instead of local definitions.
 * 
 * Usage: node scripts/generate-route-entitlement-contracts.mjs [--check]
 */

import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// Parse command line arguments
const args = process.argv.slice(2);
const checkMode = args.includes('--check');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function generateTypeScriptContract(schema) {
  const types = [];
  
  // Generate RouteEntitlement interface
  types.push(`export interface RouteEntitlement {`);
  types.push(`  path: string;`);
  types.push(`  label: string;`);
  types.push(`  minimumRole: string;`);
  types.push(`  personas: string[];`);
  types.push(`  tiers: string[];`);
  types.push(`  actions: string[];`);
  types.push(`  cards: string[];`);
  types.push(`  capabilityKey?: string;`);
  types.push(`}`);
  
  // Generate ActionEntitlement interface
  types.push(``);
  types.push(`export interface ActionEntitlement {`);
  types.push(`  id: string;`);
  types.push(`  label: string;`);
  types.push(`  routePath: string;`);
  types.push(`}`);
  
  // Generate CardEntitlement interface
  types.push(``);
  types.push(`export interface CardEntitlement {`);
  types.push(`  id: string;`);
  types.push(`  title: string;`);
  types.push(`  routePath: string;`);
  types.push(`  surface: string;`);
  types.push(`}`);
  
  // Generate ProductRouteEntitlement interface
  types.push(``);
  types.push(`export interface ProductRouteEntitlement {`);
  types.push(`  product: string;`);
  types.push(`  principalId: string;`);
  types.push(`  tenantId: string;`);
  types.push(`  role: string;`);
  types.push(`  persona?: string;`);
  types.push(`  tier?: string;`);
  types.push(`  correlationId?: string;`);
  types.push(`  routes: RouteEntitlement[];`);
  types.push(`  actions: ActionEntitlement[];`);
  types.push(`  cards: CardEntitlement[];`);
  types.push(`}`);
  
  return types.join('\n');
}

function main() {
  console.log('=== Route Entitlement Contract Generator ===\n');
  
  try {
    const schemaPath = path.join(repoRoot, 'platform/contracts/product-route-entitlement.schema.json');
    if (!existsSync(schemaPath)) {
      throw new Error('Product route entitlement schema not found');
    }
    
    const schema = readJson('platform/contracts/product-route-entitlement.schema.json');
    const contract = generateTypeScriptContract(schema);
    
    const outputPath = path.join(repoRoot, 'platform/typescript/product-shell/src/contracts/generated-route-entitlement.ts');
    
    if (checkMode) {
      if (!existsSync(outputPath)) {
        console.error('✗ No existing route entitlement contract found');
        process.exit(1);
      }
      
      const existingContract = readFileSync(outputPath, 'utf8');
      if (existingContract !== contract) {
        console.error('✗ Route entitlement contract is out of date');
        console.error('Run: node scripts/generate-route-entitlement-contracts.mjs');
        process.exit(1);
      }
      
      console.log('✓ Route entitlement contract is up to date');
    } else {
      writeFileSync(outputPath, contract + '\n');
      console.log(`Generated route entitlement contract at ${outputPath}`);
    }
    
    process.exit(0);
  } catch (error) {
    console.error('✗ Route entitlement contract generation failed:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
