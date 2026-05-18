/**
 * Agentic Development Review
 *
 * Governed review surface for agent-proposed ProductUnit lifecycle actions.
 *
 * @doc.type component
 * @doc.purpose Agent lifecycle action proposal review and approval
 * @doc.layer platform
 */

import { useMemo, useState, type ReactElement } from "react";
import {
  AgentLifecycleActionRequestSchema,
  AgentLifecycleActionResultSchema,
  type AgentLifecycleActionRequest,
  type AgentLifecycleActionResult,
} from "@ghatana/kernel-product-contracts";
import {
  Badge,
  Button,
  ConfidenceBadge,
  Typography,
} from "@ghatana/design-system";
import { useStudioTranslation } from "../i18n/studioTranslations";

type ReviewDecision = "pending" | "approved" | "rejected";

interface ProposalEvidence {
  readonly label: string;
  readonly ref: string;
  readonly provenanceRef: string;
  readonly confidence: number;
}

interface AuditEntry {
  readonly id: string;
  readonly event: string;
  readonly actor: string;
  readonly timestamp: string;
}

const ACTION_REQUEST: AgentLifecycleActionRequest =
  AgentLifecycleActionRequestSchema.parse({
    schemaVersion: "1.0.0",
    requestId: "agent-action-req-dm-001",
    correlationId: "corr-agent-action-dm-001",
    productUnitId: "digital-marketing",
    scope: {
      tenantId: "tenant-demo",
      workspaceId: "workspace-growth",
      projectId: "digital-marketing-pilot",
    },
    requestedByAgent: "aep-planner-agent",
    requestedByAgentVersion: "2026.05.0",
    masteryState: {
      state: "competent",
      stateRef: "datacloud://mastery/aep-planner-agent/2026.05.0",
      evaluatedAt: "2026-05-14T12:00:00.000Z",
    },
    policyDecision: {
      decisionId: "policy:agent-action-req-dm-001",
      decision: "requires-approval",
      evaluatedAt: "2026-05-14T12:00:00.000Z",
      reasonCodes: ["high-risk-requires-approval"],
      evidenceRefs: ["datacloud://evidence/risk-hotspots/dm-001"],
    },
    toolPermissions: [
      {
        toolId: "kernel.lifecycle.execute-phase",
        permissionRef:
          "datacloud://permissions/aep-planner-agent/execute-phase",
        granted: true,
        allowedActions: ["execute-lifecycle-phase"],
      },
    ],
    requestedAction: "execute-lifecycle-phase",
    lifecyclePhase: "build",
    proposedPlanRef:
      "datacloud://agent-actions/digital-marketing/plan/build-001",
    riskLevel: "high",
    approvalRequired: true,
    requiredApprovals: [
      {
        approvalId: "approval-product-owner",
        approverRole: "product-owner",
        required: true,
      },
      {
        approvalId: "approval-release-captain",
        approverRole: "release-captain",
        required: true,
      },
    ],
    requiredVerification: [
      {
        verificationId: "verification-policy",
        kind: "policy",
        required: true,
      },
      {
        verificationId: "verification-health",
        kind: "health",
        required: true,
      },
    ],
    inputRefs: ["datacloud://evidence/product-unit-intent/dm-001"],
    outputRefs: ["datacloud://lifecycle-runs/digital-marketing/build-001"],
    verificationProofRefs: ["datacloud://verification/policy/dm-001"],
    evidenceRefs: [
      "datacloud://evidence/product-unit-intent/dm-001",
      "datacloud://evidence/artifact-graph/dm-001",
      "datacloud://evidence/risk-hotspots/dm-001",
    ],
    rollbackPlanRef: "datacloud://rollback/digital-marketing/build-001",
    fallbackMode: "rollback",
  });

