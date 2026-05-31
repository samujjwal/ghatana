import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import {
  SMART_WORKFLOW_AI_ASSIST_DEGRADED_DETAIL,
  SMART_WORKFLOW_AI_ASSIST_DEGRADED_TITLE,
  SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_DETAIL,
  SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_TITLE,
} from "@/lib/runtime-boundaries";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TestWrapper } from "../test-utils/wrapper";

const { mockCapabilities } = vi.hoisted(() => ({
  mockCapabilities: {
    useSurfaceRegistry: vi.fn(),
  },
}));

const { mockAi, mockWorkflows, mockNavigate } = vi.hoisted(() => ({
  mockAi: {
    generateWorkflowDraft: vi.fn(),
  },
  mockWorkflows: {
    create: vi.fn(),
  },
  mockNavigate: vi.fn(),
}));

vi.mock("../../api/surfaces.service", () => ({
  useSurfaceRegistry: mockCapabilities.useSurfaceRegistry,
  getSurfaceSignal: (
    surfaces: Array<{ key: string }> | undefined,
    aliases: string[],
  ) => surfaces?.find((surface) => aliases.includes(surface.key)),
}));

vi.mock("../../lib/api/ai", () => ({
  generateWorkflowDraft: mockAi.generateWorkflowDraft,
}));

vi.mock("../../lib/api/workflows", () => ({
  workflowsApi: mockWorkflows,
}));

vi.mock("react-router", async () => {
  const actual =
    await vi.importActual<typeof import("react-router")>("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

import SessionBootstrap from "../../lib/auth/session";
import { SmartWorkflowBuilder } from "../../pages/SmartWorkflowBuilder";

describe("SmartWorkflowBuilder", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    SessionBootstrap.setTenantId(TEST_TENANT_ID);
  });

  it("shows an unavailable state when ai assist is not configured", () => {
    mockCapabilities.useSurfaceRegistry.mockReturnValue({
      data: {
        generatedAt: "2026-04-17T12:00:00Z",
        requestId: "req-builder",
        tenantId: TEST_TENANT_ID,
        surfaces: [
          {
            key: "ai.assist",
            label: "AI Assist",
            status: "UNAVAILABLE",
            summary: "NOT_CONFIGURED",
            detail: SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_DETAIL,
            rawValue: "NOT_CONFIGURED",
          },
        ],
      },
    });

    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    expect(
      screen.getByText(SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_TITLE),
    ).toBeInTheDocument();
    expect(
      screen.getByText(SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_DETAIL),
    ).toBeInTheDocument();
  });

  it("shows a degraded warning when ai assist is partially available", () => {
    mockCapabilities.useSurfaceRegistry.mockReturnValue({
      data: {
        generatedAt: "2026-04-17T12:00:00Z",
        requestId: "req-builder",
        tenantId: TEST_TENANT_ID,
        surfaces: [
          {
            key: "ai.assist",
            label: "AI Assist",
            status: "DEGRADED",
            summary: "DEGRADED",
            detail: SMART_WORKFLOW_AI_ASSIST_DEGRADED_DETAIL,
            rawValue: "DEGRADED",
          },
        ],
      },
    });

    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    expect(
      screen.getByText(SMART_WORKFLOW_AI_ASSIST_DEGRADED_TITLE),
    ).toBeInTheDocument();
    expect(
      screen.getByText(SMART_WORKFLOW_AI_ASSIST_DEGRADED_DETAIL),
    ).toBeInTheDocument();
  });

  it("generates a runtime-backed workflow draft and persists it on deploy", async () => {
    mockCapabilities.useSurfaceRegistry.mockReturnValue({
      data: {
        generatedAt: "2026-04-17T12:00:00Z",
        requestId: "req-builder",
        tenantId: TEST_TENANT_ID,
        surfaces: [
          {
            key: "ai.assist",
            label: "AI Assist",
            status: "LIVE",
            summary: "AVAILABLE",
            detail: "Ready",
            rawValue: "AVAILABLE",
          },
        ],
      },
    });
    mockAi.generateWorkflowDraft.mockResolvedValue({
      data: {
        draft: {
          workflowId: "draft-123",
          name: "Customer sync workflow",
          description:
            "Load customer data, validate fields, store in warehouse",
          reviewRequired: true,
          provenance: {
            generatedAt: "2026-04-18T10:00:00Z",
            strategy: "llm",
            promptSummary: "Load customer data and store validated records.",
          },
          steps: [
            {
              id: "step-1",
              type: "source",
              name: "Load customer data",
              description: "Read customer records from the ingestion source.",
              confidence: 0.88,
              config: {},
            },
            {
              id: "step-2",
              type: "transform",
              name: "Validate records",
              description: "Check required customer fields before publication.",
              confidence: 0.7,
              config: {},
            },
            {
              id: "step-3",
              type: "destination",
              name: "Store in warehouse",
              description: "Persist validated data to the warehouse.",
              confidence: 0.82,
              config: {},
            },
          ],
        },
        confidence: 0.71,
        fallback: false,
        model: "gpt-4o",
      },
      status: 200,
    });
    mockWorkflows.create.mockResolvedValue({ id: "pipeline-42" });

    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    fireEvent.change(screen.getByPlaceholderText(/Load data from S3/i), {
      target: {
        value: "Load customer data, validate fields, store in warehouse",
      },
    });
    fireEvent.click(screen.getByRole("button", { name: /Generate Draft/i }));

    expect(
      await screen.findByText(/Customer sync workflow/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Review required before deployment/i),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /^Deploy$/i }));

    await waitFor(() => {
      expect(mockWorkflows.create).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "Customer sync workflow",
          tags: ["smart-builder", "ai-generated"],
          nodes: expect.arrayContaining([
            expect.objectContaining({ id: "step-1", type: "source" }),
            expect.objectContaining({ id: "step-2", type: "transform" }),
            expect.objectContaining({ id: "step-3", type: "destination" }),
          ]),
        }),
      );
    });
    expect(mockNavigate).toHaveBeenCalledWith("/pipelines/pipeline-42");
  }, 30000);
});
