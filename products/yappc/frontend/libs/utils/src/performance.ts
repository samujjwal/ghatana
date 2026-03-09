/**
 *
 */
interface Metric {
  name: string;
  value: number;
  tags?: Record<string, string>;
}

const metrics: Metric[] = [];

/**
 * Track a performance metric
 */
export function trackMetric(metric: Omit<Metric, 'timestamp'>): void {
  if (process.env.NODE_ENV === 'production') {
    // In production, send to your analytics service
    metrics.push({
      ...metric,
      tags: {
        env: process.env.NODE_ENV,
        ...metric.tags,
      },
    });
    
    // Here you would typically send metrics to your analytics service
    // Example: sendToAnalytics(metric);
  } else {
    // In development, log to console
    console.debug('[Metric]', metric);
  }
}

/**
 * Measure the execution time of a function
 */
export async function measure<T>(
  name: string,
  fn: () => Promise<T>,
  tags?: Record<string, string>
): Promise<T> {
  const start = performance.now();
  
  try {
    const result = await fn();
    const duration = performance.now() - start;
    
    trackMetric({
      name: `${name}.success`,
      value: duration,
      tags: {
        ...tags,
        duration: duration.toFixed(2),
      },
    });
    
    return result;
  } catch (error) {
    const duration = performance.now() - start;
    
    trackMetric({
      name: `${name}.error`,
      value: duration,
      tags: {
        ...tags,
        error: error instanceof Error ? error.message : String(error),
        duration: duration.toFixed(2),
      },
    });
    
    throw error;
  }
}

// Example usage:
// const data = await measure('fetchUserData', () => fetchUserData());
// trackMetric({ name: 'button_click', value: 1, tags: { button_id: 'submit' } });
