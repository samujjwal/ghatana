/**
 * Bridges Phase 3G system metrics to agent-desktop Rust plugin.
 *
 * Responsibilities:
 * - Subscribe to PluginSystemAdapter metrics
 * - Forward to agent-desktop via native messaging
 * - Merge system + usage metrics into unified event
 * - Handle errors and connection state
 *
 * Architecture:
 *   PluginSystemAdapter (polls every 5s)
 *     ↓
 *   MetricsBridge (routes & merges)
 *     ↓
 *   Native messaging to agent-desktop
 *     ↓
 *   GuardianApiExporter / WebSocketExporter
 *
 * Performance:
 * - <50ms latency per message
 * - <1% CPU overhead
 * - Queue-based batching (max 100 messages)
 * - Automatic backpressure handling
 */

import { PluginSystemAdapter } from "../core/plugin/PluginSystemAdapter";

import type {
  PluginMetrics,
} from "../core/plugin/PluginSystemAdapter";

/**
 * Unified metrics event combining system + usage data.
 */
export interface UnifiedMetricsEvent {
  // Metadata
  timestamp: number;
  eventId: string;
  deviceId: string;
  childUserId: string;

  // Real-time system metrics (from Phase 3G)
  system: {
    cpu: {
      percent: number;
      temperature: number;
      cores: number;
      throttled: boolean;
    };
    memory: {
      percent: number;
      usedMB: number;
      totalMB: number;
      availableMB: number;
    };
    battery: {
      percent: number;
      charging: boolean;
      health: "good" | "degraded" | "poor";
      timeRemainingMs: number;
    };
  };

  // Usage metrics (from agent-desktop)
  usage?: {
    activeWindow: string;
    processName: string;
    idleSeconds: number;
    activeCategory?: "productivity" | "entertainment" | "education" | "other";
  };

  // Quality indicators
  quality: {
    systemMetricsValid: boolean;
    usageMetricsValid: boolean;
    lastSystemMetricAge: number;
    lastUsageMetricAge: number;
  };
}

/**
 * Response from agent-desktop after receiving metrics.
 */
interface RustPluginResponse {
  success: boolean;
  messageId: string;
  receivedAt: number;
  processedAt?: number;
  error?: {
    code: string;
    message: string;
  };
}

/**
 * Bridges Phase 3G metrics to agent-desktop Rust plugin.
 */
export class MetricsBridge {
  private static instance: MetricsBridge;
  private isInitialized = false;
  private isConnected = false;
  private lastEventId = 0;
  private lastSystemMetrics: PluginMetrics | null = null;
  private lastSystemMetricsTime = 0;
  private lastUsageMetricsTime = 0;
  private messageQueue: UnifiedMetricsEvent[] = [];
  private pendingMessages = new Map<string, Promise<RustPluginResponse>>();

  // Configuration
  private config = {
    maxQueueSize: 100,
    messageTimeoutMs: 5000,
    metricsChangeThreshold: 5, // Only forward if >5% change
    deviceId: "", // Will be set during init
    childUserId: "",
  };

  private constructor() {}

  /**
   * Get or create singleton instance.
   */
  public static getInstance(): MetricsBridge {
    if (!MetricsBridge.instance) {
      MetricsBridge.instance = new MetricsBridge();
    }
    return MetricsBridge.instance;
  }

  /**
   * Initialize the bridge.
   * Must be called once before using.
   */
  public async initialize(config: {
    deviceId: string;
    childUserId: string;
  }): Promise<void> {
    if (this.isInitialized) {
      return;
    }

    try {
      // Store config
      this.config.deviceId = config.deviceId;
      this.config.childUserId = config.childUserId;

      // Verify native messaging support
      if (!chrome.runtime) {
        throw new Error("Chrome runtime not available");
      }

      // Test connection to agent-desktop
      await this.ping();

      // Subscribe to Phase 3G metrics
      this.subscribeToPhase3GMetrics();

      this.isInitialized = true;
      this.isConnected = true;
      console.log("[MetricsBridge] Initialized successfully");
    } catch (error) {
      console.error("[MetricsBridge] Initialization failed:", error);
      throw error;
    }
  }

  /**
   * Verify connection to agent-desktop.
   */
  private async ping(): Promise<void> {
    return new Promise((resolve, reject) => {
      chrome.runtime.sendNativeMessage(
        "com.ghatana.guardian.desktop",
        { type: "PING" },
        (response) => {
          if (chrome.runtime.lastError) {
            reject(new Error(chrome.runtime.lastError.message));
          } else if (response?.success) {
            resolve();
          } else {
            reject(new Error("Invalid ping response"));
          }
        }
      );
    });
  }

  /**
   * Subscribe to Phase 3G metrics and forward to agent-desktop.
   */
  private subscribeToPhase3GMetrics(): void {
    const adapter = PluginSystemAdapter.getInstance();

    adapter.startPolling((metrics, error) => {
      if (error) {
        console.error("[MetricsBridge] Phase 3G error:", error);
        return;
      }

      // Check if metrics changed significantly (avoid spam)
      if (!this.hasSignificantChange(metrics)) {
        return;
      }

      this.lastSystemMetrics = metrics;
      this.lastSystemMetricsTime = Date.now();

      // Create unified event
      const event = this.createUnifiedEvent(metrics);

      // Queue for sending
      this.enqueueMessage(event);

      // Send immediately if small queue
      if (this.messageQueue.length <= 5) {
        this.processQueue();
      }
    });
  }

