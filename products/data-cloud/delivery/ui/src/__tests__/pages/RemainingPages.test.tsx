/**
 * Tests for remaining Data Cloud pages:
 * WorkflowsPage, WorkflowDesigner, WorkflowList, NotFound
 */
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TestWrapper } from "../test-utils/wrapper";

vi.mock("../../features/workflow/components/WorkflowCanvas", () => ({
  WorkflowCanvas: () => (
    <div data-testid="workflow-canvas">workflow-canvas</div>
  ),
}));

import { NotFound } from "../../pages/NotFound/index";
import { WorkflowDesigner } from "../../pages/WorkflowDesigner/index";
import { WorkflowList } from "../../pages/WorkflowList/index";
import { WorkflowsPage } from "../../pages/WorkflowsPage";

// ── WorkflowsPage ─────────────────────────────────────────────────────────────

describe("WorkflowsPage", () => {
  it("renders the workflows shell with search and create controls", () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    expect(
      screen.getByRole("heading", { name: "Workflows" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Keep the list about outcomes/i),
    ).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/Search workflows/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /New Pipeline/i }),
    ).toBeInTheDocument();
  });
});

// ── WorkflowDesigner ──────────────────────────────────────────────────────────

describe("WorkflowDesigner", () => {
  it("delegates rendering to the workflow canvas component", () => {
    render(<WorkflowDesigner />, { wrapper: TestWrapper });

    expect(screen.getByText(/Advanced Pipeline Editor/i)).toBeInTheDocument();
    expect(screen.getByTestId("workflow-canvas")).toBeInTheDocument();
  });
});

// ── WorkflowList ──────────────────────────────────────────────────────────────

describe("WorkflowList", () => {
  it("acts as a redirect-only compatibility shim", () => {
    render(<WorkflowList />, { wrapper: TestWrapper });

    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(screen.queryByText(/Legacy Workflow List/i)).toBeNull();
  });
});

// ── NotFound ──────────────────────────────────────────────────────────────────

describe("NotFound", () => {
  it("renders the 404 boundary shell with a recovery action", () => {
    render(<NotFound />, { wrapper: TestWrapper });

    expect(screen.getByRole("heading", { name: "404" })).toBeInTheDocument();
    expect(screen.getByText("Page Not Found")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Go to Home/i }),
    ).toBeInTheDocument();
  });
});
