import * as path from 'node:path';
import type {
  AdapterPreflightResult,
  LifecycleFailureClassifier,
  ProductLifecyclePhase,
  ProductSurfaceType,
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ToolchainPlanStep,
} from '../ToolchainAdapter.js';
import { SpawnCommandRunner } from '../execution/SpawnCommandRunner.js';
import type { CommandRunner } from '../execution/CommandRunner.js';
import {
  configuredString,
  createEnvironmentBlockedClassifier,
  defaultTimeoutMs,
  executePolyglotPlan,
  exists,
  extractExistingArtifacts,
  getConfiguredExpectedOutputs,
  validateExpectedOutputs,
  writePolyglotArtifactManifest,
} from './PolyglotAdapterSupport.js';

/**
 * Cargo Rust adapter for Kernel-managed Rust services, workers, and SDKs.
 */
export class CargoRustAdapter implements ToolchainAdapter {
  readonly id = 'cargo-rust';
  readonly supportedPhases: ProductLifecyclePhase[] = ['validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'sdk'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async preflight(context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    const checkedAt = new Date().toISOString();
    const manifestPath = this.resolveCargoToml(context);
    const manifestExists = await exists(manifestPath);
    const checks: AdapterPreflightResult['checks'] = [
      {
        checkId: 'cargo-manifest',
        checkName: 'Cargo manifest',
        status: manifestExists ? 'passed' : 'failed',
        message: manifestExists ? `Found ${manifestPath}` : `Missing ${manifestPath}`,
        ...(manifestExists ? {} : { severity: 'critical', remediation: ['Add Cargo.toml or configure cargoToml'] }),
        checkedAt,
      },
    ];
    try {
      const result = await this.commandRunner.run('cargo', ['--version'], { cwd: this.repoRoot, timeoutMs: 10_000 });
      checks.push({
        checkId: 'cargo-version',
        checkName: 'Cargo executable',
        status: result.exitCode === 0 ? 'passed' : 'failed',
        message: result.exitCode === 0 ? result.stdout.trim() : result.stderr.trim(),
        ...(result.exitCode === 0 ? {} : { severity: 'critical', remediation: ['Install Rust toolchain and Cargo'] }),
        checkedAt,
      });
    } catch (error) {
      checks.push({
        checkId: 'cargo-version',
        checkName: 'Cargo executable',
        status: 'failed',
        message: error instanceof Error ? error.message : 'Cargo executable check failed',
        severity: 'critical',
        remediation: ['Install Rust toolchain and Cargo'],
        checkedAt,
      });
    }

    const blockingIssues = checks.filter((check) => check.status === 'failed').map((check) => check.message);
    return { status: blockingIssues.length === 0 ? 'ready' : 'blocked', checks, blockingIssues, warnings: [] };
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const crateDirectory = this.resolveCrateDirectory(context);
    if (!(await exists(this.resolveCargoToml(context)))) {
      throw new Error(`cargo-manifest-not-found: ${path.join(crateDirectory, 'Cargo.toml')}`);
    }
    return this.mapPhaseToCommands(context.phase).map((command, index) => ({
      id: `cargo-${context.phase}-${index + 1}`,
      description: `Run cargo ${command.join(' ')} for ${crateDirectory}`,
      command: ['cargo', ...command],
      workingDirectory: crateDirectory,
      ...(index === 0 ? {} : { dependsOn: [`cargo-${context.phase}-${index}`] }),
    }));
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const steps = await this.plan(context);
    return executePolyglotPlan({
      adapterId: this.id,
      context,
      commandRunner: this.commandRunner,
      steps,
      validateOutputs: () => this.validateOutputs(context),
      extractArtifacts: () => this.extractArtifacts(context),
      writeArtifactManifest: (artifacts) => writePolyglotArtifactManifest(
        this.repoRoot,
        context,
        this.id,
        this.artifactType(context),
        artifacts,
      ),
      timeoutMs: this.resolveTimeoutMs(context),
    });
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const configured = getConfiguredExpectedOutputs(context);
    const outputs = configured.length > 0 ? configured : this.fallbackOutputs(context);
    return validateExpectedOutputs(this.repoRoot, outputs);
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createEnvironmentBlockedClassifier(error, this.id, 'cargo');
  }

  private mapPhaseToCommands(phase: ProductLifecyclePhase): readonly string[][] {
    switch (phase) {
      case 'validate':
        return [['fmt', '--check'], ['check'], ['clippy', '--', '-D', 'warnings']];
      case 'test':
        return [['test']];
      case 'build':
        return [['build', '--release']];
      case 'package':
        return [['build', '--release']];
      default:
        throw new Error(`CargoRustAdapter does not support phase ${phase}`);
    }
  }

  private fallbackOutputs(context: ToolchainAdapterContext): readonly string[] {
    const crateDirectory = this.relativeCrateDirectory(context);
    if (context.phase === 'validate') {
      return [path.join(crateDirectory, 'Cargo.toml').replace(/\\/g, '/')];
    }
    if (context.phase === 'test') {
      return [path.join(crateDirectory, 'target').replace(/\\/g, '/')];
    }
    const artifactName = typeof context.surfaceConfig.artifactName === 'string'
      ? context.surfaceConfig.artifactName
      : this.defaultArtifactName(context);
    return [path.join(crateDirectory, 'target', 'release', artifactName).replace(/\\/g, '/')];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<readonly string[]> {
    if (context.phase !== 'build' && context.phase !== 'package') {
      return [];
    }
    const configured = getConfiguredExpectedOutputs(context);
    const candidates = configured.length > 0 ? configured : this.fallbackOutputs(context);
    return extractExistingArtifacts(this.repoRoot, candidates);
  }

  private resolveCrateDirectory(context: ToolchainAdapterContext): string {
    return path.join(this.repoRoot, this.relativeCrateDirectory(context));
  }

  private relativeCrateDirectory(context: ToolchainAdapterContext): string {
    const configured = context.surfaceConfig.cratePath ?? context.surfaceConfig.packagePath ?? context.surface.path;
    const cratePath = configuredString(configured, 'cratePath');
    return cratePath.endsWith('Cargo.toml') ? path.dirname(cratePath) : cratePath;
  }

  private resolveCargoToml(context: ToolchainAdapterContext): string {
    const configured = context.surfaceConfig.cargoToml;
    if (typeof configured === 'string' && configured.trim().length > 0) {
      return path.isAbsolute(configured) ? configured : path.join(this.repoRoot, configured);
    }
    return path.join(this.resolveCrateDirectory(context), 'Cargo.toml');
  }

  private defaultArtifactName(context: ToolchainAdapterContext): string {
    const packageName = context.productId.replace(/[^a-zA-Z0-9_-]/g, '-');
    return process.platform === 'win32' && context.surface.type !== 'sdk' ? `${packageName}.exe` : packageName;
  }

  private artifactType(context: ToolchainAdapterContext): string {
    return context.surface.type === 'sdk' ? 'rust-library' : 'rust-binary';
  }

  private resolveTimeoutMs(context: ToolchainAdapterContext): number {
    const configured = context.phaseConfig.timeoutMs ?? context.surfaceConfig.timeoutMs;
    return typeof configured === 'number' && Number.isFinite(configured) && configured > 0
      ? configured
      : defaultTimeoutMs(context.phase);
  }
}
