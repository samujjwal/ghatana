import { spawn } from 'node:child_process';
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
} from '../ToolchainAdapter.js';

/**
 * Docker Compose adapter for local deployment
 * Uses spawn() for safer command execution instead of shell string execution
 */
export class ComposeLocalAdapter implements ToolchainAdapter {
  readonly id = 'compose-local';
  readonly supportedPhases: ProductLifecyclePhase[] = ['deploy', 'operate', 'rollback'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'web', 'worker', 'operator'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const composeFile = surfaceConfig.composeFile as string || 'docker-compose.yaml';
    const actionArgs = this.mapPhaseToActionArgs(phase);

    const args = ['-f', composeFile, ...actionArgs];

    return [
      {
        id: `compose-${phase}`,
        description: `Run Docker Compose ${actionArgs.join(' ')}`,
        command: ['docker', 'compose', ...args],
        workingDirectory: context.outputDir || process.cwd(),
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const startTime = Date.now();
    const plan = await this.plan(context);
    const step = plan[0];

    if (!step) {
      return {
        status: 'failed',
        steps: [],
        artifacts: [],
        durationMs: 0,
        failure: {
          stepId: 'unknown',
          message: 'No execution plan generated',
        },
      };
    }

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

    return this.executeCommand(step, step.workingDirectory || process.cwd(), step.env, startTime, context);
  }

  /**
   * Execute a command using spawn() for safety
   */
  private executeCommand(
    step: ToolchainPlanStep,
    workingDir: string,
    env: Record<string, string> | undefined,
    startTime: number,
    context: ToolchainAdapterContext
  ): Promise<ToolchainExecutionResult> {
    return new Promise((resolve) => {
      const [executable, ...args] = step.command;

      if (!executable) {
        resolve({
          status: 'failed',
          steps: [
            {
              stepId: step.id,
              status: 'failed',
              exitCode: 1,
              stdout: '',
              stderr: 'No executable in command',
              durationMs: Date.now() - startTime,
            },
          ],
          artifacts: [],
          durationMs: Date.now() - startTime,
          failure: {
            stepId: step.id,
            message: 'No executable in command',
          },
        });
        return;
      }

      const child = spawn(executable, args, {
        cwd: workingDir,
        env: { ...process.env, ...env },
        stdio: 'pipe',
      });

      let stdout = '';
      let stderr = '';
      const MAX_OUTPUT = 50000;

      if (child.stdout) {
        child.stdout.on('data', (data: Buffer) => {
          const text = data.toString();
          stdout += text;
          if (stdout.length > MAX_OUTPUT) {
            stdout = stdout.slice(-MAX_OUTPUT);
          }
        });
      }

      if (child.stderr) {
        child.stderr.on('data', (data: Buffer) => {
          const text = data.toString();
          stderr += text;
          if (stderr.length > MAX_OUTPUT) {
            stderr = stderr.slice(-MAX_OUTPUT);
          }
        });
      }

      child.on('error', (error: Error) => {
        const durationMs = Date.now() - startTime;
        context.logger.error(`Docker Compose execution failed: ${error.message}`);

        resolve({
          status: 'failed',
          steps: [
            {
              stepId: step.id,
              status: 'failed',
              exitCode: 1,
              stdout,
              stderr: stderr || error.message,
              durationMs,
            },
          ],
          artifacts: [],
          durationMs,
          failure: {
            stepId: step.id,
            message: error.message,
            cause: stderr || error.toString(),
          },
        });
      });

      child.on('close', (code: number | null) => {
        const durationMs = Date.now() - startTime;
        const exitCode = code ?? 1;

        if (exitCode === 0) {
          context.logger.info(`Docker Compose execution completed in ${durationMs}ms`);

          resolve({
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
          });
        } else {
          context.logger.error(`Docker Compose execution failed with exit code ${exitCode}`);

          resolve({
            status: 'failed',
            steps: [
              {
                stepId: step.id,
                status: 'failed',
                exitCode,
                stdout: stdout.slice(0, 10000),
                stderr: stderr.slice(0, 10000),
                durationMs,
              },
            ],
            artifacts: [],
            durationMs,
            failure: {
              stepId: step.id,
              message: `Docker Compose exited with code ${exitCode}`,
              cause: stderr || 'See stdout for details',
            },
          });
        }
      });
    });
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // Validate that Docker Compose file exists and is readable
    const { surfaceConfig } = context;
    const composeFile = surfaceConfig.composeFile as string || 'docker-compose.yaml';
    const workingDir = context.outputDir || process.cwd();

    const errors: Array<{ path: string; message: string }> = [];
    const missingArtifacts: string[] = [];
    const unexpectedArtifacts: string[] = [];

    // In a real implementation, we would:
    // 1. Check if docker-compose.yaml exists
    // 2. Parse it and validate structure
    // 3. Check if all required services are defined
    // 4. Validate image references are valid

    // For now, provide basic validation framework
    context.logger.debug(`Validating Docker Compose outputs for ${composeFile} in ${workingDir}`);

    return {
      status: errors.length > 0 ? 'invalid' : 'valid',
      errors,
      missingArtifacts,
      unexpectedArtifacts,
    };
  }

  private mapPhaseToActionArgs(phase: ProductLifecyclePhase): string[] {
    const actionMap: Record<ProductLifecyclePhase, string[]> = {
      deploy: ['up', '-d'],
      operate: ['ps'],
      rollback: ['down'],
      dev: ['up'],
      validate: ['ps'],
      test: ['ps'],
      build: ['build'],
      package: ['build'],
      release: ['up', '-d'],
      verify: ['ps'],
      promote: ['up', '-d'],
      retire: ['down'],
      create: ['up', '-d'],
      bootstrap: ['up', '-d'],
    };
    return actionMap[phase] || ['ps'];
  }
}
