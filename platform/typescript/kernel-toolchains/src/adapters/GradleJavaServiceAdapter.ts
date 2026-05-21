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
  AdapterPreflightResult,
  AdapterPreflightCheck,
  LifecycleFailureClassifier,
} from '../ToolchainAdapter.js';
import { SpawnCommandRunner } from '../execution/SpawnCommandRunner.js';
import type { CommandRunner } from '../execution/CommandRunner.js';
import {
  createCommandObservability,
  createDryRunObservability,
  createToolchainExecutionResult,
  truncateToolchainOutput,
} from '../execution/ToolchainExecutionResultFactory.js';
import {
  createDefaultFailureClassifier,
} from '../ToolchainAdapter.js';
import type { ToolchainCoverageResults, ToolchainTestResults } from '../ToolchainAdapter.js';

/**
 * Gradle Java service adapter
 */
export class GradleJavaServiceAdapter implements ToolchainAdapter {
  readonly id = 'gradle-java-service';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'operator'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async preflight(context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    const checkedAt = new Date().toISOString();
    const checks: AdapterPreflightCheck[] = [];
    const wrapperPath = path.join(this.repoRoot, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
    checks.push(await this.filePreflightCheck(
      'gradle-wrapper',
      'Gradle wrapper',
      wrapperPath,
      checkedAt,
      'Add the repository Gradle wrapper before executing Java lifecycle phases.',
    ));

    const gradleModule = context.surfaceConfig.gradleModule;
    checks.push({
      checkId: 'gradle-module-config',
      checkName: 'Gradle module configuration',
      status: typeof gradleModule === 'string' && gradleModule.trim().length > 0 ? 'passed' : 'failed',
      message: typeof gradleModule === 'string' && gradleModule.trim().length > 0
        ? `Configured Gradle module ${gradleModule}`
        : 'surfaceConfig.gradleModule must be a non-empty string',
      severity: 'critical',
      remediation: ['Set surfaceConfig.gradleModule to the included Gradle project path.'],
      checkedAt,
    });

    const sourcePath = this.resolveSurfacePath(context);
    checks.push(await this.filePreflightCheck(
      'gradle-surface-source',
      'Gradle surface source path',
      sourcePath,
      checkedAt,
      'Create the surface source directory or correct surface.path/source.',
    ));

    const blockingIssues = checks
      .filter((check) => check.status === 'failed')
      .map((check) => `${check.checkId}: ${check.message}`);

    return {
      status: blockingIssues.length > 0 ? 'blocked' : 'ready',
      checks,
      blockingIssues,
      warnings: [],
    };
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const gradleModule = context.surfaceConfig.gradleModule;
    if (typeof gradleModule !== 'string' || gradleModule.length === 0) {
      throw new Error('gradleModule is required for GradleJavaServiceAdapter');
    }

    const task = this.mapPhaseToTask(context.phase, context.surfaceConfig);
    return [
      {
        id: `gradle-${context.phase}`,
        description: `Run Gradle ${task} for ${gradleModule}`,
        command: [process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew', `${gradleModule}:${task}`, '--no-daemon'],
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

    await this.validateGradleModule(context);

    const commandResult = await this.commandRunner.run(step.command[0], step.command.slice(1), {
      cwd: step.workingDirectory,
      env: { ...process.env, ...step.env },
      timeoutMs: this.resolveTimeoutMs(context),
    });

    const durationMs = commandResult.durationMs || Date.now() - startedAt;

    if (commandResult.exitCode !== 0) {
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
          message: 'gradle-task-failed: Gradle execution failed',
          ...(commandResult.stderr ? { cause: commandResult.stderr } : {}),
        },
        observability: createCommandObservability(step.id, commandResult, durationMs),
      });
    }

    // dev phase: the process is long-running; write processes.json and return immediately.
    // Do NOT validate dist/jar outputs for dev — the server runs in the foreground.
    if (context.phase === 'dev') {
      await this.writeProcessesJson(context, step.id, durationMs, commandResult.pid);
      return createToolchainExecutionResult(context, {
        status: 'succeeded',
        steps: [
          {
            stepId: step.id,
            status: 'succeeded',
            exitCode: commandResult.exitCode,
            stdout: truncateToolchainOutput(commandResult.stdout),
            stderr: truncateToolchainOutput(commandResult.stderr),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
        evidenceRefs: [`process:${context.productId}:${context.surface.type}:${commandResult.pid}`],
        observability: createCommandObservability(step.id, commandResult, durationMs),
      });
    }

    const validation = await this.validateOutputs(context);
    const artifacts = await this.extractArtifacts(context);
    const testResults = await this.extractTestResults(context);
    const coverageResults = await this.extractCoverageResults(context);

    const failed = validation.status === 'invalid';
    if (failed) {
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
        evidenceRefs: artifacts.map((artifact) => `artifact:${artifact}`),
        failure: {
          stepId: step.id,
          message: validation.errors.map((error) => error.message).join('; ') || 'output-validation-failed: Output validation failed',
          ...(commandResult.stderr ? { cause: commandResult.stderr } : {}),
        },
        observability: createCommandObservability(step.id, commandResult, durationMs),
      });
    }

    return createToolchainExecutionResult(context, {
      status: 'succeeded',
      steps: [
        {
          stepId: step.id,
          status: 'succeeded',
          exitCode: commandResult.exitCode,
          stdout: truncateToolchainOutput(commandResult.stdout),
          stderr: truncateToolchainOutput(commandResult.stderr),
          durationMs,
        },
      ],
      artifacts,
      ...(testResults ? { testResults } : {}),
      ...(coverageResults ? { coverageResults } : {}),
      durationMs,
      evidenceRefs: artifacts.map((artifact) => `artifact:${artifact}`),
      observability: createCommandObservability(step.id, commandResult, durationMs),
    });
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // dev phase: outputs are a running process — nothing to validate on disk.
    if (context.phase === 'dev') {
      return { status: 'valid', errors: [], missingArtifacts: [], unexpectedArtifacts: [] };
    }

    const expectedOutputs = this.getExpectedOutputsForPhase(context);
    const fallbackExpectedOutputs = this.getFallbackExpectedOutputs(context);
    const outputPatterns = expectedOutputs.length > 0 ? expectedOutputs : fallbackExpectedOutputs;

    const missingArtifacts: string[] = [];
    for (const pattern of outputPatterns) {
      if (!(await this.patternExists(pattern))) {
        missingArtifacts.push(pattern);
      }
    }

    return {
      status: missingArtifacts.length === 0 ? 'valid' : 'invalid',
      errors: missingArtifacts.map((pattern) => ({
        path: 'outputs',
        message: `Missing expected output: ${pattern}`,
      })),
      missingArtifacts,
      unexpectedArtifacts: [],
    };
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    if (/gradle-module-not-found|settings\.gradle|gradleModule/i.test(error.message)) {
      return {
        category: 'config',
        severity: 'high',
        retryable: false,
        requiresHumanIntervention: true,
        remediationSteps: [
          'Verify surfaceConfig.gradleModule matches the Gradle project path.',
          'Ensure settings.gradle(.kts) or applied settings files include the module.',
        ],
        relatedFailureCodes: ['gradle-java-service-module-config'],
        component: this.id,
      };
    }
    if (/gradlew|ENOENT|not found/i.test(error.message)) {
      return {
        category: 'environment',
        severity: 'high',
        retryable: false,
        requiresHumanIntervention: true,
        remediationSteps: ['Restore the Gradle wrapper or install the required Java build environment.'],
        relatedFailureCodes: ['gradle-java-service-toolchain-missing'],
        component: this.id,
      };
    }
    return createDefaultFailureClassifier(error, this.id);
  }

  /** Write a processes.json to outputDir for the dev phase. */
  private async writeProcessesJson(
    context: ToolchainAdapterContext,
    stepId: string,
    durationMs: number,
    pid: number,
  ): Promise<void> {
    const outputDir = context.outputDir ?? path.join(this.repoRoot, '.kernel', 'out', 'dev');
    await fs.mkdir(outputDir, { recursive: true });

    const gradleModule = context.surfaceConfig.gradleModule ?? context.surface.path;
    const processEntry = {
      schemaVersion: '1.0.0',
      productId: context.productId,
      surface: context.surface.type,
      adapter: 'gradle-java-service',
      startedAt: new Date().toISOString(),
      stepId,
      durationMs,
      pid,
      gradleModule,
      task: this.mapPhaseToTask('dev', context.surfaceConfig),
      note: 'bootRun completes when the server terminates. PID captured for process supervision.',
      ...(context.runId ? { runId: context.runId } : {}),
      ...(context.correlationId ? { correlationId: context.correlationId } : {}),
      ...(typeof context.surfaceConfig.healthUrl === 'string'
        ? { healthUrl: context.surfaceConfig.healthUrl }
        : {}),
    };

    await fs.writeFile(
      path.join(outputDir, 'processes.json'),
      JSON.stringify(processEntry, null, 2),
      'utf-8',
    );
  }

  private mapPhaseToTask(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    switch (phase) {
      case 'dev':
        return String(surfaceConfig.devTask ?? 'bootRun');
      case 'validate':
        return String(surfaceConfig.validateTask ?? 'check');
      case 'test':
        return String(surfaceConfig.testTask ?? 'test');
      case 'build':
        return String(surfaceConfig.buildTask ?? 'build');
      case 'package':
        return String(surfaceConfig.packageTask ?? 'assemble');
      default:
        throw new Error(`GradleJavaServiceAdapter does not support phase ${phase}`);
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
    const surfacePath = this.resolveSurfacePath(context);
    if (context.phase === 'build' || context.phase === 'package') {
      return [path.join(surfacePath, 'build', 'libs', '*.jar').replace(/\\/g, '/')];
    }
    return [
      path.join(surfacePath, 'build', 'reports', 'tests').replace(/\\/g, '/'),
      path.join(surfacePath, 'build', 'test-results', 'test').replace(/\\/g, '/'),
    ];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<string[]> {
    const outputPatterns = this.getExpectedOutputsForPhase(context);
    const artifactCandidates = outputPatterns.length > 0
      ? outputPatterns
      : this.getArtifactCandidatesForPhase(context);

    const artifacts = new Set<string>();
    for (const pattern of artifactCandidates) {
      const matches = await this.resolveMatches(pattern);
      for (const match of matches) {
        artifacts.add(path.relative(this.repoRoot, match).replace(/\\/g, '/'));
      }
    }

    return [...artifacts];
  }

  private getArtifactCandidatesForPhase(context: ToolchainAdapterContext): string[] {
    const surfacePath = this.resolveSurfacePath(context);
    if (context.phase === 'build' || context.phase === 'package') {
      return [path.join(surfacePath, 'build', 'libs', '*.jar').replace(/\\/g, '/')];
    }
    return [
      path.join(surfacePath, 'build', 'reports', 'tests').replace(/\\/g, '/'),
      path.join(surfacePath, 'build', 'test-results', 'test').replace(/\\/g, '/'),
      path.join(surfacePath, 'build', 'reports', 'jacoco').replace(/\\/g, '/'),
    ];
  }

  private async validateGradleModule(context: ToolchainAdapterContext): Promise<void> {
    const gradleModule = context.surfaceConfig.gradleModule as string;

    const settingsFiles = [
      path.join(this.repoRoot, 'settings.gradle.kts'),
      path.join(this.repoRoot, 'settings.gradle'),
    ];
    const settingsContents: string[] = [];
    const visitedSettingsFiles = new Set<string>();
    for (const settingsFile of settingsFiles) {
      const discoveredContents = await this.readSettingsWithAppliedFiles(
        settingsFile,
        visitedSettingsFiles,
      );
      settingsContents.push(...discoveredContents);
    }

    if (settingsContents.some((content) => this.settingsIncludeModule(content, gradleModule))) {
      return;
    }

    throw new Error(
      `gradle-module-not-found: Gradle module ${gradleModule} is not declared in settings.gradle(.kts).`,
    );
  }

  private async readSettingsWithAppliedFiles(
    settingsFile: string,
    visited: Set<string>,
  ): Promise<string[]> {
    const absolutePath = path.resolve(settingsFile);
    if (visited.has(absolutePath)) {
      return [];
    }
    visited.add(absolutePath);

    if (!(await this.exists(absolutePath))) {
      return [];
    }

    const content = await fs.readFile(absolutePath, 'utf-8');
    const contents = [content];
    const appliedFiles = this.extractAppliedSettingsFiles(content, path.dirname(absolutePath));
    for (const appliedFile of appliedFiles) {
      const nestedContents = await this.readSettingsWithAppliedFiles(appliedFile, visited);
      contents.push(...nestedContents);
    }

    return contents;
  }

  private extractAppliedSettingsFiles(settingsContent: string, baseDir: string): string[] {
    const matches = new Set<string>();
    const patterns = [
      /apply\s*\(\s*from\s*=\s*file\(\s*["']([^"']+)["']\s*\)\s*\)/g,
      /apply\s+from\s*:\s*["']([^"']+)["']/g,
    ];

    for (const pattern of patterns) {
      let match: RegExpExecArray | null;
      // eslint-disable-next-line no-cond-assign
      while ((match = pattern.exec(settingsContent)) !== null) {
        const candidate = match[1]?.trim();
        if (candidate && candidate.length > 0) {
          matches.add(path.resolve(baseDir, candidate));
        }
      }
    }

    return [...matches];
  }

  private settingsIncludeModule(settingsContent: string, gradleModule: string): boolean {
    const escapedModule = gradleModule.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const includePattern = new RegExp(
      `include\\s*\\(?[\\s\\S]*?["']${escapedModule}["']`,
      'm',
    );
    return includePattern.test(settingsContent);
  }

  private resolveTimeoutMs(context: ToolchainAdapterContext): number {
    const configured = context.phaseConfig.timeoutMs ?? context.surfaceConfig.timeoutMs;
    return typeof configured === 'number' && Number.isFinite(configured) && configured > 0
      ? configured
      : this.defaultTimeoutMs(context.phase);
  }

  private defaultTimeoutMs(phase: ProductLifecyclePhase): number {
    switch (phase) {
      case 'dev':
        return 86_400_000;
      case 'test':
        return 900_000;
      case 'build':
      case 'package':
        return 1_200_000;
      case 'validate':
        return 600_000;
      default:
        return 900_000;
    }
  }

  private async extractTestResults(
    context: ToolchainAdapterContext,
  ): Promise<ToolchainTestResults | undefined> {
    if (!['test', 'validate', 'build'].includes(context.phase)) {
      return undefined;
    }
    const resultsDir = path.join(this.resolveSurfacePath(context), 'build', 'test-results', 'test');
    if (!(await this.exists(resultsDir))) {
      return undefined;
    }
    const files = (await this.walkFiles(resultsDir)).filter((filePath) => filePath.endsWith('.xml'));
    let tests = 0;
    let failures = 0;
    let skipped = 0;
    let durationMs = 0;
    for (const filePath of files) {
      const content = await fs.readFile(filePath, 'utf-8');
      tests += numberAttribute(content, 'tests');
      failures += numberAttribute(content, 'failures') + numberAttribute(content, 'errors');
      skipped += numberAttribute(content, 'skipped');
      durationMs += Math.round(numberAttribute(content, 'time') * 1000);
    }
    return files.length === 0 ? undefined : { tests, failures, skipped, durationMs };
  }

  private async extractCoverageResults(
    context: ToolchainAdapterContext,
  ): Promise<ToolchainCoverageResults | undefined> {
    const csvPath = path.join(
      this.resolveSurfacePath(context),
      'build',
      'reports',
      'jacoco',
      'test',
      'jacocoTestReport.csv',
    );
    if (!(await this.exists(csvPath))) {
      return undefined;
    }
    const content = await fs.readFile(csvPath, 'utf-8');
    const lines = content.trim().split(/\r?\n/);
    if (lines.length < 2) {
      return undefined;
    }
    const totals = lines.slice(1).reduce(
      (acc, line) => {
        const columns = line.split(',');
        acc.instructionMissed += Number(columns[3] ?? 0);
        acc.instructionCovered += Number(columns[4] ?? 0);
        acc.branchMissed += Number(columns[5] ?? 0);
        acc.branchCovered += Number(columns[6] ?? 0);
        acc.lineMissed += Number(columns[7] ?? 0);
        acc.lineCovered += Number(columns[8] ?? 0);
        return acc;
      },
      {
        instructionMissed: 0,
        instructionCovered: 0,
        branchMissed: 0,
        branchCovered: 0,
        lineMissed: 0,
        lineCovered: 0,
      },
    );
    return {
      lineCoverage: percentage(totals.lineCovered, totals.lineMissed),
      branchCoverage: percentage(totals.branchCovered, totals.branchMissed),
      instructionCoverage: percentage(totals.instructionCovered, totals.instructionMissed),
    };
  }

  private async patternExists(pattern: string): Promise<boolean> {
    const matches = await this.resolveMatches(pattern);
    return matches.length > 0;
  }

  private async resolveMatches(pattern: string): Promise<string[]> {
    const absolutePattern = path.isAbsolute(pattern) ? pattern : path.join(this.repoRoot, pattern);

    if (!absolutePattern.includes('*')) {
      return (await this.exists(absolutePattern)) ? [absolutePattern] : [];
    }

    const normalizedPattern = absolutePattern.replace(/\\/g, '/');
    const firstWildcardIndex = normalizedPattern.indexOf('*');
    const prefix = normalizedPattern.slice(0, firstWildcardIndex);
    const searchRoot = path.dirname(prefix);

    if (!(await this.exists(searchRoot))) {
      return [];
    }

    const regex = new RegExp(`^${normalizedPattern
      .replace(/[.+?^${}()|[\]\\]/g, '\\$&')
      .replace(/\*/g, '[^/]*')}$`);

    const discovered = await this.walkFiles(searchRoot);
    return discovered.filter((candidate) => regex.test(candidate.replace(/\\/g, '/')));
  }

  private resolveSurfacePath(context: ToolchainAdapterContext): string {
    const configuredSource = context.surfaceConfig.source;
    const relativePath = typeof configuredSource === 'string' && configuredSource.length > 0
      ? configuredSource
      : context.surface.path;
    return path.join(this.repoRoot, relativePath);
  }

  private async walkFiles(directoryPath: string): Promise<string[]> {
    const entries = await fs.readdir(directoryPath, { withFileTypes: true });
    const nested = await Promise.all(entries.map(async (entry) => {
      const fullPath = path.join(directoryPath, entry.name);
      if (entry.isDirectory()) {
        return this.walkFiles(fullPath);
      }
      return [fullPath];
    }));
    return nested.flat();
  }

  private async exists(targetPath: string): Promise<boolean> {
    try {
      await fs.access(targetPath);
      return true;
    } catch {
      return false;
    }
  }

  private async filePreflightCheck(
    checkId: string,
    checkName: string,
    targetPath: string,
    checkedAt: string,
    remediation: string,
  ): Promise<AdapterPreflightCheck> {
    const exists = await this.exists(targetPath);
    return {
      checkId,
      checkName,
      status: exists ? 'passed' : 'failed',
      message: exists ? `Found ${targetPath}` : `Missing ${targetPath}`,
      severity: 'critical',
      remediation: [remediation],
      checkedAt,
    };
  }
}

function numberAttribute(content: string, name: string): number {
  const match = content.match(new RegExp(`${name}="([^"]+)"`));
  if (!match) {
    return 0;
  }
  const parsed = Number(match[1]);
  return Number.isFinite(parsed) ? parsed : 0;
}

function percentage(covered: number, missed: number): number {
  const total = covered + missed;
  if (total === 0) {
    return 0;
  }
  return Math.round((covered / total) * 10_000) / 100;
}
