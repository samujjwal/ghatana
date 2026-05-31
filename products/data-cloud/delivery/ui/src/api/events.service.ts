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

import type { components } from "../contracts/generated/data-cloud";
import {
  EventQueryResponseSchema,
  EventSchema,
  type Event as BackendEventEntry,
  type EventQueryResponse as BackendEventQueryResponse,
} from "../contracts/schemas";
import { apiClient } from "../lib/api/client";

// =============================================================================
// Types
// =============================================================================

// DC-P1-006: Migrated to use generated type from OpenAPI spec
export type EventTier = components["schemas"]["EventTier"];

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
  private deriveTier(event: BackendEventEntry): EventTier {
    const payloadTier =
      typeof event.payload.tier === "string"
        ? event.payload.tier.toUpperCase()
        : undefined;
    if (
      payloadTier === "HOT" ||
      payloadTier === "WARM" ||
      payloadTier === "COOL" ||
      payloadTier === "COLD"
    ) {
      return payloadTier;
    }

    const timestamp = Date.parse(event.timestamp);
    if (Number.isNaN(timestamp)) {
      return "WARM";
    }

    const ageMs = Date.now() - timestamp;
    if (ageMs <= 5 * 60 * 1000) {
      return "HOT";
    }
    if (ageMs <= 60 * 60 * 1000) {
      return "WARM";
    }
    if (ageMs <= 24 * 60 * 60 * 1000) {
      return "COOL";
    }
    return "COLD";
  }

  private toEventEntry(
    event: BackendEventEntry,
    fallbackTenantId?: string,
  ): EventEntry {
    const offset = typeof event.offset === "number" ? event.offset : undefined;

    return {
      id:
        event.id ??
        (offset !== undefined ? `event-${offset}` : `event-${event.timestamp}`),
      tenantId: event.tenantId ?? fallbackTenantId ?? "unknown",
      eventType: event.type,
      tier: this.deriveTier(event),
      payload: event.payload,
      timestamp: event.timestamp,
      idempotencyKey: event.headers?.["idempotency-key"],
      correlationId: event.correlationId ?? event.headers?.["correlation-id"],
      source: event.source,
      metadata: {
        ...(offset !== undefined ? { offset } : {}),
        ...(event.schemaVersion ? { schemaVersion: event.schemaVersion } : {}),
        ...(event.headers ? { headers: event.headers } : {}),
      },
    };
  }

  /** List events with optional filtering */
  async listEvents(params: EventQueryParams = {}): Promise<EventListResponse> {
    const { tenantId, eventType, from, limit, cursor } = params;
    const queryParams: Record<string, string | number> = {};
    if (tenantId) queryParams.tenantId = tenantId;
    if (eventType) queryParams.type = eventType;
    if (from) queryParams.from = from;
    if (limit) queryParams.limit = limit;
    if (cursor) queryParams.from = cursor;

    const rawResponse = await apiClient.get<BackendEventQueryResponse>(
      "/events",
      { params: queryParams },
    );
    const response = EventQueryResponseSchema.parse(rawResponse);

    return {
      events: response.events.map((event) =>
        this.toEventEntry(event, response.tenantId ?? tenantId),
      ),
      total: response.count,
      hasMore: typeof limit === "number" ? response.count >= limit : false,
    };
  }

  /** Get event statistics */
  async getStats(tenantId?: string): Promise<EventStats> {
    const response = await this.listEvents({ tenantId, limit: 1000 });
    const byTier: Record<EventTier, number> = {
      HOT: 0,
      WARM: 0,
      COOL: 0,
      COLD: 0,
    };
    const byType: Record<string, number> = {};

    for (const event of response.events) {
      byTier[event.tier] = (byTier[event.tier] ?? 0) + 1;
      byType[event.eventType] = (byType[event.eventType] ?? 0) + 1;
    }

    return {
      total: response.total,
      byTier,
      byType,
      eventsPerMinute: response.events.length,
    };
  }

  /** Open an SSE stream for live event tailing */
  openStream(params: EventQueryParams = {}): EventSource {
    const search = new URLSearchParams();
    if (params.tenantId) search.set("tenantId", params.tenantId);
    if (params.eventType) search.set("types", params.eventType);
    const query = search.toString();
    return new EventSource(
      `${import.meta.env.VITE_API_URL ?? "/api/v1"}/events/stream${query ? `?${query}` : ""}`,
    );
  }

  parseLiveEvent(message: string, tenantId?: string): EventEntry {
    return this.toEventEntry(
      EventSchema.parse(JSON.parse(message) as unknown),
      tenantId,
    );
  }
}

export const eventsService = new EventsService();
