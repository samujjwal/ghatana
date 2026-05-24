import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { loadCanonicalRegistry } from '../resolve-affected-products.mjs';
import { buildStagePlan, repoRoot } from '../ci/product-stage-plan.mjs';

const registry = loadCanonicalRegistry(repoRoot);
const packageJson = JSON.parse(readFileSync(path.join(repoRoot, 'package.json'), 'utf8'));

function plan(options) {
  return buildStagePlan({
    registry,
    packageJson,
    includeDependencies: true,
    ...options,
  });
}

test('dev stage stays cheap and does not run product or dependency tasks', () => {
  const result = plan({
    stage: 'dev',
    products: ['digital-marketing'],
    files: ['products/digital-marketing/ui/src/App.tsx'],
  });

  assert.deepEqual(
    result.commands.map((command) => command.label),
    ['affected TypeScript workspace typecheck'],
  );
});

test('test stage uses affected product surface plus declared provider dependencies', () => {
  const result = plan({
    stage: 'test',
    products: ['digital-marketing'],
    files: ['products/digital-marketing/ui/src/routes/Campaigns.test.tsx'],
  });

  assert.equal(result.commands[0].args.at(-1), 'pnpm test:digital-marketing-web');
  assert.ok(result.commands.some((command) => command.args.includes('check:data-cloud-platform-providers')));
  assert.ok(result.commands.some((command) => command.args.includes('check:yappc-product-unit-intent-handoff')));
});

test('build stage scopes platform provider changes to the touched surface', () => {
  const result = plan({
    stage: 'build',
    products: ['data-cloud'],
    files: ['products/data-cloud/delivery/ui/src/main.tsx'],
  });

  assert.equal(result.commands.length, 1);
  assert.equal(result.commands[0].args.at(-1), 'pnpm build:data-cloud-web');
});

test('validate stage runs product validation only for contract and release sensitive changes', () => {
  const sourceOnly = plan({
    stage: 'validate',
    products: ['phr'],
    files: ['products/phr/apps/web/src/App.tsx'],
  });
  assert.ok(!sourceOnly.commands.some((command) => command.args.includes('validate:phr')));

  const contractChange = plan({
    stage: 'validate',
    products: ['phr'],
    files: ['products/phr/apps/web/src/contracts/patient.schema.ts'],
  });
  assert.ok(contractChange.commands.some((command) => command.args.includes('validate:phr')));
});
