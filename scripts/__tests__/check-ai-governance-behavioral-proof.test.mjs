#!/usr/bin/env node

/**
 * Tests for check-ai-governance-behavioral-proof.mjs
 *
 * Validates that the AI governance behavioral proof checker correctly
 * identifies implementation of AI governance controls.
 */

import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/check-ai-governance-behavioral-proof.mjs');

describe('check-ai-governance-behavioral-proof', () => {
  let tempDir;

  beforeEach(() => {
    tempDir = path.join(repoRoot, '.temp-test-ai-governance');
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
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/AIGovernanceService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class AIGovernanceService {
    private ModelRegistry modelRegistry;
    
    public void checkModelAvailability() {
        // Check model health
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('AI governance behavioral proof check passed'));
  });

  it('should detect missing AI governance infrastructure', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/SimpleService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class SimpleService {
    public void doSomething() {
        // Simple service without AI governance
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Missing AI governance infrastructure'));
  });

  it('should detect model availability proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/test/java'), { recursive: true });
    
    const testFile = path.join(productDir, 'src/test/java/AIGovernanceTest.java');
    writeFileSync(testFile, `
package com.test;

import org.junit.jupiter.api.Test;

class AIGovernanceTest {
    @Test
    void shouldCheckModelAvailability() {
        // Verify model availability before inference
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Model availability proof covered'));
  });

  it('should detect fallback prevention proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/AIService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class AIService {
    public void executeWithFallbackPrevention() {
        // Disable fallback to ensure model quality
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Fallback prevention proof covered'));
  });

  it('should detect privacy redaction proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/PrivacyService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class PrivacyService {
    public String redactPIIBeforeModelCall(String input) {
        // Redact PII before sending to model
        return input;
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Privacy redaction proof covered'));
  });

  it('should detect provenance tracking proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/ProvenanceService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class ProvenanceService {
    public void trackPromptProvenance(String prompt, String response) {
        // Track prompt and response for audit
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Provenance tracking proof covered'));
  });

  it('should detect cost budget enforcement proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/CostBudgetService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class CostBudgetService {
    public void enforceCostBudget(String model, double cost) {
        // Enforce cost budget for model calls
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Cost budget enforcement proof covered'));
  });

  it('should detect evaluation quality thresholds proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/EvaluationService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class EvaluationService {
    public boolean checkQualityThreshold(double score, double threshold) {
        // Check if model meets quality threshold
        return score >= threshold;
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Evaluation quality thresholds proof covered'));
  });

  it('should detect human approval proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/ApprovalService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class ApprovalService {
    public boolean requireHumanApprovalForRiskyAction(String action) {
        // Require human approval for risky AI actions
        return true;
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('Human approval proof covered'));
  });

  it('should detect AI audit evidence proof', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/AuditService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class AuditService {
    public void logAIRecommendation(String recommendation) {
        // Log AI-generated recommendations for audit
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('AI audit evidence proof covered'));
  });

  it('should detect AI safety guardrails', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/SafetyService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class SafetyService {
    public boolean checkSafetyGuardrails(String input) {
        // Check input against safety guardrails
        return true;
    }
}
    `);

    const result = execSync(`node ${scriptPath} --product test-product`, {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe',
    });

    assert(result.includes('AI safety guardrails implemented'));
  });

  it('should generate evidence report in CI mode', () => {
    const productDir = path.join(tempDir, 'test-product');
    mkdirSync(productDir, { recursive: true });
    mkdirSync(path.join(productDir, 'src/main/java'), { recursive: true });
    
    const serviceFile = path.join(productDir, 'src/main/java/AIGovernanceService.java');
    writeFileSync(serviceFile, `
package com.test;

@Service
class AIGovernanceService {
    private ModelRegistry modelRegistry;
}
    `);

    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'ai-governance-behavioral-proof');
    
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
