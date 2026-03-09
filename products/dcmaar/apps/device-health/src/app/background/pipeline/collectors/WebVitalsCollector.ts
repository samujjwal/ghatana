/**
 * @fileoverview Web Vitals Collector
 *
 * Collects Core Web Vitals (LCP, INP, CLS) and additional performance metrics
 * using the web-vitals library pattern.
 *
 * @module pipeline/collectors
 * @since 2.0.0
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import type { ProcessCollector } from '../ProcessManager';
import type { ProcessExecutionContext, DataSource } from '../../contracts/process';
import type { WebVitalsSnapshot, LCPMetric, INPMetric, CLSMetric } from '../../contracts/oob-analytics';
import { calculateWebVitalsRating } from '../../contracts/oob-analytics';
import browser from 'webextension-polyfill';

/**
 * Web Vitals collector
 *
 * Collects Core Web Vitals from active tabs using content script injection
 */
export class WebVitalsCollector implements ProcessCollector {
  private vitalsCache = new Map<number, WebVitalsSnapshot>();

  async collect(
    source: DataSource,
    context: ProcessExecutionContext
  ): Promise<Event[]> {
    const { config, batchSize } = source;
    const events: Event[] = [];

    try {
      // Get active tabs
      const tabs = await browser.tabs.query({
        active: true,
        currentWindow: true,
      });

      for (const tab of tabs.slice(0, batchSize)) {
        if (!tab.id || !tab.url) continue;

        // Skip non-http(s) URLs
        if (!tab.url.startsWith('http')) {
          continue;
        }

        try {
          // Collect vitals from tab
          const vitals = await this.collectFromTab(tab.id, tab.url, tab.title || '');

          if (vitals) {
            this.vitalsCache.set(tab.id, vitals);

            events.push({
              id: `web-vitals-${tab.id}-${Date.now()}`,
              type: 'analytics.web-vitals',
              timestamp: vitals.timestamp,
              payload: vitals,
              metadata: {
                source: 'web-vitals-collector',
                tabId: tab.id,
                url: tab.url,
              },
            });
          }
        } catch (error) {
          context.logger.warn(`Failed to collect vitals from tab ${tab.id}`, { error });
        }
      }

      context.logger.debug(`Collected ${events.length} Web Vitals snapshots`);
      return events;
    } catch (error) {
      context.logger.error('Web Vitals collection failed', { error });
      throw error;
    }
  }

  /**
   * Collects Web Vitals from a specific tab
   */
  private async collectFromTab(
    tabId: number,
    url: string,
    title: string
  ): Promise<WebVitalsSnapshot | null> {
    try {
      // Execute script in tab to collect vitals
      const results = await browser.tabs.executeScript(tabId, {
        code: `(${this.getVitalsCollectionScript.toString()})()`,
      });

      if (!results || results.length === 0 || !results[0]) {
        return null;
      }

      const vitalsData = results[0] as any;

      // Calculate overall rating
      const overallRating = calculateWebVitalsRating(
        vitalsData.lcp?.value,
        vitalsData.inp?.value,
        vitalsData.cls?.value
      );

      const snapshot: WebVitalsSnapshot = {
        url,
        title,
        lcp: vitalsData.lcp,
        inp: vitalsData.inp,
        cls: vitalsData.cls,
        additional: {
          ttfb: vitalsData.ttfb,
          fcp: vitalsData.fcp,
          tti: vitalsData.tti,
          tbt: vitalsData.tbt,
        },
        overallRating,
        timestamp: Date.now(),
        tabId,
      };

      return snapshot;
    } catch (error) {
      console.warn('Failed to collect vitals:', error);
      return null;
    }
  }

  /**
   * Script to collect Web Vitals in page context
   * This is injected into the tab
   */
  private getVitalsCollectionScript() {
    // This function runs in the page context
    const vitalsData: any = {
      lcp: null,
      inp: null,
      cls: null,
      ttfb: null,
      fcp: null,
      tti: null,
      tbt: null,
    };

    try {
      // Collect from Performance API
      const perfEntries = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;

      if (perfEntries) {
        // TTFB
        vitalsData.ttfb = perfEntries.responseStart - perfEntries.requestStart;
      }

      // FCP - First Contentful Paint
      const fcpEntry = performance.getEntriesByName('first-contentful-paint')[0] as PerformancePaintTiming;
      if (fcpEntry) {
        vitalsData.fcp = fcpEntry.startTime;
      }

      // LCP - Largest Contentful Paint
      const observer = new PerformanceObserver((list) => {
        const entries = list.getEntries() as PerformanceEntry[];
        const lastEntry = entries[entries.length - 1];

        if (lastEntry && 'renderTime' in lastEntry) {
          const lcpValue = (lastEntry as any).renderTime || (lastEntry as any).loadTime;

          vitalsData.lcp = {
            value: lcpValue,
            element: (lastEntry as any).element?.tagName || 'unknown',
            rating: lcpValue <= 2500 ? 'good' : lcpValue <= 4000 ? 'needs-improvement' : 'poor',
            timestamp: Date.now(),
          };
        }
      });

      try {
        observer.observe({ type: 'largest-contentful-paint', buffered: true });
      } catch (e) {
        // LCP not supported
      }

      // CLS - Cumulative Layout Shift
      let clsValue = 0;
      let clsCount = 0;

      const clsObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries() as PerformanceEntry[]) {
          if (!(entry as any).hadRecentInput) {
            clsValue += (entry as any).value;
            clsCount++;
          }
        }

        vitalsData.cls = {
          value: clsValue,
          shiftCount: clsCount,
          rating: clsValue <= 0.1 ? 'good' : clsValue <= 0.25 ? 'needs-improvement' : 'poor',
          timestamp: Date.now(),
        };
      });

      try {
        clsObserver.observe({ type: 'layout-shift', buffered: true });
      } catch (e) {
        // CLS not supported
      }

      // INP - Interaction to Next Paint (estimated from event timing)
      const inpObserver = new PerformanceObserver((list) => {
        const entries = list.getEntries() as PerformanceEntry[];
        let maxDuration = 0;
        let maxEntry: any = null;

        for (const entry of entries) {
          const duration = (entry as any).processingEnd - (entry as any).startTime;
          if (duration > maxDuration) {
            maxDuration = duration;
            maxEntry = entry;
          }
        }

        if (maxEntry) {
          vitalsData.inp = {
            value: maxDuration,
            interactionType: (maxEntry as any).name as 'click' | 'tap' | 'keyboard',
            rating: maxDuration <= 200 ? 'good' : maxDuration <= 500 ? 'needs-improvement' : 'poor',
            timestamp: Date.now(),
          };
        }
      });

      try {
        inpObserver.observe({ type: 'event', buffered: true });
      } catch (e) {
        // Event timing not supported
      }

      // Wait a bit for observers to collect data
      setTimeout(() => {
        observer.disconnect();
        clsObserver.disconnect();
        inpObserver.disconnect();
      }, 100);

    } catch (error) {
      console.error('Web Vitals collection error:', error);
    }

    return vitalsData;
  }

  /**
   * Gets cached vitals for a tab
   */
  getCachedVitals(tabId: number): WebVitalsSnapshot | undefined {
    return this.vitalsCache.get(tabId);
  }

  /**
   * Clears vitals cache
   */
  clearCache(tabId?: number): void {
    if (tabId) {
      this.vitalsCache.delete(tabId);
    } else {
      this.vitalsCache.clear();
    }
  }
}
