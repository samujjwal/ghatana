/**
 * Canvas Telemetry System
 *
 * Privacy-respecting analytics for canvas usage patterns.
 * Tracks feature adoption, performance, and user behavior.
 *
 * @doc.type utility
 * @doc.purpose Usage analytics
 * @doc.layer platform
 * @doc.pattern Telemetry
 */

/**
 * Telemetry event types
 */
export enum CanvasTelemetryEvent {
  // Canvas lifecycle
  CANVAS_LOADED = 'canvas.loaded',
  CANVAS_ERROR = 'canvas.error',

  // Frame operations
  FRAME_CREATED = 'frame.created',
  FRAME_DELETED = 'frame.deleted',
  FRAME_MOVED = 'frame.moved',
  FRAME_RESIZED = 'frame.resized',
  FRAME_COLLAPSED = 'frame.collapsed',
  FRAME_EXPANDED = 'frame.expanded',

  // Artifact operations
  ARTIFACT_CREATED = 'artifact.created',
  ARTIFACT_DELETED = 'artifact.deleted',
  ARTIFACT_MOVED = 'artifact.moved',
  ARTIFACT_EDITED = 'artifact.edited',

  // Navigation
  ZOOM_CHANGED = 'zoom.changed',
  VIEWPORT_PANNED = 'viewport.panned',
  MINIMAP_CLICKED = 'minimap.clicked',
  OUTLINE_CLICKED = 'outline.clicked',

  // Chrome interactions
  PANEL_TOGGLED = 'panel.toggled',
  CALM_MODE_TOGGLED = 'calm_mode.toggled',
  CONTEXT_BAR_SHOWN = 'context_bar.shown',
  CONTEXT_BAR_ACTION = 'context_bar.action',

  // Commands
  COMMAND_EXECUTED = 'command.executed',
  KEYBOARD_SHORTCUT_USED = 'keyboard_shortcut.used',

  // Onboarding
  TOUR_STARTED = 'tour.started',
  TOUR_COMPLETED = 'tour.completed',
  TOUR_SKIPPED = 'tour.skipped',
  TOUR_STEP_VIEWED = 'tour.step_viewed',
  HINT_SHOWN = 'hint.shown',
  HINT_DISMISSED = 'hint.dismissed',
  HINT_ACTION_CLICKED = 'hint.action_clicked',

  // Performance
  RENDER_TIME = 'render.time',
  INTERACTION_DELAY = 'interaction.delay',
  MEMORY_USAGE = 'memory.usage',

  // Errors
  COMMAND_ERROR = 'command.error',
  RENDER_ERROR = 'render.error',
}

/**
 * Telemetry event properties
 */
export interface TelemetryEventProperties {
  // Frame properties
  frameId?: string;
  framePhase?: string;
  frameCount?: number;

  // Artifact properties
  artifactId?: string;
  artifactType?: string;
  artifactCount?: number;

  // Navigation properties
  zoomLevel?: number;
  zoomMode?: 'overview' | 'focus' | 'detail';
  viewportX?: number;
  viewportY?: number;

  // Chrome properties
  panel?: string;
  panelVisible?: boolean;
  calmMode?: boolean;

  // Command properties
  commandId?: string;
  commandCategory?: string;
  shortcut?: string;

  // Onboarding properties
  tourStep?: number;
  tourStepId?: string;
  hintId?: string;

  // Performance properties
  duration?: number;
  memory?: number;
  elementCount?: number;

  // Error properties
  error?: string;
  errorStack?: string;

  // Generic properties
  [key: string]: Record<string, string | number | boolean | undefined>;
}

/**
 * Telemetry configuration
 */
export interface TelemetryConfig {
  /** Whether telemetry is enabled */
  enabled: boolean;
  /** Debug mode (log to console) */
  debug: boolean;
  /** Sampling rate (0-1) */
  sampleRate: number;
  /** Endpoint URL */
  endpoint?: string;
  /** User ID (anonymized) */
  userId?: string;
  /** Session ID */
  sessionId: string;
  /** Privacy consent */
  consent: boolean;
}

/**
 * Default telemetry configuration
 */
const DEFAULT_CONFIG: TelemetryConfig = {
  enabled: true,
  debug: process.env.NODE_ENV === 'development',
  sampleRate: 1.0,
  sessionId: generateSessionId(),
  consent: false, // Requires explicit consent
};

/**
 * Generate session ID
 */
