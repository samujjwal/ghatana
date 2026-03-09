/**
 * Mock Data Generators for Testing
 * Use these when agent or extension is not available
 */

import type { Metric, Event } from '../stores/agentStore';
import type { ExtensionEvent } from '../stores/extensionStore';

/**
 * Generate mock metrics for testing
 */
export class MockMetricsGenerator {
  private baseValues = {
    cpu_usage: 45,
    memory_usage: 60,
    disk_usage: 30,
  };

  /**
   * Generate a single metric
   */
  generateMetric(name: keyof typeof this.baseValues): Metric {
    const base = this.baseValues[name];
    const variance = Math.random() * 20 - 10; // ±10%
    const value = Math.max(0, Math.min(100, base + variance));

    return {
      name,
      value: parseFloat(value.toFixed(2)),
      timestamp: Date.now(),
      labels: {
        source: 'mock',
        host: 'localhost',
      },
    };
  }

  /**
   * Generate multiple metrics
   */
  generateMetrics(count: number = 10): Metric[] {
    const metrics: Metric[] = [];
    const names = Object.keys(this.baseValues) as Array<keyof typeof this.baseValues>;

    for (let i = 0; i < count; i++) {
      const name = names[i % names.length];
      metrics.push(this.generateMetric(name));
    }

    return metrics;
  }

  /**
   * Generate time-series metrics
   */
  generateTimeSeries(
    name: keyof typeof this.baseValues,
    points: number = 50,
    intervalMs: number = 1000
  ): Metric[] {
    const metrics: Metric[] = [];
    const now = Date.now();

    for (let i = 0; i < points; i++) {
      const timestamp = now - (points - i) * intervalMs;
      const base = this.baseValues[name];
      const trend = Math.sin((i / points) * Math.PI * 2) * 15; // Sine wave
      const noise = Math.random() * 10 - 5; // Random noise
      const value = Math.max(0, Math.min(100, base + trend + noise));

      metrics.push({
        name,
        value: parseFloat(value.toFixed(2)),
        timestamp,
        labels: {
          source: 'mock',
          host: 'localhost',
        },
      });
    }

    return metrics;
  }

  /**
   * Start streaming metrics (for real-time testing)
   */
  startStreaming(callback: (metric: Metric) => void, intervalMs: number = 2000): () => void {
    const names = Object.keys(this.baseValues) as Array<keyof typeof this.baseValues>;
    let index = 0;

    const interval = setInterval(() => {
      const name = names[index % names.length];
      callback(this.generateMetric(name));
      index++;
    }, intervalMs);

    return () => clearInterval(interval);
  }
}

/**
 * Generate mock events for testing
 */
export class MockEventsGenerator {
  private eventTypes = ['system_alert', 'performance_warning', 'security_event', 'info'];
  private severities: Array<'info' | 'warning' | 'error'> = ['info', 'warning', 'error'];
  private messages = [
    'High CPU usage detected',
    'Memory usage above threshold',
    'Disk space running low',
    'Network latency increased',
    'Service health check failed',
    'Configuration updated',
    'User login detected',
    'Backup completed successfully',
  ];

