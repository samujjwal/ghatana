/**
 * Assessment API Client
 *
 * Provides type-safe access to the assessment endpoints.
 *
 * @doc.type module
 * @doc.purpose Frontend API client for assessment operations
 * @doc.layer product
 * @doc.pattern API Client
 */

import { createLogger } from '@/utils/logger.js';
import { readAccessToken } from '@tutorputor/ui';

const logger = createLogger('assessmentApi');

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000';

/**
 * Assessment status values
 */
export type AssessmentStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED' | 'GRADED';

/**
 * Assessment list item for display
 */
export interface AssessmentListItem {
  id: string;
  title: string;
  description?: string;
  status: AssessmentStatus;
  itemCount: number;
  timeLimitMinutes?: number;
  moduleId?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Paginated assessment list response
 */
export interface AssessmentListResponse {
  items: AssessmentListItem[];
  nextCursor: string | null;
}

/**
 * Assessment item (question)
 */
export interface AssessmentItem {
  id: string;
  itemType: 'MULTIPLE_CHOICE' | 'SIMULATION' | 'FREE_RESPONSE' | 'NUMERIC';
  prompt: string;
  points: number;
  choices?: Array<{
    id: string;
    text: string;
    isCorrect?: boolean;
  }>;
  metadata?: Record<string, unknown>;
}

/**
 * Full assessment details
 */
export interface Assessment {
  id: string;
  title: string;
  description?: string;
  status: AssessmentStatus;
  moduleId?: string;
  items: AssessmentItem[];
  timeLimitMinutes?: number;
  passingScore?: number;
  allowRetries: boolean;
  maxAttempts?: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Filter options for listing assessments
 */
export interface AssessmentFilter {
  status?: AssessmentStatus;
  moduleId?: string;
  limit?: number;
  cursor?: string;
}

/**
 * Get auth token from canonical source
 */
function getAuthToken(): string | null {
  return readAccessToken();
}

/**
 * Get default headers with auth
 */
function getHeaders(): HeadersInit {
  const token = getAuthToken();
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

/**
 * Handle API errors consistently
 */
async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text().catch(() => 'Unknown error');
    logger.error('API error:', {
      status: response.status,
      statusText: response.statusText,
      body: errorText,
    });
    throw new Error(`API error ${response.status}: ${response.statusText}`);
  }
  return response.json();
}

/**
 * Assessment API client
 */
export const assessmentApi = {
  /**
   * List assessments with optional filtering
   */
  async listAssessments(filter: AssessmentFilter = {}): Promise<AssessmentListResponse> {
    const params = new URLSearchParams();
    if (filter.status) params.set('status', filter.status);
    if (filter.moduleId) params.set('moduleId', filter.moduleId);
    if (filter.limit) params.set('limit', filter.limit.toString());
    if (filter.cursor) params.set('cursor', filter.cursor);

    const url = `${API_BASE_URL}/api/v1/assessments?${params.toString()}`;
    logger.debug('Listing assessments:', { filter });

    const response = await fetch(url, {
      method: 'GET',
      headers: getHeaders(),
    });

    return handleResponse<AssessmentListResponse>(response);
  },

  /**
   * Get a single assessment by ID
   */
  async getAssessment(id: string): Promise<Assessment> {
    const url = `${API_BASE_URL}/api/v1/assessments/${id}`;
    logger.debug('Getting assessment:', { id });

    const response = await fetch(url, {
      method: 'GET',
      headers: getHeaders(),
    });

    return handleResponse<Assessment>(response);
  },

  /**
   * Start an assessment attempt
   */
  async startAttempt(assessmentId: string): Promise<{
    attemptId: string;
    assessment: Assessment;
    startedAt: string;
  }> {
    const url = `${API_BASE_URL}/api/v1/assessments/${assessmentId}/attempt`;
    logger.debug('Starting attempt:', { assessmentId });

    const response = await fetch(url, {
      method: 'POST',
      headers: getHeaders(),
    });

    return handleResponse(response);
  },

  /**
   * Submit an assessment attempt
   */
  async submitAttempt(
    attemptId: string,
    responses: Array<{
      itemId: string;
      response: unknown;
      confidence?: number;
      timeSpentSeconds?: number;
    }>,
  ): Promise<{
    attemptId: string;
    status: 'SUBMITTED' | 'GRADED';
    score?: number;
    maxScore: number;
    feedback?: Array<{
      itemId: string;
      correct: boolean;
      points: number;
      feedback: string;
    }>;
  }> {
    const url = `${API_BASE_URL}/api/v1/attempts/${attemptId}/submit`;
    logger.debug('Submitting attempt:', { attemptId, responseCount: responses.length });

    const response = await fetch(url, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ responses }),
    });

    return handleResponse(response);
  },

  /**
   * Get attempt history for current user
   */
  async getMyAttempts(assessmentId?: string): Promise<
    Array<{
      attemptId: string;
      assessmentId: string;
      assessmentTitle: string;
      status: string;
      score?: number;
      maxScore: number;
      startedAt: string;
      submittedAt?: string;
    }>
  > {
    const params = new URLSearchParams();
    if (assessmentId) params.set('assessmentId', assessmentId);

    const url = `${API_BASE_URL}/api/v1/learning/attempts?${params.toString()}`;
    logger.debug('Getting my attempts:', { assessmentId });

    const response = await fetch(url, {
      method: 'GET',
      headers: getHeaders(),
    });

    return handleResponse(response);
  },
};

export default assessmentApi;
