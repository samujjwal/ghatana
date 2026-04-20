import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { DashboardPage } from "../DashboardPage";

const navigateMock = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>(
    "react-router-dom",
  );
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

vi.mock("../../hooks/useDashboard", () => ({
  useDashboard: vi.fn(),
}));

vi.mock("../../hooks/useRecommendations", () => ({
  useRecommendations: vi.fn(),
}));

import { useDashboard } from "../../hooks/useDashboard";
import { useRecommendations } from "../../hooks/useRecommendations";

const mockUseDashboard = vi.mocked(useDashboard);
const mockUseRecommendations = vi.mocked(useRecommendations);

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("DashboardPage canonical navigation", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mockUseRecommendations.mockReturnValue({
      data: { modules: [], reasoning: undefined },
      isLoading: false,
      error: null,
    } as never);
  });

  it("uses moduleSlug for resume learning navigation", async () => {
    mockUseDashboard.mockReturnValue({
      data: {
        user: {
          id: "learner-1",
          email: "learner@example.com",
          displayName: "Jordan Learner",
        },
        currentEnrollments: [
          {
            id: "enrollment-1",
            moduleId: "module-1",
            moduleSlug: "kinematics-basics",
            moduleTitle: "Kinematics Basics",
            status: "active",
            progress: 65,
            progressPercent: 65,
            timeSpentSeconds: 1800,
          },
        ],
        recommendedModules: [],
        stats: {
          totalEnrollments: 1,
          completedModules: 0,
          averageProgress: 65,
        },
      },
      isLoading: false,
      error: null,
    } as never);

    renderWithProviders(<DashboardPage />);

    await userEvent.click(
      screen.getByRole("button", { name: /resume learning/i }),
    );

    expect(navigateMock).toHaveBeenCalledWith("/modules/kinematics-basics");
  });

  it("falls back to moduleId when moduleSlug is unavailable", async () => {
    mockUseDashboard.mockReturnValue({
      data: {
        user: {
          id: "learner-1",
          email: "learner@example.com",
          displayName: "Jordan Learner",
        },
        currentEnrollments: [
          {
            id: "enrollment-1",
            moduleId: "module-1",
            moduleTitle: "Kinematics Basics",
            status: "active",
            progress: 65,
            progressPercent: 65,
            timeSpentSeconds: 1800,
          },
        ],
        recommendedModules: [],
        stats: {
          totalEnrollments: 1,
          completedModules: 0,
          averageProgress: 65,
        },
      },
      isLoading: false,
      error: null,
    } as never);

    renderWithProviders(<DashboardPage />);

    await userEvent.click(
      screen.getByRole("button", { name: /resume learning/i }),
    );

    expect(navigateMock).toHaveBeenCalledWith("/modules/module-1");
  });
});