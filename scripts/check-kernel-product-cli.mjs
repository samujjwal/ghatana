#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const cliPath = path.join(repoRoot, 'scripts', 'kernel-product.mjs');
const source = readFileSync(cliPath, 'utf8');

const requiredSnippets = [
  {
    label: 'explain output uses adapterIds',
    snippet: 'explanation.adapterIds.join',
  },
  {
    label: 'interaction preflight steps are kernel-native',
    snippet: "step.stepKind !== 'interaction-preflight'",
  },
  {
    label: 'kernel native interaction broker adapter is allowlisted',
    snippet: "kernel-product-interaction-broker",
  },
  {
    label: 'raw execution bypass detection uses ProductLifecycleStep.execution',
    snippet: 'step.execution && !step.adapter',
  },
  {
    label: 'recover command returns recovery guidance',
    snippet: 'recoverPlan(plan)',
  },
  {
    label: 'recover mode does not execute lifecycle adapters',
    snippet: "commandMode === 'execute'",
  },
];

const errors = requiredSnippets
  .filter((check) => !source.includes(check.snippet))
  .map((check) => `${check.label}: missing "${check.snippet}"`);

if (source.includes('explanation.adapters.join')) {
  errors.push('explain output must use adapterIds, not a non-existent adapters property');
}

if (source.includes('step.command && !step.adapter')) {
  errors.push('raw execution bypass detection must inspect ProductLifecycleStep.execution, not step.command');
}

if (errors.length > 0) {
  console.error('kernel-product CLI guard failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('kernel-product CLI guard passed.');
