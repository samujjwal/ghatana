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
import {
  createCommandObservability,
  createDryRunObservability,
  createToolchainExecutionResult,
  truncateToolchainOutput,
} from '../execution/ToolchainExecutionResultFactory.js';

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
        id: `docker-buildx-package-${context.surface.type}`,
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
      return createToolchainExecutionResult(context, {
        status: 'skipped',
        steps: [{ stepId: step.id, status: 'skipped', durationMs: 0 }],
        artifacts: [],
        durationMs: 0,
        observability: createDryRunObservability(step.id),
      });
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
      return createToolchainExecutionResult(context, {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: truncateToolchainOutput(commandResult.stdout),
            stderr: truncateToolchainOutput(commandResult.stderr),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
        failure: {
          stepId: step.id,
          message: `docker buildx build exited with code ${commandResult.exitCode}`,
          cause: truncateToolchainOutput(commandResult.stderr),
        },
        observability: createCommandObservability(step.id, commandResult, durationMs),
      });
    }

    const validation = await this.validateOutputs(context);
    const artifacts = await this.extractArtifacts(context);

    if (validation.status === 'invalid') {
      return createToolchainExecutionResult(context, {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: truncateToolchainOutput(commandResult.stdout),
            stderr: truncateToolchainOutput(commandResult.stderr),
            durationMs,
          },
        ],
        artifacts,
        durationMs,
        failure: {
          stepId: step.id,
          message: validation.errors.map((e) => e.message).join('; '),
        },
        observability: createCommandObservability(step.id, commandResult, durationMs),
      });
    }

    context.logger.info(`Container image build succeeded in ${durationMs}ms`);
    return createToolchainExecutionResult(context, {
      status: 'succeeded',
      steps: [
        {
          stepId: step.id,
          status: 'succeeded',
          exitCode: 0,
          stdout: truncateToolchainOutput(commandResult.stdout),
          stderr: truncateToolchainOutput(commandResult.stderr),
          durationMs,
        },
      ],
      artifacts,
      durationMs,
      evidenceRefs: artifacts.map((artifact) => `container-image:${artifact}`),
      observability: createCommandObservability(step.id, commandResult, durationMs),
    });
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const { imageRef } = this.resolveImageRef(context);
    const result = await this.commandRunner.run(
      'docker',
      ['image', 'inspect', '--format', '{{json .RepoDigests}}|{{.Id}}', imageRef],
      { cwd: this.repoRoot, env: { ...process.env } },
    );

    if (result.exitCode !== 0 || result.stdout.trim().length === 0) {
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
    const { imageRef } = this.resolveImageRef(context);

    const idResult = await this.commandRunner.run(
      'docker',
      ['image', 'inspect', '--format', '{{json .RepoDigests}}|{{.Id}}', imageRef],
      { cwd: this.repoRoot, env: { ...process.env } },
    );

    if (idResult.exitCode === 0 && idResult.stdout.trim().length > 0) {
      return [this.resolveDigestArtifactRef(imageRef, idResult.stdout)];
    }
    return [imageRef];
  }

  /** Resolve image, tag, dockerfile, and context from adapter context config. */
  private resolveConfig(context: ToolchainAdapterContext): {
    image: string;
    tag: string;
    dockerfile: string;
    dockerContext: string;
  } {
    const cfg = context.surfaceConfig;

    const image = this.requireConfigString(cfg, 'image');
    const tag = this.requireConfigString(cfg, 'tag');
    const rawDockerfile = this.requireConfigString(cfg, 'dockerfile');
    const rawContext = this.requireConfigString(cfg, 'context');

    const dockerfile = path.isAbsolute(rawDockerfile)
      ? rawDockerfile
      : path.join(this.repoRoot, rawDockerfile);

    const dockerContext = path.isAbsolute(rawContext)
      ? rawContext
      : path.join(this.repoRoot, rawContext);

    return { image, tag, dockerfile, dockerContext };
  }

  private requireConfigString(config: Record<string, unknown>, key: string): string {
    const value = config[key];
    if (typeof value !== 'string' || value.trim().length === 0) {
      throw new Error(`DockerBuildxAdapter requires surfaceConfig.${key}`);
    }
    return value;
  }

  private resolveImageRef(context: ToolchainAdapterContext): { imageRef: string } {
    const { image, tag } = this.resolveConfig(context);
    return { imageRef: `${image}:${tag}` };
  }

  private resolveDigestArtifactRef(imageRef: string, inspectOutput: string): string {
    const [repoDigestsRaw] = inspectOutput.trim().split('|');
    if (!repoDigestsRaw) {
      return imageRef;
    }

    try {
      const repoDigests = JSON.parse(repoDigestsRaw) as unknown;
      if (Array.isArray(repoDigests)) {
        const digestRef = repoDigests.find((value): value is string =>
          typeof value === 'string' && value.includes('@sha256:'),
        );
        return digestRef ?? imageRef;
      }
    } catch {
      return imageRef;
    }

    return imageRef;
  }

  /** Build `--build-arg KEY=VALUE` args array from surfaceConfig.buildArgs. */
  private buildArgs(context: ToolchainAdapterContext): string[] {
    const buildArgs = context.surfaceConfig.buildArgs as Record<string, string> | undefined;
    if (!buildArgs) return [];
    return Object.entries(buildArgs).flatMap(([k, v]) => ['--build-arg', `${k}=${v}`]);
  }
}
