/**
 * @fileoverview Metric glossary definitions with business context.
 *
 * Provides human-friendly explanations, thresholds, and reference
 * material for every metric that appears in the analytics experience.
 *
 * @module analytics/metrics
 */

export interface MetricGlossaryEntry {
  shortName: string;
  fullName: string;
  description: string;
  why: string;
  unit: string;
  goodThreshold: number;
  poorThreshold: number;
  category: 'loading' | 'interactivity' | 'stability' | 'network';
  direction: 'higher-is-better' | 'lower-is-better';
  formula?: string;
  relatedMetrics: string[];
  learnMoreUrl: string;
  examples: {
    good: { value: number; description: string };
    poor: { value: number; description: string };
  };
}

/**
 * Metric glossary with contextual explanations.
 *
 * Note: Thresholds reflect industry guidance (web.dev or similar) and
 * internal experience with realistic performance budgets.
 */
export const METRIC_GLOSSARY: Record<string, MetricGlossaryEntry> = {
  lcp: {
    shortName: 'LCP',
    fullName: 'Largest Contentful Paint',
    description: 'Time until the largest above-the-fold element becomes visible to the user.',
    why: 'LCP strongly correlates with perceived load speed; slow hero content leads to abandonment.',
    unit: 'ms',
    goodThreshold: 2500,
    poorThreshold: 4000,
    category: 'loading',
    direction: 'lower-is-better',
    formula: '75th percentile render time of the largest content element in the viewport.',
    relatedMetrics: ['fcp', 'ttfb', 'resourceTransfer', 'resourceCount'],
    learnMoreUrl: 'https://web.dev/articles/lcp',
    examples: {
      good: {
        value: 1800,
        description: 'Optimized hero image delivered via CDN and rendered in under 2 seconds.',
      },
      poor: {
        value: 5200,
        description: 'Unoptimized carousel image blocked by render-blocking CSS and scripts.',
      },
    },
  },
  inp: {
    shortName: 'INP',
    fullName: 'Interaction to Next Paint',
    description: 'Measures how quickly the page responds to user input and renders the next frame.',
    why: 'Users expect immediate feedback; delays make interfaces feel broken or laggy.',
    unit: 'ms',
    goodThreshold: 200,
    poorThreshold: 500,
    category: 'interactivity',
    direction: 'lower-is-better',
    formula: '75th percentile of event latency across all discrete interactions.',
    relatedMetrics: ['tbt', 'longTaskCount', 'maxInteractionLatency'],
    learnMoreUrl: 'https://web.dev/articles/inp',
    examples: {
      good: {
        value: 120,
        description: 'Search input debounced and heavy work moved to a worker thread.',
      },
      poor: {
        value: 640,
        description: 'Expensive React state updates triggered on each key press without batching.',
      },
    },
  },
  cls: {
    shortName: 'CLS',
    fullName: 'Cumulative Layout Shift',
    description: 'Quantifies unexpected layout movement during the lifespan of the page.',
    why: 'Layout shifts break trust and cause mis-clicks, especially during checkout flows.',
    unit: '',
    goodThreshold: 0.1,
    poorThreshold: 0.25,
    category: 'stability',
    direction: 'lower-is-better',
    formula: 'Sum of impact fraction × distance fraction for layout shifts in the viewport.',
    relatedMetrics: ['lcp', 'resourceTransfer'],
    learnMoreUrl: 'https://web.dev/articles/cls',
    examples: {
      good: {
        value: 0.04,
        description: 'Images include width/height and space is reserved for dynamic content.',
      },
      poor: {
        value: 0.38,
        description: 'Ads injected above the hero push content as users attempt to interact.',
      },
    },
  },
  tbt: {
    shortName: 'TBT',
    fullName: 'Total Blocking Time',
    description: 'Total duration where the main thread is blocked for more than 50 ms at a time.',
    why: 'Blocked main threads delay input handling, leading to poor responsiveness scores.',
    unit: 'ms',
    goodThreshold: 200,
    poorThreshold: 600,
    category: 'interactivity',
    direction: 'lower-is-better',
    formula: 'Sum of (task duration − 50 ms) for long tasks between FCP and TTI.',
    relatedMetrics: ['inp', 'longTaskCount', 'maxInteractionLatency'],
    learnMoreUrl: 'https://web.dev/articles/tbt',
    examples: {
      good: {
        value: 120,
        description: 'Scripts split by route with idle callbacks for hydration tasks.',
      },
      poor: {
        value: 780,
        description: 'Large bundles and synchronous third-party scripts block the main thread.',
      },
    },
  },
  fcp: {
    shortName: 'FCP',
    fullName: 'First Contentful Paint',
    description: 'Time for the first text or image to render on screen.',
    why: 'FCP is the first visual feedback; slow FCP makes pages appear blank and unresponsive.',
    unit: 'ms',
    goodThreshold: 1800,
    poorThreshold: 3000,
    category: 'loading',
    direction: 'lower-is-better',
    formula: 'Timestamp difference between navigation start and first paint of DOM content.',
    relatedMetrics: ['ttfb', 'lcp'],
    learnMoreUrl: 'https://web.dev/articles/fcp',
    examples: {
      good: {
        value: 1200,
        description: 'Critical CSS inlined and fonts preloaded for above-the-fold content.',
      },
      poor: {
        value: 3600,
        description: 'Render-blocking JavaScript delays first meaningful paint.',
      },
    },
  },
  ttfb: {
    shortName: 'TTFB',
    fullName: 'Time to First Byte',
    description: 'Measures server responsiveness from request start until the first byte arrives.',
    why: 'Slow TTFB cascades into every other loading metric and hurts SEO rankings.',
    unit: 'ms',
    goodThreshold: 800,
    poorThreshold: 1800,
    category: 'loading',
    direction: 'lower-is-better',
    formula: 'Time from navigation start to first byte of the initial HTML response.',
    relatedMetrics: ['fcp', 'lcp', 'resourceTransfer'],
    learnMoreUrl: 'https://web.dev/articles/ttfb',
    examples: {
      good: {
        value: 420,
        description: 'Pages served from edge cache with optimized database queries.',
      },
      poor: {
        value: 2100,
        description: 'Cold cache and chatty backend calls delay initial response.',
      },
    },
  },
  resourceCount: {
    shortName: 'Requests',
    fullName: 'Total Network Requests',
    description: 'Number of network requests required to render the page.',
    why: 'Every network round-trip adds overhead; excessive requests slow down loads on slow connections.',
    unit: '',
    goodThreshold: 50,
    poorThreshold: 100,
    category: 'network',
    direction: 'lower-is-better',
    formula: 'Count of fetch/XHR/resource entries captured during page load.',
    relatedMetrics: ['resourceTransfer', 'cachedRequests', 'lcp'],
    learnMoreUrl: 'https://web.dev/articles/fast#minimize-network-requests',
    examples: {
      good: {
        value: 42,
        description: 'Assets bundled and deferred, with lazy loading for non-critical imagery.',
      },
      poor: {
        value: 146,
        description: 'Multiple widget scripts and unoptimized image galleries load up front.',
      },
    },
  },
  resourceTransfer: {
    shortName: 'Transfer Size',
    fullName: 'Total Transfer Size',
    description: 'Combined size of all resources downloaded to render the page.',
    why: 'Larger payloads increase load times and cost users data, especially on mobile.',
    unit: 'KB',
    goodThreshold: 1600,
    poorThreshold: 3200,
    category: 'network',
    direction: 'lower-is-better',
    formula: 'Sum of transferSize across network requests during the session.',
    relatedMetrics: ['resourceCount', 'cachedRequests', 'lcp'],
    learnMoreUrl: 'https://web.dev/articles/fast#keep-request-sizes-small',
    examples: {
      good: {
        value: 950,
        description: 'Images served in AVIF/WebP and JavaScript split by route.',
      },
      poor: {
        value: 4100,
        description: 'Uncompressed hero media and duplicated library bundles.',
      },
    },
  },
  cachedRequests: {
    shortName: 'Cache Hit Rate',
    fullName: 'Cached Requests',
    description: 'Percentage of requests resolved from cache instead of the network.',
    why: 'Strong caching reduces bandwidth, latency, and load on origin servers.',
    unit: '%',
    goodThreshold: 80,
    poorThreshold: 50,
    category: 'network',
    direction: 'higher-is-better',
    formula: 'cachedRequests ÷ totalRequests × 100.',
    relatedMetrics: ['resourceCount', 'resourceTransfer', 'ttfb'],
    learnMoreUrl: 'https://web.dev/articles/uses-long-cache-ttl',
    examples: {
      good: {
        value: 88,
        description: 'Static assets versioned with long-lived cache headers and service worker support.',
      },
      poor: {
        value: 34,
        description: 'Assets served with default cache-control and no CDN edge caching.',
      },
    },
  },
  interactionCount: {
    shortName: 'Interactions',
    fullName: 'User Interaction Count',
    description: 'Number of tracked user interactions (clicks, taps, keyboard events).',
    why: 'Higher interaction volume signals engagement and helps evaluate UX changes.',
    unit: '',
    goodThreshold: 200,
    poorThreshold: 50,
    category: 'interactivity',
    direction: 'higher-is-better',
    formula: 'Count of interaction events captured by engagement collectors per reporting window.',
    relatedMetrics: ['sessionCount', 'averageSessionDuration', 'inp'],
    learnMoreUrl: 'https://web.dev/articles/user-centric-performance-metrics',
    examples: {
      good: {
        value: 260,
        description: 'Dashboard with clear CTAs that invite exploration and drilling.',
      },
      poor: {
        value: 24,
        description: 'Confusing layout that leads users to bounce before interacting.',
      },
    },
  },
  sessionCount: {
    shortName: 'Sessions',
    fullName: 'Session Count',
    description: 'Total user sessions observed during the selected period.',
    why: 'Session volume contextualizes engagement and conversion performance.',
    unit: '',
    goodThreshold: 500,
    poorThreshold: 100,
    category: 'interactivity',
    direction: 'higher-is-better',
    formula: 'Unique session identifiers aggregated within the reporting interval.',
    relatedMetrics: ['interactionCount', 'averageSessionDuration', 'bounceRate'],
    learnMoreUrl: 'https://support.google.com/analytics/answer/9191807',
    examples: {
      good: {
        value: 840,
        description: 'Successful campaign driving qualified traffic to the product area.',
      },
      poor: {
        value: 75,
        description: 'Limited discoverability with most acquisition channels underperforming.',
      },
    },
  },
  averageSessionDuration: {
    shortName: 'Avg. Session',
    fullName: 'Average Session Duration',
    description: 'Average time users stay engaged during a session.',
    why: 'Longer sessions suggest valuable content and sustained engagement.',
    unit: 's',
    goodThreshold: 300,
    poorThreshold: 120,
    category: 'interactivity',
    direction: 'higher-is-better',
    formula: 'Total session seconds ÷ session count within the reporting window.',
    relatedMetrics: ['sessionCount', 'interactionCount', 'bounceRate'],
    learnMoreUrl: 'https://support.google.com/analytics/answer/9191807#zippy=%2Caverage-session-duration',
    examples: {
      good: {
        value: 420,
        description: 'Personalized recommendations keep users exploring relevant content.',
      },
      poor: {
        value: 95,
        description: 'Users struggle to find information and exit after a quick glance.',
      },
    },
  },
  bounceRate: {
    shortName: 'Bounce Rate',
    fullName: 'Bounce Rate',
    description: 'Share of sessions where users leave after viewing a single page.',
    why: 'High bounce rates indicate the landing experience fails to meet user intent.',
    unit: '%',
    goodThreshold: 40,
    poorThreshold: 60,
    category: 'stability',
    direction: 'lower-is-better',
    formula: 'Single-page sessions ÷ total sessions × 100.',
    relatedMetrics: ['sessionCount', 'averageSessionDuration', 'lcp'],
    learnMoreUrl: 'https://support.google.com/analytics/answer/1009409',
    examples: {
      good: {
        value: 32,
        description: 'Fast landing pages with tailored messaging drive deeper exploration.',
      },
      poor: {
        value: 68,
        description: 'Slow hero image and unclear CTA cause users to abandon immediately.',
      },
    },
  },
  errorRate: {
    shortName: 'Error Rate',
    fullName: 'JavaScript Error Rate',
    description: 'Percentage of sessions with uncaught errors or rejected promises.',
    why: 'Client-side errors break key flows and erode user trust, often causing churn.',
    unit: '%',
    goodThreshold: 1,
    poorThreshold: 2,
    category: 'stability',
    direction: 'lower-is-better',
    formula: 'Erroring sessions ÷ total sessions × 100.',
    relatedMetrics: ['lcp', 'interactionCount'],
    learnMoreUrl: 'https://web.dev/articles/monitoring-javascript-errors',
    examples: {
      good: {
        value: 0.4,
        description: 'Errors captured with Sentry and fixed quickly before impacting many users.',
      },
      poor: {
        value: 3.5,
        description: 'Unhandled promise rejections block checkout flow on Safari.',
      },
    },
  },
  longTaskCount: {
    shortName: 'Long Tasks',
    fullName: 'Long Task Count',
    description: 'Number of main-thread tasks exceeding 50 ms.',
    why: 'Clusters of long tasks block the UI and inflate INP/TBT scores.',
    unit: '',
    goodThreshold: 10,
    poorThreshold: 30,
    category: 'interactivity',
    direction: 'lower-is-better',
    formula: 'Count of PerformanceObserver long task entries per reporting window.',
    relatedMetrics: ['tbt', 'inp', 'maxInteractionLatency'],
    learnMoreUrl: 'https://web.dev/articles/long-tasks-devtools',
    examples: {
      good: {
        value: 6,
        description: 'Code-splitting ensures hydration is spread across idle slots.',
      },
      poor: {
        value: 48,
        description: 'Heavy third-party script runs synchronously on every route.',
      },
    },
  },
  maxInteractionLatency: {
    shortName: 'Max Latency',
    fullName: 'Max Interaction Latency',
    description: 'Slowest interaction recorded during the reporting window.',
    why: 'Outlier latency often highlights worst-case UX that damages brand trust.',
    unit: 'ms',
    goodThreshold: 250,
    poorThreshold: 600,
    category: 'interactivity',
    direction: 'lower-is-better',
    formula: 'Maximum duration from interaction start to next paint.',
    relatedMetrics: ['inp', 'tbt', 'longTaskCount'],
    learnMoreUrl: 'https://web.dev/articles/inp#outliers',
    examples: {
      good: {
        value: 180,
        description: 'Autocomplete results render in worker thread with incremental hydration.',
      },
      poor: {
        value: 920,
        description: 'Blocking analytics script fires on every click before UI updates.',
      },
    },
  },
  activeAlerts: {
    shortName: 'Active Alerts',
    fullName: 'Critical Alert Count',
    description: 'Number of unresolved alerts breaching configured performance thresholds.',
    why: 'Tracking unresolved alerts ensures regressions are triaged before they impact users widely.',
    unit: '',
    goodThreshold: 0,
    poorThreshold: 1,
    category: 'stability',
    direction: 'lower-is-better',
    formula: 'Count of open alert records with severity warning or higher.',
    relatedMetrics: ['lcp', 'inp', 'errorRate'],
    learnMoreUrl: 'https://web.dev/articles/establish-quality-baselines',
    examples: {
      good: {
        value: 0,
        description: 'All recent regressions resolved and performance budgets satisfied.',
      },
      poor: {
        value: 3,
        description: 'Multiple LCP and INP alerts pending, indicating user-experience risk.',
      },
    },
  },
};

/**
 * Safe helper for retrieving glossary entries.
 */
export const getMetricGlossaryEntry = (metricKey: string): MetricGlossaryEntry | undefined => {
  return METRIC_GLOSSARY[metricKey];
};
