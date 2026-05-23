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
  private isWorkspaceMemberCache: boolean | null = null;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async preflight(context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    const checkedAt = new Date().toISOString();
    const manifestPath = this.resolveCargoToml(context);
    const manifestExists = await exists(manifestPath);
    
    // Cache workspace detection result
    this.isWorkspaceMemberCache = await this.isWorkspaceMember(context);
    
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

    // Check for llvm-cov if coverage is configured
    if (context.phaseConfig.enableCoverage === true) {
      try {
        const llvmCovResult = await this.commandRunner.run('cargo', ['llvm-cov', '--version'], { cwd: this.repoRoot, timeoutMs: 10_000 });
        checks.push({
          checkId: 'cargo-llvm-cov',
          checkName: 'Cargo llvm-cov',
          status: llvmCovResult.exitCode === 0 ? 'passed' : 'failed',
          message: llvmCovResult.exitCode === 0 ? llvmCovResult.stdout.trim() : llvmCovResult.stderr.trim(),
          ...(llvmCovResult.exitCode === 0 ? {} : { severity: 'low', remediation: ['Install cargo-llvm-cov: cargo install cargo-llvm-cov'] }),
          checkedAt,
        });
      } catch (error) {
        checks.push({
          checkId: 'cargo-llvm-cov',
          checkName: 'Cargo llvm-cov',
          status: 'failed',
          message: error instanceof Error ? error.message : 'Cargo llvm-cov check failed',
          severity: 'low',
          remediation: ['Install cargo-llvm-cov: cargo install cargo-llvm-cov'],
          checkedAt,
        });
      }
    }

    const blockingIssues = checks.filter((check) => check.status === 'failed' && check.severity === 'critical').map((check) => check.message);
    const warnings = checks.filter((check) => check.status === 'failed' && check.severity === 'low').map((check) => check.message);
    return { status: blockingIssues.length === 0 ? 'ready' : 'blocked', checks, blockingIssues, warnings };
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const crateDirectory = this.resolveCrateDirectory(context);
    if (!(await exists(this.resolveCargoToml(context)))) {
      throw new Error(`cargo-manifest-not-found: ${path.join(crateDirectory, 'Cargo.toml')}`);
    }
    return this.mapPhaseToCommands(context.phase, context).map((command, index) => ({
      id: `cargo-${context.phase}-${index + 1}`,
      description: `Run cargo ${command.join(' ')} for ${crateDirectory}`,
      command: ['cargo', ...command],
      workingDirectory: crateDirectory,
      ...(index === 0 ? {} : { dependsOn: [`cargo-${context.phase}-${index}`] }),
    }));
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const steps = await this.plan(context);
    const result = await executePolyglotPlan({
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

    // Parse cargo test output for structured test results
    if (context.phase === 'test' && result.stdout) {
      result.testResults = this.parseCargoTestOutput(result.stdout);
    }

    // Add observability metadata for build/package phases
    if ((context.phase === 'build' || context.phase === 'package') && result.artifacts.length > 0) {
      // P1-01: Add target triple and binary metadata to result metadata
      const targetTriple = await this.detectTargetTriple();
      const rustVersion = await this.detectRustVersion();
      result.observability = {
        ...result.observability,
        commandId: result.observability?.commandId ?? 'cargo-build',
        durationMs: result.durationMs,
        stdoutBytes: result.stdout?.length ?? 0,
        stderrBytes: result.stderr?.length ?? 0,
        stdoutTruncated: false,
        stderrTruncated: false,
        outputLimitBytes: 10_000_000,
      };
      result.metadata = {
        ...result.metadata,
        targetTriple,
        rustVersion,
        artifactType: this.artifactType(context),
      };
    }

    return result;
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const configured = getConfiguredExpectedOutputs(context);
    const outputs = configured.length > 0 ? configured : this.fallbackOutputs(context);
    return validateExpectedOutputs(this.repoRoot, outputs);
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createEnvironmentBlockedClassifier(error, this.id, 'cargo');
  }

  private mapPhaseToCommands(phase: ProductLifecyclePhase, context: ToolchainAdapterContext): readonly string[][] {
    const isWorkspace = this.isWorkspaceMemberCache ?? false;
    switch (phase) {
      case 'validate':
        return [
          ['fmt', '--check'],
          isWorkspace ? ['fmt', '--check', '--workspace'] : ['check'],
          ['clippy', '--', '-D', 'warnings'],
        ];
      case 'test':
        if (context.phaseConfig?.enableCoverage === true) {
          return [
            ['llvm-cov', 'clean', '--workspace'],
            ['llvm-cov', '--workspace', '--lcov', '--output-path', 'target/llvm-cov'],
          ];
        }
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

  private async isWorkspaceMember(context: ToolchainAdapterContext): Promise<boolean> {
    const crateDirectory = this.resolveCrateDirectory(context);
    const workspaceTomlPath = path.join(this.repoRoot, 'Cargo.toml');
    const localWorkspaceTomlPath = path.join(crateDirectory, 'Cargo.toml');

    // Check for workspace manifest at repo root
    const hasWorkspaceAtRoot = await exists(workspaceTomlPath);
    if (hasWorkspaceAtRoot) {
      try {
        const content = await this.commandRunner.run('cat', [workspaceTomlPath], { cwd: this.repoRoot, timeoutMs: 5000 });
        return content.stdout.includes('[workspace]');
      } catch {
        return false;
      }
    }

    // Check for workspace manifest in local crate directory
    const hasLocalWorkspace = await exists(localWorkspaceTomlPath);
    if (hasLocalWorkspace) {
      try {
        const content = await this.commandRunner.run('cat', [localWorkspaceTomlPath], { cwd: crateDirectory, timeoutMs: 5000 });
        return content.stdout.includes('[workspace]');
      } catch {
        return false;
      }
    }

    return false;
  }

  /**
   * Parse cargo test output into structured test results.
   */
  private parseCargoTestOutput(stdout: string): { tests: number; failures: number; skipped: number; durationMs: number } {
    const result = {
      tests: 0,
      failures: 0,
      skipped: 0,
      durationMs: 0,
    };

    // Parse cargo test output format
    const lines = stdout.split('\n');
    for (const line of lines) {
      // Match test result lines like "test test_name ... ok"
      const testMatch = line.match(/^test\s+(.+?)\s+\.\.\.(\w+)$/);
      if (testMatch) {
        const [, _testName, status] = testMatch;
        result.tests++;
        if (status === 'FAILED') {
          result.failures++;
        } else if (status === 'ignored') {
          result.skipped++;
        }
      }

      // Match summary line like "test result: ok. 10 passed; 0 failed; 1 ignored"
      const summaryMatch = line.match(/test result:\s+(\w+)\.\s+(\d+)\s+passed;\s+(\d+)\s+failed;\s+(\d+)\s+ignored/);
      if (summaryMatch) {
        const [, _overallStatus, passed, failed, ignored] = summaryMatch;
        result.tests = parseInt(passed, 10) + parseInt(failed, 10) + parseInt(ignored, 10);
        result.failures = parseInt(failed, 10);
        result.skipped = parseInt(ignored, 10);
      }

      // Match duration line like "test result: ok. 0.001s"
      const durationMatch = line.match(/test result:\s+\w+\.\s+([\d.]+)s/);
      if (durationMatch) {
        result.durationMs = Math.round(parseFloat(durationMatch[1]) * 1000);
      }
    }

    return result;
  }

  /**
   * P1-01: Detect the target triple for the current build.
   */
  private async detectTargetTriple(): Promise<string> {
    try {
      const result = await this.commandRunner.run('rustc', ['-vV'], { cwd: this.repoRoot, timeoutMs: 10_000 });
      const match = result.stdout.match(/host:\s+(.+)/);
      return match ? match[1].trim() : 'unknown';
    } catch {
      return 'unknown';
    }
  }

  /**
   * P1-01: Detect the Rust version.
   */
  private async detectRustVersion(): Promise<string> {
    try {
      const result = await this.commandRunner.run('rustc', ['--version'], { cwd: this.repoRoot, timeoutMs: 10_000 });
      const match = result.stdout.match(/rustc\s+([\d.]+)/);
      return match ? match[1] : 'unknown';
    } catch {
      return 'unknown';
    }
  }
}
