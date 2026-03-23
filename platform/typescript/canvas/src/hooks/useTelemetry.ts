/**
 * Advanced Telemetry Hooks
 *
 * Comprehensive analytics and telemetry tracking for canvas interactions.
 * Tracks user actions, performance metrics, and usage patterns.
 *
 * @doc.type hook
 * @doc.purpose Analytics and telemetry tracking
 * @doc.layer application
 */

import { useCallback, useEffect, useRef } from "react";
import { useAtomValue } from "jotai";
import {
  chromeCurrentPhaseAtom,
  chromeActiveRolesAtom,
  chromeSemanticLayerAtom,
  chromeZoomLevelAtom,
} from "../chrome";

// ============================================================================
// TYPES
// ============================================================================

export interface TelemetryEvent {
  eventType: string;
  eventCategory:
    | "interaction"
    | "navigation"
    | "performance"
    | "error"
    | "feature";
  timestamp: number;
  sessionId: string;
  userId?: string;
  metadata?: Record<string, unknown>;
}

export interface PerformanceMetrics {
  renderTime: number;
  interactionLatency: number;
  memoryUsage?: number;
  fps?: number;
}

export interface UserAction {
  action: string;
  target?: string;
  context?: Record<string, unknown>;
}

export interface TelemetryConfig {
  enabled: boolean;
  sampleRate: number;
  batchSize: number;
  flushInterval: number;
  endpoint?: string;
  debug?: boolean;
}

// ============================================================================
// TELEMETRY MANAGER
// ============================================================================

class TelemetryManager {
  private static instance: TelemetryManager;
  private config: TelemetryConfig;
  private eventQueue: TelemetryEvent[] = [];
  private sessionId: string;
  private flushTimer?: NodeJS.Timeout;
  private performanceObserver?: PerformanceObserver;

  private constructor(config: Partial<TelemetryConfig> = {}) {
    this.config = {
      enabled: true,
      sampleRate: 1.0,
      batchSize: 50,
      flushInterval: 30000, // 30 seconds
      debug: false,
      ...config,
    };
    this.sessionId = this.generateSessionId();
    this.initializePerformanceMonitoring();
    this.startFlushTimer();
  }

  static getInstance(config?: Partial<TelemetryConfig>): TelemetryManager {
    if (!TelemetryManager.instance) {
      TelemetryManager.instance = new TelemetryManager(config);
    }
    return TelemetryManager.instance;
  }

  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }

  private initializePerformanceMonitoring(): void {
    if (typeof window === "undefined" || !window.PerformanceObserver) return;

    try {
      this.performanceObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (entry.entryType === "measure") {
            this.trackPerformance({
              renderTime: entry.duration,
              interactionLatency: entry.duration,
            });
          }
        }
      });

      this.performanceObserver.observe({
        entryTypes: ["measure", "navigation"],
      });
    } catch (error) {
      if (this.config.debug) {
        console.warn("Performance monitoring not available:", error);
      }
    }
  }

  private startFlushTimer(): void {
    if (typeof window === "undefined") return;

    this.flushTimer = setInterval(() => {
      this.flush();
    }, this.config.flushInterval);
  }

  private shouldSample(): boolean {
    return Math.random() < this.config.sampleRate;
  }

  track(event: Omit<TelemetryEvent, "timestamp" | "sessionId">): void {
    if (!this.config.enabled || !this.shouldSample()) return;

    const telemetryEvent: TelemetryEvent = {
      ...event,
      timestamp: Date.now(),
      sessionId: this.sessionId,
    };

    this.eventQueue.push(telemetryEvent);

    if (this.config.debug) {
      console.log("[Telemetry]", telemetryEvent);
    }

    if (this.eventQueue.length >= this.config.batchSize) {
      this.flush();
    }
  }

  trackPerformance(metrics: PerformanceMetrics): void {
    this.track({
      eventType: "performance_metric",
      eventCategory: "performance",
      metadata: { ...metrics },
    });
  }

  trackError(error: Error, context?: Record<string, unknown>): void {
    this.track({
      eventType: "error",
      eventCategory: "error",
      metadata: {
        message: error.message,
        stack: error.stack,
        ...context,
      },
    });
  }

  async flush(): Promise<void> {
    if (this.eventQueue.length === 0) return;

    const events = [...this.eventQueue];
    this.eventQueue = [];

    if (this.config.endpoint) {
      try {
        await fetch(this.config.endpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ events }),
        });
      } catch (error) {
        if (this.config.debug) {
          console.error("[Telemetry] Failed to send events:", error);
        }
        // Re-queue events on failure
        this.eventQueue.unshift(...events);
      }
    } else if (this.config.debug) {
      console.log("[Telemetry] Flushing events:", events);
    }
  }

  updateConfig(config: Partial<TelemetryConfig>): void {
    this.config = { ...this.config, ...config };
  }

  destroy(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
    }
    if (this.performanceObserver) {
      this.performanceObserver.disconnect();
    }
    this.flush();
  }
}

