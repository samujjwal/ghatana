/**
 * Resource Access Helpers Tests
 *
 * @doc.type test-suite
 * @doc.purpose Validate tenant-scoped lookup helpers and self-or-privileged checks
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, expect, it } from "vitest";
import {
  assertSameTenant,
  buildSensitiveOperationAuditEntry,
  buildTenantScopedWhere,
  isSelfOrPrivileged,
  type ResourceActor,
} from "../resource-access-helpers.js";

describe("resource-access-helpers", () => {
  describe("buildTenantScopedWhere", () => {
    it("always includes both id and tenantId", () => {
      const where = buildTenantScopedWhere("tenant-1", "resource-1");
      expect(where).toEqual({ id: "resource-1", tenantId: "tenant-1" });
    });
  });

  describe("isSelfOrPrivileged", () => {
    it("returns true when actor owns resource", () => {
      const actor: ResourceActor = {
        userId: "user-1",
        tenantId: "tenant-1",
        roles: ["student"],
      };

      expect(isSelfOrPrivileged(actor, "user-1")).toBe(true);
    });

    it("returns true for admin when actor is not owner", () => {
      const actor: ResourceActor = {
        userId: "admin-1",
        tenantId: "tenant-1",
        roles: ["admin"],
      };

      expect(isSelfOrPrivileged(actor, "user-2")).toBe(true);
    });

    it("returns true for superadmin when actor is not owner", () => {
      const actor: ResourceActor = {
        userId: "super-1",
        tenantId: "tenant-1",
        roles: ["superadmin"],
      };

      expect(isSelfOrPrivileged(actor, "user-2")).toBe(true);
    });

    it("returns false for non-owner, non-privileged actor", () => {
      const actor: ResourceActor = {
        userId: "teacher-1",
        tenantId: "tenant-1",
        roles: ["teacher"],
      };

      expect(isSelfOrPrivileged(actor, "user-2")).toBe(false);
    });
  });

  describe("assertSameTenant", () => {
    it("does not throw for same tenant", () => {
      expect(() => assertSameTenant("tenant-1", "tenant-1", "update user_profile")).not.toThrow();
    });

    it("throws for cross-tenant access", () => {
      expect(() => assertSameTenant("tenant-1", "tenant-2", "delete experience")).toThrow(
        "Cross-tenant delete experience denied",
      );
    });
  });

  describe("buildSensitiveOperationAuditEntry", () => {
    it("creates complete audit payload for ALLOW decision", () => {
      const entry = buildSensitiveOperationAuditEntry({
        actorId: "admin-1",
        actorTenantId: "tenant-1",
        targetResourceType: "experience",
        targetResourceId: "exp-1",
        operation: "publish",
        decision: "ALLOW",
        reason: "Policy matched",
        correlationId: "corr-123",
        metadata: { riskLevel: "low", requiresReview: false },
      });

      expect(entry.actorId).toBe("admin-1");
      expect(entry.actorTenantId).toBe("tenant-1");
      expect(entry.operation).toBe("publish");
      expect(entry.decision).toBe("ALLOW");
      expect(entry.correlationId).toBe("corr-123");
      expect(entry.timestamp).toBeTruthy();
      expect(() => new Date(entry.timestamp)).not.toThrow();
    });

    it("supports DENY decisions with empty metadata", () => {
      const entry = buildSensitiveOperationAuditEntry({
        actorId: "student-1",
        actorTenantId: "tenant-1",
        targetResourceType: "generation_request",
        targetResourceId: "req-1",
        operation: "create",
        decision: "DENY",
        reason: "Insufficient role",
        correlationId: undefined,
        metadata: {},
      });

      expect(entry.decision).toBe("DENY");
      expect(entry.reason).toBe("Insufficient role");
      expect(entry.metadata).toEqual({});
    });
  });
});
