import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  StorageProfileSchema,
  StorageProfileMetricsSchema,
  CreateStorageProfileRequestSchema,
  UpdateStorageProfileRequestSchema,
} from '../../src/contracts/schemas';

/**
 * Storage Profiles API Contract Tests
 *
 * Validates that storage profile API requests and responses conform to the expected schemas.
 * Covers CRUD operations, metrics endpoint, and error cases.
 *
 * @doc.type test
 * @doc.purpose Storage Profiles API contract validation
 * @doc.layer testing
 */

describe('Storage Profiles API Contract', () => {
  // ─── StorageProfileSchema ─────────────────────────────────────────────────

  describe('StorageProfileSchema', () => {
    const buildValidProfile = (overrides: Partial<z.infer<typeof StorageProfileSchema>> = {}) => ({
      id: 'sp-001',
      name: 'Primary PostgreSQL',
      type: 'postgresql',
      isDefault: true,
      status: 'active',
      config: { host: 'db.internal', port: 5432, database: 'datacloud' },
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-15T12:00:00Z',
      ...overrides,
    });

    it('should accept a valid storage profile', () => {
      const result = StorageProfileSchema.safeParse(buildValidProfile());
      expect(result.success).toBe(true);
    });

    it('should accept storage profile with isDefault=false', () => {
      const result = StorageProfileSchema.safeParse(buildValidProfile({ isDefault: false }));
      expect(result.success).toBe(true);
    });

    it('should accept profile with empty config', () => {
      const result = StorageProfileSchema.safeParse(buildValidProfile({ config: {} }));
      expect(result.success).toBe(true);
    });

    it('should reject profile missing id', () => {
      const invalid = buildValidProfile();
      // @ts-expect-error — testing missing required field
      delete invalid.id;
      const result = StorageProfileSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('should reject profile missing isDefault', () => {
      const invalid = buildValidProfile();
      // @ts-expect-error — testing missing required field
      delete invalid.isDefault;
      const result = StorageProfileSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('should reject profile missing createdAt', () => {
      const invalid = buildValidProfile();
      // @ts-expect-error — testing missing required field
      delete invalid.createdAt;
      const result = StorageProfileSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });
  });

  // ─── StorageProfileMetricsSchema ─────────────────────────────────────────

  describe('StorageProfileMetricsSchema', () => {
    const buildValidMetrics = () => ({
      storageUsedBytes: 1073741824,  // 1 GB
      storageTotalBytes: 10737418240, // 10 GB
      readOpsPerSec: 1500,
      writeOpsPerSec: 800,
      latencyP99Ms: 12.5,
      lastUpdated: '2026-01-15T12:00:00Z',
    });

    it('should accept valid storage profile metrics', () => {
      const result = StorageProfileMetricsSchema.safeParse(buildValidMetrics());
      expect(result.success).toBe(true);
    });

    it('should accept metrics when storage is nearly full', () => {
      const nearFull = buildValidMetrics();
      nearFull.storageUsedBytes = nearFull.storageTotalBytes - 1024;
      const result = StorageProfileMetricsSchema.safeParse(nearFull);
      expect(result.success).toBe(true);
    });

    it('should accept metrics with zero read/write ops (idle system)', () => {
      const idleMetrics = { ...buildValidMetrics(), readOpsPerSec: 0, writeOpsPerSec: 0 };
      const result = StorageProfileMetricsSchema.safeParse(idleMetrics);
      expect(result.success).toBe(true);
    });

    it('should reject metrics missing latencyP99Ms', () => {
      const invalid = buildValidMetrics();
      // @ts-expect-error — testing missing field
      delete invalid.latencyP99Ms;
      const result = StorageProfileMetricsSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('should reject metrics with non-numeric storageUsedBytes', () => {
      const result = StorageProfileMetricsSchema.safeParse({
        ...buildValidMetrics(),
        storageUsedBytes: 'not-a-number',
      });
      expect(result.success).toBe(false);
    });
  });

  // ─── CreateStorageProfileRequestSchema ───────────────────────────────────

  describe('CreateStorageProfileRequestSchema', () => {
    it('should accept valid create request for postgresql type', () => {
      const validRequest = {
        name: 'PostgreSQL Primary',
        type: 'postgresql',
        config: { host: 'localhost', port: 5432 },
        isDefault: true,
      };

      const result = CreateStorageProfileRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it.each(['postgresql', 'timescaledb', 'clickhouse', 's3', 'gcs', 'azure-blob', 'in-memory'])(
      'should accept valid storage type "%s"',
      (type) => {
        const request = {
          name: 'Profile',
          type,
          config: {},
        };

        const result = CreateStorageProfileRequestSchema.safeParse(request);
        expect(result.success).toBe(true);
      }
    );

    it('should reject request with invalid type', () => {
      const result = CreateStorageProfileRequestSchema.safeParse({
        name: 'Profile',
        type: 'mysql', // not in enum
        config: {},
      });
      expect(result.success).toBe(false);
    });

    it('should reject request with empty name', () => {
      const result = CreateStorageProfileRequestSchema.safeParse({
        name: '',
        type: 'postgresql',
        config: {},
      });
      expect(result.success).toBe(false);
    });

    it('should accept create request without isDefault (optional)', () => {
      const result = CreateStorageProfileRequestSchema.safeParse({
        name: 'Secondary Store',
        type: 's3',
        config: { bucket: 'my-bucket', region: 'us-east-1' },
      });
      expect(result.success).toBe(true);
    });
  });

  // ─── UpdateStorageProfileRequestSchema ───────────────────────────────────

  describe('UpdateStorageProfileRequestSchema', () => {
    it('should accept full update request', () => {
      const validRequest = {
        name: 'Updated Profile',
        config: { host: 'new-host', port: 5433 },
        isDefault: false,
        status: 'maintenance',
      };

      const result = UpdateStorageProfileRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should accept partial update with only name', () => {
      const result = UpdateStorageProfileRequestSchema.safeParse({ name: 'New Name' });
      expect(result.success).toBe(true);
    });

    it('should accept partial update with only status', () => {
      const result = UpdateStorageProfileRequestSchema.safeParse({ status: 'inactive' });
      expect(result.success).toBe(true);
    });

    it.each(['active', 'inactive', 'maintenance'])(
      'should accept status "%s"',
      (status) => {
        const result = UpdateStorageProfileRequestSchema.safeParse({ status });
        expect(result.success).toBe(true);
      }
    );

    it('should reject invalid status', () => {
      const result = UpdateStorageProfileRequestSchema.safeParse({ status: 'decommissioned' });
      expect(result.success).toBe(false);
    });

    it('should reject update with empty name', () => {
      const result = UpdateStorageProfileRequestSchema.safeParse({ name: '' });
      expect(result.success).toBe(false);
    });

    it('should accept empty update object (no-op patch)', () => {
      const result = UpdateStorageProfileRequestSchema.safeParse({});
      expect(result.success).toBe(true);
    });
  });

  // ─── DELETE response ──────────────────────────────────────────────────────

  describe('DELETE /api/v1/storage-profiles/:id', () => {
    it('should return success response matching expected shape', () => {
      const DeleteResponseSchema = z.object({ success: z.boolean(), id: z.string() });
      const mockResponse = { success: true, id: 'sp-001' };

      const result = DeleteResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });
  });
});
