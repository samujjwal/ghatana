#!/usr/bin/env node

/**
 * Tests for check-phr-allowlist-roles.mjs
 */

import { readFileSync, writeFileSync, unlinkSync, existsSync } from 'fs';
import { join } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPT_PATH = join(__dirname, '..', 'check-phr-allowlist-roles.mjs');
const TEST_DIR = join(__dirname, 'temp-test-files');

function runScript() {
  try {
    execSync(`node "${SCRIPT_PATH}"`, { encoding: 'utf-8' });
    return { success: true, output: '' };
  } catch (error) {
    return { success: false, output: error.stdout + error.stderr };
  }
}

function setupTestFile(content) {
  if (!existsSync(TEST_DIR)) {
    // Create test directory structure
    execSync(`mkdir -p "${TEST_DIR}/products/phr/src/main/java/com/ghatana/phr/api/routes"`, { encoding: 'utf-8' });
  }
  writeFileSync(join(TEST_DIR, 'products/phr/src/main/java/com/ghatana/phr/api/routes/TestFile.java'), content);
}

function cleanup() {
  if (existsSync(TEST_DIR)) {
    execSync(`rm -rf "${TEST_DIR}"`, { encoding: 'utf-8' });
  }
}

let testsPassed = 0;
let testsFailed = 0;

function test(name, fn) {
  try {
    fn();
    console.log(`✓ ${name}`);
    testsPassed++;
  } catch (error) {
    console.error(`✗ ${name}`);
    console.error(`  ${error.message}`);
    testsFailed++;
  }
}

function assertEqual(actual, expected, message) {
  if (actual !== expected) {
    throw new Error(`${message}\n  Expected: ${expected}\n  Actual: ${actual}`);
  }
}

function assertContains(haystack, needle, message) {
  if (!haystack.includes(needle)) {
    throw new Error(`${message}\n  Expected to contain: ${needle}\n  Actual: ${haystack}`);
  }
}

// Test 1: Script should pass when no violations exist
test('Script passes when no ALLOWED_ROLES.contains violations exist', () => {
  cleanup();
  const result = runScript();
  assertEqual(result.success, true, 'Script should succeed with no violations');
});

// Test 2: Script should fail when violation exists in non-allowed file
test('Script fails when ALLOWED_ROLES.contains used in non-allowed file', () => {
  cleanup();
  setupTestFile(`
    package com.ghatana.phr.api.routes;
    
    public class TestFile {
        public void checkRole(String role) {
            if (ALLOWED_ROLES.contains(role)) {
                // This should be flagged
            }
        }
    }
  `);
  
  const result = runScript();
  assertEqual(result.success, false, 'Script should fail with violation');
  assertContains(result.output, 'TestFile.java', 'Output should mention the violating file');
  assertContains(result.output, 'ALLOWED_ROLES.contains', 'Output should mention the violation');
  cleanup();
});

// Test 3: Script should ignore violations in PhrRouteSupport.java
test('Script ignores ALLOWED_ROLES.contains in PhrRouteSupport.java', () => {
  cleanup();
  setupTestFile(`
    package com.ghatana.phr.api.routes;
    
    public class PhrRouteSupport {
        static final Set<String> ALLOWED_ROLES = Set.of("patient", "caregiver");
        
        public static void validateRole(String role) {
            if (!ALLOWED_ROLES.contains(role)) {
                throw new IllegalArgumentException("Invalid role");
            }
        }
    }
  `);
  
  const result = runScript();
  assertEqual(result.success, true, 'Script should succeed (PhrRouteSupport is allowed)');
  cleanup();
});

// Test 4: Script should find multiple violations
test('Script reports multiple violations', () => {
  cleanup();
  setupTestFile(`
    package com.ghatana.phr.api.routes;
    
    public class TestFile {
        public void checkRole(String role) {
            if (ALLOWED_ROLES.contains(role)) {
                // First violation
            }
            if (ALLOWED_ROLES.contains("admin")) {
                // Second violation
            }
        }
    }
  `);
  
  const result = runScript();
  assertEqual(result.success, false, 'Script should fail with violations');
  assertContains(result.output, '2 violation', 'Output should report 2 violations');
  cleanup();
});

// Test 5: Script provides helpful error message
test('Script provides helpful error message with context', () => {
  cleanup();
  setupTestFile(`
    package com.ghatana.phr.api.routes;
    
    public class TestFile {
        public void checkRole(String role) {
            if (ALLOWED_ROLES.contains(role)) {
                // Violation
            }
        }
    }
  `);
  
  const result = runScript();
  assertEqual(result.success, false, 'Script should fail');
  assertContains(result.output, 'PhrPolicyEvaluator', 'Output should mention PhrPolicyEvaluator');
  assertContains(result.output, 'Context:', 'Output should provide context');
  cleanup();
});

// Cleanup
cleanup();

// Summary
console.log(`\n${testsPassed} passed, ${testsFailed} failed`);
process.exit(testsFailed === 0 ? 0 : 1);
