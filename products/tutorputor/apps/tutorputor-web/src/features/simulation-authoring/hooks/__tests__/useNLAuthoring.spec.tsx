/**
 * useNLAuthoring Hook Tests
 *
 * @doc.type test
 * @doc.purpose Test NL authoring hook functionality
 * @doc.layer product
 * @doc.pattern Test
 */

import React from "react";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useNLAuthoring } from "../useNLAuthoring";
import type { SimulationManifest } from "../useSimulationTimeline";

// Local type for test
interface GenerateManifestResult {
  manifest: SimulationManifest;
  confidence?: number;
  suggestions?: string[];
  warnings?: string[];
}

// =============================================================================
// Test Fixtures
// =============================================================================

const mockManifest: SimulationManifest = {
  id: "sim_test_123",
  title: "Bubble Sort Visualization",
  description: "Step-by-step bubble sort algorithm",
  domain: "CS_DISCRETE",
  version: "1.0.0",
  steps: [
    {
      id: "step_1",
      duration: 1000,
      narration: "Start with unsorted array",
      actions: [
        {
          action: "CREATE_ENTITY",
          type: "CREATE_ENTITY",
          entityId: "arr_0",
          params: { value: 5 },
        },
      ],
    },
    {
      id: "step_2",
      duration: 1000,
      narration: "Compare first two elements",
      actions: [
        {
          action: "COMPARE",
          type: "COMPARE",
          entityId: "arr_0",
          params: { compareWith: "arr_1" },
        },
      ],
    },
  ],
  initialEntities: [
    {
      id: "arr_0",
      type: "array-element",
      properties: { value: 5, index: 0 },
    },
  ],
  metadata: {
    author: "AI",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
};

const mockGenerateResult: GenerateManifestResult = {
  manifest: mockManifest,
  confidence: 0.85,
  suggestions: [],
  warnings: [],
};

// =============================================================================
// Test Setup
// =============================================================================

// Mock fetch
const mockFetch = vi.fn();
(globalThis as Record<string, unknown>).fetch = mockFetch;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

// =============================================================================
// Tests
// =============================================================================

describe("useNLAuthoring", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("initialization", () => {
    it("should initialize with default state", () => {
      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      expect(result.current.domain).toBe("CS_DISCRETE");
      expect(result.current.manifest).toBeNull();
      expect(result.current.history).toHaveLength(0);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBeNull();
      expect(result.current.canRefine).toBe(false);
    });

    it("should initialize with provided options", () => {
      const { result } = renderHook(
        () =>
          useNLAuthoring({
            initialDomain: "PHYSICS",
            initialManifest: mockManifest,
          }),
        { wrapper: createWrapper() },
      );

      // Domain should match manifest domain
      expect(result.current.domain).toBe("CS_DISCRETE");
      expect(result.current.manifest).toEqual(mockManifest);
      expect(result.current.canRefine).toBe(true);
    });
  });

  describe("setDomain", () => {
    it("should update domain", () => {
      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      act(() => {
        result.current.setDomain("CHEMISTRY");
      });

      expect(result.current.domain).toBe("CHEMISTRY");
    });
  });

  describe("setConstraints", () => {
    it("should update constraints partially", () => {
      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      act(() => {
        result.current.setConstraints({ maxSteps: 20 });
      });

      expect(result.current.constraints.maxSteps).toBe(20);
      expect(result.current.constraints.maxEntities).toBe(8); // default unchanged
    });
  });

  describe("generate", () => {
    it("should generate manifest from prompt", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockGenerateResult,
      });

      const onManifestChange = vi.fn();
      const { result } = renderHook(
        () => useNLAuthoring({ onManifestChange }),
        { wrapper: createWrapper() },
      );

      let generatedManifest: SimulationManifest | null = null;
      await act(async () => {
        generatedManifest = await result.current.generate(
          "Show bubble sort on [5,2,8,1]",
        );
      });

      expect(generatedManifest).toEqual(mockManifest);
      expect(result.current.manifest).toEqual(mockManifest);
      expect(result.current.confidence).toBe(0.85);
      expect(result.current.history).toHaveLength(2); // user + assistant
      expect(result.current.history[0].role).toBe("user");
      expect(result.current.history[1].role).toBe("assistant");
      expect(onManifestChange).toHaveBeenCalledWith(mockManifest);
    });

    it("should handle generation error", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: async () => ({ message: "Server error" }),
      });

      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      await expect(
        act(async () => {
          await result.current.generate("Invalid prompt");
        }),
      ).rejects.toThrow("Server error");

      // Wait for the error state to be set
      await waitFor(() => {
        expect(result.current.error).toBeTruthy();
      });
      expect(result.current.manifest).toBeNull();
    });

    it("should add user message to history before API call", async () => {
      mockFetch.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () =>
                resolve({
                  ok: true,
                  json: async () => mockGenerateResult,
                }),
              100,
            ),
          ),
      );

      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      // Start generation but don't await
      act(() => {
        result.current.generate("Test prompt");
      });

      // Check history immediately - user message should be there
      await waitFor(() => {
        expect(result.current.history.length).toBeGreaterThanOrEqual(1);
        expect(result.current.history[0].content).toBe("Test prompt");
        expect(result.current.history[0].role).toBe("user");
      });
    });
  });

  describe("refine", () => {
    it("should refine existing manifest", async () => {
      const refinedManifest: SimulationManifest = {
        ...mockManifest,
        title: "Optimized Bubble Sort",
        steps: [
          ...mockManifest.steps,
          {
            id: "step_3" as any,
            index: 2,
            narration: "Continue sorting",
            actions: [],
          },
        ],
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          manifest: refinedManifest,
          confidence: 0.9,
          suggestions: [],
          warnings: [],
        }),
      });

      const { result } = renderHook(
        () => useNLAuthoring({ initialManifest: mockManifest }),
        { wrapper: createWrapper() },
      );

      await act(async () => {
        await result.current.refine("Add one more step");
      });

      expect(result.current.manifest).toEqual(refinedManifest);
      expect(result.current.manifest?.steps).toHaveLength(3);
    });

    it("should throw error when no manifest to refine", async () => {
      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      await expect(
        act(async () => {
          await result.current.refine("Improve it");
        }),
      ).rejects.toThrow("No manifest to refine");
    });
  });

  describe("history management", () => {
    it("should clear history", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockGenerateResult,
      });

      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.generate("Test");
      });

      expect(result.current.history.length).toBeGreaterThan(0);

      act(() => {
        result.current.clearHistory();
      });

      expect(result.current.history).toHaveLength(0);
      expect(result.current.error).toBeNull();
    });

    it("should track snapshots in history", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockGenerateResult,
      });

      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.generate("Create simulation");
      });

      expect(result.current.historySnapshots).toHaveLength(1);
      expect(result.current.historySnapshots[0].title).toBe(
        "Bubble Sort Visualization",
      );
    });

    it("should revert to snapshot", async () => {
      // First generation
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockGenerateResult,
      });

      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.generate("Create simulation");
      });

      const snapshotId = result.current.historySnapshots[0].id;

      // Second generation with different manifest
      const modifiedManifest = { ...mockManifest, title: "Modified" };
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          manifest: modifiedManifest,
          confidence: 0.8,
          suggestions: [],
          warnings: [],
        }),
      });

      await act(async () => {
        await result.current.refine("Modify it");
      });

      expect(result.current.manifest?.title).toBe("Modified");

      // Revert to first snapshot
      act(() => {
        result.current.revertToSnapshot(snapshotId);
      });

      expect(result.current.manifest?.title).toBe("Bubble Sort Visualization");
    });
  });

  describe("reset", () => {
    it("should reset all state", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockGenerateResult,
      });

      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      await act(async () => {
        await result.current.generate("Test");
        result.current.setDomain("PHYSICS");
        result.current.setConstraints({ maxSteps: 50 });
      });

      act(() => {
        result.current.reset();
      });

      expect(result.current.domain).toBe("CS_DISCRETE");
      expect(result.current.manifest).toBeNull();
      expect(result.current.history).toHaveLength(0);
      expect(result.current.constraints.maxSteps).toBe(10);
    });
  });

  describe("loading state", () => {
    it("should track loading state during generation", async () => {
      mockFetch.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () =>
                resolve({
                  ok: true,
                  json: async () => mockGenerateResult,
                }),
              100,
            ),
          ),
      );

      const { result } = renderHook(() => useNLAuthoring(), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(false);

      const generatePromise = act(async () => {
        await result.current.generate("Test");
      });

      // Should be loading
      await waitFor(() => {
        expect(result.current.isLoading).toBe(true);
      });

      await generatePromise;

      // Should not be loading after completion
      expect(result.current.isLoading).toBe(false);
    });
  });
});
