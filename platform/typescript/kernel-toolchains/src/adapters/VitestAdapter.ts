import { promisify } from 'node:util';
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
  ToolchainTestResults,
  AdapterPreflightResult,
  LifecycleFailureClassifier,
} from '../ToolchainAdapter.js';
import {
  createDefaultPreflightResult,
  createDefaultFailureClassifier,
} from '../ToolchainAdapter.js';

const execAsync = promisify(require('node:child_process').exec);
const NO_VITEST_ARTIFACTS: readonly string[] = Object.freeze([]);

/**
 * Vitest adapter for running unit/integration tests
 */
export class VitestAdapter implements ToolchainAdapter {
  readonly id = 'vitest';
  readonly supportedPhases: ProductLifecyclePhase[] = ['test', 'validate'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['web', 'backend-api', 'worker', 'operator', 'sdk'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const packagePath = surfaceConfig.packagePath as string;

    if (!packagePath) {
      throw new Error('packagePath is required for VitestAdapter');
    }

    const script = this.mapPhaseToScript(phase, surfaceConfig);
    const command = ['pnpm', '--dir', packagePath, script];

    return [
      {
        id: `vitest-${phase}`,
        description: `Run Vitest ${script} for ${packagePath}`,
        command,
        workingDirectory: packagePath,
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const startTime = Date.now();
    const plan = await this.plan(context);
    const step = plan[0];

    if (context.dryRun) {
      context.logger.info(`[DRY-RUN] Would execute: ${step.command.join(' ')}`);
      return {
        status: 'skipped',
        steps: [
          {
            stepId: step.id,
            status: 'skipped',
            durationMs: 0,
          },
        ],
        artifacts: [],
        durationMs: 0,
      };
    }

    context.logger.info(`Executing Vitest: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;
      const testResults = this.parseTestResults(stdout);

      context.logger.info(`Vitest execution completed in ${durationMs}ms: ${testResults.tests} tests, ${testResults.failures} failures`);

      return {
        status: testResults.failures === 0 ? 'succeeded' : 'failed',
        steps: [
          {
            stepId: step.id,
            status: testResults.failures === 0 ? 'succeeded' : 'failed',
            exitCode: testResults.failures === 0 ? 0 : 1,
            stdout: stdout.slice(0, 10000),
            stderr: stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts: this.extractArtifacts(stdout, context),
        testResults,
        durationMs,
      };
    } catch (error) {
      const durationMs = Date.now() - startTime;
      const execError = error as { message?: string; code?: number; stdout?: string; stderr?: string };
      context.logger.error(`Vitest execution failed: ${execError.message}`);

      const failedResult: ToolchainExecutionResult = {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: execError.code || 1,
            stdout: execError.stdout?.slice(0, 10000) || '',
            stderr: execError.stderr?.slice(0, 10000) || '',
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
      };

      if (execError.stderr) {
        failedResult.failure = {
          stepId: step.id,
          message: execError.message || 'Unknown error',
          cause: execError.stderr,
        };
      }

      return failedResult;
    }
  }

  async validateOutputs(_context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // Implementation note: Implement actual validation
    return {
      status: 'valid',
      errors: [],
      missingArtifacts: [],
      unexpectedArtifacts: [],
    };
  }

  async preflight(_context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    return createDefaultPreflightResult();
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createDefaultFailureClassifier(error, this.id);
  }

  private mapPhaseToScript(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    const scriptMap: Record<ProductLifecyclePhase, string> = {
      test: (surfaceConfig.testScript as string) || 'test',
      validate: (surfaceConfig.validateScript as string) || 'test',
      dev: 'test',
      build: 'test',
      package: 'test',
      release: 'test',
      deploy: 'test',
      verify: 'test',
      promote: 'test',
      rollback: 'test',
      operate: 'test',
      retire: 'test',
      create: 'test',
      bootstrap: 'test',
    };
    return scriptMap[phase];
  }

  private parseTestResults(stdout: string): ToolchainTestResults {
    // Parse Vitest output to extract test results
    const testMatch = stdout.match(/Test Files\s+(\d+)\s+passed\s+\((\d+)\)/);
    const tests = testMatch ? parseInt(testMatch[1], 10) : 0;

    const failureMatch = stdout.match(/FAIL\s+(\d+)/);
    const failures = failureMatch ? parseInt(failureMatch[1], 10) : 0;

    const skippedMatch = stdout.match(/SKIP\s+(\d+)/);
    const skipped = skippedMatch ? parseInt(skippedMatch[1], 10) : 0;

    const durationMatch = stdout.match(/Duration\s+(\d+\.?\d*)\s*s/);
    const durationMs = durationMatch ? parseFloat(durationMatch[1]) * 1000 : 0;

    return { tests, failures, skipped, durationMs };
  }

  private extractArtifacts(_stdout: string, _context: ToolchainAdapterContext): string[] {
    // Implementation note: Parse Vitest output to extract artifact paths (coverage reports, etc.)
    return Array.from(NO_VITEST_ARTIFACTS);
  }
}
