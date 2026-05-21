import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { createHash } from 'node:crypto';
import type {
  LifecycleFailureClassifier,
  ProductLifecyclePhase,
  ToolchainAdapterContext,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ToolchainPlanStep,
  ToolchainStepResult,
} from '../ToolchainAdapter.js';
import type { CommandResult } from '../execution/CommandResult.js';
import type { CommandRunner } from '../execution/CommandRunner.js';
import {
  createCommandObservability,
  createDryRunObservability,
  createToolchainExecutionResult,
  truncateToolchainOutput,
} from '../execution/ToolchainExecutionResultFactory.js';

export interface PolyglotExecutionOptions {
  readonly adapterId: string;
  readonly context: ToolchainAdapterContext;
  readonly commandRunner: CommandRunner;
  readonly steps: readonly ToolchainPlanStep[];
  readonly validateOutputs: () => Promise<ToolchainOutputValidationResult>;
  readonly extractArtifacts: () => Promise<readonly string[]>;
  readonly writeArtifactManifest?: (artifacts: readonly string[]) => Promise<string | undefined>;
  readonly timeoutMs: number;
}

export async function executePolyglotPlan(
  options: PolyglotExecutionOptions,
): Promise<ToolchainExecutionResult> {
  const startedAt = Date.now();
  const firstStep = options.steps[0];
  if (firstStep === undefined) {
    throw new Error(`${options.adapterId} produced no plan steps`);
  }

  if (options.context.dryRun) {
    for (const step of options.steps) {
      options.context.logger.info(`[DRY-RUN] Would execute: ${step.command.join(' ')}`);
    }
    return createToolchainExecutionResult(options.context, {
      status: 'skipped',
      steps: options.steps.map((step) => ({ stepId: step.id, status: 'skipped', durationMs: 0 })),
      artifacts: [],
      durationMs: 0,
      observability: createDryRunObservability(firstStep.id),
    });
  }

  const stepResults: ToolchainStepResult[] = [];
  let lastCommandResult: CommandResult | undefined;
  for (const step of options.steps) {
    const commandResult = await options.commandRunner.run(step.command[0], step.command.slice(1), {
      cwd: step.workingDirectory,
      env: { ...process.env, ...step.env },
      timeoutMs: options.timeoutMs,
    });
    lastCommandResult = commandResult;
    const durationMs = commandResult.durationMs || Date.now() - startedAt;
    const stepResult: ToolchainStepResult = {
      stepId: step.id,
      status: commandResult.exitCode === 0 ? 'succeeded' : 'failed',
      exitCode: commandResult.exitCode,
      stdout: truncateToolchainOutput(commandResult.stdout),
      stderr: truncateToolchainOutput(commandResult.stderr),
      durationMs,
    };
    stepResults.push(stepResult);

    if (commandResult.exitCode !== 0) {
      return createToolchainExecutionResult(options.context, {
        status: 'failed',
        steps: stepResults,
        artifacts: [],
        durationMs,
        failure: {
          stepId: step.id,
          message: `${options.adapterId}-command-failed: ${step.description}`,
          ...(commandResult.stderr ? { cause: commandResult.stderr } : {}),
        },
        observability: createCommandObservability(step.id, commandResult, durationMs),
      });
    }
  }

  if (options.context.phase === 'dev') {
    return createToolchainExecutionResult(options.context, {
      status: 'succeeded',
      steps: stepResults,
      artifacts: [],
      durationMs: Date.now() - startedAt,
      evidenceRefs: [`process:${options.context.productId}:${options.context.surface.type}:${lastCommandResult?.pid ?? 0}`],
      observability: createCommandObservability(firstStep.id, lastCommandResult ?? emptyCommandResult(), Date.now() - startedAt),
    });
  }

  const validation = await options.validateOutputs();
  const artifacts = [...await options.extractArtifacts()];
  const durationMs = Date.now() - startedAt;
  if (validation.status === 'invalid') {
    return createToolchainExecutionResult(options.context, {
      status: 'failed',
      steps: stepResults,
      artifacts,
      durationMs,
      evidenceRefs: artifacts.map((artifact) => `artifact:${artifact}`),
      failure: {
        stepId: firstStep.id,
        message: validation.errors.map((error) => error.message).join('; ') || 'output-validation-failed',
      },
      observability: createCommandObservability(firstStep.id, lastCommandResult ?? emptyCommandResult(), durationMs),
    });
  }

  const artifactManifest = options.writeArtifactManifest === undefined
    ? undefined
    : await options.writeArtifactManifest(artifacts);

  return createToolchainExecutionResult(options.context, {
    status: 'succeeded',
    steps: stepResults,
    artifacts,
    durationMs,
    ...(artifactManifest !== undefined ? { manifestRefs: { artifactManifest } } : {}),
    evidenceRefs: artifacts.map((artifact) => `artifact:${artifact}`),
    observability: createCommandObservability(firstStep.id, lastCommandResult ?? emptyCommandResult(), durationMs),
  });
}

