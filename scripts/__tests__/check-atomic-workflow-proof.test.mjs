#!/usr/bin/env node

/**
 * Tests for check-atomic-workflow-proof.mjs
 *
 * Validates that the atomic workflow failure-injection checker correctly
 * identifies test coverage for transactional atomicity scenarios.
 */

import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/check-atomic-workflow-proof.mjs');

describe('check-atomic-workflow-proof', () => {
  let tempDir;

  beforeEach(() => {
    tempDir = path.join(repoRoot, '.temp-test-atomic-workflow');
    if (existsSync(tempDir)) {
      rmSync(tempDir, { recursive: true, force: true });
    }
    mkdirSync(tempDir, { recursive: true });
  });

  afterEach(() => {
    if (existsSync(tempDir)) {
      rmSync(tempDir, { recursive: true, force: true });
    }
  });

  it('should run without errors on valid product structure', () => {
    // Create a minimal valid product structure
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldCompensateOnEventAppendFailure() {
        // Test business write succeeds, event append fails
    }
    
    @Test
    void shouldCompensateOnAuditWriteFailure() {
        // Test event append succeeds, audit write fails
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Atomic workflow proof check passed'));
  });

  it('should detect missing atomic workflow tests', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/SimpleTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class SimpleTest {
    @Test
    void shouldDoSomething() {
        // Simple test without atomic workflow coverage
    }
}
    `);

    try {
      execSync(`node ${scriptPath} --product test-product`, {
        cwd: repoRoot,
        encoding: 'utf8',
        stdio: 'pipe',
      });
      assert.fail('Should have thrown error for missing atomic workflow tests');
    } catch (error) {
      assert(error.stdout.includes('Missing atomic workflow failure-injection tests'));
    }
  });

  it('should detect business write + event append failure scenario', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldHandleBusinessWriteEventAppendFailure() {
        // Simulate business write success, event append failure
        // Verify compensation is triggered
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Business write + event append failure scenario covered'));
  });

  it('should detect event append + audit write failure scenario', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldHandleEventAppendAuditWriteFailure() {
        // Simulate event append success, audit write failure
        // Verify compensation is triggered
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Event append + audit write failure scenario covered'));
  });

  it('should detect audit + outbox failure scenario', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldHandleAuditOutboxFailure() {
        // Simulate audit success, outbox failure
        // Verify compensation is triggered
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Audit + outbox failure scenario covered'));
  });

  it('should detect idempotency write failure scenario', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldHandleIdempotencyWriteFailure() {
        // Simulate idempotency write failure
        // Verify retry logic works correctly
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Idempotency write failure scenario covered'));
  });

  it('should detect retry after partial failure scenario', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldRetryAfterPartialFailure() {
        // Simulate partial failure and verify retry
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Retry after partial failure scenario covered'));
  });

  it('should detect rollback after partial failure scenario', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldRollbackAfterPartialFailure() {
        // Simulate partial failure and verify rollback
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Rollback after partial failure scenario covered'));
  });

  it('should detect replay after crash scenario', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldReplayAfterCrash() {
        // Simulate crash and verify replay logic
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Replay after crash scenario covered'));
  });

  it('should detect transaction boundary markers', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/TransactionService.java');
    writeFileSync(serviceFile, `
package com.test;

import org.springframework.transaction.annotation.Transactional;

@Service
class TransactionService {
    @Transactional
    public void executeBusinessLogic() {
        // Business logic with transaction boundary
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Has transaction boundary markers'));
  });

  it('should generate evidence report in CI mode', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AtomicWorkflowTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AtomicWorkflowTest {
    @Test
    void shouldCompensateOnFailure() {
        // Test compensation logic
    }
}
    `);

    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'atomic-workflow-proof');
    
    execSync(`node ${scriptPath} --product test-product --ci`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(existsSync(evidenceDir), 'Evidence directory should be created');
    
    const evidenceFiles = require('node:fs').readdirSync(evidenceDir);
    assert(evidenceFiles.length > 0, 'Evidence files should be generated');
  });
});
