import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import {
  ArtifactManifestGenerator,
  type ArtifactEntryInput,
  type ArtifactPackaging,
  type ArtifactType,
} from '@ghatana/kernel-artifacts';
import {
  DeploymentManifestGenerator,
  DeploymentManifestSchema,
  type DeploymentEnvironment,
  type DeploymentTargetType,
} from '@ghatana/kernel-deployment';
import type { KernelLifecycleProviderContext } from '@ghatana/kernel-product-contracts';
import { validateKernelLifecycleEvent } from '@ghatana/kernel-product-contracts';
import type {
  ProductArtifact,
  ProductLifecycleManifestRefs,
  ProductLifecycleManifestType,
  ProductLifecyclePhase,
  ProductLifecycleResult,
} from '../domain/ProductLifecyclePhase.js';

export interface LifecycleManifestWriterOptions {
  readonly outputDirectory: string;
  readonly providerContext?: KernelLifecycleProviderContext;
}

export interface LifecycleManifestWriteRequest {
  readonly result: ProductLifecycleResult;
  readonly requiredManifests: readonly ProductLifecycleManifestType[];
  readonly environment?: string;
}

export interface LifecycleManifestWriteResult {
  readonly result: ProductLifecycleResult;
  readonly manifestRefs: ProductLifecycleManifestRefs;
  readonly failure?: {
    readonly reasonCode: 'manifest-write-failed';
    readonly stepId: string;
    readonly message: string;
    readonly cause?: string;
  };
}

interface ManifestWriteOutcome {
  readonly manifestRefs: ProductLifecycleManifestRefs;
  readonly failures: string[];
}

const DEPLOYMENT_ENVIRONMENTS = ['local', 'dev', 'staging', 'prod'] as const;

type DeploymentManifestSurfaceStatus = 'pending' | 'in-progress' | 'deployed' | 'failed' | 'rolled-back';
type WritableManifestType = Exclude<ProductLifecycleManifestType, 'lifecycle-result'>;

export class LifecycleManifestWriter {
  private readonly outputDirectory: string;
  private readonly providerContext: KernelLifecycleProviderContext | undefined;
  private readonly artifactManifestGenerator = new ArtifactManifestGenerator();
  private readonly deploymentManifestGenerator = new DeploymentManifestGenerator();

  constructor(options: LifecycleManifestWriterOptions) {
    this.outputDirectory = options.outputDirectory;
    this.providerContext = options.providerContext;
  }

  async writeRequiredManifests(
    request: LifecycleManifestWriteRequest,
  ): Promise<LifecycleManifestWriteResult> {
    const lifecycleResultRequired = request.requiredManifests.includes('lifecycle-result');
    const requiredManifests = request.requiredManifests.filter(
      (manifest): manifest is WritableManifestType => manifest !== 'lifecycle-result',
    );
    const outcome = await this.writeManifests({
      ...request,
      requiredManifests,
    });
    const mergedRefs = {
      ...(request.result.manifestRefs ?? {}),
      ...outcome.manifestRefs,
    };
    let nextResult: ProductLifecycleResult = {
      ...request.result,
      manifestRefs: mergedRefs,
      ...(mergedRefs.lifecycleEvents !== undefined ? { eventsRef: mergedRefs.lifecycleEvents } : {}),
      ...(mergedRefs.lifecycleHealthSnapshot !== undefined ? { healthSnapshotRef: mergedRefs.lifecycleHealthSnapshot } : {}),
    };

    if (outcome.failures.length > 0) {
      nextResult = {
        ...nextResult,
        status: 'failed',
        failure: {
          reasonCode: 'manifest-write-failed',
          stepId: 'manifest-writer',
          message: `Required manifest write failed: ${outcome.failures.join('; ')}`,
        },
      };
    }

    const lifecycleResultFailure = await this.writeLifecycleResultIfRequired(
      lifecycleResultRequired,
      nextResult,
      mergedRefs,
    );
    if (lifecycleResultFailure !== undefined) {
      outcome.failures.push(lifecycleResultFailure);
      nextResult = {
        ...nextResult,
        status: 'failed',
        failure: {
          reasonCode: 'manifest-write-failed',
          stepId: 'manifest-writer',
          message: `Required manifest write failed: ${outcome.failures.join('; ')}`,
        },
      };
    }

    if (outcome.failures.length === 0) {
      return { result: nextResult, manifestRefs: mergedRefs };
    }

    return {
      result: nextResult,
      manifestRefs: mergedRefs,
      failure: {
        reasonCode: 'manifest-write-failed',
        stepId: 'manifest-writer',
        message: `Required manifest write failed: ${outcome.failures.join('; ')}`,
      },
    };
  }

