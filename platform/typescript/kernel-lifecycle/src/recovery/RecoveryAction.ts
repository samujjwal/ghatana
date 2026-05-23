/**
 * Recovery action definitions for failure recovery.
 *
 * @doc.type type
 * @doc.purpose Define recovery actions for failure types
 * @doc.layer kernel-lifecycle
 * @doc.pattern ValueObject
 */

export type RecoveryActionType =
  | "install-dependency"
  | "configure-toolchain"
  | "fix-permission"
  | "retry-with-backoff"
  | "clear-cache"
  | "update-dependencies"
  | "fix-configuration"
  | "check-network"
  | "increase-resources"
  | "contact-support"
  | "manual-intervention"
  | "address-gate"
  | "regenerate-artifact";

export interface RecoveryAction {
  readonly type: RecoveryActionType;
  readonly description: string;
  readonly command?: string;
  readonly steps: readonly string[];
  readonly estimatedDuration: string;
  readonly automated: boolean;
}
