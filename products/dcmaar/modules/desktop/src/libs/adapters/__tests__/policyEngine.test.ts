/**
 * Unit tests for policy engine and compliance rules.
 */

import { describe, it, expect } from 'vitest';
import { createPolicyEngine } from '../policyEngine';
import type { PolicyContext } from '../policyEngine';

describe('PolicyEngine', () => {
  it('should allow operations when all rules pass', async () => {
    const engine = createPolicyEngine({
      rules: [],
      strictMode: true,
    });

    const context: PolicyContext = {
      config: {
        allowRemote: true,
        requireMTLS: true,
      },
    };

    const result = await engine.evaluate(context);

    expect(result.allowed).toBe(true);
    expect(result.violations).toBeUndefined();
  });

  it('should enforce mTLS requirement', async () => {
    const engine = createPolicyEngine({
      rules: [],
      strictMode: true,
    });

    const context: PolicyContext = {
      config: {
        allowRemote: true,
        requireMTLS: false,
      },
    };

    const result = await engine.evaluate(context);

    expect(result.allowed).toBe(false);
    expect(result.violations).toEqual(['mTLS required for remote operations']);
  });

  it('should enforce queue size limits', async () => {
    const engine = createPolicyEngine({
      rules: [],
      strictMode: true,
    });

    const context: PolicyContext = {
      config: {
        allowRemote: false,
        requireMTLS: false,
        maxQueueSizeMB: 50,
      },
      metadata: {
        queueSizeMB: 75,
      },
    };

    const result = await engine.evaluate(context);

    expect(result.allowed).toBe(false);
    expect(result.violations).toEqual(['Queue size 75MB exceeds limit 50MB']);
  });

  it('should enforce HIPAA audit requirements', async () => {
    const engine = createPolicyEngine({
      rules: [],
      strictMode: true,
      complianceFrameworks: ['HIPAA'],
    });

    const context: PolicyContext = {
      command: {
        id: 'cmd-1',
        category: 'config',
        payload: {},
        metadata: {
          issuedBy: 'test',
          issuedAt: new Date().toISOString(),
          priority: 'medium',
        },
      },
      metadata: {
        auditLogged: false,
      },
    };

    const result = await engine.evaluate(context);

    expect(result.allowed).toBe(false);
    expect(result.violations).toEqual(['HIPAA requires all access to be audited']);
  });

  it('should enforce SOC2 encryption requirements', async () => {
    const engine = createPolicyEngine({
      rules: [],
      strictMode: true,
      complianceFrameworks: ['SOC2'],
    });

    const context: PolicyContext = {
      metadata: {
        encryptionEnabled: false,
      },
    };

    const result = await engine.evaluate(context);

    expect(result.allowed).toBe(false);
    expect(result.violations).toEqual(['SOC2 requires encryption at rest']);
  });

  it('should allow adding custom rules', async () => {
    const engine = createPolicyEngine({
      rules: [],
    });

    engine.addRule({
      id: 'custom-rule',
      name: 'Custom Test Rule',
      category: 'operational',
      evaluate: async () => ({
        allowed: false,
        reason: 'Custom rule violation',
      }),
    });

    const result = await engine.evaluate({});

    expect(result.allowed).toBe(false);
    expect(result.violations).toEqual(['Custom rule violation']);
  });
});
