import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import MetricsBridge, { UnifiedMetricsEvent } from "../metrics-bridge";

import type { PluginMetrics } from "../../core/plugin/PluginSystemAdapter";

describe("MetricsBridge", () => {
  let bridge: MetricsBridge;

  beforeEach(() => {
    bridge = MetricsBridge.getInstance();

    // Mock Chrome runtime
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (global as any).chrome = {
      runtime: {
        sendNativeMessage: vi.fn((host, message, callback) => {
          // Simulate successful response
          setTimeout(() => {
            callback({
              success: true,
              messageId: message.messageId,
              receivedAt: Date.now(),
              processedAt: Date.now(),
            });
          }, 10);
        }),
        lastError: null,
      },
    };
  });

  afterEach(() => {
    bridge.shutdown();
  });

  describe("Initialization", () => {
    it("should initialize successfully", async () => {
      await bridge.initialize({
        deviceId: "device-123",
        childUserId: "user-456",
      });

      expect(bridge.isReady()).toBe(true);
    });

    it("should fail if native messaging unavailable", async () => {
      (global as any).chrome.runtime.sendNativeMessage = vi.fn(
        (host, message, callback) => {
          (global as any).chrome.runtime.lastError = {
            message: "Native host not found",
          };
          callback(null);
        }
      );

      await expect(
        bridge.initialize({
          deviceId: "device-123",
          childUserId: "user-456",
        })
      ).rejects.toThrow();
    });

    it("should handle missing Chrome runtime", async () => {
      const originalChrome = (global as any).chrome;
      (global as any).chrome = undefined;

      await expect(
        bridge.initialize({
          deviceId: "device-123",
          childUserId: "user-456",
        })
      ).rejects.toThrow("Chrome runtime not available");

      (global as any).chrome = originalChrome;
    });
  });

  describe("Unified Event Creation", () => {
    beforeEach(async () => {
      await bridge.initialize({
        deviceId: "device-123",
        childUserId: "user-456",
      });
    });

    it("should create unified event with system metrics", () => {
      const metrics: PluginMetrics = {
        cpu: {
          usage: 45.5,
          temperature: 65,
          throttled: false,
          cores: 8,
        },
        memory: {
          usage: 62.3,
          total: 16384,
          available: 6200,
          gcActivity: 0,
        },
        battery: {
          level: 78.5,
          charging: true,
          health: "good",
          timeRemaining: 120,
        },
        timestamp: Date.now(),
      };

      // Access private method for testing (via reflection)
      const event = (bridge as any).createUnifiedEvent(metrics);

      expect(event).toMatchObject({
        deviceId: "device-123",
        childUserId: "user-456",
        system: {
          cpu: {
            percent: 45.5,
            temperature: 65,
            cores: 8,
            throttled: false,
          },
          memory: {
            percent: 62.3,
            totalMB: 16384,
          },
          battery: {
            percent: 78.5,
            charging: true,
            health: "good",
          },
        },
      });

      expect(event.eventId).toMatch(/^evt_\d+$/);
      expect(event.timestamp).toBeLessThanOrEqual(Date.now());
    });

    it("should format battery time correctly", () => {
      const metrics: PluginMetrics = {
        cpu: {
          usage: 45,
          temperature: 65,
          throttled: false,
          cores: 8,
        },
        memory: {
          usage: 62,
          total: 16384,
          available: 6200,
          gcActivity: 0,
        },
        battery: {
          level: 78,
          charging: true,
          health: "good",
          timeRemaining: 120, // 120 minutes
        },
        timestamp: Date.now(),
      };

      const event = (bridge as any).createUnifiedEvent(metrics);

      // Should convert to milliseconds
      expect(event.system.battery.timeRemainingMs).toBe(120 * 60 * 1000);
    });

    it("should detect significant metric changes", () => {
      const metrics1: PluginMetrics = {
        cpu: { usage: 40, temperature: 60, throttled: false, cores: 8 },
        memory: { usage: 60, total: 16384, available: 6554, gcActivity: 0 },
        battery: {
          level: 80,
          charging: true,
          health: "good",
          timeRemaining: 120,
        },
        timestamp: Date.now(),
      };

      // First event should always be forwarded
      expect((bridge as any).hasSignificantChange(metrics1)).toBe(true);

      // Store as last metrics
      (bridge as any).lastSystemMetrics = metrics1;

      // Small change should not trigger forward
      const metrics2: PluginMetrics = {
        ...metrics1,
        cpu: { ...metrics1.cpu, usage: 42 }, // 2% change
      };
      expect((bridge as any).hasSignificantChange(metrics2)).toBe(false);

      // Large change should trigger forward
      const metrics3: PluginMetrics = {
        ...metrics1,
        cpu: { ...metrics1.cpu, usage: 50 }, // 10% change (> 5% threshold)
      };
      expect((bridge as any).hasSignificantChange(metrics3)).toBe(true);
    });

    it("should detect memory changes", () => {
      const metrics1: PluginMetrics = {
        cpu: { usage: 40, temperature: 60, throttled: false, cores: 8 },
        memory: { usage: 60, total: 16384, available: 6554, gcActivity: 0 },
        battery: {
          level: 80,
          charging: true,
          health: "good",
          timeRemaining: 120,
        },
        timestamp: Date.now(),
      };

      (bridge as any).lastSystemMetrics = metrics1;

      const metrics2: PluginMetrics = {
        ...metrics1,
        memory: { ...metrics1.memory, usage: 67 }, // 7% change
      };
      expect((bridge as any).hasSignificantChange(metrics2)).toBe(true);
    });

    it("should detect battery changes", () => {
      const metrics1: PluginMetrics = {
        cpu: { usage: 40, temperature: 60, throttled: false, cores: 8 },
        memory: { usage: 60, total: 16384, available: 6554, gcActivity: 0 },
        battery: {
          level: 80,
          charging: true,
          health: "good",
          timeRemaining: 120,
        },
        timestamp: Date.now(),
      };

      (bridge as any).lastSystemMetrics = metrics1;

      const metrics2: PluginMetrics = {
        ...metrics1,
        battery: { ...metrics1.battery, level: 73 }, // 7% change
      };
      expect((bridge as any).hasSignificantChange(metrics2)).toBe(true);
    });
  });

  describe("Message Queuing", () => {
    beforeEach(async () => {
      await bridge.initialize({
        deviceId: "device-123",
        childUserId: "user-456",
      });
    });

    it("should queue messages when connection busy", () => {
      const event: UnifiedMetricsEvent = {
        timestamp: Date.now(),
        eventId: "evt_1",
        deviceId: "device-123",
        childUserId: "user-456",
        system: {
          cpu: { percent: 45, temperature: 65, cores: 8, throttled: false },
          memory: {
            percent: 62,
            usedMB: 10173,
            totalMB: 16384,
            availableMB: 6200,
          },
          battery: {
            percent: 78,
            charging: true,
            health: "good",
            timeRemainingMs: 7200000,
          },
        },
        quality: {
          systemMetricsValid: true,
          usageMetricsValid: true,
          lastSystemMetricAge: 0,
          lastUsageMetricAge: 0,
        },
      };

      (bridge as any).enqueueMessage(event);
      expect(bridge.getQueueSize()).toBe(1);

      (bridge as any).enqueueMessage({ ...event, eventId: "evt_2" });
      expect(bridge.getQueueSize()).toBe(2);
    });

    it("should not exceed max queue size", () => {
      const maxSize = (bridge as any).config.maxQueueSize;

      for (let i = 0; i < maxSize + 10; i++) {
        (bridge as any).enqueueMessage({
          timestamp: Date.now(),
          eventId: `evt_${i}`,
          deviceId: "device-123",
          childUserId: "user-456",
          system: {
            cpu: { percent: 45, temperature: 65, cores: 8, throttled: false },
            memory: {
              percent: 62,
              usedMB: 10173,
              totalMB: 16384,
              availableMB: 6200,
            },
            battery: {
              percent: 78,
              charging: true,
              health: "good",
              timeRemainingMs: 7200000,
            },
          },
          quality: {
            systemMetricsValid: true,
            usageMetricsValid: true,
            lastSystemMetricAge: 0,
            lastUsageMetricAge: 0,
          },
        });
      }

      expect(bridge.getQueueSize()).toBeLessThanOrEqual(maxSize);
    });

    it("should drop oldest message when queue full", () => {
      const maxSize = (bridge as any).config.maxQueueSize;

      // Fill queue
      for (let i = 0; i < maxSize; i++) {
        (bridge as any).enqueueMessage({
          timestamp: Date.now(),
          eventId: `evt_${i}`,
          deviceId: "device-123",
          childUserId: "user-456",
          system: {
            cpu: { percent: 45, temperature: 65, cores: 8, throttled: false },
            memory: {
              percent: 62,
              usedMB: 10173,
              totalMB: 16384,
              availableMB: 6200,
            },
            battery: {
              percent: 78,
              charging: true,
              health: "good",
              timeRemainingMs: 7200000,
            },
          },
          quality: {
            systemMetricsValid: true,
            usageMetricsValid: true,
            lastSystemMetricAge: 0,
            lastUsageMetricAge: 0,
          },
        });
      }

      // Add one more - should drop first
      const newEvent: UnifiedMetricsEvent = {
        timestamp: Date.now(),
        eventId: `evt_${maxSize}`,
        deviceId: "device-123",
        childUserId: "user-456",
        system: {
          cpu: { percent: 45, temperature: 65, cores: 8, throttled: false },
          memory: {
            percent: 62,
            usedMB: 10173,
            totalMB: 16384,
            availableMB: 6200,
          },
          battery: {
            percent: 78,
            charging: true,
            health: "good",
            timeRemainingMs: 7200000,
          },
        },
        quality: {
          systemMetricsValid: true,
          usageMetricsValid: true,
          lastSystemMetricAge: 0,
          lastUsageMetricAge: 0,
        },
      };

      (bridge as any).enqueueMessage(newEvent);

      expect(bridge.getQueueSize()).toBe(maxSize);
      // New event should be in queue
      expect((bridge as any).messageQueue[maxSize - 1]).toEqual(newEvent);
    });
  });

  describe("Native Messaging", () => {
    beforeEach(async () => {
      await bridge.initialize({
        deviceId: "device-123",
        childUserId: "user-456",
      });
    });

    it("should send message to native host", async () => {
      const sendSpy = vi.spyOn(
        (global as any).chrome.runtime,
        "sendNativeMessage"
      );

      const event: UnifiedMetricsEvent = {
        timestamp: Date.now(),
        eventId: "evt_1",
        deviceId: "device-123",
        childUserId: "user-456",
        system: {
          cpu: { percent: 45, temperature: 65, cores: 8, throttled: false },
          memory: {
            percent: 62,
            usedMB: 10173,
            totalMB: 16384,
            availableMB: 6200,
          },
          battery: {
            percent: 78,
            charging: true,
            health: "good",
            timeRemainingMs: 7200000,
          },
        },
        quality: {
          systemMetricsValid: true,
          usageMetricsValid: true,
          lastSystemMetricAge: 0,
          lastUsageMetricAge: 0,
        },
      };

      await (bridge as any).sendToRustPlugin(event);

      expect(sendSpy).toHaveBeenCalledWith(
        "com.ghatana.guardian.desktop",
        expect.objectContaining({
          type: "METRICS_UPDATE",
          messageId: "evt_1",
          payload: event,
        }),
        expect.any(Function)
      );
    });

    it("should timeout on no response", async () => {
      (global as any).chrome.runtime.sendNativeMessage = vi.fn(
        (host, message, callback) => {
          // Never call callback (simulate timeout)
        }
      );

      const event: UnifiedMetricsEvent = {
        timestamp: Date.now(),
        eventId: "evt_1",
        deviceId: "device-123",
        childUserId: "user-456",
        system: {
          cpu: { percent: 45, temperature: 65, cores: 8, throttled: false },
          memory: {
            percent: 62,
            usedMB: 10173,
            totalMB: 16384,
            availableMB: 6200,
          },
          battery: {
            percent: 78,
            charging: true,
            health: "good",
            timeRemainingMs: 7200000,
          },
        },
        quality: {
          systemMetricsValid: true,
          usageMetricsValid: true,
          lastSystemMetricAge: 0,
          lastUsageMetricAge: 0,
        },
      };

      // Shorten timeout for test
      (bridge as any).config.messageTimeoutMs = 100;

      await expect((bridge as any).sendToRustPlugin(event)).rejects.toThrow(
        "Message timeout"
      );
    });

    it("should handle native messaging errors", async () => {
      (global as any).chrome.runtime.sendNativeMessage = vi.fn(
        (host, message, callback) => {
          (global as any).chrome.runtime.lastError = {
            message: "Native host disconnected",
          };
          callback(null);
        }
      );

      const event: UnifiedMetricsEvent = {
        timestamp: Date.now(),
        eventId: "evt_1",
        deviceId: "device-123",
        childUserId: "user-456",
        system: {
          cpu: { percent: 45, temperature: 65, cores: 8, throttled: false },
          memory: {
            percent: 62,
            usedMB: 10173,
            totalMB: 16384,
            availableMB: 6200,
          },
          battery: {
            percent: 78,
            charging: true,
            health: "good",
            timeRemainingMs: 7200000,
          },
        },
        quality: {
          systemMetricsValid: true,
          usageMetricsValid: true,
          lastSystemMetricAge: 0,
          lastUsageMetricAge: 0,
        },
      };

      const response = await (bridge as any).sendToRustPlugin(event);

      expect(response.success).toBe(false);
      expect(response.error?.code).toBe("SEND_ERROR");
    });
  });

  describe("Connection Status", () => {
    it("should report not ready before initialization", () => {
      expect(bridge.isReady()).toBe(false);
    });

    it("should report ready after initialization", async () => {
      await bridge.initialize({
        deviceId: "device-123",
        childUserId: "user-456",
      });

      expect(bridge.isReady()).toBe(true);
    });

    it("should report not ready after shutdown", async () => {
      await bridge.initialize({
        deviceId: "device-123",
        childUserId: "user-456",
      });

      bridge.shutdown();

      expect(bridge.isReady()).toBe(false);
    });
  });

  describe("Metrics Retrieval", () => {
    beforeEach(async () => {
      await bridge.initialize({
        deviceId: "device-123",
        childUserId: "user-456",
      });
    });

    it("should return null before any metrics", () => {
      expect(bridge.getLastMetrics()).toBeNull();
    });

    it("should return empty queue initially", () => {
      expect(bridge.getQueueSize()).toBe(0);
    });
  });
});
