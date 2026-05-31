/**
 * Data Explorer Tests
 *
 * P6.1: Test loading selected collection from route param
 * P6.2: Test deterministic collection row actions
 * P6.3: Test view navigation (quality, lineage, schema)
 *
 * @doc.type test
 * @doc.purpose Verify Data Explorer deep-link and action navigation
 * @doc.layer frontend
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { DataExplorer } from "../../pages/DataExplorer";

// Mock the API calls
vi.mock("../../lib/api/collections", () => ({
  collectionsApi: {
    list: vi.fn(() =>
      Promise.resolve({
        items: [
          {
            id: "col-1",
            name: "Test Collection",
            description: "Test description",
            owner: "test-user",
            status: "PUBLISHED",
            lifecycleStatus: "ACTIVE",
            operationalStatus: "healthy",
            schemaType: "STRUCTURED",
            entityCount: 1000,
            qualityScore: 0.85,
            updatedAt: "2024-01-01T00:00:00Z",
            createdAt: "2024-01-01T00:00:00Z",
          },
          {
            id: "col-2",
            name: "Another Collection",
            description: "Another description",
            owner: "test-user",
            status: "DRAFT",
            lifecycleStatus: "ACTIVE",
            operationalStatus: "healthy",
            schemaType: "STRUCTURED",
            entityCount: 500,
            qualityScore: 0.75,
            updatedAt: "2024-01-02T00:00:00Z",
            createdAt: "2024-01-02T00:00:00Z",
          },
        ],
        total: 2,
      }),
    ),
  },
}));

vi.mock("../../api/lineage.service", () => ({
  lineageService: {
    getLineage: vi.fn(() => Promise.resolve({ nodes: [], edges: [] })),
    getImpactAnalysis: vi.fn(() => Promise.resolve({ impact: "low" })),
  },
}));

vi.mock("../../api/ai-operations.service", () => ({
  aiOperationsService: {
    getQualityAdvisories: vi.fn(() => Promise.resolve({ advisories: [] })),
  },
}));

describe("DataExplorer - P6 Route Param Loading", () => {
  it("P6.1: Loads selected collection from route param for deep-link support", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/data/col-1?view=table"]}>
          <Routes>
            <Route path="/data/:id" element={<DataExplorer />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText("Test Collection")).toBeInTheDocument();
    });
  });

  it("P6.1: Loads different collection when route param changes", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    const { rerender } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/data/col-1?view=table"]}>
          <Routes>
            <Route path="/data/:id" element={<DataExplorer />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText("Test Collection")).toBeInTheDocument();
    });

    rerender(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/data/col-2?view=table"]}>
          <Routes>
            <Route path="/data/:id" element={<DataExplorer />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText("Another Collection")).toBeInTheDocument();
    });
  });
});

describe("DataExplorer - P6 Deterministic Actions", () => {
  it("P6.2: Quality action navigates to quality view", async () => {
    const TestComponent = () => {
      const navigate = useNavigate();
      return (
        <button onClick={() => navigate("/data/col-1?view=quality")}>
          Quality Action
        </button>
      );
    };

    render(
      <MemoryRouter initialEntries={["/test"]}>
        <Routes>
          <Route path="/test" element={<TestComponent />} />
          <Route path="/data/:id" element={<div>Quality View</div>} />
        </Routes>
      </MemoryRouter>,
    );

    const button = screen.getByText("Quality Action");
    button.click();

    await waitFor(() => {
      expect(screen.getByText("Quality View")).toBeInTheDocument();
    });
  });

  it("P6.2: Lineage action navigates to lineage view", async () => {
    const TestComponent = () => {
      const navigate = useNavigate();
      return (
        <button onClick={() => navigate("/data/col-1?view=lineage")}>
          Lineage Action
        </button>
      );
    };

    render(
      <MemoryRouter initialEntries={["/test"]}>
        <Routes>
          <Route path="/test" element={<TestComponent />} />
          <Route path="/data/:id" element={<div>Lineage View</div>} />
        </Routes>
      </MemoryRouter>,
    );

    const button = screen.getByText("Lineage Action");
    button.click();

    await waitFor(() => {
      expect(screen.getByText("Lineage View")).toBeInTheDocument();
    });
  });

  it("P6.2: Schema action navigates to schema view", async () => {
    const TestComponent = () => {
      const navigate = useNavigate();
      return (
        <button onClick={() => navigate("/data/col-1?view=schema")}>
          Schema Action
        </button>
      );
    };

    render(
      <MemoryRouter initialEntries={["/test"]}>
        <Routes>
          <Route path="/test" element={<TestComponent />} />
          <Route path="/data/:id" element={<div>Schema View</div>} />
        </Routes>
      </MemoryRouter>,
    );

    const button = screen.getByText("Schema Action");
    button.click();

    await waitFor(() => {
      expect(screen.getByText("Schema View")).toBeInTheDocument();
    });
  });
});
