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
 * @doc.type class
 * @doc.purpose Manages Docker Compose local deployments for Kernel lifecycle deploy / verify / rollback phases.
 *   Uses SpawnCommandRunner for safe arg-array execution. Reads composeFile and envFile from
 *   the adapter context (populated by the planner from kernel-product.yaml deployment.local).
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export class ComposeLocalAdapter implements ToolchainAdapter {
  readonly id = 'compose-local';
  readonly supportedPhases: ProductLifecyclePhase[] = ['deploy', 'verify', 'rollback', 'operate'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'web', 'worker', 'operator'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { composeFile, envFile } = this.resolveConfig(context);

    if (context.phase === 'verify') {
      // Verify does health checks, not a compose command
      return [
        {
          id: `compose-verify-${context.surface}`,
          description: 'Verify service health after deployment',
          command: ['docker', 'compose', '-f', composeFile, 'ps'],
          workingDirectory: this.repoRoot,
        },
      ];
    }

    const actionArgs = this.mapPhaseToActionArgs(context.phase);
    const envArgs = envFile ? ['--env-file', envFile] : [];
    const command: string[] = [
      'docker', 'compose',
      '-f', composeFile,
      ...envArgs,
      ...actionArgs,
    ];

    return [
      {
        id: `compose-${context.phase}-${context.surface}`,
        description: `docker compose ${actionArgs.join(' ')} (${path.basename(composeFile)})`,
        command,
        workingDirectory: this.repoRoot,
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
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

    await this.validatePrerequisites(context);

    const startedAt = Date.now();
    context.logger.info(`Executing: ${step.command.join(' ')}`);

    if (context.phase === 'verify') {
      return this.executeVerify(context, startedAt);
    }

    const commandResult = await this.commandRunner.run(
      step.command[0],
      step.command.slice(1),
      {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
        timeoutMs: 300_000, // 5 min
      },
    );

    const durationMs = commandResult.durationMs || Date.now() - startedAt;

    if (commandResult.exitCode !== 0) {
      context.logger.error(`docker compose ${context.phase} failed (exit ${commandResult.exitCode})`);
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
          message: `docker compose exited with code ${commandResult.exitCode}`,
          cause: commandResult.stderr.slice(0, 2_000),
        },
      };
    }

    context.logger.info(`docker compose ${context.phase} succeeded in ${durationMs}ms`);

    const artifacts = context.phase === 'deploy'
      ? [await this.writeDeploymentManifest(context)]
      : [];

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
    context.logger.debug(`[compose-local] validateOutputs for ${context.productId} (${context.phase})`);

    // compose-local validates that required containers are running.
    // Prerequisites (compose file existence) are checked during execute().
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

  /** Validate that compose file exists and env example is present (if env file is required). */
  private async validatePrerequisites(context: ToolchainAdapterContext): Promise<void> {
    const { composeFile, envFile, envExampleFile, requireEnvFile } = this.resolveConfig(context);

    try {
      await fs.access(composeFile);
    } catch {
      throw new Error(
        `Compose file not found: ${composeFile}. ` +
          'Check deployment.local.composeFile in kernel-product.yaml.',
      );
    }

    if (requireEnvFile && envFile) {
      try {
        await fs.access(envFile);
      } catch {
        const envExampleHint = envExampleFile
          ? ` Copy ${path.basename(envExampleFile)} to ${path.basename(envFile)} and fill in values.`
          : ' Create the env file with required variables.';
        throw new Error(
          `Env file not found: ${envFile}.${envExampleHint}`,
        );
      }
    } else if (envExampleFile) {
      try {
        await fs.access(envExampleFile);
      } catch {
        context.logger.warn(`Env example file declared but not found: ${envExampleFile}`);
      }
    }
  }

  /** Execute health checks for the verify phase. */
  private async executeVerify(
    context: ToolchainAdapterContext,
    startedAt: number,
  ): Promise<ToolchainExecutionResult> {
    const stepId = `compose-verify-${String(context.surface.type)}`;

    // Use docker compose ps to see running services
    const { composeFile, envFile } = this.resolveConfig(context);
    const envArgs = envFile ? ['--env-file', envFile] : [];
    const psResult = await this.commandRunner.run(
      'docker',
      ['compose', '-f', composeFile, ...envArgs, 'ps', '--format', 'json'],
      { cwd: this.repoRoot, env: { ...process.env } },
    );

    const durationMs = Date.now() - startedAt;

    if (psResult.exitCode !== 0) {
      return {
        status: 'failed',
        steps: [
          {
            stepId,
            status: 'failed',
            exitCode: psResult.exitCode,
            stdout: psResult.stdout.slice(0, 10_000),
            stderr: psResult.stderr.slice(0, 10_000),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
        failure: {
          stepId,
          message: 'docker compose ps failed during verify',
          cause: psResult.stderr.slice(0, 2_000),
        },
      };
    }

    // Check health checks from surfaceConfig (populated by planner from verify section)
    const healthResult = await this.runHealthChecks(context, startedAt);
    if (healthResult !== null) {
      return healthResult;
    }

    context.logger.info(`Verify passed in ${durationMs}ms`);
    return {
      status: 'succeeded',
      steps: [
        {
          stepId,
          status: 'succeeded',
          exitCode: 0,
          stdout: psResult.stdout.slice(0, 10_000),
          durationMs,
        },
      ],
      artifacts: [],
      durationMs,
    };
  }

  /** Run HTTP health checks if declared in surfaceConfig.healthChecks. */
  private async runHealthChecks(
    context: ToolchainAdapterContext,
    startedAt: number,
  ): Promise<ToolchainExecutionResult | null> {
    const healthChecks = context.surfaceConfig.healthChecks as
      | Record<string, { type: string; url?: string; retries?: number; intervalMs?: number; timeoutMs?: number }>
      | undefined;

    if (!healthChecks) return null;

    for (const [checkName, check] of Object.entries(healthChecks)) {
      if (check.type !== 'http' || !check.url) continue;

      const retries = check.retries ?? 5;
      const intervalMs = check.intervalMs ?? 2000;
      let lastError = '';

      for (let attempt = 0; attempt < retries; attempt++) {
        try {
          const curlResult = await this.commandRunner.run(
            'curl',
            ['-sf', '--max-time', '5', check.url],
            { cwd: this.repoRoot, env: { ...process.env }, timeoutMs: (check.timeoutMs ?? 10_000) },
          );

          if (curlResult.exitCode === 0) {
            context.logger.info(`Health check "${checkName}" passed: ${check.url}`);
            break;
          }

          lastError = curlResult.stderr || `exit code ${curlResult.exitCode}`;
        } catch (err) {
          lastError = String((err as Error).message);
        }

        if (attempt < retries - 1) {
          context.logger.warn(`Health check "${checkName}" attempt ${attempt + 1}/${retries} failed, retrying in ${intervalMs}ms`);
          await this.sleep(intervalMs);
        } else {
          const stepId = `compose-verify-health-${checkName}`;
          const durationMs = Date.now() - startedAt;
          return {
            status: 'failed',
            steps: [{ stepId, status: 'failed', durationMs }],
            artifacts: [],
            durationMs,
            failure: {
              stepId,
              message: `Health check "${checkName}" failed after ${retries} attempts: ${check.url}`,
              cause: lastError,
            },
          };
        }
      }
    }

    return null;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /** Write a deployment manifest JSON file to the output directory. */
  private async writeDeploymentManifest(context: ToolchainAdapterContext): Promise<string> {
    const deployStartedAt = new Date().toISOString();
    const outputDir = context.outputDir ?? path.join(this.repoRoot, '.kernel', 'out', 'deploy');
    await fs.mkdir(outputDir, { recursive: true });

    const { composeFile, envFile } = this.resolveConfig(context);
    const manifestPath = path.join(outputDir, 'deployment-manifest.json');

    // Parse running services from docker compose ps
    const services: Record<string, string> = {};
    try {
      const psResult = await this.commandRunner.run(
        'docker',
        ['compose', '-f', composeFile, ...(envFile ? ['--env-file', envFile] : []), 'ps', '--format', 'json'],
        { cwd: this.repoRoot, env: { ...process.env } },
      );
      if (psResult.exitCode === 0 && psResult.stdout.trim()) {
        // Output may be NDJSON (one JSON object per line) or a JSON array
        const lines = psResult.stdout.trim().split('\n').filter((l) => l.trim());
        for (const line of lines) {
          try {
            const entry = JSON.parse(line) as Record<string, unknown>;
            const name = String(entry.Name ?? entry.Service ?? '');
            const state = String(entry.State ?? entry.Status ?? 'unknown');
            if (name) services[name] = state;
          } catch {
            // Ignore non-JSON lines (e.g. table headers)
          }
        }
      }
    } catch {
      // Not fatal — manifest can still be written without service list
    }

    // Collect health check URLs from context surfaceConfig
    const healthChecks = context.surfaceConfig.healthChecks as
      | Record<string, { type: string; url?: string }>
      | undefined;
    const healthUrls = healthChecks
      ? Object.values(healthChecks)
          .filter((c) => c.type === 'http' && typeof c.url === 'string')
          .map((c) => c.url as string)
      : [];

    const completedAt = new Date().toISOString();

    const manifest = {
      schemaVersion: '1.0.0',
      productId: context.productId,
      environment: context.environment ?? 'local',
      adapter: 'compose-local',
      composeFile: path.relative(this.repoRoot, composeFile).replace(/\\/g, '/'),
      project: path.basename(composeFile, path.extname(composeFile)),
      services,
      env: context.environment ?? 'local',
      healthUrls,
      startedAt: deployStartedAt,
      completedAt,
      deployedAt: completedAt,
      status: 'deployed',
    };

    await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
    return manifestPath;
  }

  private resolveConfig(context: ToolchainAdapterContext): {
    composeFile: string;
    envFile: string | undefined;
    envExampleFile: string | undefined;
    requireEnvFile: boolean;
  } {
    const cfg = context.surfaceConfig;

    const composeFile = this.resolvePath(String(cfg.composeFile ?? 'docker-compose.yaml'));
    const envFile = cfg.envFile ? this.resolvePath(String(cfg.envFile)) : undefined;
    const envExampleFile = cfg.envExampleFile
      ? this.resolvePath(String(cfg.envExampleFile))
      : undefined;
    const requireEnvFile = Boolean(cfg.requireEnvFile ?? false);

    return { composeFile, envFile, envExampleFile, requireEnvFile };
  }

  private resolvePath(p: string): string {
    return path.isAbsolute(p) ? p : path.join(this.repoRoot, p);
  }

  private mapPhaseToActionArgs(phase: ProductLifecyclePhase): string[] {
    switch (phase) {
      case 'deploy': return ['up', '-d', '--remove-orphans'];
      case 'rollback': return ['down', '--remove-orphans'];
      case 'operate': return ['ps'];
      default: return ['ps'];
    }
  }
}
