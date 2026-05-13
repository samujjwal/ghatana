import { promisify } from 'node:util';
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
} from '../ToolchainAdapter.js';

const execAsync = promisify(require('node:child_process').exec);

/**
 * pnpm Vite React adapter
 */
export class PnpmViteReactAdapter implements ToolchainAdapter {
  readonly id = 'pnpm-vite-react';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['web'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const packagePath = surfaceConfig.packagePath as string;

    if (!packagePath) {
      throw new Error('packagePath is required for PnpmViteReactAdapter');
    }

    const script = this.mapPhaseToScript(phase, surfaceConfig);
    const command = ['pnpm', '--dir', packagePath, script];

    return [
      {
        id: `pnpm-${phase}`,
        description: `Run pnpm ${script} for ${packagePath}`,
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

    context.logger.info(`Executing pnpm: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;

      context.logger.info(`pnpm execution completed in ${durationMs}ms`);

      return {
        status: 'succeeded',
        steps: [
          {
            stepId: step.id,
            status: 'succeeded',
            exitCode: 0,
            stdout: stdout.slice(0, 10000),
            stderr: stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts: this.extractArtifacts(stdout, context),
        durationMs,
      };
    } catch (error) {
      const durationMs = Date.now() - startTime;
      const execError = error as { message?: string; code?: number; stdout?: string; stderr?: string };
      context.logger.error(`pnpm execution failed: ${execError.message}`);

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
    // TODO: Implement actual validation
    return {
      status: 'valid',
      errors: [],
      missingArtifacts: [],
      unexpectedArtifacts: [],
    };
  }

  private mapPhaseToScript(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    const scriptMap: Record<ProductLifecyclePhase, string> = {
      dev: (surfaceConfig.devScript as string) || 'dev',
      validate: (surfaceConfig.validateScript as string) || 'lint',
      test: (surfaceConfig.testScript as string) || 'test',
      build: (surfaceConfig.buildScript as string) || 'build',
      package: (surfaceConfig.packageScript as string) || 'build',
      release: 'build',
      deploy: 'build',
      verify: 'test',
      promote: 'build',
      rollback: 'build',
      operate: 'test',
      retire: 'build',
      create: 'build',
      bootstrap: 'build',
    };
    return scriptMap[phase];
  }

  private extractArtifacts(_stdout: string, _context: ToolchainAdapterContext): string[] {
    // TODO: Parse pnpm output to extract artifact paths
    // For now, return empty array
    return [];
  }
}
