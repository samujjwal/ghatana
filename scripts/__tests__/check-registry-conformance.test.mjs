#!/usr/bin/env node

/**
 * Tests for check-registry-conformance.mjs
 * REG-P1-001, REG-P2-004
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync, writeFileSync, unlinkSync, existsSync } from 'fs';
import { join } from 'path';
import { execSync } from 'child_process';

const REGISTRY_PATH = join(process.cwd(), 'config', 'canonical-product-registry.json');
const BACKUP_PATH = join(process.cwd(), 'config', 'canonical-product-registry.json.backup');

describe('check-registry-conformance', () => {
  let originalContent;

  beforeEach(() => {
    // Backup original registry
    if (existsSync(REGISTRY_PATH)) {
      originalContent = readFileSync(REGISTRY_PATH, 'utf-8');
      writeFileSync(BACKUP_PATH, originalContent);
    }
  });

  afterEach(() => {
    // Restore original registry
    if (existsSync(BACKUP_PATH)) {
      writeFileSync(REGISTRY_PATH, readFileSync(BACKUP_PATH, 'utf-8'));
      unlinkSync(BACKUP_PATH);
    }
  });

  it('should pass when implemented surfaces have proper productionReadinessStatus', () => {
    // This test assumes the current registry is valid
    const result = execSync('node scripts/check-registry-conformance.mjs', {
      encoding: 'utf-8',
      cwd: process.cwd()
    });
    expect(result).toContain('✓ Registry conformance validation passed');
  });

  it('should fail when implemented surfaces are marked production-ready with blocked lifecycle', () => {
    // Create a test scenario that would fail REG-P1-001
    const registry = JSON.parse(originalContent);
    
    // Modify YAPPC to have lifecycleExecutionAllowed: true but no productionReadinessStatus
    if (registry.registry.yappc) {
      registry.registry.yappc.lifecycleExecutionAllowed = true;
      delete registry.registry.yappc.productionReadinessStatus;
      delete registry.registry.yappc.productionReadinessReason;
    }
    
    writeFileSync(REGISTRY_PATH, JSON.stringify(registry, null, 2));
    
    try {
      execSync('node scripts/check-registry-conformance.mjs', {
        encoding: 'utf-8',
        cwd: process.cwd()
      });
      expect.fail('Should have failed with REG-P1-001 error');
    } catch (error) {
      expect(error.stdout).toContain('REG-P1-001');
    }
  });

  it('should fail when active product with implemented surfaces has missing conformance', () => {
    // Create a test scenario that would fail REG-P2-004
    const registry = JSON.parse(originalContent);
    
    // Modify PHR to have missing conformance fields
    if (registry.registry.phr) {
      registry.registry.phr.conformance = {
        manifest: true,
        // Missing security, observability, dataAccess
      };
    }
    
    writeFileSync(REGISTRY_PATH, JSON.stringify(registry, null, 2));
    
    try {
      execSync('node scripts/check-registry-conformance.mjs', {
        encoding: 'utf-8',
        cwd: process.cwd()
      });
      expect.fail('Should have failed with REG-P2-004 error');
    } catch (error) {
      expect(error.stdout).toContain('REG-P2-004');
    }
  });

  it('should pass when product has no implemented surfaces', () => {
    const registry = JSON.parse(originalContent);
    
    // Modify a product to have no implemented surfaces
    if (registry.registry.finance) {
      registry.registry.finance.surfaces = [
        { type: 'backend-api', path: 'products/finance', implementationStatus: 'planned' }
      ];
    }
    
    writeFileSync(REGISTRY_PATH, JSON.stringify(registry, null, 2));
    
    const result = execSync('node scripts/check-registry-conformance.mjs', {
      encoding: 'utf-8',
      cwd: process.cwd()
    });
    expect(result).toContain('✓ Registry conformance validation passed');
  });

  it('should pass when product is not active', () => {
    const registry = JSON.parse(originalContent);
    
    // Modify a product to be inactive
    if (registry.registry.finance) {
      registry.registry.finance.metadata = { status: 'planned' };
    }
    
    writeFileSync(REGISTRY_PATH, JSON.stringify(registry, null, 2));
    
    const result = execSync('node scripts/check-registry-conformance.mjs', {
      encoding: 'utf-8',
      cwd: process.cwd()
    });
    expect(result).toContain('✓ Registry conformance validation passed');
  });
});
