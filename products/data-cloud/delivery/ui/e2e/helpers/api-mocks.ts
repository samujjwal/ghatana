import { expect, Page } from '@playwright/test';
import {
  mockCollections,
  mockWorkflows,
  mockExecutions,
  mockEntities,
  mockAlerts,
  mockAlertGroups,
  mockAlertSuggestions,
  mockAlertRules,
} from '../fixtures/test-data';
import { COLLECTION_RUNTIME_OPENAPI_PATHS, DEPRECATED_COLLECTION_ROUTE_REDIRECTS, buildDeprecatedRouteHeaders, warnDeprecatedRoute } from '../../test-fixtures/deprecatedRoutes';

const COLLECTION_LIST_ROUTE = '**/api/v1/entities/dc_collections';
const COLLECTION_ITEM_ROUTE = '**/api/v1/entities/dc_collections/*';
const ALERT_LIST_ROUTE = '**/api/v1/alerts*';
const ALERT_GROUPS_ROUTE = '**/api/v1/alerts/groups*';
const ALERT_SUGGESTIONS_ROUTE = '**/api/v1/alerts/suggestions*';
const ALERT_RULES_ROUTE = '**/api/v1/alerts/rules*';
const SURFACES_ROUTES = ['**/surfaces', '**/api/v1/surfaces'] as const;
// DC-P1.12: Removed compatibility /api/v1/capabilities routes; use canonical /api/v1/surfaces only
const RUNTIME_TRUTH_ROUTES = [...SURFACES_ROUTES] as const;
const SURFACES_SCHEMA_ROUTES = ['**/surfaces/schema', '**/api/v1/surfaces/schema'] as const;
// DC-P1.12: Removed compatibility /api/v1/capabilities/schema routes; use canonical /api/v1/surfaces/schema only
const USER_ACTIVITY_ROUTE = '**/api/v1/user-activity/recent';
const ANALYTICS_SUGGEST_ROUTE = '**/api/v1/analytics/suggest';
const ANALYTICS_QUERY_ROUTE = '**/api/v1/analytics/query';
const ANALYTICS_EXPLAIN_ROUTE = '**/api/v1/analytics/explain';
const AI_QUALITY_SUMMARY_ROUTE = '**/api/v1/ai/quality-summary';
const FEDERATED_QUERY_ROUTE = '**/api/v1/queries/federated';
const BRAIN_STATS_ROUTE = '**/api/v1/brain/stats';
const GOVERNANCE_SUMMARY_ROUTE = '**/governance/compliance/summary';
const GOVERNANCE_PII_FIELDS_ROUTE = '**/governance/privacy/pii-fields';
const GOVERNANCE_CLASSIFY_ROUTE = '**/governance/retention/classify';
const GOVERNANCE_RETENTION_POLICY_ROUTE = '**/governance/retention/policy*';
const GOVERNANCE_REDACT_ROUTE = '**/governance/privacy/redact';
const GOVERNANCE_PURGE_ROUTE = '**/governance/retention/purge';

/**
 * Mark onboarding complete before page load so specs exercise the target page
 * instead of the first-run modal.
 */
export async function disableOnboardingWizard(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('dc:onboarding:complete', 'true');
    window.localStorage.setItem('tenantId', 'test-tenant');
    window.sessionStorage.setItem('dc:session:tenantId', 'test-tenant');
    window.sessionStorage.setItem('dc:session:shellRole', 'primary-user');
  });
}

export async function dismissOnboardingWizard(page: Page) {
  const onboardingDialog = page.getByRole('dialog', { name: /getting started/i });
  const skipSetupButton = page.getByRole('button', { name: /skip setup/i });
  const isVisible = await onboardingDialog.isVisible().catch(() => false);

  if (!isVisible) {
    await skipSetupButton.waitFor({ state: 'visible', timeout: 2000 }).catch(() => undefined);
  }

  const shouldDismiss = await onboardingDialog.isVisible().catch(() => false);

  if (!shouldDismiss) {
    return;
  }

  await skipSetupButton.click();
  await expect(onboardingDialog).toBeHidden();
}

