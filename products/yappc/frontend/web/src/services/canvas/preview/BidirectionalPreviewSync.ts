/**
 * Bidirectional Preview Synchronization
 *
 * Handles bidirectional preview selection with:
 * - Click/hover sync between canvas and preview
 * - Interaction telemetry
 * - Element highlighting
 *
 * @doc.type service
 * @doc.purpose Bidirectional preview synchronization
 * @doc.layer product
 */

export interface PreviewSyncEvent {
  /** Event type */
  type: 'click' | 'hover' | 'focus' | 'blur';
  /** Element ID in canvas */
  canvasElementId: string;
  /** Element ID in preview */
  previewElementId?: string;
  /** Timestamp */
  timestamp: string;
  /** User ID */
  userId?: string;
}

export interface PreviewSyncState {
  /** Currently selected canvas element */
  selectedCanvasElementId: string | null;
  /** Currently hovered canvas element */
  hoveredCanvasElementId: string | null;
  /** Currently selected preview element */
  selectedPreviewElementId: string | null;
  /** Currently hovered preview element */
  hoveredPreviewElementId: string | null;
  /** Sync enabled flag */
  syncEnabled: boolean;
  /** Event history */
  eventHistory: PreviewSyncEvent[];
}

export interface PreviewTelemetryData {
  /** Event type */
  eventType: string;
  /** Canvas element ID */
  canvasElementId?: string;
  /** Preview element ID */
  previewElementId?: string;
  /** Timestamp */
  timestamp: string;
  /** Duration */
  duration?: number;
  /** User ID */
  userId?: string;
  /** Project ID */
  projectId?: string;
}

/**
 * Bidirectional preview sync service
 */
export class BidirectionalPreviewSync {
  private state: PreviewSyncState;
  private telemetryCallbacks: ((data: PreviewTelemetryData) => void)[] = [];
  private eventHistoryLimit = 100;

  constructor() {
    this.state = {
      selectedCanvasElementId: null,
      hoveredCanvasElementId: null,
      selectedPreviewElementId: null,
      hoveredPreviewElementId: null,
      syncEnabled: true,
      eventHistory: [],
    };
  }

  /**
   * Handle canvas element click
   */
  handleCanvasClick(canvasElementId: string, previewElementId?: string, userId?: string): void {
    if (!this.state.syncEnabled) return;

    this.state.selectedCanvasElementId = canvasElementId;
    this.state.selectedPreviewElementId = previewElementId || null;

    const event: PreviewSyncEvent = {
      type: 'click',
      canvasElementId,
      previewElementId,
      timestamp: new Date().toISOString(),
      userId,
    };

    this.recordEvent(event);
    this.emitTelemetry({
      eventType: 'canvas_click',
      canvasElementId,
      previewElementId,
      timestamp: event.timestamp,
      userId,
    });
  }

  /**
   * Handle canvas element hover
   */
  handleCanvasHover(canvasElementId: string, previewElementId?: string, userId?: string): void {
    if (!this.state.syncEnabled) return;

    this.state.hoveredCanvasElementId = canvasElementId;
    this.state.hoveredPreviewElementId = previewElementId || null;

    const event: PreviewSyncEvent = {
      type: 'hover',
      canvasElementId,
      previewElementId,
      timestamp: new Date().toISOString(),
      userId,
    };

    this.recordEvent(event);
    this.emitTelemetry({
      eventType: 'canvas_hover',
      canvasElementId,
      previewElementId,
      timestamp: event.timestamp,
      userId,
    });
  }

  /**
   * Handle preview element click
   */
  handlePreviewClick(previewElementId: string, canvasElementId?: string, userId?: string): void {
    if (!this.state.syncEnabled) return;

    this.state.selectedPreviewElementId = previewElementId;
    this.state.selectedCanvasElementId = canvasElementId || null;

    const event: PreviewSyncEvent = {
      type: 'click',
      canvasElementId: canvasElementId || '',
      previewElementId,
      timestamp: new Date().toISOString(),
      userId,
    };

    this.recordEvent(event);
    this.emitTelemetry({
      eventType: 'preview_click',
      canvasElementId,
      previewElementId,
      timestamp: event.timestamp,
      userId,
    });
  }

  /**
   * Handle preview element hover
   */
  handlePreviewHover(previewElementId: string, canvasElementId?: string, userId?: string): void {
    if (!this.state.syncEnabled) return;

    this.state.hoveredPreviewElementId = previewElementId;
    this.state.hoveredCanvasElementId = canvasElementId || null;

    const event: PreviewSyncEvent = {
      type: 'hover',
      canvasElementId: canvasElementId || '',
      previewElementId,
      timestamp: new Date().toISOString(),
      userId,
    };

    this.recordEvent(event);
    this.emitTelemetry({
      eventType: 'preview_hover',
      canvasElementId,
      previewElementId,
      timestamp: event.timestamp,
      userId,
    });
  }

  /**
   * Clear selection
   */
  clearSelection(): void {
    this.state.selectedCanvasElementId = null;
    this.state.selectedPreviewElementId = null;
  }

  /**
   * Clear hover state
   */
  clearHover(): void {
    this.state.hoveredCanvasElementId = null;
    this.state.hoveredPreviewElementId = null;
  }

  /**
   * Enable sync
   */
  enableSync(): void {
    this.state.syncEnabled = true;
  }

  /**
   * Disable sync
   */
  disableSync(): void {
    this.state.syncEnabled = false;
  }

  /**
   * Get current state
   */
  getState(): PreviewSyncState {
    return { ...this.state };
  }

  /**
   * Get event history
   */
  getEventHistory(): PreviewSyncEvent[] {
    return [...this.state.eventHistory];
  }

  /**
   * Register telemetry callback
   */
  onTelemetry(callback: (data: PreviewTelemetryData) => void): void {
    this.telemetryCallbacks.push(callback);
  }

  /**
   * Record event
   */
  private recordEvent(event: PreviewSyncEvent): void {
    this.state.eventHistory.push(event);
    
    // Limit history size
    if (this.state.eventHistory.length > this.eventHistoryLimit) {
      this.state.eventHistory.shift();
    }
  }

  /**
   * Emit telemetry
   */
  private emitTelemetry(data: PreviewTelemetryData): void {
    this.telemetryCallbacks.forEach(callback => {
      try {
        callback(data);
      } catch (error) {
        console.error('Telemetry callback error:', error);
      }
    });
  }

  /**
   * Clear event history
   */
  clearHistory(): void {
    this.state.eventHistory = [];
  }

  /**
   * Reset state
   */
  reset(): void {
    this.state = {
      selectedCanvasElementId: null,
      hoveredCanvasElementId: null,
      selectedPreviewElementId: null,
      hoveredPreviewElementId: null,
      syncEnabled: true,
      eventHistory: [],
    };
  }
}

/**
 * Create a singleton instance
 */
let previewSyncInstance: BidirectionalPreviewSync | null = null;

export function getPreviewSync(): BidirectionalPreviewSync {
  if (!previewSyncInstance) {
    previewSyncInstance = new BidirectionalPreviewSync();
  }
  return previewSyncInstance;
}

export default BidirectionalPreviewSync;
