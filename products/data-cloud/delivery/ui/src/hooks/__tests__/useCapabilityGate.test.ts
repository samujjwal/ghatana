/**
 * DC-P1-006: Tests for useCapabilityGate and useCapabilitySignal hooks.
 *
 * Verifies that the hooks correctly translate runtime surface registry data
 * into boolean access signals for all three gate modes.
 */

import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  SurfaceRegistrySnapshot,
  SurfaceSignal,
} from "../../api/surfaces.service";

// ---------------------------------------------------------------------------
// Hoist mocks before imports
// ---------------------------------------------------------------------------

const { mockUseSurfaceRegistry } = vi.hoisted(() => ({
  mockUseSurfaceRegistry:
    vi.fn<
      () => { data: SurfaceRegistrySnapshot | undefined; isLoading: boolean }
    >(),
}));

vi.mock("../../api/surfaces.service", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("../../api/surfaces.service")>();
  return {
    ...actual,
    useSurfaceRegistry: mockUseSurfaceRegistry,
  };
});

import { useCapabilityGate, useCapabilitySignal } from "../useCapabilityGate";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeSnapshot(
  entries: Array<{ key: string; status: SurfaceSignal["status"] }>,
): SurfaceRegistrySnapshot {
  return {
    surfaces: entries.map(({ key, status }) => ({
      key,
      label: key,
      status,
      summary: status,
      ownerPlane: "test",
      requiredDependencies: [],
      dependencyProbes: [],
      tenantScope: "tenant",
      runtimeProfile: "test",
      limitations: "",
      actionsAllowed: [],
      rawValue: status,
    })),
    generatedAt: "2026-01-01T00:00:00Z",
    requestId: "test-req",
    tenantId: "test-tenant",
  };
}

// ---------------------------------------------------------------------------
// useCapabilityGate — mode: 'active'
// ---------------------------------------------------------------------------

describe("useCapabilityGate — mode: active", () => {
  beforeEach(() => vi.clearAllMocks());

  it("returns true when the capability is active", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "analytics", status: "LIVE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["analytics"], "active"),
    );
    expect(result.current).toBe(true);
  });

  it("returns false when the capability is degraded (strict active mode)", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "analytics", status: "DEGRADED" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["analytics"], "active"),
    );
    expect(result.current).toBe(false);
  });

  it("returns false when the capability is unavailable", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "analytics", status: "UNAVAILABLE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["analytics"], "active"),
    );
    expect(result.current).toBe(false);
  });

  it("returns false when the capability key is not in the registry", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["missing-capability"], "active"),
    );
    expect(result.current).toBe(false);
  });

  it("returns true while registry is still loading (open-while-loading default)", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: undefined,
      isLoading: true,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["analytics"], "active"),
    );
    expect(result.current).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// useCapabilityGate — mode: 'activeOrDegraded'
// ---------------------------------------------------------------------------

describe("useCapabilityGate — mode: activeOrDegraded", () => {
  beforeEach(() => vi.clearAllMocks());

  it("returns true when the capability is active", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "ai.assist", status: "LIVE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["ai.assist"], "activeOrDegraded"),
    );
    expect(result.current).toBe(true);
  });

  it("returns true when the capability is degraded", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "ai.assist", status: "DEGRADED" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["ai.assist"], "activeOrDegraded"),
    );
    expect(result.current).toBe(true);
  });

  it("returns false when the capability is unavailable", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "ai.assist", status: "UNAVAILABLE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["ai.assist"], "activeOrDegraded"),
    );
    expect(result.current).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// useCapabilityGate — mode: 'notUnavailable'
// ---------------------------------------------------------------------------

describe("useCapabilityGate — mode: notUnavailable", () => {
  beforeEach(() => vi.clearAllMocks());

  it("returns true when the capability is active", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "data-fabric", status: "LIVE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["data-fabric"], "notUnavailable"),
    );
    expect(result.current).toBe(true);
  });

  it("returns true when the capability is degraded", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "data-fabric", status: "DEGRADED" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["data-fabric"], "notUnavailable"),
    );
    expect(result.current).toBe(true);
  });

  it("returns false when the capability is unavailable", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "data-fabric", status: "UNAVAILABLE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["data-fabric"], "notUnavailable"),
    );
    expect(result.current).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// useCapabilityGate — alias resolution
// ---------------------------------------------------------------------------

describe("useCapabilityGate — alias fallback", () => {
  beforeEach(() => vi.clearAllMocks());

  it("matches on the second alias when the first is absent", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "ai_assist", status: "LIVE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() =>
      useCapabilityGate(["ai.assist", "ai_assist"], "active"),
    );
    expect(result.current).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// useCapabilitySignal
// ---------------------------------------------------------------------------

describe("useCapabilitySignal", () => {
  beforeEach(() => vi.clearAllMocks());

  it("returns the capability signal for a known key", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "analytics", status: "DEGRADED" }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilitySignal(["analytics"]));
    expect(result.current?.key).toBe("analytics");
    expect(result.current?.status).toBe("DEGRADED");
  });

  it("returns undefined when registry is not yet loaded", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: undefined,
      isLoading: true,
    });

    const { result } = renderHook(() => useCapabilitySignal(["analytics"]));
    expect(result.current).toBeUndefined();
  });

  it("returns undefined for unknown capability key", () => {
    mockUseSurfaceRegistry.mockReturnValue({
      data: makeSnapshot([{ key: "analytics", status: "LIVE" }]),
      isLoading: false,
    });

    const { result } = renderHook(() => useCapabilitySignal(["unknown-cap"]));
    expect(result.current).toBeUndefined();
  });
});
