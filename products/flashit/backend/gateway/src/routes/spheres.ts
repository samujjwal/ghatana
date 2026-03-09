/**
 * Spheres API routes for managing privacy boundaries
 */

import { FastifyInstance } from "fastify";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { getUserIdFromRequest, JwtPayload } from "../lib/auth";
import { Logger } from "../lib/logger";

const createSphereSchema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().optional(),
  type: z.enum(["PERSONAL", "SHARED", "WORK", "FAMILY", "PROJECT", "CUSTOM"]),
  visibility: z.enum(["PRIVATE", "INVITE_ONLY", "LINK_SHARED", "PUBLIC"]),
});

export const registerSphereRoutes = async (app: FastifyInstance) => {
  /**
   * POST /api/spheres
   * Create a new Sphere
   */
  app.post("/api/spheres", {
    onRequest: [app.authenticate],
  }, async (request, reply) => {
    const logger = Logger.fromRequest(request);
    const userId = getUserIdFromRequest(request);
    const body = createSphereSchema.parse(request.body);

    // Create Sphere
    const sphere = await prisma.sphere.create({
      data: {
        userId,
        name: body.name,
        description: body.description,
        type: body.type,
        visibility: body.visibility,
        sphereAccess: {
          create: {
            userId,
            role: "OWNER",
            grantedBy: userId,
          },
        },
      },
    });

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: "SPHERE_CREATED",
        userId,
        sphereId: sphere.id,
        actor: (request.user as JwtPayload).email,
        action: "CREATE",
        resourceType: "SPHERE",
        resourceId: sphere.id,
      },
    });

    logger.logBusinessEvent('SPHERE_CREATED', {
      sphereId: sphere.id,
      sphereName: sphere.name,
      sphereType: sphere.type,
      visibility: sphere.visibility,
      userId
    });

    return reply.code(201).send({ sphere });
  });

  /**
   * GET /api/spheres
   * List Spheres accessible to the user
   */
  app.get("/api/spheres", {
    onRequest: [app.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { owned_only } = request.query as { owned_only?: string };

    const where: any = {
      sphereAccess: {
        some: {
          userId,
          revokedAt: null,
        },
      },
      deletedAt: null,
    };

    if (owned_only === "true") {
      where.userId = userId;
    }

    const spheres = await prisma.sphere.findMany({
      where,
      include: {
        sphereAccess: {
          where: {
            userId,
            revokedAt: null,
          },
          select: {
            role: true,
          },
        },
        _count: {
          select: {
            moments: {
              where: {
                deletedAt: null,
              },
            },
          },
        },
      },
      orderBy: { createdAt: "desc" },
    });

    const spheresWithRole = spheres.map(sphere => ({
      ...sphere,
      userRole: sphere.sphereAccess[0]?.role,
      momentCount: sphere._count.moments,
      sphereAccess: undefined,
      _count: undefined,
    }));

    return reply.send({ spheres: spheresWithRole });
  });

  /**
   * GET /api/spheres/:id
   * Get a specific Sphere by ID
   */
  app.get<{ Params: { id: string } }>("/api/spheres/:id", {
    onRequest: [app.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id } = request.params;

    const sphere = await prisma.sphere.findUnique({
      where: { id },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            displayName: true,
          },
        },
        sphereAccess: {
          where: {
            revokedAt: null,
          },
          include: {
            user: {
              select: {
                id: true,
                email: true,
                displayName: true,
              },
            },
          },
        },
        _count: {
          select: {
            moments: {
              where: {
                deletedAt: null,
              },
            },
          },
        },
      },
    });

    if (!sphere || sphere.deletedAt) {
      return reply.code(404).send({
        error: "Not found",
        message: "Sphere not found",
      });
    }

    // Check user has access
    const userAccess = sphere.sphereAccess.find(a => a.userId === userId);
    if (!userAccess) {
      return reply.code(403).send({
        error: "Access denied",
        message: "You do not have access to this Sphere",
      });
    }

    return reply.send({
      sphere: {
        ...sphere,
        userRole: userAccess.role,
        momentCount: sphere._count.moments,
        _count: undefined,
      },
    });
  });

  /**
   * PATCH /api/spheres/:id
   * Update a Sphere (name, description, visibility)
   */
  app.patch<{ Params: { id: string } }>("/api/spheres/:id", {
    onRequest: [app.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id } = request.params;

    const updateSchema = z.object({
      name: z.string().min(1).max(255).optional(),
      description: z.string().optional(),
      visibility: z.enum(["PRIVATE", "INVITE_ONLY", "LINK_SHARED", "PUBLIC"]).optional(),
    });

    const body = updateSchema.parse(request.body);

    // Check OWNER access
    const access = await prisma.sphereAccess.findFirst({
      where: {
        sphereId: id,
        userId,
        role: "OWNER",
        revokedAt: null,
      },
    });

    if (!access) {
      return reply.code(403).send({
        error: "Access denied",
        message: "Only Sphere owners can update Sphere settings",
      });
    }

    // Update Sphere
    const sphere = await prisma.sphere.update({
      where: { id },
      data: body,
    });

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: "SPHERE_UPDATED",
        userId,
        sphereId: id,
        actor: (request.user as JwtPayload).email,
        action: "UPDATE",
        resourceType: "SPHERE",
        resourceId: id,
        details: body,
      },
    });

    return reply.send({ sphere });
  });

  /**
   * DELETE /api/spheres/:id
   * Soft delete a Sphere (and all its Moments)
   */
  app.delete<{ Params: { id: string } }>("/api/spheres/:id", {
    onRequest: [app.authenticate],
  }, async (request, reply) => {
    const userId = getUserIdFromRequest(request);
    const { id } = request.params;

    // Check OWNER access
    const sphere = await prisma.sphere.findUnique({
      where: { id },
      select: {
        id: true,
        userId: true,
        deletedAt: true,
      },
    });

    if (!sphere || sphere.deletedAt) {
      return reply.code(404).send({
        error: "Not found",
        message: "Sphere not found",
      });
    }

    if (sphere.userId !== userId) {
      return reply.code(403).send({
        error: "Access denied",
        message: "Only the Sphere creator can delete it",
      });
    }

    // Soft delete Sphere and all its Moments
    await prisma.$transaction([
      prisma.moment.updateMany({
        where: { sphereId: id },
        data: { deletedAt: new Date() },
      }),
      prisma.sphere.update({
        where: { id },
        data: { deletedAt: new Date() },
      }),
    ]);

    // Audit log
    await prisma.auditEvent.create({
      data: {
        eventType: "SPHERE_DELETED",
        userId,
        sphereId: id,
        actor: (request.user as JwtPayload).email,
        action: "DELETE",
        resourceType: "SPHERE",
        resourceId: id,
      },
    });

    return reply.code(204).send();
  });
};

