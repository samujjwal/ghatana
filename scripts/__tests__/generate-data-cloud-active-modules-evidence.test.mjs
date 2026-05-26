import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { createDataCloudActiveModulesEvidence } from '../generate-data-cloud-active-modules-evidence.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-data-cloud-active-module-evidence-'));
}

function write(root, relativePath, source) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, source);
}

function writeBuild(root, modulePath, buildSource = 'plugins { id("java-library") }\n') {
  write(root, `${modulePath.replace(/^:/, '').replaceAll(':', '/')}/build.gradle.kts`, buildSource);
}

test('generates executable evidence for classified active modules', () => {
  const root = tempRepo();
  try {
    write(root, 'config/generated/settings-gradle-includes.kts', `
      // platform-provider: Data Cloud (data-cloud)
      include(":products:data-cloud:planes:shared-spi")
      include(":products:data-cloud:planes:action:agent-runtime")
      include(":products:data-cloud:integration-tests")

      // business-product: Finance (finance)
      include(":products:finance")
    `);
    writeBuild(root, ':products:data-cloud:planes:shared-spi');
    writeBuild(root, ':products:data-cloud:planes:action:agent-runtime');
    write(root, '.github/workflows/data-cloud-ci.yml', `
      run: node ./scripts/list-data-cloud-active-modules.mjs --scope=all-active --task=compileJava --format=shell
    `);

    const evidence = createDataCloudActiveModulesEvidence(root, new Date('2026-05-24T00:00:00.000Z'));

    assert.equal(evidence.pass, true);
    assert.equal(evidence.summary.totalActiveModules, 3);
    assert.equal(evidence.summary.releaseBlockingModules, 3);
    assert.equal(evidence.summary.advisoryModules, 0);
    assert.deepEqual(evidence.validation.invalidModules, []);
    assert.ok(evidence.generatedTasks.compileJava.includes(':products:data-cloud:planes:action:agent-runtime:compileJava'));
    assert.ok(evidence.generatedTasks.releaseBlockingCheck.includes(':products:data-cloud:planes:shared-spi:check'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('fails evidence when a generated Data Cloud module is unclassified', () => {
  const root = tempRepo();
  try {
    write(root, 'config/generated/settings-gradle-includes.kts', `
      // platform-provider: Data Cloud (data-cloud)
      include(":products:data-cloud:planes:shared-spi")
      include(":products:data-cloud:planes:unknown:new-module")
    `);
    writeBuild(root, ':products:data-cloud:planes:shared-spi');

    const evidence = createDataCloudActiveModulesEvidence(root, new Date('2026-05-24T00:00:00.000Z'));

    assert.equal(evidence.pass, false);
    assert.deepEqual(evidence.validation.invalidModules, [':products:data-cloud:planes:unknown:new-module']);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
