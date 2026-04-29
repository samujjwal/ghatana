/**
 * ABAC (Attribute-Based Access Control) Service
 *
 * Implements attribute-based access control beyond role-based access control.
 * Evaluates policies based on user, resource, and environmental attributes.
 *
 * @doc.type service
 * @doc.purpose Attribute-based access control evaluation
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export interface ABACPolicyCondition {
  operator: "eq" | "ne" | "gt" | "lt" | "gte" | "lte" | "in" | "contains";
  attribute: string;
  value: unknown;
}

export interface ABACPolicy {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  enabled: boolean;
  effect: "allow" | "deny";
  priority: number;
  conditions: ABACPolicyCondition[];
  actions: string[];
  resourceTypes: string[];
}

export interface ABACContext {
  tenantId: string;
  userId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  attributes?: Record<string, unknown>;
}

export class ABACService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Evaluate if an action is allowed based on ABAC policies
   */
  async evaluate(context: ABACContext): Promise<boolean> {
    const { tenantId, action, resourceType } = context;

    // Fetch applicable policies for tenant
    const policies = await this.prisma.aBACPolicy.findMany({
      where: {
        tenantId,
        enabled: true,
        actions: { has: action },
        resourceTypes: { has: resourceType },
      },
      orderBy: { priority: "desc" },
    });

    if (policies.length === 0) {
      // No applicable policies - default to deny
      return false;
    }

    // Evaluate policies in priority order
    for (const policy of policies) {
      const conditions = JSON.parse(policy.conditions) as ABACPolicyCondition[];
      
      if (await this.evaluateConditions(conditions, context)) {
        // Policy conditions matched - return effect
        return policy.effect === "allow";
      }
    }

    // No policy conditions matched - default to deny
    return false;
  }

  /**
   * Evaluate policy conditions against context
   */
  private async evaluateConditions(
    conditions: ABACPolicyCondition[],
    context: ABACContext,
  ): Promise<boolean> {
    for (const condition of conditions) {
      const actualValue = await this.getAttributeValue(
        condition.attribute,
        context,
      );
      
      const result = this.evaluateCondition(
        condition.operator,
        actualValue,
        condition.value,
      );
      
      if (!result) {
        return false;
      }
    }
    
    return true;
  }

  /**
   * Get attribute value from context or database
   */
  private async getAttributeValue(
    attribute: string,
    context: ABACContext,
  ): Promise<unknown> {
    // Check context attributes first
    if (context.attributes && attribute in context.attributes) {
      return context.attributes[attribute];
    }

    // Parse attribute path (e.g., "user.department", "resource.classification")
    const parts = attribute.split(".");
    const entityType = parts[0];
    const attributeName = parts.slice(1).join(".");

    // Fetch from database if needed
    if (entityType === "user") {
      const attribute = await this.prisma.aBACAttribute.findUnique({
        where: {
          tenantId_entityType_entityId_attributeName: {
            tenantId: context.tenantId,
            entityType: "user",
            entityId: context.userId,
            attributeName,
          },
        },
      });
      return attribute?.attributeValue;
    }

    if (entityType === "resource") {
      const attribute = await this.prisma.aBACAttribute.findUnique({
        where: {
          tenantId_entityType_entityId_attributeName: {
            tenantId: context.tenantId,
            entityType: "resource",
            entityId: context.resourceId,
            attributeName,
          },
        },
      });
      return attribute?.attributeValue;
    }

    return undefined;
  }

  /**
   * Evaluate a single condition
   */
  private evaluateCondition(
    operator: string,
    actual: unknown,
    expected: unknown,
  ): boolean {
    switch (operator) {
      case "eq":
        return actual === expected;
      case "ne":
        return actual !== expected;
      case "gt":
        return typeof actual === "number" && typeof expected === "number" && actual > expected;
      case "lt":
        return typeof actual === "number" && typeof expected === "number" && actual < expected;
      case "gte":
        return typeof actual === "number" && typeof expected === "number" && actual >= expected;
      case "lte":
        return typeof actual === "number" && typeof expected === "number" && actual <= expected;
      case "in":
        return Array.isArray(expected) && expected.includes(actual);
      case "contains":
        return typeof actual === "string" && typeof expected === "string" && actual.includes(expected);
      default:
        return false;
    }
  }

  /**
   * Create a new ABAC policy
   */
  async createPolicy(
    tenantId: string,
    policy: Omit<ABACPolicy, "id" | "tenantId" | "conditions"> & {
      conditions: ABACPolicyCondition[];
    },
  ): Promise<ABACPolicy> {
    const created = await this.prisma.aBACPolicy.create({
      data: {
        tenantId,
        name: policy.name,
        description: policy.description ?? null,
        enabled: policy.enabled,
        effect: policy.effect,
        priority: policy.priority,
        conditions: JSON.stringify(policy.conditions),
        actions: policy.actions,
        resourceTypes: policy.resourceTypes,
      },
    });

    const { description: _description, ...createdWithoutDescription } = created;
    return {
      ...createdWithoutDescription,
      effect: created.effect as "allow" | "deny",
      ...(created.description !== null ? { description: created.description } : {}),
      conditions: policy.conditions,
    };
  }

  /**
   * Set an attribute on an entity
   */
  async setAttribute(
    tenantId: string,
    entityType: "user" | "resource" | "environment",
    entityId: string,
    attributeName: string,
    attributeValue: string,
  ): Promise<void> {
    await this.prisma.aBACAttribute.upsert({
      where: {
        tenantId_entityType_entityId_attributeName: {
          tenantId,
          entityType,
          entityId,
          attributeName,
        },
      },
      create: {
        tenantId,
        entityType,
        entityId,
        attributeName,
        attributeValue,
      },
      update: {
        attributeValue,
        updatedAt: new Date(),
      },
    });
  }

  /**
   * Get all attributes for an entity
   */
  async getEntityAttributes(
    tenantId: string,
    entityType: "user" | "resource" | "environment",
    entityId: string,
  ): Promise<Record<string, string>> {
    const attributes = await this.prisma.aBACAttribute.findMany({
      where: {
        tenantId,
        entityType,
        entityId,
      },
    });

    const result: Record<string, string> = {};
    for (const attr of attributes) {
      result[attr.attributeName] = attr.attributeValue;
    }

    return result;
  }
}
