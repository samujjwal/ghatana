/**
 * Test suite for useDashboard hook
 *
 * @doc.type tests
 * @doc.purpose Unit tests for dashboard data fetching hook
 * @doc.layer product
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useDashboard } from "../useDashboard";

// Mock the apiClient
vi.mock("../../api/tutorputorClient", () => ({
  apiClient: {
    getDashboard: vi.fn(),
  },
}));

import { apiClient } from "../../api/tutorputorClient";

const mockGetDashboard = vi.mocked(apiClient.getDashboard);

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

describe("useDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Initial State", () => {
    it("starts with loading state", () => {
      mockGetDashboard.mockImplementation(
        () => new Promise(() => {}), // Never resolves
      );

      const { result } = renderHook(() => useDashboard(), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();
    });
  });

  describe("Successful Fetch", () => {
    const mockDashboardData = {
      user: {
        id: "user-1",
        email: "test@example.com",
        displayName: "Test User",
      },
      currentEnrollments: [
        {
          id: "e1",
          moduleId: "m1",
          status: "active" as const,
          progress: 50,
          progressPercent: 50,
          timeSpentSeconds: 1800,
        },
      ],
      recommendedModules: [
        {
          id: "m2",
          title: "Recommended Module",
          slug: "recommended-module",
          tags: ["test"],
        },
      ],
      stats: {
        totalEnrollments: 5,
        completedModules: 2,
        averageProgress: 60,
      },
    };

    it("returns dashboard data on success", async () => {
      mockGetDashboard.mockResolvedValueOnce(mockDashboardData);

      const { result } = renderHook(() => useDashboard(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.data).toEqual(mockDashboardData);
      expect(result.current.error).toBeNull();
    });

    it("calls getDashboard from apiClient", async () => {
      mockGetDashboard.mockResolvedValueOnce(mockDashboardData);

      renderHook(() => useDashboard(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(mockGetDashboard).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe("Error Handling", () => {
    it("returns error on fetch failure", async () => {
      const error = new Error("Network error");
      mockGetDashboard.mockRejectedValueOnce(error);

      const { result } = renderHook(() => useDashboard(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.error).toBeTruthy();
      expect(result.current.data).toBeUndefined();
    });
  });

  describe("Caching", () => {
    it("uses query key 'dashboard'", async () => {
      mockGetDashboard.mockResolvedValue({
        user: { id: "1", email: "test@test.com", displayName: "Test" },
        currentEnrollments: [],
        recommendedModules: [],
        stats: { totalEnrollments: 0, completedModules: 0, averageProgress: 0 },
      });

      const { result, rerender } = renderHook(() => useDashboard(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Rerender should not trigger new fetch due to caching
      const callCountAfterFirstRender = mockGetDashboard.mock.calls.length;
      rerender();

      // Should still be same call count (cached)
      expect(mockGetDashboard.mock.calls.length).toBe(
        callCountAfterFirstRender,
      );
    });
  });
});
