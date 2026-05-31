import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockApiClient, mockRequireTenantId, mockIsAlertsSurfaceEnabled } =
  vi.hoisted(() => ({
    mockApiClient: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
    mockRequireTenantId: vi.fn(() => TEST_TENANT_ID),
    mockIsAlertsSurfaceEnabled: vi.fn(() => true),
  }));

class MockEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;

  readonly url: string;
  readonly withCredentials = false;
  readonly readyState = MockEventSource.CONNECTING;
  onerror: ((this: EventSource, ev: Event) => unknown) | null = null;
  onmessage: ((this: EventSource, ev: MessageEvent) => unknown) | null = null;
  onopen: ((this: EventSource, ev: Event) => unknown) | null = null;

  constructor(url: string) {
    this.url = url;
  }

  addEventListener(): void {}

  removeEventListener(): void {}

  dispatchEvent(): boolean {
    return false;
  }

  close(): void {}
}

vi.mock("@/lib/api/client", () => ({
  apiClient: mockApiClient,
}));

vi.mock("@/lib/auth/session", () => ({
  default: {
    requireTenantId: mockRequireTenantId,
  },
}));

vi.mock("@/lib/feature-gates", () => ({
  isAlertsSurfaceEnabled: mockIsAlertsSurfaceEnabled,
  isAiAlertGroupingFallbackEnabled: () => true,
}));

import { alertsService } from "@/api/alerts.service";
import { ALERTS_UNSUPPORTED_MESSAGE } from "@/lib/runtime-boundaries";

