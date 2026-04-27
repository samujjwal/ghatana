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
