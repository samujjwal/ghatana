/**
 * Comprehensive test suite for DashboardPage component (Simplified Layout)
 *
 * Tests the new simplified dashboard design:
 * - Continue Learning card (primary CTA)
 * - Start Something New section (AI recommendations)
 * - Quick Actions with progressive disclosure
 * - Empty states
 *
 * @doc.type tests
 * @doc.purpose Unit tests for the simplified student dashboard page
 * @doc.layer product
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "@testing-library/jest-dom";
import { DashboardPage } from "../DashboardPage";

// Mock the hooks
vi.mock("../../hooks/useDashboard", () => ({
  useDashboard: vi.fn(),
}));

vi.mock("../../hooks/useRecommendations", () => ({
  useRecommendations: vi.fn(),
}));

// Import the mocked modules
import { useDashboard } from "../../hooks/useDashboard";
import { useRecommendations } from "../../hooks/useRecommendations";

const mockUseDashboard = vi.mocked(useDashboard);
const mockUseRecommendations = vi.mocked(useRecommendations);

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

describe("DashboardPage - Simplified Layout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default mock for useRecommendations
    mockUseRecommendations.mockReturnValue({
      data: {
        modules: [
          {
            id: "mod-3",
            title: "Advanced Physics",
            slug: "advanced-physics",
            description: "Learn advanced physics concepts",
            tags: ["physics", "advanced"],
            estimatedTimeMinutes: 180,
            difficultyLevel: "advanced",
            domain: "PHYSICS",
            isAiRecommended: true,
            matchScore: 0.85,
          },
        ],
        reasoning: {
          basedOn: "Recent physics interest",
          userLevel: "intermediate",
          suggestedDomains: ["PHYSICS"],
        },
      },
      isLoading: false,
      error: null,
    } as any);
  });

  describe("Loading State", () => {
    it("shows loading skeleton while data is being fetched", () => {
      mockUseDashboard.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      } as any);

      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/loading dashboard/i)).toBeInTheDocument();
    });
  });

  describe("Error State", () => {
    it("shows error message with retry button when data fetch fails", () => {
      mockUseDashboard.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error("Network error"),
      } as any);

      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/error loading dashboard/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /retry/i })).toBeInTheDocument();
    });
  });

  describe("Continue Learning Section (Primary CTA)", () => {
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
      ],
      recommendedModules: [],
      stats: {
        totalEnrollments: 1,
        completedModules: 0,
        averageProgress: 50,
      },
    };

    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: mockDashboardData,
        isLoading: false,
        error: null,
      } as any);
    });

    it("displays Continue Learning card when user has active enrollment", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/continue learning/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /resume learning/i })).toBeInTheDocument();
    });

    it("displays progress bar with correct percentage", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText("50%")).toBeInTheDocument();
    });

    it("has link to see all enrollments", () => {
      renderWithProviders(<DashboardPage />);

      const seeAllLink = screen.getByRole("link", { name: /see all/i });
      expect(seeAllLink).toHaveAttribute("href", "/enrollments");
    });
  });

  describe("Start Something New Section", () => {
    const mockDashboardData = {
      user: {
        id: "user-1",
        email: "student@example.com",
        displayName: "Test Student",
        avatarUrl: null,
      },
      currentEnrollments: [],
      recommendedModules: [],
      stats: {
        totalEnrollments: 0,
        completedModules: 0,
        averageProgress: 0,
      },
    };

    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: mockDashboardData,
        isLoading: false,
        error: null,
      } as any);
    });

    it("displays 'Start Something New' section with AI recommendations", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/start something new/i)).toBeInTheDocument();
      expect(screen.getByText("Advanced Physics")).toBeInTheDocument();
    });

    it("shows AI recommended badge on suggested modules", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/ai recommended/i)).toBeInTheDocument();
    });

    it("has 'Browse All' button to explore more modules", () => {
      renderWithProviders(<DashboardPage />);

      const browseAllLink = screen.getByRole("link", { name: /browse all/i });
      expect(browseAllLink).toHaveAttribute("href", "/search");
    });
  });

  describe("Quick Actions", () => {
    const mockDashboardData = {
      user: { id: "user-1", email: "test@test.com", displayName: "Test" },
      currentEnrollments: [],
      recommendedModules: [],
      stats: { totalEnrollments: 0, completedModules: 0, averageProgress: 0 },
    };

    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: mockDashboardData,
        isLoading: false,
        error: null,
      } as any);
    });

    it("displays primary quick action buttons", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByRole("button", { name: /browse all/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /my learning/i })).toBeInTheDocument();
    });

    it("has 'More options' button for progressive disclosure", async () => {
      renderWithProviders(<DashboardPage />);

      const moreOptionsButton = screen.getByRole("button", { name: /more options/i });
      expect(moreOptionsButton).toBeInTheDocument();

      // Click to expand
      await userEvent.click(moreOptionsButton);

      // Should show additional options
      expect(screen.getByText(/achievements/i)).toBeInTheDocument();
      expect(screen.getByText(/study groups/i)).toBeInTheDocument();
    });
  });

  describe("Empty State", () => {
    beforeEach(() => {
      mockUseDashboard.mockReturnValue({
        data: {
          user: { id: "1", email: "new@student.com", displayName: "New Student" },
          currentEnrollments: [],
          recommendedModules: [],
          stats: { totalEnrollments: 0, completedModules: 0, averageProgress: 0 },
        },
        isLoading: false,
        error: null,
      } as any);
    });

    it("shows empty state with welcome message for new users", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/welcome to tutorputor/i)).toBeInTheDocument();
      expect(screen.getByText(/start your learning journey/i)).toBeInTheDocument();
    });

    it("shows 'Get Started' CTA button for new users", () => {
      renderWithProviders(<DashboardPage />);

      const getStartedButton = screen.getByRole("button", { name: /get started/i });
      expect(getStartedButton).toBeInTheDocument();
    });

    it("shows AI-suggested starter modules in empty state", () => {
      renderWithProviders(<DashboardPage />);

      expect(screen.getByText(/here are some suggestions/i)).toBeInTheDocument();
      expect(screen.getByText("Advanced Physics")).toBeInTheDocument();
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

    it("has link to browse all modules", () => {
      renderWithProviders(<DashboardPage />);

      const browseLink = screen.getByRole("link", { name: /browse all/i });
      expect(browseLink).toHaveAttribute("href", "/search");
    });

    it("has link to learning pathways", () => {
      renderWithProviders(<DashboardPage />);

      const pathwaysLink = screen.getByRole("link", { name: /learning pathways/i });
      expect(pathwaysLink).toHaveAttribute("href", "/pathways");
    });

    it("has link to AI Tutor", () => {
      renderWithProviders(<DashboardPage />);

      const aiTutorLink = screen.getByRole("link", { name: /ai tutor/i });
      expect(aiTutorLink).toHaveAttribute("href", "/ai-tutor");
    });

    it("has link to Assessments in more options", async () => {
      renderWithProviders(<DashboardPage />);

      const moreOptionsButton = screen.getByRole("button", { name: /more options/i });
      await userEvent.click(moreOptionsButton);

      const assessmentsLink = screen.getByRole("link", { name: /assessments/i });
      expect(assessmentsLink).toHaveAttribute("href", "/assessments");
    });

    it("has link to Analytics", () => {
      renderWithProviders(<DashboardPage />);

      const analyticsLink = screen.getByRole("link", { name: /analytics/i });
      expect(analyticsLink).toHaveAttribute("href", "/analytics");
    });
  });

  describe("Continue Learning Progress Display", () => {
    it("shows progress percentage for most recent active enrollment", () => {
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

      // Continue Learning card should show 75% progress
      expect(screen.getByText("75%")).toBeInTheDocument();
      expect(screen.getByText(/continue learning/i)).toBeInTheDocument();
    });

    it("displays completed badge for finished enrollment", () => {
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

      // Should show Start Something New instead of Continue Learning
      expect(screen.getByText(/start something new/i)).toBeInTheDocument();
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