  private async writeLifecycleResultIfRequired(
    required: boolean,
    result: ProductLifecycleResult,
    refs: ProductLifecycleManifestRefs,
  ): Promise<string | undefined> {
    if (!required) {
      return undefined;
    }
    try {
      const lifecycleResultWrite = await this.writeLifecycleResult(result);
      refs.lifecycleResult = lifecycleResultWrite.ref;
      await this.writeLifecycleResult({
        ...result,
        manifestRefs: refs,
      });
      return undefined;
    } catch (error) {
      return `lifecycle-result: ${stringifyError(error)}`;
    }
  }

  private async writeManifests(
    request: Omit<LifecycleManifestWriteRequest, 'requiredManifests'> & {
      readonly requiredManifests: readonly WritableManifestType[];
    },
  ): Promise<ManifestWriteOutcome> {
    const manifestRefs: ProductLifecycleManifestRefs = {};
    const failures: string[] = [];

    for (const manifestType of request.requiredManifests) {
      const result = await this.writeManifestByType(manifestType, request, manifestRefs);
      if (result.ref !== undefined) {
        this.setManifestRef(manifestRefs, manifestType, result.ref);
      }
      if (result.error !== undefined) {
        failures.push(result.error);
      }
    }

    return { manifestRefs, failures };
  }

  private async writeManifestByType(
    manifestType: WritableManifestType,
    request: Omit<LifecycleManifestWriteRequest, 'requiredManifests'> & {
      readonly requiredManifests: readonly WritableManifestType[];
    },
    manifestRefs: ProductLifecycleManifestRefs,
  ): Promise<{ readonly ref?: string; readonly error?: string }> {
    try {
      switch (manifestType) {
        case 'artifact-manifest':
          return await this.writeArtifactManifest(request.result);
        case 'deployment-manifest':
          return await this.writeDeploymentManifest(request.result, request.environment, manifestRefs);
        case 'verify-health-report':
          return await this.writeVerifyHealthReport(request.result);
        case 'lifecycle-health-snapshot':
          return await this.writeLifecycleHealthSnapshot(request.result);
        case 'gate-result-manifest':
          return await this.writeGateResultManifest(request.result);
        case 'lifecycle-events':
          return await this.writeLifecycleEventsManifest(request.result);
        case 'lifecycle-plan':
          return { ref: this.relativeManifestPath(manifestType) };
      }
    } catch (error) {
      return {
        error: `${manifestType}: ${stringifyError(error)}`,
      };
    }
  }

  private async writeArtifactManifest(
    result: ProductLifecycleResult,
  ): Promise<{ readonly ref: string }> {
    const artifacts = result.artifacts.map((artifact) => this.toArtifactEntry(artifact, result));
    const surface = this.singleSurface(result.artifacts);
    const manifest = this.artifactManifestGenerator.createManifest({
      productId: result.productId,
      phase: result.phase,
      surface,
      artifacts,
    });
    this.artifactManifestGenerator.validateManifest(manifest);

    const manifestPath = this.manifestPath(result.productId, result.runId, result.phase, 'artifact-manifest.json');
    await this.writeJson(manifestPath, manifest);
    await this.recordArtifactManifestProviderRef(result, manifestPath, manifest.artifacts.length);
    return { ref: manifestPath };
  }

  private async writeDeploymentManifest(
    result: ProductLifecycleResult,
    environment: string | undefined,
    manifestRefs: ProductLifecycleManifestRefs,
  ): Promise<{ readonly ref: string }> {
    const deploymentEnvironment = this.normalizeDeploymentEnvironment(environment);
    const surfaces = result.artifacts.length > 0
      ? result.artifacts.map((artifact) => ({
          surface: artifact.surface,
          status: this.toDeploymentStatus(result.status),
          artifactId: artifact.id,
          deploymentTarget: this.deploymentTargetForPhase(result.phase),
        }))
      : [
          {
            surface: 'product',
            status: this.toDeploymentStatus(result.status),
            artifactId: 'none',
            deploymentTarget: this.deploymentTargetForPhase(result.phase),
          },
        ];

    const manifest = this.deploymentManifestGenerator.createManifest({
      productId: result.productId,
      version: result.runId,
      environment: deploymentEnvironment,
      surfaces,
      rollbackPlan: {
        strategy: 'previous-artifact',
        targetVersion: result.runId,
        reason: 'Lifecycle deployment rollback plan',
        steps: ['restore previous artifact', 'verify health'],
      },
      target: this.deploymentTargetForPhase(result.phase),
      ...(manifestRefs.artifactManifest !== undefined
        ? { artifactManifestRef: manifestRefs.artifactManifest }
        : {}),
    });
    DeploymentManifestSchema.parse(manifest);

    const manifestPath = this.manifestPath(result.productId, result.runId, result.phase, 'deployment-manifest.json');
    await this.writeJson(manifestPath, manifest);
    return { ref: manifestPath };
  }

