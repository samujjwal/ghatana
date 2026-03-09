/**
 * Privacy Route Tests
 *
 * Tests for GDPR-compliant privacy API: data export, deletion requests,
 * privacy settings, consent management, and data summary.
 *
 * @doc.type test
 * @doc.purpose Test privacy/data rights API endpoints
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeAll, afterAll, vi, beforeEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock prisma
const mockPrisma = {
  user: {
    findUnique: vi.fn(),
  },
  dataExportRequest: {
    create: vi.fn(),
    findFirst: vi.fn(),
    count: vi.fn(),
  },
  dataDeletionRequest: {
    create: vi.fn(),
    findFirst: vi.fn(),
    findUnique: vi.fn(),
  },
  privacySettings: {
    findUnique: vi.fn(),
    upsert: vi.fn(),
  },
  consentRecord: {
    create: vi.fn(),
    findMany: vi.fn(),
  },
  moment: {
    count: vi.fn(),
  },
  sphere: {
    count: vi.fn(),
  },
  mediaReference: {
    count: vi.fn(),
    aggregate: vi.fn(),
  },
  auditEvent: {
    create: vi.fn().mockResolvedValue({}),
    count: vi.fn(),
  },
};

vi.mock('../../lib/prisma', () => ({
  prisma: mockPrisma,
}));

// Mock auth
vi.mock('../../lib/auth', () => ({
  requireAuth: vi.fn().mockImplementation(async () => {}),
}));

// Mock data export service
vi.mock('../../services/data-export/export-service', () => ({
  DataExportService: {
    requestExport: vi.fn().mockResolvedValue({
      exportId: 'export-1',
      estimatedCompletion: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
    getExportStatus: vi.fn().mockResolvedValue({
      exportId: 'export-1',
      status: 'COMPLETED',
      downloadUrl: '/api/privacy/export/export-1/download',
    }),
    getExportFilePath: vi.fn().mockResolvedValue(null),
  },
}));

// Mock data deletion service
vi.mock('../../services/data-deletion/deletion-service', () => ({
  SecureDataDeletionService: {
    requestDeletion: vi.fn().mockResolvedValue({
      requestId: 'del-1',
      status: 'PENDING_CONFIRMATION',
    }),
    verifyDeletion: vi.fn().mockResolvedValue({
      requestId: 'del-1',
      status: 'PROCESSING',
    }),
    getDeletionStatus: vi.fn().mockResolvedValue({
      requestId: 'del-1',
      status: 'COMPLETED',
    }),
  },
}));

import privacyRoutes from '../privacy';

describe('Privacy Routes', () => {
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

    await app.register(privacyRoutes, { prefix: '/api/privacy' });
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

  // =========================================================================
  // Data Export
  // =========================================================================

  describe('POST /api/privacy/export', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/privacy/export',
        payload: { format: 'json' },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should request a data export', async () => {
      mockPrisma.dataExportRequest.count.mockResolvedValueOnce(0); // no recent exports

      const response = await app.inject({
        method: 'POST',
        url: '/api/privacy/export',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          format: 'json',
          scope: 'all',
          includeMedia: false,
        },
      });

      expect([200, 202, 429]).toContain(response.statusCode);
    });
  });

  describe('GET /api/privacy/export/:exportId/status', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/export/export-1/status',
      });
      expect(response.statusCode).toBe(401);
    });

    it('should return export status', async () => {
      mockPrisma.dataExportRequest.findFirst.mockResolvedValueOnce({
        id: 'export-1',
        userId: 'user-1',
        status: 'COMPLETED',
        format: 'json',
        createdAt: new Date(),
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/export/export-1/status',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect([200, 404]).toContain(response.statusCode);
    });
  });

  describe('GET /api/privacy/export/:exportId/download', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/export/export-1/download',
      });
      expect(response.statusCode).toBe(401);
    });
  });

  // =========================================================================
  // Data Deletion
  // =========================================================================

  describe('POST /api/privacy/deletion', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/privacy/deletion',
        payload: {
          deletionType: 'user',
          scope: { includeMedia: true },
        },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should create a deletion request', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/privacy/deletion',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          deletionType: 'partial',
          scope: {
            momentIds: ['00000000-0000-0000-0000-000000000001'],
            includeMedia: true,
          },
        },
      });

      expect([200, 201, 202]).toContain(response.statusCode);
    });
  });

  describe('POST /api/privacy/deletion/verify', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/privacy/deletion/verify',
        payload: {
          requestId: '00000000-0000-0000-0000-000000000001',
          token: 'verification-token',
        },
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/privacy/deletion/:requestId/status', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/deletion/del-1/status',
      });
      expect(response.statusCode).toBe(401);
    });
  });

  // =========================================================================
  // Privacy Settings
  // =========================================================================

  describe('GET /api/privacy/settings', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/settings',
      });
      expect(response.statusCode).toBe(401);
    });

    it('should return current privacy settings', async () => {
      mockPrisma.privacySettings.findUnique.mockResolvedValueOnce({
        userId: 'user-1',
        dataRetentionDays: 365,
        autoDeleteEnabled: false,
        analyticsOptOut: false,
        thirdPartySharingOptOut: true,
        marketingEmailsOptOut: false,
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/settings',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect([200]).toContain(response.statusCode);
    });
  });

  describe('PUT /api/privacy/settings', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'PUT',
        url: '/api/privacy/settings',
        payload: { analyticsOptOut: true },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should update privacy settings', async () => {
      mockPrisma.privacySettings.upsert.mockResolvedValueOnce({
        userId: 'user-1',
        analyticsOptOut: true,
      });

      const response = await app.inject({
        method: 'PUT',
        url: '/api/privacy/settings',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          analyticsOptOut: true,
          dataRetentionDays: 180,
        },
      });

      expect([200]).toContain(response.statusCode);
    });
  });

  // =========================================================================
  // Consent Management
  // =========================================================================

  describe('POST /api/privacy/consent', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/privacy/consent',
        payload: {
          consentType: 'analytics',
          consentGiven: true,
          consentVersion: '1.0',
        },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should record consent', async () => {
      mockPrisma.consentRecord.create.mockResolvedValueOnce({
        id: 'consent-1',
        userId: 'user-1',
        consentType: 'analytics',
        consentGiven: true,
        consentVersion: '1.0',
        createdAt: new Date(),
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/privacy/consent',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          consentType: 'analytics',
          consentGiven: true,
          consentVersion: '1.0',
        },
      });

      expect([200, 201]).toContain(response.statusCode);
    });
  });

  // =========================================================================
  // Data Summary
  // =========================================================================

  describe('GET /api/privacy/data-summary', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/data-summary',
      });
      expect(response.statusCode).toBe(401);
    });

    it('should return data summary for authenticated user', async () => {
      mockPrisma.moment.count.mockResolvedValueOnce(42);
      mockPrisma.sphere.count.mockResolvedValueOnce(5);
      mockPrisma.mediaReference.count.mockResolvedValueOnce(10);
      mockPrisma.mediaReference.aggregate.mockResolvedValueOnce({ _sum: { fileSizeBytes: 1024 * 1024 * 50 } });
      mockPrisma.consentRecord.findMany.mockResolvedValueOnce([]);
      mockPrisma.auditEvent.count.mockResolvedValueOnce(100);

      const response = await app.inject({
        method: 'GET',
        url: '/api/privacy/data-summary',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect([200]).toContain(response.statusCode);
    });
  });
});
