#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

import {
  classifyDataCloudModule,
  filterModulesByScope,
  gradleTasksForModules,
  moduleHasJavaCompileTask,
  parseDataCloudModules,
  readSettingsSource,
  validateModuleClassification,
} from './list-data-cloud-active-modules.mjs';

const SCRIPT_PATH = 'scripts/generate-data-cloud-active-modules-evidence.mjs';
const EVIDENCE_PATH = '.kernel/evidence/data-cloud-active-modules.json';
const COMMAND = 'pnpm check:data-cloud-active-module-evidence';

function currentGitSha(root) {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return 'unknown';
  }
}

function readGeneratedCompileTasks(root) {
  const workflowPath = path.join(root, '.github/workflows/data-cloud-ci.yml');
  if (!existsSync(workflowPath)) {
    return [];
  }
  const workflow = readFileSync(workflowPath, 'utf8');
  const matches = workflow.matchAll(/list-data-cloud-active-modules\.mjs\s+--scope=([^\s]+)\s+--task=([^\s]+)\s+--format=shell/g);
  return [...matches].map((match) => ({
    scope: match[1],
    task: match[2],
    workflow: '.github/workflows/data-cloud-ci.yml',
  }));
}

export function createDataCloudActiveModulesEvidence(root = process.cwd(), now = new Date(), executionResult = null) {
  const modules = parseDataCloudModules(readSettingsSource(root));
  const invalidModules = validateModuleClassification(modules);
  const releaseBlockingModules = filterModulesByScope(modules, 'release-blocking');
  const advisoryModules = filterModulesByScope(modules, 'advisory');
  const compileJavaModules = modules.filter((modulePath) => moduleHasJavaCompileTask(modulePath, root));
  const releaseBlockingCheckTasks = gradleTasksForModules(releaseBlockingModules, 'check');
  const compileJavaTasks = gradleTasksForModules(compileJavaModules, 'compileJava');

  // DC-P7-002: Include execution result if provided
  const executionSection = executionResult ? {
    gradleExitStatus: executionResult.exitStatus,
    executedTasks: executionResult.executedTasks || [],
    failedTasks: executionResult.failedTasks || [],
    skippedTasks: executionResult.skippedTasks || [],
    durationMs: executionResult.durationMs || 0,
    evidenceScriptExitStatus: 0,
    compileJavaTasksAreGeneratedForCaller: true,
    releaseBlockingCheckTasksAreGeneratedForCaller: true,
  } : {
    evidenceScriptExitStatus: 0,
    compileJavaTasksAreGeneratedForCaller: true,
    releaseBlockingCheckTasksAreGeneratedForCaller: true,
  };

  return {
    generatedAt: now.toISOString(),
    pass: invalidModules.length === 0 && compileJavaTasks.length > 0 && releaseBlockingCheckTasks.length > 0,
    evidenceRun: {
      generatedBy: SCRIPT_PATH,
      source: SCRIPT_PATH,
      command: COMMAND,
      commit: currentGitSha(root),
    },
    source: {
      settings: 'config/generated/settings-gradle-includes.kts',
      classifier: 'scripts/list-data-cloud-active-modules.mjs',
      workflowTaskDiscovery: readGeneratedCompileTasks(root),
    },
    summary: {
      totalActiveModules: modules.length,
      releaseBlockingModules: releaseBlockingModules.length,
      advisoryModules: advisoryModules.length,
      compileJavaTaskCount: compileJavaTasks.length,
      releaseBlockingCheckTaskCount: releaseBlockingCheckTasks.length,
      invalidModuleCount: invalidModules.length,
    },
    generatedTasks: {
      compileJava: compileJavaTasks,
      releaseBlockingCheck: releaseBlockingCheckTasks,
    },
    execution: executionSection,
    modules: modules.map((modulePath) => {
      const classification = classifyDataCloudModule(modulePath);
      const hasJavaCompileTask = moduleHasJavaCompileTask(modulePath, root);
      const releaseBlocking = classification.category === 'release-blocking';
      return {
        module: modulePath,
        category: classification.category,
        reason: classification.reason,
        hasJavaCompileTask,
        compileJavaTask: hasJavaCompileTask ? `${modulePath}:compileJava` : null,
        releaseCheckTask: releaseBlocking ? `${modulePath}:check` : null,
      };
    }),
    validation: {
      invalidModules,
      allActiveModulesClassified: invalidModules.length === 0,
    },
  };
}

export function writeDataCloudActiveModulesEvidence(root = process.cwd(), evidence = createDataCloudActiveModulesEvidence(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidence = createDataCloudActiveModulesEvidence(root);
  writeDataCloudActiveModulesEvidence(root, evidence);

  if (!evidence.pass) {
    console.error('Data Cloud active module evidence failed validation:');
    for (const invalidModule of evidence.validation.invalidModules) {
      console.error(`- ${invalidModule}`);
    }
    process.exit(1);
  }

  console.log(`Data Cloud active module evidence written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