  private async writeVerifyHealthReport(
    result: ProductLifecycleResult,
  ): Promise<{ readonly ref: string }> {
    const report = {
      schemaVersion: '1.0.0',
      productId: result.productId,
      runId: result.runId,
      phase: result.phase,
      status: result.status,
      checkedAt: result.completedAt,
      steps: result.steps.map((step) => ({
        stepId: step.stepId,
        status: step.status,
        durationMs: step.durationMs,
      })),
    };
    const manifestPath = this.manifestPath(result.productId, result.runId, result.phase, 'verify-health-report.json');
    await this.writeJson(manifestPath, report);
    return { ref: manifestPath };
  }

  private async writeLifecycleHealthSnapshot(
    result: ProductLifecycleResult,
  ): Promise<{ readonly ref: string }> {
    const snapshot = {
      schemaVersion: '1.0.0',
      productId: result.productId,
      runId: result.runId,
      phase: result.phase,
      status: result.status === 'succeeded' ? 'healthy' : result.status,
      snapshotAt: result.completedAt,
    };
    const manifestPath = this.manifestPath(
      result.productId,
      result.runId,
      result.phase,
      'lifecycle-health-snapshot.json',
    );
    await this.writeJson(manifestPath, snapshot);
    await this.recordHealthProviderRef(result, manifestPath, snapshot.status);
    return { ref: manifestPath };
  }

  private async writeGateResultManifest(
    result: ProductLifecycleResult,
  ): Promise<{ readonly ref: string }> {
    const manifest = {
      schemaVersion: '1.0.0',
      productId: result.productId,
      runId: result.runId,
      phase: result.phase,
      status: result.status,
      gates: result.gates,
      writtenAt: new Date().toISOString(),
    };
    const manifestPath = this.manifestPath(result.productId, result.runId, result.phase, 'gate-result-manifest.json');
    await this.writeJson(manifestPath, manifest);
    return { ref: manifestPath };
  }

  private async writeLifecycleEventsManifest(
    result: ProductLifecycleResult,
  ): Promise<{ readonly ref: string }> {
    const events = this.providerContext?.events === undefined
      ? []
      : await this.providerContext.events.listEvents({
          productUnitId: result.productId,
          runId: result.runId,
          ...(result.correlationId !== undefined ? { correlationId: result.correlationId } : {}),
        });
    const invalidEvent = events.find((event) => !validateKernelLifecycleEvent(event).valid);
    if (invalidEvent !== undefined) {
      throw new Error(`lifecycle events provider returned invalid event ${invalidEvent.metadata.eventId}`);
    }

    const manifest = {
      schemaVersion: '1.0.0',
      productId: result.productId,
      runId: result.runId,
      phase: result.phase,
      eventCount: events.length,
      events,
      writtenAt: new Date().toISOString(),
    };
    const manifestPath = this.manifestPath(result.productId, result.runId, result.phase, 'lifecycle-events.json');
    await this.writeJson(manifestPath, manifest);
    return { ref: manifestPath };
  }

  private async writeLifecycleResult(
    result: ProductLifecycleResult,
  ): Promise<{ readonly ref: string }> {
    const manifestPath = this.manifestPath(result.productId, result.runId, result.phase, 'lifecycle-result.json');
    await this.writeJson(manifestPath, result);
    return { ref: manifestPath };
  }

  private async recordArtifactManifestProviderRef(
    result: ProductLifecycleResult,
    manifestPath: string,
    artifactCount: number,
  ): Promise<void> {
    const provider = this.providerContext?.artifacts;
    if (provider === undefined) {
      return;
    }
    const providerResult = await provider.recordArtifactManifest(
      {
        productUnitId: result.productId,
        runId: result.runId,
        manifestPath,
        artifactCount,
      },
      {
        required: true,
        correlationId: result.correlationId ?? result.runId,
      },
    );
    if (!providerResult.success) {
      throw new Error(providerResult.error ?? 'artifact provider write failed');
    }
  }

