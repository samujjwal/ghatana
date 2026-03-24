/**
 * Simulation Authoring Routes
 *
 * HTTP endpoints for AI-powered simulation manifest generation and refinement.
 * Delegates to the sim-author library — no business logic here.
 *
 * @doc.type routes
 * @doc.purpose Simulation authoring HTTP endpoint handlers
 * @doc.layer platform
 * @doc.pattern Routes
 */
import type { FastifyInstance } from "fastify";
import {
  createSimulationAuthorService,
  type SimAuthorConfig,
} from "@tutorputor/simulation/engine";
import type {
  GenerateManifestRequest,
  RefineManifestRequest,
  SuggestParametersRequest,
} from "@tutorputor/contracts/v1/simulation/types";
import { getTenantId, getUserId } from "../../utils/request-helpers.js";

export async function simulationAuthoringRoutes(app: FastifyInstance) {
  const config: SimAuthorConfig = {
    providers: [],
  };

  if (process.env.OPENAI_API_KEY) {
    config.providers.push({
      name: "openai",
      config: {
        provider: "openai",
        apiKey: process.env.OPENAI_API_KEY,
        model: process.env.OPENAI_MODEL || "gpt-4o",
      },
      isDefault: true,
    });
  }

  if (process.env.ANTHROPIC_API_KEY) {
    config.providers.push({
      name: "anthropic",
      config: {
        provider: "anthropic",
        apiKey: process.env.ANTHROPIC_API_KEY,
        model: process.env.ANTHROPIC_MODEL || "claude-3-5-sonnet-20240620",
      },
    });
  }

  const service = createSimulationAuthorService(app.prisma, config);

  // Generate Manifest
  app.post<{ Body: Omit<GenerateManifestRequest, "tenantId" | "userId"> }>(
    "/api/sim-author/generate",
    async (request) => {
      return service.generateManifest({
        ...request.body,
        tenantId: getTenantId(request),
        userId: getUserId(request),
      });
    },
  );

  // Refine Manifest
  app.post<{ Body: Omit<RefineManifestRequest, "tenantId" | "userId"> }>(
    "/api/sim-author/refine",
    async (request) => {
      return service.refineManifest({
        ...request.body,
        tenantId: getTenantId(request),
        userId: getUserId(request),
      });
    },
  );

  // Suggest Parameters
  app.post<{ Body: Omit<SuggestParametersRequest, "tenantId"> }>(
    "/api/sim-author/suggest",
    async (request) => {
      return service.suggestParameters({
        ...request.body,
        tenantId: getTenantId(request),
      });
    },
  );

  // Get saved manifest by ID
  app.get<{ Params: { id: string } }>(
    "/api/sim-author/manifests/:id",
    async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);
      const record = await app.prisma.simulationManifest.findFirst({
        where: { id, tenantId: tenantId as string },
      });
      if (!record) {
        return reply.code(404).send({ error: "Manifest not found" });
      }
      return reply.send({
        id: record.id,
        title: record.title,
        manifest: record.manifest,
      });
    },
  );

  // Save / update manifest
  app.put<{ Params: { id: string }; Body: Record<string, unknown> }>(
    "/api/sim-author/manifests/:id",
    async (request, reply) => {
      const { id } = request.params;
      const tenantId = getTenantId(request);
      const existing = await app.prisma.simulationManifest.findFirst({
        where: { id, tenantId: tenantId as string },
        select: { id: true },
      });
      if (!existing) {
        return reply.code(404).send({ error: "Manifest not found" });
      }
      await app.prisma.simulationManifest.update({
        where: { id },
        data: { manifest: request.body },
      });
      return reply.send({ success: true });
    },
  );

  // Link a saved manifest to a claim within a learning experience
  // Body: { experienceId, claimRef, interactionType?, goal?, successCriteria?, estimatedMinutes? }
  app.post<{
    Params: { id: string };
    Body: {
      experienceId: string;
      claimRef: string;
      interactionType?: string;
      goal?: string;
      successCriteria?: Record<string, unknown>;
      estimatedMinutes?: number;
    };
  }>("/api/sim-author/manifests/:id/link-claim", async (request, reply) => {
    const { id } = request.params;
    const tenantId = getTenantId(request);
    const {
      experienceId,
      claimRef,
      interactionType,
      goal,
      successCriteria,
      estimatedMinutes,
    } = request.body;

    if (!experienceId || !claimRef) {
      return reply
        .code(400)
        .send({ error: "experienceId and claimRef are required" });
    }

    // Verify manifest exists for this tenant
    const manifest = await app.prisma.simulationManifest.findFirst({
      where: { id, tenantId: tenantId as string },
      select: { id: true },
    });
    if (!manifest) {
      return reply.code(404).send({ error: "Manifest not found" });
    }

    // Verify claim exists for the experience
    const claim = await app.prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimRef }, { claimRef }],
      },
      select: { claimRef: true },
    });
    if (!claim) {
      return reply
        .code(404)
        .send({ error: "Claim not found for this experience" });
    }

    // Upsert the ClaimSimulation link (unique on experienceId + claimRef)
    await app.prisma.claimSimulation.upsert({
      where: {
        experienceId_claimRef: { experienceId, claimRef: claim.claimRef },
      },
      create: {
        experienceId,
        claimRef: claim.claimRef,
        simulationManifestId: id,
        interactionType: interactionType ?? "parameter_exploration",
        goal: goal ?? "",
        successCriteria: successCriteria ?? {},
        estimatedMinutes: estimatedMinutes ?? 10,
      },
      update: {
        simulationManifestId: id,
        interactionType: interactionType ?? "parameter_exploration",
        goal: goal ?? "",
        successCriteria: successCriteria ?? {},
        estimatedMinutes: estimatedMinutes ?? 10,
      },
    });

    return reply
      .code(201)
      .send({
        success: true,
        simulationManifestId: id,
        claimRef: claim.claimRef,
      });
  });
}
