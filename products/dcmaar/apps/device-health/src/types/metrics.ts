export interface BaseMetric {
  type: string;
  timestamp?: number;
  pageId?: string;
  context?: Record<string, unknown>;
  data?: unknown;
}

export interface PageViewEvent extends BaseMetric {
  type: 'page_view';
  data: {
    pageTitle: string;
    referrer: string;
  };
}

export interface PerformanceEvent extends BaseMetric {
  type: 'performance';
  data: {
    metric: string;
    value: number;
    timing?: Record<string, number>;
    navigation?: {
      type: string;
      redirectCount: number;
    };
  };
}

export interface UserInteractionEvent extends BaseMetric {
  type: 'user_interaction';
  data: {
    eventType: string;
    target: {
      tagName: string;
      id: string;
      className: string;
    };
  };
}

export interface PageVisibilityEvent extends BaseMetric {
  type: 'page_visibility';
  data: {
    state: DocumentVisibilityState;
    isVisible: boolean;
  };
}

export type MetricEvent =
  | PageViewEvent
  | PerformanceEvent
  | UserInteractionEvent
  | PageVisibilityEvent
  | BaseMetric;

export type RawMetricEvent = Omit<MetricEvent, 'timestamp' | 'pageId' | 'context'>;
