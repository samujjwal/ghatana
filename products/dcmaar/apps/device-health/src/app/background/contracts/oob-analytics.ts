/**
 * @fileoverview OOB Analytics Schema
 *
 * Defines comprehensive Out-of-Box analytics data structures for:
 * 1. Website/page visit tracking and statistics
 * 2. Network monitoring, web vitals, performance, and health
 * 3. Web app/page engagement: sessions, page loads, clicks, interactions
 *
 * Based on research from:
 * - Google Chrome Web Vitals Extension
 * - Web Activity Time Tracker patterns
 * - Industry best practices for engagement metrics
 *
 * @module contracts/oob-analytics
 * @since 2.0.0
 */

import { z } from 'zod';

// ============================================================================
// Page Visit Tracking
// ============================================================================

/**
 * Page visit event
 */
export const PageVisitSchema = z.object({
  /** Unique visit ID */
  visitId: z.string(),
  /** URL visited */
  url: z.string().url(),
  /** Domain extracted from URL */
  domain: z.string(),
  /** Page title */
  title: z.string().optional(),
  /** Visit start timestamp */
  startTime: z.number(),
  /** Visit end timestamp (null if still active) */
  endTime: z.number().nullable(),
  /** Time spent on page (milliseconds) */
  duration: z.number().default(0),
  /** Tab ID */
  tabId: z.number(),
  /** Window ID */
  windowId: z.number(),
  /** Referrer URL */
  referrer: z.string().optional(),
  /** Visit type */
  visitType: z.enum(['typed', 'link', 'auto_bookmark', 'reload', 'generated', 'keyword']).optional(),
  /** Page lifecycle state */
  lifecycleState: z.enum(['active', 'passive', 'hidden', 'frozen', 'terminated']).default('active'),
});

export type PageVisit = z.infer<typeof PageVisitSchema>;

/**
 * Page visit statistics (aggregated)
 */
export const PageVisitStatsSchema = z.object({
  /** Domain or URL */
  identifier: z.string(),
  /** Total visits */
  totalVisits: z.number().default(0),
  /** Total time spent (milliseconds) */
  totalTime: z.number().default(0),
  /** Average time per visit */
  averageTime: z.number().default(0),
  /** First visit timestamp */
  firstVisit: z.number().optional(),
  /** Last visit timestamp */
  lastVisit: z.number().optional(),
  /** Daily breakdown */
  dailyStats: z.array(z.object({
    date: z.string(), // YYYY-MM-DD
    visits: z.number(),
    time: z.number(),
  })).default([]),
  /** Category classification */
  category: z.enum(['work', 'social', 'entertainment', 'shopping', 'news', 'productivity', 'other']).optional(),
});

export type PageVisitStats = z.infer<typeof PageVisitStatsSchema>;

// ============================================================================
// Core Web Vitals
// ============================================================================

/**
 * Largest Contentful Paint (LCP)
 * Measures perceived load speed
 * Good: < 2.5s, Needs Improvement: 2.5s-4s, Poor: > 4s
 */
export const LCPMetricSchema = z.object({
  /** LCP value in milliseconds */
  value: z.number(),
  /** Element that triggered LCP */
  element: z.string().optional(),
  /** Rating based on thresholds */
  rating: z.enum(['good', 'needs-improvement', 'poor']),
  /** Time when metric was captured */
  timestamp: z.number(),
});

export type LCPMetric = z.infer<typeof LCPMetricSchema>;

/**
 * Interaction to Next Paint (INP)
 * Measures responsiveness to user interactions
 * Good: < 200ms, Needs Improvement: 200ms-500ms, Poor: > 500ms
 */
export const INPMetricSchema = z.object({
  /** INP value in milliseconds */
  value: z.number(),
  /** Interaction type */
  interactionType: z.enum(['click', 'tap', 'keyboard']).optional(),
  /** Rating based on thresholds */
  rating: z.enum(['good', 'needs-improvement', 'poor']),
  /** Time when metric was captured */
  timestamp: z.number(),
});

