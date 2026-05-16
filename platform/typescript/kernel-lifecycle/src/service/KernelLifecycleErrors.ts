import type { ProductLifecyclePhase } from '../domain/ProductLifecyclePhase.js';

export interface KernelLifecycleErrorOptions {
  readonly reasonCode: string;
  readonly message: string;
  readonly correlationId?: string;
  readonly productUnitId?: string;
  readonly runId?: string;
  readonly phase?: ProductLifecyclePhase;
  readonly safeDetails?: Record<string, unknown>;
  readonly cause?: unknown;
}

export interface LifecycleTruthReadErrorOptions {
  readonly correlationId?: string;
  readonly productUnitId?: string;
  readonly runId?: string;
  readonly phase?: ProductLifecyclePhase;
  readonly filePath: string;
  readonly operation: string;
  readonly cause?: unknown;
}

export class KernelLifecycleError extends Error {
  readonly reasonCode: string;
  readonly correlationId: string | undefined;
  readonly productUnitId: string | undefined;
  readonly runId: string | undefined;
  readonly phase: ProductLifecyclePhase | undefined;
  readonly safeDetails: Record<string, unknown> | undefined;

  constructor(options: KernelLifecycleErrorOptions) {
    super(options.message, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = new.target.name;
    this.reasonCode = options.reasonCode;
    this.correlationId = options.correlationId;
    this.productUnitId = options.productUnitId;
    this.runId = options.runId;
    this.phase = options.phase;
    this.safeDetails = options.safeDetails;
  }

  toSafeResponse(): Record<string, unknown> {
    return {
      reasonCode: this.reasonCode,
      message: this.message,
      ...(this.correlationId === undefined ? {} : { correlationId: this.correlationId }),
      ...(this.productUnitId === undefined ? {} : { productUnitId: this.productUnitId }),
      ...(this.runId === undefined ? {} : { runId: this.runId }),
      ...(this.phase === undefined ? {} : { phase: this.phase }),
      ...(this.safeDetails === undefined ? {} : { safeDetails: this.safeDetails }),
    };
  }
}

export class ProductUnitNotFoundError extends KernelLifecycleError {
  constructor(productUnitId: string, correlationId?: string) {
    super({
      reasonCode: 'product-unit-not-found',
      message: `ProductUnit not found: ${productUnitId}`,
      productUnitId,
      ...(correlationId === undefined ? {} : { correlationId }),
    });
  }
}

export class LifecycleNotEnabledError extends KernelLifecycleError {
  constructor(productUnitId: string, correlationId?: string) {
    super({
      reasonCode: 'lifecycle-not-enabled',
      message: `ProductUnit lifecycle is not enabled: ${productUnitId}`,
      productUnitId,
      ...(correlationId === undefined ? {} : { correlationId }),
    });
  }
}

export class ProviderUnavailableError extends KernelLifecycleError {
  constructor(message: string, options: Omit<KernelLifecycleErrorOptions, 'reasonCode' | 'message'> = {}) {
    super({ ...options, reasonCode: 'provider-unavailable', message });
  }
}

export class ManifestNotFoundError extends KernelLifecycleError {
  constructor(message: string, options: Omit<KernelLifecycleErrorOptions, 'reasonCode' | 'message'> = {}) {
    super({ ...options, reasonCode: 'manifest-not-found', message });
  }
}

export class ApprovalRequiredError extends KernelLifecycleError {
  constructor(message: string, options: Omit<KernelLifecycleErrorOptions, 'reasonCode' | 'message'> = {}) {
    super({ ...options, reasonCode: 'approval-required', message });
  }
}

export class GateFailedError extends KernelLifecycleError {
  constructor(message: string, options: Omit<KernelLifecycleErrorOptions, 'reasonCode' | 'message'> = {}) {
    super({ ...options, reasonCode: 'gate-failed', message });
  }
}

export class ArtifactMissingError extends KernelLifecycleError {
  constructor(message: string, options: Omit<KernelLifecycleErrorOptions, 'reasonCode' | 'message'> = {}) {
    super({ ...options, reasonCode: 'artifact-missing', message });
  }
}

export class ExecutionFailedError extends KernelLifecycleError {
  constructor(message: string, options: Omit<KernelLifecycleErrorOptions, 'reasonCode' | 'message'> = {}) {
    super({ ...options, reasonCode: 'execution-failed', message });
  }
}

export class LifecycleTruthReadError extends KernelLifecycleError {
  constructor(options: LifecycleTruthReadErrorOptions) {
    super({
      reasonCode: 'lifecycle-truth-read-failed',
      message: `Lifecycle truth read failed during ${options.operation}: ${options.filePath}`,
      ...(options.correlationId === undefined ? {} : { correlationId: options.correlationId }),
      ...(options.productUnitId === undefined ? {} : { productUnitId: options.productUnitId }),
      ...(options.runId === undefined ? {} : { runId: options.runId }),
      ...(options.phase === undefined ? {} : { phase: options.phase }),
      safeDetails: {
        filePath: options.filePath,
        operation: options.operation,
        reasonCode: 'lifecycle-truth-read-failed',
      },
      ...(options.cause === undefined ? {} : { cause: options.cause }),
    });
  }
}

export class LifecycleManifestCorruptError extends KernelLifecycleError {
  constructor(options: LifecycleTruthReadErrorOptions) {
    super({
      reasonCode: 'lifecycle-manifest-corrupt',
      message: `Lifecycle manifest is corrupt or unparseable during ${options.operation}: ${options.filePath}`,
      ...(options.correlationId === undefined ? {} : { correlationId: options.correlationId }),
      ...(options.productUnitId === undefined ? {} : { productUnitId: options.productUnitId }),
      ...(options.runId === undefined ? {} : { runId: options.runId }),
      ...(options.phase === undefined ? {} : { phase: options.phase }),
      safeDetails: {
        filePath: options.filePath,
        operation: options.operation,
        reasonCode: 'lifecycle-manifest-corrupt',
      },
      ...(options.cause === undefined ? {} : { cause: options.cause }),
    });
  }
}

export class LifecycleRunIndexUnavailableError extends KernelLifecycleError {
  constructor(options: LifecycleTruthReadErrorOptions) {
    super({
      reasonCode: 'lifecycle-run-index-unavailable',
      message: `Lifecycle run index is unavailable during ${options.operation}: ${options.filePath}`,
      ...(options.correlationId === undefined ? {} : { correlationId: options.correlationId }),
      ...(options.productUnitId === undefined ? {} : { productUnitId: options.productUnitId }),
      ...(options.runId === undefined ? {} : { runId: options.runId }),
      ...(options.phase === undefined ? {} : { phase: options.phase }),
      safeDetails: {
        filePath: options.filePath,
        operation: options.operation,
        reasonCode: 'lifecycle-run-index-unavailable',
      },
      ...(options.cause === undefined ? {} : { cause: options.cause }),
    });
  }
}
