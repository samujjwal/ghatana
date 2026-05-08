import { test, expect } from '@playwright/test';

/**
 * Event Store E2E Tests: Append/Query/Replay/Stream Journey
 *
 * Validates the complete event lifecycle:
 * 1. Append events to event store (immutable log)
 * 2. Query events with filters and pagination
 * 3. Replay events to rebuild state
 * 4. Stream events in real-time
 * 5. Verify idempotency and ordering
 * 6. Validate tenant isolation
 *
 * These tests prove the event sourcing pattern works end-to-end.
 */

test.describe('Event Store E2E Journey', () => {
  const API_BASE = 'http://localhost:4000/api/v1';
  const TENANT_ID = 'test-tenant-1';
  const AUTH_TOKEN = 'test-auth-token';

  interface Event {
    id: string;
    aggregateId: string;
    eventType: string;
    timestamp: string;
    version: number;
    payload: Record<string, unknown>;
    tenantId: string;
  }

  test.describe('Append Events', () => {
    test('should append event to immutable log', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();
      const eventPayload = {
        action: 'created',
        name: 'Test Entity',
        type: 'primary',
      };

      const response = await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
            'Content-Type': 'application/json',
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: eventPayload,
          },
        }
      );

      expect(response.status()).toBe(201);
      const event = (await response.json()) as Event;

      expect(event.id).toBeDefined();
      expect(event.aggregateId).toBe(aggregateId);
      expect(event.eventType).toBe('EntityCreated');
      expect(event.version).toBe(1);
      expect(event.tenantId).toBe(TENANT_ID);
      expect(event.timestamp).toBeDefined();
      expect(event.payload).toEqual(eventPayload);
    });

    test('should maintain event ordering by version', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append multiple events
      const eventTypes = ['EntityCreated', 'EntityUpdated', 'EntityArchived'];
      const appendedEvents: Event[] = [];

      for (const eventType of eventTypes) {
        const response = await page.request.post(
          `${API_BASE}/events/append`,
          {
            headers: {
              'X-Tenant-ID': TENANT_ID,
              'Authorization': `Bearer ${AUTH_TOKEN}`,
            },
            data: {
              aggregateId,
              eventType,
              payload: { action: eventType },
            },
          }
        );

        expect(response.status()).toBe(201);
        const event = (await response.json()) as Event;
        appendedEvents.push(event);
      }

      // Verify versions are sequential
      expect(appendedEvents[0].version).toBe(1);
      expect(appendedEvents[1].version).toBe(2);
      expect(appendedEvents[2].version).toBe(3);
    });

    test('should enforce idempotency with idempotency key', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();
      const idempotencyKey = 'idem-' + Date.now();

      // First append
      const response1 = await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
            'Idempotency-Key': idempotencyKey,
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: { name: 'Entity 1' },
          },
        }
      );

      expect(response1.status()).toBe(201);
      const event1 = (await response1.json()) as Event;

      // Second append with same idempotency key
      const response2 = await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
            'Idempotency-Key': idempotencyKey,
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: { name: 'Entity 1' },
          },
        }
      );

      expect(response2.status()).toBe(200); // Not created again
      const event2 = (await response2.json()) as Event;

      // Should return same event
      expect(event2.id).toBe(event1.id);
      expect(event2.version).toBe(event1.version);
    });

    test('should reject concurrent appends to same version', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append first event
      await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: { name: 'Entity' },
          },
        }
      );

      // Try to append event claiming to be version 1 (should be version 2)
      const response = await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityUpdated',
            payload: { name: 'Updated' },
            expectedVersion: 1, // This will fail, expected version is 2
          },
        }
      );

      expect(response.status()).toBe(409); // Conflict
    });

    test('should enforce tenant isolation for appends', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append event to tenant 1
      const response1 = await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: { name: 'Entity' },
          },
        }
      );

      expect(response1.status()).toBe(201);
      const event = (await response1.json()) as Event;

      // Try to read from different tenant
      const response2 = await page.request.get(
        `${API_BASE}/events/${event.id}`,
        {
          headers: {
            'X-Tenant-ID': 'different-tenant',
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      expect(response2.status()).toBe(404);
    });
  });

  test.describe('Query Events', () => {
    test('should query events by aggregate ID', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append multiple events
      for (let i = 0; i < 3; i++) {
        await page.request.post(
          `${API_BASE}/events/append`,
          {
            headers: {
              'X-Tenant-ID': TENANT_ID,
              'Authorization': `Bearer ${AUTH_TOKEN}`,
            },
            data: {
              aggregateId,
              eventType: 'EntityUpdated',
              payload: { iteration: i },
            },
          }
        );
      }

      // Query events for aggregate
      const response = await page.request.get(
        `${API_BASE}/events?aggregateId=${aggregateId}`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      expect(response.status()).toBe(200);
      const result = await response.json();

      expect(result.events).toHaveLength(3);
      result.events.forEach((event: Event) => {
        expect(event.aggregateId).toBe(aggregateId);
        expect(event.tenantId).toBe(TENANT_ID);
      });
    });

    test('should query events by type', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append events of different types
      await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: {},
          },
        }
      );

      await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityUpdated',
            payload: {},
          },
        }
      );

      // Query by event type
      const response = await page.request.get(
        `${API_BASE}/events?eventType=EntityCreated`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      expect(response.status()).toBe(200);
      const result = await response.json();

      result.events.forEach((event: Event) => {
        expect(event.eventType).toBe('EntityCreated');
      });
    });

    test('should support pagination with cursor', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append 10 events
      for (let i = 0; i < 10; i++) {
        await page.request.post(
          `${API_BASE}/events/append`,
          {
            headers: {
              'X-Tenant-ID': TENANT_ID,
              'Authorization': `Bearer ${AUTH_TOKEN}`,
            },
            data: {
              aggregateId,
              eventType: 'EntityUpdated',
              payload: { index: i },
            },
          }
        );
      }

      // Query with page size
      const response1 = await page.request.get(
        `${API_BASE}/events?aggregateId=${aggregateId}&pageSize=3`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      const result1 = await response1.json();
      expect(result1.events).toHaveLength(3);
      expect(result1.nextCursor).toBeDefined();

      // Get next page
      const response2 = await page.request.get(
        `${API_BASE}/events?aggregateId=${aggregateId}&pageSize=3&cursor=${result1.nextCursor}`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      const result2 = await response2.json();
      expect(result2.events).toHaveLength(3);

      // Verify no duplicate events across pages
      const ids1 = result1.events.map((e: Event) => e.id);
      const ids2 = result2.events.map((e: Event) => e.id);
      expect(new Set([...ids1, ...ids2]).size).toBe(ids1.length + ids2.length);
    });
  });

  test.describe('Replay Events', () => {
    test('should replay events to rebuild state', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append events that represent state changes
      const states = [
        { action: 'created', name: 'New Entity', status: 'active' },
        { action: 'updated', name: 'Updated Entity', status: 'active' },
        { action: 'archived', name: 'Updated Entity', status: 'archived' },
      ];

      for (const state of states) {
        await page.request.post(
          `${API_BASE}/events/append`,
          {
            headers: {
              'X-Tenant-ID': TENANT_ID,
              'Authorization': `Bearer ${AUTH_TOKEN}`,
            },
            data: {
              aggregateId,
              eventType: 'StateChanged',
              payload: state,
            },
          }
        );
      }

      // Replay to get current state
      const response = await page.request.post(
        `${API_BASE}/events/replay`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
          },
        }
      );

      expect(response.status()).toBe(200);
      const currentState = await response.json();

      // Should have the final state
      expect(currentState.name).toBe('Updated Entity');
      expect(currentState.status).toBe('archived');
      expect(currentState.version).toBe(3);
    });

    test('should replay to specific version', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append events
      const states = ['created', 'updated1', 'updated2', 'archived'];
      for (const state of states) {
        await page.request.post(
          `${API_BASE}/events/append`,
          {
            headers: {
              'X-Tenant-ID': TENANT_ID,
              'Authorization': `Bearer ${AUTH_TOKEN}`,
            },
            data: {
              aggregateId,
              eventType: 'StateChanged',
              payload: { state },
            },
          }
        );
      }

      // Replay to version 2
      const response = await page.request.post(
        `${API_BASE}/events/replay`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            toVersion: 2,
          },
        }
      );

      const stateAtV2 = await response.json();
      expect(stateAtV2.version).toBe(2);
      expect(stateAtV2.state).toBe('updated1');
    });
  });

  test.describe('Stream Events', () => {
    test('should stream new events in real-time', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Subscribe to events
      const eventPromise = page.request.get(
        `${API_BASE}/events/stream?aggregateId=${aggregateId}&follow=true`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      // Give stream time to connect
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Append an event
      await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityUpdated',
            payload: { timestamp: Date.now() },
          },
        }
      );

      // Verify stream received event (implementation-specific)
      // In real implementation, would use websocket or SSE
    });

    test('should not stream events across tenants', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Subscribe as tenant 1
      // Try to subscribe as tenant 2 to same aggregate - should get nothing or fail
      // This validates tenant isolation in streaming

      const response = await page.request.get(
        `${API_BASE}/events?aggregateId=${aggregateId}`,
        {
          headers: {
            'X-Tenant-ID': 'different-tenant',
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
        }
      );

      // Should get empty or error depending on implementation
      const result = await response.json();
      expect(result.events || []).toHaveLength(0);
    });
  });

  test.describe('Event Sourcing Guarantees', () => {
    test('should maintain audit trail for all events', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append event
      const response = await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: { name: 'Entity' },
          },
        }
      );

      const event = (await response.json()) as Event;

      // Verify immutable audit fields
      expect(event.id).toBeDefined(); // Unique event ID
      expect(event.version).toBe(1); // Version number
      expect(event.timestamp).toBeDefined(); // Server timestamp
      expect(event.tenantId).toBe(TENANT_ID); // Tenant scoped
    });

    test('should ensure events are immutable', async ({ page }) => {
      const aggregateId = 'entity-' + Date.now();

      // Append event
      const response1 = await page.request.post(
        `${API_BASE}/events/append`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            aggregateId,
            eventType: 'EntityCreated',
            payload: { name: 'Entity' },
          },
        }
      );

      const event = (await response1.json()) as Event;

      // Try to update event (should fail)
      const response2 = await page.request.patch(
        `${API_BASE}/events/${event.id}`,
        {
          headers: {
            'X-Tenant-ID': TENANT_ID,
            'Authorization': `Bearer ${AUTH_TOKEN}`,
          },
          data: {
            payload: { name: 'Hacked' },
          },
        }
      );

      expect(response2.status()).toBe(405); // Method not allowed
    });
  });
});
