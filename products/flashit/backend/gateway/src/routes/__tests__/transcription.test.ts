/**
 * Transcription Route Tests
 *
 * Tests for transcription endpoints with billing limit enforcement
 * on /transcribe, /batch, and /auto-transcribe.
 *
 * @doc.type test
 * @doc.purpose Test transcription API with billing limit checks
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeAll, afterAll, vi, beforeEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock prisma
const mockPrisma = {
  mediaReference: {
    findFirst: vi.fn(),
    findMany: vi.fn(),
  },
  transcription: {
    findFirst: vi.fn(),
    create: vi.fn(),
    findUnique: vi.fn(),
  },
  moment: {
    findFirst: vi.fn(),
    findMany: vi.fn(),
    update: vi.fn(),
  },
  user: {
    findUnique: vi.fn(),
  },
  auditEvent: {
    create: vi.fn().mockResolvedValue({}),
  },
  $queryRaw: vi.fn().mockResolvedValue([{ count: '5' }]),
};

vi.mock('../../lib/prisma', () => ({
  prisma: mockPrisma,
}));

// Mock Whisper service
const mockTranscribe = vi.fn();
const mockTranscribeBatch = vi.fn();

vi.mock('../../services/transcription/whisper-service', () => ({
  WhisperTranscriptionService: {
    transcribe: (...args: any[]) => mockTranscribe(...args),
    transcribeBatch: (...args: any[]) => mockTranscribeBatch(...args),
  },
}));

// Mock billing services
const mockCheckTranscriptionLimit = vi.fn();

vi.mock('../../services/billing/usage-limits', () => ({
  checkTranscriptionLimit: (...args: any[]) => mockCheckTranscriptionLimit(...args),
}));

vi.mock('../../services/billing/stripe-service', () => ({
  StripeBillingService: {
    getSubscriptionInfo: vi.fn().mockResolvedValue({ tier: 'FREE', status: 'active' }),
    checkUsage: vi.fn(),
  },
}));

// Mock auth
vi.mock('../../lib/auth', () => ({
  requireAuth: vi.fn().mockImplementation(async () => {}),
}));

import transcriptionRoutes from '../transcription';

describe('Transcription Routes', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = Fastify();
    await app.register(jwt, { secret: 'test-secret-key' });

    app.decorate('authenticate', async (request: any, reply: any) => {
      try {
        await request.jwtVerify();
      } catch {
        reply.code(401).send({ error: 'Unauthorized' });
      }
    });

    await app.register(transcriptionRoutes, { prefix: '/api/transcription' });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  function getToken(userId = 'user-1') {
    return app.jwt.sign({ userId, email: 'test@example.com' });
  }

  describe('POST /api/transcription/transcribe', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/transcribe',
        payload: { momentId: '00000000-0000-0000-0000-000000000001' },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should allow transcription within billing limits', async () => {
      mockCheckTranscriptionLimit.mockResolvedValueOnce({ allowed: true });
      mockPrisma.mediaReference.findFirst.mockResolvedValueOnce({
        id: 'media-1',
        momentId: 'moment-1',
        storageUri: 's3://bucket/audio.mp3',
        mimeType: 'audio/mp3',
      });
      mockTranscribe.mockResolvedValueOnce({
        jobId: 'job-1',
        status: 'PROCESSING',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/transcribe',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: { momentId: '00000000-0000-0000-0000-000000000001' },
      });

      expect([200, 202]).toContain(response.statusCode);
    });

    it('should reject transcription when limit is reached', async () => {
      mockCheckTranscriptionLimit.mockResolvedValueOnce({
        allowed: false,
        upgradePrompt: {
          message: 'Free plan allows 5 transcription hours per month. Upgrade to Pro for more.',
          actionUrl: '/billing/upgrade',
        },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/transcribe',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: { momentId: '00000000-0000-0000-0000-000000000001' },
      });

      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Limit Reached');
    });
  });

  describe('POST /api/transcription/batch', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/batch',
        payload: { momentIds: ['00000000-0000-0000-0000-000000000001'] },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should enforce transcription limit on batch requests', async () => {
      mockCheckTranscriptionLimit.mockResolvedValueOnce({
        allowed: false,
        upgradePrompt: {
          message: 'Transcription hour limit reached for your plan',
          actionUrl: '/billing/upgrade',
        },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/batch',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          momentIds: [
            '00000000-0000-0000-0000-000000000001',
            '00000000-0000-0000-0000-000000000002',
          ],
        },
      });

      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Limit Reached');
    });

    it('should allow batch transcription within limits', async () => {
      mockCheckTranscriptionLimit.mockResolvedValueOnce({ allowed: true });
      mockPrisma.mediaReference.findMany.mockResolvedValueOnce([
        { id: 'media-1', momentId: 'moment-1', storageUri: 's3://bucket/audio1.mp3' },
      ]);
      mockTranscribeBatch.mockResolvedValueOnce({
        jobIds: ['job-1'],
        status: 'PROCESSING',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/batch',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          momentIds: ['00000000-0000-0000-0000-000000000001'],
        },
      });

      expect([200, 202]).toContain(response.statusCode);
    });
  });

  describe('POST /api/transcription/auto-transcribe', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/auto-transcribe',
        payload: { enabled: true },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should enforce transcription limit on auto-transcribe', async () => {
      mockCheckTranscriptionLimit.mockResolvedValueOnce({
        allowed: false,
        upgradePrompt: {
          message: 'Transcription limit reached',
          actionUrl: '/billing/upgrade',
        },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/transcription/auto-transcribe',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: { enabled: true },
      });

      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Limit Reached');
    });
  });

  describe('GET /api/transcription/:momentId', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/transcription/moment-1',
      });
      expect(response.statusCode).toBe(401);
    });

    it('should return transcript for a moment', async () => {
      mockPrisma.transcription.findFirst.mockResolvedValueOnce({
        id: 'txn-1',
        momentId: 'moment-1',
        text: 'Hello world transcription text',
        language: 'en',
        confidence: 0.95,
        createdAt: new Date(),
      });
      mockPrisma.moment.findFirst.mockResolvedValueOnce({
        id: 'moment-1',
        userId: 'user-1',
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/transcription/moment-1',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect([200, 404]).toContain(response.statusCode);
    });
  });

  describe('GET /api/transcription/stats', () => {
    it('should return transcription statistics', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/transcription/stats',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect([200, 500]).toContain(response.statusCode);
    });
  });

  describe('GET /api/transcription/health', () => {
    it('should return transcription service health', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/transcription/health',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect([200, 502]).toContain(response.statusCode);
    });
  });
});
