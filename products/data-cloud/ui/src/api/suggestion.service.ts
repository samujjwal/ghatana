/**
 * Suggestion service for AI-powered workflow recommendations.
 *
 * <p><b>Purpose</b><br>
 * Provides API integration for fetching AI suggestions.
 * Includes caching, history tracking, and quality metrics.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { suggestionService } from '@/api/suggestion.service';
 *
 * // Get suggestions
 * const response = await suggestionService.getSuggestions(
 *   workflowId,
 *   'User wants to filter by price > 100',
 *   'field'
 * );
 *
 * // Accept suggestion
 * await suggestionService.acceptSuggestion(workflowId, suggestionId);
 *
 * // Reject suggestion
 * await suggestionService.rejectSuggestion(workflowId, suggestionId, 'Not relevant');
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Fetch AI suggestions from backend
 * - Cache suggestions
 * - Track suggestion history
 * - Rate suggestion quality
 * - Accept/reject feedback
 *
 * @doc.type service
 * @doc.purpose AI suggestion management
 * @doc.layer frontend
 */

export interface Suggestion {
  id: string;
  text: string;
  confidence: number;
  type: 'field' | 'transformation' | 'validation';
  explanation?: string;
}

export interface SuggestionResponse {
  suggestions: Suggestion[];
  overallConfidence: number;
}

export interface SuggestionFeedback {
  suggestionId: string;
  workflowId: string;
  accepted: boolean;
  reason?: string;
  timestamp: number;
}

const API_BASE = '/api/v1';
const CACHE_TTL = 10 * 60 * 1000; // 10 minutes

interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

class SuggestionService {
  private suggestionCache: Map<string, CacheEntry<SuggestionResponse>> = new Map();
  private feedbackHistory: SuggestionFeedback[] = [];

  /**
   * Gets suggestions for a workflow.
   *
   * <p>GIVEN: A workflow ID and suggestion context
   * WHEN: getSuggestions() is called
   * THEN: Returns AI-powered suggestions from backend
   *
   * @param workflowId the workflow identifier
   * @param context the suggestion context
   * @param type the suggestion type
   * @returns suggestion response
   * @throws Error if fetch fails
   */
  async getSuggestions(
    workflowId: string,
    context: string,
    type: 'field' | 'transformation' | 'validation'
  ): Promise<SuggestionResponse> {
    const cacheKey = `${workflowId}:${context}:${type}`;

    // Check cache
    const cached = this.suggestionCache.get(cacheKey);
    if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
      console.debug('Suggestion cache hit:', cacheKey);
      return cached.data;
    }

    // Fetch from API
    try {
      const response = await fetch(`${API_BASE}/workflows/${workflowId}/suggestions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': this.getTenantId(),
        },
        body: JSON.stringify({ context, type }),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch suggestions: ${response.statusText}`);
      }

      const data = (await response.json()) as SuggestionResponse;

      // Cache result
      this.suggestionCache.set(cacheKey, {
        data,
        timestamp: Date.now(),
      });

