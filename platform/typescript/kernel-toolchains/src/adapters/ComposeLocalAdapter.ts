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
 * Docker Compose adapter for local deployment
 */
export class ComposeLocalAdapter implements ToolchainAdapter {
  readonly id = 'compose-local';
  readonly supportedPhases: ProductLifecyclePhase[] = ['deploy', 'operate', 'rollback'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'web', 'worker', 'operator'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const composeFile = surfaceConfig.composeFile as string || 'docker-compose.yaml';
    const action = this.mapPhaseToAction(phase);

    const command = ['docker', 'compose', '-f', composeFile, action];

    return [
      {
        id: `compose-${phase}`,
        description: `Run Docker Compose ${action}`,
        command,
        workingDirectory: context.outputDir || process.cwd(),
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

    context.logger.info(`Executing Docker Compose: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;

      context.logger.info(`Docker Compose execution completed in ${durationMs}ms`);

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
        artifacts: [],
        durationMs,
      };
    } catch (error) {
      const durationMs = Date.now() - startTime;
      const execError = error as { message?: string; code?: number; stdout?: string; stderr?: string };
      context.logger.error(`Docker Compose execution failed: ${execError.message}`);

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

  private mapPhaseToAction(phase: ProductLifecyclePhase): string {
    const actionMap: Record<ProductLifecyclePhase, string> = {
      deploy: 'up -d',
      operate: 'ps',
      rollback: 'down',
      dev: 'up',
      validate: 'ps',
      test: 'ps',
      build: 'build',
      package: 'build',
      release: 'up -d',
      verify: 'ps',
      promote: 'up -d',
      retire: 'down',
      create: 'up -d',
      bootstrap: 'up -d',
    };
    return actionMap[phase];
  }
}
