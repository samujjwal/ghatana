import { describe, expect, it } from "vitest";
import { getSimulationStarterById, listSimulationStarters } from "./starter-catalog";
import {
  buildSimulationAccessibilityRuntime,
  validateSimulationAccessibilityRuntime,
} from "./accessibility";

describe("simulation accessibility runtime", () => {
  it("builds keyboard, ARIA, focus, text alternative, and state-summary metadata for every starter domain", () => {
    const startersByDomain = new Map(
      listSimulationStarters().map((starter) => [starter.domain, starter]),
    );

    for (const starter of startersByDomain.values()) {
      const runtime = buildSimulationAccessibilityRuntime(starter.manifest);

      expect(runtime.aria.role, starter.id).toBe("application");
      expect(runtime.aria.tabIndex, starter.id).toBe(0);
      expect(runtime.aria["aria-label"], starter.id).toContain(starter.name);
      expect(runtime.focusClassName, starter.id).toContain("focus-visible");
      expect(runtime.keyboardControls.length, starter.id).toBeGreaterThan(0);
      expect(runtime.keyboardControls[0]?.keys.increment, starter.id).toContain("ArrowRight");
      expect(runtime.chartAltText, starter.id).toContain("Baseline output");
      expect(runtime.nonVisualStateSummary, starter.id).toContain("Step 1");
      expect(validateSimulationAccessibilityRuntime(starter.manifest), starter.id).toEqual([]);
    }
  });

  it("disables autoplay and slows playback when reduced motion is requested", () => {
    const starter = getSimulationStarterById("starter-newton-cart");
    expect(starter).not.toBeNull();

    const runtime = buildSimulationAccessibilityRuntime(starter!.manifest, {
      prefersReducedMotion: true,
    });

    expect(runtime.reducedMotionPlayback.autoPlay).toBe(false);
    expect(runtime.reducedMotionPlayback.loop).toBe(false);
    expect(runtime.reducedMotionPlayback.defaultSpeed).toBeLessThanOrEqual(0.5);
  });
});
