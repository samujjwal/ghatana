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
 * Terraform adapter for infrastructure deployment
 */
export class TerraformDeploymentAdapter implements ToolchainAdapter {
  readonly id = 'terraform';
  readonly supportedPhases: ProductLifecyclePhase[] = ['deploy', 'verify', 'rollback'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'web', 'worker', 'operator'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const workspace = surfaceConfig.workspace as string || 'default';
    const configPath = surfaceConfig.configPath as string || '.';
    const variablesFile = surfaceConfig.variablesFile as string || 'terraform.tfvars';
    const action = this.mapPhaseToAction(phase);

    const command = ['terraform', action, '-chdir', configPath, '-var-file', variablesFile];

    if (workspace !== 'default') {
      command.push('-workspace', workspace);
    }

    return [
      {
        id: `terraform-${phase}`,
        description: `Run Terraform ${action} in workspace ${workspace}`,
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

    context.logger.info(`Executing Terraform: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;

      context.logger.info(`Terraform execution completed in ${durationMs}ms`);

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
      context.logger.error(`Terraform execution failed: ${execError.message}`);

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
      deploy: 'apply',
      operate: 'show',
      rollback: 'destroy',
      dev: 'apply',
      validate: 'plan',
      test: 'plan',
      build: 'plan',
      package: 'plan',
      release: 'apply',
      verify: 'apply',
      promote: 'apply',
      retire: 'destroy',
      create: 'apply',
      bootstrap: 'init',
    };
    return actionMap[phase];
  }
}