function toCollectionEntity(collection: (typeof mockCollections)[number]) {
  return {
    id: collection.id,
    data: {
      name: collection.name,
      description: collection.description,
      schemaType: collection.schemaType,
      status: collection.status,
      isActive: collection.status === 'active',
      entityCount: collection.entityCount,
      schema: collection.schema,
      tags: collection.tags,
      createdBy: collection.createdBy,
      createdAt: collection.createdAt,
      updatedAt: collection.updatedAt,
    },
    createdAt: collection.createdAt,
    updatedAt: collection.updatedAt,
  };
}

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
  await page.route(COLLECTION_LIST_ROUTE, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          entities: mockCollections.map(toCollectionEntity),
          count: mockCollections.length,
        }),
      });
    } else if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      const id = typeof body.id === 'string' ? body.id : `col-${Date.now()}`;
      const timestamp = new Date().toISOString();
      await route.fulfill({
        status: typeof body.id === 'string' ? 200 : 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id,
          collection: 'dc_collections',
          createdAt: timestamp,
          timestamp,
        }),
      });
    }
  });

  // Get collection by ID
  await page.route(COLLECTION_ITEM_ROUTE, async (route) => {
    const url = route.request().url();
    const id = url.split('/').pop()?.split('?')[0];
    const collection = mockCollections.find(c => c.id === id);

    if (route.request().method() === 'GET') {
      if (collection) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(toCollectionEntity(collection)),
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
          id,
          collection: 'dc_collections',
          createdAt: collection?.createdAt ?? new Date().toISOString(),
          timestamp: new Date().toISOString(),
        }),
      });
    } else if (route.request().method() === 'DELETE') {
      await route.fulfill({
        status: 204,
        body: '',
      });
    }
  });

  // Explicit deprecated CRUD-route coverage while canonical entity routes remain primary.
  await page.route('**/api/v1/collections', async (route) => {
    const legacyPath = '/api/v1/collections';
    const canonicalPath = '/api/v1/entities/dc_collections';
    warnDeprecatedRoute(legacyPath, canonicalPath);
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 308,
        headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
        body: '',
      });
    } else {
      await route.fulfill({
        status: 308,
        headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
        body: '',
      });
    }
  });

  await page.route('**/api/v1/collections/*', async (route) => {
    const url = route.request().url();
    const suffix = url.split('/api/v1/collections/')[1] ?? '';
    const legacyPath = `/api/v1/collections/${suffix}`;
    const canonicalPath = `/api/v1/entities/dc_collections/${suffix}`;
    warnDeprecatedRoute(legacyPath, canonicalPath);
    await route.fulfill({
      status: 308,
      headers: buildDeprecatedRouteHeaders(legacyPath, canonicalPath, canonicalPath),
      body: '',
    });
  });
}

/**
 * Mock pipelines API endpoints
 */
