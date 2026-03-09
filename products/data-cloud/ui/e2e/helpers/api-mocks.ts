import { Page } from '@playwright/test';
import { mockCollections, mockWorkflows, mockExecutions, mockEntities } from '../fixtures/test-data';

/**
 * API Mock Helpers
 * 
 * Provides utilities to mock API responses in E2E tests.
 * 
 * @doc.type helper
 * @doc.purpose Mock API responses for E2E testing
 * @doc.layer testing
 */

/**
 * Mock collections API endpoints
 */
export async function mockCollectionsAPI(page: Page) {
  // List collections
  await page.route('**/api/v1/collections', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: mockCollections,
          total: mockCollections.length,
          page: 1,
          pageSize: 10,
          hasMore: false,
        }),
      });
    } else if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: `col-${Date.now()}`,
          ...body,
          entityCount: 0,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          createdBy: 'test-user',
        }),
      });
    }
  });

  // Get collection by ID
  await page.route('**/api/v1/collections/*', async (route) => {
    const url = route.request().url();
    const id = url.split('/').pop()?.split('?')[0];
    const collection = mockCollections.find(c => c.id === id);

    if (route.request().method() === 'GET') {
      if (collection) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(collection),
        });
      } else {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({ code: 'NOT_FOUND', message: 'Collection not found' }),
        });
      }
    } else if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...collection,
          ...body,
          updatedAt: new Date().toISOString(),
        }),
      });
    } else if (route.request().method() === 'DELETE') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true }),
      });
    }
  });
}

/**
 * Mock workflows API endpoints
 */
export async function mockWorkflowsAPI(page: Page) {
  // List workflows
  await page.route('**/api/v1/workflows', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: mockWorkflows,
          total: mockWorkflows.length,
          page: 1,
          pageSize: 10,
          hasMore: false,
        }),
      });
    } else if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: `wf-${Date.now()}`,
          ...body,
          executionCount: 0,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
      });
    }
  });

  // Get workflow by ID
  await page.route('**/api/v1/workflows/*', async (route) => {
    const url = route.request().url();
    const id = url.split('/').pop()?.split('?')[0];
    const workflow = mockWorkflows.find(w => w.id === id);

    if (route.request().method() === 'GET') {
      if (workflow) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(workflow),
        });
      } else {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({ code: 'NOT_FOUND', message: 'Workflow not found' }),
        });
      }
    }
  });

  // Workflow executions
  await page.route('**/api/v1/workflows/*/executions', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: mockExecutions,
        total: mockExecutions.length,
        page: 1,
        pageSize: 10,
        hasMore: false,
      }),
    });
  });
}

/**
 * Mock entities API endpoints
 */
export async function mockEntitiesAPI(page: Page, collectionId: string) {
  await page.route(`**/api/v1/tenants/*/collections/${collectionId}/records`, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: mockEntities.filter(e => e.collectionId === collectionId),
          total: mockEntities.filter(e => e.collectionId === collectionId).length,
          offset: 0,
          limit: 10,
        }),
      });
    }
  });
}

/**
 * Mock all API endpoints
 */
export async function mockAllAPIs(page: Page) {
  await mockCollectionsAPI(page);
  await mockWorkflowsAPI(page);
}

/**
 * Mock API error responses
 */
export async function mockAPIError(page: Page, endpoint: string, statusCode = 500) {
  await page.route(endpoint, async (route) => {
    await route.fulfill({
      status: statusCode,
      contentType: 'application/json',
      body: JSON.stringify({
        code: statusCode === 404 ? 'NOT_FOUND' : 'INTERNAL_ERROR',
        message: statusCode === 404 ? 'Resource not found' : 'Internal server error',
      }),
    });
  });
}