// ============================================================================
// HOOKS
// ============================================================================

/**
 * Main telemetry hook for tracking canvas interactions
 *
 * @example
 * ```tsx
 * const { trackAction, trackNavigation, trackFeature } = useTelemetry({
 *   userId: 'user-123',
 *   enabled: true,
 * });
 *
 * // Track user action
 * trackAction('create_node', { nodeType: 'task' });
 *
 * // Track navigation
 * trackNavigation('zoom_to_frame', { frameId: 'frame-1' });
 *
 * // Track feature usage
 * trackFeature('collaboration_cursor', { active: true });
 * ```
 */
export const useTelemetry = (config?: {
  userId?: string;
  enabled?: boolean;
}) => {
  const telemetry = useRef(TelemetryManager.getInstance()).current;
  const currentPhase = useAtomValue(chromeCurrentPhaseAtom);
  const activeRoles = useAtomValue(chromeActiveRolesAtom);
  const semanticLayer = useAtomValue(chromeSemanticLayerAtom);
  const zoomLevel = useAtomValue(chromeZoomLevelAtom);

  useEffect(() => {
    if (config?.enabled !== undefined) {
      telemetry.updateConfig({ enabled: config.enabled });
    }
  }, [config?.enabled, telemetry]);

  const getContextMetadata = useCallback(
    () => ({
      phase: currentPhase,
      roles: activeRoles,
      layer: semanticLayer,
      zoom: zoomLevel,
      userId: config?.userId,
    }),
    [currentPhase, activeRoles, semanticLayer, zoomLevel, config?.userId],
  );

  const trackAction = useCallback(
    (action: string, metadata?: Record<string, unknown>) => {
      telemetry.track({
        eventType: action,
        eventCategory: "interaction",
        metadata: {
          ...getContextMetadata(),
          ...metadata,
        },
      });
    },
    [telemetry, getContextMetadata],
  );

  const trackNavigation = useCallback(
    (destination: string, metadata?: Record<string, unknown>) => {
      telemetry.track({
        eventType: `navigate_${destination}`,
        eventCategory: "navigation",
        metadata: {
          ...getContextMetadata(),
          ...metadata,
        },
      });
    },
    [telemetry, getContextMetadata],
  );

  const trackFeature = useCallback(
    (feature: string, metadata?: Record<string, unknown>) => {
      telemetry.track({
        eventType: `feature_${feature}`,
        eventCategory: "feature",
        metadata: {
          ...getContextMetadata(),
          ...metadata,
        },
      });
    },
    [telemetry, getContextMetadata],
  );

  const trackPerformance = useCallback(
    (metrics: PerformanceMetrics) => {
      telemetry.trackPerformance(metrics);
    },
    [telemetry],
  );

  const trackError = useCallback(
    (error: Error, context?: Record<string, unknown>) => {
      telemetry.trackError(error, {
        ...getContextMetadata(),
        ...context,
      });
    },
    [telemetry, getContextMetadata],
  );

  return {
    trackAction,
    trackNavigation,
    trackFeature,
    trackPerformance,
    trackError,
  };
};

/**
 * Hook for tracking canvas element interactions
 *
 * @example
 * ```tsx
 * const { trackElementCreate, trackElementUpdate, trackElementDelete } = useElementTelemetry();
 *
 * trackElementCreate('task', { priority: 'high' });
 * trackElementUpdate('task-1', { status: 'done' });
 * trackElementDelete('task-1');
 * ```
 */