const ACTION_RESULT: AgentLifecycleActionResult =
  AgentLifecycleActionResultSchema.parse({
    schemaVersion: "1.0.0",
    resultId: "agent-action-result-dm-001",
    requestId: ACTION_REQUEST.requestId,
    correlationId: ACTION_REQUEST.correlationId,
    productUnitId: ACTION_REQUEST.productUnitId,
    policyDecision: "allowed",
    masteryDecision: "requires-approval",
    approvalDecision: "pending",
    lifecycleRunRef: "datacloud://lifecycle-runs/digital-marketing/build-001",
    evidenceRefs: [
      ...ACTION_REQUEST.evidenceRefs,
      "datacloud://agent-traces/corr-agent-action-dm-001",
    ],
    healthStatus: "degraded",
    rollbackReadiness: "ready",
    evaluatedAt: "2026-05-14T12:00:00.000Z",
    request: ACTION_REQUEST,
  });

const EVIDENCE: readonly ProposalEvidence[] = [
  {
    label: "ProductUnitIntent",
    ref: "datacloud://evidence/product-unit-intent/dm-001",
    provenanceRef: "provenance://agent-action/product-unit-intent/dm-001",
    confidence: 0.94,
  },
  {
    label: "Artifact graph",
    ref: "datacloud://evidence/artifact-graph/dm-001",
    provenanceRef: "provenance://agent-action/artifact-graph/dm-001",
    confidence: 0.88,
  },
  {
    label: "Risk hotspots",
    ref: "datacloud://evidence/risk-hotspots/dm-001",
    provenanceRef: "provenance://agent-action/risk-hotspots/dm-001",
    confidence: 0.79,
  },
] as const;

const INITIAL_AUDIT_TRAIL: readonly AuditEntry[] = [
  {
    id: "audit-1",
    event: "Proposal received",
    actor: "aep-planner-agent",
    timestamp: "2026-05-14 12:00 UTC",
  },
  {
    id: "audit-2",
    event: "Policy allowed",
    actor: "kernel-policy",
    timestamp: "2026-05-14 12:01 UTC",
  },
  {
    id: "audit-3",
    event: "Mastery requires approval",
    actor: "mastery-registry",
    timestamp: "2026-05-14 12:02 UTC",
  },
] as const;

function redactEvidenceRef(ref: string): string {
  const parts = ref.split("/");
  const last = parts.at(-1) ?? "";
  return last.length === 0 ? "***" : `.../${last}`;
}

