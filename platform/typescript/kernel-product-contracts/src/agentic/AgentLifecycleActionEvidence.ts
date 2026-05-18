/**
 * AgentLifecycleActionEvidence - evidence submitted alongside an agent lifecycle action.
 *
 * Agents operating in agentic/autonomous mode must provide verifiable evidence.
 *
 * @doc.type module
 * @doc.purpose Agent lifecycle action evidence contract
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// Evidence kinds
// ---------------------------------------------------------------------------

export const AGENT_EVIDENCE_KINDS = [
  "test-run-report",
  "static-analysis-report",
  "security-scan-report",
  "artifact-fingerprint",
  "approval-record",
  "policy-evaluation-result",
  "health-snapshot",
  "deployment-verification",
  "provenance-record",
  "custom",
] as const;

export type AgentEvidenceKind = (typeof AGENT_EVIDENCE_KINDS)[number];

// ---------------------------------------------------------------------------
// AgentLifecycleActionEvidence
// ---------------------------------------------------------------------------

export const AgentLifecycleActionEvidenceSchema = z.object({
  evidenceId: z.string().min(1),
  kind: z.enum(AGENT_EVIDENCE_KINDS),
  /** Reference to the evidence artifact (path, URI, or manifest ref). */
  ref: z.string().min(1),
  capturedAt: z.string().datetime(),
  /**
   * When true, indicates that personally identifiable or sensitive
   * information in this evidence record has been redacted.
   */
  redacted: z.boolean().default(false),
  providedByAgentId: z.string().optional(),
  description: z.string().optional(),
});

export type AgentLifecycleActionEvidence = z.infer<
  typeof AgentLifecycleActionEvidenceSchema
>;

export function parseAgentLifecycleActionEvidence(
  input: unknown,
): AgentLifecycleActionEvidence {
  return AgentLifecycleActionEvidenceSchema.parse(input);
}
