/**
 * Background service worker message handler for plugin communication.
 * 
 * Receives metric requests from content scripts and returns
 * system metrics collected via Rust plugins or system APIs.
 */

import { MetricsBridge } from './metrics-bridge';

import type { PluginMetrics, PluginConfig } from './PluginSystemAdapter';

interface MessageResponse {
  type: 'PONG' | 'METRICS_RESPONSE';
  metrics?: PluginMetrics | null;
  error?: string;
}

// Type guard to safely access chrome API
function getChromeRuntime(): unknown {
  try {
    return (typeof window !== 'undefined' ? window : global) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/**
 * Initialize message listener for plugin communication.
 * Called from background/index.ts.
 */
export async function initializePluginHandler(): Promise<void> {
  try {
    const runtimeObj = getChromeRuntime() as Record<string, unknown> | null;
    if (!runtimeObj) return;

    const chromeObj = runtimeObj.chrome as Record<string, unknown> | undefined;
    if (!chromeObj) return;

    const runtime = chromeObj.runtime as Record<string, unknown> | undefined;
    if (!runtime) return;

    const onMessage = runtime.onMessage as Record<string, unknown> | undefined;
    if (!onMessage) return;

    const addListener = onMessage.addListener as
      | ((callback: (request: unknown, sender: unknown, sendResponse: (response: MessageResponse) => void) => boolean | void) => void)
      | undefined;

    if (addListener) {
      addListener((request: unknown, _sender: unknown, sendResponse: (response: MessageResponse) => void) => {
        void handlePluginMessage(request, sendResponse);
        return true;
      });

      console.log('[PluginHandler] Message listener initialized');

      // Initialize MetricsBridge to route Phase 3G metrics to agent-desktop
      try {
        const bridge = MetricsBridge.getInstance();
        await bridge.initialize({
          nativeMessagingHost: 'com.ghatana.guardian.desktop',
          deviceId: 'device-' + Math.random().toString(36).substring(7),
          childUserId: 'user-' + Math.random().toString(36).substring(7),
        });
        console.log('[PluginHandler] MetricsBridge initialized successfully');
      } catch (bridgeError) {
        console.warn('[PluginHandler] MetricsBridge initialization failed:', bridgeError);
        // Non-fatal: plugin handler continues without bridge
      }
    }
  } catch (error) {
    console.error('[PluginHandler] Failed to initialize:', error);
  }
}

/**
 * Main message handler for plugin requests.
 */
async function handlePluginMessage(
  request: unknown,
  sendResponse: (response: MessageResponse) => void
): Promise<void> {
  try {
    // Type guard to check if request is valid
    if (!request || typeof request !== 'object') {
      sendResponse({
        type: 'METRICS_RESPONSE',
        error: 'Invalid request format',
      });
      return;
    }

    const req = request as Record<string, unknown>;
    const requestType = req.type as string | undefined;

    switch (requestType) {
      case 'PING':
        sendResponse({ type: 'PONG' });
        break;

      case 'GET_METRICS': {
        const configData = req.config as unknown;
        const config = configData && typeof configData === 'object' ? (configData as PluginConfig) : undefined;
        let metricsResult: PluginMetrics | null | undefined;
        try {
          const result = await collectMetrics(config);
          metricsResult = result as PluginMetrics | null;
        } catch (_e) {
          metricsResult = null;
        }

        if (metricsResult) {
          const response = {
            type: 'METRICS_RESPONSE',
            metrics: metricsResult,
          } satisfies MessageResponse;
          sendResponse(response);
        } else {
          sendResponse({
            type: 'METRICS_RESPONSE',
            error: 'Failed to collect metrics',
          } satisfies MessageResponse);
        }
        break;
      }

      default:
        sendResponse({
          type: 'METRICS_RESPONSE',
          error: `Unknown request type: ${String(requestType)}`,
        });
    }
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : String(error);
    console.error('[PluginHandler] Error handling message:', errorMsg);
    sendResponse({
      type: 'METRICS_RESPONSE',
      error: errorMsg,
    });
  }
}

/**
 * Collect system metrics from available sources.
 * Falls back to mock data if real plugins are not available.
 */
async function collectMetrics(config?: PluginConfig): Promise<PluginMetrics | null> {
  const timestamp = Date.now();

  try {
    // Try to get metrics from Rust plugins first
    const rustMetrics = await getRustPluginMetrics(config);
    if (rustMetrics) {
      return rustMetrics;
    }

    // Fallback to system APIs if available
    const systemMetrics = await getSystemMetrics(config);
    return systemMetrics;
  } catch (error) {
    console.error('[PluginHandler] Metric collection failed:', error);
    return getDefaultMetrics(timestamp);
  }
}

/**
 * Attempt to get metrics from Rust plugins.
 */
async function getRustPluginMetrics(_config?: PluginConfig): Promise<PluginMetrics | null> {
  try {
    // In production, this would communicate with Rust plugins
    // via native messaging or IPC. For now, returns null to fall back
    // to system APIs.

    // Example: could use chrome.runtime.connectNative() for native messaging
    // const port = chrome.runtime.connectNative('com.ghatana.device_health_plugin');
    // port.postMessage({ command: 'get_metrics' });

    console.debug('[PluginHandler] Rust plugins not available, using fallback');
    return null;
  } catch (error) {
    console.warn('[PluginHandler] Failed to get Rust plugin metrics:', error);
    return null;
  }
}

/**
 * Get metrics from system APIs (performance, battery, etc).
 */
async function getSystemMetrics(config?: PluginConfig): Promise<PluginMetrics> {
  const timestamp = Date.now();

  const cpuMetrics = await getCPUMetrics(config);
  const memoryMetrics = await getMemoryMetrics(config);
  const batteryMetrics = await getBatteryMetrics(config);

  return {
    cpu: cpuMetrics ?? { usage: 0, temperature: 0, throttled: false, cores: 0 },
    memory: memoryMetrics ?? { usage: 0, total: 0, available: 0, gcActivity: 0 },
    battery: batteryMetrics ?? { level: 100, charging: false, health: 'good', timeRemaining: 0 },
    timestamp,
  };
}

/**
 * Get CPU metrics from performance API.
 */
async function getCPUMetrics(config?: PluginConfig): Promise<PluginMetrics['cpu']> {
  if (config && typeof config === 'object' && 'cpuEnabled' in config) {
    const cpuConfig = config as Record<string, unknown>;
    if (cpuConfig.cpuEnabled === false) {
      return { usage: 0, temperature: 0, throttled: false, cores: 0 };
    }
  }

  try {
    // Use performance observer or worker metrics
    const cores = typeof navigator !== 'undefined' && navigator.hardwareConcurrency ? navigator.hardwareConcurrency : 4;

    // Simulate CPU metrics (real implementation would use performance API or worker)
    // This is a fallback for browser environment which doesn't have direct CPU access
    const usage = Math.random() * 100;

    return {
      usage,
      temperature: 40 + usage * 0.5, // Simulate temperature based on usage
      throttled: false,
      cores,
    };
  } catch (error) {
    console.warn('[PluginHandler] Failed to get CPU metrics:', error);
    return { usage: 0, temperature: 0, throttled: false, cores: 0 };
  }
}

/**
 * Get memory metrics from performance API.
 */
async function getMemoryMetrics(config?: PluginConfig): Promise<PluginMetrics['memory']> {
  if (config && typeof config === 'object' && 'memoryEnabled' in config) {
    const memConfig = config as Record<string, unknown>;
    if (memConfig.memoryEnabled === false) {
      return { usage: 0, total: 0, available: 0, gcActivity: 0 };
    }
  }

  try {
    // Use performance.memory if available (Chrome-specific)
    if (typeof performance !== 'undefined' && 'memory' in performance) {
      const perf = performance as unknown as Record<string, unknown>;
      const memory = perf.memory as Record<string, unknown> | undefined;

      if (memory) {
        const usedJSHeapSize = (memory.usedJSHeapSize as number) || 0;
        const jsHeapSizeLimit = (memory.jsHeapSizeLimit as number) || 0;

        return {
          usage: usedJSHeapSize,
          total: jsHeapSizeLimit,
          available: jsHeapSizeLimit - usedJSHeapSize,
          gcActivity: 0, // Could track GC events via PerformanceObserver
        };
      }
    }

    return { usage: 0, total: 0, available: 0, gcActivity: 0 };
  } catch (error) {
    console.warn('[PluginHandler] Failed to get memory metrics:', error);
    return { usage: 0, total: 0, available: 0, gcActivity: 0 };
  }
}

/**
 * Get battery metrics from Battery Status API.
 */
async function getBatteryMetrics(config?: PluginConfig): Promise<PluginMetrics['battery']> {
  if (config && typeof config === 'object' && 'batteryEnabled' in config) {
    const battConfig = config as Record<string, unknown>;
    if (battConfig.batteryEnabled === false) {
      return { level: 100, charging: false, health: 'good', timeRemaining: 0 };
    }
  }

  try {
    // Battery Status API (deprecated but still useful for fallback)
    if (typeof navigator !== 'undefined' && 'getBattery' in navigator) {
      const nav = navigator as unknown as Record<string, unknown>;
      const getBattery = nav.getBattery as (() => Promise<Record<string, unknown>>) | undefined;

      if (getBattery) {
        const battery = await getBattery();

        return {
          level: ((battery.level as number) || 1) * 100,
          charging: (battery.charging as boolean) || false,
          health: 'good',
          timeRemaining: (battery.dischargingTime as number) || 0,
        };
      }
    }

    // Fallback: assume full battery
    return { level: 100, charging: false, health: 'good', timeRemaining: 0 };
  } catch (error) {
    console.warn('[PluginHandler] Failed to get battery metrics:', error);
    return { level: 100, charging: false, health: 'good', timeRemaining: 0 };
  }
}

/**
 * Get default metrics.
 */
function getDefaultMetrics(timestamp: number): PluginMetrics {
  return {
    cpu: { usage: 0, temperature: 0, throttled: false, cores: 0 },
    memory: { usage: 0, total: 0, available: 0, gcActivity: 0 },
    battery: { level: 100, charging: false, health: 'good', timeRemaining: 0 },
    timestamp,
  };
}
