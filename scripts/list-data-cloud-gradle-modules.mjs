#!/usr/bin/env node

/**
 * Phase 1: Script to list all active Data-Cloud Gradle modules from generated settings.
 *
 * This script parses config/generated/settings-gradle-includes.kts to extract
 * all Data-Cloud modules and classify them by category (release-blocking, advisory, etc.).
 *
 * Usage:
 *   node scripts/list-data-cloud-gradle-modules.mjs                    # List all modules
 *   node scripts/list-data-cloud-gradle-modules.mjs --check-tasks        # Output Gradle tasks
 *   node scripts/list-data-cloud-gradle-modules.mjs --release-blocking   # Only release-blocking
 *   node scripts/list-data-cloud-gradle-modules.mjs --validate          # Validate module classification
 *
 * @doc.type script
 * @doc.purpose List and classify all active Data-Cloud Gradle modules
 * @doc.layer repo
 * @doc.pattern Module enumeration
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '..');

// Phase 1: Module classification configuration
const MODULE_CLASSIFICATION = {
  // Release-blocking modules (must compile/test in CI)
  releaseBlocking: [
    ':products:data-cloud:planes:shared-spi',
    ':products:data-cloud:planes:data:entity',
    ':products:data-cloud:planes:event:core',
    ':products:data-cloud:planes:event:store',
    ':products:data-cloud:planes:operations:config',
    ':products:data-cloud:planes:intelligence:analytics',
    ':products:data-cloud:planes:intelligence:feature-ingest',
    ':products:data-cloud:planes:governance:core',
    ':products:data-cloud:delivery:runtime-composition',
    ':products:data-cloud:delivery:api',
    ':products:data-cloud:delivery:launcher',
    ':products:data-cloud:delivery:sdk',
    ':products:data-cloud:contracts',
    ':products:data-cloud:extensions:plugins',
    ':products:data-cloud:extensions:agent-registry',
    ':products:data-cloud:extensions:agent-catalog',
    ':products:data-cloud:delivery:api-contract-tests',
    ':products:data-cloud:integration-tests',
    ':products:data-cloud:extensions:kernel-bridge',
  ],
  
  // Advisory modules (checked in CI but not blocking)
  advisory: [],
  
  // Action Plane modules (AEP co-located - special handling)
  actionPlane: [
    ':products:data-cloud:planes:action',
    ':products:data-cloud:planes:action:operator-contracts',
    ':products:data-cloud:planes:action:central-runtime',
    ':products:data-cloud:planes:action:engine',
    ':products:data-cloud:planes:action:registry',
    ':products:data-cloud:planes:action:analytics',
    ':products:data-cloud:planes:action:security',
    ':products:data-cloud:planes:action:event-bridge',
    ':products:data-cloud:planes:action:agent-runtime',
    ':products:data-cloud:planes:action:api',
    ':products:data-cloud:planes:action:scaling',
    ':products:data-cloud:planes:action:observability',
    ':products:data-cloud:planes:action:orchestrator',
    ':products:data-cloud:planes:action:server',
    ':products:data-cloud:planes:action:identity',
    ':products:data-cloud:planes:action:compliance',
    ':products:data-cloud:planes:action:kernel-bridge',
  ],
  
  // Experimental modules (not yet production-ready)
  experimental: [],
  
  // Deprecated modules (to be removed)
  deprecated: [],
  
  // Excluded modules with reasons
  excluded: {},
};

/**
 * Parse settings-gradle-includes.kts to extract Data-Cloud modules
 */
function parseSettingsIncludes() {
  const settingsPath = path.join(REPO_ROOT, 'config/generated/settings-gradle-includes.kts');
  
  if (!fs.existsSync(settingsPath)) {
    console.error('Error: config/generated/settings-gradle-includes.kts not found');
    console.error('Run: node scripts/generate-product-registry-artifacts.mjs');
    process.exit(1);
  }
  
  const content = fs.readFileSync(settingsPath, 'utf8');
  const lines = content.split('\n');
  
  const dataCloudModules = [];
  let inDataCloudSection = false;
  
  for (const line of lines) {
    // Detect Data-Cloud section
    if (line.includes('// platform-provider: Data Cloud (data-cloud)')) {
      inDataCloudSection = true;
      continue;
    }
    
    // Exit Data-Cloud section when we hit another product
    if (inDataCloudSection && line.includes('// platform-provider:') || 
        inDataCloudSection && line.includes('// business-product:') ||
        inDataCloudSection && line.includes('// shared-service:')) {
      inDataCloudSection = false;
      continue;
    }
    
    // Extract include statements
    if (inDataCloudSection && line.trim().startsWith('include(')) {
      const match = line.match(/include\("([^"]+)"\)/);
      if (match) {
        dataCloudModules.push(match[1]);
      }
    }
  }
  
  return dataCloudModules;
}

