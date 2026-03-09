/**
 * Web Vitals Monitoring
 * Track Core Web Vitals for performance monitoring
 */

import { onCLS, onINP, onFCP, onLCP, onTTFB, type Metric } from 'web-vitals';
import { captureMessage, setContext } from '../config/sentry';

/**
 * Performance thresholds (in milliseconds)
 * Based on Google's Web Vitals recommendations
 */
const THRESHOLDS = {
  CLS: { good: 0.1, needsImprovement: 0.25 }, // Cumulative Layout Shift (no unit)
  INP: { good: 200, needsImprovement: 500 }, // Interaction to Next Paint (ms)
  FCP: { good: 1800, needsImprovement: 3000 }, // First Contentful Paint (ms)
  LCP: { good: 2500, needsImprovement: 4000 }, // Largest Contentful Paint (ms)
  TTFB: { good: 800, needsImprovement: 1800 }, // Time to First Byte (ms)
};

/**
 * Get rating for a metric value
 */
function getRating(
  name: keyof typeof THRESHOLDS,
  value: number
): 'good' | 'needs-improvement' | 'poor' {
  const threshold = THRESHOLDS[name];
  if (value <= threshold.good) return 'good';
  if (value <= threshold.needsImprovement) return 'needs-improvement';
  return 'poor';
}

/**
 * Send metric to analytics
 */
function sendToAnalytics(metric: Metric): void {
  // Get rating
  const rating = metric.rating || getRating(metric.name as keyof typeof THRESHOLDS, metric.value);

  // Log in development
  if (import.meta.env.DEV) {
    console.log(`[Web Vitals] ${metric.name}:`, {
      value: metric.value,
      rating,
      delta: metric.delta,
      id: metric.id,
    });
  }

  // Send to Sentry
  setContext('web-vitals', {
    name: metric.name,
    value: metric.value,
    rating,
    delta: metric.delta,
    id: metric.id,
    navigationType: metric.navigationType,
  });

  // Capture poor metrics as messages
  if (rating === 'poor') {
    captureMessage(
      `Poor Web Vital: ${metric.name} (${metric.value.toFixed(2)})`,
      'warning'
    );
  }

  // Send to Google Analytics if available
  if (typeof window !== 'undefined' && 'gtag' in window) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const gtag = (window as any).gtag;
    gtag('event', metric.name, {
      event_category: 'Web Vitals',
      event_label: metric.id,
      value: Math.round(metric.name === 'CLS' ? metric.value * 1000 : metric.value),
      non_interaction: true,
      metric_rating: rating,
      metric_delta: metric.delta,
    });
  }

  // Send to custom analytics endpoint
  sendToCustomAnalytics(metric, rating);
}

/**
 * Send to custom analytics endpoint
 */
function sendToCustomAnalytics(metric: Metric, rating: string): void {
  const apiUrl = import.meta.env.VITE_API_BASE_URL;
  if (!apiUrl) return;

  try {
    // Use sendBeacon for reliability (won't be cancelled on page unload)
    if (navigator.sendBeacon) {
      const data = JSON.stringify({
        name: metric.name,
        value: metric.value,
        rating,
        delta: metric.delta,
        id: metric.id,
        navigationType: metric.navigationType,
        url: window.location.href,
        timestamp: Date.now(),
      });

      navigator.sendBeacon(`${apiUrl}/analytics/web-vitals`, data);
    } else {
      // Fallback to fetch with keepalive
      fetch(`${apiUrl}/analytics/web-vitals`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: metric.name,
          value: metric.value,
          rating,
          delta: metric.delta,
          id: metric.id,
          navigationType: metric.navigationType,
          url: window.location.href,
          timestamp: Date.now(),
        }),
        keepalive: true,
      }).catch((error) => {
        console.error('Failed to send Web Vitals:', error);
      });
    }
  } catch (error) {
    console.error('Failed to send Web Vitals:', error);
  }
}

/**
 * Initialize Web Vitals monitoring
 */
export function initWebVitals(): void {
  // Cumulative Layout Shift
  onCLS(sendToAnalytics);

  // Interaction to Next Paint
  onINP(sendToAnalytics);

  // First Contentful Paint
  onFCP(sendToAnalytics);

  // Largest Contentful Paint
  onLCP(sendToAnalytics);

  // Time to First Byte
  onTTFB(sendToAnalytics);

  if (import.meta.env.DEV) {
    console.log('[Web Vitals] Monitoring initialized');
  }
}

/**
 * Report all Web Vitals (useful for testing)
 */
export function reportWebVitals(): void {
  initWebVitals();
}

export default initWebVitals;
