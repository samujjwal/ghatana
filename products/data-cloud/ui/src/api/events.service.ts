/**
 * Events API Service
 *
 * Provides API client for Data Cloud event stream operations.
 * Supports listing, filtering events and SSE-based streaming.
 *
 * @doc.type service
 * @doc.purpose Event Explorer API client for AEP event fabric
 * @doc.layer frontend
 */

import axios, { type AxiosInstance } from 'axios';

// =============================================================================
// Types
// =============================================================================

export type EventTier = 'HOT' | 'WARM' | 'COOL' | 'COLD';

export interface EventEntry {
  id: string;
  tenantId: string;
  eventType: string;
  tier: EventTier;
  payload: Record<string, unknown>;
  timestamp: string;
  idempotencyKey?: string;
  correlationId?: string;
  source?: string;
  metadata: Record<string, unknown>;
}

export interface EventStats {
  total: number;
  byTier: Record<EventTier, number>;
  byType: Record<string, number>;
  eventsPerMinute: number;
}

export interface EventListResponse {
  events: EventEntry[];
  total: number;
  hasMore: boolean;
}

export interface EventQueryParams {
  tenantId?: string;
  eventType?: string;
  tier?: EventTier;
  from?: string;
  to?: string;
  limit?: number;
  cursor?: string;
}

// =============================================================================
// Client
// =============================================================================

/**
 * EventsService — typed client for DC event log / stream endpoints.
 *
 * @doc.type class
 * @doc.purpose REST + SSE client for Data-Cloud event log
 * @doc.layer frontend
 * @doc.pattern Service
 */
export class EventsService {
  private client: AxiosInstance;
  private baseURL: string;

  constructor(baseURL: string = '/api') {
    this.baseURL = baseURL;
    this.client = axios.create({
      baseURL,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  /** List events with optional filtering */
  async listEvents(params: EventQueryParams = {}): Promise<EventListResponse> {
    const { data } = await this.client.get<EventListResponse>('/dc/events', {
      params,
    });
    return data;
  }

  /** Get event statistics */
  async getStats(tenantId?: string): Promise<EventStats> {
    const { data } = await this.client.get<EventStats>('/dc/events/stats', {
      params: tenantId ? { tenantId } : {},
    });
    return data;
  }

  /** Open an SSE stream for live event tailing */
  openStream(params: EventQueryParams = {}): EventSource {
    const search = new URLSearchParams();
    if (params.tenantId) search.set('tenantId', params.tenantId);
    if (params.eventType) search.set('eventType', params.eventType);
    if (params.tier) search.set('tier', params.tier);
    const query = search.toString();
    return new EventSource(
      `${this.baseURL}/dc/events/stream${query ? `?${query}` : ''}`,
    );
  }
}

export const eventsService = new EventsService(
  import.meta.env.VITE_DC_API_URL ?? '/api',
);