/**
 * Classify a module based on the classification config
 */
function classifyModule(modulePath) {
  // Check release-blocking
  if (MODULE_CLASSIFICATION.releaseBlocking.includes(modulePath)) {
    return { category: 'release-blocking', reason: 'Core production module' };
  }
  
  // Check advisory
  if (MODULE_CLASSIFICATION.advisory.includes(modulePath)) {
    return { category: 'advisory', reason: 'Non-production module' };
  }
  
  // Check Action Plane
  if (MODULE_CLASSIFICATION.actionPlane.includes(modulePath)) {
    return { category: 'action-plane', reason: 'AEP co-located modules (migration compatibility)' };
  }
  
  // Check experimental
  if (MODULE_CLASSIFICATION.experimental.includes(modulePath)) {
    return { category: 'experimental', reason: 'Experimental feature' };
  }
  
  // Check deprecated
  if (MODULE_CLASSIFICATION.deprecated.includes(modulePath)) {
    return { category: 'deprecated', reason: 'Deprecated module' };
  }
  
  // Check excluded
  if (MODULE_CLASSIFICATION.excluded[modulePath]) {
    return { category: 'excluded', reason: MODULE_CLASSIFICATION.excluded[modulePath] };
  }
  
  // Default: unclassified (should be classified)
  return { category: 'unclassified', reason: 'Needs classification in MODULE_CLASSIFICATION' };
}

/**
 * Validate that all modules in generated settings are classified
 */
function validateClassification(allModules) {
  const unclassified = [];
  
  for (const module of allModules) {
    const classification = classifyModule(module);
    if (classification.category === 'unclassified') {
      unclassified.push(module);
    }
  }
  
  return unclassified;
}

/**
 * Generate Gradle task names for a list of modules
 */
function generateGradleTasks(modules, taskSuffix = 'compileJava') {
  return modules.map(m => `:${m}:${taskSuffix}`);
}

/**
 * Main execution
 */
function main() {
  const args = process.argv.slice(2);
  const checkTasksMode = args.includes('--check-tasks');
  const releaseBlockingOnly = args.includes('--release-blocking');
  const validateMode = args.includes('--validate');
  
  // Parse generated settings
  const allModules = parseSettingsIncludes();
  
  if (validateMode) {
    // Validate classification
    const unclassified = validateClassification(allModules);
    
    if (unclassified.length > 0) {
      console.error('❌ Found unclassified modules:');
      unclassified.forEach(m => console.error(`  - ${m}`));
      console.error('\nAdd these modules to MODULE_CLASSIFICATION in this script.');
      process.exit(1);
    }
    
    console.log('✅ All Data-Cloud modules are properly classified.');
    process.exit(0);
  }
  
  if (releaseBlockingOnly) {
    // Output only release-blocking modules
    const releaseBlocking = allModules.filter(m => 
      classifyModule(m).category === 'release-blocking'
    );
    
    if (checkTasksMode) {
      console.log(generateGradleTasks(releaseBlocking).join(' '));
    } else {
      releaseBlocking.forEach(m => console.log(m));
    }
    return;
  }
  
  if (checkTasksMode) {
    // Output Gradle tasks for all modules
    const tasks = generateGradleTasks(allModules);
    console.log(tasks.join(' '));
    return;
  }
  
  // Default: list all modules with classification
  console.log('Data-Cloud Gradle Modules (from config/generated/settings-gradle-includes.kts):\n');
  
  const categorized = {
    'release-blocking': [],
    'advisory': [],
    'action-plane': [],
    'experimental': [],
    'deprecated': [],
    'excluded': [],
    'unclassified': [],
  };
  
  for (const module of allModules) {
    const classification = classifyModule(module);
    categorized[classification.category].push({ module, reason: classification.reason });
  }
  
  for (const [category, modules] of Object.entries(categorized)) {
    if (modules.length === 0) continue;
    
    console.log(`${category.toUpperCase()} (${modules.length}):`);
    for (const { module, reason } of modules) {
      console.log(`  ${module}`);
      console.log(`    Reason: ${reason}`);
    }
    console.log('');
  }
  
  // Summary
  console.log(`Total: ${allModules.length} modules`);
  console.log(`Release-blocking: ${categorized['release-blocking'].length}`);
  console.log(`Advisory: ${categorized['advisory'].length}`);
  console.log(`Action Plane: ${categorized['action-plane'].length}`);
}

main();
