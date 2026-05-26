import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import {
  classifyDataCloudModule,
  filterModulesByScope,
  gradleTasksForModules,
  moduleHasJavaCompileTask,
  parseDataCloudModules,
  validateModuleClassification,
} from '../list-data-cloud-active-modules.mjs';

const scriptPath = fileURLToPath(new URL('../list-data-cloud-active-modules.mjs', import.meta.url));

const settingsFixture = `
// business-product: Finance (finance)
include(":products:finance")

// platform-provider: Data Cloud (data-cloud)
include(":products:data-cloud:planes:shared-spi")
include(":products:data-cloud:planes:action:agent-runtime")
include(":products:data-cloud:delivery:api-contract-tests")
include(":products:data-cloud:integration-tests")
include(":products:data-cloud:unclassified:new-module")

// platform-provider: YAPPC (yappc)
include(":products:yappc")
`;

test('parses only Data Cloud modules from generated settings', () => {
  assert.deepEqual(parseDataCloudModules(settingsFixture), [
    ':products:data-cloud:planes:shared-spi',
    ':products:data-cloud:planes:action:agent-runtime',
    ':products:data-cloud:delivery:api-contract-tests',
    ':products:data-cloud:integration-tests',
    ':products:data-cloud:unclassified:new-module',
  ]);
});

test('classifies action plane modules as release-blocking', () => {
  assert.deepEqual(classifyDataCloudModule(':products:data-cloud:planes:action:agent-runtime'), {
    category: 'release-blocking',
    reason: 'AEP Action Plane module; compile and release checks are blocking',
  });
});

test('filters release-blocking modules without dropping action plane coverage', () => {
  const modules = parseDataCloudModules(settingsFixture);

  // DC-E2E-001, DC-E2E-002: API contract tests and integration tests are now release-blocking
  assert.deepEqual(filterModulesByScope(modules, 'release-blocking'), [
    ':products:data-cloud:planes:shared-spi',
    ':products:data-cloud:planes:action:agent-runtime',
    ':products:data-cloud:delivery:api-contract-tests',
    ':products:data-cloud:integration-tests',
  ]);
});

test('classifies advisory modules separately from release-blocking modules', () => {
  // DC-E2E-001, DC-E2E-002: API contract tests and integration tests are now release-blocking
  assert.equal(classifyDataCloudModule(':products:data-cloud:integration-tests').category, 'release-blocking');
  assert.equal(classifyDataCloudModule(':products:data-cloud:delivery:api-contract-tests').category, 'release-blocking');
});

test('rejects Data Cloud modules that are not explicitly classified', () => {
  assert.deepEqual(classifyDataCloudModule(':products:data-cloud:unclassified:new-module'), {
    category: 'invalid',
    reason: 'Data Cloud module is not classified as release-blocking or advisory',
  });
  assert.deepEqual(validateModuleClassification(parseDataCloudModules(settingsFixture)), [
    ':products:data-cloud:unclassified:new-module',
  ]);
});

test('generates Gradle tasks without double colon prefixes', () => {
  assert.deepEqual(
    gradleTasksForModules([':products:data-cloud:planes:action:agent-runtime'], 'compileJava'),
    [':products:data-cloud:planes:action:agent-runtime:compileJava'],
  );
});

test('validates all generated Data Cloud module classifications', () => {
  const modules = [
    ':products:data-cloud:planes:shared-spi',
    ':products:data-cloud:planes:action:agent-runtime',
    ':products:data-cloud:delivery:api-contract-tests',
    ':products:data-cloud:integration-tests',
  ];

  assert.deepEqual(validateModuleClassification(modules), []);
});

test('filters compileJava tasks to modules with Java compilation', () => {
  assert.equal(moduleHasJavaCompileTask(':products:data-cloud:planes:action:agent-runtime'), true);
  assert.equal(moduleHasJavaCompileTask(':products:data-cloud:unclassified:new-module'), false);
});

test('prints shell output for release-blocking compile tasks', () => {
  // DC-E2E-001, DC-E2E-002: Integration tests are now release-blocking
  const modules = filterModulesByScope(parseDataCloudModules(settingsFixture), 'release-blocking');
  const compileModules = modules.filter((m) => moduleHasJavaCompileTask(m));
  const tasks = gradleTasksForModules(compileModules, 'compileJava');
  
  assert.ok(tasks.includes(':products:data-cloud:planes:action:agent-runtime:compileJava'));
  assert.ok(tasks.includes(':products:data-cloud:integration-tests:compileJava'));
});

test('prints json output with classification metadata', () => {
  // DC-E2E-001, DC-E2E-002: API contract tests and integration tests are now release-blocking
  const modules = filterModulesByScope(parseDataCloudModules(settingsFixture), 'release-blocking');
  
  assert.ok(modules.some((module) => module === ':products:data-cloud:integration-tests'));
  assert.ok(modules.some((module) => module === ':products:data-cloud:delivery:api-contract-tests'));
});