export async function mockWorkflowsAPI(page: Page) {
  // List workflows
  await page.route('**/api/v1/pipelines', async (route) => {
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

  // Get pipeline by ID
  await page.route('**/api/v1/pipelines/*', async (route) => {
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

  // Pipeline executions
  await page.route('**/api/v1/pipelines/*/executions', async (route) => {
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
 * Mock alerts API endpoints
 */
export async function mockAlertsAPI(page: Page) {
  await page.route(ALERT_LIST_ROUTE, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tenantId: 'test-tenant',
          alerts: mockAlerts,
          count: mockAlerts.length,
          timestamp: new Date('2026-04-18T00:00:00.000Z').toISOString(),
        }),
      });
    }
  });

  await page.route(ALERT_GROUPS_ROUTE, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tenantId: 'test-tenant',
          groups: mockAlertGroups,
          count: mockAlertGroups.length,
          timestamp: new Date('2026-04-18T00:00:00.000Z').toISOString(),
        }),
      });
      return;
    }

    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'accepted' }),
      });
    }
  });

  await page.route(ALERT_SUGGESTIONS_ROUTE, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tenantId: 'test-tenant',
          suggestions: mockAlertSuggestions,
          count: mockAlertSuggestions.length,
          timestamp: new Date('2026-04-18T00:00:00.000Z').toISOString(),
        }),
      });
      return;
    }

    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'accepted' }),
      });
    }
  });

  await page.route(ALERT_RULES_ROUTE, async (route) => {
    const method = route.request().method();
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          tenantId: 'test-tenant',
          rules: mockAlertRules,
          count: mockAlertRules.length,
          timestamp: new Date('2026-04-18T00:00:00.000Z').toISOString(),
        }),
      });
      return;
    }

    if (method === 'POST' || method === 'PUT') {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        status: method === 'POST' ? 201 : 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: typeof body.id === 'string' ? body.id : 'rule-created',
          name: typeof body.name === 'string' ? body.name : 'Generated Rule',
          description: typeof body.description === 'string' ? body.description : 'Generated during Playwright test',
          enabled: typeof body.enabled === 'boolean' ? body.enabled : true,
          severity: typeof body.severity === 'string' ? body.severity : 'warning',
          conditionType: typeof body.conditionType === 'string' ? body.conditionType : 'threshold',
          metric: typeof body.metric === 'string' ? body.metric : 'workflow.error_rate',
          operator: typeof body.operator === 'string' ? body.operator : 'gt',
          threshold: typeof body.threshold === 'number' ? body.threshold : 1,
          duration: typeof body.duration === 'number' ? body.duration : 5,
          channels: Array.isArray(body.channels) ? body.channels : ['email'],
          recipients: Array.isArray(body.recipients) ? body.recipients : ['ops@datacloud.example'],
          webhookUrl: typeof body.webhookUrl === 'string' ? body.webhookUrl : undefined,
        }),
      });
      return;
    }

    if (method === 'DELETE') {
      await route.fulfill({
        status: 204,
        body: '',
      });
    }
  });

  await page.route('**/api/v1/alerts/*', async (route) => {
    const path = route.request().url().split('/api/v1/alerts/')[1] ?? '';
    const id = path.split('?')[0].split('/')[0];
    const alert = mockAlerts.find((a) => a.id === id);
    const method = route.request().method();

    if (method === 'GET') {
      await route.fulfill(
        alert
          ? { status: 200, contentType: 'application/json', body: JSON.stringify(alert) }
          : { status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Alert not found' }) }
      );
      return;
    }

    if (method === 'POST') {
      await route.fulfill(
        alert
          ? {
              status: 200,
              contentType: 'application/json',
              body: JSON.stringify({
                ...alert,
                status: path.includes('/resolve') ? 'resolved' : 'acknowledged',
                acknowledgedAt: path.includes('/acknowledge') ? new Date('2026-04-18T00:00:00.000Z').toISOString() : alert.acknowledgedAt,
                resolvedAt: path.includes('/resolve') ? new Date('2026-04-18T00:00:00.000Z').toISOString() : alert.resolvedAt,
              }),
            }
          : { status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Alert not found' }) }
      );
    }
  });
}

/**
 * Mock SQL workspace runtime endpoints.
 */
export async function mockQueryWorkspaceAPI(page: Page) {
  for (const routePattern of RUNTIME_TRUTH_ROUTES) {
    await page.route(routePattern, async (route) => {
      // DC-P1.12: Removed compatibility route handling; only canonical /api/v1/surfaces routes remain
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            generatedAt: '2026-04-18T00:00:00.000Z',
            capabilities: {
              analytics: 'ACTIVE',
              trino: { status: 'DEGRADED', reason: 'Trino is not configured for this environment.' },
              ai_assist: { status: 'DEGRADED', reason: 'AI assist is running in fallback mode.' },
            },
          },
          meta: {
            requestId: 'req-query-e2e',
            tenantId: 'test-tenant',
          },
        }),
      });
    });
  }

  for (const routePattern of SURFACES_SCHEMA_ROUTES) {
    await page.route(routePattern, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            version: '1.0.0',
            metadata: {
              description: 'Runtime capability schema for E2E tests',
              last_updated: '2026-05-08',
              generators: ['playwright-api-mocks'],
            },
            kernel_capabilities: [],
            data_cloud_capabilities: [],
            aep_capabilities: [],
            ui_feature_gates: [],
            status_definitions: {
              stable: { description: 'stable', ui_indicator: 'green', allowed_in_production: true },
              preview: { description: 'preview', ui_indicator: 'amber', allowed_in_production: false },
              deprecated: { description: 'deprecated', ui_indicator: 'red', allowed_in_production: false },
              experimental: { description: 'experimental', ui_indicator: 'purple', allowed_in_production: false },
            },
          },
        }),
      });
    });
  }

  // DC-P1.12: Removed CAPABILITIES_SCHEMA_ROUTES loop; use canonical SURFACES_SCHEMA_ROUTES only

  await page.route(USER_ACTIVITY_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        activities: [
          {
            id: 'activity-1',
            action: 'query',
            target: 'Reviewed products performance',
            timestamp: '2026-04-18T00:00:00.000Z',
            type: 'query',
          },
        ],
        continueWorking: [
          {
            id: 'continue-1',
            name: 'Products',
            type: 'collection',
            lastAccessed: '5 minutes ago',
            path: '/data/col-1',
          },
        ],
      }),
    });
  });

  await page.route(ANALYTICS_SUGGEST_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          queries: [
            {
              name: 'Top products by count',
              template: 'SELECT name, COUNT(*) AS total\nFROM products\nGROUP BY name\nORDER BY total DESC\nLIMIT 10;',
              explanation: 'Summarize product volume for the current collection context.',
            },
          ],
        },
        ai: {
          fallback: false,
          confidence: 0.82,
        },
      }),
    });
  });

  await page.route(ANALYTICS_QUERY_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        queryId: 'analytics-1',
        queryType: 'analytics',
        rowCount: 1,
        columnCount: 2,
        rows: [{ name: 'Product A', total: 42 }],
        executionTimeMs: 22,
        optimized: true,
        timestamp: '2026-04-18T00:00:00.000Z',
      }),
    });
  });

  await page.route(ANALYTICS_EXPLAIN_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        queryId: 'analytics-plan-1',
        queryType: 'AGGREGATE',
        dataSources: ['products'],
        estimatedCost: 144,
        optimized: true,
        explain: true,
        timestamp: '2026-04-18T00:00:00.000Z',
      }),
    });
  });

  await page.route(FEDERATED_QUERY_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        queryId: 'federated-1',
        queryType: 'federated',
        rowCount: 1,
        columnCount: 1,
        rows: [{ region: 'global' }],
        executionTimeMs: 41,
        optimized: true,
        timestamp: '2026-04-18T00:00:00.000Z',
      }),
    });
  });
}

