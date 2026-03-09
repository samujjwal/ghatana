/**
 * useSimulationTimeline Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Test suite for simulation timeline hook
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useSimulationTimeline } from "../useSimulationTimeline";
import type { SimulationManifest, SimulationStep, SimStepId } from "../useSimulationTimeline";

// =============================================================================
// Test Fixtures
// =============================================================================

const createMockManifest = (steps: SimulationStep[] = []): SimulationManifest => ({
  id: "sim_test_001",
  version: "1.0.0",
  title: "Test Simulation",
  description: "A test simulation",
  domain: "CS_DISCRETE",
  initialEntities: [],
  steps,
});

const createMockStep = (orderIndex: number): SimulationStep => ({
  id: `step_${orderIndex}` as SimStepId,
  label: `Step ${orderIndex + 1}`,
  duration: 500,
  actions: [
    {
      action: "HIGHLIGHT",
      target: "node_1",
      duration: 500,
    },
  ],
});

describe("useSimulationTimeline", () => {
  let onManifestChange: (manifest: SimulationManifest) => void;

  beforeEach(() => {
    onManifestChange = vi.fn();
  });

  describe("initialization", () => {
    it("should initialize with manifest steps", () => {
      const steps = [createMockStep(0), createMockStep(1)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.steps).toHaveLength(2);
      expect(result.current.steps[0].id).toBe("step_0");
      expect(result.current.steps[1].id).toBe("step_1");
    });

    it("should initialize with empty steps when manifest is null", () => {
      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest: null,
          onManifestChange,
        })
      );

      expect(result.current.steps).toHaveLength(0);
    });

    it("should initialize with no selection", () => {
      const manifest = createMockManifest([createMockStep(0)]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.selectedStepId).toBeNull();
      expect(result.current.selectedStep).toBeUndefined();
    });

    it("should initialize playback state correctly", () => {
      const manifest = createMockManifest([createMockStep(0)]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.isPlaying).toBe(false);
      expect(result.current.currentPlaybackStep).toBe(0);
      expect(result.current.playbackSpeed).toBe(1);
    });
  });

  describe("step selection", () => {
    it("should select a step by ID", () => {
      const steps = [createMockStep(0), createMockStep(1)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.selectStep("step_1" as SimStepId);
      });

      expect(result.current.selectedStepId).toBe("step_1");
      expect(result.current.selectedStep?.id).toBe("step_1");
    });

    it("should update playback step when selecting", () => {
      const steps = [createMockStep(0), createMockStep(1), createMockStep(2)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.selectStep("step_2" as SimStepId);
      });

      expect(result.current.currentPlaybackStep).toBe(2);
    });

    it("should clear selection when passing null", () => {
      const steps = [createMockStep(0)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.selectStep("step_0" as SimStepId);
      });

      expect(result.current.selectedStepId).toBe("step_0");

      act(() => {
        result.current.selectStep(null);
      });

      expect(result.current.selectedStepId).toBeNull();
    });
  });

  describe("step management", () => {
    it("should add a new step", () => {
      const manifest = createMockManifest([]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      let newStepId: SimStepId;
      act(() => {
        newStepId = result.current.addStep();
      });

      expect(result.current.steps).toHaveLength(1);
      expect(result.current.steps[0].id).toBe(newStepId!);
      expect(result.current.selectedStepId).toBe(newStepId!);
    });

    it("should add step after specified step", () => {
      const steps = [createMockStep(0), createMockStep(1)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.addStep("step_0" as SimStepId);
      });

      expect(result.current.steps).toHaveLength(3);
      expect(result.current.steps[0].id).toBe("step_0");
      expect(result.current.steps[2].id).toBe("step_1");
    });

    it("should delete a step", () => {
      const steps = [createMockStep(0), createMockStep(1)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.deleteStep("step_0" as SimStepId);
      });

      expect(result.current.steps).toHaveLength(1);
      expect(result.current.steps[0].id).toBe("step_1");
      expect(result.current.steps[0].orderIndex).toBe(0); // reindexed
    });

    it("should clear selection when deleting selected step", () => {
      const steps = [createMockStep(0)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.selectStep("step_0" as SimStepId);
      });

      expect(result.current.selectedStepId).toBe("step_0");

      act(() => {
        result.current.deleteStep("step_0" as SimStepId);
      });

      expect(result.current.selectedStepId).toBeNull();
    });

    it("should duplicate a step", () => {
      const steps = [createMockStep(0)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      let duplicatedId: SimStepId;
      act(() => {
        duplicatedId = result.current.duplicateStep("step_0" as SimStepId);
      });

      expect(result.current.steps).toHaveLength(2);
      expect(result.current.steps[1].id).toBe(duplicatedId!);
      expect(result.current.steps[1].title).toContain("copy");
      expect(result.current.selectedStepId).toBe(duplicatedId!);
    });

    it("should reorder a step", () => {
      const steps = [createMockStep(0), createMockStep(1), createMockStep(2)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.reorderStep("step_2" as SimStepId, 0);
      });

      expect(result.current.steps[0].id).toBe("step_2");
      expect(result.current.steps[1].id).toBe("step_0");
      expect(result.current.steps[2].id).toBe("step_1");

      // Check orderIndex is updated
      expect(result.current.steps[0].orderIndex).toBe(0);
      expect(result.current.steps[1].orderIndex).toBe(1);
      expect(result.current.steps[2].orderIndex).toBe(2);
    });
  });

  describe("playback controls", () => {
    it("should start playback", () => {
      const steps = [createMockStep(0), createMockStep(1)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.play();
      });

      expect(result.current.isPlaying).toBe(true);
    });

    it("should pause playback", () => {
      const steps = [createMockStep(0), createMockStep(1)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.play();
      });

      act(() => {
        result.current.pause();
      });

      expect(result.current.isPlaying).toBe(false);
    });

    it("should toggle play/pause", () => {
      const steps = [createMockStep(0)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.isPlaying).toBe(false);

      act(() => {
        result.current.togglePlayPause();
      });

      expect(result.current.isPlaying).toBe(true);

      act(() => {
        result.current.togglePlayPause();
      });

      expect(result.current.isPlaying).toBe(false);
    });

    it("should seek to a step", () => {
      const steps = [createMockStep(0), createMockStep(1), createMockStep(2)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.seekTo(2);
      });

      expect(result.current.currentPlaybackStep).toBe(2);
    });

    it("should clamp seek within bounds", () => {
      const steps = [createMockStep(0), createMockStep(1)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.seekTo(100);
      });

      expect(result.current.currentPlaybackStep).toBe(1); // max is length - 1

      act(() => {
        result.current.seekTo(-5);
      });

      expect(result.current.currentPlaybackStep).toBe(0);
    });

    it("should go to next step", () => {
      const steps = [createMockStep(0), createMockStep(1), createMockStep(2)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.currentPlaybackStep).toBe(0);

      act(() => {
        result.current.nextStep();
      });

      expect(result.current.currentPlaybackStep).toBe(1);

      act(() => {
        result.current.nextStep();
      });

      expect(result.current.currentPlaybackStep).toBe(2);

      // Should not go past last step
      act(() => {
        result.current.nextStep();
      });

      expect(result.current.currentPlaybackStep).toBe(2);
    });

    it("should go to previous step", () => {
      const steps = [createMockStep(0), createMockStep(1), createMockStep(2)];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.seekTo(2);
      });

      act(() => {
        result.current.prevStep();
      });

      expect(result.current.currentPlaybackStep).toBe(1);

      act(() => {
        result.current.prevStep();
      });

      expect(result.current.currentPlaybackStep).toBe(0);

      // Should not go below 0
      act(() => {
        result.current.prevStep();
      });

      expect(result.current.currentPlaybackStep).toBe(0);
    });
  });

  describe("history (undo/redo)", () => {
    it("should track history for step changes", () => {
      const manifest = createMockManifest([]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.canUndo).toBe(false);

      act(() => {
        result.current.addStep();
      });

      // After first change, we should have history
      // Note: The first entry is pushed when we make a change
      expect(result.current.steps).toHaveLength(1);
    });

    it("should undo step additions", () => {
      const manifest = createMockManifest([]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.addStep();
      });

      expect(result.current.steps).toHaveLength(1);

      act(() => {
        result.current.addStep();
      });

      expect(result.current.steps).toHaveLength(2);

      act(() => {
        result.current.undo();
      });

      expect(result.current.steps).toHaveLength(1);
    });

    it("should redo after undo", () => {
      const manifest = createMockManifest([]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.addStep();
      });

      act(() => {
        result.current.addStep();
      });

      act(() => {
        result.current.undo();
      });

      expect(result.current.steps).toHaveLength(1);
      expect(result.current.canRedo).toBe(true);

      act(() => {
        result.current.redo();
      });

      expect(result.current.steps).toHaveLength(2);
    });
  });

  describe("computed values", () => {
    it("should calculate total duration", () => {
      const steps = [
        { ...createMockStep(0), actions: [{ action: "HIGHLIGHT", duration: 500 } as any] },
        { ...createMockStep(1), actions: [{ action: "COMPARE", duration: 1000 } as any] },
      ];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.totalDuration).toBe(1500);
    });

    it("should calculate current timestamp", () => {
      const steps = [
        { ...createMockStep(0), actions: [{ action: "HIGHLIGHT", duration: 500 } as any] },
        { ...createMockStep(1), actions: [{ action: "COMPARE", duration: 1000 } as any] },
        { ...createMockStep(2), actions: [{ action: "SWAP", duration: 750 } as any] },
      ];
      const manifest = createMockManifest(steps);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      // At step 0, timestamp should be 0
      expect(result.current.currentTimestamp).toBe(0);

      act(() => {
        result.current.seekTo(1);
      });

      // At step 1, timestamp should be 500 (duration of step 0)
      expect(result.current.currentTimestamp).toBe(500);

      act(() => {
        result.current.seekTo(2);
      });

      // At step 2, timestamp should be 500 + 1000 = 1500
      expect(result.current.currentTimestamp).toBe(1500);
    });
  });

  describe("dirty state", () => {
    it("should mark as dirty when steps change", () => {
      const manifest = createMockManifest([]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      expect(result.current.isDirty).toBe(false);

      act(() => {
        result.current.addStep();
      });

      expect(result.current.isDirty).toBe(true);
    });

    it("should clear dirty state after save", async () => {
      const manifest = createMockManifest([]);

      const { result } = renderHook(() =>
        useSimulationTimeline({
          manifest,
          onManifestChange,
        })
      );

      act(() => {
        result.current.addStep();
      });

      expect(result.current.isDirty).toBe(true);

      await act(async () => {
        await result.current.save();
      });

      expect(result.current.isDirty).toBe(false);
      expect(onManifestChange).toHaveBeenCalled();
    });
  });
});