export default function AIReviewConsole(): ReactElement {
  const [decision, setDecision] = useState<ReviewDecision>("pending");
  const t = useStudioTranslation();
  const auditTrail = useMemo<readonly AuditEntry[]>(() => {
    if (decision === "approved") {
      return [
        ...INITIAL_AUDIT_TRAIL,
        {
          id: "audit-approved",
          event: "Proposal approved",
          actor: "studio-reviewer",
          timestamp: "2026-05-14 12:05 UTC",
        },
      ];
    }
    if (decision === "rejected") {
      return [
        ...INITIAL_AUDIT_TRAIL,
        {
          id: "audit-rejected",
          event: "Proposal rejected",
          actor: "studio-reviewer",
          timestamp: "2026-05-14 12:05 UTC",
        },
      ];
    }
    return INITIAL_AUDIT_TRAIL;
  }, [decision]);

  return (
    <section className="p-6" aria-labelledby="agentic-review-title">
      <div className="studio-section space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <Typography
              id="agentic-review-title"
              variant="h2"
              className="text-2xl font-bold"
            >
              Agentic Development Review
            </Typography>
            <p className="mt-1 text-sm text-gray-600">
              Digital Marketing build proposal from{" "}
              {ACTION_REQUEST.requestedByAgent}
            </p>
          </div>
          <Badge
            tone={
              decision === "pending"
                ? "warning"
                : decision === "approved"
                  ? "success"
                  : "danger"
            }
            variant="soft"
          >
            {decision}
          </Badge>
        </div>

        <article
          className="studio-card space-y-5"
          aria-labelledby="proposal-title"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3
                id="proposal-title"
                className="text-base font-semibold text-gray-950"
              >
                {ACTION_REQUEST.requestedAction} /{" "}
                {ACTION_REQUEST.lifecyclePhase}
              </h3>
              <p className="mt-1 text-sm text-gray-600">
                {ACTION_REQUEST.proposedPlanRef}
              </p>
            </div>
            <Badge tone="danger" variant="soft">
              {ACTION_REQUEST.riskLevel} risk
            </Badge>
          </div>

          <div className="grid gap-3 md:grid-cols-4">
            <ReviewMetric label="Policy" value={ACTION_RESULT.policyDecision} />
            <ReviewMetric
              label="Mastery"
              value={ACTION_RESULT.masteryDecision}
            />
            <ReviewMetric
              label="Approval"
              value={
                decision === "pending"
                  ? ACTION_RESULT.approvalDecision
                  : decision
              }
            />
            <ReviewMetric
              label="Rollback"
              value={ACTION_RESULT.rollbackReadiness}
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <section aria-labelledby="evidence-title" className="space-y-3">
              <h4
                id="evidence-title"
                className="text-sm font-semibold text-gray-950"
              >
                Evidence
              </h4>
              <div className="space-y-2">
                {EVIDENCE.map((item: ProposalEvidence) => (
                  <div
                    key={item.ref}
                    className="rounded-md border border-gray-200 p-3"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-sm font-medium text-gray-950">
                        {item.label}
                      </span>
                      <ConfidenceBadge confidence={item.confidence} size="sm" />
                    </div>
                    <p
                      className="mt-1 break-all text-xs text-gray-600"
                      data-sensitive="redacted"
                    >
                      {t("studio.security.redactedValue")}:{" "}
                      {redactEvidenceRef(item.ref)}
                    </p>
                    <p className="mt-1 break-all text-xs text-gray-600">
                      {t("studio.security.provenance")}: {item.provenanceRef}
                    </p>
                  </div>
                ))}
              </div>
            </section>

            <section aria-labelledby="controls-title" className="space-y-3">
              <h4
                id="controls-title"
                className="text-sm font-semibold text-gray-950"
              >
                Controls
              </h4>
              <div className="rounded-md border border-gray-200 p-3">
                <dl className="grid gap-3 text-sm">
                  <ReviewDefinition
                    label="Lifecycle run"
                    value={ACTION_RESULT.lifecycleRunRef}
                  />
                  <ReviewDefinition
                    label="Verification"
                    value={ACTION_REQUEST.requiredVerification
                      .map((item) => item.kind)
                      .join(", ")}
                  />
                  <ReviewDefinition
                    label="Approvals"
                    value={ACTION_REQUEST.requiredApprovals
                      .map((item) => item.approverRole)
                      .join(", ")}
                  />
                  <ReviewDefinition
                    label="Health"
                    value={ACTION_RESULT.healthStatus}
                  />
                  <ReviewDefinition
                    label={t("studio.security.previewTrust")}
                    value="evidence-required"
                  />
                </dl>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="primary"
                  onClick={() => setDecision("approved")}
                >
                  Approve proposal
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => setDecision("rejected")}
                >
                  Reject proposal
                </Button>
              </div>
              <p className="text-sm text-gray-600">
                Raw command execution is absent; approved work resolves through
                Kernel lifecycle contracts.
              </p>
            </section>
          </div>

          <section aria-labelledby="audit-title" className="space-y-3">
            <h4
              id="audit-title"
              className="text-sm font-semibold text-gray-950"
            >
              Audit trail
            </h4>
            <ol className="space-y-2">
              {auditTrail.map((entry: AuditEntry) => (
                <li
                  key={entry.id}
                  className="rounded-md border border-gray-200 p-3"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="text-sm font-medium text-gray-950">
                      {entry.event}
                    </span>
                    <span className="text-xs text-gray-600">
                      {entry.timestamp}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-gray-600">{entry.actor}</p>
                </li>
              ))}
            </ol>
          </section>
        </article>
      </div>
    </section>
  );
}

interface ReviewMetricProps {
  readonly label: string;
  readonly value: string;
}

function ReviewMetric({ label, value }: ReviewMetricProps): ReactElement {
  return (
    <div className="rounded-md border border-gray-200 p-3">
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">
        {label}
      </p>
      <p className="mt-1 text-sm font-semibold text-gray-950">{value}</p>
    </div>
  );
}

interface ReviewDefinitionProps {
  readonly label: string;
  readonly value: string;
}

function ReviewDefinition({
  label,
  value,
}: ReviewDefinitionProps): ReactElement {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-gray-500">
        {label}
      </dt>
      <dd className="mt-1 break-all text-sm text-gray-900">{value}</dd>
    </div>
  );
}
