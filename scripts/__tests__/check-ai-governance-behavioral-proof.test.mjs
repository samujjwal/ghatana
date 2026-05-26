import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  findAIGovernanceTestFiles,
  isAIGovernanceTestFile,
  resolveProductArg,
} from '../check-ai-governance-behavioral-proof.mjs';

function tempDir() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-ai-governance-proof-'));
}

test('resolveProductArg supports --product=value', () => {
  const value = resolveProductArg(['node', 'script', '--product=data-cloud']);
  assert.equal(value, 'data-cloud');
});

test('resolveProductArg supports --product value', () => {
  const value = resolveProductArg(['node', 'script', '--product', 'tutorputor']);
  assert.equal(value, 'tutorputor');
});

test('resolveProductArg returns undefined when no product is provided', () => {
  const value = resolveProductArg(['node', 'script', '--ci']);
  assert.equal(value, undefined);
});

test('isAIGovernanceTestFile matches java governance tests under src/test/java', () => {
  const filePath = 'products/finance/src/test/java/com/ghatana/finance/ai/AIGovernanceBehavioralTest.java';
  const content = 'class AIGovernanceBehavioralTest {}';

  assert.equal(isAIGovernanceTestFile(filePath, content), true);
});

test('isAIGovernanceTestFile matches ts governance tests under src/__tests__', () => {
  const filePath = 'products/tutorputor/services/tutorputor-platform/src/__tests__/governance.test.ts';
  const content = 'describe("AI governance policy", () => {});';

  assert.equal(isAIGovernanceTestFile(filePath, content), true);
});

test('isAIGovernanceTestFile rejects files outside test roots', () => {
  const filePath = 'products/data-cloud/src/main/java/com/ghatana/datacloud/service/Whatever.java';
  const content = 'class Whatever {}';

  assert.equal(isAIGovernanceTestFile(filePath, content), false);
});

test('findAIGovernanceTestFiles discovers only governance tests', () => {
  const root = tempDir();
  try {
    const governanceJava = path.join(root, 'src', 'test', 'java', 'com', 'example', 'AIGovernanceBehavioralTest.java');
    const governanceTs = path.join(root, 'src', '__tests__', 'governance.test.ts');
    const nonTestFile = path.join(root, 'src', 'main', 'java', 'com', 'example', 'Service.java');

    mkdirSync(path.dirname(governanceJava), { recursive: true });
    mkdirSync(path.dirname(governanceTs), { recursive: true });
    mkdirSync(path.dirname(nonTestFile), { recursive: true });

    writeFileSync(governanceJava, 'class AIGovernanceBehavioralTest {}');
    writeFileSync(governanceTs, 'describe("AI governance policy", () => {});');
    writeFileSync(nonTestFile, 'class Service {}');

    const found = findAIGovernanceTestFiles(root).map((file) => file.replaceAll('\\', '/'));

    assert.equal(found.length, 2);
    assert.ok(found.some((file) => file.endsWith('AIGovernanceBehavioralTest.java')));
    assert.ok(found.some((file) => file.endsWith('governance.test.ts')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});