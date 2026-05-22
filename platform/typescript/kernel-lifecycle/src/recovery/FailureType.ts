/**
 * Failure type classification for recovery mapping.
 *
 * @doc.type type
 * @doc.purpose Classify failure types for recovery mapping
 * @doc.layer kernel-lifecycle
 * @doc.pattern Enumeration
 */

export type FailureCategory =
  | "adapter-preflight"
  | "adapter-plan"
  | "adapter-execute"
  | "policy-evaluation"
  | "policy-denied"
  | "interaction-timeout"
  | "interaction-handler-error"
  | "evidence-write-failure"
  | "toolchain-missing"
  | "dependency-missing"
  | "configuration-error"
  | "permission-error"
  | "network-error"
  | "resource-exhaustion"
  | "unknown";

export type FailureSource =
  | "cargo-rust-adapter"
  | "python-pyproject-adapter"
  | "pnpm-node-api-adapter"
  | "gradle-java-adapter"
  | "interaction-broker"
  | "event-broker"
  | "policy-evaluator"
  | "evidence-writer"
  | "lifecycle-executor"
  | "unknown";

export interface FailureClassification {
  readonly category: FailureCategory;
  readonly source: FailureSource;
  readonly reasonCode: string;
  readonly context: Record<string, unknown>;
}