export type INPMetric = z.infer<typeof INPMetricSchema>;

/**
 * Cumulative Layout Shift (CLS)
 * Measures visual stability
 * Good: < 0.1, Needs Improvement: 0.1-0.25, Poor: > 0.25
 */
export const CLSMetricSchema = z.object({
  /** CLS value (score) */
  value: z.number(),
  /** Number of layout shifts */
  shiftCount: z.number().default(0),
  /** Rating based on thresholds */
  rating: z.enum(['good', 'needs-improvement', 'poor']),
  /** Time when metric was captured */
  timestamp: z.number(),
});

export type CLSMetric = z.infer<typeof CLSMetricSchema>;

/**
 * Additional Web Vitals
 */
export const AdditionalWebVitalsSchema = z.object({
  /** Time to First Byte */
  ttfb: z.number().optional(),
  /** First Contentful Paint */
  fcp: z.number().optional(),
  /** Time to Interactive */
  tti: z.number().optional(),
  /** Total Blocking Time */
  tbt: z.number().optional(),
});

export type AdditionalWebVitals = z.infer<typeof AdditionalWebVitalsSchema>;

/**
 * Complete Web Vitals snapshot
 */
export const WebVitalsSnapshotSchema = z.object({
  /** URL being measured */
  url: z.string().url(),
  /** Page title */
  title: z.string().optional(),
  /** Core Web Vitals */
  lcp: LCPMetricSchema.optional(),
  inp: INPMetricSchema.optional(),
  cls: CLSMetricSchema.optional(),
  /** Additional metrics */
  additional: AdditionalWebVitalsSchema.optional(),
  /** Overall rating */
  overallRating: z.enum(['good', 'needs-improvement', 'poor']),
  /** Capture timestamp */
  timestamp: z.number(),
  /** Tab ID */
  tabId: z.number(),
});

export type WebVitalsSnapshot = z.infer<typeof WebVitalsSnapshotSchema>;

// ============================================================================
// Network Monitoring
// ============================================================================

/**
 * Network request details
 */
export const NetworkRequestSchema = z.object({
  /** Request ID */
  requestId: z.string(),
  /** URL requested */
  url: z.string().url(),
  /** HTTP method */
  method: z.enum(['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']),
  /** Request type */
  type: z.enum([
    'main_frame', 'sub_frame', 'stylesheet', 'script', 'image',
    'font', 'object', 'xmlhttprequest', 'ping', 'csp_report',
    'media', 'websocket', 'webtransport', 'webbundle', 'other'
  ]),
  /** HTTP status code */
  statusCode: z.number().optional(),
  /** Request start time */
  startTime: z.number(),
  /** Request end time */
  endTime: z.number().optional(),
  /** Request duration (milliseconds) */
  duration: z.number().optional(),
  /** Request size (bytes) */
  requestSize: z.number().optional(),
  /** Response size (bytes) */
  responseSize: z.number().optional(),
  /** From cache */
  fromCache: z.boolean().default(false),
  /** Request initiator */
  initiator: z.string().optional(),
  /** Tab ID */
  tabId: z.number(),
});

export type NetworkRequest = z.infer<typeof NetworkRequestSchema>;

/**
 * Network statistics (aggregated)
 */
export const NetworkStatsSchema = z.object({
  /** Total requests */
  totalRequests: z.number().default(0),
  /** Failed requests */
  failedRequests: z.number().default(0),
  /** Cached requests */
  cachedRequests: z.number().default(0),
  /** Total bytes transferred */
  totalBytes: z.number().default(0),
  /** Average request duration */
  averageDuration: z.number().default(0),
  /** Requests by type */
  byType: z.record(z.string(), z.number()).default({}),
  /** Requests by status code */
  byStatusCode: z.record(z.string(), z.number()).default({}),
  /** Time window */
  timeWindow: z.object({
    start: z.number(),
    end: z.number(),
  }),
});

