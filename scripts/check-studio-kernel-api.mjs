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
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'KernelLifecycleClientAuthContext', 'Studio Kernel API client auth context');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'KernelLifecycleClientError', 'Studio Kernel API client error mapping');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'KernelLifecycleAuthError', 'Studio Kernel API client auth error');
requireIncludes('platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts', 'KernelLifecycleScopeError', 'Studio Kernel API client scope error');

// Check Studio artifact workflow persistence and source acquisition use Kernel-backed endpoints
requireFile('platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts');
requireIncludes('platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts', '/api/v1/studio/workflow-state', 'Studio workflow persistence adapter');
requireIncludes('platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts', '/api/v1/studio/workflow-evidence', 'Studio workflow persistence adapter');
requireIncludes('platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts', 'Idempotency-Key', 'Studio workflow persistence adapter');
requireFile('platform/typescript/ghatana-studio/src/providers/source-acquisition.ts');
requireIncludes('platform/typescript/ghatana-studio/src/providers/source-acquisition.ts', '/api/v1/studio/source-acquisition/repository', 'Studio source acquisition client');
requireIncludes('platform/typescript/ghatana-studio/src/providers/source-acquisition.ts', '/api/v1/studio/source-acquisition/archive', 'Studio source acquisition client');
requireIncludes('platform/typescript/ghatana-studio/src/providers/source-acquisition.ts', '/api/v1/studio/source-acquisition/jobs/', 'Studio source acquisition client');
requireFile('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts');
requireIncludes('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts', '/api/v1/studio/workflow-state', 'Kernel Studio API handlers');
requireIncludes('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts', '/api/v1/studio/workflow-evidence', 'Kernel Studio API handlers');
requireIncludes('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts', '/api/v1/studio/source-acquisition/repository', 'Kernel Studio API handlers');
requireIncludes('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts', '/api/v1/studio/source-acquisition/archive', 'Kernel Studio API handlers');
requireIncludes('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts', '/api/v1/studio/source-acquisition/jobs/:jobId', 'Kernel Studio API handlers');
requireIncludes('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts', 'patchStudioSourceAcquisitionJob', 'Kernel Studio API handlers');
requireFile('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'StudioSourceAcquisitionWorker', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'HttpStudioRepositoryArchiveFetcher', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'StudioRepositoryArchiveTokenProvider', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'FileSystemStudioSourceAcquisitionPayloadStore', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'executeStoredArchive', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'StudioSourceAcquisitionQueueRunner', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'claimNextPendingJob', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/acquisition/StudioSourceAcquisitionWorker.ts', 'getQueueSnapshot', 'Kernel Studio source acquisition worker');
requireIncludes('platform/typescript/kernel-lifecycle/src/index.ts', 'StudioSourceAcquisitionWorker', 'Kernel lifecycle public exports');
requireIncludes('platform/typescript/kernel-lifecycle/src/index.ts', 'FileSystemStudioSourceAcquisitionPayloadStore', 'Kernel lifecycle public exports');
requireIncludes('platform/typescript/kernel-lifecycle/src/index.ts', 'StudioSourceAcquisitionQueueRunner', 'Kernel lifecycle public exports');
requireIncludes('platform/typescript/kernel-lifecycle/src/index.ts', 'StudioSourceAcquisitionQueueSnapshot', 'Kernel lifecycle public exports');
requireIncludes('platform/typescript/kernel-lifecycle/src/index.ts', 'HttpStudioRepositoryArchiveFetcher', 'Kernel lifecycle public exports');

// Check Studio runtime context exists and is used
requireFile('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'StudioRuntimeIdentity', 'Studio runtime context');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'StudioRuntimeContextState', 'Studio runtime context');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'resolveStudioRuntimeContext', 'Studio runtime context');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'VITE_STUDIO_TENANT_ID', 'Studio runtime context env vars');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'VITE_STUDIO_WORKSPACE_ID', 'Studio runtime context env vars');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'VITE_STUDIO_PROJECT_ID', 'Studio runtime context env vars');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'VITE_STUDIO_AUTH_TOKEN', 'Studio runtime context env vars');
requireIncludes('platform/typescript/ghatana-studio/src/config/studioRuntimeContext.ts', 'VITE_STUDIO_USER_ID', 'Studio runtime context env vars');

// Check Studio main.tsx wires runtime context
requireFile('platform/typescript/ghatana-studio/src/main.tsx');
requireIncludes('platform/typescript/ghatana-studio/src/main.tsx', 'resolveStudioRuntimeContext', 'Studio main entry point');
requireIncludes('platform/typescript/ghatana-studio/src/main.tsx', 'runtimeContext', 'Studio main entry point');
requireIncludes('platform/typescript/ghatana-studio/src/main.tsx', 'tenantId', 'Studio main entry point');
requireIncludes('platform/typescript/ghatana-studio/src/main.tsx', 'workspaceId', 'Studio main entry point');
requireIncludes('platform/typescript/ghatana-studio/src/main.tsx', 'projectId', 'Studio main entry point');
requireIncludes('platform/typescript/ghatana-studio/src/main.tsx', 'authToken', 'Studio main entry point');

// Check Studio data context uses Kernel lifecycle client and runtime context
requireFile('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'KernelLifecycleClient', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'client?: KernelLifecycleClient', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'runtimeContext', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'authenticatedUserId', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'createPlan', 'Studio data context');
requireIncludes('platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx', 'executePhase', 'Studio data context');

// Check Studio LifecyclePage uses Kernel client for execute buttons and resolves approver
requireFile('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'createPlan', 'Studio LifecyclePage');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'executePhase', 'Studio LifecyclePage');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'useStudioLifecycleData', 'Studio LifecyclePage');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'authenticatedUserId', 'Studio LifecyclePage');
requireIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'runtimeContext', 'Studio LifecyclePage must use runtime context for session/scope');
requireNotIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', 'studio-operator', 'Studio LifecyclePage must not hardcode approver');
requireNotIncludes('platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx', "fetch('/api/kernel", 'Studio LifecyclePage must use typed client');
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
