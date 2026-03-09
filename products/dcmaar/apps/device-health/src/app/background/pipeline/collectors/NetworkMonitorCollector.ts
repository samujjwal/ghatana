/**
 * @fileoverview Network Monitor Collector
 *
 * Monitors network requests, performance, and generates statistics.
 * Tracks request timing, sizes, status codes, and caching.
 *
 * @module pipeline/collectors
 * @since 2.0.0
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import type { ProcessCollector } from '../ProcessManager';
import type { ProcessExecutionContext, DataSource } from '../../contracts/process';
import type { NetworkRequest, NetworkStats } from '../../contracts/oob-analytics';
import browser from 'webextension-polyfill';

/**
 * Network Monitor Collector
 *
 * Collects network request data and statistics
 */
export class NetworkMonitorCollector implements ProcessCollector {
  private requests = new Map<string, NetworkRequest>();
  private requestsByTab = new Map<number, string[]>();
  private stats: NetworkStats = this.createEmptyStats();

  constructor() {
    this.setupListeners();
  }

  async collect(
    source: DataSource,
    context: ProcessExecutionContext
  ): Promise<Event[]> {
    const { config, batchSize } = source;
    const events: Event[] = [];

    try {
      // Collect network request events
      const requestIds = Array.from(this.requests.keys()).slice(0, batchSize);

      for (const requestId of requestIds) {
        const request = this.requests.get(requestId);
        if (!request) continue;

        events.push({
          id: requestId,
          type: 'analytics.network-request',
          timestamp: request.startTime,
          payload: request,
          metadata: {
            source: 'network-monitor-collector',
            tabId: request.tabId,
            requestType: request.type,
          },
        });

        // Remove from pending requests if completed
        if (request.endTime) {
          this.requests.delete(requestId);
        }
      }

      // Include network stats
      if (config.includeStats !== false) {
        events.push({
          id: `network-stats-${Date.now()}`,
          type: 'analytics.network-stats',
          timestamp: Date.now(),
          payload: this.stats,
          metadata: {
            source: 'network-monitor-collector',
          },
        });
      }

      context.logger.debug(`Collected ${events.length} network events`);
      return events;
    } catch (error) {
      context.logger.error('Network monitoring collection failed', { error });
      throw error;
    }
  }

  /**
   * Sets up network listeners
   */
  private setupListeners(): void {
    // Listen for web requests (if permissions available)
    if (browser.webRequest) {
      // Request started
      browser.webRequest.onBeforeRequest.addListener(
        (details) => {
          this.trackRequestStart(details);
        },
        { urls: ['<all_urls>'] }
      );

      // Request completed
      browser.webRequest.onCompleted.addListener(
        (details) => {
          this.trackRequestComplete(details);
        },
        { urls: ['<all_urls>'] }
      );

      // Request failed
      browser.webRequest.onErrorOccurred.addListener(
        (details) => {
          this.trackRequestError(details);
        },
        { urls: ['<all_urls>'] }
      );

      // Response headers (for size info)
      browser.webRequest.onResponseStarted.addListener(
        (details) => {
          this.trackResponseStart(details);
        },
        { urls: ['<all_urls>'] },
        ['responseHeaders']
      );
    }
  }

  /**
   * Tracks request start
   */
  private trackRequestStart(details: any): void {
    const request: NetworkRequest = {
      requestId: details.requestId,
      url: details.url,
      method: details.method as NetworkRequest['method'],
      type: details.type as NetworkRequest['type'],
      startTime: details.timeStamp,
      requestSize: details.requestBody?.raw
        ? details.requestBody.raw.reduce((sum: number, part: any) => sum + (part.bytes?.byteLength || 0), 0)
        : undefined,
      initiator: details.initiator?.url,
      tabId: details.tabId,
      fromCache: details.fromCache ?? false,
    };

    this.requests.set(details.requestId, request);

    // Track by tab
    const tabRequests = this.requestsByTab.get(details.tabId) || [];
    tabRequests.push(details.requestId);
    this.requestsByTab.set(details.tabId, tabRequests);

    // Update stats
    this.stats.totalRequests++;
  }

  /**
   * Tracks request completion
   */
  private trackRequestComplete(details: any): void {
    const request = this.requests.get(details.requestId);
    if (!request) return;

    const endTime = details.timeStamp as number;

    request.statusCode = details.statusCode;
    request.endTime = endTime;
    request.duration = endTime - request.startTime;
    request.fromCache = details.fromCache || false;

    // Update stats
    this.updateStats(request);
  }

  /**
   * Tracks request error
   */
  private trackRequestError(details: any): void {
    const request = this.requests.get(details.requestId);
    if (!request) return;

    const endTime = details.timeStamp as number;
    request.endTime = endTime;
    request.duration = endTime - request.startTime;

    // Update stats
    this.stats.failedRequests++;
    this.updateStats(request);
  }

  /**
   * Tracks response start (for size info)
   */
  private trackResponseStart(details: any): void {
    const request = this.requests.get(details.requestId);
    if (!request) return;

    // Extract content length from headers
    const headers = details.responseHeaders || [];
    const contentLength = headers.find((h: any) => h.name.toLowerCase() === 'content-length');

    if (contentLength) {
      request.responseSize = parseInt(contentLength.value, 10);
    }
  }

  /**
   * Updates network statistics
   */
  private updateStats(request: NetworkRequest): void {
    // Update cached requests
    if (request.fromCache) {
      this.stats.cachedRequests++;
    }

    // Update total bytes
    if (request.responseSize) {
      this.stats.totalBytes += request.responseSize;
    }

    // Update by type
    if (!this.stats.byType[request.type]) {
      this.stats.byType[request.type] = 0;
    }
    this.stats.byType[request.type]++;

    // Update by status code
    if (request.statusCode) {
      const code = request.statusCode.toString();
      if (!this.stats.byStatusCode[code]) {
        this.stats.byStatusCode[code] = 0;
      }
      this.stats.byStatusCode[code]++;
    }

    // Update average duration
    if (request.duration) {
      const totalDuration = this.stats.averageDuration * (this.stats.totalRequests - 1);
      this.stats.averageDuration = (totalDuration + request.duration) / this.stats.totalRequests;
    }
  }

  /**
   * Creates empty stats object
   */
  private createEmptyStats(): NetworkStats {
    return {
      totalRequests: 0,
      failedRequests: 0,
      cachedRequests: 0,
      totalBytes: 0,
      averageDuration: 0,
      byType: {},
      byStatusCode: {},
      timeWindow: {
        start: Date.now(),
        end: Date.now(),
      },
    };
  }

  /**
   * Gets requests for a tab
   */
  getTabRequests(tabId: number): NetworkRequest[] {
    const requestIds = this.requestsByTab.get(tabId) || [];
    return requestIds
      .map((id) => this.requests.get(id))
      .filter((r): r is NetworkRequest => r !== undefined);
  }

  /**
   * Gets network statistics
   */
  getStats(): NetworkStats {
    this.stats.timeWindow.end = Date.now();
    return { ...this.stats };
  }

  /**
   * Resets statistics
   */
  resetStats(): void {
    this.stats = this.createEmptyStats();
  }

  /**
   * Clears requests for a tab
   */
  clearTabRequests(tabId: number): void {
    const requestIds = this.requestsByTab.get(tabId) || [];
    for (const requestId of requestIds) {
      this.requests.delete(requestId);
    }
    this.requestsByTab.delete(tabId);
  }

  /**
   * Cleanup
   */
  destroy(): void {
    this.requests.clear();
    this.requestsByTab.clear();
    this.resetStats();
  }
}