export type NetworkStats = z.infer<typeof NetworkStatsSchema>;

// ============================================================================
// Performance Metrics
// ============================================================================

/**
 * Page load performance
 */
export const PageLoadPerformanceSchema = z.object({
  /** URL */
  url: z.string().url(),
  /** Navigation type */
  navigationType: z.enum(['navigate', 'reload', 'back_forward', 'prerender']),
  /** Navigation timing */
  timing: z.object({
    /** DNS lookup time */
    dnsTime: z.number().optional(),
    /** TCP connection time */
    tcpTime: z.number().optional(),
    /** TLS negotiation time */
    tlsTime: z.number().optional(),
    /** Request time */
    requestTime: z.number().optional(),
    /** Response time */
    responseTime: z.number().optional(),
    /** DOM processing time */
    domProcessingTime: z.number().optional(),
    /** DOM content loaded */
    domContentLoaded: z.number().optional(),
    /** Load complete */
    loadComplete: z.number().optional(),
    /** Total load time */
    totalLoadTime: z.number(),
  }),
  /** Resource timing summary */
  resources: z.object({
    totalResources: z.number().default(0),
    totalSize: z.number().default(0),
    byType: z.record(z.string(), z.object({
      count: z.number(),
      size: z.number(),
      duration: z.number(),
    })).default({}),
  }).optional(),
  /** Timestamp */
  timestamp: z.number(),
  /** Tab ID */
  tabId: z.number(),
});

export type PageLoadPerformance = z.infer<typeof PageLoadPerformanceSchema>;

/**
 * Browser performance snapshot
 */
export const BrowserPerformanceSchema = z.object({
  /** Memory usage */
  memory: z.object({
    /** Used JS heap size (bytes) */
    usedJSHeapSize: z.number(),
    /** Total JS heap size (bytes) */
    totalJSHeapSize: z.number(),
    /** JS heap size limit (bytes) */
    jsHeapSizeLimit: z.number(),
    /** Usage percentage */
    usagePercent: z.number(),
  }).optional(),
  /** CPU usage estimate */
  cpu: z.object({
    /** CPU usage percentage (estimated) */
    usage: z.number(),
  }).optional(),
  /** Active tabs count */
  activeTabs: z.number(),
  /** Active windows count */
  activeWindows: z.number(),
  /** Timestamp */
  timestamp: z.number(),
});

export type BrowserPerformance = z.infer<typeof BrowserPerformanceSchema>;

// ============================================================================
// Engagement Metrics
// ============================================================================

/**
 * User interaction event
 */
export const UserInteractionSchema = z.object({
  /** Interaction ID */
  interactionId: z.string(),
  /** Interaction type */
  type: z.enum(['click', 'scroll', 'input', 'focus', 'blur', 'submit', 'hover', 'keypress']),
  /** Target element (selector or description) */
  target: z.string().optional(),
  /** X coordinate */
  x: z.number().optional(),
  /** Y coordinate */
  y: z.number().optional(),
  /** Scroll depth (percentage) */
  scrollDepth: z.number().optional(),
  /** Input value length (for privacy, not actual value) */
  inputLength: z.number().optional(),
  /** Timestamp */
  timestamp: z.number(),
  /** URL where interaction occurred */
  url: z.string().url(),
  /** Tab ID */
  tabId: z.number(),
});

export type UserInteraction = z.infer<typeof UserInteractionSchema>;

/**
 * Session information
 */
export const SessionSchema = z.object({
  /** Session ID */
  sessionId: z.string(),
  /** Session start time */
  startTime: z.number(),
  /** Session end time (null if active) */
  endTime: z.number().nullable(),
  /** Session duration (milliseconds) */
  duration: z.number().default(0),
  /** Total page views in session */
  pageViews: z.number().default(0),
  /** Unique domains visited */
  uniqueDomains: z.number().default(0),
  /** Total interactions */
  totalInteractions: z.number().default(0),
  /** Is engaged session (>10s, 2+ pageviews, or conversion) */
  isEngaged: z.boolean().default(false),
  /** Entry URL */
  entryUrl: z.string().url().optional(),
  /** Exit URL */
  exitUrl: z.string().url().optional(),
  /** User agent */
  userAgent: z.string().optional(),
});

