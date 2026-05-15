import { describe, expect, it } from 'vitest';
import {
  ApprovalRequiredError,
  ArtifactMissingError,
  ExecutionFailedError,
  GateFailedError,
  KernelLifecycleError,
  LifecycleNotEnabledError,
  ManifestNotFoundError,
  ProductUnitNotFoundError,
  ProviderUnavailableError,
} from '../KernelLifecycleErrors.js';

describe('KernelLifecycleErrors', () => {
  it('serializes safe error details without leaking causes', () => {
    const error = new KernelLifecycleError({
      reasonCode: 'provider-unavailable',
      message: 'Provider write failed',
      correlationId: 'corr-1',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      safeDetails: { provider: 'runtimeTruth' },
      cause: new Error('disk path with internal detail'),
    });

    expect(error.toSafeResponse()).toEqual({
      reasonCode: 'provider-unavailable',
      message: 'Provider write failed',
      correlationId: 'corr-1',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      safeDetails: { provider: 'runtimeTruth' },
    });
  });

  it('assigns stable reason codes for lifecycle service errors', () => {
    expect(new ProductUnitNotFoundError('missing').reasonCode).toBe('product-unit-not-found');
    expect(new LifecycleNotEnabledError('planned').reasonCode).toBe('lifecycle-not-enabled');
    expect(new ProviderUnavailableError('missing provider').reasonCode).toBe('provider-unavailable');
    expect(new ManifestNotFoundError('missing manifest').reasonCode).toBe('manifest-not-found');
    expect(new ApprovalRequiredError('approval needed').reasonCode).toBe('approval-required');
    expect(new GateFailedError('gate failed').reasonCode).toBe('gate-failed');
    expect(new ArtifactMissingError('artifact missing').reasonCode).toBe('artifact-missing');
    expect(new ExecutionFailedError('execution failed').reasonCode).toBe('execution-failed');
  });
});