      console.debug('Suggestions fetched and cached:', cacheKey);
      return data;
    } catch (error) {
      console.error('Error fetching suggestions:', error);
      throw new Error(
        `Failed to fetch suggestions: ${error instanceof Error ? error.message : 'Unknown error'}`
      );
    }
  }

  /**
   * Accepts a suggestion.
   *
   * <p>GIVEN: A workflow ID and suggestion ID
   * WHEN: acceptSuggestion() is called
   * THEN: Suggestion is accepted and applied
   *
   * @param workflowId the workflow identifier
   * @param suggestionId the suggestion identifier
   * @returns void
   * @throws Error if request fails
   */
  async acceptSuggestion(workflowId: string, suggestionId: string): Promise<void> {
    try {
      const response = await fetch(
        `${API_BASE}/workflows/${workflowId}/suggestions/${suggestionId}/accept`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Tenant-ID': this.getTenantId(),
          },
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to accept suggestion: ${response.statusText}`);
      }

      // Record feedback
      this.recordFeedback(workflowId, suggestionId, true);
      console.debug('Suggestion accepted:', suggestionId);
    } catch (error) {
      console.error('Error accepting suggestion:', error);
      throw new Error(
        `Failed to accept suggestion: ${error instanceof Error ? error.message : 'Unknown error'}`
      );
    }
  }

  /**
   * Rejects a suggestion.
   *
   * <p>GIVEN: A workflow ID, suggestion ID, and optional reason
   * WHEN: rejectSuggestion() is called
   * THEN: Suggestion is rejected and feedback recorded
   *
   * @param workflowId the workflow identifier
   * @param suggestionId the suggestion identifier
   * @param reason the rejection reason (optional)
   * @returns void
   * @throws Error if request fails
   */
  async rejectSuggestion(
    workflowId: string,
    suggestionId: string,
    reason?: string
  ): Promise<void> {
    try {
      const url = new URL(`${API_BASE}/workflows/${workflowId}/suggestions/${suggestionId}/reject`, window.location.origin);
      if (reason) {
        url.searchParams.append('reason', reason);
      }

      const response = await fetch(url.toString(), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': this.getTenantId(),
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to reject suggestion: ${response.statusText}`);
      }

      // Record feedback
      this.recordFeedback(workflowId, suggestionId, false, reason);
      console.debug('Suggestion rejected:', suggestionId);
    } catch (error) {
      console.error('Error rejecting suggestion:', error);
      throw new Error(
        `Failed to reject suggestion: ${error instanceof Error ? error.message : 'Unknown error'}`
      );
    }
  }

  /**
   * Records suggestion feedback.
   *
   * @param workflowId the workflow identifier
   * @param suggestionId the suggestion identifier
   * @param accepted whether suggestion was accepted
   * @param reason optional feedback reason
   */
  private recordFeedback(
    workflowId: string,
    suggestionId: string,
    accepted: boolean,
    reason?: string
  ): void {
    const feedback: SuggestionFeedback = {
      suggestionId,
      workflowId,
      accepted,
      reason,
      timestamp: Date.now(),
    };

    this.feedbackHistory.push(feedback);

    // Keep only last 100 feedback entries
    if (this.feedbackHistory.length > 100) {
      this.feedbackHistory = this.feedbackHistory.slice(-100);
    }
  }

  /**
   * Gets suggestion feedback history.
   *
   * @returns feedback history
   */
  getFeedbackHistory(): SuggestionFeedback[] {
    return [...this.feedbackHistory];
  }

  /**
   * Gets suggestion quality metrics.
   *
   * @returns quality metrics
   */
  getQualityMetrics(): {
    totalFeedback: number;
    acceptanceRate: number;
    rejectionRate: number;
  } {
    if (this.feedbackHistory.length === 0) {
      return {
        totalFeedback: 0,
        acceptanceRate: 0,
        rejectionRate: 0,
      };
    }

    const accepted = this.feedbackHistory.filter((f) => f.accepted).length;
    const rejected = this.feedbackHistory.length - accepted;

    return {
      totalFeedback: this.feedbackHistory.length,
      acceptanceRate: accepted / this.feedbackHistory.length,
      rejectionRate: rejected / this.feedbackHistory.length,
    };
  }

  /**
   * Clears suggestion cache.
   */
  clearCache(): void {
    this.suggestionCache.clear();
    console.debug('Suggestion cache cleared');
  }

  /**
   * Clears feedback history.
   */
  clearFeedbackHistory(): void {
    this.feedbackHistory = [];
    console.debug('Feedback history cleared');
  }

  /**
   * Gets cache statistics.
   *
   * @returns cache statistics
   */
  getCacheStats(): {
    cacheSize: number;
    feedbackCount: number;
  } {
    return {
      cacheSize: this.suggestionCache.size,
      feedbackCount: this.feedbackHistory.length,
    };
  }

  /**
   * Gets tenant ID from localStorage or session.
   *
   * @returns tenant ID
   */
  private getTenantId(): string {
    return localStorage.getItem('tenantId') || 'default-tenant';
  }
}

// Export singleton instance
export const suggestionService = new SuggestionService();

export default suggestionService;