  /**
   * Generate a single event
   */
  generateEvent(): Event {
    const eventType = this.eventTypes[Math.floor(Math.random() * this.eventTypes.length)];
    const severity = this.severities[Math.floor(Math.random() * this.severities.length)];
    const message = this.messages[Math.floor(Math.random() * this.messages.length)];

    return {
      id: `evt_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type: eventType,
      severity,
      message,
      timestamp: Date.now(),
    };
  }

  /**
   * Generate multiple events
   */
  generateEvents(count: number = 10): Event[] {
    return Array.from({ length: count }, () => this.generateEvent());
  }

  /**
   * Start streaming events
   */
  startStreaming(callback: (event: Event) => void, intervalMs: number = 5000): () => void {
    const interval = setInterval(() => {
      callback(this.generateEvent());
    }, intervalMs);

    return () => clearInterval(interval);
  }
}

/**
 * Generate mock extension events for testing
 */
export class MockExtensionEventsGenerator {
  private urls = [
    'https://example.com',
    'https://github.com',
    'https://stackoverflow.com',
    'https://reddit.com',
    'https://twitter.com',
  ];

  private titles = [
    'Example Domain',
    'GitHub - Where the world builds software',
    'Stack Overflow - Where Developers Learn',
    'Reddit - Dive into anything',
    "Twitter - What's happening",
  ];

  private elements = ['button#submit', 'a.nav-link', 'input[type="text"]', 'div.card', 'span.icon'];

  /**
   * Generate a page view event
   */
  generatePageView(): ExtensionEvent {
    const index = Math.floor(Math.random() * this.urls.length);
    return {
      type: 'page_view',
      url: this.urls[index],
      title: this.titles[index],
      timestamp: Date.now(),
    };
  }

  /**
   * Generate a click event
   */
  generateClick(): ExtensionEvent {
    const urlIndex = Math.floor(Math.random() * this.urls.length);
    const elementIndex = Math.floor(Math.random() * this.elements.length);

    return {
      type: 'click',
      url: this.urls[urlIndex],
      element: this.elements[elementIndex],
      timestamp: Date.now(),
    };
  }

  /**
   * Generate a form submit event
   */
  generateFormSubmit(): ExtensionEvent {
    const urlIndex = Math.floor(Math.random() * this.urls.length);

    return {
      type: 'form_submit',
      url: this.urls[urlIndex],
      form_id: `form_${Math.random().toString(36).substr(2, 9)}`,
      timestamp: Date.now(),
    };
  }

  /**
   * Generate a random event
   */
  generateEvent(): ExtensionEvent {
    const eventTypes = [this.generatePageView, this.generateClick, this.generateFormSubmit];
    const generator = eventTypes[Math.floor(Math.random() * eventTypes.length)];
    return generator.call(this);
  }

  /**
   * Generate multiple events
   */
  generateEvents(count: number = 10): ExtensionEvent[] {
    return Array.from({ length: count }, () => this.generateEvent());
  }

  /**
   * Start streaming events
   */
  startStreaming(callback: (event: ExtensionEvent) => void, intervalMs: number = 3000): () => void {
    const interval = setInterval(() => {
      callback(this.generateEvent());
    }, intervalMs);

    return () => clearInterval(interval);
  }
}

/**
 * Mock data manager - coordinates all generators
 */
export class MockDataManager {
  private metricsGenerator = new MockMetricsGenerator();
  private eventsGenerator = new MockEventsGenerator();
  private extensionEventsGenerator = new MockExtensionEventsGenerator();
  private stopFunctions: Array<() => void> = [];

  /**
   * Start all mock data streams
   */
  startAll(callbacks: {
    onMetric?: (metric: Metric) => void;
    onEvent?: (event: Event) => void;
    onExtensionEvent?: (event: ExtensionEvent) => void;
  }) {
    if (callbacks.onMetric) {
      const stop = this.metricsGenerator.startStreaming(callbacks.onMetric, 2000);
      this.stopFunctions.push(stop);
    }

    if (callbacks.onEvent) {
      const stop = this.eventsGenerator.startStreaming(callbacks.onEvent, 5000);
      this.stopFunctions.push(stop);
    }

    if (callbacks.onExtensionEvent) {
      const stop = this.extensionEventsGenerator.startStreaming(callbacks.onExtensionEvent, 3000);
      this.stopFunctions.push(stop);
    }
  }

  /**
   * Stop all mock data streams
   */
  stopAll() {
    this.stopFunctions.forEach(stop => stop());
    this.stopFunctions = [];
  }

  /**
   * Get generators for manual use
   */
  get metrics() {
    return this.metricsGenerator;
  }

  get events() {
    return this.eventsGenerator;
  }

  get extensionEvents() {
    return this.extensionEventsGenerator;
  }
}

// Export singleton instance
export const mockData = new MockDataManager();