  private async recordHealthProviderRef(
    result: ProductLifecycleResult,
    snapshotPath: string,
    status: string,
  ): Promise<void> {
    const provider = this.providerContext?.health;
    if (provider === undefined) {
      return;
    }
    const providerResult = await provider.recordHealthSnapshot(
      {
        productUnitId: result.productId,
        runId: result.runId,
        status,
        snapshotPath,
      },
      {
        required: true,
        correlationId: result.correlationId ?? result.runId,
      },
    );
    if (!providerResult.success) {
      throw new Error(providerResult.error ?? 'health provider write failed');
    }
  }

  private toArtifactEntry(
    artifact: ProductArtifact,
    result: ProductLifecycleResult,
  ): ArtifactEntryInput {
    return {
      id: artifact.id,
      path: artifact.path,
      metadata: {
        type: this.artifactType(artifact.type),
        packaging: this.artifactPackaging(artifact.type),
        version: result.runId,
        gitCommit: undefined,
        gitBranch: undefined,
        timestamp: result.completedAt,
        sizeBytes: artifact.sizeBytes ?? 0,
      },
      fingerprint: this.artifactFingerprint(artifact.fingerprint),
      expected: true,
    };
  }

  private artifactFingerprint(value: string): { readonly algorithm: 'sha256' | 'sha512'; readonly hash: string } {
    if (value.startsWith('sha512:')) {
      return { algorithm: 'sha512', hash: value.slice('sha512:'.length) };
    }
    if (value.startsWith('sha256:')) {
      return { algorithm: 'sha256', hash: value.slice('sha256:'.length) };
    }
    return { algorithm: 'sha256', hash: value };
  }

  private artifactType(value: string): ArtifactType {
    const allowed: readonly ArtifactType[] = [
      'jvm-service',
      'jvm-library',
      'node-service',
      'static-web-bundle',
      'container-image',
      'mobile-bundle',
      'sdk-package',
      'domain-pack',
      'test-report',
      'coverage-report',
      'source-map',
      'documentation',
    ];
    return allowed.includes(value as ArtifactType) ? (value as ArtifactType) : 'documentation';
  }

  private artifactPackaging(type: string): ArtifactPackaging {
    switch (type) {
      case 'jvm-service':
      case 'jvm-library':
        return 'jar';
      case 'static-web-bundle':
        return 'static-files';
      case 'container-image':
        return 'container';
      case 'node-service':
      case 'sdk-package':
        return 'npm';
      case 'test-report':
      case 'coverage-report':
      case 'documentation':
      case 'domain-pack':
      default:
        return 'json';
    }
  }

  private setManifestRef(
    refs: ProductLifecycleManifestRefs,
    manifestType: ProductLifecycleManifestType,
    ref: string,
  ): void {
    if (manifestType === 'lifecycle-plan') refs.lifecyclePlan = ref;
    if (manifestType === 'lifecycle-events') refs.lifecycleEvents = ref;
    if (manifestType === 'gate-result-manifest') refs.gateResultManifest = ref;
    if (manifestType === 'artifact-manifest') refs.artifactManifest = ref;
    if (manifestType === 'deployment-manifest') refs.deploymentManifest = ref;
    if (manifestType === 'verify-health-report') refs.verifyHealthReport = ref;
    if (manifestType === 'lifecycle-health-snapshot') refs.lifecycleHealthSnapshot = ref;
  }

  private manifestPath(
    productId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    fileName: string,
  ): string {
    return path.join(this.outputDirectory, productId, runId, phase, fileName);
  }

  private relativeManifestPath(manifestType: ProductLifecycleManifestType): string {
    return path.join(this.outputDirectory, `${manifestType}.json`);
  }

  private singleSurface(artifacts: readonly ProductArtifact[]): string {
    const firstSurface = artifacts[0]?.surface;
    if (firstSurface === undefined) {
      return 'product';
    }
    return artifacts.every((artifact) => artifact.surface === firstSurface)
      ? firstSurface
      : 'multiple';
  }

  private normalizeDeploymentEnvironment(environment: string | undefined): DeploymentEnvironment {
    if (environment !== undefined && DEPLOYMENT_ENVIRONMENTS.includes(environment as DeploymentEnvironment)) {
      return environment as DeploymentEnvironment;
    }
    return 'local';
  }

  private toDeploymentStatus(status: ProductLifecycleResult['status']): DeploymentManifestSurfaceStatus {
    if (status === 'succeeded') {
      return 'deployed';
    }
    if (status === 'failed') {
      return 'failed';
    }
    return 'pending';
  }

  private deploymentTargetForPhase(_phase: ProductLifecyclePhase): DeploymentTargetType {
    return 'compose-local';
  }

  private async writeJson(filePath: string, value: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(value, null, 2)}\n`, 'utf-8');
    await fs.rename(tempPath, filePath);
  }
}

function stringifyError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
