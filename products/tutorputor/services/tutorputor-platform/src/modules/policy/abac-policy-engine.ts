/**
 * ABAC Policy Engine
 *
 * Attribute-Based Access Control for TutorPutor.
 * Evaluates actor/resource/action/context tuples against declarative policies.
 * Supports tenant-scoped lookups, consent attributes, and produces audit log entries.
 *
 * Design: pure function evaluation — no side effects. Callers are responsible for
 * persisting audit log entries returned by `evaluate()`.
 *
 * @doc.type service
 * @doc.purpose ABAC policy evaluation engine with audit trail
 * @doc.layer product
 * @doc.pattern PolicyEngine
 */

// ─── Actor ────────────────────────────────────────────────────────────────────

export type TutorRole = "student" | "teacher" | "admin" | "superadmin" | "creator";

export interface AbacActor {
  userId: string;
  tenantId: string;
  roles: TutorRole[];
  /** Consent flags granted by this actor */
  consentFlags: string[];
  /** Arbitrary extra attributes for future extension */
  attributes: Record<string, string | number | boolean>;
}

// ─── Resource ─────────────────────────────────────────────────────────────────

export type ResourceType =
  | "experience"
  | "generation_request"
  | "simulation"
  | "assessment"
  | "learning_path"
  | "review_queue"
  | "evaluation_record"
  | "tenant_config"
  | "user_profile";

export interface AbacResource {
  type: ResourceType;
  id: string;
  /** Tenant that owns this resource */
  ownerTenantId: string;
  /** Optional owner user ID for personal resources */
  ownerUserId: string | undefined;
  attributes: Record<string, string | number | boolean>;
}

// ─── Action ───────────────────────────────────────────────────────────────────

export type AbacAction =
  | "read"
  | "create"
  | "update"
  | "delete"
  | "publish"
  | "review"
  | "execute"
  | "export"
  | "admin";

// ─── Decision ─────────────────────────────────────────────────────────────────

export type PolicyDecision = "ALLOW" | "DENY";

export interface AbacDecision {
  decision: PolicyDecision;
  reason: string;
  matchedPolicyId: string | undefined;
  auditEntry: AbacAuditEntry;
}

export interface AbacAuditEntry {
  actorId: string;
  tenantId: string;
  resourceType: ResourceType;
  resourceId: string;
  action: AbacAction;
  decision: PolicyDecision;
  reason: string;
  matchedPolicyId: string | undefined;
  timestamp: string;
}

// ─── Policy definition ────────────────────────────────────────────────────────

export interface AbacPolicy {
  id: string;
  description: string;
  /**
   * Evaluation priority (lower = evaluated first).
   * Policies with higher priority (lower number) win.
   */
  priority: number;
  effect: PolicyDecision;
  /**
   * Conditions — all must match for the policy to apply.
   */
  conditions: AbacCondition[];
}

export type AbacCondition =
  | { type: "actor_has_role"; role: TutorRole }
  | { type: "actor_in_tenant"; tenantId: string }
  | { type: "resource_type"; resourceType: ResourceType }
  | { type: "resource_action"; action: AbacAction }
  | { type: "resource_owned_by_actor" }
  | { type: "resource_in_same_tenant" }
  | { type: "actor_has_consent"; consentFlag: string }
  | { type: "actor_attribute_equals"; attribute: string; value: string | number | boolean }
  | { type: "resource_attribute_equals"; attribute: string; value: string | number | boolean };

// ─── Built-in policies ────────────────────────────────────────────────────────

export const BUILTIN_POLICIES: AbacPolicy[] = [
  {
    id: "builtin:superadmin-full-access",
    description: "Superadmins have full access to all resources within their tenant",
    priority: 0,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "superadmin" },
      { type: "resource_in_same_tenant" },
    ],
  },

  // P1: Admin can do anything within their tenant
  {
    id: "builtin:admin-full-access",
    description: "Admins have full access to all resources within their tenant",
    priority: 1,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "admin" },
      { type: "resource_in_same_tenant" },
    ],
  },

  // P2: Creator can create and manage generation requests + experiences
  {
    id: "builtin:creator-generation",
    description: "Creators can create, update, and publish experiences and generation requests",
    priority: 2,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "creator" },
      { type: "resource_in_same_tenant" },
      { type: "resource_type", resourceType: "generation_request" },
    ],
  },
  {
    id: "builtin:creator-experience-write",
    description: "Creators can create, update, and publish experiences",
    priority: 2,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "creator" },
      { type: "resource_in_same_tenant" },
      { type: "resource_type", resourceType: "experience" },
    ],
  },

  // P3: Teacher can read and review
  {
    id: "builtin:teacher-read-review",
    description: "Teachers can read and review resources within their tenant",
    priority: 3,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "teacher" },
      { type: "resource_in_same_tenant" },
      { type: "resource_action", action: "read" },
    ],
  },
  {
    id: "builtin:teacher-review-queue",
    description: "Teachers can review items in the review queue",
    priority: 3,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "teacher" },
      { type: "resource_in_same_tenant" },
      { type: "resource_type", resourceType: "review_queue" },
      { type: "resource_action", action: "review" },
    ],
  },

  // P4: Student can read experiences and simulations they have consent for
  {
    id: "builtin:student-read-with-consent",
    description: "Students can read experiences and simulations when AI consent granted",
    priority: 4,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "student" },
      { type: "resource_in_same_tenant" },
      { type: "resource_action", action: "read" },
      { type: "actor_has_consent", consentFlag: "ai_tutor_consent" },
    ],
  },
  {
    id: "builtin:student-execute-simulation",
    description: "Students can execute simulations within their tenant with consent",
    priority: 4,
    effect: "ALLOW",
    conditions: [
      { type: "actor_has_role", role: "student" },
      { type: "resource_in_same_tenant" },
      { type: "resource_type", resourceType: "simulation" },
      { type: "resource_action", action: "execute" },
      { type: "actor_has_consent", consentFlag: "ai_tutor_consent" },
    ],
  },

  // P5: Owner can always read their own resources
  {
    id: "builtin:owner-read-own",
    description: "Any actor can read resources they own",
    priority: 5,
    effect: "ALLOW",
    conditions: [
      { type: "resource_in_same_tenant" },
      { type: "resource_owned_by_actor" },
      { type: "resource_action", action: "read" },
    ],
  },

  // P6: Cross-tenant access is always denied (catch-all deny)
  {
    id: "builtin:deny-cross-tenant",
    description: "Cross-tenant access is always denied",
    priority: 100,
    effect: "DENY",
    conditions: [],
  },
];

