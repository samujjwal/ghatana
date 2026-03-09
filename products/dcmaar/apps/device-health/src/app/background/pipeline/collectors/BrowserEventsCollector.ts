/**
 * @fileoverview Browser Events Collector
 *
 * Collects browser events (tabs, navigation, performance) for process pipelines.
 *
 * @module pipeline/collectors
 * @since 2.0.0
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import type { ProcessCollector } from '../ProcessManager';
import type { ProcessExecutionContext, DataSource } from '../../contracts/process';
import browser from 'webextension-polyfill';

/**
 * Browser events collector
 */
export class BrowserEventsCollector implements ProcessCollector {
  /**
   * Collects browser events based on source configuration
   */
  async collect(
    source: DataSource,
    context: ProcessExecutionContext
  ): Promise<Event[]> {
    const events: Event[] = [];
    const { config, filters, samplingRate, batchSize } = source;

    try {
      // Determine which browser events to collect
      const eventTypes = config.eventTypes || ['tabs', 'navigation'];

      for (const eventType of eventTypes) {
        switch (eventType) {
          case 'tabs':
            events.push(...(await this.collectTabEvents(batchSize)));
            break;
          case 'navigation':
            events.push(...(await this.collectNavigationEvents(batchSize)));
            break;
          case 'performance':
            events.push(...(await this.collectPerformanceEvents(batchSize)));
            break;
          default:
            context.logger.warn(`Unknown event type: ${eventType}`);
        }
      }

      // Apply sampling
      const sampledEvents = this.applySampling(events, samplingRate);

      // Apply filters
      const filteredEvents = filters
        ? sampledEvents.filter((event) => this.matchesFilters(event, filters))
        : sampledEvents;

      context.logger.debug(`Collected ${filteredEvents.length} browser events`, {
        source: source.id,
      });

      return filteredEvents;
    } catch (error) {
      context.logger.error('Browser events collection failed', { error });
      throw error;
    }
  }

  /**
   * Collects tab events
   */
  private async collectTabEvents(limit: number): Promise<Event[]> {
    const tabs = await browser.tabs.query({ currentWindow: true });

    return tabs.slice(0, limit).map((tab) => ({
      id: `tab-${tab.id}-${Date.now()}`,
      type: 'browser.tab',
      timestamp: Date.now(),
      payload: {
        id: tab.id,
        url: tab.url,
        title: tab.title,
        active: tab.active,
        status: tab.status,
      },
      metadata: {
        source: 'browser-events-collector',
      },
    }));
  }

  /**
   * Collects navigation events from history
   */
  private async collectNavigationEvents(limit: number): Promise<Event[]> {
    const history = await browser.history.search({
      text: '',
      maxResults: limit,
      startTime: Date.now() - 3600000, // Last hour
    });

    return history.map((item) => ({
      id: `nav-${item.id}-${Date.now()}`,
      type: 'browser.navigation',
      timestamp: item.lastVisitTime || Date.now(),
      payload: {
        url: item.url,
        title: item.title,
        visitCount: item.visitCount,
        lastVisitTime: item.lastVisitTime,
      },
      metadata: {
        source: 'browser-events-collector',
      },
    }));
  }

  /**
   * Collects performance events
   */
  private async collectPerformanceEvents(limit: number): Promise<Event[]> {
    // For browser extension, we collect basic memory usage
    const events: Event[] = [];

    if ((performance as any).memory) {
      const memory = (performance as any).memory;
      events.push({
        id: `perf-${Date.now()}`,
        type: 'browser.performance',
        timestamp: Date.now(),
        payload: {
          usedJSHeapSize: memory.usedJSHeapSize,
          totalJSHeapSize: memory.totalJSHeapSize,
          jsHeapSizeLimit: memory.jsHeapSizeLimit,
        },
        metadata: {
          source: 'browser-events-collector',
        },
      });
    }

    return events.slice(0, limit);
  }

  /**
   * Applies sampling to events
   */
  private applySampling(events: Event[], samplingRate: number): Event[] {
    if (samplingRate >= 1.0) return events;
    return events.filter(() => Math.random() < samplingRate);
  }

  /**
   * Checks if event matches filters
   */
  private matchesFilters(event: Event, filters: any[]): boolean {
    // TODO: Implement proper filter matching using evaluateCondition
    return true;
  }
}
