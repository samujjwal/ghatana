import { describe, expect, it } from "vitest";
import {
  AUTHORING_WORKFLOW_STEPS,
  PUBLISH_READINESS_STAGES,
  getAuthoringWorkflowCurrentStep,
  getPublishReadinessActions,
  getPublishReadinessCurrentStage,
  type PublishReadinessCheck,
} from "../authoring-workflow";

describe("authoring workflow helper", () => {
  it("exposes the canonical seven-step author journey", () => {
    expect(AUTHORING_WORKFLOW_STEPS).toHaveLength(7);
    expect(AUTHORING_WORKFLOW_STEPS[0]?.id).toBe("describe-intent");
    expect(AUTHORING_WORKFLOW_STEPS[6]?.id).toBe("publish-with-provenance");
  });

  it("starts at describe-intent when no content is selected", () => {
    expect(
      getAuthoringWorkflowCurrentStep({
        hasSelectedContent: false,
      }),
    ).toBe(0);
  });

  it("moves to generation stage for draft content", () => {
    expect(
      getAuthoringWorkflowCurrentStep({
        hasSelectedContent: true,
        contentStatus: "draft",
      }),
    ).toBe(3);
  });

  it("moves to approval stage for review content", () => {
    expect(
      getAuthoringWorkflowCurrentStep({
        hasSelectedContent: true,
        contentStatus: "review",
      }),
    ).toBe(5);
  });

  it("moves to publish step for published and archived content", () => {
    expect(
      getAuthoringWorkflowCurrentStep({
        hasSelectedContent: true,
        contentStatus: "published",
      }),
    ).toBe(6);

    expect(
      getAuthoringWorkflowCurrentStep({
        hasSelectedContent: true,
        contentStatus: "archived",
      }),
    ).toBe(6);
  });
});

describe("publish readiness workflow helper", () => {
  const completeChecks: PublishReadinessCheck[] = PUBLISH_READINESS_STAGES.flatMap(
    (stage) =>
      stage.requiredCheckIds.map((checkId) => ({
        checkId,
        passed: true,
        severity: "info",
        name: checkId,
        message: "Passed",
      })),
  );

  it("exposes the canonical Draft to Publish quality gate sequence", () => {
    expect(PUBLISH_READINESS_STAGES.map((stage) => stage.id)).toEqual([
      "draft",
      "review",
      "qa",
      "accessibility",
      "publish",
    ]);
  });

  it("starts at draft when claims, simulation, assessment, telemetry, or AI disclosure are missing", () => {
    const checks = completeChecks.map((check) =>
      check.checkId === "simulation-configured"
        ? {
            ...check,
            passed: false,
            severity: "error" as const,
            message: "Simulation block is missing.",
          }
        : check,
    );

    expect(getPublishReadinessCurrentStage(checks)).toBe(0);
    expect(getPublishReadinessActions(checks)).toContainEqual(
      expect.objectContaining({
        stageId: "draft",
        checkId: "simulation-configured",
        label: "Configure simulation block",
      }),
    );
  });

  it("moves to review when draft gates pass but SME review is incomplete", () => {
    const checks = completeChecks.map((check) =>
      check.checkId === "sme-review-complete"
        ? {
            ...check,
            passed: false,
            severity: "error" as const,
            message: "SME review has not been completed.",
          }
        : check,
    );

    expect(getPublishReadinessCurrentStage(checks)).toBe(1);
    expect(getPublishReadinessActions(checks)[0]).toMatchObject({
      stageId: "review",
      checkId: "sme-review-complete",
      label: "Complete SME review",
    });
  });

  it("moves through QA and accessibility before publish", () => {
    const qaBlocked = completeChecks.map((check) =>
      check.checkId === "qa-review-complete"
        ? { ...check, passed: false, severity: "error" as const }
        : check,
    );
    const accessibilityBlocked = completeChecks.map((check) =>
      check.checkId === "accessibility-review-complete"
        ? { ...check, passed: false, severity: "error" as const }
        : check,
    );

    expect(getPublishReadinessCurrentStage(qaBlocked)).toBe(2);
    expect(getPublishReadinessCurrentStage(accessibilityBlocked)).toBe(3);
  });

  it("lands on publish when every quality gate passes", () => {
    expect(getPublishReadinessCurrentStage(completeChecks)).toBe(4);
    expect(getPublishReadinessActions(completeChecks)).toEqual([]);
  });
});
