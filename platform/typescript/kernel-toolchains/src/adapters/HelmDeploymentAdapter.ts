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
 * Helm adapter for production deployment
 */
export class HelmDeploymentAdapter implements ToolchainAdapter {
  readonly id = 'helm';
  readonly supportedPhases: ProductLifecyclePhase[] = ['deploy', 'verify', 'rollback'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'web', 'worker', 'operator'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const namespace = surfaceConfig.namespace as string || 'default';
    const releaseName = surfaceConfig.releaseName as string || `${context.productId}-${context.surface}`;
    const chartPath = surfaceConfig.chartPath as string || './helm';
    const valuesFile = surfaceConfig.valuesFile as string || 'values.yaml';
    const action = this.mapPhaseToAction(phase);

    const command = ['helm', action, releaseName, chartPath, '-n', namespace, '-f', valuesFile];

    return [
      {
        id: `helm-${phase}`,
        description: `Run Helm ${action} for ${releaseName}`,
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

    context.logger.info(`Executing Helm: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;

      context.logger.info(`Helm execution completed in ${durationMs}ms`);

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
      context.logger.error(`Helm execution failed: ${execError.message}`);

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
    return {
      status: 'valid',
      errors: [],
      missingArtifacts: [],
      unexpectedArtifacts: [],
    };
  }

  private mapPhaseToAction(phase: ProductLifecyclePhase): string {
    const actionMap: Record<ProductLifecyclePhase, string> = {
      deploy: 'upgrade',
      operate: 'status',
      rollback: 'rollback',
      dev: 'upgrade',
      validate: 'status',
      test: 'status',
      build: 'build',
      package: 'package',
      release: 'upgrade',
      verify: 'test',
      promote: 'upgrade',
      retire: 'uninstall',
      create: 'install',
      bootstrap: 'install',
    };
    return actionMap[phase];
  }
}
