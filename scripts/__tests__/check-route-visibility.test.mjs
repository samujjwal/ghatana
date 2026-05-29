#!/usr/bin/env node

/**
 * Tests for check-route-visibility.mjs
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'fs';
import { join } from 'path';
import { execSync } from 'child_process';

const TEST_DIR = join(process.cwd(), '.tmp-test-route-visibility');

describe('check-route-visibility', () => {
  beforeEach(() => {
    if (existsSync(TEST_DIR)) {
      rmSync(TEST_DIR, { recursive: true, force: true });
    }
    mkdirSync(TEST_DIR, { recursive: true });
  });

  afterEach(() => {
    if (existsSync(TEST_DIR)) {
      rmSync(TEST_DIR, { recursive: true, force: true });
    }
  });

  it('should pass when all routes have correct visibility semantics', () => {
    const configDir = join(TEST_DIR, 'products', 'phr', 'config');
    mkdirSync(configDir, { recursive: true });
    
    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          description: 'Patient dashboard',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          stability: 'stable',
          discoverable: true
        },
        {
          path: '/hidden-route',
          label: 'Hidden Route',
          description: 'Hidden route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          stability: 'hidden',
          discoverable: false
        }
      ],
      roleOrder: { patient: 0 }
    }, null, 2));

    const result = execSync(
      `node scripts/check-route-visibility.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('All 2 routes have correct visibility semantics');
  });

  it('should fail when hidden route is discoverable', () => {
    const configDir = join(TEST_DIR, 'products', 'phr', 'config');
    mkdirSync(configDir, { recursive: true });
    
    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/hidden-route',
          label: 'Hidden Route',
          description: 'Hidden route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          stability: 'hidden',
          discoverable: true
        }
      ],
      roleOrder: { patient: 0 }
    }, null, 2));

    try {
      execSync(
        `node scripts/check-route-visibility.mjs`,
        { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
      );
      expect.fail('Should have thrown an error');
    } catch (error) {
      expect(error.stdout).toContain('Hidden route is discoverable');
    }
  });

  it('should fail when stable route is not discoverable', () => {
    const configDir = join(TEST_DIR, 'products', 'phr', 'config');
    mkdirSync(configDir, { recursive: true });
    
    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/dashboard',
          label: 'Dashboard',
          description: 'Patient dashboard',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          stability: 'stable',
          discoverable: false
        }
      ],
      roleOrder: { patient: 0 }
    }, null, 2));

    try {
      execSync(
        `node scripts/check-route-visibility.mjs`,
        { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
      );
      expect.fail('Should have thrown an error');
    } catch (error) {
      expect(error.stdout).toContain('Stable route is not discoverable');
    }
  });

  it('should warn when preview route lacks explicit discoverable flag', () => {
    const configDir = join(TEST_DIR, 'products', 'phr', 'config');
    mkdirSync(configDir, { recursive: true });
    
    writeFileSync(join(configDir, 'phr-route-contract.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/preview-route',
          label: 'Preview Route',
          description: 'Preview route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          stability: 'preview'
        }
      ],
      roleOrder: { patient: 0 }
    }, null, 2));

    const result = execSync(
      `node scripts/check-route-visibility.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('Warnings (1)');
    expect(result).toContain('Preview route lacks explicit discoverable flag');
  });

  it('should handle missing route contract gracefully', () => {
    const result = execSync(
      `node scripts/check-route-visibility.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('PHR route contract not found');
  });
});