export function configuredString(value: unknown, fieldName: string): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`${fieldName} is required`);
  }
  return value;
}

export function getConfiguredExpectedOutputs(
  context: ToolchainAdapterContext,
): readonly string[] {
  const configured = context.surfaceConfig.expectedOutputs;
  if (typeof configured !== 'object' || configured === null) {
    return [];
  }
  const forPhase = (configured as Record<string, unknown>)[context.phase];
  return Array.isArray(forPhase)
    ? forPhase.filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
    : [];
}

export async function validateExpectedOutputs(
  repoRoot: string,
  outputs: readonly string[],
): Promise<ToolchainOutputValidationResult> {
  const missingArtifacts: string[] = [];
  for (const output of outputs) {
    const absoluteOutput = path.isAbsolute(output) ? output : path.join(repoRoot, output);
    if (!(await exists(absoluteOutput))) {
      missingArtifacts.push(output);
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

export async function extractExistingArtifacts(
  repoRoot: string,
  candidates: readonly string[],
): Promise<readonly string[]> {
  const artifacts = new Set<string>();
  for (const candidate of candidates) {
    const absoluteCandidate = path.isAbsolute(candidate) ? candidate : path.join(repoRoot, candidate);
    if (await exists(absoluteCandidate)) {
      artifacts.add(path.relative(repoRoot, absoluteCandidate).replace(/\\/g, '/'));
    }
  }
  return [...artifacts];
}

export async function writePolyglotArtifactManifest(
  repoRoot: string,
  context: ToolchainAdapterContext,
  adapterId: string,
  artifactType: string,
  artifacts: readonly string[],
): Promise<string | undefined> {
  if (artifacts.length === 0) {
    return undefined;
  }
  const outputDir = context.outputDir ?? path.join(repoRoot, '.kernel', 'artifacts', context.productId, context.surface.type);
  await fs.mkdir(outputDir, { recursive: true });
  const generatedAt = new Date().toISOString();
  const manifest = {
    schemaVersion: '1.0.0',
    productId: context.productId,
    phase: context.phase,
    surface: context.surface.type,
    adapter: adapterId,
    generatedAt,
    source: {
      path: context.surface.path,
      ...(typeof context.metadata?.gitCommit === 'string' ? { gitCommit: context.metadata.gitCommit } : {}),
      ...(typeof context.metadata?.gitBranch === 'string' ? { gitBranch: context.metadata.gitBranch } : {}),
    },
    trustState: {
      status: 'verified',
      verifiedAt: generatedAt,
      validation: 'expected-output-validation',
    },
    artifacts: await Promise.all(artifacts.map(async (artifact) => artifactManifestEntry(
      repoRoot,
      artifact,
      artifactType,
      adapterId,
      context,
      generatedAt,
    ))),
  };
  const manifestPath = path.join(outputDir, 'artifact-manifest.json');
  const tempManifestPath = path.join(outputDir, `artifact-manifest.${process.pid}.${Date.now()}.tmp`);
  await fs.writeFile(tempManifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
  await fs.rename(tempManifestPath, manifestPath);
  return path.relative(repoRoot, manifestPath).replace(/\\/g, '/');
}

async function artifactManifestEntry(
  repoRoot: string,
  artifact: string,
  artifactType: string,
  adapterId: string,
  context: ToolchainAdapterContext,
  generatedAt: string,
): Promise<Record<string, unknown>> {
  const absoluteArtifact = path.isAbsolute(artifact) ? artifact : path.join(repoRoot, artifact);
  const stats = await fs.stat(absoluteArtifact);
  const fingerprint = stats.isDirectory()
    ? await hashDirectory(absoluteArtifact)
    : await hashFile(absoluteArtifact);
  return {
    id: artifactId(context.productId, context.surface.type, artifact),
    path: artifact,
    type: artifactType,
    expected: true,
    found: true,
    directory: stats.isDirectory(),
    sizeBytes: stats.isDirectory() ? await directorySize(absoluteArtifact) : stats.size,
    fingerprint: {
      algorithm: 'sha256',
      hash: fingerprint,
    },
    metadata: {
      type: artifactType,
      productId: context.productId,
      phase: context.phase,
      surfaceType: context.surface.type,
      adapter: adapterId,
      generatedAt,
      sourcePath: context.surface.path,
    },
  };
}

async function hashFile(filePath: string): Promise<string> {
  const content = await fs.readFile(filePath);
  return createHash('sha256').update(content).digest('hex');
}

async function hashDirectory(directoryPath: string): Promise<string> {
  const hash = createHash('sha256');
  for (const filePath of await listFiles(directoryPath)) {
    const relativePath = path.relative(directoryPath, filePath).replace(/\\/g, '/');
    hash.update(relativePath);
    hash.update('\0');
    hash.update(await hashFile(filePath));
    hash.update('\0');
  }
  return hash.digest('hex');
}

async function directorySize(directoryPath: string): Promise<number> {
  let size = 0;
  for (const filePath of await listFiles(directoryPath)) {
    size += (await fs.stat(filePath)).size;
  }
  return size;
}

async function listFiles(directoryPath: string): Promise<string[]> {
  const entries = await fs.readdir(directoryPath, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const entryPath = path.join(directoryPath, entry.name);
    if (entry.isDirectory()) {
      files.push(...await listFiles(entryPath));
    } else if (entry.isFile()) {
      files.push(entryPath);
    }
  }
  return files.sort((left, right) => left.localeCompare(right));
}

function artifactId(productId: string, surfaceType: string, artifact: string): string {
  return createHash('sha256')
    .update(`${productId}|${surfaceType}|${artifact}`)
    .digest('hex')
    .slice(0, 24);
}

export function createEnvironmentBlockedClassifier(
  error: Error,
  adapterId: string,
  toolName: string,
): LifecycleFailureClassifier {
  const message = error.message.toLowerCase();
  const missingTool = message.includes('enoent') || message.includes('not recognized') || message.includes('not found');
  return {
    category: missingTool ? 'infrastructure' : 'adapter',
    severity: missingTool ? 'high' : 'medium',
    retryable: false,
    requiresHumanIntervention: true,
    remediationSteps: [
      `Install ${toolName} in the execution environment`,
      `Verify ${toolName} is available on PATH for Kernel lifecycle runs`,
      `Re-run the ${adapterId} preflight before executing the lifecycle phase`,
    ],
    relatedFailureCodes: [missingTool ? `${adapterId}-toolchain-missing` : `${adapterId}-failure`],
    component: adapterId,
  };
}

export async function exists(targetPath: string): Promise<boolean> {
  try {
    await fs.access(targetPath);
    return true;
  } catch {
    return false;
  }
}

export function defaultTimeoutMs(phase: ProductLifecyclePhase): number {
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

function emptyCommandResult(): CommandResult {
  return { exitCode: 0, stdout: '', stderr: '', durationMs: 0, pid: 0 };
}