/**
 * Mock Insights runtime endpoints, including operator AI truth telemetry.
 */
export async function mockInsightsAPI(page: Page) {
  await mockCollectionsAPI(page);
  await mockWorkflowsAPI(page);

  for (const routePattern of RUNTIME_TRUTH_ROUTES) {
    await page.route(routePattern, async (route) => {
      // DC-P1.12: Removed compatibility route handling; only canonical /api/v1/surfaces routes remain
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            generatedAt: '2026-04-18T12:40:00Z',
            capabilities: {
              analytics: 'ACTIVE',
              brain: 'ACTIVE',
            },
          },
          meta: {
            requestId: 'req-runtime',
            tenantId: 'test-tenant',
            timestamp: '2026-04-18T12:40:00Z',
            apiVersion: 'v1',
          },
        }),
      });
    });
  }

  await page.route(BRAIN_STATS_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        totalRecordsProcessed: 1234,
        activePatterns: 7,
        hotTierRecords: 55,
        timestamp: '2026-04-18T12:34:00Z',
      }),
    });
  });

  await page.route('**/api/v1/collections/*/cost-report', async (route) => {
    const collectionId = route.request().url().split('/collections/')[1]?.split('/cost-report')[0] ?? 'unknown';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        collectionId,
        currency: 'DCC',
        totalCostDccPerDay: collectionId === 'col-1' ? 120 : 45,
        lastUpdatedAt: '2026-04-18T12:00:00Z',
      }),
    });
  });

  await page.route(ANALYTICS_SUGGEST_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          queries: [
            {
              name: 'Cache repeated analytics query',
              template: 'query hot path',
              explanation: 'query hot path',
            },
          ],
        },
        ai: {
          confidence: 0.87,
          fallback: false,
          model: 'datacloud-assist-v1',
          reasons: ['query'],
        },
      }),
    });
  });

  await page.route(AI_QUALITY_SUMMARY_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          generatedAt: '2026-04-18T12:40:00Z',
          scope: 'launcher-process',
          summary: {
            requestCount: 6,
            fallbackCount: 2,
            fallbackRate: 0.333,
            llmConfigured: true,
          },
          types: [
            {
              type: 'analytics_suggest',
              label: 'Analytics suggestions',
              route: '/api/v1/analytics/suggest',
              requestCount: 3,
              fallbackCount: 1,
              fallbackRate: 0.333,
              meanConfidence: 0.84,
              provenanceMode: 'ai-envelope',
              reviewGuidance: 'Fallback-heavy analytics suggestions should trigger manual SQL review before execution.',
            },
            {
              type: 'pipeline_draft',
              label: 'Workflow draft generation',
              route: '/api/v1/pipelines/draft',
              requestCount: 2,
              fallbackCount: 1,
              fallbackRate: 0.5,
              meanConfidence: 0.64,
              provenanceMode: 'ai-envelope-and-draft-provenance',
              reviewGuidance: 'Review low-confidence drafts or any fallback-generated workflow before saving.',
            },
          ],
        },
        meta: {
          requestId: 'req-ai-quality',
          tenantId: 'test-tenant',
          timestamp: '2026-04-18T12:40:00Z',
          apiVersion: 'v1',
        },
      }),
    });
  });
}

