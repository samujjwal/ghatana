#!/usr/bin/env node

/**
 * Check Studio Kernel API Integration
 *
 * Validates that:
 * - Studio must not render lifecycle execute buttons without Kernel client
 * - Studio Kernel API client is properly typed and validated
 * - Studio uses Kernel lifecycle client for all lifecycle operations
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const errors = [];

function read(relativePath) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
    return '';
  }
  return readFileSync(join(repoRoot, relativePath), 'utf8');
}

function requireIncludes(relativePath, needle, label = relativePath) {
  const source = read(relativePath);
  if (!source.includes(needle)) {
    errors.push(`${label} must include ${needle}`);
  }
}

function requireNotIncludes(relativePath, needle, label = relativePath) {
  const source = read(relativePath);
  if (source.includes(needle)) {
    errors.push(`${label} must not include ${needle}`);
  }
}

// Check Studio Kernel API client exists and is properly typed
requireFile('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'KernelLifecycleClient', 'Studio Kernel API client');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'createLifecyclePlan', 'Studio Kernel API client');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'executeLifecyclePhase', 'Studio Kernel API client');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'LifecyclePlanSchema', 'Studio Kernel API client');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'LifecycleRunSchema', 'Studio Kernel API client');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'KernelLifecycleApiErrorSchema', 'Studio Kernel API client');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'providerMode', 'Studio Kernel API client');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'authToken', 'Studio Kernel API client');

// Check Studio data context uses Kernel lifecycle client
requireFile('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'KernelLifecycleClient', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'createKernelLifecycleClient', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'createPlan', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'executePhase', 'Studio data context');

// Check Studio LifecyclePage uses Kernel client for execute buttons
requireFile('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'createPlan', 'Studio LifecyclePage');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'executePhase', 'Studio LifecyclePage');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'useStudioLifecycleData', 'Studio LifecyclePage');
requireNotIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'fetch('/api/kernel', 'Studio LifecyclePage must use typed client');
requireNotIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'axios.get', 'Studio LifecyclePage must use typed client');

// Check Studio AgentsPage uses agent lifecycle client
requireFile('platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx');
requireFile('platform/typescript/ghatana-studio/src/api/agentLifecycleClient.ts');
requireIncludes('platform/typescript/ghatana-studio/src/api/agentLifecycleClient.ts', 'AgentLifecycleActionRequestSchema', 'Studio agent lifecycle client');
requireIncludes('platform/typescript/ghatana-studio/src/api/agentLifecycleClient.ts', 'AgentLifecycleActionResultSchema', 'Studio agent lifecycle client');
requireIncludes('platform/typescript/ghatana-studio/src/api/agentLifecycleClient.ts', 'submitAction', 'Studio agent lifecycle client');

function requireFile(relativePath) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
  }
}

if (errors.length > 0) {
  console.error('Studio Kernel API integration check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('Studio Kernel API integration check passed.');
