import { VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE } from "@/lib/runtime-boundaries";
import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  useDashboards,
  useDataCloudMetrics,
  useRecentEvents,
  useSystemHealth,
} from "../visualization-integration";

describe("visualization integration launcher boundaries", () => {
  it("returns boundary errors and empty data for visualization hooks", async () => {
    const { result: metricsResult } = renderHook(() =>
      useDataCloudMetrics({
        metricIds: ["events.processed"],
        timeRange: { preset: "last1h" },
      }),
    );
    const { result: dashboardsResult } = renderHook(() => useDashboards());
    const { result: healthResult } = renderHook(() => useSystemHealth());
    const { result: recentEventsResult } = renderHook(() =>
      useRecentEvents({ limit: 10 }),
    );

    expect(metricsResult.current.metrics).toEqual([]);
    expect(metricsResult.current.error?.message).toBe(
      VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE,
    );
    await expect(metricsResult.current.refetch()).rejects.toThrow(
      VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE,
    );

    expect(dashboardsResult.current.dashboards).toEqual([]);
    expect(dashboardsResult.current.error?.message).toBe(
      VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE,
    );
    await expect(
      dashboardsResult.current.save({
        id: "main",
        name: "Main",
        layout: "grid",
        panels: [],
      }),
    ).rejects.toThrow(VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE);

    expect(healthResult.current.health).toBeUndefined();
    expect(healthResult.current.error?.message).toBe(
      VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE,
    );
    expect(recentEventsResult.current.events).toEqual([]);
    expect(recentEventsResult.current.error?.message).toBe(
      VISUALIZATION_INTEGRATION_BOUNDARY_MESSAGE,
    );
  });
});