/**
 * Mock Trust Center governance runtime endpoints.
 */
export async function mockGovernanceAPI(page: Page) {
  await page.route(GOVERNANCE_SUMMARY_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          tenantId: 'test-tenant',
          collectionsTotal: 12,
          collectionsClassified: 9,
          collectionsUnclassified: 3,
          piiFieldsRegistered: 2,
          legalHoldsActive: 1,
          retentionExpirationsIn30Days: 2,
          lastAuditAt: '2026-04-18T00:00:00.000Z',
          auditEventsIn30Days: 18,
          authFailuresIn30Days: 1,
          redactionsIn30Days: 4,
          purgesIn30Days: 1,
          recentAuditEvents: [
            {
              id: 'evt-1',
              timestamp: '2026-04-18T00:00:00.000Z',
              userId: 'auditor-1',
              userName: 'Auditor',
              action: 'PII_SCAN',
              resourceType: 'governance',
              resourceId: 'test-tenant',
              outcome: 'SUCCESS',
            },
          ],
          complianceStatus: 'REVIEW_REQUIRED',
          generatedAt: '2026-04-18T00:05:00.000Z',
        },
        meta: {
          tenantId: 'test-tenant',
          requestId: 'req-governance-summary',
          timestamp: '2026-04-18T00:05:00.000Z',
          apiVersion: 'v1',
        },
      }),
    });
  });

  await page.route(GOVERNANCE_PII_FIELDS_ROUTE, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          globalFields: ['email'],
          tenantFields: ['ssn'],
          effectiveCount: 2,
        },
        meta: {
          tenantId: 'test-tenant',
          requestId: 'req-governance-pii',
          timestamp: '2026-04-18T00:05:00.000Z',
          apiVersion: 'v1',
        },
      }),
    });
  });

  await page.route(GOVERNANCE_CLASSIFY_ROUTE, async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          collection: typeof body.collection === 'string' ? body.collection : 'customers',
          tier: typeof body.tier === 'string' ? body.tier : 'compliance',
          retentionDays: 2555,
          expiresAt: '2033-04-18T00:05:00.000Z',
          classifiedAt: '2026-04-18T00:05:00.000Z',
          classifiedBy: 'test-tenant',
          reason: typeof body.reason === 'string' ? body.reason : 'GDPR Article 17 review',
          piiFields: Array.isArray(body.piiFields) ? body.piiFields : ['email'],
          status: 'CLASSIFIED',
        },
        meta: {
          tenantId: 'test-tenant',
          requestId: 'req-governance-classify',
        },
      }),
    });
  });

  await page.route(GOVERNANCE_RETENTION_POLICY_ROUTE, async (route) => {
    const url = new URL(route.request().url());
    const collection = url.searchParams.get('collection') ?? 'customers';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          collection,
          tier: 'standard',
          retentionDays: 365,
          legalHolds: [],
          piiFields: ['email'],
          lastClassifiedAt: '2026-04-01T00:00:00.000Z',
          expiresAt: '2027-04-01T00:00:00.000Z',
          status: 'DEFAULT',
        },
        meta: {
          tenantId: 'test-tenant',
          requestId: 'req-governance-policy',
        },
      }),
    });
  });

  await page.route(GOVERNANCE_REDACT_ROUTE, async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>;
    const requestedFields = Array.isArray(body.fields) ? body.fields.filter((field): field is string => typeof field === 'string') : [];
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          collection: typeof body.collection === 'string' ? body.collection : 'customers',
          entityId: typeof body.entityId === 'string' ? body.entityId : 'ent-123',
          redactedFields: requestedFields.length > 0 ? requestedFields.slice(0, 1) : ['email'],
          requestedFields: requestedFields.length > 0 ? requestedFields : ['email'],
          reason: typeof body.reason === 'string' ? body.reason : 'Customer privacy request',
          status: 'REDACTED',
          redactedAt: '2026-04-18T00:05:00.000Z',
        },
        meta: {
          tenantId: 'test-tenant',
          requestId: 'req-governance-redact',
        },
      }),
    });
  });

  await page.route(GOVERNANCE_PURGE_ROUTE, async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>;
    const isDryRun = body.dryRun !== false;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: isDryRun
          ? {
              collection: typeof body.collection === 'string' ? body.collection : 'customers',
              dryRun: true,
              status: 'DRY_RUN_COMPLETE',
              confirmationToken: 'confirm-123',
              tokenExpiresInSec: 900,
              estimatedRows: 24,
              sampleEntityIds: ['cust-1', 'cust-2'],
              requestId: 'req-governance-purge-dry-run',
            }
          : {
              collection: typeof body.collection === 'string' ? body.collection : 'customers',
              dryRun: false,
              status: 'PURGE_COMPLETED',
              deletedRows: 24,
              requestedRows: 24,
              failedRows: 0,
              deletedEntityIds: ['cust-1', 'cust-2'],
              completedAt: '2026-04-18T00:10:00.000Z',
              requestId: 'req-governance-purge-execute',
            },
        meta: {
          tenantId: 'test-tenant',
        },
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
  await disableOnboardingWizard(page);
  await mockCollectionsAPI(page);
  await mockWorkflowsAPI(page);
  await mockAlertsAPI(page);
  await mockGovernanceAPI(page);
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
