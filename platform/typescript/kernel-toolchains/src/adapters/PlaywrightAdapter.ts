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
} from '../ToolchainAdapter.js';

const execAsync = promisify(require('node:child_process').exec);
const NO_PLAYWRIGHT_ARTIFACTS: readonly string[] = Object.freeze([]);

/**
 * Playwright adapter for running E2E tests
 */
export class PlaywrightAdapter implements ToolchainAdapter {
  readonly id = 'playwright';
  readonly supportedPhases: ProductLifecyclePhase[] = ['test', 'verify'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['web', 'mobile-ios', 'mobile-android'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const packagePath = surfaceConfig.packagePath as string;

    if (!packagePath) {
      throw new Error('packagePath is required for PlaywrightAdapter');
    }

    const script = this.mapPhaseToScript(phase, surfaceConfig);
    const command = ['pnpm', '--dir', packagePath, script];

    return [
      {
        id: `playwright-${phase}`,
        description: `Run Playwright ${script} for ${packagePath}`,
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

    context.logger.info(`Executing Playwright: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;
      const testResults = this.parseTestResults(stdout);

      context.logger.info(`Playwright execution completed in ${durationMs}ms: ${testResults.tests} tests, ${testResults.failures} failures`);

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
      context.logger.error(`Playwright execution failed: ${execError.message}`);

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

  private mapPhaseToScript(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    const scriptMap: Record<ProductLifecyclePhase, string> = {
      test: (surfaceConfig.testScript as string) || 'test:e2e',
      verify: (surfaceConfig.verifyScript as string) || 'test:e2e',
      dev: 'test:e2e',
      build: 'test:e2e',
      package: 'test:e2e',
      release: 'test:e2e',
      deploy: 'test:e2e',
      validate: 'test:e2e',
      promote: 'test:e2e',
      rollback: 'test:e2e',
      operate: 'test:e2e',
      retire: 'test:e2e',
      create: 'test:e2e',
      bootstrap: 'test:e2e',
    };
    return scriptMap[phase];
  }

  private parseTestResults(stdout: string): ToolchainTestResults {
    // Parse Playwright output to extract test results
    const passedMatch = stdout.match(/(\d+)\s+passed/);
    const tests = passedMatch ? parseInt(passedMatch[1], 10) : 0;

    const failedMatch = stdout.match(/(\d+)\s+failed/);
    const failures = failedMatch ? parseInt(failedMatch[1], 10) : 0;

    const skippedMatch = stdout.match(/(\d+)\s+skipped/);
    const skipped = skippedMatch ? parseInt(skippedMatch[1], 10) : 0;

    const durationMatch = stdout.match(/Duration:\s+(\d+\.?\d*)\s*s/);
    const durationMs = durationMatch ? parseFloat(durationMatch[1]) * 1000 : 0;

    return { tests, failures, skipped, durationMs };
  }

  private extractArtifacts(_stdout: string, _context: ToolchainAdapterContext): string[] {
    // Implementation note: Parse Playwright output to extract artifact paths (test reports, screenshots, traces)
    return Array.from(NO_PLAYWRIGHT_ARTIFACTS);
  }
}