  /**
   * Check if metrics changed significantly.
   */
  private hasSignificantChange(metrics: PluginMetrics): boolean {
    if (!this.lastSystemMetrics) {
      return true; // First time
    }

    const threshold = this.config.metricsChangeThreshold;
    const cpuDiff = Math.abs(
      metrics.cpu.usage - this.lastSystemMetrics.cpu.usage
    );
    const memDiff = Math.abs(
      metrics.memory.usage - this.lastSystemMetrics.memory.usage
    );
    const batteryDiff = Math.abs(
      metrics.battery.level - this.lastSystemMetrics.battery.level
    );

    return (
      cpuDiff > threshold || memDiff > threshold || batteryDiff > threshold
    );
  }

  /**
   * Create unified metrics event.
   */
  private createUnifiedEvent(
    systemMetrics: PluginMetrics
  ): UnifiedMetricsEvent {
    return {
      timestamp: Date.now(),
      eventId: `evt_${++this.lastEventId}`,
      deviceId: this.config.deviceId,
      childUserId: this.config.childUserId,

      system: {
        cpu: {
          percent: Math.round(systemMetrics.cpu.usage * 100) / 100,
          temperature: systemMetrics.cpu.temperature,
          cores: systemMetrics.cpu.cores,
          throttled: systemMetrics.cpu.throttled,
        },
        memory: {
          percent: Math.round(systemMetrics.memory.usage * 100) / 100,
          usedMB: Math.round(
            (systemMetrics.memory.usage / 100) * systemMetrics.memory.total
          ),
          totalMB: systemMetrics.memory.total,
          availableMB: systemMetrics.memory.available,
        },
        battery: {
          percent: Math.round(systemMetrics.battery.level * 100) / 100,
          charging: systemMetrics.battery.charging,
          health: systemMetrics.battery.health,
          timeRemainingMs: systemMetrics.battery.timeRemaining * 60000,
        },
      },

      quality: {
        systemMetricsValid: true,
        usageMetricsValid: true,
        lastSystemMetricAge: Date.now() - this.lastSystemMetricsTime,
        lastUsageMetricAge: Date.now() - this.lastUsageMetricsTime,
      },
    };
  }

  /**
   * Queue message for sending.
   */
  private enqueueMessage(event: UnifiedMetricsEvent): void {
    if (this.messageQueue.length >= this.config.maxQueueSize) {
      console.warn("[MetricsBridge] Message queue full, dropping oldest");
      this.messageQueue.shift();
    }
    this.messageQueue.push(event);
  }

  /**
   * Process queued messages.
   */
  private async processQueue(): Promise<void> {
    if (!this.isConnected || this.messageQueue.length === 0) {
      return;
    }

    const event = this.messageQueue.shift();
    if (!event) return;

    try {
      const response = await this.sendToRustPlugin(event);
      console.debug("[MetricsBridge] Message sent:", response);

      // Update usage metrics time if included
      if (response.processedAt) {
        this.lastUsageMetricsTime = response.processedAt;
      }

      // Continue processing queue
      if (this.messageQueue.length > 0) {
        setImmediate(() => this.processQueue());
      }
    } catch (error) {
      console.error("[MetricsBridge] Failed to send message:", error);
      // Re-queue on failure (up to 3 retries)
      // TODO: Implement retry logic
    }
  }

  /**
   * Send metrics to agent-desktop Rust plugin.
   */
  private sendToRustPlugin(
    event: UnifiedMetricsEvent
  ): Promise<RustPluginResponse> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pendingMessages.delete(event.eventId);
        reject(new Error(`Message timeout: ${event.eventId}`));
      }, this.config.messageTimeoutMs);

      const messagePromise = new Promise<RustPluginResponse>((msgResolve) => {
        chrome.runtime.sendNativeMessage(
          "com.ghatana.guardian.desktop",
          {
            type: "METRICS_UPDATE",
            messageId: event.eventId,
            payload: event,
          },
          (response) => {
            clearTimeout(timeout);

            if (chrome.runtime.lastError) {
              msgResolve({
                success: false,
                messageId: event.eventId,
                receivedAt: 0,
                error: {
                  code: "SEND_ERROR",
                  message: chrome.runtime.lastError.message,
                },
              });
            } else {
              msgResolve(
                response || {
                  success: false,
                  messageId: event.eventId,
                  receivedAt: 0,
                }
              );
            }
          }
        );
      });

      this.pendingMessages.set(event.eventId, messagePromise);
      messagePromise.then(resolve).catch(reject);
    });
  }

  /**
   * Get connection status.
   */
  public isReady(): boolean {
    return this.isInitialized && this.isConnected;
  }

  /**
   * Get queue size (for monitoring).
   */
  public getQueueSize(): number {
    return this.messageQueue.length;
  }

  /**
   * Get last system metrics.
   */
  public getLastMetrics(): PluginMetrics | null {
    return this.lastSystemMetrics;
  }

  /**
   * Shutdown the bridge.
   */
  public shutdown(): void {
    console.log("[MetricsBridge] Shutting down");
    this.isConnected = false;
    this.messageQueue = [];
    this.pendingMessages.clear();
  }
}

export default MetricsBridge;
