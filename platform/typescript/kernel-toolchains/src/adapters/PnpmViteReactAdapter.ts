import { promises as fs } from 'node:fs';
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

/**
 * pnpm Vite React adapter
 */
export class PnpmViteReactAdapter implements ToolchainAdapter {
  readonly id = 'pnpm-vite-react';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['web'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const packagePath = context.surfaceConfig.packagePath;
    if (typeof packagePath !== 'string' || packagePath.length === 0) {
      throw new Error('packagePath is required for PnpmViteReactAdapter');
    }

    const script = this.mapPhaseToScript(context.phase, context.surfaceConfig);
    const packageDirectory = this.resolvePackageDirectory(packagePath);

    return [
      {
        id: `pnpm-${context.phase}`,
        description: `Run pnpm ${script} for ${packageDirectory}`,
        command: ['pnpm', '--dir', packageDirectory, 'run', script],
        workingDirectory: this.repoRoot,
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const startedAt = Date.now();

    // Package phase: reject if container image is the expected artifact type.
    // Container images must be built via the docker-buildx adapter.
    if (context.phase === 'package') {
      const expectedArtifactType = context.surfaceConfig.expectedArtifactType;
      if (expectedArtifactType === 'container-image') {
        throw new Error(
          `PnpmViteReactAdapter cannot build container images. ` +
            `Configure the docker-buildx adapter for the package phase of surface ${context.surface.type}.`,
        );
      }
    }

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

    const commandResult = await this.commandRunner.run(step.command[0], step.command.slice(1), {
      cwd: step.workingDirectory,
      env: { ...process.env, ...step.env },
    });

    const durationMs = commandResult.durationMs || Date.now() - startedAt;

    if (commandResult.exitCode !== 0) {
      return {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10000),
            stderr: commandResult.stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
        failure: {
          stepId: step.id,
          message: 'pnpm execution failed',
          ...(commandResult.stderr ? { cause: commandResult.stderr } : {}),
        },
      };
    }

    // dev phase: the Vite dev server is long-running; write processes.json, skip dist validation.
    if (context.phase === 'dev') {
      await this.writeProcessesJson(context, step.id, durationMs);
      return {
        status: 'succeeded',
        steps: [
          {
            stepId: step.id,
            status: 'succeeded',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10000),
            stderr: commandResult.stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
      };
    }

    const validation = await this.validateOutputs(context);
    const artifacts = await this.extractArtifacts(context);

    const failed = validation.status === 'invalid';
    if (failed) {
      return {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10000),
            stderr: commandResult.stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts,
        durationMs,
        failure: {
          stepId: step.id,
          message: validation.errors.map((error) => error.message).join('; ') || 'pnpm execution failed',
          ...(commandResult.stderr ? { cause: commandResult.stderr } : {}),
        },
      };
    }

    if (context.phase === 'package') {
      await this.writeArtifactManifest(context, artifacts);
    }

    return {
      status: 'succeeded',
      steps: [
        {
          stepId: step.id,
          status: 'succeeded',
          exitCode: commandResult.exitCode,
          stdout: commandResult.stdout.slice(0, 10000),
          stderr: commandResult.stderr.slice(0, 10000),
          durationMs,
        },
      ],
      artifacts,
      durationMs,
    };
  }

  private async writeArtifactManifest(
    context: ToolchainAdapterContext,
    staticArtifacts: string[],
  ): Promise<void> {
    const packagePath = context.surfaceConfig.packagePath;
    if (typeof packagePath !== 'string') return;

    const packageDirectory = path.isAbsolute(packagePath)
      ? this.resolvePackageDirectory(packagePath)
      : path.join(this.repoRoot, this.resolvePackageDirectory(packagePath));

    const buildDir = path.join(packageDirectory, 'build');
    await fs.mkdir(buildDir, { recursive: true });

    const manifestArtifacts: Array<{ path: string; metadata: { type: string } }> = staticArtifacts.map(
      (artifactPath) => ({ path: artifactPath, metadata: { type: 'static-web-bundle' } }),
    );

    // Detect Dockerfile and build a Docker image if present.
    const dockerfilePath = path.join(packageDirectory, 'Dockerfile');
    if (await this.exists(dockerfilePath)) {
      const imageName = `${context.productId}/${context.surface.path ?? 'web'}:latest`
        .toLowerCase()
        .replace(/[^a-z0-9/:.-]/g, '-');
      const buildResult = await this.commandRunner.run(
        'docker',
        ['buildx', 'build', '--load', '-t', imageName, packageDirectory],
        { cwd: this.repoRoot },
      );
      if (buildResult.exitCode === 0) {
        manifestArtifacts.push({ path: imageName, metadata: { type: 'docker-image' } });
      }
    }

    const manifest = {
      productId: context.productId,
      phase: context.phase,
      generatedAt: new Date().toISOString(),
      artifacts: manifestArtifacts,
    };

    await fs.writeFile(
      path.join(buildDir, 'artifact-manifest.json'),
      JSON.stringify(manifest, null, 2),
      'utf-8',
    );
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // dev phase: outputs are a running dev server — nothing to validate on disk.
    if (context.phase === 'dev') {
      return { status: 'valid', errors: [], missingArtifacts: [], unexpectedArtifacts: [] };
    }

    const expectedOutputs = this.getExpectedOutputsForPhase(context);
    const outputPaths = expectedOutputs.length > 0
      ? expectedOutputs
      : this.getFallbackExpectedOutputs(context);

    const missingArtifacts: string[] = [];
    for (const relativePath of outputPaths) {
      const absolutePath = path.isAbsolute(relativePath)
        ? relativePath
        : path.join(this.repoRoot, relativePath);
      if (!(await this.exists(absolutePath))) {
        missingArtifacts.push(relativePath);
      }
    }

    return {
      status: missingArtifacts.length === 0 ? 'valid' : 'invalid',
      errors: missingArtifacts.map((artifact) => ({
        path: 'outputs',
        message: `Missing expected output: ${artifact}`,
      })),
      missingArtifacts,
      unexpectedArtifacts: [],
    };
  }

  /** Write a processes.json to outputDir for the dev phase. */
  private async writeProcessesJson(
    context: ToolchainAdapterContext,
    stepId: string,
    durationMs: number,
  ): Promise<void> {
    const outputDir = context.outputDir ?? path.join(this.repoRoot, '.kernel', 'out', 'dev');
    await fs.mkdir(outputDir, { recursive: true });

    const rawPackagePath = String(context.surfaceConfig.packagePath ?? context.surface.path);
    const packageDirectory = this.resolvePackageDirectory(rawPackagePath);
    const processEntry = {
      schemaVersion: '1.0.0',
      productId: context.productId,
      surface: context.surface.type,
      adapter: 'pnpm-vite-react',
      startedAt: new Date().toISOString(),
      stepId,
      durationMs,
      packageDirectory,
      script: this.mapPhaseToScript('dev', context.surfaceConfig),
      note: 'Vite dev server completes when terminated. PID not captured in batch mode.',
    };

    await fs.writeFile(
      path.join(outputDir, 'processes.json'),
      JSON.stringify(processEntry, null, 2),
      'utf-8',
    );
  }

  private mapPhaseToScript(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    switch (phase) {
      case 'dev':
        return String(surfaceConfig.devScript ?? 'dev');
      case 'validate':
        return String(surfaceConfig.validateScript ?? 'lint');
      case 'test':
        return String(surfaceConfig.testScript ?? 'test');
      case 'build':
        return String(surfaceConfig.buildScript ?? 'build');
      case 'package':
        return String(surfaceConfig.packageScript ?? 'build');
      default:
        throw new Error(`PnpmViteReactAdapter does not support phase ${phase}`);
    }
  }

  private getExpectedOutputsForPhase(context: ToolchainAdapterContext): string[] {
    const configured = context.surfaceConfig.expectedOutputs;
    if (!configured || typeof configured !== 'object') {
      return [];
    }

    const byPhase = configured as Record<string, unknown>;
    const forPhase = byPhase[context.phase];
    if (!Array.isArray(forPhase)) {
      return [];
    }

    return forPhase.filter((value): value is string => typeof value === 'string' && value.length > 0);
  }

  private getFallbackExpectedOutputs(context: ToolchainAdapterContext): string[] {
    const packageDirectory = this.resolvePackageDirectory(
      String(context.surfaceConfig.packagePath ?? context.surface.path),
    );

    if (context.phase === 'build' || context.phase === 'package') {
      return [
        path.join(packageDirectory, 'dist').replace(/\\/g, '/'),
        path.join(packageDirectory, 'dist', 'index.html').replace(/\\/g, '/'),
      ];
    }

    return [path.join(packageDirectory, 'package.json').replace(/\\/g, '/')];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<string[]> {
    if (context.phase !== 'build' && context.phase !== 'package') {
      return [];
    }

    const expectedOutputs = this.getExpectedOutputsForPhase(context);
    const artifactOutputs = expectedOutputs.length > 0
      ? expectedOutputs
      : [this.getFallbackExpectedOutputs(context)[0]];

    const artifacts = new Set<string>();
    for (const candidate of artifactOutputs) {
      const absolute = path.isAbsolute(candidate) ? candidate : path.join(this.repoRoot, candidate);
      if (await this.exists(absolute)) {
        artifacts.add(path.relative(this.repoRoot, absolute).replace(/\\/g, '/'));
      }
    }

    return [...artifacts];
  }

  private resolvePackageDirectory(packagePath: string): string {
    return packagePath.endsWith('package.json') ? path.dirname(packagePath) : packagePath;
  }

  private async exists(targetPath: string): Promise<boolean> {
    try {
      await fs.access(targetPath);
      return true;
    } catch {
      return false;
    }
  }
}
