/**
 * @fileoverview Environment Context Collector
 *
 * Collects device, network, viewport, and geo context information
 * for segmentation and analytics.
 *
 * @module browser/context/EnvironmentCollector
 */

export interface DeviceContext {
  userAgent: string;
  platform?: string;
  language?: string;
  deviceMemory?: number;
}

export interface NetworkContext {
  effectiveType?: string;
  downlink?: number;
  rtt?: number;
  saveData?: boolean;
  type?: string;
}

export interface ViewportContext {
  width: number;
  height: number;
  devicePixelRatio: number;
}

export interface GeoContext {
  continent?: string;
  country?: string;
  timezone?: string;
}

type NetworkInfoLike = {
  effectiveType?: string;
  downlink?: number;
  rtt?: number;
  saveData?: boolean;
  type?: string;
  addEventListener?: (type: string, listener: () => void) => void;
  removeEventListener?: (type: string, listener: () => void) => void;
  onchange?: () => void;
};

export type ContextEvent =
  | { type: 'network'; context: NetworkContext; timestamp: number }
  | { type: 'viewport'; context: ViewportContext; timestamp: number };

type ContextListener = (event: ContextEvent) => void;

export class EnvironmentCollector {
  private listeners = new Set<ContextListener>();
  private networkCleanup?: () => void;
  private viewportCleanup?: () => void;

  /**
   * Collect device context synchronously
   */
  async collectDeviceContext(): Promise<DeviceContext> {
    if (typeof navigator === 'undefined') {
      return { userAgent: 'unknown' };
    }

    const nav = navigator as Navigator & { deviceMemory?: number };
    return {
      userAgent: nav.userAgent,
      platform: nav.platform,
      language: nav.language,
      deviceMemory: nav.deviceMemory,
    };
  }

  /**
   * Collect network context
   */
  async collectNetworkContext(): Promise<NetworkContext> {
    if (typeof navigator === 'undefined') {
      return {};
    }

    const connection = (navigator as Navigator & {
      connection?: NetworkInfoLike & Record<string, unknown>;
    }).connection;

    if (!connection) {
      return {};
    }

    return {
      effectiveType: (connection as { effectiveType?: string }).effectiveType,
      downlink: (connection as { downlink?: number }).downlink,
      rtt: (connection as { rtt?: number }).rtt,
      saveData: (connection as { saveData?: boolean }).saveData,
      type: (connection as { type?: string }).type,
    };
  }

  /**
   * Collect viewport context
   */
  async collectViewportContext(): Promise<ViewportContext> {
    if (typeof window === 'undefined') {
      return {
        width: 0,
        height: 0,
        devicePixelRatio: 1,
      };
    }

    return {
      width: window.innerWidth,
      height: window.innerHeight,
      devicePixelRatio: window.devicePixelRatio ?? 1,
    };
  }

  /**
   * Collect geo context (privacy-safe)
   */
  async collectGeoContext(): Promise<GeoContext> {
    const timezone =
      typeof Intl !== 'undefined' ? Intl.DateTimeFormat().resolvedOptions().timeZone : undefined;
    const locale = typeof navigator !== 'undefined' ? navigator.language : undefined;

    let country: string | undefined;
    if (locale && locale.includes('-')) {
      country = locale.split('-')[1]?.toUpperCase();
    }

    return {
      timezone,
      continent: undefined,
      country,
    };
  }

  /**
   * Start monitoring network changes
   */
  startNetworkMonitoring(): void {
    if (typeof navigator === 'undefined') {
      return;
    }

    const connection = (navigator as Navigator & {
      connection?: NetworkInfoLike;
    }).connection;

    if (!connection) {
      return;
    }

    const emit = () => {
      void this.collectNetworkContext().then((context) => {
        this.emitContextChange({
          type: 'network',
          context,
          timestamp: Date.now(),
        });
      });
    };

    if (typeof connection.addEventListener === 'function') {
      connection.addEventListener('change', emit);
      this.networkCleanup = () => {
        connection.removeEventListener?.('change', emit);
      };
    } else if (typeof connection.onchange === 'function') {
      const original = connection.onchange;
      connection.onchange = () => {
        original?.();
        emit();
      };
      this.networkCleanup = () => {
        connection.onchange = original;
      };
    } else {
      emit();
    }
  }

  /**
   * Start monitoring viewport changes
   */
  startViewportMonitoring(): void {
    if (typeof window === 'undefined') {
      return;
    }

    const onResize = () => {
      void this.collectViewportContext().then((context) => {
        this.emitContextChange({
          type: 'viewport',
          context,
          timestamp: Date.now(),
        });
      });
    };

    window.addEventListener('resize', onResize);
    this.viewportCleanup = () => window.removeEventListener('resize', onResize);
  }

  /**
   * Subscribe to context changes
   */
  onContextChange(listener: ContextListener): void {
    this.listeners.add(listener);
  }

  /**
   * Unsubscribe from context changes
   */
  offContextChange(listener: ContextListener): void {
    this.listeners.delete(listener);
  }

  /**
   * Cleanup listeners
   */
  dispose(): void {
    this.networkCleanup?.();
    this.viewportCleanup?.();
    this.networkCleanup = undefined;
    this.viewportCleanup = undefined;
    this.listeners.clear();
  }

  private emitContextChange(event: ContextEvent): void {
    this.listeners.forEach((listener) => listener(event));
  }
}
