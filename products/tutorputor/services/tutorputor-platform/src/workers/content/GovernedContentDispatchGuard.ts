/**
 * Governed Content Dispatch Guard
 *
 * Pre-flight safety barrier applied before every content generation gRPC
 * dispatch in the Tutorputor content worker.  Mirrors the role of the
 * Java-side {@code GovernedAgentDispatcher} for the TypeScript worker plane.
 *
 * Responsibilities:
 * - Release guard: rejects dispatch when the product is not yet in an
 *   approved release tier (candidate-mode enforcement).
 * - Safety invariant check: ensures the job type is within the governed
 *   set of allowed content generation types.
 * - Tenant scoping: hard-blocks any dispatch lacking a valid tenant id.
 * - Structured audit emission: writes a machine-readable dispatch decision
 *   entry via the provided logger so it can be correlated in observability
 *   tooling.
 *
 * @doc.type class
 * @doc.purpose Pre-flight release and safety gate for content generation dispatch
 * @doc.layer product
 * @doc.pattern GovernanceGuard
 */

import type { Logger } from "pino";
import type { GenerationRequestExecutionJobData } from "../../modules/content/generation/queue-dispatcher.js";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type DispatchDecision =
  | { readonly allowed: true }
  | { readonly allowed: false; readonly reason: string; readonly code: DispatchBlockCode };

export type DispatchBlockCode =
  | "TENANT_MISSING"
  | "JOB_TYPE_NOT_GOVERNED"
  | "RELEASE_GUARD_CANDIDATE"
  | "SAFETY_INVARIANT_VIOLATION";

export interface GovernedContentDispatchGuardConfig {
  readonly productReleaseStatus: "candidate" | "hardened" | "production";
  readonly allowInCandidateMode: boolean;
  readonly logger: Logger;
}

// ---------------------------------------------------------------------------
// Governed job types — the exhaustive set of content generation job types
// that have passed agent safety review and are permitted for governed dispatch.
// Any new job type must be added here after safety review.
// ---------------------------------------------------------------------------
const GOVERNED_JOB_TYPES: ReadonlySet<string> = new Set([
  "claim",
  "explainer",
  "worked_example",
  "simulation",
  "animation",
  "assessment",
  "evaluation",
]);

// ---------------------------------------------------------------------------
// Guard
// ---------------------------------------------------------------------------

export class GovernedContentDispatchGuard {
  private readonly productReleaseStatus: GovernedContentDispatchGuardConfig["productReleaseStatus"];
  private readonly allowInCandidateMode: boolean;
  private readonly logger: Logger;

  constructor(config: GovernedContentDispatchGuardConfig) {
    this.productReleaseStatus = config.productReleaseStatus;
    this.allowInCandidateMode = config.allowInCandidateMode;
    this.logger = config.logger;
  }

  /**
   * Evaluate whether the given job is permitted to proceed to gRPC dispatch.
   *
   * Must be called before every {@code grpcClient.*} invocation inside
   * {@code GenerationRequestJobProcessor}.
   */
  evaluate(jobData: GenerationRequestExecutionJobData): DispatchDecision {
    // 1. Tenant scoping invariant
    if (!jobData.tenantId || jobData.tenantId.trim().length === 0) {
      return this.block("TENANT_MISSING", "tenantId must be a non-empty string");
    }

    // 2. Job type invariant — only governed types are allowed
    if (!GOVERNED_JOB_TYPES.has(jobData.generationJobType)) {
      return this.block(
        "JOB_TYPE_NOT_GOVERNED",
        `Job type '${jobData.generationJobType}' is not in the governed dispatch set`,
      );
    }

    // 3. Release guard — candidate products are blocked unless explicitly
    //    allowed (used for local development / integration testing only)
    if (this.productReleaseStatus === "candidate" && !this.allowInCandidateMode) {
      return this.block(
        "RELEASE_GUARD_CANDIDATE",
        "Tutorputor product is in candidate status. Production dispatch is not permitted until release gates pass.",
      );
    }

    this.emitAudit(jobData, "ALLOWED");
    return { allowed: true };
  }

  // ---------------------------------------------------------------------------
  // Internal
  // ---------------------------------------------------------------------------

  private block(code: DispatchBlockCode, reason: string): DispatchDecision {
    this.logger.warn(
      {
        event: "governed_content_dispatch_blocked",
        code,
        reason,
      },
      "GovernedContentDispatchGuard: dispatch blocked",
    );
    return { allowed: false, reason, code };
  }

  private emitAudit(
    jobData: GenerationRequestExecutionJobData,
    verdict: "ALLOWED",
  ): void {
    this.logger.info(
      {
        event: "governed_content_dispatch_decision",
        verdict,
        tenantId: jobData.tenantId,
        generationRequestId: jobData.generationRequestId,
        generationJobId: jobData.generationJobId,
        generationJobType: jobData.generationJobType,
        productReleaseStatus: this.productReleaseStatus,
      },
      "GovernedContentDispatchGuard: dispatch allowed",
    );
  }
}
