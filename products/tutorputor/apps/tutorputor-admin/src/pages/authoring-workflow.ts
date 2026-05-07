export type AuthoringWorkflowStepId =
  | "describe-intent"
  | "generate-plan"
  | "review-plan"
  | "generate-artifacts"
  | "validate-gaps"
  | "author-approval"
  | "publish-with-provenance";

export interface AuthoringWorkflowStep {
  id: AuthoringWorkflowStepId;
  label: string;
  description: string;
}

export const AUTHORING_WORKFLOW_STEPS: readonly AuthoringWorkflowStep[] = [
  {
    id: "describe-intent",
    label: "Describe intent",
    description: "Author defines goals, audience, and constraints.",
  },
  {
    id: "generate-plan",
    label: "System generates plan",
    description: "AI drafts sequence, claims, and assessment strategy.",
  },
  {
    id: "review-plan",
    label: "Author reviews plan",
    description: "Author accepts or adjusts generated structure.",
  },
  {
    id: "generate-artifacts",
    label: "System auto-generates artifacts",
    description: "Claims, tasks, simulations, and drafts are generated.",
  },
  {
    id: "validate-gaps",
    label: "System validates and explains gaps",
    description: "Validator checks quality, safety, and factual grounding.",
  },
  {
    id: "author-approval",
    label: "Author approves/revises/escalates",
    description: "Author finalizes content or escalates issues.",
  },
  {
    id: "publish-with-provenance",
    label: "Publish with provenance",
    description: "Artifact ships with traceable source context.",
  },
] as const;

export type AuthoringContentStatus = "draft" | "review" | "published" | "archived";

export interface AuthoringWorkflowContext {
  hasSelectedContent: boolean;
  contentStatus?: AuthoringContentStatus;
}

export function getAuthoringWorkflowCurrentStep(
  context: AuthoringWorkflowContext,
): number {
  if (!context.hasSelectedContent) {
    return 0;
  }

  switch (context.contentStatus) {
    case "review":
      return 5;
    case "published":
    case "archived":
      return 6;
    case "draft":
    default:
      return 3;
  }
}

export type PublishReadinessStageId =
  | "draft"
  | "review"
  | "qa"
  | "accessibility"
  | "publish";

export interface PublishReadinessStage {
  id: PublishReadinessStageId;
  label: string;
  requiredCheckIds: readonly string[];
}

export interface PublishReadinessCheck {
  checkId: string;
  passed: boolean;
  severity: "error" | "warning" | "info";
  name: string;
  message: string;
  suggestion?: string;
}

export interface PublishReadinessAction {
  stageId: PublishReadinessStageId;
  checkId: string;
  label: string;
  message: string;
}

export const PUBLISH_READINESS_STAGES: readonly PublishReadinessStage[] = [
  {
    id: "draft",
    label: "Draft",
    requiredCheckIds: [
      "learning-claims-present",
      "simulation-configured",
      "assessment-coverage",
      "telemetry-enabled",
      "ai-use-disclosure",
    ],
  },
  {
    id: "review",
    label: "Review",
    requiredCheckIds: ["sme-review-complete"],
  },
  {
    id: "qa",
    label: "QA",
    requiredCheckIds: ["qa-review-complete"],
  },
  {
    id: "accessibility",
    label: "Accessibility",
    requiredCheckIds: ["accessibility-notes-present", "accessibility-review-complete"],
  },
  {
    id: "publish",
    label: "Publish",
    requiredCheckIds: ["no-unresolved-validation-errors"],
  },
] as const;

const FIX_ACTION_LABELS: Record<string, string> = {
  "learning-claims-present": "Add learning claims",
  "simulation-configured": "Configure simulation block",
  "assessment-coverage": "Map assessments to claims and evidence",
  "telemetry-enabled": "Enable telemetry capture",
  "ai-use-disclosure": "Configure AI-use disclosure",
  "sme-review-complete": "Complete SME review",
  "qa-review-complete": "Complete QA review",
  "accessibility-notes-present": "Add accessibility notes",
  "accessibility-review-complete": "Complete accessibility review",
  "no-unresolved-validation-errors": "Resolve validation errors",
};

export function getPublishReadinessActions(
  checks: readonly PublishReadinessCheck[],
): PublishReadinessAction[] {
  const checksById = new Map(checks.map((check) => [check.checkId, check]));

  return PUBLISH_READINESS_STAGES.flatMap((stage) =>
    stage.requiredCheckIds.flatMap((checkId) => {
      const check = checksById.get(checkId);
      if (check?.passed) {
        return [];
      }

      return [
        {
          stageId: stage.id,
          checkId,
          label: check?.suggestion ?? FIX_ACTION_LABELS[checkId] ?? "Resolve publish gate",
          message: check?.message ?? `${stage.label} gate is incomplete.`,
        },
      ];
    }),
  );
}

export function getPublishReadinessCurrentStage(
  checks: readonly PublishReadinessCheck[],
): number {
  const checksById = new Map(checks.map((check) => [check.checkId, check]));
  const blockedStageIndex = PUBLISH_READINESS_STAGES.findIndex((stage) =>
    stage.requiredCheckIds.some((checkId) => !checksById.get(checkId)?.passed),
  );

  return blockedStageIndex === -1
    ? PUBLISH_READINESS_STAGES.length - 1
    : blockedStageIndex;
}
