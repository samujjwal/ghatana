import test from 'node:test';
import assert from 'node:assert/strict';

import {
  classifyDataCloudModule,
  filterModulesByScope,
  gradleTasksForModules,
  parseDataCloudModules,
  validateModuleClassification,
} from '../list-data-cloud-active-modules.mjs';

const settingsFixture = `
// business-product: Finance (finance)
include(":products:finance")

// platform-provider: Data Cloud (data-cloud)
include(":products:data-cloud:planes:shared-spi")
include(":products:data-cloud:planes:action:agent-runtime")
include(":products:data-cloud:delivery:api-contract-tests")
include(":products:data-cloud:integration-tests")

// platform-provider: YAPPC (yappc)
include(":products:yappc")
`;

test('parses only Data Cloud modules from generated settings', () => {
  assert.deepEqual(parseDataCloudModules(settingsFixture), [
    ':products:data-cloud:planes:shared-spi',
    ':products:data-cloud:planes:action:agent-runtime',
    ':products:data-cloud:delivery:api-contract-tests',
    ':products:data-cloud:integration-tests',
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

  assert.deepEqual(filterModulesByScope(modules, 'release-blocking'), [
    ':products:data-cloud:planes:shared-spi',
    ':products:data-cloud:planes:action:agent-runtime',
  ]);
});

test('generates Gradle tasks without double colon prefixes', () => {
  assert.deepEqual(
    gradleTasksForModules([':products:data-cloud:planes:action:agent-runtime'], 'compileJava'),
    [':products:data-cloud:planes:action:agent-runtime:compileJava'],
  );
});

test('validates all generated Data Cloud module classifications', () => {
  const modules = parseDataCloudModules(settingsFixture);

  assert.deepEqual(validateModuleClassification(modules), []);
});
