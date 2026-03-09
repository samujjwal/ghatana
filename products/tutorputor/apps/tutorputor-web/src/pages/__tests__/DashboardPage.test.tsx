/**
 * Comprehensive test suite for DashboardPage component
 *
 * @doc.type tests
 * @doc.purpose Unit tests for the student dashboard page
 * @doc.layer product
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DashboardPage } from "../DashboardPage";

// Mock the useDashboard hook
vi.mock("../../hooks/useDashboard", () => ({
  useDashboard: vi.fn(),
}));

// Import the mocked module
import { useDashboard } from "../../hooks/useDashboard";

const mockUseDashboard = vi.mocked(useDashboard);

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = createTestQueryClient();
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{ui}</MemoryRouter>
      </QueryClientProvider>,
    ),
    queryClient,
  };
}

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Loading State", () => {
    it("shows loading indicator while data is being fetched", () => {
      mockUseDashboard.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      } as any);

      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/loading/i)).toBeInTheDocument();
    });
  });

  describe("Error State", () => {
    it("shows error message when data fetch fails", () => {
      mockUseDashboard.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error("Network error"),
      } as any);

      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/error/i)).toBeInTheDocument();
    });
  });

  describe("Empty State", () => {
    it("returns null when data is undefined and not loading", () => {
      mockUseDashboard.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: null,
      } as any);

      const { container } = renderWithProviders(<DashboardPage />);

      expect(container.firstChild).toBeNull();
    });
  });

  describe("Content Rendering", () => {
    const mockDashboardData = {
      user: {
        id: "user-1",
        email: "student@example.com",
        displayName: "Test Student",
        avatarUrl: null,
      },
      currentEnrollments: [
        {
          id: "enrollment-1",
          moduleId: "mod-1",
          status: "active" as const,
          progress: 50,
          progressPercent: 50,
          timeSpentSeconds: 3600,
        },
        {
          id: "enrollment-2",
          moduleId: "mod-2",
          status: "completed" as const,
          progress: 100,
          progressPercent: 100,
          timeSpentSeconds: 7200,
        },
      ],
      recommendedModules: [
        {
          id: "mod-3",
          title: "Advanced Physics",
          slug: "advanced-physics",
          description: "Learn advanced physics concepts",
          tags: ["physics", "advanced"],
          estimatedMinutes: 180,
          difficulty: "advanced",
          domain: "PHYSICS",
        },
      ],
      stats: {
        totalEnrollments: 5,
        completedModules: 2,
        averageProgress: 60,
      },
    };

    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: mockDashboardData,
        isLoading: false,
        error: null,
      } as any);
    });

    it("displays user greeting with display name", () => {
      renderWithProviders(<DashboardPage />);

      expect(
        screen.getByText(/welcome back, test student/i),
      ).toBeInTheDocument();
    });

    it("falls back to email when display name is missing", () => {
      mockUseDashboard.mockReturnValue({
        data: {
          ...mockDashboardData,
          user: { ...mockDashboardData.user, displayName: null },
        },
        isLoading: false,
        error: null,
      } as any);

      renderWithProviders(<DashboardPage />);

      expect(
        screen.getByText(/welcome back, student@example.com/i),
      ).toBeInTheDocument();
    });

    it("displays stats cards with correct values", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText("Enrollments")).toBeInTheDocument();
      expect(screen.getByText("5")).toBeInTheDocument(); // totalEnrollments
      expect(screen.getByText("Completed")).toBeInTheDocument();
      expect(screen.getByText("2")).toBeInTheDocument(); // completedModules
      expect(screen.getByText(/avg\. progress/i)).toBeInTheDocument();
      expect(screen.getByText("60%")).toBeInTheDocument(); // averageProgress
    });

    it("calculates stats from enrollments when stats object is missing", () => {
      mockUseDashboard.mockReturnValue({
        data: {
          ...mockDashboardData,
          stats: undefined,
        },
        isLoading: false,
        error: null,
      } as any);

      renderWithProviders(<DashboardPage />);

      // Should calculate from currentEnrollments
      expect(screen.getByText("2")).toBeInTheDocument(); // 2 enrollments
      expect(screen.getByText("1")).toBeInTheDocument(); // 1 completed
      expect(screen.getByText("75%")).toBeInTheDocument(); // (50+100)/2 = 75%
    });

    it("displays feature tiles", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText("Learning Pathways")).toBeInTheDocument();
      expect(screen.getByText("Browse Modules")).toBeInTheDocument();
      expect(screen.getByText("AI Tutor")).toBeInTheDocument();
      expect(screen.getByText("Assessments")).toBeInTheDocument();
      expect(screen.getByText("Analytics")).toBeInTheDocument();
      expect(screen.getByText("Marketplace")).toBeInTheDocument();
    });

    it("displays recommended modules", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText("Advanced Physics")).toBeInTheDocument();
    });

    it("displays current enrollments", () => {
      renderWithProviders(<DashboardPage />);

      // Should show enrollments section with progress
      expect(screen.getByText(/your progress/i)).toBeInTheDocument();
    });
  });

  describe("Navigation", () => {
    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: {
          user: { id: "1", email: "test@test.com", displayName: "Test" },
          currentEnrollments: [],
          recommendedModules: [],
          stats: {
            totalEnrollments: 0,
            completedModules: 0,
            averageProgress: 0,
          },
        },
        isLoading: false,
        error: null,
      } as any);
    });

    it("has link to Learning Pathways", () => {
      renderWithProviders(<DashboardPage />);

      const pathwaysLink = screen.getByRole("link", {
        name: /learning pathways/i,
      });
      expect(pathwaysLink).toHaveAttribute("href", "/pathways");
    });

    it("has link to Browse Modules", () => {
      renderWithProviders(<DashboardPage />);

      const searchLink = screen.getByRole("link", { name: /browse modules/i });
      expect(searchLink).toHaveAttribute("href", "/search");
    });

    it("has link to AI Tutor", () => {
      renderWithProviders(<DashboardPage />);

      const aiTutorLink = screen.getByRole("link", { name: /ai tutor/i });
      expect(aiTutorLink).toHaveAttribute("href", "/ai-tutor");
    });

    it("has link to Assessments", () => {
      renderWithProviders(<DashboardPage />);

      const assessmentsLink = screen.getByRole("link", {
        name: /assessments/i,
      });
      expect(assessmentsLink).toHaveAttribute("href", "/assessments");
    });

    it("has links to Analytics sections", () => {
      renderWithProviders(<DashboardPage />);

      // Multiple analytics links exist - use getAllByRole
      const analyticsLinks = screen.getAllByRole("link", {
        name: /analytics/i,
      });
      expect(analyticsLinks.length).toBeGreaterThanOrEqual(1);
      // At least one should link to /analytics
      const mainAnalyticsLink = analyticsLinks.find(
        (link) => link.getAttribute("href") === "/analytics",
      );
      expect(mainAnalyticsLink).toBeDefined();
    });

    it("has link to Marketplace", () => {
      renderWithProviders(<DashboardPage />);

      const marketplaceLink = screen.getByRole("link", {
        name: /marketplace/i,
      });
      expect(marketplaceLink).toHaveAttribute("href", "/marketplace");
    });
  });

  describe("Empty Enrollments", () => {
    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: {
          user: {
            id: "1",
            email: "new@student.com",
            displayName: "New Student",
          },
          currentEnrollments: [],
          recommendedModules: [
            {
              id: "mod-1",
              title: "Getting Started",
              slug: "getting-started",
              tags: ["beginner"],
            },
          ],
          stats: {
            totalEnrollments: 0,
            completedModules: 0,
            averageProgress: 0,
          },
        },
        isLoading: false,
        error: null,
      } as any);
    });

    it("shows 0 for all stats when no enrollments", () => {
      renderWithProviders(<DashboardPage />);

      // Should show zeros
      const statCards = screen.getAllByText("0");
      expect(statCards.length).toBeGreaterThanOrEqual(2);
    });

    it("still shows recommended modules", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText("Getting Started")).toBeInTheDocument();
    });
  });

  describe("Module Progress Display", () => {
    it("shows progress percentage for active enrollments", () => {
      mockUseDashboard.mockReturnValue({
        data: {
          user: { id: "1", email: "test@test.com", displayName: "Test" },
          currentEnrollments: [
            {
              id: "e1",
              moduleId: "m1",
              status: "active",
              progress: 75,
              progressPercent: 75,
              timeSpentSeconds: 1800,
            },
          ],
          recommendedModules: [],
          stats: {
            totalEnrollments: 1,
            completedModules: 0,
            averageProgress: 75,
          },
        },
        isLoading: false,
        error: null,
      } as any);

      renderWithProviders(<DashboardPage />);

      // Should display 75% somewhere in the progress section
      expect(screen.getByText("75%")).toBeInTheDocument();
    });

    it("displays correct status badge for completed enrollment", () => {
      mockUseDashboard.mockReturnValue({
        data: {
          user: { id: "1", email: "test@test.com", displayName: "Test" },
          currentEnrollments: [
            {
              id: "e1",
              moduleId: "m1",
              status: "completed",
              progress: 100,
              progressPercent: 100,
              timeSpentSeconds: 3600,
            },
          ],
          recommendedModules: [],
          stats: {
            totalEnrollments: 1,
            completedModules: 1,
            averageProgress: 100,
          },
        },
        isLoading: false,
        error: null,
      } as any);

      renderWithProviders(<DashboardPage />);

      // Should show 100% or completed status
      expect(screen.getByText("100%")).toBeInTheDocument();
    });
  });

  describe("Accessibility", () => {
    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: {
          user: { id: "1", email: "test@test.com", displayName: "Test" },
          currentEnrollments: [],
          recommendedModules: [],
          stats: {
            totalEnrollments: 0,
            completedModules: 0,
            averageProgress: 0,
          },
        },
        isLoading: false,
        error: null,
      } as any);
    });

    it("has descriptive headings", () => {
      renderWithProviders(<DashboardPage />);

      // Should have a main heading
      const headings = screen.getAllByRole("heading");
      expect(headings.length).toBeGreaterThan(0);
    });

    it("all links have accessible names", () => {
      renderWithProviders(<DashboardPage />);

      const links = screen.getAllByRole("link");
      links.forEach((link) => {
        expect(link).toHaveAccessibleName();
      });
    });
  });
});

describe("DashboardPage - Data Variations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("handles missing optional fields gracefully", () => {
    mockUseDashboard.mockReturnValue({
      data: {
        user: { id: "1" }, // Minimal user
        currentEnrollments: [],
        recommendedModules: [],
      },
      isLoading: false,
      error: null,
    } as any);

    renderWithProviders(<DashboardPage />);

    // Should use fallback "Student"
    expect(screen.getByText(/welcome back, student/i)).toBeInTheDocument();
  });

  it("handles very long display names", () => {
    const longName = "A".repeat(100);
    mockUseDashboard.mockReturnValue({
      data: {
        user: { id: "1", email: "test@test.com", displayName: longName },
        currentEnrollments: [],
        recommendedModules: [],
        stats: { totalEnrollments: 0, completedModules: 0, averageProgress: 0 },
      },
      isLoading: false,
      error: null,
    } as any);

    renderWithProviders(<DashboardPage />);

    expect(screen.getByText(new RegExp(longName))).toBeInTheDocument();
  });

  it("handles large numbers in stats", () => {
    mockUseDashboard.mockReturnValue({
      data: {
        user: { id: "1", email: "test@test.com", displayName: "Test" },
        currentEnrollments: [],
        recommendedModules: [],
        stats: {
          totalEnrollments: 99999,
          completedModules: 50000,
          averageProgress: 99.99,
        },
      },
      isLoading: false,
      error: null,
    } as any);

    renderWithProviders(<DashboardPage />);

    expect(screen.getByText("99999")).toBeInTheDocument();
    expect(screen.getByText("50000")).toBeInTheDocument();
  });

  it("handles many enrollments", () => {
    const manyEnrollments = Array.from({ length: 50 }, (_, i) => ({
      id: `e${i}`,
      moduleId: `m${i}`,
      status: i % 2 === 0 ? "active" : "completed",
      progress: i * 2,
      progressPercent: i * 2,
      timeSpentSeconds: i * 100,
    }));

    mockUseDashboard.mockReturnValue({
      data: {
        user: { id: "1", email: "test@test.com", displayName: "Test" },
        currentEnrollments: manyEnrollments,
        recommendedModules: [],
        stats: {
          totalEnrollments: 50,
          completedModules: 25,
          averageProgress: 49,
        },
      },
      isLoading: false,
      error: null,
    } as any);

    renderWithProviders(<DashboardPage />);

    // Should render without crashing
    expect(screen.getByText("50")).toBeInTheDocument();
  });

  it("handles many recommended modules", () => {
    const manyModules = Array.from({ length: 20 }, (_, i) => ({
      id: `m${i}`,
      title: `Module ${i}`,
      slug: `module-${i}`,
      tags: ["test"],
    }));

    mockUseDashboard.mockReturnValue({
      data: {
        user: { id: "1", email: "test@test.com", displayName: "Test" },
        currentEnrollments: [],
        recommendedModules: manyModules,
        stats: { totalEnrollments: 0, completedModules: 0, averageProgress: 0 },
      },
      isLoading: false,
      error: null,
    } as any);

    renderWithProviders(<DashboardPage />);

    // Should show at least some modules
    expect(screen.getByText("Module 0")).toBeInTheDocument();
  });
});
