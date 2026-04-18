/**
 * @doc.type test
 * @doc.purpose Unit tests for APIMetricsService - latency tracking, percentiles, slow requests
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, beforeEach } from "vitest";
import { APIMetricsService } from "../api-metrics";

describe("APIMetricsService", () => {
  let service: APIMetricsService;

  beforeEach(() => {
    service = new APIMetricsService({
      maxMetricsHistory: 100,
      slowRequestThresholdMs: 500,
      retentionWindowMs: 60_000,
    });
  });

  describe("recordRequest", () => {
    it("records a request metric", () => {
      service.recordRequest({
        endpoint: "/api/v1/modules",
        method: "GET",
        statusCode: 200,
        latencyMs: 120,
      });

      const snapshot = service.getMetricsSnapshot();
      expect(snapshot.totalRequests).toBe(1);
      expect(snapshot.totalErrors).toBe(0);
    });

    it("counts 4xx responses as errors", () => {
      service.recordRequest({
        endpoint: "/api/v1/modules/missing",
        method: "GET",
        statusCode: 404,
        latencyMs: 40,
      });

      const snapshot = service.getMetricsSnapshot();
      expect(snapshot.totalErrors).toBe(1);
      expect(snapshot.errorRate).toBe(1);
    });

    it("counts 5xx responses as errors", () => {
      service.recordRequest({
        endpoint: "/api/v1/fail",
        method: "POST",
        statusCode: 500,
        latencyMs: 200,
      });

      const snapshot = service.getMetricsSnapshot();
      expect(snapshot.totalErrors).toBe(1);
    });

    it("tracks multiple requests with correct averages", () => {
      const latencies = [100, 200, 300];
      for (const latencyMs of latencies) {
        service.recordRequest({
          endpoint: "/api/v1/test",
          method: "GET",
          statusCode: 200,
          latencyMs,
        });
      }

      const snapshot = service.getMetricsSnapshot();
      expect(snapshot.totalRequests).toBe(3);
      expect(snapshot.avgLatencyMs).toBe(200);
    });
  });

  describe("getMetricsSnapshot", () => {
    it("returns empty snapshot with zero requests", () => {
      const snapshot = service.getMetricsSnapshot();
      expect(snapshot.totalRequests).toBe(0);
      expect(snapshot.errorRate).toBe(0);
      expect(snapshot.avgLatencyMs).toBe(0);
      expect(snapshot.endpoints).toHaveLength(0);
    });

    it("populates endpoint stats", () => {
      service.recordRequest({
        endpoint: "/api/v1/items",
        method: "GET",
        statusCode: 200,
        latencyMs: 80,
      });
      service.recordRequest({
        endpoint: "/api/v1/items",
        method: "GET",
        statusCode: 200,
        latencyMs: 120,
      });

      const snapshot = service.getMetricsSnapshot();
      const endpoint = snapshot.endpoints.find((e) => e.endpoint === "/api/v1/items");

      expect(endpoint).toBeDefined();
      expect(endpoint?.requestCount).toBe(2);
    });

    it("marks requests above threshold as slow", () => {
      service.recordRequest({
        endpoint: "/api/v1/heavy",
        method: "GET",
        statusCode: 200,
        latencyMs: 1200, // above 500ms threshold
      });
      service.recordRequest({
        endpoint: "/api/v1/fast",
        method: "GET",
        statusCode: 200,
        latencyMs: 30,
      });

      const snapshot = service.getMetricsSnapshot();
      expect(snapshot.slowRequests).toHaveLength(1);
      expect(snapshot.slowRequests[0]?.endpoint).toBe("/api/v1/heavy");
    });
  });

  describe("getSlowestEndpoints", () => {
    it("returns endpoints sorted by average latency", () => {
      for (let i = 0; i < 3; i++) {
        service.recordRequest({ endpoint: "/slow", method: "GET", statusCode: 200, latencyMs: 800 });
        service.recordRequest({ endpoint: "/medium", method: "GET", statusCode: 200, latencyMs: 300 });
        service.recordRequest({ endpoint: "/fast", method: "GET", statusCode: 200, latencyMs: 50 });
      }

      const slowest = service.getSlowestEndpoints(3);

      expect(slowest[0]?.endpoint).toBe("/slow");
      expect(slowest[1]?.endpoint).toBe("/medium");
      expect(slowest[2]?.endpoint).toBe("/fast");
    });

    it("respects the limit parameter", () => {
      for (let i = 0; i < 5; i++) {
        service.recordRequest({ endpoint: `/ep-${i}`, method: "GET", statusCode: 200, latencyMs: i * 100 });
      }

      const result = service.getSlowestEndpoints(2);
      expect(result).toHaveLength(2);
    });
  });

  describe("getErrorProneEndpoints", () => {
    it("returns endpoints with highest error rates first", () => {
      // endpoint with 100% error rate
      service.recordRequest({ endpoint: "/broken", method: "GET", statusCode: 500, latencyMs: 100 });
      // endpoint with 0% error rate
      service.recordRequest({ endpoint: "/healthy", method: "GET", statusCode: 200, latencyMs: 50 });

      const result = service.getErrorProneEndpoints(2);

      expect(result[0]?.endpoint).toBe("/broken");
    });
  });
});
