import { describe, expect, it } from "vitest";
import {
  AUTHORING_WORKFLOW_STEPS,
  getAuthoringWorkflowCurrentStep,
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