export type Session = z.infer<typeof SessionSchema>;

/**
 * Page engagement metrics
 */
export const PageEngagementSchema = z.object({
  /** URL */
  url: z.string().url(),
  /** Visit ID reference */
  visitId: z.string(),
  /** Active time on page (milliseconds, excluding idle) */
  activeTime: z.number().default(0),
  /** Total time on page (milliseconds) */
  totalTime: z.number().default(0),
  /** Scroll depth reached (percentage) */
  maxScrollDepth: z.number().default(0),
  /** Click count */
  clickCount: z.number().default(0),
  /** Input interactions */
  inputCount: z.number().default(0),
  /** Mouse movements */
  mouseMovements: z.number().default(0),
  /** Time to first interaction (milliseconds) */
  timeToFirstInteraction: z.number().optional(),
  /** Rage clicks detected */
  rageClicks: z.number().default(0),
  /** Dead clicks detected (clicks with no response) */
  deadClicks: z.number().default(0),
  /** Page exit type */
  exitType: z.enum(['close', 'navigate', 'reload', 'timeout', 'unknown']).optional(),
  /** Timestamp */
  timestamp: z.number(),
  /** Tab ID */
  tabId: z.number(),
});

export type PageEngagement = z.infer<typeof PageEngagementSchema>;

/**
 * Engagement statistics (aggregated)
 */
export const EngagementStatsSchema = z.object({
  /** Total sessions */
  totalSessions: z.number().default(0),
  /** Engaged sessions */
  engagedSessions: z.number().default(0),
  /** Engagement rate */
  engagementRate: z.number().default(0),
  /** Average session duration */
  avgSessionDuration: z.number().default(0),
  /** Average page views per session */
  avgPageViewsPerSession: z.number().default(0),
  /** Total interactions */
  totalInteractions: z.number().default(0),
  /** Bounce rate (single page sessions) */
  bounceRate: z.number().default(0),
  /** Time window */
  timeWindow: z.object({
    start: z.number(),
    end: z.number(),
  }),
});

export type EngagementStats = z.infer<typeof EngagementStatsSchema>;

// ============================================================================
// Health & Diagnostics
// ============================================================================

/**
 * Page health snapshot
 */
export const PageHealthSchema = z.object({
  /** URL */
  url: z.string().url(),
  /** Overall health score (0-100) */
  healthScore: z.number().min(0).max(100),
  /** Web Vitals status */
  webVitals: z.object({
    lcp: z.enum(['good', 'needs-improvement', 'poor']).optional(),
    inp: z.enum(['good', 'needs-improvement', 'poor']).optional(),
    cls: z.enum(['good', 'needs-improvement', 'poor']).optional(),
  }),
  /** Performance grade */
  performanceGrade: z.enum(['A', 'B', 'C', 'D', 'F']),
  /** Issues detected */
  issues: z.array(z.object({
    type: z.enum(['performance', 'security', 'accessibility', 'seo', 'network']),
    severity: z.enum(['critical', 'high', 'medium', 'low']),
    message: z.string(),
  })).default([]),
  /** Recommendations */
  recommendations: z.array(z.string()).default([]),
  /** Timestamp */
  timestamp: z.number(),
});

export type PageHealth = z.infer<typeof PageHealthSchema>;

// ============================================================================
// Composite Analytics Event
// ============================================================================

/**
 * Complete analytics event combining all metrics
 */