export const useElementTelemetry = () => {
  const { trackAction } = useTelemetry();

  const trackElementCreate = useCallback(
    (elementType: string, metadata?: Record<string, unknown>) => {
      trackAction("element_create", { elementType, ...metadata });
    },
    [trackAction],
  );

  const trackElementUpdate = useCallback(
    (elementId: string, metadata?: Record<string, unknown>) => {
      trackAction("element_update", { elementId, ...metadata });
    },
    [trackAction],
  );

  const trackElementDelete = useCallback(
    (elementId: string, metadata?: Record<string, unknown>) => {
      trackAction("element_delete", { elementId, ...metadata });
    },
    [trackAction],
  );

  const trackElementSelect = useCallback(
    (elementId: string, metadata?: Record<string, unknown>) => {
      trackAction("element_select", { elementId, ...metadata });
    },
    [trackAction],
  );

  const trackElementMove = useCallback(
    (elementId: string, position: { x: number; y: number }) => {
      trackAction("element_move", { elementId, position });
    },
    [trackAction],
  );

  return {
    trackElementCreate,
    trackElementUpdate,
    trackElementDelete,
    trackElementSelect,
    trackElementMove,
  };
};

/**
 * Hook for tracking panel interactions
 *
 * @example
 * ```tsx
 * const { trackPanelOpen, trackPanelClose, trackPanelAction } = usePanelTelemetry();
 *
 * trackPanelOpen('tasks');
 * trackPanelAction('tasks', 'filter', { status: 'done' });
 * trackPanelClose('tasks');
 * ```
 */
export const usePanelTelemetry = () => {
  const { trackAction } = useTelemetry();

  const trackPanelOpen = useCallback(
    (panelType: string) => {
      trackAction("panel_open", { panelType });
    },
    [trackAction],
  );

  const trackPanelClose = useCallback(
    (panelType: string) => {
      trackAction("panel_close", { panelType });
    },
    [trackAction],
  );

  const trackPanelAction = useCallback(
    (panelType: string, action: string, metadata?: Record<string, unknown>) => {
      trackAction(`panel_${action}`, { panelType, ...metadata });
    },
    [trackAction],
  );

  return {
    trackPanelOpen,
    trackPanelClose,
    trackPanelAction,
  };
};

/**
 * Hook for tracking keyboard shortcuts
 *
 * @example
 * ```tsx
 * const { trackShortcut } = useShortcutTelemetry();
 *
 * trackShortcut('cmd+z', 'undo');
 * ```
 */
export const useShortcutTelemetry = () => {
  const { trackAction } = useTelemetry();

  const trackShortcut = useCallback(
    (shortcut: string, action: string) => {
      trackAction("keyboard_shortcut", { shortcut, action });
    },
    [trackAction],
  );

  return { trackShortcut };
};

/**
 * Hook for tracking drawing tool usage
 *
 * @example
 * ```tsx
 * const { trackToolSelect, trackDrawingStart, trackDrawingEnd } = useDrawingTelemetry();
 *
 * trackToolSelect('pen');
 * trackDrawingStart('pen');
 * trackDrawingEnd('pen', { strokeCount: 150, duration: 2500 });
 * ```
 */
export const useDrawingTelemetry = () => {
  const { trackAction } = useTelemetry();
  const drawingStartTime = useRef<number>(0);

  const trackToolSelect = useCallback(
    (tool: string) => {
      trackAction("tool_select", { tool });
    },
    [trackAction],
  );

  const trackDrawingStart = useCallback(
    (tool: string) => {
      drawingStartTime.current = Date.now();
      trackAction("drawing_start", { tool });
    },
    [trackAction],
  );

  const trackDrawingEnd = useCallback(
    (tool: string, metadata?: Record<string, unknown>) => {
      const duration = Date.now() - drawingStartTime.current;
      trackAction("drawing_end", { tool, duration, ...metadata });
    },
    [trackAction],
  );

  return {
    trackToolSelect,
    trackDrawingStart,
    trackDrawingEnd,
  };
};

/**
 * Initialize telemetry system
 *
 * @example
 * ```tsx
 * initializeTelemetry({
 *   enabled: true,
 *   endpoint: 'https://api.example.com/telemetry',
 *   sampleRate: 0.1, // 10% sampling
 *   debug: process.env.NODE_ENV === 'development',
 * });
 * ```
 */
export const initializeTelemetry = (config: Partial<TelemetryConfig>): void => {
  TelemetryManager.getInstance(config);
};

/**
 * Cleanup telemetry system
 */
export const destroyTelemetry = (): void => {
  const instance = TelemetryManager.getInstance();
  instance.destroy();
};
