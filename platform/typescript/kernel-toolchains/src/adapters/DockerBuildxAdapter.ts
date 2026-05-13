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
 * Docker Buildx adapter for container image builds
 */
export class DockerBuildxAdapter implements ToolchainAdapter {
  readonly id = 'docker-buildx';
  readonly supportedPhases: ProductLifecyclePhase[] = ['package', 'deploy'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'operator', 'web'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const dockerfilePath = surfaceConfig.dockerfilePath as string || './Dockerfile';
    const contextPath = surfaceConfig.dockerContext as string || '.';
    const imageName = surfaceConfig.imageName as string || `${context.productId}-${context.surface}:${context.phase}`;

    const command = ['docker', 'buildx', 'build', '-f', dockerfilePath, '-t', imageName, contextPath];

    return [
      {
        id: `docker-${phase}`,
        description: `Build Docker image ${imageName}`,
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

    context.logger.info(`Executing Docker Buildx: ${step.command.join(' ')}`);

    try {
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;
      const artifactPaths = this.extractArtifacts(stdout, context);

      context.logger.info(`Docker Buildx execution completed in ${durationMs}ms`);

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
        artifacts: artifactPaths,
        durationMs,
      };
    } catch (error) {
      const durationMs = Date.now() - startTime;
      const execError = error as { message?: string; code?: number; stdout?: string; stderr?: string };
      context.logger.error(`Docker Buildx execution failed: ${execError.message}`);

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

  private extractArtifacts(_stdout: string, _context: ToolchainAdapterContext): string[] {
    // TODO: Parse Docker Buildx output to extract image references
    return [];
  }
}