export const AnalyticsEventSchema = z.object({
  /** Event ID */
  eventId: z.string(),
  /** Event type */
  type: z.enum([
    'page-visit',
    'web-vitals',
    'network-request',
    'page-load',
    'user-interaction',
    'session',
    'engagement',
    'performance',
    'health',
  ]),
  /** Timestamp */
  timestamp: z.number(),
  /** URL context */
  url: z.string().url(),
  /** Tab ID */
  tabId: z.number(),
  /** Event-specific payload */
  payload: z.union([
    PageVisitSchema,
    WebVitalsSnapshotSchema,
    NetworkRequestSchema,
    PageLoadPerformanceSchema,
    UserInteractionSchema,
    SessionSchema,
    PageEngagementSchema,
    BrowserPerformanceSchema,
    PageHealthSchema,
  ]),
  /** Session ID reference */
  sessionId: z.string().optional(),
  /** Visit ID reference */
  visitId: z.string().optional(),
});

export type AnalyticsEvent = z.infer<typeof AnalyticsEventSchema>;

// ============================================================================
// Validation Helpers
// ============================================================================

/**
 * Validates an analytics event
 */
export function validateAnalyticsEvent(event: unknown): {
  valid: boolean;
  error?: string;
  data?: AnalyticsEvent;
} {
  try {
    const data = AnalyticsEventSchema.parse(event);
    return { valid: true, data };
  } catch (error) {
    const errorMessage =
      error instanceof z.ZodError
        ? error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`).join('; ')
        : String(error);
    return { valid: false, error: errorMessage };
  }
}

/**
 * Calculates Web Vitals rating
 */
export function calculateWebVitalsRating(
  lcp?: number,
  inp?: number,
  cls?: number
): 'good' | 'needs-improvement' | 'poor' {
  const ratings: Array<'good' | 'needs-improvement' | 'poor'> = [];

  if (lcp !== undefined) {
    if (lcp <= 2500) ratings.push('good');
    else if (lcp <= 4000) ratings.push('needs-improvement');
    else ratings.push('poor');
  }

  if (inp !== undefined) {
    if (inp <= 200) ratings.push('good');
    else if (inp <= 500) ratings.push('needs-improvement');
    else ratings.push('poor');
  }

  if (cls !== undefined) {
    if (cls <= 0.1) ratings.push('good');
    else if (cls <= 0.25) ratings.push('needs-improvement');
    else ratings.push('poor');
  }

  if (ratings.length === 0) return 'good';

  // If any metric is poor, overall is poor
  if (ratings.includes('poor')) return 'poor';
  // If any metric needs improvement, overall needs improvement
  if (ratings.includes('needs-improvement')) return 'needs-improvement';
  // All good
  return 'good';
}

/**
 * Calculates health score (0-100)
 */
export function calculateHealthScore(params: {
  webVitals?: WebVitalsSnapshot;
  performance?: PageLoadPerformance;
  networkStats?: NetworkStats;
}): number {
  let score = 100;

  // Web Vitals impact (40 points)
  if (params.webVitals) {
    const vitalsRating = params.webVitals.overallRating;
    if (vitalsRating === 'poor') score -= 40;
    else if (vitalsRating === 'needs-improvement') score -= 20;
  }

  // Performance impact (30 points)
  if (params.performance) {
    const loadTime = params.performance.timing.totalLoadTime;
    if (loadTime > 10000) score -= 30;
    else if (loadTime > 5000) score -= 15;
    else if (loadTime > 3000) score -= 5;
  }

  // Network impact (30 points)
  if (params.networkStats) {
    const failureRate = params.networkStats.failedRequests / Math.max(params.networkStats.totalRequests, 1);
    if (failureRate > 0.2) score -= 30;
    else if (failureRate > 0.1) score -= 15;
    else if (failureRate > 0.05) score -= 5;
  }

  return Math.max(0, Math.min(100, score));
}

/**
 * Determines if session is engaged
 */
export function isEngagedSession(session: {
  duration: number;
  pageViews: number;
  totalInteractions: number;
}): boolean {
  return (
    session.duration >= 10000 || // 10+ seconds
    session.pageViews >= 2 ||     // 2+ page views
    session.totalInteractions > 0 // Has interactions
  );
}
