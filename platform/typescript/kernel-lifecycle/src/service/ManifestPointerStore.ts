import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type {
  ProductLifecycleManifestRefs,
  ProductLifecycleManifestType,
  ProductLifecyclePhase,
} from '../domain/ProductLifecyclePhase.js';
import { KernelLifecycleError, ManifestNotFoundError } from './KernelLifecycleErrors.js';

const NO_MANIFEST_RUN_DIRECTORIES: readonly string[] = Object.freeze([]);

export interface ManifestPointers extends ProductLifecycleManifestRefs {
  readonly runId: string;
  readonly correlationId: string;
  readonly providerMode: 'bootstrap' | 'platform';
}

export interface ManifestPointerStoreOptions {
  readonly repoRoot: string;
  readonly outputRoot?: string;
  readonly allowOutputOutsideKernelOut?: boolean;
}

export class ManifestPointerStore {
  private readonly repoRoot: string;
  private readonly outputRoot: string;
  private readonly allowOutputOutsideKernelOut: boolean;

  constructor(options: ManifestPointerStoreOptions) {
    this.repoRoot = path.resolve(options.repoRoot);
    this.outputRoot = path.resolve(
      this.repoRoot,
      options.outputRoot ?? path.join('.kernel', 'out'),
    );
    this.allowOutputOutsideKernelOut = options.allowOutputOutsideKernelOut ?? false;
    this.assertSafeOutputRoot();
  }

  latestDirectory(productUnitId: string, phase: ProductLifecyclePhase): string {
    return path.join(this.outputRoot, 'products', productUnitId, phase, 'latest');
  }

  async writeLatestPointers(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    pointers: ManifestPointers,
  ): Promise<void> {
    const latestDir = this.latestDirectory(productUnitId, phase);
    await fs.mkdir(latestDir, { recursive: true });
    await this.writeAtomic(path.join(latestDir, 'run-id.txt'), pointers.runId);
    await this.writeAtomic(
      path.join(latestDir, 'manifest-pointers.json'),
      `${JSON.stringify(pointers, null, 2)}\n`,
    );
  }

  async readLatestRunId(productUnitId: string, phase: ProductLifecyclePhase): Promise<string> {
    try {
      return (await fs.readFile(
        path.join(this.latestDirectory(productUnitId, phase), 'run-id.txt'),
        'utf-8',
      )).trim();
    } catch (error) {
      throw new KernelLifecycleError({
        reasonCode: 'run-not-found',
        message: `No latest lifecycle run found for ${productUnitId}/${phase}`,
        productUnitId,
        phase,
        cause: error,
      });
    }
  }

  async readLatestPointers(
    productUnitId: string,
    phase: ProductLifecyclePhase,
  ): Promise<ManifestPointers> {
    const pointerPath = path.join(this.latestDirectory(productUnitId, phase), 'manifest-pointers.json');
    try {
      return JSON.parse(await fs.readFile(pointerPath, 'utf-8')) as ManifestPointers;
    } catch (error) {
      throw new ManifestNotFoundError(
        `No latest manifest pointers found for ${productUnitId}/${phase}`,
        { productUnitId, phase, safeDetails: { pointerPath }, cause: error },
      );
    }
  }

  async listRunDirectories(
    productUnitId: string,
    phase?: ProductLifecyclePhase,
  ): Promise<readonly string[]> {
    const root = phase === undefined
      ? path.join(this.outputRoot, 'products', productUnitId)
      : path.join(this.outputRoot, 'products', productUnitId, phase);
    try {
      const entries = await fs.readdir(root, { withFileTypes: true });
      return entries
        .filter((entry) => entry.isDirectory() && entry.name !== 'latest')
        .map((entry) => path.join(root, entry.name))
        .sort();
    } catch {
      return Array.from(NO_MANIFEST_RUN_DIRECTORIES);
    }
  }

  async resolveManifestByType(
    productUnitId: string,
    runId: string,
    phase: ProductLifecyclePhase,
    manifestType: ProductLifecycleManifestType,
  ): Promise<string> {
    const pointerPath = path.join(
      this.outputRoot,
      'products',
      productUnitId,
      phase,
      'latest',
      'manifest-pointers.json',
    );
    const pointers = await this.readJsonIfExists<Partial<ManifestPointers>>(pointerPath);
    const fromPointers = pointers === null ? undefined : this.refForManifestType(pointers, manifestType);
    const candidate = fromPointers ?? path.join(
      this.outputRoot,
      'products',
      productUnitId,
      phase,
      runId,
      `${manifestType}.json`,
    );
    const resolved = path.isAbsolute(candidate)
      ? path.resolve(candidate)
      : path.resolve(this.outputRoot, candidate);
    this.assertInsideOutputRoot(resolved);
    try {
      await fs.access(resolved);
      return resolved;
    } catch (error) {
      throw new ManifestNotFoundError(
        `Manifest not found: ${manifestType} for ${productUnitId}/${phase}/${runId}`,
        { productUnitId, runId, phase, safeDetails: { manifestType }, cause: error },
      );
    }
  }

  private refForManifestType(
    refs: Partial<ManifestPointers>,
    manifestType: ProductLifecycleManifestType,
  ): string | undefined {
    if (manifestType === 'lifecycle-plan') return refs.lifecyclePlan;
    if (manifestType === 'lifecycle-result') return refs.lifecycleResult;
    if (manifestType === 'lifecycle-events') return refs.lifecycleEvents;
    if (manifestType === 'gate-result-manifest') return refs.gateResultManifest;
    if (manifestType === 'artifact-manifest') return refs.artifactManifest;
    if (manifestType === 'deployment-manifest') return refs.deploymentManifest;
    if (manifestType === 'rollback-manifest') return refs.rollbackManifest;
    if (manifestType === 'verify-health-report') return refs.verifyHealthReport;
    if (manifestType === 'lifecycle-health-snapshot') return refs.lifecycleHealthSnapshot;
    return undefined;
  }

  private async readJsonIfExists<TValue>(filePath: string): Promise<TValue | null> {
    try {
      return JSON.parse(await fs.readFile(filePath, 'utf-8')) as TValue;
    } catch {
      return null;
    }
  }

  private async writeAtomic(filePath: string, content: string): Promise<void> {
    this.assertInsideOutputRoot(path.resolve(filePath));
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, content, 'utf-8');
    await fs.rename(tempPath, filePath);
  }

  private assertSafeOutputRoot(): void {
    if (this.allowOutputOutsideKernelOut) {
      return;
    }
    const expected = path.join(this.repoRoot, '.kernel', 'out');
    if (!this.isSamePathOrChild(this.outputRoot, expected)) {
      throw new KernelLifecycleError({
        reasonCode: 'unsafe-output-path',
        message: `Kernel lifecycle output root must be inside ${expected}; received ${this.outputRoot}`,
        safeDetails: { outputRoot: this.outputRoot },
      });
    }
  }

  private assertInsideOutputRoot(candidate: string): void {
    if (!this.isSamePathOrChild(candidate, this.outputRoot)) {
      throw new KernelLifecycleError({
        reasonCode: 'unsafe-output-path',
        message: `Manifest path escapes Kernel output root: ${candidate}`,
        safeDetails: { outputRoot: this.outputRoot },
      });
    }
  }

  private isSamePathOrChild(candidate: string, parent: string): boolean {
    const relativePath = path.relative(parent, candidate);
    return relativePath === '' || (!relativePath.startsWith('..') && !path.isAbsolute(relativePath));
  }
}