describe("alertsService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsAlertsSurfaceEnabled.mockReturnValue(true);
    vi.stubGlobal(
      "EventSource",
      MockEventSource as unknown as typeof EventSource,
    );
    vi.stubEnv("VITE_API_URL", "/api/v1");
  });

  it("fails closed before network calls when alerts surface gate is disabled", async () => {
    mockIsAlertsSurfaceEnabled.mockReturnValue(false);

    await expect(alertsService.getAlerts()).rejects.toThrow(
      ALERTS_UNSUPPORTED_MESSAGE,
    );
    await expect(alertsService.acknowledgeAlert("alert-1")).rejects.toThrow(
      ALERTS_UNSUPPORTED_MESSAGE,
    );
    await expect(alertsService.resolveAlert("alert-1")).rejects.toThrow(
      ALERTS_UNSUPPORTED_MESSAGE,
    );

    expect(mockApiClient.get).not.toHaveBeenCalled();
    expect(mockApiClient.post).not.toHaveBeenCalled();
  });

  it("lists alerts from the canonical launcher envelope", async () => {
    mockApiClient.get.mockResolvedValue({
      tenantId: TEST_TENANT_ID,
      alerts: [
        {
          id: "alert-1",
          title: "Kafka lag spike",
          description: "Consumer lag exceeded threshold",
          severity: "critical",
          status: "active",
          source: "kafka",
          createdAt: "2026-04-18T10:00:00Z",
        },
      ],
      count: 1,
      timestamp: "2026-04-18T10:01:00Z",
    });

    const alerts = await alertsService.getAlerts({
      severity: "critical",
      limit: 25,
    });

    expect(mockApiClient.get).toHaveBeenCalledWith("/alerts", {
      params: {
        severity: "critical",
        limit: 25,
        tenantId: TEST_TENANT_ID,
      },
      headers: { "X-Tenant-ID": TEST_TENANT_ID },
    });
    expect(alerts).toEqual([
      expect.objectContaining({
        id: "alert-1",
        severity: "critical",
        status: "active",
      }),
    ]);
  });

  it("maps group and suggestion envelopes into page-ready data", async () => {
    mockApiClient.get
      .mockResolvedValueOnce({
        tenantId: TEST_TENANT_ID,
        groups: [
          {
            id: "group-kafka",
            title: "Kafka degradation",
            rootCause: "Kafka",
            alertIds: ["alert-1", "alert-2"],
            aiConfidence: 0.84,
            suggestedAction: "Restart consumer group",
            suggestedActionType: "auto",
          },
        ],
        count: 1,
        timestamp: "2026-04-18T10:05:00Z",
      })
      .mockResolvedValueOnce({
        tenantId: TEST_TENANT_ID,
        suggestions: [
          {
            id: "suggestion-alert-1",
            alertId: "alert-1",
            suggestion: "Restart consumer group",
            confidence: 0.91,
            canAutoResolve: true,
            steps: ["Inspect lag", "Restart consumer"],
          },
        ],
        count: 1,
        timestamp: "2026-04-18T10:06:00Z",
      });

    const groups = await alertsService.getAlertGroups();
    const suggestions = await alertsService.getResolutionSuggestions();

    expect(groups[0]?.id).toBe("group-kafka");
    expect(suggestions[0]?.steps).toEqual(["Inspect lag", "Restart consumer"]);
  });

  it("builds heuristic fallback groups when group endpoint returns empty list", async () => {
    mockApiClient.get
      .mockResolvedValueOnce({
        tenantId: TEST_TENANT_ID,
        groups: [],
        count: 0,
        timestamp: "2026-04-24T11:00:00Z",
      })
      .mockResolvedValueOnce({
        tenantId: TEST_TENANT_ID,
        alerts: [
          {
            id: "alert-1",
            title: "Kafka lag spike",
            description: "Consumer lag high",
            severity: "critical",
            status: "active",
            source: "kafka",
            createdAt: "2026-04-24T11:00:00Z",
          },
          {
            id: "alert-2",
            title: "Kafka lag spike",
            description: "Consumer lag still elevated",
            severity: "critical",
            status: "active",
            source: "kafka",
            createdAt: "2026-04-24T11:01:00Z",
          },
        ],
        count: 2,
        timestamp: "2026-04-24T11:02:00Z",
      });

    const groups = await alertsService.getAlertGroups();

    expect(groups.length).toBe(1);
    expect(groups[0]?.id.startsWith("fallback-group-")).toBe(true);
    expect(groups[0]?.alertIds).toEqual(["alert-1", "alert-2"]);
    expect(groups[0]?.suggestedActionType).toBe("manual");
  });

  it("acknowledges and resolves alerts through canonical mutation routes", async () => {
    mockApiClient.post
      .mockResolvedValueOnce({
        id: "alert-1",
        title: "Kafka lag spike",
        description: "Consumer lag exceeded threshold",
        severity: "critical",
        status: "acknowledged",
        source: "kafka",
        createdAt: "2026-04-18T10:00:00Z",
        acknowledgedAt: "2026-04-18T10:02:00Z",
      })
      .mockResolvedValueOnce({
        id: "alert-1",
        title: "Kafka lag spike",
        description: "Consumer lag exceeded threshold",
        severity: "critical",
        status: "resolved",
        source: "kafka",
        createdAt: "2026-04-18T10:00:00Z",
        resolvedAt: "2026-04-18T10:03:00Z",
      });

    const acknowledged = await alertsService.acknowledgeAlert("alert-1");
    const resolved = await alertsService.resolveAlert("alert-1");

    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      1,
      "/alerts/alert-1/acknowledge",
      {},
      {
        params: { tenantId: TEST_TENANT_ID },
        headers: { "X-Tenant-ID": TEST_TENANT_ID },
      },
    );
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      2,
      "/alerts/alert-1/resolve",
      {},
      {
        params: { tenantId: TEST_TENANT_ID },
        headers: { "X-Tenant-ID": TEST_TENANT_ID },
      },
    );
    expect(acknowledged.status).toBe("acknowledged");
    expect(resolved.status).toBe("resolved");
  });

  it("applies group and suggestion actions without inventing extra client payloads", async () => {
    mockApiClient.post.mockResolvedValue({ ok: true });

    await alertsService.resolveGroup("group-kafka");
    await alertsService.applySuggestion("suggestion-alert-1");

    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      1,
      "/alerts/groups/group-kafka/resolve",
      {},
      {
        params: { tenantId: TEST_TENANT_ID },
        headers: { "X-Tenant-ID": TEST_TENANT_ID },
      },
    );
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      2,
      "/alerts/suggestions/suggestion-alert-1/apply",
      {},
      {
        params: { tenantId: TEST_TENANT_ID },
        headers: { "X-Tenant-ID": TEST_TENANT_ID },
      },
    );
  });

  it("creates, updates, and deletes alert rules through the live alerts routes", async () => {
    mockApiClient.get.mockResolvedValue({
      tenantId: TEST_TENANT_ID,
      rules: [
        {
          id: "rule-1",
          name: "High CPU",
          description: "Detect CPU pressure",
          enabled: true,
          severity: "warning",
          conditionType: "threshold",
          metric: "cpu_usage",
          operator: "gt",
          threshold: 85,
          duration: 5,
          channels: ["email"],
          recipients: ["ops@ghatana.dev"],
        },
      ],
      count: 1,
      timestamp: "2026-04-18T10:10:00Z",
    });
    mockApiClient.post.mockResolvedValue({
      id: "rule-2",
      name: "Kafka lag",
      enabled: true,
      severity: "critical",
      conditionType: "threshold",
      metric: "queue_depth",
      operator: "gt",
      threshold: 100,
      duration: 10,
      channels: ["slack"],
    });
    mockApiClient.put.mockResolvedValue({
      id: "rule-2",
      name: "Kafka lag",
      enabled: false,
      severity: "critical",
      conditionType: "threshold",
      metric: "queue_depth",
      operator: "gt",
      threshold: 100,
      duration: 10,
      channels: ["slack"],
    });
    mockApiClient.delete.mockResolvedValue(undefined);

    const rules = await alertsService.listAlertRules();
    const created = await alertsService.createAlertRule({
      name: "Kafka lag",
      enabled: true,
      severity: "critical",
      conditionType: "threshold",
      metric: "queue_depth",
      operator: "gt",
      threshold: 100,
      duration: 10,
      channels: ["slack"],
    });
    const updated = await alertsService.updateAlertRule("rule-2", {
      enabled: false,
    });
    await alertsService.deleteAlertRule("rule-2");

    expect(rules[0]?.id).toBe("rule-1");
    expect(created.id).toBe("rule-2");
    expect(updated.enabled).toBe(false);
    expect(mockApiClient.delete).toHaveBeenCalledWith("/alerts/rules/rule-2", {
      params: { tenantId: TEST_TENANT_ID },
      headers: { "X-Tenant-ID": TEST_TENANT_ID },
    });
  });

  it("opens an alerts stream on the canonical SSE route", () => {
    const stream = alertsService.openStream();

    expect(stream.url).toBe(
      "/api/v1/alerts/stream?tenantId=tenant-alpha&types=alert.acknowledged%2Calert.resolved%2Calert.group.resolved%2Calert.suggestion.applied%2Calert.rule.created%2Calert.rule.updated%2Calert.rule.deleted",
    );
  });
});
