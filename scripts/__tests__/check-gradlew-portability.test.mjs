#!/usr/bin/env node

/**
 * Tests for gradlew portability governance check
 */

import { readFileSync, writeFileSync, unlinkSync, existsSync, mkdirSync, rmSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const ROOT_DIR = join(__dirname, '..', '..');
const SCRIPT_PATH = join(ROOT_DIR, 'scripts', 'check-gradlew-portability.mjs');
const TEST_SCRIPTS_DIR = join(ROOT_DIR, '.tmp-gradlew-test-scripts');

function setupTestDir() {
  if (!existsSync(TEST_SCRIPTS_DIR)) {
    mkdirSync(TEST_SCRIPTS_DIR, { recursive: true });
  }
}

function cleanupTestDir() {
  if (existsSync(TEST_SCRIPTS_DIR)) {
    rmSync(TEST_SCRIPTS_DIR, { recursive: true, force: true });
  }
}

function runCheck() {
  try {
    // Temporarily modify the script to scan test directory
    const originalScript = readFileSync(SCRIPT_PATH, 'utf-8');
    const modifiedScript = originalScript.replace(
      /const SCRIPTS_DIR = join\(ROOT_DIR, 'scripts'\);/,
      `const SCRIPTS_DIR = join(ROOT_DIR, '.tmp-gradlew-test-scripts');`
    );
    writeFileSync(SCRIPT_PATH, modifiedScript);
    
    execSync(`node "${SCRIPT_PATH}"`, {
      cwd: ROOT_DIR,
      stdio: 'pipe'
    });
    
    // Restore original script
    writeFileSync(SCRIPT_PATH, originalScript);
    return { success: true, output: '' };
  } catch (error) {
    // Restore original script
    const originalScript = readFileSync(SCRIPT_PATH, 'utf-8');
    const restoredScript = originalScript.replace(
      /const SCRIPTS_DIR = join\(ROOT_DIR, '\.tmp-gradlew-test-scripts'\);/,
      `const SCRIPTS_DIR = join(ROOT_DIR, 'scripts');`
    );
    writeFileSync(SCRIPT_PATH, restoredScript);
    
    return { success: false, output: error.stdout?.toString() || error.stderr?.toString() || '' };
  }
}

function test(name, fn) {
  try {
    fn();
    console.log(`✓ ${name}`);
  } catch (error) {
    console.error(`✗ ${name}`);
    console.error(`  ${error.message}`);
    process.exit(1);
  }
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message || 'Assertion failed');
  }
}

// Test 1: Shell script with ./gradlew should pass
test('Shell script with ./gradlew passes', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.sh');
  writeFileSync(testFile, '#!/bin/bash\n./gradlew build\n');
  
  const result = runCheck();
  assert(result.success, 'Should pass with ./gradlew');
  
  cleanupTestDir();
});

// Test 2: Shell script with bare gradlew should fail
test('Shell script with bare gradlew fails', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.sh');
  writeFileSync(testFile, '#!/bin/bash\ngradlew build\n');
  
  const result = runCheck();
  assert(!result.success, 'Should fail with bare gradlew');
  assert(result.output.includes('Bare'), 'Should report bare gradlew usage');
  
  cleanupTestDir();
});

// Test 3: Batch file with gradlew.bat should pass
test('Batch file with gradlew.bat passes', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.bat');
  writeFileSync(testFile, '@echo off\ncall gradlew.bat build\n');
  
  const result = runCheck();
  assert(result.success, 'Should pass with gradlew.bat');
  
  cleanupTestDir();
});

// Test 4: Batch file with bare gradlew should fail
test('Batch file with bare gradlew fails', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.bat');
  writeFileSync(testFile, '@echo off\ncall gradlew build\n');
  
  const result = runCheck();
  assert(!result.success, 'Should fail with bare gradlew');
  assert(result.output.includes('Bare'), 'Should report bare gradlew usage');
  
  cleanupTestDir();
});

// Test 5: Node script with ./gradlew should pass
test('Node script with ./gradlew passes', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.mjs');
  writeFileSync(testFile, 'execSync("./gradlew build");\n');
  
  const result = runCheck();
  assert(result.success, 'Should pass with ./gradlew');
  
  cleanupTestDir();
});

// Test 6: Node script with platform-specific logic should pass
test('Node script with platform-specific logic passes', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.mjs');
  writeFileSync(testFile, 'const gradleCommand = process.platform === "win32" ? "gradlew.bat" : "./gradlew";\n');
  
  const result = runCheck();
  assert(result.success, 'Should pass with platform-specific logic');
  
  cleanupTestDir();
});

// Test 7: Node script with bare gradlew string should fail
test('Node script with bare gradlew string fails', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.mjs');
  writeFileSync(testFile, 'execSync("gradlew build");\n');
  
  const result = runCheck();
  assert(!result.success, 'Should fail with bare gradlew string');
  assert(result.output.includes('Bare'), 'Should report bare gradlew usage');
  
  cleanupTestDir();
});

// Test 8: Comments should be ignored
test('Comments with gradlew are ignored', () => {
  setupTestDir();
  const testFile = join(TEST_SCRIPTS_DIR, 'test.sh');
  writeFileSync(testFile, '#!/bin/bash\n# gradlew build\n./gradlew build\n');
  
  const result = runCheck();
  assert(result.success, 'Should ignore comments');
  
  cleanupTestDir();
});

// Test 9: Empty directory should pass
test('Empty directory passes', () => {
  setupTestDir();
  
  const result = runCheck();
  assert(result.success, 'Empty directory should pass');
  
  cleanupTestDir();
});

console.log('\nAll gradlew portability checks passed!');
