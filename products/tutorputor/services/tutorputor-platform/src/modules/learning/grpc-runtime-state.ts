/**
 * Learner Profile gRPC Runtime State
 *
 * Shared runtime state shape used by startup, health, and learning-module
 * observability to report whether the optional learner-profile gRPC listener
 * is enabled and actually serving traffic.
 *
 * @doc.type module
 * @doc.purpose Define learner-profile gRPC runtime health state
 * @doc.layer product
 * @doc.pattern State Model
 */

export type LearnerProfileGrpcRuntimeStatus =
  | "disabled"
  | "starting"
  | "running"
  | "stopped"
  | "failed";

export interface LearnerProfileGrpcRuntimeState {
  enabled: boolean;
  status: LearnerProfileGrpcRuntimeStatus;
  address?: string;
  port?: number;
  startedAt?: string;
  lastError?: string;
}

export function createLearnerProfileGrpcRuntimeState(): LearnerProfileGrpcRuntimeState {
  return {
    enabled: false,
    status: "disabled",
  };
}
