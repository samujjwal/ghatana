/**
 * Config Compiler
 *
 * Main compiler that orchestrates code generation from PageConfig.
 *
 * @packageDocumentation
 */

import type {
  Compiler,
  CompilerOptions,
  CompilationResult,
  ValidationResult,
} from '../types';
import {
  PageConfigSchema,
  IntentConfigSchema,
  RequirementConfigSchema,
  type PageConfig,
} from '@yappc/config-schema';
import { CodeGenerator } from './CodeGenerator';
import { CanvasGenerator } from './CanvasGenerator';

/**
 * Config Compiler implementation
 */
export class ConfigCompiler implements Compiler {
  name = 'ConfigCompiler';
  version = '1.0.0';

  private codeGenerator = new CodeGenerator();
  private canvasGenerator = new CanvasGenerator();

  /**
   * Compile config to artifacts
   */
  async compile(
    config: unknown,
    options: CompilerOptions
  ): Promise<CompilationResult> {
    const validationResult = this.validate(config);

    if (!validationResult.valid) {
      return {
        success: false,
        context: {
          compilationId: '',
          sourceConfig: config,
          options,
          startedAt: new Date(),
          errors: validationResult.errors.map((msg) => ({
            code: 'VALIDATION_ERROR',
            message: msg,
            severity: 'error' as const,
          })),
          warnings: [],
          artifacts: [],
          metadata: {},
        },
        artifacts: [],
      };
    }

    // Actual compilation logic
    const compilationId = `compilation-${Date.now()}`;
    const startedAt = new Date();
    const artifacts: any[] = [];
    const warnings: Array<{
      code: string;
      message: string;
      severity: 'warning';
    }> = [];

    const cfg = config as Record<string, unknown>;

    // Generate code artifacts if it's a PageConfig
    if (cfg.components && Array.isArray(cfg.components)) {
      try {
        const codeArtifacts = await this.codeGenerator.generateFromPageConfig(
          config as PageConfig,
          { includeImports: true, includeComments: true }
        );
        artifacts.push(...codeArtifacts);
      } catch (error) {
        warnings.push({
          code: 'CODE_GEN_WARNING',
          message: error instanceof Error ? error.message : String(error),
          severity: 'warning' as const,
        });
      }

      // Generate canvas scene
      try {
        const canvasArtifact =
          await this.canvasGenerator.generateFromPageConfig(
            config as PageConfig,
            { layout: 'auto' }
          );
        artifacts.push(canvasArtifact);
      } catch (error) {
        warnings.push({
          code: 'CANVAS_GEN_WARNING',
          message: error instanceof Error ? error.message : String(error),
          severity: 'warning' as const,
        });
      }
    }

    return {
      success: true,
      context: {
        compilationId,
        sourceConfig: config,
        options,
        startedAt,
        errors: [],
        warnings: warnings as any,
        artifacts,
        metadata: {
          artifactCount: artifacts.length,
          artifactTypes: [...new Set(artifacts.map((a) => a.type))],
        },
      },
      artifacts,
    };
  }

  /**
   * Validate config before compilation
   */
  validate(config: unknown): ValidationResult {
    if (!config || typeof config !== 'object') {
      return {
        valid: false,
        errors: ['Config must be an object'],
      };
    }

    const cfg = config as Record<string, unknown>;

    // Determine config type and validate with appropriate schema
    if (cfg.components && Array.isArray(cfg.components)) {
      // Likely a PageConfig
      const result = PageConfigSchema.safeParse(config);
      if (result.success) {
        return { valid: true, errors: [] };
      } else {
        return {
          valid: false,
          errors: result.error.issues.map((issue) => issue.message),
        };
      }
    } else if (cfg.intent && typeof cfg.intent === 'string') {
      // Likely an IntentConfig
      const result = IntentConfigSchema.safeParse(config);
      if (result.success) {
        return { valid: true, errors: [] };
      } else {
        return {
          valid: false,
          errors: result.error.issues.map((issue) => issue.message),
        };
      }
    } else if (cfg.title && cfg.acceptanceCriteria) {
      // Likely a RequirementConfig
      const result = RequirementConfigSchema.safeParse(config);
      if (result.success) {
        return { valid: true, errors: [] };
      } else {
        return {
          valid: false,
          errors: result.error.issues.map((issue) => issue.message),
        };
      }
    }

    // Fallback to basic validation
    if (!cfg.id || typeof cfg.id !== 'string') {
      return {
        valid: false,
        errors: ['Config must have an id field'],
      };
    }

    if (!cfg.version || typeof cfg.version !== 'string') {
      return {
        valid: false,
        errors: ['Config must have a version field'],
      };
    }

    return {
      valid: true,
      errors: [],
    };
  }

  /**
   * Get supported config types
   */
  getSupportedTypes(): string[] {
    return ['PageConfig', 'IntentConfig', 'RequirementConfig'];
  }
}
