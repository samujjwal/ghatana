/**
 * Tests for the Analytics page (InsightsPage).
 *
 * Supplements InsightsPage.test.tsx with additional scenario coverage.
 *
 * @doc.type test
 * @doc.purpose RTL tests for InsightsPage analytics scenarios
 * @doc.layer frontend
 */
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TestWrapper } from "../test-utils/wrapper";

const analyticsMocks = vi.hoisted(() => ({
  useAnalyticsQuery: vi.fn(),
  useCollectionEntityCounts: vi.fn(),
  useAnalyticsAiSuggestions: vi.fn(),
}));

vi.mock("../../api/brain.service", () => ({
  brainService: {
    getBrainStats: vi
      .fn()
      .mockResolvedValue({
        totalRecordsProcessed: 0,
        activePatterns: 0,
        hotTierRecords: 0,
      }),
  },
}));

vi.mock("../../api/cost.service", () => ({
  costService: {
    getCostAnalysis: vi.fn().mockResolvedValue({ total: 0 }),
  },
}));

vi.mock("../../lib/api/workflows", () => ({
  workflowsApi: {
    list: vi.fn().mockResolvedValue({ total: 0 }),
  },
}));

vi.mock("../../api/analytics.service", () => analyticsMocks);

vi.mock("../../lib/api/collections", () => ({
  collectionsApi: {
    list: vi.fn().mockResolvedValue({
      items: [
        { id: "orders", name: "orders" },
        { id: "customers", name: "customers" },
      ],
    }),
  },
}));

vi.mock("../../components/layout/PageLayout", () => ({
  PageHeader: ({ title }: { title: string }) => (
    <div>
      <h1>{title}</h1>
    </div>
  ),
  PageContent: ({
    children,
    contextSidebar,
  }: {
    children: React.ReactNode;
    contextSidebar?: React.ReactNode;
  }) => (
    <div>
      {children}
      {contextSidebar}
    </div>
  ),
  ContextSidebar: ({ children }: { children: React.ReactNode }) => (
    <section>{children}</section>
  ),
  ContextPanel: ({
    title,
    children,
  }: {
    title?: string;
    children: React.ReactNode;
  }) => (
    <aside>
      {title && <h2>{title}</h2>}
      {children}
    </aside>
  ),
  SuggestionCard: ({
    title,
    description,
  }: {
    title: string;
    description: string;
  }) => (
    <div>
      <span>{title}</span>
      <span>{description}</span>
    </div>
  ),
  StatCard: ({ label, value }: { label: string; value: React.ReactNode }) => (
    <div>
      <span>{label}</span>
      <span>{String(value)}</span>
    </div>
  ),
}));

vi.mock("../../components/brain/SpotlightRing", () => ({
  SpotlightRing: () => <div>Spotlight Ring</div>,
}));
vi.mock("../../components/brain/AutonomyTimeline", () => ({
  AutonomyTimeline: () => <div>Autonomy Timeline</div>,
}));

import { InsightsPage } from "../../pages/InsightsPage";

describe("AnalyticsPage — InsightsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    analyticsMocks.useCollectionEntityCounts.mockReturnValue({
      data: [
        { collection: "orders", count: 14, executionTimeMs: 5 },
        { collection: "customers", count: 6, executionTimeMs: 4 },
      ],
      isLoading: false,
    });
    analyticsMocks.useAnalyticsAiSuggestions.mockReturnValue({
      data: [
        {
          key: "warn-1",
          type: "warning",
          title: "Freshness lag detected",
          description: "orders has not refreshed in 6h",
          confidence: 0,
          reasons: ["stale"],
          fallback: true,
        },
      ],
      isLoading: false,
    });
    analyticsMocks.useAnalyticsQuery.mockReturnValue({
      mutate: vi.fn(),
      data: undefined,
      isPending: false,
      error: null,
      reset: vi.fn(),
    });
  });

  it("renders canonical analytics collection summaries and anomaly hints", async () => {
    render(<InsightsPage />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole("tab", { name: /analytics/i }));

    expect(
      await screen.findByText("Entity Distribution by Collection"),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(
        within(
          screen.getByText("Total Entities").closest("div") as HTMLElement,
        ).getByText("20"),
      ).toBeInTheDocument();
      expect(screen.getByText("orders")).toBeInTheDocument();
      expect(screen.getByText("customers")).toBeInTheDocument();
      expect(
        screen.getAllByText("Freshness lag detected").length,
      ).toBeGreaterThan(0);
      expect(screen.getAllByText(/heuristic/i).length).toBeGreaterThan(0);
    });
  });

  it("submits quick analytics queries through the canonical mutation hook", async () => {
    const mutate = vi.fn();
    analyticsMocks.useAnalyticsQuery.mockReturnValue({
      mutate,
      data: undefined,
      isPending: false,
      error: null,
      reset: vi.fn(),
    });

    render(<InsightsPage />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole("tab", { name: /analytics/i }));

    const editor = await screen.findByPlaceholderText(
      /select count\(\*\) as total/i,
    );
    fireEvent.change(editor, {
      target: { value: "  SELECT id FROM orders  " },
    });
    fireEvent.click(screen.getByRole("button", { name: /run query/i }));

    await waitFor(() => {
      expect(mutate).toHaveBeenCalledWith({ sql: "SELECT id FROM orders" });
    });
  });
});
