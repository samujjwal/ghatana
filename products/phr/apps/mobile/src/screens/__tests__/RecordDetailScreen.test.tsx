/**
 * Unit tests for RecordDetailScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from "react";
import { render } from "@testing-library/react-native";
import { RecordDetailScreen } from "../RecordDetailScreen";
import type { MobileRecord, MobileSession } from "../../types";

const record: MobileRecord = {
  id: "rec-42",
  title: "Hemoglobin A1c",
  summary: "HbA1c: 6.1% — within target range.",
  fhirPreview: '{"resourceType":"Observation","status":"final"}',
};

const session: MobileSession = {
  principalId: "patient-1",
  tenantId: "tenant-1",
  role: "patient",
  name: "Test Patient",
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
};

describe("RecordDetailScreen", () => {
  function renderedText(rendered: { toJSON: () => unknown }): string {
    return JSON.stringify(rendered.toJSON());
  }

  function pressNode(node: { props: Record<string, unknown> }): void {
    const onPress = node.props.onPress;
    if (typeof onPress !== "function") {
      throw new Error("Expected rendered node to expose an onPress handler.");
    }
    onPress();
  }

  it("renders record title (type field)", () => {
    const rendered = render(
      <RecordDetailScreen
        record={record}
        onBack={() => {}}
        session={session}
      />,
    );
    expect(renderedText(rendered)).toContain("Hemoglobin A1c");
  });

  it("renders record summary", () => {
    const rendered = render(
      <RecordDetailScreen
        record={record}
        onBack={() => {}}
        session={session}
      />,
    );
    expect(renderedText(rendered)).toContain("HbA1c: 6.1%");
    expect(renderedText(rendered)).toContain("within target range.");
  });

  it("renders FHIR preview code", () => {
    const rendered = render(
      <RecordDetailScreen
        record={record}
        onBack={() => {}}
        session={session}
      />,
    );
    expect(renderedText(rendered)).toContain("resourceType");
    expect(renderedText(rendered)).toContain("Observation");
    expect(renderedText(rendered)).toContain("final");
  });

  it("renders the record ID", () => {
    const rendered = render(
      <RecordDetailScreen
        record={record}
        onBack={() => {}}
        session={session}
      />,
    );
    expect(renderedText(rendered)).toContain("rec-42");
  });

  it("calls onBack when back button is pressed", () => {
    const onBack = jest.fn();
    const { UNSAFE_getByProps } = render(
      <RecordDetailScreen record={record} onBack={onBack} session={session} />,
    );
    pressNode(UNSAFE_getByProps({ accessibilityLabel: "Back" }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
