/**
 * ABAC Policy Engine Tests
 *
 * Covers:
 * - Admin full access within tenant
 * - Creator generation/experience access
 * - Teacher read/review access
 * - Student read with consent (denied without consent)
 * - Cross-tenant isolation (always denied)
 * - Owner-read-own policy
 * - Custom policy injection
 * - Audit entry is always produced
 * - Batch evaluation
 *
 * @doc.type test
 * @doc.purpose Prove ABAC policy engine correctness for all roles and scenarios
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, expect, it } from "vitest";
import {
  AbacPolicyEngine,
  type AbacActor,
  type AbacResource,
  type AbacAction,
  type AbacPolicy,
} from "../abac-policy-engine.js";

// ─── Test helpers ──────────────────────────────────────────────────────────────

const TENANT_A = "tenant-A";
const TENANT_B = "tenant-B";

function makeActor(overrides: Partial<AbacActor> = {}): AbacActor {
  return {
    userId: "user-1",
    tenantId: TENANT_A,
    roles: ["student"],
    consentFlags: [],
    attributes: {},
    ...overrides,
  };
}

function makeResource(overrides: Partial<AbacResource> = {}): AbacResource {
  return {
    type: "experience",
    id: "exp-1",
    ownerTenantId: TENANT_A,
    ownerUserId: undefined,
    attributes: {},
    ...overrides,
  };
}

const engine = new AbacPolicyEngine();

// ─── Tenant Isolation ─────────────────────────────────────────────────────────

describe("ABAC - Tenant Isolation", () => {
  it("denies access when actor is in a different tenant than the resource", () => {
    const result = engine.evaluate(
      makeActor({ tenantId: TENANT_A }),
      makeResource({ ownerTenantId: TENANT_B }),
      "read",
    );
    expect(result.decision).toBe("DENY");
    expect(result.reason.toLowerCase()).toContain("cross-tenant");
  });

  it("allows read within same tenant for admin", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["admin"] }),
      makeResource(),
      "read",
    );
    expect(result.decision).toBe("ALLOW");
  });

  it("denies admin access to other tenant resources", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["admin"], tenantId: TENANT_A }),
      makeResource({ ownerTenantId: TENANT_B }),
      "admin",
    );
    expect(result.decision).toBe("DENY");
  });
});

// ─── Admin Role ───────────────────────────────────────────────────────────────

describe("ABAC - Admin Full Access", () => {
  it("admin can read any resource in their tenant", () => {
    for (const action of ["read", "create", "update", "delete", "publish", "admin"] as AbacAction[]) {
      const result = engine.evaluate(
        makeActor({ roles: ["admin"] }),
        makeResource(),
        action,
      );
      expect(result.decision).toBe("ALLOW");
    }
  });

  it("admin full-access matches policy id: builtin:admin-full-access", () => {
    const result = engine.evaluate(makeActor({ roles: ["admin"] }), makeResource(), "delete");
    expect(result.matchedPolicyId).toBe("builtin:admin-full-access");
  });

  it("superadmin has full access in-tenant", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["superadmin"] }),
      makeResource({ type: "tenant_config" }),
      "admin",
    );
    expect(result.decision).toBe("ALLOW");
    expect(result.matchedPolicyId).toBe("builtin:superadmin-full-access");
  });
});

// ─── Creator Role ─────────────────────────────────────────────────────────────

describe("ABAC - Creator Access", () => {
  it("creator can create generation_requests in their tenant", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["creator"] }),
      makeResource({ type: "generation_request" }),
      "create",
    );
    expect(result.decision).toBe("ALLOW");
  });

  it("creator can create and update experiences in their tenant", () => {
    for (const action of ["create", "update", "read"] as AbacAction[]) {
      const result = engine.evaluate(
        makeActor({ roles: ["creator"] }),
        makeResource({ type: "experience" }),
        action,
      );
      expect(result.decision).toBe("ALLOW");
    }
  });

  it("creator cannot access tenant_config", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["creator"] }),
      makeResource({ type: "tenant_config" }),
      "read",
    );
    expect(result.decision).toBe("DENY");
  });
});

// ─── Teacher Role ─────────────────────────────────────────────────────────────

describe("ABAC - Teacher Access", () => {
  it("teacher can read experiences in their tenant", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["teacher"] }),
      makeResource({ type: "experience" }),
      "read",
    );
    expect(result.decision).toBe("ALLOW");
  });

  it("teacher can review items in the review_queue", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["teacher"] }),
      makeResource({ type: "review_queue" }),
      "review",
    );
    expect(result.decision).toBe("ALLOW");
  });

  it("teacher cannot create generation_requests", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["teacher"] }),
      makeResource({ type: "generation_request" }),
      "create",
    );
    expect(result.decision).toBe("DENY");
  });

  it("teacher cannot delete experiences", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["teacher"] }),
      makeResource({ type: "experience" }),
      "delete",
    );
    expect(result.decision).toBe("DENY");
  });
});

// ─── Student Role ─────────────────────────────────────────────────────────────

describe("ABAC - Student Access", () => {
  it("student can read experiences when consent granted", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["student"], consentFlags: ["ai_tutor_consent"] }),
      makeResource({ type: "experience" }),
      "read",
    );
    expect(result.decision).toBe("ALLOW");
  });

  it("student cannot read experiences without consent", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["student"], consentFlags: [] }),
      makeResource({ type: "experience" }),
      "read",
    );
    expect(result.decision).toBe("DENY");
  });

  it("student can execute simulations with consent", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["student"], consentFlags: ["ai_tutor_consent"] }),
      makeResource({ type: "simulation" }),
      "execute",
    );
    expect(result.decision).toBe("ALLOW");
  });

  it("student cannot execute simulations without consent", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["student"], consentFlags: [] }),
      makeResource({ type: "simulation" }),
      "execute",
    );
    expect(result.decision).toBe("DENY");
  });

  it("student cannot create generation_requests even with consent", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["student"], consentFlags: ["ai_tutor_consent"] }),
      makeResource({ type: "generation_request" }),
      "create",
    );
    expect(result.decision).toBe("DENY");
  });
});

// ─── Owner Policy ─────────────────────────────────────────────────────────────

describe("ABAC - Owner Read Own", () => {
  it("student can read their own user_profile without consent", () => {
    const result = engine.evaluate(
      makeActor({ userId: "user-42", roles: ["student"], consentFlags: [] }),
      makeResource({ type: "user_profile", id: "profile-42", ownerUserId: "user-42" }),
      "read",
    );
    expect(result.decision).toBe("ALLOW");
    expect(result.matchedPolicyId).toBe("builtin:owner-read-own");
  });

  it("student cannot read another student's user_profile", () => {
    const result = engine.evaluate(
      makeActor({ userId: "user-1", roles: ["student"], consentFlags: [] }),
      makeResource({ type: "user_profile", id: "profile-99", ownerUserId: "user-99" }),
      "read",
    );
    expect(result.decision).toBe("DENY");
  });
});

// ─── Custom Policies ──────────────────────────────────────────────────────────

describe("ABAC - Custom Policy Injection", () => {
  it("custom DENY policy blocks access even for admin when matched", () => {
    const restrictivePolicy: AbacPolicy = {
      id: "custom:deny-experience-export",
      description: "Deny all export of experiences",
      priority: 0, // higher priority than admin full-access (1)
      effect: "DENY",
      conditions: [
        { type: "resource_type", resourceType: "experience" },
        { type: "resource_action", action: "export" },
      ],
    };

    const customEngine = new AbacPolicyEngine([restrictivePolicy]);
    const result = customEngine.evaluate(
      makeActor({ roles: ["admin"] }),
      makeResource({ type: "experience" }),
      "export",
    );
    expect(result.decision).toBe("DENY");
    expect(result.matchedPolicyId).toBe("custom:deny-experience-export");
  });

  it("custom ALLOW policy grants access to a specific attribute", () => {
    const customPolicy: AbacPolicy = {
      id: "custom:beta-tester-read",
      description: "Beta testers can read assessment resources",
      priority: 3,
      effect: "ALLOW",
      conditions: [
        { type: "actor_attribute_equals", attribute: "beta_tester", value: true },
        { type: "resource_in_same_tenant" },
        { type: "resource_type", resourceType: "assessment" },
        { type: "resource_action", action: "read" },
      ],
    };

    const customEngine = new AbacPolicyEngine([customPolicy]);
    const result = customEngine.evaluate(
      makeActor({
        roles: ["student"],
        consentFlags: [],
        attributes: { beta_tester: true },
      }),
      makeResource({ type: "assessment" }),
      "read",
    );
    expect(result.decision).toBe("ALLOW");
    expect(result.matchedPolicyId).toBe("custom:beta-tester-read");
  });
});

// ─── Audit Entry ─────────────────────────────────────────────────────────────

describe("ABAC - Audit Trail", () => {
  it("every decision produces a complete audit entry", () => {
    const actor = makeActor({ userId: "audit-user", roles: ["teacher"] });
    const resource = makeResource({ type: "review_queue", id: "rq-1" });
    const result = engine.evaluate(actor, resource, "review");

    expect(result.auditEntry).toBeDefined();
    expect(result.auditEntry.actorId).toBe("audit-user");
    expect(result.auditEntry.tenantId).toBe(TENANT_A);
    expect(result.auditEntry.resourceType).toBe("review_queue");
    expect(result.auditEntry.resourceId).toBe("rq-1");
    expect(result.auditEntry.action).toBe("review");
    expect(result.auditEntry.decision).toBe("ALLOW");
    expect(result.auditEntry.timestamp).toBeTruthy();
    expect(() => new Date(result.auditEntry.timestamp)).not.toThrow();
  });

  it("denied access also produces audit entry with DENY decision", () => {
    const result = engine.evaluate(
      makeActor({ roles: ["student"], consentFlags: [] }),
      makeResource({ type: "experience" }),
      "delete",
    );
    expect(result.auditEntry.decision).toBe("DENY");
    expect(result.auditEntry.actorId).toBeTruthy();
  });
});

// ─── Batch Evaluation ─────────────────────────────────────────────────────────

describe("ABAC - Batch Evaluation", () => {
  it("evaluates multiple actions for the same actor/resource pair", () => {
    const actor = makeActor({ roles: ["teacher"] });
    const resource = makeResource({ type: "experience" });
    const results = engine.evaluateBatch(actor, resource, ["read", "create", "delete"]);

    expect(results.get("read")?.decision).toBe("ALLOW");
    expect(results.get("create")?.decision).toBe("DENY");
    expect(results.get("delete")?.decision).toBe("DENY");
  });

  it("returns a decision for every requested action", () => {
    const actions: AbacAction[] = ["read", "create", "update", "delete", "publish", "review", "execute", "export", "admin"];
    const results = engine.evaluateBatch(
      makeActor({ roles: ["admin"] }),
      makeResource(),
      actions,
    );
    expect(results.size).toBe(actions.length);
    for (const action of actions) {
      expect(results.has(action)).toBe(true);
    }
  });
});
