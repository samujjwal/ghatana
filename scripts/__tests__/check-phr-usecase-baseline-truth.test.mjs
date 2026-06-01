import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import assert from 'node:assert/strict';

const script = new URL('../check-phr-usecase-baseline-truth.mjs', import.meta.url).pathname;

function fixtureRoot(name) {
  const root = join(tmpdir(), `${name}-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  mkdirSync(join(root, 'products/phr/config'), { recursive: true });
  return root;
}

function writeJson(root, relativePath, value) {
  writeFileSync(join(root, relativePath), `${JSON.stringify(value, null, 2)}\n`);
}

function run(root) {
  return spawnSync(process.execPath, [script, root], {
    cwd: root,
    encoding: 'utf8',
  });
}

const routeContract = {
  routes: [
    {
      path: '/provider/patients',
      stability: 'hidden',
      apiEndpoint: '/api/v1/provider/patients',
    },
  ],
};

test('passes canonical baseline APIs', () => {
  const root = fixtureRoot('phr-baseline-truth-pass');
  writeJson(root, 'products/phr/config/phr-route-contract.json', routeContract);
  writeJson(root, 'products/phr/config/phr-usecase-baseline.json', {
    usecases: [
      {
        id: 'uc-records',
        status: 'implemented',
        backendApis: ['GET /api/v1/records/:patientId'],
      },
      {
        id: 'uc-provider',
        status: 'deferred',
        backendApis: ['GET /api/v1/provider/patients'],
      },
    ],
  });

  const result = run(root);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /PASS/);
});

test('fails legacy non-versioned baseline APIs', () => {
  const root = fixtureRoot('phr-baseline-truth-legacy');
  writeJson(root, 'products/phr/config/phr-route-contract.json', routeContract);
  writeJson(root, 'products/phr/config/phr-usecase-baseline.json', {
    usecases: [
      {
        id: 'uc-records',
        status: 'implemented',
        backendApis: ['GET /records/:patientId'],
      },
    ],
  });

  const result = run(root);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /canonical \/api\/v1/);
});

test('fails non-deferred use cases that claim hidden API families', () => {
  const root = fixtureRoot('phr-baseline-truth-hidden');
  writeJson(root, 'products/phr/config/phr-route-contract.json', routeContract);
  writeJson(root, 'products/phr/config/phr-usecase-baseline.json', {
    usecases: [
      {
        id: 'uc-provider',
        status: 'implemented',
        backendApis: ['GET /api/v1/provider/patients'],
      },
    ],
  });

  const result = run(root);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /hidden API family/);
});
