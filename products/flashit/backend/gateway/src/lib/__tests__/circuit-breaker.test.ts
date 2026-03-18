/**
 * @fileoverview Circuit Breaker Tests
 * Tests for circuit breaker pattern implementation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  CircuitBreaker,
  CircuitState,
  CircuitBreakerConfig,
} from "../circuit-breaker";

describe("CircuitBreaker", () => {
  const defaultConfig: CircuitBreakerConfig = {
    failureThreshold: 3,
    successThreshold: 2,
    timeout: 1000,
    resetTimeout: 100,
    monitoringWindow: 5000,
  };

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("initial state", () => {
    it("should start in CLOSED state", () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      expect(cb.getStats().state).toBe(CircuitState.CLOSED);
    });

    it("should have zero counters initially", () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const stats = cb.getStats();
      expect(stats.failures).toBe(0);
      expect(stats.successes).toBe(0);
      expect(stats.rejections).toBe(0);
    });
  });

  describe("closed state operation", () => {
    it("should execute function successfully", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockResolvedValue("success");

      const result = await cb.execute(fn);

      expect(result).toBe("success");
      expect(fn).toHaveBeenCalled();
    });

    it("should track successful executions", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockResolvedValue("success");

      await cb.execute(fn);
      await cb.execute(fn);

      const stats = cb.getStats();
      expect(stats.successes).toBe(2);
    });

    it("should track failures", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockRejectedValue(new Error("failure"));

      try {
        await cb.execute(fn);
      } catch {
        // expected
      }

      const stats = cb.getStats();
      expect(stats.failures).toBe(1);
      expect(stats.lastFailureTime).not.toBeNull();
    });
  });

  describe("opening circuit", () => {
    it("should open after threshold failures", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockRejectedValue(new Error("failure"));

      // Trigger 3 failures (threshold)
      for (let i = 0; i < 3; i++) {
        try {
          await cb.execute(fn);
        } catch {
          // expected
        }
      }

      expect(cb.getStats().state).toBe(CircuitState.OPEN);
    });

    it("should reject requests when OPEN", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockRejectedValue(new Error("failure"));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        try {
          await cb.execute(fn);
        } catch {
          // expected
        }
      }

      const successFn = vi.fn().mockResolvedValue("success");
      await expect(cb.execute(successFn)).rejects.toThrow(
        "Circuit breaker test-service is OPEN",
      );
      expect(successFn).not.toHaveBeenCalled();
    });

    it("should track rejections", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockRejectedValue(new Error("failure"));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        try {
          await cb.execute(fn);
        } catch {
          // expected
        }
      }

      // Try more requests
      try {
        await cb.execute(fn);
      } catch {
        // expected
      }

      expect(cb.getStats().rejections).toBe(1);
    });
  });

  describe("half-open state", () => {
    it("should transition to HALF_OPEN after reset timeout", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockRejectedValue(new Error("failure"));

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        try {
          await cb.execute(fn);
        } catch {
          // expected
        }
      }

      expect(cb.getStats().state).toBe(CircuitState.OPEN);

      // Advance time past reset timeout
      vi.advanceTimersByTime(150);

      // Next request should trigger half-open check
      const successFn = vi.fn().mockResolvedValue("success");
      await cb.execute(successFn);

      expect(cb.getStats().state).toBe(CircuitState.HALF_OPEN);
    });

    it("should close after success threshold in half-open", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const failFn = vi.fn().mockRejectedValue(new Error("failure"));

      // Open circuit
      for (let i = 0; i < 3; i++) {
        try {
          await cb.execute(failFn);
        } catch {
          // expected
        }
      }

      // Wait for reset
      vi.advanceTimersByTime(150);

      // Execute success twice (successThreshold = 2)
      const successFn = vi.fn().mockResolvedValue("success");
      await cb.execute(successFn);
      await cb.execute(successFn);

      expect(cb.getStats().state).toBe(CircuitState.CLOSED);
    });

    it("should open again if failure in half-open", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const failFn = vi.fn().mockRejectedValue(new Error("failure"));

      // Open circuit
      for (let i = 0; i < 3; i++) {
        try {
          await cb.execute(failFn);
        } catch {
          // expected
        }
      }

      // Wait for reset
      vi.advanceTimersByTime(150);

      // One failure should reopen
      try {
        await cb.execute(failFn);
      } catch {
        // expected
      }

      expect(cb.getStats().state).toBe(CircuitState.OPEN);
    });
  });

  describe("fallback handling", () => {
    it("should use fallback when circuit is open", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockRejectedValue(new Error("failure"));
      const fallback = vi.fn().mockResolvedValue("fallback-value");

      // Open the circuit
      for (let i = 0; i < 3; i++) {
        try {
          await cb.execute(fn);
        } catch {
          // expected
        }
      }

      const result = await cb.execute(fn, fallback);

      expect(result).toBe("fallback-value");
      expect(fallback).toHaveBeenCalled();
    });

    it("should not use fallback on success", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const fn = vi.fn().mockResolvedValue("success");
      const fallback = vi.fn().mockResolvedValue("fallback");

      const result = await cb.execute(fn, fallback);

      expect(result).toBe("success");
      expect(fallback).not.toHaveBeenCalled();
    });
  });

  describe("timeout handling", () => {
    it("should treat timeout as failure", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);
      const slowFn = vi
        .fn()
        .mockImplementation(
          () => new Promise((resolve) => setTimeout(resolve, 2000)),
        );

      try {
        await cb.execute(slowFn);
      } catch {
        // expected
      }

      vi.advanceTimersByTime(1100);

      expect(cb.getStats().failures).toBe(1);
    });
  });

  describe("stats", () => {
    it("should provide accurate statistics", async () => {
      const cb = new CircuitBreaker("test-service", defaultConfig);

      const successFn = vi.fn().mockResolvedValue("success");
      const failFn = vi.fn().mockRejectedValue(new Error("failure"));

      await cb.execute(successFn);
      await cb.execute(successFn);

      try {
        await cb.execute(failFn);
      } catch {
        // expected
      }

      const stats = cb.getStats();
      expect(stats.state).toBe(CircuitState.CLOSED);
      expect(stats.successes).toBe(2);
      expect(stats.failures).toBe(1);
      expect(stats.lastSuccessTime).not.toBeNull();
      expect(stats.lastFailureTime).not.toBeNull();
    });
  });
});
