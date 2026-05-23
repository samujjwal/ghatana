#!/usr/bin/env node

/**
 * Tests for gradle wrapper script usage governance check
 */

import { writeFileSync, unlinkSync, mkdirSync, rmdirSync, existsSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';
import { test, before, after } from 'node:test';
import assert from 'node:assert';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(__dirname, '..', '..');
const SCRIPT_PATH = join(ROOT_DIR, 'scripts', 'check-gradle-wrapper-script-usage.mjs');
const TEST_SCRIPTS_DIR = join(ROOT_DIR, '.tmp-gradle-wrapper-test-scripts');

function runCheck() {
  try {
    execSync(`node ${SCRIPT_PATH} --test-dir`, { cwd: TEST_SCRIPTS_DIR, stdio: 'pipe' });
    return { success: true, output: '' };
  } catch (error) {
    return { success: false, output: error.stdout?.toString() + error.stderr?.toString() };
  }
}

before(() => {
  // Setup test directory
  if (!existsSync(TEST_SCRIPTS_DIR)) {
    mkdirSync(TEST_SCRIPTS_DIR, { recursive: true });
  }
});

after(() => {
  // Cleanup test directory
  if (existsSync(TEST_SCRIPTS_DIR)) {
    rmSync(TEST_SCRIPTS_DIR, { recursive: true, force: true });
  }
});

// Test 1: package.json with ./gradlew should pass
test('package.json with ./gradlew passes', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    scripts: {
      build: './gradlew build',
      test: './gradlew test',
    },
  }, null, 2));

  const result = runCheck();
  assert(result.success, 'Should pass with ./gradlew');
  unlinkSync(testFile);
});

// Test 2: package.json with bare gradlew should fail
test('package.json with bare gradlew fails', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    scripts: {
      build: 'gradlew build',
      test: 'gradlew test',
    },
  }, null, 2));

  const result = runCheck();
  assert(!result.success, 'Should fail with bare gradlew');
  assert(result.output.includes('Bare'), 'Should report bare gradlew usage');
  unlinkSync(testFile);
});

// Test 3: package.json with platform-specific logic should pass
test('package.json with platform-specific logic passes', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    scripts: {
      build: 'node scripts/run-gradle-wrapper.mjs build',
    },
  }, null, 2));

  const result = runCheck();
  assert(result.success, 'Should pass with platform-specific logic');
  unlinkSync(testFile);
});

// Test 4: package.json with gradlew.bat should pass
test('package.json with gradlew.bat passes', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    scripts: {
      build: 'gradlew.bat build',
    },
  }, null, 2));

  const result = runCheck();
  assert(result.success, 'Should pass with gradlew.bat');
  unlinkSync(testFile);
});

// Test 5: Mixed usage should fail
test('Mixed bare and portable gradlew fails', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    scripts: {
      build: './gradlew build',
      test: 'gradlew test', // This should fail
    },
  }, null, 2));

  const result = runCheck();
  assert(!result.success, 'Should fail with mixed usage');
  assert(result.output.includes('test'), 'Should report the failing script');
  unlinkSync(testFile);
});

// Test 6: Empty scripts object should pass
test('Empty scripts object passes', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    scripts: {},
  }, null, 2));

  const result = runCheck();
  assert(result.success, 'Should pass with empty scripts');
  unlinkSync(testFile);
});

// Test 7: No scripts property should pass
test('No scripts property passes', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    name: 'test-package',
  }, null, 2));

  const result = runCheck();
  assert(result.success, 'Should pass with no scripts');
  unlinkSync(testFile);
});

// Test 8: Comments in scripts are ignored
test('Comments in scripts are ignored', () => {
  const testFile = join(TEST_SCRIPTS_DIR, 'package.json');
  writeFileSync(testFile, JSON.stringify({
    scripts: {
      build: './gradlew build',
    },
  }, null, 2));

  const result = runCheck();
  assert(result.success, 'Should pass with ./gradlew');
  unlinkSync(testFile);
});

console.log('\nAll gradle wrapper script usage checks passed!');