// ─── Policy Engine ────────────────────────────────────────────────────────────

export class AbacPolicyEngine {
  private readonly policies: AbacPolicy[];

  constructor(additionalPolicies: AbacPolicy[] = []) {
    // Merge built-in + additional, sorted by priority ascending
    this.policies = [...BUILTIN_POLICIES, ...additionalPolicies].sort(
      (a, b) => a.priority - b.priority,
    );
  }

  /**
   * Evaluate a single access request.
   * Returns an AbacDecision with the verdict, reason, and audit entry.
   */
  evaluate(
    actor: AbacActor,
    resource: AbacResource,
    action: AbacAction,
  ): AbacDecision {
    // Tenant isolation check — always applied before policies
    if (actor.tenantId !== resource.ownerTenantId) {
      return this.buildDecision("DENY", actor, resource, action, "Cross-tenant access denied", "builtin:deny-cross-tenant");
    }

    // Evaluate policies in priority order
    for (const policy of this.policies) {
      if (this.matchesAllConditions(policy.conditions, actor, resource, action)) {
        return this.buildDecision(
          policy.effect,
          actor,
          resource,
          action,
          policy.description,
          policy.id,
        );
      }
    }

    // Default deny
    return this.buildDecision(
      "DENY",
      actor,
      resource,
      action,
      "No policy granted access",
      undefined,
    );
  }

  /**
   * Evaluate multiple actions at once (convenience method).
   */
  evaluateBatch(
    actor: AbacActor,
    resource: AbacResource,
    actions: AbacAction[],
  ): Map<AbacAction, AbacDecision> {
    const results = new Map<AbacAction, AbacDecision>();
    for (const action of actions) {
      results.set(action, this.evaluate(actor, resource, action));
    }
    return results;
  }

  // ─── Private helpers ────────────────────────────────────────────────────────

  private matchesAllConditions(
    conditions: AbacCondition[],
    actor: AbacActor,
    resource: AbacResource,
    action: AbacAction,
  ): boolean {
    return conditions.every((cond) => this.matchCondition(cond, actor, resource, action));
  }

  private matchCondition(
    cond: AbacCondition,
    actor: AbacActor,
    resource: AbacResource,
    action: AbacAction,
  ): boolean {
    switch (cond.type) {
      case "actor_has_role":
        return actor.roles.includes(cond.role);

      case "actor_in_tenant":
        return actor.tenantId === cond.tenantId;

      case "resource_type":
        return resource.type === cond.resourceType;

      case "resource_action":
        return action === cond.action;

      case "resource_owned_by_actor":
        return resource.ownerUserId !== undefined && resource.ownerUserId === actor.userId;

      case "resource_in_same_tenant":
        return actor.tenantId === resource.ownerTenantId;

      case "actor_has_consent":
        return actor.consentFlags.includes(cond.consentFlag);

      case "actor_attribute_equals":
        return actor.attributes[cond.attribute] === cond.value;

      case "resource_attribute_equals":
        return resource.attributes[cond.attribute] === cond.value;

      default:
        // Exhaustiveness check
        return false;
    }
  }

  private buildDecision(
    decision: PolicyDecision,
    actor: AbacActor,
    resource: AbacResource,
    action: AbacAction,
    reason: string,
    matchedPolicyId: string | undefined,
  ): AbacDecision {
    const auditEntry: AbacAuditEntry = {
      actorId: actor.userId,
      tenantId: actor.tenantId,
      resourceType: resource.type,
      resourceId: resource.id,
      action,
      decision,
      reason,
      matchedPolicyId,
      timestamp: new Date().toISOString(),
    };

    return { decision, reason, matchedPolicyId, auditEntry };
  }
}
