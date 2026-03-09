/**
 * Web API Client for Flashit
 * Extends shared FlashitApiClient with web-specific endpoints
 */

import { FlashitApiClient } from '@ghatana/flashit-shared';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:2900';

/**
 * Extended API client for web app with additional endpoints
 * for moment links, memory expansion, and analytics
 */
class WebApiClient extends FlashitApiClient {
  constructor() {
    super({
      baseURL: API_BASE_URL,
      getToken: async () => {
        const token = localStorage.getItem('flashit_token');
        return token;
      },
      onTokenChange: async (token) => {
        if (token) {
          localStorage.setItem('flashit_token', token);
        } else {
          localStorage.removeItem('flashit_token');
        }
      },
      onUnauthorized: () => {
        window.location.href = '/login';
      },
    });
  }

  // Web-specific: Get spheres with additional params
  async getSpheres(ownedOnly = false) {
    const response = await this.request<{ spheres: any[] }>('/api/spheres', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
    return response;
  }

  // Web-specific: Update sphere (PATCH instead of PUT)
  async updateSphere(id: string, data: Partial<{
    name: string;
    description: string;
    visibility: string;
  }>) {
    const response = await this.request(`/api/spheres/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
    return response;
  }

  // Web-specific: Classify sphere with AI
  async classifySphereAI(data: {
    content: {
      text: string;
      transcript?: string;
      type: 'TEXT' | 'VOICE' | 'VIDEO' | 'IMAGE' | 'MIXED';
    };
    signals?: {
      emotions?: string[];
      tags?: string[];
      intent?: string;
      sentimentScore?: number;
      importance?: number;
      entities?: string[];
    };
  }) {
    return this.classifySphere(data);
  }

  // Web-specific: Search moments (uses hybrid AI search when query is provided)
  async searchMoments(params?: {
    sphereIds?: string[];
    query?: string;
    tags?: string[];
    emotions?: string[];
    startDate?: string;
    endDate?: string;
    limit?: number;
    cursor?: string;
  }) {
    // If there's a text query, use the hybrid search API for AI-powered results
    if (params?.query && params.query.trim().length > 0) {
      return this.request('/api/search', {
        method: 'POST',
        body: JSON.stringify({
          query: params.query,
          type: 'hybrid',
          filters: {
            sphereIds: params.sphereIds,
            tags: params.tags,
            emotions: params.emotions,
            dateRange: params.startDate || params.endDate
              ? {
                  from: params.startDate ? new Date(params.startDate).toISOString() : undefined,
                  to: params.endDate ? new Date(params.endDate).toISOString() : undefined,
                }
              : undefined,
          },
          limit: params.limit || 20,
          offset: 0,
          includeHighlights: true,
        }),
      });
    }
    // Fallback to standard moment listing for non-search filtering
    return this.getMoments(params);
  }

  // Moment Links (Temporal Arcs) endpoints
  async createMomentLink(momentId: string, data: {
    targetMomentId: string;
    linkType: string;
    metadata?: Record<string, unknown>;
  }) {
    return this.request(`/api/moments/${momentId}/links`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getMomentLinks(momentId: string, params?: {
    direction?: 'outgoing' | 'incoming' | 'both';
    linkType?: string;
    limit?: number;
  }) {
    const queryParams = new URLSearchParams();
    if (params?.direction) queryParams.append('direction', params.direction);
    if (params?.linkType) queryParams.append('linkType', params.linkType);
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    const query = queryParams.toString();
    return this.request(`/api/moments/${momentId}/links${query ? `?${query}` : ''}`);
  }

  async deleteMomentLink(momentId: string, linkId: string) {
    return this.request(`/api/moments/${momentId}/links/${linkId}`, {
      method: 'DELETE',
    });
  }

  async getLinkGraph(sphereId: string, params?: {
    depth?: number;
    linkTypes?: string;
    limit?: number;
  }) {
    const queryParams = new URLSearchParams();
    if (params?.depth) queryParams.append('depth', params.depth.toString());
    if (params?.linkTypes) queryParams.append('linkTypes', params.linkTypes);
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    const query = queryParams.toString();
    return this.request(`/api/spheres/${sphereId}/link-graph${query ? `?${query}` : ''}`);
  }

  async getLinkSuggestions(momentId: string, params?: {
    limit?: number;
    threshold?: number;
  }) {
    const queryParams = new URLSearchParams();
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    if (params?.threshold) queryParams.append('threshold', params.threshold.toString());
    const query = queryParams.toString();
    return this.request(`/api/moment-links/suggestions/${momentId}${query ? `?${query}` : ''}`);
  }

  async getMomentLinkStats(sphereId: string) {
    return this.request(`/api/moment-links/stats/${sphereId}`);
  }

  async getMomentLinkTimeline(sphereId: string, params?: {
    startDate?: string;
    endDate?: string;
    linkTypes?: string[];
    groupBy?: 'day' | 'week' | 'month';
  }) {
    const queryParams = new URLSearchParams();
    if (params?.startDate) queryParams.append('startDate', params.startDate);
    if (params?.endDate) queryParams.append('endDate', params.endDate);
    if (params?.linkTypes) queryParams.append('linkTypes', params.linkTypes.join(','));
    if (params?.groupBy) queryParams.append('groupBy', params.groupBy);
    const query = queryParams.toString();
    return this.request(`/api/moment-links/timeline/${sphereId}${query ? `?${query}` : ''}`);
  }

  // Meaning Metrics endpoints
  async getMeaningMetrics(params?: {
    sphereId?: string;
    startDate?: string;
    endDate?: string;
  }) {
    const queryParams = new URLSearchParams();
    if (params?.sphereId) queryParams.append('sphereId', params.sphereId);
    if (params?.startDate) queryParams.append('startDate', params.startDate);
    if (params?.endDate) queryParams.append('endDate', params.endDate);
    const query = queryParams.toString();
    return this.request(`/api/analytics/meaning-metrics${query ? `?${query}` : ''}`);
  }

  async compareMeaningMetrics(params: {
    period1Start: string;
    period1End: string;
    period2Start: string;
    period2End: string;
    sphereId?: string;
  }) {
    const queryParams = new URLSearchParams();
    queryParams.append('period1Start', params.period1Start);
    queryParams.append('period1End', params.period1End);
    queryParams.append('period2Start', params.period2Start);
    queryParams.append('period2End', params.period2End);
    if (params.sphereId) queryParams.append('sphereId', params.sphereId);
    return this.request(`/api/analytics/meaning-metrics/compare?${queryParams.toString()}`);
  }

  async recordMeaningEvent(data: {
    eventType: string;
    momentId?: string;
    sphereId?: string;
    metadata?: Record<string, unknown>;
  }) {
    return this.request('/api/analytics/meaning-metrics/events', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // Language Evolution endpoints
  async getLanguageEvolution(params?: {
    periodDays?: number;
    sphereId?: string;
  }) {
    const queryParams = new URLSearchParams();
    if (params?.periodDays) queryParams.append('periodDays', params.periodDays.toString());
    if (params?.sphereId) queryParams.append('sphereId', params.sphereId);
    const query = queryParams.toString();
    return this.request(`/api/analytics/meaning/language-evolution${query ? `?${query}` : ''}`);
  }

  async getReturnToMeaningRate(params?: {
    periodDays?: number;
    sphereId?: string;
  }) {
    const queryParams = new URLSearchParams();
    if (params?.periodDays) queryParams.append('periodDays', params.periodDays.toString());
    if (params?.sphereId) queryParams.append('sphereId', params.sphereId);
    const query = queryParams.toString();
    return this.request(`/api/analytics/meaning/return-to-meaning-rate${query ? `?${query}` : ''}`);
  }

  async getCrossTimeReferencing(params?: {
    periodDays?: number;
    sphereId?: string;
  }) {
    const queryParams = new URLSearchParams();
    if (params?.periodDays) queryParams.append('periodDays', params.periodDays.toString());
    if (params?.sphereId) queryParams.append('sphereId', params.sphereId);
    const query = queryParams.toString();
    return this.request(`/api/analytics/meaning/cross-time-referencing${query ? `?${query}` : ''}`);
  }

  // Memory Expansion endpoints
  async requestMemoryExpansion(data: {
    sphereId?: string;
    momentIds?: string[];
    expansionType: 'summarize' | 'extract_themes' | 'identify_patterns' | 'find_connections';
    timeRange?: {
      startDate: string;
      endDate: string;
    };
    priority?: 'high' | 'normal' | 'low';
  }) {
    return this.request('/api/memory-expansion', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getExpansionResult(jobId: string) {
    return this.request(`/api/memory-expansion/${jobId}`);
  }

  async getUserExpansions(params?: { limit?: number }) {
    const queryParams = new URLSearchParams();
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    const query = queryParams.toString();
    return this.request(`/api/memory-expansion${query ? `?${query}` : ''}`);
  }

  async getExpansionById(expansionId: string) {
    return this.request(`/api/memory-expansion/result/${expansionId}`);
  }

  async requestBatchExpansions(requests: Array<{
    sphereId?: string;
    momentIds?: string[];
    expansionType: 'summarize' | 'extract_themes' | 'identify_patterns' | 'find_connections';
    timeRange?: {
      startDate: string;
      endDate: string;
    };
    priority?: 'high' | 'normal' | 'low';
  }>) {
    return this.request('/api/memory-expansion/batch', {
      method: 'POST',
      body: JSON.stringify({ requests }),
    });
  }
}

export const apiClient = new WebApiClient();

