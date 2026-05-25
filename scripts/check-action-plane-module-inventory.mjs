#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import {
  classifyDataCloudModule,
  parseDataCloudModules,
  readSettingsSource,
} from './list-data-cloud-active-modules.mjs';

const SCRIPT_PATH = 'scripts/check-action-plane-module-inventory.mjs';
const INVENTORY_PATH = 'products/data-cloud/docs/architecture/ACTION_PLANE_MODULE_INVENTORY.md';
const EVIDENCE_PATH = '.kernel/evidence/action-plane-module-inventory.json';
const COMMAND = 'pnpm check:action-plane-module-inventory';
const REQUIRED_COLUMNS = [
  'Gradle Module',
  'Gradle Included?',
  'Ownership Classification',
  'Implementation State',
  'Release Blocking?',
  'Migration Destination',
];
const VALID_OWNERSHIP = new Set([
  'AEP-owned semantic module temporarily co-located',
  'Data-Cloud-owned persistence/metadata/governance module',
  'Temporary compatibility module',
  'Migration-only module',
]);
const ACTIVE_STATES = new Set(['active', 'temporary']);

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

function parseTable(markdown) {
  const rows = [];
  const lines = markdown.split(/\r?\n/);
  let headers = null;

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index].trim();
    if (!line.startsWith('|') || !line.endsWith('|')) {
      continue;
    }
    const cells = line.slice(1, -1).split('|').map((cell) => cell.trim());
    if (!headers && cells.includes('Gradle Module')) {
      headers = cells;
      continue;
    }
    if (!headers || cells.every((cell) => /^:?-{3,}:?$/.test(cell))) {
      continue;
    }
    if (cells.length !== headers.length) {
      continue;
    }
    rows.push(Object.fromEntries(headers.map((header, cellIndex) => [header, cells[cellIndex]])));
  }

  return { headers: headers ?? [], rows };
}

function cleanModule(value) {
  return value.replaceAll('`', '').trim();
}

function actionModulesFromSettings(root) {
  return parseDataCloudModules(readSettingsSource(root))
    .filter((modulePath) => modulePath.startsWith(':products:data-cloud:planes:action'));
}

export function createActionPlaneModuleInventoryEvidence(root = process.cwd(), now = new Date()) {
  const inventoryFullPath = path.join(root, INVENTORY_PATH);
  const violations = [];
  const settingsModules = actionModulesFromSettings(root);
  const settingsSet = new Set(settingsModules);

  if (!existsSync(inventoryFullPath)) {
    violations.push(`${INVENTORY_PATH} is missing`);
  }

  const { headers, rows } = existsSync(inventoryFullPath)
    ? parseTable(readFileSync(inventoryFullPath, 'utf8'))
    : { headers: [], rows: [] };

  for (const column of REQUIRED_COLUMNS) {
    if (!headers.includes(column)) {
      violations.push(`Inventory table is missing required column "${column}"`);
    }
  }

  const inventoryByModule = new Map();
  for (const row of rows) {
    const modulePath = cleanModule(row['Gradle Module'] ?? '');
    if (!modulePath) {
      violations.push('Inventory row is missing Gradle Module');
      continue;
    }
    inventoryByModule.set(modulePath, row);

    const included = (row['Gradle Included?'] ?? '').toLowerCase();
    const state = (row['Implementation State'] ?? '').toLowerCase();
    const releaseBlocking = (row['Release Blocking?'] ?? '').toLowerCase();
    const ownership = row['Ownership Classification'] ?? '';
    const migrationDestination = row['Migration Destination'] ?? '';

    if (!VALID_OWNERSHIP.has(ownership)) {
      violations.push(`${modulePath}: ownership classification is missing or invalid`);
    }
    if (!state) {
      violations.push(`${modulePath}: implementation state is missing`);
    }
    if (!migrationDestination || migrationDestination === 'pending') {
      violations.push(`${modulePath}: migration destination is missing`);
    }
    if (!['yes', 'no'].includes(included)) {
      violations.push(`${modulePath}: Gradle Included? must be yes/no`);
    }
    if (!['yes', 'no'].includes(releaseBlocking)) {
      violations.push(`${modulePath}: Release Blocking? must be yes/no`);
    }
    if (included === 'yes' && !settingsSet.has(modulePath)) {
      violations.push(`${modulePath}: inventory says Gradle included but generated settings do not include it`);
    }
    if (included === 'no' && settingsSet.has(modulePath)) {
      violations.push(`${modulePath}: generated settings include module but inventory says not included`);
    }
    if (state === 'active' && !settingsSet.has(modulePath)) {
      violations.push(`${modulePath}: inventory marks non-included module as active`);
    }
    if (settingsSet.has(modulePath)) {
      const classification = classifyDataCloudModule(modulePath);
      if (classification.category !== 'release-blocking') {
        violations.push(`${modulePath}: Action Plane Gradle module must be release-blocking in active-module classifier`);
      }
      if (releaseBlocking !== 'yes') {
        violations.push(`${modulePath}: Gradle-included Action Plane module must be release-blocking`);
      }
      if (!ACTIVE_STATES.has(state)) {
        violations.push(`${modulePath}: Gradle-included Action Plane module must be active or temporary`);
      }
    }
  }

  for (const modulePath of settingsModules) {
    if (!inventoryByModule.has(modulePath)) {
      violations.push(`${modulePath}: Gradle-included Action Plane module is missing from inventory`);
    }
  }

  return {
    generatedAt: now.toISOString(),
    pass: violations.length === 0,
    evidenceRun: {
      generatedBy: SCRIPT_PATH,
      source: SCRIPT_PATH,
      command: COMMAND,
      commit: currentGitSha(root),
    },
    source: {
      inventory: INVENTORY_PATH,
      settings: 'config/generated/settings-gradle-includes.kts',
      classifier: 'scripts/list-data-cloud-active-modules.mjs',
    },
    summary: {
      gradleIncludedActionModules: settingsModules.length,
      inventoryRows: rows.length,
      violationCount: violations.length,
    },
    modules: rows.map((row) => ({
      module: cleanModule(row['Gradle Module'] ?? ''),
      gradleIncluded: row['Gradle Included?'] ?? '',
      ownershipClassification: row['Ownership Classification'] ?? '',
      implementationState: row['Implementation State'] ?? '',
      releaseBlocking: row['Release Blocking?'] ?? '',
      migrationDestination: row['Migration Destination'] ?? '',
    })),
    violations,
  };
}

export function writeActionPlaneModuleInventoryEvidence(root = process.cwd(), evidence = createActionPlaneModuleInventoryEvidence(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidence = createActionPlaneModuleInventoryEvidence(root);
  writeActionPlaneModuleInventoryEvidence(root, evidence);

  if (!evidence.pass) {
    console.error('Action Plane module inventory check failed:\n');
    for (const violation of evidence.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Action Plane module inventory evidence written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
