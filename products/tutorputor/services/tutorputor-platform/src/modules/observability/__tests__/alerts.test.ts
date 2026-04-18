/**
 * @doc.type test
 * @doc.purpose Unit tests for AlertService - rule evaluation, cooldowns, acknowledge, resolve
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import { AlertService, type AlertRule } from "../alerts";

function makePrisma() {
  return {
    $queryRaw: vi.fn().mockResolvedValue([]),
    $executeRaw: vi.fn().mockResolvedValue(1),
  };
}

function makeAlertRule(overrides: Partial<AlertRule> = {}): AlertRule {
  return {
    ruleId: "rule-test-1",
    name: "High Latency",
    description: "Average latency exceeds threshold",
    enabled: true,
    severity: "warning",
    condition: {
      metric: "avgLatencyMs",
      operator: "gt",
      threshold: 1000,
    },
    cooldownSeconds: 60,
    notificationChannels: [],
    ...overrides,
  };
}

describe("AlertService", () => {
  let service: AlertService;

  beforeEach(() => {
    service = new AlertService(makePrisma() as never);
    vi.clearAllMocks();
  });

  describe("setAlertRule / getAlertRules", () => {
    it("stores and retrieves alert rules", () => {
      const rule = makeAlertRule();
      service.setAlertRule(rule);

      const rules = service.getAlertRules();
      const found = rules.find((r) => r.ruleId === rule.ruleId);
      expect(found).toBeDefined();
      expect(found?.name).toBe("High Latency");
    });

    it("overwrites existing rule on update", () => {
      const rule = makeAlertRule();
      service.setAlertRule(rule);
      service.setAlertRule({ ...rule, name: "Updated Latency Rule" });

      const rules = service.getAlertRules();
      const found = rules.filter((r) => r.ruleId === rule.ruleId);
      expect(found).toHaveLength(1);
      expect(found[0]?.name).toBe("Updated Latency Rule");
    });

    it("removes a rule with removeAlertRule", () => {
      const rule = makeAlertRule();
      service.setAlertRule(rule);
      service.removeAlertRule(rule.ruleId);

      const rules = service.getAlertRules();
      expect(rules.find((r) => r.ruleId === rule.ruleId)).toBeUndefined();
    });
  });

  describe("evaluateMetrics", () => {
    it("triggers alert when condition is met", () => {
      const rule = makeAlertRule({ ruleId: "rule-eval-1" });
      service.setAlertRule(rule);

      const alerts = service.evaluateMetrics({ avgLatencyMs: 2000 });

      expect(alerts).toHaveLength(1);
      expect(alerts[0]?.ruleId).toBe("rule-eval-1");
      expect(alerts[0]?.severity).toBe("warning");
      expect(alerts[0]?.status).toBe("active");
    });

    it("does not trigger when condition is not met", () => {
      const rule = makeAlertRule({ ruleId: "rule-eval-2" });
      service.setAlertRule(rule);

      const alerts = service.evaluateMetrics({ avgLatencyMs: 500 }); // below 1000 threshold

      expect(alerts).toHaveLength(0);
    });

    it("respects cooldown after first trigger", () => {
      const rule = makeAlertRule({ ruleId: "rule-cooldown", cooldownSeconds: 3600 });
      service.setAlertRule(rule);

      const first = service.evaluateMetrics({ avgLatencyMs: 2000 });
      const second = service.evaluateMetrics({ avgLatencyMs: 2000 });

      expect(first).toHaveLength(1);
      expect(second).toHaveLength(0); // in cooldown
    });

    it("skips disabled rules", () => {
      const rule = makeAlertRule({ ruleId: "rule-disabled", enabled: false });
      service.setAlertRule(rule);

      const alerts = service.evaluateMetrics({ avgLatencyMs: 9999 });

      expect(alerts).toHaveLength(0);
    });

    it("handles lt operator correctly", () => {
      const rule = makeAlertRule({
        ruleId: "rule-lt",
        condition: { metric: "successRate", operator: "lt", threshold: 0.9 },
      });
      service.setAlertRule(rule);

      const belowThreshold = service.evaluateMetrics({ successRate: 0.7 });
      expect(belowThreshold).toHaveLength(1);

      const aboveThreshold = service.evaluateMetrics({ successRate: 0.95 });
      expect(aboveThreshold).toHaveLength(0); // cooldown OR condition not met
    });
  });

  describe("getActiveAlerts", () => {
    it("returns all active alerts", () => {
      const rule = makeAlertRule({ ruleId: "rule-active-check" });
      service.setAlertRule(rule);
      service.evaluateMetrics({ avgLatencyMs: 5000 });

      const active = service.getActiveAlerts();
      expect(active.length).toBeGreaterThan(0);
      expect(active.every((a) => a.status === "active")).toBe(true);
    });

    it("filters by severity", () => {
      const warnRule = makeAlertRule({ ruleId: "rule-warn", severity: "warning" });
      const critRule = makeAlertRule({
        ruleId: "rule-crit",
        severity: "critical",
        condition: { metric: "errorRate", operator: "gt", threshold: 0.1 },
      });
      service.setAlertRule(warnRule);
      service.setAlertRule(critRule);
      service.evaluateMetrics({ avgLatencyMs: 5000, errorRate: 0.5 });

      const critAlerts = service.getActiveAlerts("critical");
      expect(critAlerts.every((a) => a.severity === "critical")).toBe(true);
    });
  });

  describe("acknowledgeAlert", () => {
    it("acknowledges an active alert", async () => {
      const rule = makeAlertRule({ ruleId: "rule-ack" });
      service.setAlertRule(rule);
      const [triggered] = service.evaluateMetrics({ avgLatencyMs: 5000 });

      expect(triggered).toBeDefined();

      const acknowledged = await service.acknowledgeAlert(
        triggered!.alertId,
        "admin@example.com",
      );

      expect(acknowledged).not.toBeNull();
      expect(acknowledged?.status).toBe("acknowledged");
      expect(acknowledged?.acknowledgedBy).toBe("admin@example.com");
      expect(acknowledged?.acknowledgedAt).toBeInstanceOf(Date);
    });

    it("returns null for unknown alertId", async () => {
      const result = await service.acknowledgeAlert("nonexistent", "admin");
      expect(result).toBeNull();
    });
  });

  describe("resolveAlert", () => {
    it("resolves an active alert", async () => {
      const rule = makeAlertRule({ ruleId: "rule-resolve" });
      service.setAlertRule(rule);
      const [triggered] = service.evaluateMetrics({ avgLatencyMs: 5000 });

      expect(triggered).toBeDefined();

      const resolved = await service.resolveAlert(triggered!.alertId);

      expect(resolved).not.toBeNull();
      expect(resolved?.status).toBe("resolved");
      expect(resolved?.resolvedAt).toBeInstanceOf(Date);
    });

    it("returns null for unknown alertId", async () => {
      const result = await service.resolveAlert("nonexistent");
      expect(result).toBeNull();
    });
  });
});