function generateSessionId(): string {
  return `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Canvas telemetry tracker
 */
export class CanvasTelemetry {
  private config: TelemetryConfig;
  private eventQueue: Array<{
    event: CanvasTelemetryEvent;
    properties: TelemetryEventProperties;
    timestamp: number;
  }> = [];
  private flushTimer?: NodeJS.Timeout;

  constructor(config: Partial<TelemetryConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };

    // Set up flush timer
    if (this.config.enabled) {
      this.flushTimer = setInterval(() => {
        this.flush();
      }, 10000); // Flush every 10 seconds
    }

    // Track canvas load
    this.track(CanvasTelemetryEvent.CANVAS_LOADED, {
      sessionId: this.config.sessionId,
      timestamp: Date.now(),
    });
  }

  /**
   * Track an event
   */
  track(
    event: CanvasTelemetryEvent,
    properties: TelemetryEventProperties = {}
  ): void {
    // Check consent
    if (!this.config.consent) {
      if (this.config.debug) {
        console.log('[Telemetry] Consent not given, skipping event:', event);
      }
      return;
    }

    // Check if enabled
    if (!this.config.enabled) return;

    // Check sampling rate
    if (Math.random() > this.config.sampleRate) return;

    // Debug logging
    if (this.config.debug) {
      console.log('[Telemetry]', event, properties);
    }

    // Add to queue
    this.eventQueue.push({
      event,
      properties: {
        ...properties,
        sessionId: this.config.sessionId,
        userId: this.config.userId,
        userAgent: navigator.userAgent,
        timestamp: Date.now(),
      },
      timestamp: Date.now(),
    });

    // Flush if queue is large
    if (this.eventQueue.length >= 50) {
      this.flush();
    }
  }

  /**
   * Track timing event
   */
  trackTiming(event: CanvasTelemetryEvent, duration: number): void {
    this.track(event, { duration });
  }

  /**
   * Track error
   */
  trackError(event: CanvasTelemetryEvent, error: Error): void {
    this.track(event, {
      error: error.message,
      errorStack: error.stack,
    });
  }

  /**
   * Flush event queue
   */
  private async flush(): Promise<void> {
    if (this.eventQueue.length === 0) return;

    const events = [...this.eventQueue];
    this.eventQueue = [];

    if (this.config.endpoint) {
      try {
        await fetch(this.config.endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ events }),
        });
      } catch (error) {
        if (this.config.debug) {
          console.error('[Telemetry] Failed to send events:', error);
        }
      }
    }
  }

  /**
   * Update configuration
   */
  setConfig(config: Partial<TelemetryConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Enable telemetry
   */
  enable(): void {
    this.config.enabled = true;
  }

  /**
   * Disable telemetry
   */
  disable(): void {
    this.config.enabled = false;
    this.eventQueue = [];
  }

  /**
   * Set consent
   */
  setConsent(consent: boolean): void {
    this.config.consent = consent;

    // Store consent preference
    localStorage.setItem('canvas-telemetry-consent', consent.toString());

    if (consent) {
      this.track(CanvasTelemetryEvent.CANVAS_LOADED, {
        consentGiven: true,
      });
    }
  }

  /**
   * Clean up
   */
  destroy(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
    }
    this.flush();
  }
}

/**
 * Global telemetry instance
 */
let telemetryInstance: CanvasTelemetry | null = null;

/**
 * Get or create telemetry instance
 */
export function getCanvasTelemetry(
  config?: Partial<TelemetryConfig>
): CanvasTelemetry {
  if (!telemetryInstance) {
    // Check for stored consent
    const storedConsent = localStorage.getItem('canvas-telemetry-consent');
    const consent = storedConsent === 'true';

    telemetryInstance = new CanvasTelemetry({
      ...config,
      consent,
    });
  }
  return telemetryInstance;
}

/**
 * React hook for telemetry
 */
export function useCanvasTelemetry() {
  const telemetry = getCanvasTelemetry();

  return {
    track: (
      event: CanvasTelemetryEvent,
      properties?: TelemetryEventProperties
    ) => telemetry.track(event, properties),
    trackTiming: (event: CanvasTelemetryEvent, duration: number) =>
      telemetry.trackTiming(event, duration),
    trackError: (event: CanvasTelemetryEvent, error: Error) =>
      telemetry.trackError(event, error),
    setConsent: (consent: boolean) => telemetry.setConsent(consent),
  };
}

/**
 * Performance tracking hook
 */
export function usePerformanceTracking(_eventName: string) {
  const { trackTiming } = useCanvasTelemetry();
  const startTime = Date.now();

  return () => {
    const duration = Date.now() - startTime;
    trackTiming(CanvasTelemetryEvent.RENDER_TIME, duration);
  };
}
