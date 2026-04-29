/**
 * ABAC Module - Attribute-Based Access Control
 *
 * Implements attribute-based access control beyond role-based access control.
 * Evaluates policies based on user, resource, and environmental attributes.
 *
 * @doc.type module
 * @doc.purpose Attribute-based access control module
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { ABACService } from "./ABACService.js";
import { z } from "zod";

const evaluateRequestSchema = z.object({
  action: z.string(),
  resourceType: z.string(),
  resourceId: z.string(),
  attributes: z.record(z.string(), z.unknown()).optional(),
});

const createPolicySchema = z.object({
  name: z.string(),
  description: z.string().optional(),
  enabled: z.boolean().default(true),
  effect: z.enum(["allow", "deny"]),
  priority: z.number().default(0),
  conditions: z.array(z.object({
    operator: z.enum(["eq", "ne", "gt", "lt", "gte", "lte", "in", "contains"]),
    attribute: z.string(),
    value: z.unknown(),
  })),
  actions: z.array(z.string()),
  resourceTypes: z.array(z.string()),
});

const setAttributeSchema = z.object({
  entityType: z.enum(["user", "resource", "environment"]),
  entityId: z.string(),
  attributeName: z.string(),
  attributeValue: z.string(),
});

export const abacModule: FastifyPluginAsync = async (app) => {
  const abacService = new ABACService(app.prisma as any);
  app.decorate("abacService", abacService);

  // POST /api/v1/abac/evaluate - Evaluate access decision
  app.post("/api/v1/abac/evaluate", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const userId = (request as any).userId;

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const body = evaluateRequestSchema.parse(request.body);
    const allowed = await abacService.evaluate({
      tenantId,
      userId,
      action: body.action,
      resourceType: body.resourceType,
      resourceId: body.resourceId,
      attributes: body.attributes || {},
    });

    return reply.send({ allowed });
  });

  // POST /api/v1/abac/policies - Create a new policy (admin only)
  app.post("/api/v1/abac/policies", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = createPolicySchema.parse(request.body);
    const { description, ...policyWithoutDescription } = body;
    const policy = await abacService.createPolicy(tenantId, {
      ...policyWithoutDescription,
      ...(description !== undefined ? { description } : {}),
    });

    return reply.send({ success: true, policy });
  });

  // POST /api/v1/abac/attributes - Set an attribute on an entity (admin only)
  app.post("/api/v1/abac/attributes", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = setAttributeSchema.parse(request.body);
    await abacService.setAttribute(
      tenantId,
      body.entityType,
      body.entityId,
      body.attributeName,
      body.attributeValue,
    );

    return reply.send({ success: true });
  });

  // GET /api/v1/abac/attributes/:entityType/:entityId - Get attributes for an entity
  app.get("/api/v1/abac/attributes/:entityType/:entityId", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const userId = (request as any).userId;

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const { entityType, entityId } = request.params as {
      entityType: "user" | "resource" | "environment";
      entityId: string;
    };

    const attributes = await abacService.getEntityAttributes(
      tenantId,
      entityType,
      entityId,
    );

    return reply.send({ attributes });
  });

  app.log.info("✅ ABAC module registered");
};
