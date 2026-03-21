/**
 * Contract-Backed Route Tests
 *
 * Validates that MSW mock responses flowing through UI routes
 * conform to the shared contract schemas. This bridges the gap
 * between "mock-driven" tests and "contract-driven" tests:
 *
 * 1. MSW handlers serve mock data → validated against Zod schemas
 * 2. Pages render using that data → verified via RTL
 * 3. If schemas change, both contract tests AND these route tests break
 *
 * @doc.type test
 * @doc.purpose Contract-backed route integration tests
 * @doc.layer frontend
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { http, HttpResponse } from 'msw';
import { server } from '../../mocks/server';
import { TestWrapper } from '../test-utils/wrapper';
import {
  CollectionSchema,
  PaginatedCollectionResponseSchema,
  StorageProfileSchema,
  ConnectorSchema,
} from '../../contracts/schemas';

// ---------------------------------------------------------------------------
// Test: Collections route with contract-validated data
// ---------------------------------------------------------------------------

describe('Contract-Backed Route Tests', () => {
  describe('Collections Page — contract compliance', () => {
    // Contract-validated seed data
    const contractCollection = CollectionSchema.parse({
      id: 'contract-col-1',
      name: 'Contract Test Collection',
      description: 'Verified against Zod contract',
      schemaType: 'entity',
      status: 'active',
      entityCount: 42,
      schema: { fields: [] },
      tags: ['contract-test'],
      createdAt: '2024-06-01T00:00:00Z',
      updatedAt: '2024-06-01T00:00:00Z',
      createdBy: 'contract-runner',
    });

    const contractPaginatedResponse = PaginatedCollectionResponseSchema.parse({
      items: [contractCollection],
      total: 1,
      page: 1,
      pageSize: 20,
      hasMore: false,
    });

    it('should render collection data that passes contract validation', async () => {
      // Override MSW with contract-validated response
      server.use(
        http.get('/api/v1/collections', () =>
          HttpResponse.json(contractPaginatedResponse)
        )
      );

      // Dynamically import to avoid circular/static issues
      const { default: DataExplorer } = await import(
        '../../pages/DataExplorer'
      );

      render(<DataExplorer />, { wrapper: TestWrapper });

      await waitFor(
        () => {
          expect(
            screen.getByText(/Contract Test Collection/i)
          ).toBeInTheDocument();
        },
        { timeout: 3000 }
      );

      // The response was .parse()'d — if schema drifts, this test fails at
      // the parse() call above, not silently at render time.
    });

    it('contract schema rejects response missing required fields', () => {
      const incomplete = {
        id: 'col-bad',
        name: 'Incomplete Collection',
        // missing description, schemaType, status, etc.
      };

      const result = CollectionSchema.safeParse(incomplete);
      expect(result.success).toBe(false);
    });
  });

  describe('Data Fabric — contract compliance', () => {
    it('storage profile data passes contract validation', () => {
      const profile = StorageProfileSchema.parse({
        id: 'sp-contract-1',
        name: 'Contract RocksDB',
        type: 'rocksdb',
        isDefault: true,
        status: 'healthy',
        config: { path: '/data/rocksdb' },
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-06-01T00:00:00Z',
      });

      expect(profile.id).toBe('sp-contract-1');
      expect(profile.isDefault).toBe(true);
    });

    it('connector data passes contract validation', () => {
      const connector = ConnectorSchema.parse({
        id: 'dc-contract-1',
        name: 'Contract JDBC',
        type: 'jdbc',
        storageProfileId: 'sp-contract-1',
        status: 'active',
        config: { url: 'jdbc:postgresql://host/db' },
        createdAt: '2024-02-01T00:00:00Z',
        updatedAt: '2024-06-01T00:00:00Z',
      });

      expect(connector.type).toBe('jdbc');
    });
  });
});
