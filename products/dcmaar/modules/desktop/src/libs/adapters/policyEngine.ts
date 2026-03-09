/**
 * Policy engine for compliance enforcement and operation validation.
 * Supports HIPAA, SOC2, and custom policy rules.
 */

import type { ControlCommand, PolicyConfig } from './types';

export interface PolicyRule {
  id: string;
  name: string;
  category: 'security' | 'compliance' | 'operational';
  evaluate: (context: PolicyContext) => Promise<PolicyResult>;
}

export interface PolicyContext {
  command?: ControlCommand;
  config?: PolicyConfig;
  metadata?: Record<string, unknown>;
}

export interface PolicyResult {
  allowed: boolean;
  reason?: string;
  violations?: string[];
}

export interface PolicyEngineConfig {
  rules: PolicyRule[];
  strictMode?: boolean;
  complianceFrameworks?: ('HIPAA' | 'SOC2' | 'ISO27001')[];
}

export class PolicyEngine {
  private config: PolicyEngineConfig;
  private rules: Map<string, PolicyRule>;

  constructor(config: PolicyEngineConfig) {
    this.config = {
      strictMode: true,
      ...config,
    };
    this.rules = new Map(config.rules.map((r) => [r.id, r]));
    this.registerBuiltInRules();
  }

  async evaluate(context: PolicyContext): Promise<PolicyResult> {
    const results: PolicyResult[] = [];

    for (const rule of this.rules.values()) {
      try {
        let result = await rule.evaluate(context);
        if (!result.allowed && (!result.violations || result.violations.length === 0) && result.reason) {
          result = {
            ...result,
            violations: [result.reason],
          };
        }
        results.push(result);

        if (!result.allowed && this.config.strictMode) {
          return result; // Fail fast in strict mode
        }
      } catch (error) {
        results.push({
          allowed: false,
          reason: `Rule evaluation failed: ${(error as Error).message}`,
          violations: [`Rule evaluation failed: ${(error as Error).message}`],
        });
      }
    }

    const allAllowed = results.every((r) => r.allowed);
    const violations = results
      .filter((r) => !r.allowed)
      .flatMap((r) => r.violations ?? [r.reason ?? 'Unknown violation']);

    return {
      allowed: allAllowed,
      violations: violations.length > 0 ? violations : undefined,
    };
  }

  addRule(rule: PolicyRule): void {
    this.rules.set(rule.id, rule);
  }

  removeRule(ruleId: string): void {
    this.rules.delete(ruleId);
  }

  getRule(ruleId: string): PolicyRule | undefined {
    return this.rules.get(ruleId);
  }

  listRules(): PolicyRule[] {
    return Array.from(this.rules.values());
  }

  private registerBuiltInRules(): void {
    // Rule: Require mTLS for remote sinks
    this.addRule({
      id: 'require-mtls',
      name: 'Require mTLS for Remote Operations',
      category: 'security',
      evaluate: async (ctx) => {
        if (ctx.config?.allowRemote && !ctx.config?.requireMTLS) {
          return {
            allowed: false,
            reason: 'mTLS required for remote operations',
            violations: ['mTLS required for remote operations'],
          };
        }

        return { allowed: true };
      },
    });

    // Rule: Enforce queue size limits
    this.addRule({
      id: 'queue-size-limit',
      name: 'Enforce Queue Size Limits',
      category: 'operational',
      evaluate: async (ctx) => {
        const maxSizeMB = ctx.config?.maxQueueSizeMB ?? 100;
        const currentSizeMB = (ctx.metadata?.queueSizeMB as number) ?? 0;

        if (currentSizeMB > maxSizeMB) {
          return {
            allowed: false,
            reason: `Queue size ${currentSizeMB}MB exceeds limit ${maxSizeMB}MB`,
            violations: [`Queue size ${currentSizeMB}MB exceeds limit ${maxSizeMB}MB`],
          };
        }

        return { allowed: true };
      },
    });

    // Rule: HIPAA - Audit all access
    if (this.config.complianceFrameworks?.includes('HIPAA')) {
      this.addRule({
        id: 'hipaa-audit-access',
        name: 'HIPAA: Audit All Access',
        category: 'compliance',
        evaluate: async (ctx) => {
          if (ctx.command && !ctx.metadata?.auditLogged) {
            return {
              allowed: false,
              reason: 'HIPAA requires all access to be audited',
              violations: ['HIPAA requires all access to be audited'],
            };
          }

          return { allowed: true };
        },
      });
    }

    // Rule: SOC2 - Encryption at rest
    if (this.config.complianceFrameworks?.includes('SOC2')) {
      this.addRule({
        id: 'soc2-encryption-at-rest',
        name: 'SOC2: Encryption at Rest',
        category: 'compliance',
        evaluate: async (ctx) => {
          if (ctx.metadata?.encryptionEnabled === false) {
            return {
              allowed: false,
              reason: 'SOC2 requires encryption at rest',
              violations: ['SOC2 requires encryption at rest'],
            };
          }

          return { allowed: true };
        },
      });
    }
  }
}

export const createPolicyEngine = (config: PolicyEngineConfig): PolicyEngine => {
  return new PolicyEngine(config);
};
