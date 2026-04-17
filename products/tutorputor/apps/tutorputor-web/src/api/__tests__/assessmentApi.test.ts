/**
 * Assessment API Client Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for assessment API client
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { assessmentApi, type AssessmentListItem } from '../assessmentApi';

const API_BASE_URL = 'http://localhost:3000';

describe('assessmentApi', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    global.fetch = fetchMock;
    
    // Mock localStorage
    vi.stubGlobal('localStorage', {
      getItem: vi.fn().mockReturnValue('test-token-123'),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('listAssessments', () => {
    it('should list assessments without filter', async () => {
      const mockResponse: { items: AssessmentListItem[]; nextCursor: null } = {
        items: [
          {
            id: 'assess-1',
            title: 'Physics Quiz 1',
            description: 'Basic physics concepts',
            status: 'ACTIVE',
            itemCount: 10,
            timeLimitMinutes: 30,
            createdAt: '2024-01-15T10:00:00Z',
            updatedAt: '2024-01-15T10:00:00Z',
          },
        ],
        nextCursor: null,
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      } as Response);

      const result = await assessmentApi.listAssessments();

      expect(fetchMock).toHaveBeenCalledWith(
        `${API_BASE_URL}/api/v1/assessments?`,
        expect.objectContaining({
          method: 'GET',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
            'Authorization': 'Bearer test-token-123',
          }),
        }),
      );
      expect(result.items).toHaveLength(1);
      expect(result.items[0].title).toBe('Physics Quiz 1');
    });

    it('should list assessments with status filter', async () => {
      const mockResponse = {
        items: [],
        nextCursor: null,
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      } as Response);

      await assessmentApi.listAssessments({ status: 'ACTIVE', limit: 20 });

      expect(fetchMock).toHaveBeenCalledWith(
        `${API_BASE_URL}/api/v1/assessments?status=ACTIVE&limit=20`,
        expect.any(Object),
      );
    });

    it('should handle API errors', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: async () => 'Server error',
      } as Response);

      await expect(assessmentApi.listAssessments()).rejects.toThrow(
        'API error 500: Internal Server Error',
      );
    });

    it('should work without auth token', async () => {
      vi.stubGlobal('localStorage', {
        getItem: vi.fn().mockReturnValue(null),
        setItem: vi.fn(),
        removeItem: vi.fn(),
      });

      const mockResponse = { items: [], nextCursor: null };
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      } as Response);

      await assessmentApi.listAssessments();

      expect(fetchMock).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
        }),
      );
    });
  });

  describe('getAssessment', () => {
    it('should fetch single assessment', async () => {
      const mockAssessment = {
        id: 'assess-1',
        title: 'Physics Quiz',
        status: 'ACTIVE',
        items: [
          {
            id: 'item-1',
            itemType: 'MULTIPLE_CHOICE',
            prompt: 'What is gravity?',
            points: 10,
          },
        ],
        allowRetries: true,
        createdAt: '2024-01-15T10:00:00Z',
        updatedAt: '2024-01-15T10:00:00Z',
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockAssessment,
      } as Response);

      const result = await assessmentApi.getAssessment('assess-1');

      expect(fetchMock).toHaveBeenCalledWith(
        `${API_BASE_URL}/api/v1/assessments/assess-1`,
        expect.objectContaining({ method: 'GET' }),
      );
      expect(result.id).toBe('assess-1');
      expect(result.items).toHaveLength(1);
    });

    it('should handle 404 for non-existent assessment', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        text: async () => 'Assessment not found',
      } as Response);

      await expect(assessmentApi.getAssessment('non-existent')).rejects.toThrow(
        'API error 404: Not Found',
      );
    });
  });

  describe('startAttempt', () => {
    it('should start an assessment attempt', async () => {
      const mockResponse = {
        attemptId: 'attempt-123',
        assessment: { id: 'assess-1', title: 'Quiz' },
        startedAt: '2024-01-15T10:00:00Z',
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      } as Response);

      const result = await assessmentApi.startAttempt('assess-1');

      expect(fetchMock).toHaveBeenCalledWith(
        `${API_BASE_URL}/api/v1/assessments/assess-1/attempt`,
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Authorization': 'Bearer test-token-123',
          }),
        }),
      );
      expect(result.attemptId).toBe('attempt-123');
    });
  });

  describe('submitAttempt', () => {
    it('should submit attempt with responses', async () => {
      const mockResponse = {
        attemptId: 'attempt-123',
        status: 'GRADED',
        score: 85,
        maxScore: 100,
        feedback: [
          {
            itemId: 'item-1',
            correct: true,
            points: 10,
            feedback: 'Correct!',
          },
        ],
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      } as Response);

      const responses = [
        {
          itemId: 'item-1',
          response: { choiceId: 'choice-a' },
          confidence: 0.9,
          timeSpentSeconds: 60,
        },
      ];

      const result = await assessmentApi.submitAttempt('attempt-123', responses);

      expect(fetchMock).toHaveBeenCalledWith(
        `${API_BASE_URL}/api/v1/attempts/attempt-123/submit`,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ responses }),
        }),
      );
      expect(result.score).toBe(85);
      expect(result.status).toBe('GRADED');
    });
  });

  describe('getMyAttempts', () => {
    it('should fetch user attempts', async () => {
      const mockResponse = [
        {
          attemptId: 'attempt-1',
          assessmentId: 'assess-1',
          assessmentTitle: 'Physics Quiz',
          status: 'GRADED',
          score: 85,
          maxScore: 100,
          startedAt: '2024-01-15T10:00:00Z',
          submittedAt: '2024-01-15T10:30:00Z',
        },
      ];

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      } as Response);

      const result = await assessmentApi.getMyAttempts();

      expect(result).toHaveLength(1);
      expect(result[0].assessmentTitle).toBe('Physics Quiz');
    });

    it('should filter by assessment ID', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      } as Response);

      await assessmentApi.getMyAttempts('assess-1');

      expect(fetchMock).toHaveBeenCalledWith(
        `${API_BASE_URL}/api/v1/learning/attempts?assessmentId=assess-1`,
        expect.any(Object),
      );
    });
  });
});
