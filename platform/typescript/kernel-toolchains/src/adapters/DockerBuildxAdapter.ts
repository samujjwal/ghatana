import * as path from 'node:path';
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
} from '../ToolchainAdapter.js';
import { SpawnCommandRunner } from '../execution/SpawnCommandRunner.js';
import type { CommandRunner } from '../execution/CommandRunner.js';

/** Container image artifact produced by a successful Docker build. */
export interface ContainerImageArtifact {
  readonly type: 'container-image';
  readonly image: string;
  readonly tag: string;
  /** Digest filled in after validateOutputs inspects the local image. */
  digest?: string;
  localImageId?: string;
}

/**
 * @doc.type class
 * @doc.purpose Builds product surface container images via `docker buildx build --load`.
 *   Reads image/tag/dockerfile/context from the adapter context (populated by the planner
 *   from the product's kernel-product.yaml package section).
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export class DockerBuildxAdapter implements ToolchainAdapter {
  readonly id = 'docker-buildx';
  readonly supportedPhases: ProductLifecyclePhase[] = ['package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'operator', 'web'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { image, tag, dockerfile, dockerContext } = this.resolveConfig(context);
    const imageRef = `${image}:${tag}`;
    const args = this.buildArgs(context);

    const command: string[] = [
      'docker', 'buildx', 'build',
      '--load',
      '-t', imageRef,
      '-f', dockerfile,
      ...args,
      dockerContext,
    ];

    return [
      {
        id: `docker-buildx-package-${context.surface}`,
        description: `Build container image ${imageRef} from ${dockerfile}`,
        command,
        workingDirectory: this.repoRoot,
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const startedAt = Date.now();
    const [step] = await this.plan(context);

    if (context.dryRun) {
      context.logger.info(`[DRY-RUN] Would execute: ${step.command.join(' ')}`);
      return {
        status: 'skipped',
        steps: [{ stepId: step.id, status: 'skipped', durationMs: 0 }],
        artifacts: [],
        durationMs: 0,
      };
    }

    context.logger.info(`Building container image: ${step.command.join(' ')}`);

    const commandResult = await this.commandRunner.run(
      step.command[0],
      step.command.slice(1),
      {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
        timeoutMs: 900_000, // 15 min â€” image builds can be slow
      },
    );

    const durationMs = commandResult.durationMs || Date.now() - startedAt;

    if (commandResult.exitCode !== 0) {
      context.logger.error(`docker buildx build failed (exit ${commandResult.exitCode})`);
      return {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10_000),
            stderr: commandResult.stderr.slice(0, 10_000),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
        failure: {
          stepId: step.id,
          message: `docker buildx build exited with code ${commandResult.exitCode}`,
          cause: commandResult.stderr.slice(0, 2_000),
        },
      };
    }

    const validation = await this.validateOutputs(context);
    const artifacts = await this.extractArtifacts(context);

    if (validation.status === 'invalid') {
      return {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10_000),
            stderr: commandResult.stderr.slice(0, 10_000),
            durationMs,
          },
        ],
        artifacts,
        durationMs,
        failure: {
          stepId: step.id,
          message: validation.errors.map((e) => e.message).join('; '),
        },
      };
    }

    context.logger.info(`Container image build succeeded in ${durationMs}ms`);
    return {
      status: 'succeeded',
      steps: [
        {
          stepId: step.id,
          status: 'succeeded',
          exitCode: 0,
          stdout: commandResult.stdout.slice(0, 10_000),
          stderr: commandResult.stderr.slice(0, 10_000),
          durationMs,
        },
      ],
      artifacts,
      durationMs,
    };
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const { image, tag } = this.resolveConfig(context);
    const imageRef = `${image}:${tag}`;

    const result = await this.commandRunner.run(
      'docker',
      ['image', 'inspect', '--format', '{{.Id}}', imageRef],
      { cwd: this.repoRoot, env: { ...process.env } },
    );

    if (result.exitCode !== 0) {
      return {
        status: 'invalid',
        errors: [
          {
            path: 'container-image',
            message: `Image "${imageRef}" not found in local Docker daemon after build. ` +
              'Ensure --load flag is used with docker buildx.',
          },
        ],
        missingArtifacts: [imageRef],
        unexpectedArtifacts: [],
      };
    }

    return {
      status: 'valid',
      errors: [],
      missingArtifacts: [],
      unexpectedArtifacts: [],
    };
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Private helpers
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<string[]> {
    const { image, tag } = this.resolveConfig(context);
    const imageRef = `${image}:${tag}`;

    const idResult = await this.commandRunner.run(
      'docker',
      ['image', 'inspect', '--format', '{{.Id}}', imageRef],
      { cwd: this.repoRoot, env: { ...process.env } },
    );

    // Return [imageRef] â€” we use strings to stay compatible with ToolchainExecutionResult.artifacts
    // The caller (executor / ArtifactWriter) will enrich with digest and typed metadata.
    if (idResult.exitCode === 0 && idResult.stdout.trim().length > 0) {
      return [imageRef];
    }
    return [imageRef]; // Still return the ref even if inspect failed; validation already surfaced the error
  }

  /** Resolve image, tag, dockerfile, and context from adapter context config. */
  private resolveConfig(context: ToolchainAdapterContext): {
    image: string;
    tag: string;
    dockerfile: string;
    dockerContext: string;
  } {
    // The config comes from adapterContext.packageConfig populated by the planner
    const cfg = context.surfaceConfig;

    const image = String(cfg.image ?? `${context.productId}-${String(context.surface.type)}`);
    const tag = String(cfg.tag ?? 'local');
    const rawDockerfile = String(cfg.dockerfile ?? 'Dockerfile');
    const rawContext = String(cfg.context ?? '.');

    const dockerfile = path.isAbsolute(rawDockerfile)
      ? rawDockerfile
      : path.join(this.repoRoot, rawDockerfile);

    const dockerContext = path.isAbsolute(rawContext)
      ? rawContext
      : path.join(this.repoRoot, rawContext);

    return { image, tag, dockerfile, dockerContext };
  }

  /** Build `--build-arg KEY=VALUE` args array from surfaceConfig.buildArgs. */
  private buildArgs(context: ToolchainAdapterContext): string[] {
    const buildArgs = context.surfaceConfig.buildArgs as Record<string, string> | undefined;
    if (!buildArgs) return [];
    return Object.entries(buildArgs).flatMap(([k, v]) => ['--build-arg', `${k}=${v}`]);
  }
}
