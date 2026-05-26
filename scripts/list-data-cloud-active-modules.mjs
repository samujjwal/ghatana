#!/usr/bin/env node

/**
 * Lists active Data Cloud Gradle modules from generated settings.
 *
 * @doc.type script
 * @doc.purpose Provides the CI/release source of truth for active Data Cloud Gradle modules
 * @doc.layer repo
 * @doc.pattern Module enumeration
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const settingsPath = path.join(repoRoot, 'config/generated/settings-gradle-includes.kts');

const advisoryModules = new Set([
  // DC-E2E-001: Promoted API contract and integration tests to release-blocking
  // These modules are now required for release to ensure E2E journey coverage
]);

const releaseBlockingModules = new Set([
  ':products:data-cloud:planes:shared-spi',
  ':products:data-cloud:planes:data:entity',
  ':products:data-cloud:planes:event:core',
  ':products:data-cloud:planes:operations:config',
  ':products:data-cloud:planes:intelligence:analytics',
  ':products:data-cloud:planes:governance:core',
  ':products:data-cloud:delivery:runtime-composition',
  ':products:data-cloud:extensions:plugins',
  ':products:data-cloud:delivery:api',
  ':products:data-cloud:delivery:launcher',
  ':products:data-cloud:delivery:sdk',
  ':products:data-cloud:contracts',
  ':products:data-cloud:extensions:agent-registry',
  ':products:data-cloud:extensions:agent-catalog',
  ':products:data-cloud:delivery:api-contract-tests',
  ':products:data-cloud:integration-tests',
  ':products:data-cloud:planes:intelligence:feature-ingest',
  ':products:data-cloud:planes:event:store',
  ':products:data-cloud:extensions:kernel-bridge',
]);

const actionPlaneModules = new Set([
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
]);

export function parseDataCloudModules(settingsSource) {
  const modules = [];
  let inDataCloudSection = false;

  for (const line of settingsSource.split(/\r?\n/)) {
    if (line.includes('// platform-provider: Data Cloud (data-cloud)')) {
      inDataCloudSection = true;
      continue;
    }

    if (inDataCloudSection && /^\/\/\s+(business-product|platform-provider|shared-service):/.test(line.trim())) {
      break;
    }

    if (inDataCloudSection) {
      const match = /include\("([^"]+)"\)/.exec(line);
      if (match) {
        modules.push(match[1]);
      }
    }
  }

  return modules;
}

export function classifyDataCloudModule(modulePath) {
  if (!modulePath.startsWith(':products:data-cloud:')) {
    return { category: 'invalid', reason: 'Not a Data Cloud module' };
  }
  if (advisoryModules.has(modulePath)) {
    return { category: 'advisory', reason: 'Integration or contract-test module; compile-gated, release-check optional' };
  }
  if (actionPlaneModules.has(modulePath)) {
    return { category: 'release-blocking', reason: 'AEP Action Plane module; compile and release checks are blocking' };
  }
  if (releaseBlockingModules.has(modulePath)) {
    return { category: 'release-blocking', reason: 'Active Data Cloud production module' };
  }
  return { category: 'invalid', reason: 'Data Cloud module is not classified as release-blocking or advisory' };
}

export function filterModulesByScope(modules, scope) {
  if (scope === 'all-active') {
    return modules;
  }
  if (scope === 'release-blocking') {
    return modules.filter((modulePath) => classifyDataCloudModule(modulePath).category === 'release-blocking');
  }
  if (scope === 'advisory') {
    return modules.filter((modulePath) => classifyDataCloudModule(modulePath).category === 'advisory');
  }
  throw new Error(`Unsupported scope: ${scope}`);
}

export function gradleTasksForModules(modules, taskName) {
  return modules.map((modulePath) => `${modulePath}:${taskName}`);
}

export function moduleBuildFilePath(modulePath, root = repoRoot) {
  return path.join(root, ...modulePath.replace(/^:/, '').split(':'), 'build.gradle.kts');
}

export function moduleHasJavaCompileTask(modulePath, root = repoRoot) {
  const buildFilePath = moduleBuildFilePath(modulePath, root);
  if (!existsSync(buildFilePath)) {
    return false;
  }

  const buildFile = readFileSync(buildFilePath, 'utf8');
  return /id\("java-module"\)|id\("java-library"\)|id\("java"\)|`java-module`|`java-library`|`java`/.test(buildFile)
    || existsSync(path.join(path.dirname(buildFilePath), 'src/main/java'));
}

export function validateModuleClassification(modules) {
  return modules.filter((modulePath) => {
    const classification = classifyDataCloudModule(modulePath);
    return classification.category === 'invalid';
  });
}

export function readSettingsSource(root = repoRoot) {
  const generatedSettingsPath = path.join(root, 'config/generated/settings-gradle-includes.kts');
  if (!existsSync(generatedSettingsPath)) {
    throw new Error('config/generated/settings-gradle-includes.kts not found; run node scripts/generate-product-registry-artifacts.mjs');
  }
  return readFileSync(generatedSettingsPath, 'utf8');
}

function printJson(payload) {
  process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
}

function main() {
  const args = process.argv.slice(2);
  const scopeArg = args.find((arg) => arg.startsWith('--scope='))?.split('=')[1] ?? 'all-active';
  const taskArg = args.find((arg) => arg.startsWith('--task='))?.split('=')[1] ?? null;
  const formatArg = args.find((arg) => arg.startsWith('--format='))?.split('=')[1] ?? 'lines';
  const validate = args.includes('--validate');

  const modules = parseDataCloudModules(readSettingsSource());
  const invalidModules = validateModuleClassification(modules);

  if (validate) {
    if (invalidModules.length > 0) {
      console.error('Unclassified or invalid Data Cloud modules:');
      for (const modulePath of invalidModules) {
        console.error(`- ${modulePath}`);
      }
      process.exit(1);
    }
    console.log(`All ${modules.length} active Data Cloud modules are classified.`);
    return;
  }

  const scopedModules = filterModulesByScope(modules, scopeArg);
  const taskModules = taskArg === 'compileJava'
    ? scopedModules.filter((modulePath) => moduleHasJavaCompileTask(modulePath))
    : scopedModules;
  const values = taskArg ? gradleTasksForModules(taskModules, taskArg) : scopedModules;

  if (formatArg === 'json') {
    printJson(values.map((value) => {
      const modulePath = taskArg ? value.slice(0, -1 * (`:${taskArg}`.length)) : value;
      return {
        value,
        module: modulePath,
        ...classifyDataCloudModule(modulePath),
      };
    }));
    return;
  }

  if (formatArg === 'shell') {
    console.log(values.join(' '));
    return;
  }

  for (const value of values) {
    console.log(value);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    main();
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
