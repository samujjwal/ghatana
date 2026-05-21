/**
 * Resume policy - determines when and how to resume interrupted lifecycle executions.
 *
 * This module provides contracts for resuming interrupted lifecycle executions,
 * enabling replay-safe recovery from failures.
 *
 * @doc.type module
 * @doc.purpose Resume policy contracts for lifecycle execution recovery
 * @doc.layer platform
 * @doc.pattern Policy
 */

import type { PhaseGraph, PhaseGraphNode, PhaseNodeState } from "./PhaseGraph.js";
import type { RunRecord } from "./RunStore.js";

/**
 * Resume decision - whether and how to resume an interrupted execution.
 */
export interface ResumeDecision {
  /**
   * Whether the execution can be resumed.
   */
  readonly canResume: boolean;

  /**
   * Reason for the decision.
   */
  readonly reason: string;

  /**
   * Nodes that can be re-executed (if resuming).
   */
  readonly reexecutableNodes: readonly PhaseGraphNode[];

  /**
   * Nodes that must be skipped (if resuming).
   */
  readonly skipNodes: readonly PhaseGraphNode[];

  /**
   * Strategy for resuming.
   */
  readonly strategy: ResumeStrategy;
}

/**
 * Resume strategy - how to resume the execution.
 */
export type ResumeStrategy =
  | "from-failed-node"
  | "from-beginning"
  | "skip-failed"
  | "no-resume";

/**
 * Resume policy configuration.
 */
export interface ResumePolicyConfig {
  /**
   * Maximum number of resume attempts for a run.
   */
  readonly maxResumeAttempts: number;

  /**
   * Whether to allow resuming from failed nodes (vs from beginning).
   */
  readonly allowResumeFromFailed: boolean;

  /**
   * Whether to skip failed nodes and continue.
   */
  readonly allowSkipFailed: boolean;

  /**
   * Maximum age of a run in hours before it cannot be resumed.
   */
  readonly maxRunAgeHours: number;

  /**
   * Node states that are considered safe to resume from.
   */
  readonly safeResumeStates: readonly PhaseNodeState[];
}

/**
 * Default resume policy configuration.
 */
export const DEFAULT_RESUME_POLICY_CONFIG: ResumePolicyConfig = {
  maxResumeAttempts: 3,
  allowResumeFromFailed: true,
  allowSkipFailed: false,
  maxRunAgeHours: 24,
  safeResumeStates: ["succeeded", "skipped"],
} as const satisfies ResumePolicyConfig;

/**
 * Resume policy interface - determines when and how to resume executions.
 */
export interface ResumePolicy {
  /**
   * Evaluate whether a run can be resumed.
   */
  canResume(runRecord: RunRecord, phaseGraph: PhaseGraph): Promise<ResumeDecision>;

  /**
   * Get the resume decision for a run.
   */
  getResumeDecision(runRecord: RunRecord, phaseGraph: PhaseGraph): Promise<ResumeDecision>;

  /**
   * Validate that a resume is safe.
   */
  validateResume(runRecord: RunRecord, phaseGraph: PhaseGraph): Promise<{
    readonly valid: boolean;
    readonly errors: readonly string[];
  }>;
}

/**
 * Default resume policy implementation.
 */
export class DefaultResumePolicy implements ResumePolicy {
  private readonly config: ResumePolicyConfig;

  constructor(config: Partial<ResumePolicyConfig> = {}) {
    this.config = { ...DEFAULT_RESUME_POLICY_CONFIG, ...config };
  }

  async canResume(runRecord: RunRecord, phaseGraph: PhaseGraph): Promise<ResumeDecision> {
    const decision = await this.getResumeDecision(runRecord, phaseGraph);
    return decision;
  }

  async getResumeDecision(runRecord: RunRecord, phaseGraph: PhaseGraph): Promise<ResumeDecision> {
    // Check if run is too old
    const runAge = Date.now() - new Date(runRecord.startedAt).getTime();
    const maxAgeMs = this.config.maxRunAgeHours * 60 * 60 * 1000;
    if (runAge > maxAgeMs) {
      return {
        canResume: false,
        reason: `Run is too old (${Math.floor(runAge / (60 * 60 * 1000))} hours > ${this.config.maxRunAgeHours} hours)`,
        reexecutableNodes: [],
        skipNodes: [],
        strategy: "no-resume",
      };
    }

    // Check if run is still running
    if (runRecord.status === "running") {
      return {
        canResume: false,
        reason: "Run is still in progress",
        reexecutableNodes: [],
        skipNodes: [],
        strategy: "no-resume",
      };
    }

    // Check if run succeeded
    if (runRecord.status === "succeeded") {
      return {
        canResume: false,
        reason: "Run already succeeded",
        reexecutableNodes: [],
        skipNodes: [],
        strategy: "no-resume",
      };
    }

    // Find failed nodes
    const failedNodes = phaseGraph.nodes.filter((n) => n.state === "failed");
    const succeededNodes = phaseGraph.nodes.filter((n) => n.state === "succeeded");

    // If no failed nodes, cannot resume (shouldn't happen but handle gracefully)
    if (failedNodes.length === 0) {
      return {
        canResume: false,
        reason: "No failed nodes found to resume from",
        reexecutableNodes: [],
        skipNodes: [],
        strategy: "no-resume",
      };
    }

    // Determine resume strategy
    if (this.config.allowResumeFromFailed) {
      return {
        canResume: true,
        reason: `Resume from ${failedNodes.length} failed node(s)`,
        reexecutableNodes: failedNodes,
        skipNodes: succeededNodes,
        strategy: "from-failed-node",
      };
    }

    if (this.config.allowSkipFailed) {
      return {
        canResume: true,
        reason: `Skip ${failedNodes.length} failed node(s) and continue`,
        reexecutableNodes: [],
        skipNodes: failedNodes,
        strategy: "skip-failed",
      };
    }

    // Default: resume from beginning
    return {
      canResume: true,
      reason: "Resume from beginning",
      reexecutableNodes: phaseGraph.nodes,
      skipNodes: [],
      strategy: "from-beginning",
    };
  }

  async validateResume(
    runRecord: RunRecord,
    phaseGraph: PhaseGraph,
  ): Promise<{
    readonly valid: boolean;
    readonly errors: readonly string[];
  }> {
    const errors: string[] = [];

    // Check run age
    const runAge = Date.now() - new Date(runRecord.startedAt).getTime();
    const maxAgeMs = this.config.maxRunAgeHours * 60 * 60 * 1000;
    if (runAge > maxAgeMs) {
      errors.push(`Run is too old (${Math.floor(runAge / (60 * 60 * 1000))} hours > ${this.config.maxRunAgeHours} hours)`);
    }

    // Check run status
    if (runRecord.status === "running") {
      errors.push("Run is still in progress");
    }

    if (runRecord.status === "succeeded") {
      errors.push("Run already succeeded");
    }

    // Check phase graph integrity
    if (!phaseGraph.nodes || phaseGraph.nodes.length === 0) {
      errors.push("Phase graph is empty or invalid");
    }

    // Check for nodes in unsafe states
    const unsafeNodes = phaseGraph.nodes.filter(
      (n) => !this.config.safeResumeStates.includes(n.state) && n.state !== "failed"
    );
    if (unsafeNodes.length > 0) {
      errors.push(
        `${unsafeNodes.length} node(s) in unsafe states: ${unsafeNodes.map((n) => n.nodeId).join(", ")}`
      );
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}
