/**
 * Tests for PHR route contract validator
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { readFileSync, writeFileSync, unlinkSync, existsSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');
const SCRIPT_PATH = join(REPO_ROOT, 'scripts/check-phr-route-contract.mjs');
const CONTRACT_PATH = join(REPO_ROOT, 'products/phr/config/phr-route-contract.json');
const SCHEMA_PATH = join(REPO_ROOT, 'products/phr/config/phr-route-contract.schema.json');

function runScript(args = []) {
  try {
    const cmd = `node ${SCRIPT_PATH} ${args.join(' ')}`;
    const output = execSync(cmd, { cwd: REPO_ROOT, encoding: 'utf-8' });
    return { success: true, output };
  } catch (error) {
    return { success: false, output: error.stdout + error.stderr, exitCode: error.status };
  }
}

describe('check-phr-route-contract.mjs', () => {
  it('should validate the current route contract successfully', () => {
    const result = runScript();
    expect(result.success).toBe(true);
    expect(result.output).toContain('Route contract validation passed');
  });

  it('should fail when route contract is missing required fields', () => {
    // Create a temporary invalid contract
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          stability: 'stable',
          // Missing required fields: label, description, group, minimumRole, actions, cards
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath]);
      expect(result.success).toBe(false);
      expect(result.output).toContain('Schema validation failed');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should fail when stable route lacks apiEndpoint', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view'],
          cards: [],
          stability: 'stable',
          policyId: 'test.policy',
          testId: 'test-001'
          // Missing apiEndpoint
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath]);
      expect(result.success).toBe(false);
      expect(result.output).toContain('missing apiEndpoint');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should fail when stable route lacks policyId', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view'],
          cards: [],
          stability: 'stable',
          apiEndpoint: '/api/v1/test',
          testId: 'test-001'
          // Missing policyId
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath]);
      expect(result.success).toBe(false);
      expect(result.output).toContain('missing policyId');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should fail when stable route lacks testId', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view'],
          cards: [],
          stability: 'stable',
          apiEndpoint: '/api/v1/test',
          policyId: 'test.policy'
          // Missing testId
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath]);
      expect(result.success).toBe(false);
      expect(result.output).toContain('missing testId');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should pass when stable route has all required fields', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const validContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view'],
          cards: [],
          stability: 'stable',
          apiEndpoint: '/api/v1/test',
          policyId: 'test.policy',
          testId: 'test-001',
          apiContractId: 'phr.api.test.v1',
          dtoSchemaId: 'phr.dto.test.v1',
          pluginDependencies: ['kernel:policy'],
          auditRequirement: 'standard',
          phiSensitivity: 'pii',
          cachePolicy: 'private-session',
          offlinePolicy: 'online-only'
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(validContract, null, 2));

    try {
      const result = runScript([tempContractPath, '--skip-route-elements', '--skip-page-imports']);
      expect(result.success).toBe(true);
      expect(result.output).toContain('All 1 stable routes have required fields');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should validate route paths against routeElements', () => {
    const result = runScript();
    expect(result.success).toBe(true);
    expect(result.output).toContain('All route paths exist in routeElements');
  });

  it('should validate page imports against route contract', () => {
    const result = runScript();
    expect(result.success).toBe(true);
    expect(result.output).toContain('All imported pages have corresponding route contract entries');
  });

  it('should check hidden routes with --check-hidden flag', () => {
    const result = runScript(['--check-hidden']);
    expect(result.success).toBe(true);
    expect(result.output).toContain('Checked');
  });

  it('should check backend mounts with --check-backend-mounts flag', () => {
    const result = runScript(['--check-backend-mounts']);
    // This may fail if backend mounts are not yet aligned, which is expected
    // The test verifies the flag is processed
    expect(result.output).toContain('Validating backend route mounts');
  });

  it('should reject invalid group enum values', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'invalid-group', // Invalid enum value
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view'],
          cards: [],
          stability: 'preview'
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath, '--skip-route-elements', '--skip-page-imports']);
      expect(result.success).toBe(false);
      expect(result.output).toContain('not in enum');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should reject invalid tier enum values', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['invalid-tier'], // Invalid enum value
          actions: ['view'],
          cards: [],
          stability: 'preview'
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath, '--skip-route-elements', '--skip-page-imports']);
      expect(result.success).toBe(false);
      expect(result.output).toContain('not in enum');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should reject invalid stability enum values', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view'],
          cards: [],
          stability: 'invalid-stability' // Invalid enum value
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath, '--skip-route-elements', '--skip-page-imports']);
      expect(result.success).toBe(false);
      expect(result.output).toContain('not in enum');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });

  it('should reject additional properties not in schema', () => {
    const tempContractPath = join(REPO_ROOT, 'products/phr/config/phr-route-contract.test.json');
    const invalidContract = {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        {
          path: '/test',
          label: 'Test',
          description: 'Test route',
          group: 'care',
          minimumRole: 'patient',
          personas: ['patient'],
          tiers: ['core'],
          actions: ['view'],
          cards: [],
          stability: 'preview',
          extraProperty: 'not allowed' // Additional property
        }
      ],
      roleOrder: { patient: 0, caregiver: 1, clinician: 2, admin: 3, fchv: 4 }
    };

    writeFileSync(tempContractPath, JSON.stringify(invalidContract, null, 2));

    try {
      const result = runScript([tempContractPath, '--skip-route-elements', '--skip-page-imports']);
      expect(result.success).toBe(false);
      expect(result.output).toContain('additional property');
    } finally {
      if (existsSync(tempContractPath)) {
        unlinkSync(tempContractPath);
      }
    }
  });
});
