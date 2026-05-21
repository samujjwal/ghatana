import { promisify } from 'node:util';
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
  AdapterPreflightResult,
  LifecycleFailureClassifier,
} from '../ToolchainAdapter.js';
import {
  createDefaultPreflightResult,
  createDefaultFailureClassifier,
} from '../ToolchainAdapter.js';

const execAsync = promisify(require('node:child_process').exec);

/**
 * Kubernetes adapter for production deployment
 */
export class KubernetesDeploymentAdapter implements ToolchainAdapter {
  readonly id = 'kubernetes';
  readonly supportedPhases: ProductLifecyclePhase[] = ['deploy', 'verify', 'rollback'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'web', 'worker', 'operator'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const namespace = surfaceConfig.namespace as string || 'default';
    const deploymentName = surfaceConfig.deploymentName as string || `${context.productId}-${context.surface}`;
    const action = this.mapPhaseToAction(phase);

    const command = ['kubectl', action, '-n', namespace, deploymentName];

    return [
      {
        id: `kubernetes-${phase}`,
        description: `Run Kubernetes ${action} for ${deploymentName}`,
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

    context.logger.info(`Executing Kubernetes: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;

      context.logger.info(`Kubernetes execution completed in ${durationMs}ms`);

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
      context.logger.error(`Kubernetes execution failed: ${execError.message}`);

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

  async preflight(_context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    return createDefaultPreflightResult();
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createDefaultFailureClassifier(error, this.id);
  }

  private mapPhaseToAction(phase: ProductLifecyclePhase): string {
    const actionMap: Record<ProductLifecyclePhase, string> = {
      deploy: 'apply',
      operate: 'get',
      rollback: 'rollout undo',
      dev: 'apply',
      validate: 'get',
      test: 'get',
      build: 'build',
      package: 'build',
      release: 'apply',
      verify: 'rollout status',
      promote: 'apply',
      retire: 'delete',
      create: 'apply',
      bootstrap: 'apply',
    };
    return actionMap[phase];
  }
}
