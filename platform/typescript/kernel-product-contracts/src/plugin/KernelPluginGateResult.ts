/**
 * KernelPluginGateResult - represents the result of a plugin gate evaluation.
 *
 * Gate results provide the outcome of plugin-based governance checks,
 * including pass/fail status and supporting evidence.
 *
 * @doc.type interface
 * @doc.purpose Plugin gate evaluation result representation
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

/**
 * Represents the result of a plugin gate evaluation.
 */
export interface KernelPluginGateResult {
  /**
   * Unique identifier for the gate result.
   */
  readonly id: string;

  /**
   * Plugin identifier that produced this result.
   */
  readonly pluginId: string;

  /**
   * Gate identifier.
   */
  readonly gateId: string;

  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Whether the gate passed.
   */
  readonly passed: boolean;

  /**
   * Timestamp of the gate evaluation.
   */
  readonly timestamp: string;

  /**
   * Duration of the gate evaluation in milliseconds.
   */
  readonly durationMs: number;

  /**
   * Message describing the result.
   */
  readonly message: string;

  /**
   * Evidence supporting the gate decision.
   */
  readonly evidence: GateEvidence[];

  /**
   * Severity level (if gate failed).
   */
  readonly severity?: "error" | "warning" | "info";

  /**
   * Additional metadata.
   */
  readonly metadata?: Record<string, unknown>;
}

/**
 * Evidence supporting a gate decision.
 */
export interface GateEvidence {
  /**
   * Type of evidence (e.g., "metric", "log", "scan-result").
   */
  readonly type: string;

  /**
   * Evidence identifier or key.
   */
  readonly key: string;

  /**
   * Evidence value or data.
   */
  readonly value: string | number | boolean | Record<string, unknown>;

  /**
   * Description of the evidence.
   */
  readonly description?: string;
}

/**
 * Type guard to check if a value is a KernelPluginGateResult.
 */
export function isKernelPluginGateResult(value: unknown): value is KernelPluginGateResult {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const result = value as Record<string, unknown>;
  return (
    typeof result.id === 'string' &&
    typeof result.pluginId === 'string' &&
    typeof result.gateId === 'string' &&
    typeof result.productUnitId === 'string' &&
    typeof result.runId === 'string' &&
    typeof result.passed === 'boolean' &&
    typeof result.timestamp === 'string' &&
    typeof result.durationMs === 'number' &&
    typeof result.message === 'string' &&
    Array.isArray(result.evidence)
  );
}
