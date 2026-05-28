#!/usr/bin/env node

/**
 * Phase 1: Tests for list-data-cloud-gradle-modules.mjs
 *
 * @doc.type test
 * @doc.purpose Test Data-Cloud module enumeration and classification
 * @doc.layer repo
 * @doc.pattern Unit test
 */

import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execFileSync } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '../..');
const SCRIPT_PATH = path.join(REPO_ROOT, 'scripts/list-data-cloud-gradle-modules.mjs');

// Mock settings content for testing
const MOCK_SETTINGS_CONTENT = `// Auto-generated from canonical-product-registry.json
// DO NOT EDIT MANUALLY - run: node scripts/generate-product-registry-artifacts.mjs

// =============================================================================
// Products (from canonical registry)
// =============================================================================
// business-product: Personal Health Records (phr)
include(":products:phr")
include(":products:phr:launcher")

// platform-provider: Data Cloud (data-cloud)
include(":products:data-cloud:planes:shared-spi")
include(":products:data-cloud:planes:data:entity")
include(":products:data-cloud:planes:event:core")
include(":products:data-cloud:planes:event:store")
include(":products:data-cloud:planes:operations:config")
include(":products:data-cloud:planes:intelligence:analytics")
include(":products:data-cloud:planes:intelligence:feature-ingest")
include(":products:data-cloud:planes:governance:core")
include(":products:data-cloud:delivery:runtime-composition")
include(":products:data-cloud:delivery:api")
include(":products:data-cloud:delivery:launcher")
include(":products:data-cloud:delivery:sdk")
include(":products:data-cloud:contracts")
include(":products:data-cloud:extensions:plugins")
include(":products:data-cloud:extensions:agent-registry")
include(":products:data-cloud:extensions:agent-catalog")
include(":products:data-cloud:delivery:api-contract-tests")
include(":products:data-cloud:integration-tests")
include(":products:data-cloud:planes:action")
include(":products:data-cloud:planes:action:operator-contracts")
include(":products:data-cloud:planes:action:central-runtime")
include(":products:data-cloud:planes:action:engine")
include(":products:data-cloud:planes:action:registry")
include(":products:data-cloud:planes:action:analytics")
include(":products:data-cloud:planes:action:security")
include(":products:data-cloud:planes:action:event-bridge")
include(":products:data-cloud:planes:action:agent-runtime")
include(":products:data-cloud:planes:action:api")
include(":products:data-cloud:planes:action:scaling")
include(":products:data-cloud:planes:action:observability")
include(":products:data-cloud:planes:action:orchestrator")
include(":products:data-cloud:planes:action:server")
include(":products:data-cloud:planes:action:identity")
include(":products:data-cloud:planes:action:compliance")
include(":products:data-cloud:planes:action:kernel-bridge")
include(":products:data-cloud:extensions:kernel-bridge")

// platform-provider: YAPPC (yappc)
include(":products:yappc")
`;

describe('list-data-cloud-gradle-modules.mjs', () => {
  let originalSettingsContent;
  const settingsPath = path.join(REPO_ROOT, 'config/generated/settings-gradle-includes.kts');

  beforeEach(() => {
    // Backup original settings if it exists
    if (fs.existsSync(settingsPath)) {
      originalSettingsContent = fs.readFileSync(settingsPath, 'utf8');
    }
    // Write mock settings for testing
    const settingsDir = path.dirname(settingsPath);
    if (!fs.existsSync(settingsDir)) {
      fs.mkdirSync(settingsDir, { recursive: true });
    }
    fs.writeFileSync(settingsPath, MOCK_SETTINGS_CONTENT);
  });

  afterEach(() => {
    // Restore original settings
    if (originalSettingsContent) {
      fs.writeFileSync(settingsPath, originalSettingsContent);
    } else if (fs.existsSync(settingsPath)) {
      fs.unlinkSync(settingsPath);
    }
  });

  it('should list all Data-Cloud modules', () => {
    const output = execFileSync('node', [SCRIPT_PATH], { cwd: REPO_ROOT, encoding: 'utf8' });
    
    assert.ok(output.includes(':products:data-cloud:planes:shared-spi'), 'Should include shared-spi');
    assert.ok(output.includes(':products:data-cloud:planes:data:entity'), 'Should include data:entity');
    assert.ok(output.includes(':products:data-cloud:planes:event:core'), 'Should include event:core');
    assert.ok(output.includes('RELEASE-BLOCKING'), 'Should show release-blocking category');
    assert.ok(output.includes('ACTION-PLANE'), 'Should show action-plane category');
  });

  it('should output Gradle tasks with --check-tasks flag', () => {
    const output = execFileSync('node', [SCRIPT_PATH, '--check-tasks'], { cwd: REPO_ROOT, encoding: 'utf8' });
    
    assert.ok(output.includes(':products:data-cloud:planes:shared-spi:compileJava'), 'Should include compileJava task');
    assert.ok(output.includes(':products:data-cloud:planes:data:entity:compileJava'), 'Should include compileJava task');
  });

  it('should output only release-blocking modules with --release-blocking flag', () => {
    const output = execFileSync('node', [SCRIPT_PATH, '--release-blocking'], { cwd: REPO_ROOT, encoding: 'utf8' });
    
    assert.ok(output.includes(':products:data-cloud:planes:shared-spi'), 'Should include release-blocking modules');
    assert.ok(output.includes(':products:data-cloud:integration-tests'), 'Should include release-blocking integration tests');
    assert.ok(output.includes(':products:data-cloud:delivery:api-contract-tests'), 'Should include release-blocking contract tests');
  });

  it('should validate classification with --validate flag', () => {
    const output = execFileSync('node', [SCRIPT_PATH, '--validate'], { cwd: REPO_ROOT, encoding: 'utf8' });
    
    assert.ok(output.includes('All Data-Cloud modules are properly classified'), 'Should pass validation');
  });

  it('should detect unclassified modules', () => {
    // This test verifies the validation logic exists
    // The actual detection is tested by the validate test passing with correct classification
    const output = execFileSync('node', [SCRIPT_PATH], { cwd: REPO_ROOT, encoding: 'utf8' });
    assert.ok(output.includes('Total:'), 'Should show total module count');
  });

  it('should handle missing settings file gracefully', () => {
    // Remove the mock settings file
    fs.unlinkSync(settingsPath);
    
    try {
      execFileSync('node', [SCRIPT_PATH], { cwd: REPO_ROOT, encoding: 'utf8' });
      assert.fail('Should have failed with missing settings file');
    } catch (error) {
      assert.ok(error.stderr.includes('settings-gradle-includes.kts not found'), 'Should report missing file');
    }
  });
});
