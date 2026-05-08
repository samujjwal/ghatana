import { test, expect } from '@playwright/test';

/**
 * Entity CRUD E2E Tests
 *
 * Validates the complete entity lifecycle journey:
 * 1. Create entity via API with required fields
 * 2. Read entity and verify all persisted data
 * 3. List entities with pagination
 * 4. Update entity and verify changes persist
 * 5. Delete entity and verify removal
 * 6. Verify tenant isolation throughout
 *
 * These tests use real backend API and validate end-to-end persistence.
 */

test.describe('Entity CRUD E2E Journey', () => {
  const API_BASE = 'http://localhost:4000/api/v1';
  const TENANT_ID = 'test-tenant-1';
  const AUTH_TOKEN = 'test-auth-token'; // Would be obtained from auth flow

  let entityId: string;

  test.beforeAll(async () => {
    // Would normally authenticate and get valid token
    console.log(`Testing entity CRUD for tenant: ${TENANT_ID}`);
  });

  test.describe('Create Entity', () => {
    test('should create entity with required fields', async ({ page }) => {
      const createPayload = {
        name: 'Test Entity',
        description: 'A test entity for CRUD validation',
        type: 'primary',
        metadata: {
          source: 'e2e-test',
          version: '1.0',
        },
      };

      // Create entity via API
      const response = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
          'Content-Type': 'application/json',
        },
        data: createPayload,
      });

      expect(response.status()).toBe(201);

      const responseBody = await response.json();
      expect(responseBody).toHaveProperty('id');
      expect(responseBody).toHaveProperty('createdAt');
      expect(responseBody).toHaveProperty('tenantId', TENANT_ID);
      expect(responseBody.name).toBe(createPayload.name);

      entityId = responseBody.id;
      console.log(`Created entity: ${entityId}`);
    });

    test('should reject entity without required fields', async ({ page }) => {
      const invalidPayload = {
        description: 'Missing name field',
        type: 'primary',
      };

      const response = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
          'Content-Type': 'application/json',
        },
        data: invalidPayload,
      });

      expect(response.status()).toBe(400);
      const error = await response.json();
      expect(error.code).toBe('VALIDATION_ERROR');
      expect(error.message).toContain('name');
    });

    test('should set default values for optional fields', async ({ page }) => {
      const createPayload = {
        name: 'Entity with Defaults',
        type: 'primary',
      };

      const response = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
          'Content-Type': 'application/json',
        },
        data: createPayload,
      });

      expect(response.status()).toBe(201);
      const entity = await response.json();

      // Default values should be set
      expect(entity.status).toBe('active');
      expect(entity.visibility).toBe('private');
      expect(entity.tags).toEqual([]);
    });
  });

  test.describe('Read Entity', () => {
    test('should read entity by ID and verify all fields', async ({ page }) => {
      // First create an entity
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Read Test Entity',
          type: 'secondary',
          metadata: { key: 'value' },
        },
      });

      const created = await createResponse.json();
      const id = created.id;

      // Now read it back
      const readResponse = await page.request.get(`${API_BASE}/entities/${id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      expect(readResponse.status()).toBe(200);
      const entity = await readResponse.json();

      // Verify all persisted data
      expect(entity.id).toBe(id);
      expect(entity.tenantId).toBe(TENANT_ID);
      expect(entity.name).toBe('Read Test Entity');
      expect(entity.type).toBe('secondary');
      expect(entity.metadata.key).toBe('value');
      expect(entity.createdAt).toBeDefined();
      expect(entity.updatedAt).toBeDefined();
    });

    test('should return 404 for non-existent entity', async ({ page }) => {
      const response = await page.request.get(`${API_BASE}/entities/non-existent-id`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      expect(response.status()).toBe(404);
      const error = await response.json();
      expect(error.code).toBe('ENTITY_NOT_FOUND');
    });

    test('should enforce tenant isolation for read', async ({ page }) => {
      // Create entity in tenant 1
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Tenant-Scoped Entity',
          type: 'primary',
        },
      });

      const entity = await createResponse.json();

      // Try to read from different tenant
      const readResponse = await page.request.get(`${API_BASE}/entities/${entity.id}`, {
        headers: {
          'X-Tenant-ID': 'different-tenant',
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      expect(readResponse.status()).toBe(404);
      // Entity from different tenant should not be accessible
    });
  });

  test.describe('List Entities', () => {
    test('should list entities with pagination', async ({ page }) => {
      // Create multiple entities
      const ids: string[] = [];
      for (let i = 0; i < 5; i++) {
        const response = await page.request.post(`${API_BASE}/entities`, {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            name: `List Test Entity ${i}`,
            type: 'primary',
          },
        });
        const entity = await response.json();
        ids.push(entity.id);
      }

      // List with page size
      const listResponse = await page.request.get(
        `${API_BASE}/entities?pageSize=2&pageNumber=1`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      expect(listResponse.status()).toBe(200);
      const listData = await listResponse.json();

      expect(listData).toHaveProperty('items');
      expect(listData).toHaveProperty('totalCount');
      expect(listData.items.length).toBeLessThanOrEqual(2);
      expect(listData.totalCount).toBeGreaterThanOrEqual(5);
    });

    test('should filter entities by type', async ({ page }) => {
      // Create entities of different types
      await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Type A Entity',
          type: 'type-a',
        },
      });

      await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Type B Entity',
          type: 'type-b',
        },
      });

      // Filter by type
      const filterResponse = await page.request.get(
        `${API_BASE}/entities?type=type-a`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      expect(filterResponse.status()).toBe(200);
      const filtered = await filterResponse.json();

      // All returned items should match the filter
      filtered.items.forEach((item: any) => {
        expect(item.type).toBe('type-a');
      });
    });

    test('should enforce tenant isolation for list', async ({ page }) => {
      // Create entity in tenant 1
      await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Tenant 1 Entity',
          type: 'primary',
        },
      });

      // List from tenant 1
      const list1 = await page.request.get(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      const tenant1Items = await list1.json();

      // List from different tenant
      const list2 = await page.request.get(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': 'different-tenant-2',
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      const tenant2Items = await list2.json();

      // Lists should be different (or tenant 2 should have 0 items)
      // Verify no cross-tenant leakage
      tenant1Items.items.forEach((item: any) => {
        expect(item.tenantId).toBe(TENANT_ID);
      });

      tenant2Items.items.forEach((item: any) => {
        expect(item.tenantId).toBe('different-tenant-2');
      });
    });
  });

  test.describe('Update Entity', () => {
    test('should update entity and persist changes', async ({ page }) => {
      // Create entity
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Original Name',
          type: 'primary',
          status: 'active',
        },
      });

      const entity = await createResponse.json();
      const id = entity.id;
      const originalUpdatedAt = entity.updatedAt;

      // Wait a moment to ensure timestamp difference
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Update entity
      const updateResponse = await page.request.patch(`${API_BASE}/entities/${id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
          'Content-Type': 'application/json',
        },
        data: {
          name: 'Updated Name',
          status: 'archived',
        },
      });

      expect(updateResponse.status()).toBe(200);
      const updated = await updateResponse.json();

      expect(updated.name).toBe('Updated Name');
      expect(updated.status).toBe('archived');
      expect(updated.type).toBe('primary'); // Unchanged field
      expect(new Date(updated.updatedAt).getTime()).toBeGreaterThan(
        new Date(originalUpdatedAt).getTime()
      );

      // Verify changes persisted by reading again
      const readResponse = await page.request.get(`${API_BASE}/entities/${id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      const reread = await readResponse.json();
      expect(reread.name).toBe('Updated Name');
      expect(reread.status).toBe('archived');
    });

    test('should prevent updating other tenant entities', async ({ page }) => {
      // Create entity in tenant 1
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Protected Entity',
          type: 'primary',
        },
      });

      const entity = await createResponse.json();

      // Try to update from different tenant
      const updateResponse = await page.request.patch(
        `${API_BASE}/entities/${entity.id}`,
        {
          headers: {
            'X-Tenant-ID': 'different-tenant',
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: { name: 'Hacked Name' },
        }
      );

      expect(updateResponse.status()).toBe(404);

      // Verify entity unchanged
      const readResponse = await page.request.get(`${API_BASE}/entities/${entity.id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      const reread = await readResponse.json();
      expect(reread.name).toBe('Protected Entity');
    });
  });

  test.describe('Delete Entity', () => {
    test('should delete entity and remove from storage', async ({ page }) => {
      // Create entity
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Entity to Delete',
          type: 'primary',
        },
      });

      const entity = await createResponse.json();
      const id = entity.id;

      // Delete entity
      const deleteResponse = await page.request.delete(`${API_BASE}/entities/${id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      expect(deleteResponse.status()).toBe(204);

      // Verify entity no longer exists
      const readResponse = await page.request.get(`${API_BASE}/entities/${id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      expect(readResponse.status()).toBe(404);
    });

    test('should prevent deleting entities from other tenants', async ({ page }) => {
      // Create entity in tenant 1
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Protected Entity',
          type: 'primary',
        },
      });

      const entity = await createResponse.json();

      // Try to delete from different tenant
      const deleteResponse = await page.request.delete(
        `${API_BASE}/entities/${entity.id}`,
        {
          headers: {
            'X-Tenant-ID': 'different-tenant',
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      expect(deleteResponse.status()).toBe(404);

      // Verify entity still exists for original tenant
      const readResponse = await page.request.get(`${API_BASE}/entities/${entity.id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      expect(readResponse.status()).toBe(200);
    });
  });

  test.describe('Data Persistence Verification', () => {
    test('should persist metadata through CRUD cycle', async ({ page }) => {
      const metadata = {
        source: 'e2e-test',
        version: '1.0',
        custom: {
          nested: 'value',
        },
      };

      // Create
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Metadata Test',
          type: 'primary',
          metadata,
        },
      });

      const created = await createResponse.json();

      // Read and verify metadata persisted
      const readResponse = await page.request.get(`${API_BASE}/entities/${created.id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      const read = await readResponse.json();
      expect(read.metadata).toEqual(metadata);

      // Update other fields
      await page.request.patch(`${API_BASE}/entities/${created.id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: { name: 'Updated Name' },
      });

      // Verify metadata survived update
      const rereadResponse = await page.request.get(
        `${API_BASE}/entities/${created.id}`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      const reread = await rereadResponse.json();
      expect(reread.metadata).toEqual(metadata);
    });

    test('should maintain audit trail for all operations', async ({ page }) => {
      // Create
      const createResponse = await page.request.post(`${API_BASE}/entities`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: {
          name: 'Audit Test',
          type: 'primary',
        },
      });

      const created = await createResponse.json();

      // Verify audit fields
      expect(created.createdAt).toBeDefined();
      expect(created.createdBy).toBeDefined();

      // Update
      await page.request.patch(`${API_BASE}/entities/${created.id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
        data: { name: 'Updated' },
      });

      // Read and verify audit trail
      const readResponse = await page.request.get(`${API_BASE}/entities/${created.id}`, {
        headers: {
          'X-Tenant-ID': TENANT_ID,
          'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
      });

      const read = await readResponse.json();
      expect(read.createdAt).toBeDefined();
      expect(read.updatedAt).toBeDefined();
      expect(read.createdBy).toBeDefined();
      expect(read.updatedBy).toBeDefined();
    });
  });
});
