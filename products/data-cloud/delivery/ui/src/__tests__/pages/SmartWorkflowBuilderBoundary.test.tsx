import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import { smartWorkflowGenerationBoundary } from "@/components/common/unsupportedSurfaceRegistry";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SmartWorkflowBuilder } from "../../pages/SmartWorkflowBuilder";
import { TestWrapper } from "../test-utils/wrapper";

vi.mock("../../api/surfaces.service", () => ({
  useSurfaceRegistry: () => ({
    data: {
      generatedAt: "2026-04-17T12:00:00Z",
      requestId: "req-smart-workflow",
      tenantId: TEST_TENANT_ID,
      surfaces: [
        {
          key: "ai_assist",
          label: "AI Assist",
          status: "UNAVAILABLE",
          summary: "UNAVAILABLE",
          detail: smartWorkflowGenerationBoundary.details[1],
          rawValue: "UNAVAILABLE",
        },
      ],
    },
  }),
  getSurfaceSignal: (
    surfaces: Array<{ key: string }> | undefined,
    aliases: string[],
  ) => surfaces?.find((surface) => aliases.includes(surface.key)),
}));

describe("SmartWorkflowBuilder boundary", () => {
  it("renders the shared unsupported boundary when AI draft generation is unavailable", async () => {
    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    fireEvent.change(screen.getByPlaceholderText(/Load data from S3/i), {
      target: {
        value: "Load data from S3, clean email addresses, save to PostgreSQL",
      },
    });
    fireEvent.click(screen.getByRole("button", { name: /Generate Draft/i }));

    expect(
      await screen.findByText(/Temporarily unavailable/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(smartWorkflowGenerationBoundary.summary),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText(smartWorkflowGenerationBoundary.details[1]).length,
    ).toBeGreaterThan(0);
  }, 30000);
});
