#!/usr/bin/env node

/**
 * Tests for KERNEL-P1-002: No duplicate lifecycle runtimes
 * 
 * @doc.type test
 * @doc.purpose Test the duplicate lifecycle runtimes checker script
 * @doc.layer scripts
 */

import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'fs';
import { join } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = join(__filename, '..', '..');

const TEST_DIR = join(__dirname, '.tmp-lifecycle-test');
const SCRIPT_PATH = join(__dirname, '..', 'check-duplicate-lifecycle-runtimes.mjs');

function setupTestDir() {
  try {
    rmSync(TEST_DIR, { recursive: true, force: true });
  } catch (e) {
    // Ignore if directory doesn't exist
  }
  mkdirSync(TEST_DIR, { recursive: true });
}

function cleanupTestDir() {
  try {
    rmSync(TEST_DIR, { recursive: true, force: true });
  } catch (e) {
    // Ignore
  }
}

function writeTestFile(filename, content) {
  writeFileSync(join(TEST_DIR, filename), content, 'utf-8');
}

function runScript(dir = TEST_DIR) {
  try {
    execSync(`node ${SCRIPT_PATH} ${dir}`, { encoding: 'utf-8' });
    return { success: true, output: '' };
  } catch (error) {
    return { success: false, output: error.stdout + error.stderr };
  }
}

function test(description, fn) {
  try {
    fn();
    console.log(`✓ ${description}`);
  } catch (error) {
    console.error(`✗ ${description}`);
    console.error(`  ${error.message}`);
    process.exit(1);
  }
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message || 'Assertion failed');
  }
}

// Test cases
setupTestDir();

test('Passes when no duplicate lifecycle code exists', () => {
  writeTestFile('CleanCode.java', `
    public class CleanCode {
      public void doSomething() {
        System.out.println("No lifecycle code here");
      }
    }
  `);
  
  const result = runScript();
  assert(result.success, 'Should pass with no lifecycle code');
});

test('Passes when using Kernel lifecycle', () => {
  writeTestFile('KernelUsage.java', `
    import com.ghatana.platform.kernel.lifecycle.KernelLifecycle;
    
    public class KernelUsage {
      private KernelLifecycle lifecycle;
      
      public void execute() {
        lifecycle.execute();
      }
    }
  `);
  
  const result = runScript();
  assert(result.success, 'Should pass when using Kernel lifecycle');
});

test('Fails when custom LifecycleOrchestrator is found', () => {
  writeTestFile('CustomOrchestrator.java', `
    public class CustomLifecycleOrchestrator {
      public void execute() {
        // Custom lifecycle logic
      }
    }
  `);
  
  const result = runScript();
  assert(!result.success, 'Should fail with custom LifecycleOrchestrator');
  assert(result.output.includes('custom lifecycle orchestrator'), 'Should mention the violation');
});

test('Fails when custom ProductLifecycleManager is found', () => {
  writeTestFile('CustomManager.java', `
    public class ProductLifecycleManager {
      public void manage() {
        // Custom lifecycle management
      }
    }
  `);
  
  const result = runScript();
  assert(!result.success, 'Should fail with custom ProductLifecycleManager');
  assert(result.output.includes('custom product lifecycle manager'), 'Should mention the violation');
});

test('Fails when executeLifecycle method is found', () => {
  writeTestFile('ExecuteLifecycle.java', `
    public class SomeService {
      public void executeLifecycle() {
        // Custom lifecycle execution
      }
    }
  `);
  
  const result = runScript();
  assert(!result.success, 'Should fail with executeLifecycle method');
  assert(result.output.includes('executeLifecycle method'), 'Should mention the violation');
});

test('Skips platform-kernel directory', () => {
  mkdirSync(join(TEST_DIR, 'platform-kernel'), { recursive: true });
  writeTestFile('platform-kernel/KernelLifecycle.java', `
    public class KernelLifecycle {
      public void execute() {
        // Kernel lifecycle is allowed
      }
    }
  `);
  
  const result = runScript();
  assert(result.success, 'Should skip platform-kernel directory');
});

test('Skips test directories', () => {
  mkdirSync(join(TEST_DIR, 'test'), { recursive: true });
  writeTestFile('test/CustomLifecycle.java', `
    public class CustomLifecycle {
      public void execute() {
        // Test code is allowed
      }
    }
  `);
  
  const result = runScript();
  assert(result.success, 'Should skip test directories');
});

test('Handles multiple violations', () => {
  writeTestFile('MultipleViolations.java', `
    public class CustomLifecycleOrchestrator {
      public void execute() {
        // Custom orchestrator
      }
    }
    
    public class ProductLifecycleManager {
      public void manage() {
        // Custom manager
      }
    }
  `);
  
  const result = runScript();
  assert(!result.success, 'Should fail with multiple violations');
});

cleanupTestDir();

console.log('\nAll duplicate lifecycle runtime tests passed!');
