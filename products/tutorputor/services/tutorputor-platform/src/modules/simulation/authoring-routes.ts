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
import { Prisma } from "@tutorputor/core/db";
import {
  VRSimulationExporter,
  bootstrapCompatibleAutoPreset,
  createSimulationStarterManifest,
  exportCompatibleAutoPreset,
  exportSimulationStarterPackage,
  getAutoPresetNormalizationSummary,
  getLegacyAutoRuntimeSummary,
  getSimulationStarterCatalogSummary,
  getSimulationStarterById,
  getSimulationStarterByLegacyPresetId,
  listSimulationStarters,
  listCompatibleAutoPresets,
  listLegacyRuntimePresetSummaries,
  resolveSimulationStarterReference,
  resolveCompatibleAutoPreset,
} from "@tutorputor/simulation/engine";
import type {
  GenerateManifestRequest,
  GenerateManifestResult,
  RefineManifestRequest,
  SimulationManifest,
  SimulationDomain,
  SuggestParametersRequest,
  SuggestParametersResult,
} from "@tutorputor/contracts/v1/simulation/types";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import { getTenantId, getUserId } from "../../core/http/requestContext.js";
import { SimulationTemplateLibraryService } from "./template-library-service.js";
import { applyManifestGuardrails } from "./manifest-guardrails.js";

type SimAuthorConfig = {
  providers: Array<{
    name: string;
    config: {
      provider: string;
      apiKey: string;
      model: string;
    };
    isDefault?: boolean;
  }>;
};

type SimulationAuthorService = {
  generateManifest(
    request: GenerateManifestRequest,
  ): Promise<GenerateManifestResult>;
  refineManifest(
    request: RefineManifestRequest,
  ): Promise<GenerateManifestResult>;
  suggestParameters(
    request: SuggestParametersRequest,
  ): Promise<SuggestParametersResult>;
};

async function createAuthorService(
  app: FastifyInstance,
  config: SimAuthorConfig,
): Promise<SimulationAuthorService> {
  const simulationModule = await import("@tutorputor/simulation/engine");
  return (
    simulationModule as unknown as {
      createSimulationAuthorService: (
        prisma: FastifyInstance["prisma"],
        serviceConfig: SimAuthorConfig,
      ) => SimulationAuthorService;
    }
  ).createSimulationAuthorService(app.prisma, config);
}

function toInputJsonValue(
  value: Record<string, unknown>,
): Prisma.InputJsonValue {
  return value as Prisma.InputJsonValue;
}

export async function simulationAuthoringRoutes(app: FastifyInstance) {
  const vrExporter = new VRSimulationExporter();
  const templateLibrary = new SimulationTemplateLibraryService(app.prisma);
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

  if (config.providers.length === 0) {
    app.log.warn(
      "No AI providers configured (OPENAI_API_KEY / ANTHROPIC_API_KEY) — simulation authoring routes will not be registered.",
    );
  }

  const service =
    config.providers.length > 0 ? await createAuthorService(app, config) : null;

  const getAuthorService = (reply: Parameters<FastifyInstance["get"]>[1] extends never ? never : any) => {
    if (service) {
      return service;
    }

    reply.code(503).send({
      error: "Simulation authoring unavailable",
      message:
        "Simulation AI endpoints require OPENAI_API_KEY or ANTHROPIC_API_KEY, but manifest CRUD remains available.",
    });
    return null;
  };

  app.get<{
    Querystring: {
      domain?: SimulationDomain;
      difficulty?: "beginner" | "intermediate" | "advanced";
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      q?: string;
      tag?: string;
    };
  }>("/api/sim-author/starters", async (request, reply) => {
    const starters = listSimulationStarters({
      ...(request.query.domain ? { domain: request.query.domain } : {}),
      ...(request.query.difficulty
        ? { difficulty: request.query.difficulty }
        : {}),
      ...(request.query.audience ? { audience: request.query.audience } : {}),
      ...(request.query.q ? { query: request.query.q } : {}),
      ...(request.query.tag ? { tag: request.query.tag } : {}),
    }).map((starter) => ({
      id: starter.id,
      name: starter.name,
      summary: starter.summary,
      domain: starter.domain,
      difficulty: starter.difficulty,
      tags: starter.tags,
      estimatedMinutes: starter.estimatedMinutes,
      audience: starter.audience,
      legacyPresetIds: starter.legacyPresetIds,
    }));

    return reply.send({
      items: starters,
      total: starters.length,
    });
  });

  app.get("/api/sim-author/starters/summary", async (_request, reply) => {
    return reply.send(getSimulationStarterCatalogSummary());
  });

  app.get<{
    Querystring: {
      domain?: "physics" | "chemistry" | "biology" | "medicine" | "cs" | "math";
      q?: string;
      source?: "legacy_auto" | "curated_starter";
      bootstrapOnly?: "true" | "false";
    };
  }>("/api/sim-author/auto-presets", async (request, reply) => {
    const items = listCompatibleAutoPresets({
      ...(request.query.domain ? { domain: request.query.domain } : {}),
      ...(request.query.q ? { query: request.query.q } : {}),
      ...(request.query.source ? { source: request.query.source } : {}),
      ...(request.query.bootstrapOnly === "true"
        ? { bootstrapOnly: true }
        : {}),
    });

    return reply.send({
      items,
      total: items.length,
    });
  });

  app.get("/api/sim-author/auto-presets/summary", async (_request, reply) => {
    return reply.send(getAutoPresetNormalizationSummary());
  });

  app.get<{
    Querystring: {
      domain?: "physics" | "chemistry" | "biology" | "medicine" | "cs" | "math";
      status?: "governed_starter_available" | "legacy_compatibility_only";
      q?: string;
    };
  }>("/api/sim-author/legacy-runtime/presets", async (request, reply) => {
    const items = listLegacyRuntimePresetSummaries({
      ...(request.query.domain ? { domain: request.query.domain } : {}),
      ...(request.query.status ? { status: request.query.status } : {}),
      ...(request.query.q ? { query: request.query.q } : {}),
    });

    return reply.send({
      items,
      total: items.length,
    });
  });

  app.get("/api/sim-author/legacy-runtime/summary", async (_request, reply) => {
    return reply.send(getLegacyAutoRuntimeSummary());
  });

  app.get<{ Params: { id: string } }>(
    "/api/sim-author/auto-presets/:id",
    async (request, reply) => {
      const preset = resolveCompatibleAutoPreset(request.params.id);
      if (!preset) {
        return reply.code(404).send({ error: "Auto preset not found" });
      }

      return reply.send(preset);
    },
  );

  app.get<{
    Params: { id: string };
    Querystring: {
      manifestId?: string;
      title?: string;
      description?: string;
    };
  }>("/api/sim-author/auto-presets/:id/bootstrap", async (request, reply) => {
    const manifest = bootstrapCompatibleAutoPreset({
      presetRef: request.params.id,
      ...(request.query.manifestId
        ? { manifestId: request.query.manifestId }
        : {}),
      tenantId: getTenantId(request) as TenantId,
      authorId: getUserId(request) as UserId,
      ...(request.query.title ? { title: request.query.title } : {}),
      ...(request.query.description
        ? { description: request.query.description }
        : {}),
    });

    if (!manifest) {
      return reply.code(404).send({
        error: "Auto preset is not yet normalized onto a curated starter",
      });
    }

    return reply.send({
      presetId: request.params.id,
      manifest,
    });
  });

  app.get<{
    Params: { id: string };
    Querystring: {
      format?: "manifest" | "webxr" | "unity";
      manifestId?: string;
      title?: string;
      description?: string;
    };
  }>("/api/sim-author/auto-presets/:id/export", async (request, reply) => {
    const exported = exportCompatibleAutoPreset({
      presetRef: request.params.id,
      format: request.query.format ?? "manifest",
      ...(request.query.manifestId
        ? { manifestId: request.query.manifestId }
        : {}),
      tenantId: getTenantId(request) as TenantId,
      authorId: getUserId(request) as UserId,
      ...(request.query.title ? { title: request.query.title } : {}),
      ...(request.query.description
        ? { description: request.query.description }
        : {}),
    });

    if (!exported) {
      return reply.code(404).send({
        error: "Auto preset is not yet normalized onto a curated starter",
      });
    }

    return reply.send(exported);
  });

  app.post<{
    Params: { id: string };
    Body: {
      manifestId?: string;
      title?: string;
      description?: string;
    };
  }>("/api/sim-author/auto-presets/:id/manifests", async (request, reply) => {
    const manifest = bootstrapCompatibleAutoPreset({
      presetRef: request.params.id,
      manifestId:
        request.body?.manifestId && request.body.manifestId.length > 0
          ? request.body.manifestId
          : crypto.randomUUID(),
      tenantId: getTenantId(request) as TenantId,
      authorId: getUserId(request) as UserId,
      ...(request.body?.title ? { title: request.body.title } : {}),
      ...(request.body?.description
        ? { description: request.body.description }
        : {}),
    });

    if (!manifest) {
      return reply.code(404).send({ error: "Auto preset not found" });
    }

    const resolved = resolveCompatibleAutoPreset(request.params.id);
    const created = await app.prisma.simulationManifest.create({
      data: {
        id: manifest.id,
        tenantId: getTenantId(request) as string,
        title: manifest.title,
        description: manifest.description ?? null,
        version: manifest.version,
        domain: manifest.domain as any,
        manifest: toInputJsonValue(
          manifest as unknown as Record<string, unknown>,
        ),
      },
    });

    return reply.code(201).send({
      id: created.id,
      title: created.title,
      presetId: resolved?.id ?? request.params.id,
      source: resolved?.source ?? "legacy_auto",
      ...(resolved?.starterId ? { starterId: resolved.starterId } : {}),
      manifest: created.manifest,
    });
  });

  app.get<{ Params: { id: string } }>(
    "/api/sim-author/starters/:id",
    async (request, reply) => {
      const starter = getSimulationStarterById(request.params.id);
      if (!starter) {
        return reply.code(404).send({ error: "Starter not found" });
      }

      return reply.send(starter);
    },
  );

  app.get<{ Params: { id: string } }>(
    "/api/sim-author/legacy-presets/:id",
    async (request, reply) => {
      const resolved = getSimulationStarterByLegacyPresetId(request.params.id);
      if (!resolved) {
        return reply.code(404).send({ error: "Legacy preset not mapped" });
      }

      return reply.send({
        requestedId: request.params.id,
        matchedBy: "legacy_preset",
        starterId: resolved.id,
        starter: resolved,
      });
    },
  );

  app.get<{
    Params: { id: string };
    Querystring: {
      manifestId?: string;
      title?: string;
      description?: string;
    };
  }>("/api/sim-author/starters/:id/bootstrap", async (request, reply) => {
    const manifest = createSimulationStarterManifest({
      starterRef: request.params.id,
      ...(request.query.manifestId
        ? { manifestId: request.query.manifestId }
        : {}),
      tenantId: getTenantId(request) as TenantId,
      authorId: getUserId(request) as UserId,
      ...(request.query.title ? { title: request.query.title } : {}),
      ...(request.query.description
        ? { description: request.query.description }
        : {}),
    });

    if (!manifest) {
      return reply.code(404).send({ error: "Starter not found" });
    }

    return reply.send({
      starterId: request.params.id,
      manifest,
    });
  });

  app.get<{
    Params: { id: string };
    Querystring: {
      format?: "manifest" | "webxr" | "unity";
      manifestId?: string;
      title?: string;
      description?: string;
    };
  }>("/api/sim-author/starters/:id/export", async (request, reply) => {
    const exported = exportSimulationStarterPackage({
      starterRef: request.params.id,
      format: request.query.format ?? "manifest",
      ...(request.query.manifestId
        ? { manifestId: request.query.manifestId }
        : {}),
      tenantId: getTenantId(request) as TenantId,
      authorId: getUserId(request) as UserId,
      ...(request.query.title ? { title: request.query.title } : {}),
      ...(request.query.description
        ? { description: request.query.description }
        : {}),
    });

    if (!exported) {
      return reply.code(404).send({ error: "Starter not found" });
    }

    return reply.send(exported);
  });

  app.post<{
    Params: { id: string };
    Body: {
      manifestId?: string;
      title?: string;
      description?: string;
    };
  }>("/api/sim-author/starters/:id/manifests", async (request, reply) => {
    const manifest = createSimulationStarterManifest({
      starterRef: request.params.id,
      manifestId:
        request.body?.manifestId && request.body.manifestId.length > 0
          ? request.body.manifestId
          : crypto.randomUUID(),
      tenantId: getTenantId(request) as TenantId,
      authorId: getUserId(request) as UserId,
      ...(request.body?.title ? { title: request.body.title } : {}),
      ...(request.body?.description
        ? { description: request.body.description }
        : {}),
    });

    if (!manifest) {
      return reply.code(404).send({ error: "Starter not found" });
    }

    const resolved = resolveSimulationStarterReference(request.params.id);
    const created = await app.prisma.simulationManifest.create({
      data: {
        id: manifest.id,
        tenantId: getTenantId(request) as string,
        title: manifest.title,
        description: manifest.description ?? null,
        version: manifest.version,
        domain: manifest.domain as any,
        manifest: toInputJsonValue(
          manifest as unknown as Record<string, unknown>,
        ),
      },
    });

    return reply.code(201).send({
      id: created.id,
      title: created.title,
      starterId: resolved?.starter.id ?? request.params.id,
      matchedBy: resolved?.matchedBy ?? "starter_id",
      manifest: created.manifest,
    });
  });

  app.get<{
    Querystring: {
      domain?: string;
      status?: string;
      q?: string;
    };
  }>("/api/sim-author/templates", async (request, reply) => {
    const tenantId = getTenantId(request);
    const templates = await templateLibrary.listTemplates(tenantId, {
      ...(request.query.domain ? { domain: request.query.domain } : {}),
      ...(request.query.status ? { status: request.query.status } : {}),
      ...(request.query.q ? { q: request.query.q } : {}),
    });
    return reply.send({ items: templates, total: templates.length });
  });

  app.get<{ Params: { id: string } }>(
    "/api/sim-author/templates/:id",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getTemplateById(
          getTenantId(request),
          request.params.id,
        ),
      );
    },
  );

  app.get("/api/sim-author/templates/summary", async (request, reply) => {
    return reply.send(
      await templateLibrary.getTemplateSummary(getTenantId(request)),
    );
  });

  app.get(
    "/api/sim-author/templates/governance/summary",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getGovernanceSummary(getTenantId(request)),
      );
    },
  );

  app.get(
    "/api/sim-author/templates/coverage/starters",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getStarterCoverage(getTenantId(request)),
      );
    },
  );

  app.get(
    "/api/sim-author/templates/coverage/auto-presets",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getAutoPresetCoverage(getTenantId(request)),
      );
    },
  );

  app.get(
    "/api/sim-author/templates/coverage/summary",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getCoverageSummary(getTenantId(request)),
      );
    },
  );

  app.get<{
    Querystring: {
      domains?: string;
      audiences?: string;
    };
  }>("/api/sim-author/templates/catalog/progress", async (request, reply) => {
    return reply.send(
      await templateLibrary.getCatalogProgressMatrix(getTenantId(request), {
        ...(request.query.domains
          ? {
              domains: request.query.domains
                .split(",")
                .map((value) => value.trim()),
            }
          : {}),
        ...(request.query.audiences
          ? {
              audiences: request.query.audiences
                .split(",")
                .map((value) => value.trim()) as Array<
                "k12" | "undergraduate" | "graduate" | "professional"
              >,
            }
          : {}),
      }),
    );
  });

  app.get<{
    Params: { domain: string };
    Querystring: {
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
    };
  }>(
    "/api/sim-author/templates/catalog/:domain/backlog",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getDomainCatalogBacklog(getTenantId(request), {
          domain: request.params.domain,
          ...(request.query.audience
            ? { audience: request.query.audience }
            : {}),
        }),
      );
    },
  );

  app.get<{
    Querystring: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      limitPerPhase?: string;
    };
  }>("/api/sim-author/templates/coverage/campaign", async (request, reply) => {
    return reply.send(
      await templateLibrary.getCoverageCampaignPlan(getTenantId(request), {
        ...(request.query.domain ? { domain: request.query.domain } : {}),
        ...(request.query.audience ? { audience: request.query.audience } : {}),
        ...(request.query.limitPerPhase
          ? { limitPerPhase: Number(request.query.limitPerPhase) }
          : {}),
      }),
    );
  });

  app.get<{
    Querystring: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      limit?: string;
    };
  }>(
    "/api/sim-author/templates/coverage/action-plan",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getCoverageActionPlan(getTenantId(request), {
          ...(request.query.domain ? { domain: request.query.domain } : {}),
          ...(request.query.audience
            ? { audience: request.query.audience }
            : {}),
          ...(request.query.limit
            ? { limit: Number(request.query.limit) }
            : {}),
        }),
      );
    },
  );

  app.post<{
    Body: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      limit?: number;
    };
  }>(
    "/api/sim-author/templates/coverage/action-plan/execute",
    async (request, reply) => {
      return reply
        .code(201)
        .send(
          await templateLibrary.executeCoverageActionPlan(
            getTenantId(request),
            getUserId(request),
            request.body ?? {},
          ),
        );
    },
  );

  app.post<{
    Body: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      phases?: Array<
        "starter_foundation" | "review_ready_starters" | "legacy_retirement"
      >;
      limitPerPhase?: number;
    };
  }>(
    "/api/sim-author/templates/coverage/campaign/execute",
    async (request, reply) => {
      return reply
        .code(201)
        .send(
          await templateLibrary.executeCoverageCampaign(
            getTenantId(request),
            getUserId(request),
            request.body ?? {},
          ),
        );
    },
  );

  app.post<{
    Params: { domain: string };
    Body: {
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      autoSubmitStarters?: boolean;
      autoSubmitLegacy?: boolean;
      autoPublishLegacy?: boolean;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limit?: number;
    };
  }>(
    "/api/sim-author/templates/catalog/:domain/seed",
    async (request, reply) => {
      return reply.code(201).send(
        await templateLibrary.seedDomainCatalogCoverage(
          getTenantId(request),
          getUserId(request),
          {
            domain: request.params.domain,
            ...(request.body ?? {}),
          },
        ),
      );
    },
  );

  app.post<{
    Body: {
      domains?: string[];
      audiences?: Array<"k12" | "undergraduate" | "graduate" | "professional">;
      autoSubmitStarters?: boolean;
      autoSubmitLegacy?: boolean;
      autoPublishLegacy?: boolean;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limitPerDomain?: number;
    };
  }>("/api/sim-author/templates/catalog/seed-multi", async (request, reply) => {
    return reply
      .code(201)
      .send(
        await templateLibrary.seedCatalogProgressMatrix(
          getTenantId(request),
          getUserId(request),
          request.body ?? {},
        ),
      );
  });

  app.get(
    "/api/sim-author/templates/coverage/retirement-plan",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getLegacyAutoRetirementPlan(getTenantId(request)),
      );
    },
  );

  app.post<{
    Body: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      includeStarters?: boolean;
      includeLegacyAutoPresets?: boolean;
      autoSubmitForReview?: boolean;
      autoPublish?: boolean;
      limit?: number;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    };
  }>(
    "/api/sim-author/templates/coverage/seed-backlog",
    async (request, reply) => {
      const result = await templateLibrary.seedCoverageBacklog(
        getTenantId(request),
        getUserId(request),
        request.body ?? {},
      );
      return reply.code(201).send(result);
    },
  );

  app.post<{
    Body: {
      domain?: string;
      limit?: number;
      autoSubmitForReview?: boolean;
      autoPublish?: boolean;
    };
  }>(
    "/api/sim-author/templates/coverage/retirement-plan/execute",
    async (request, reply) => {
      const result = await templateLibrary.executeLegacyAutoRetirement(
        getTenantId(request),
        getUserId(request),
        request.body ?? {},
      );
      return reply.code(201).send(result);
    },
  );

  app.get<{ Params: { id: string } }>(
    "/api/sim-author/templates/:id/review-history",
    async (request, reply) => {
      const items = await templateLibrary.getTemplateReviewHistory(
        getTenantId(request),
        request.params.id,
      );
      return reply.send({ items, total: items.length });
    },
  );

  app.get<{ Params: { id: string } }>(
    "/api/sim-author/templates/:id/lineage",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.getTemplateLineage(
          getTenantId(request),
          request.params.id,
        ),
      );
    },
  );

  app.get<{
    Params: { id: string };
    Querystring: { format?: "manifest" | "webxr" | "unity" };
  }>("/api/sim-author/templates/:id/export", async (request, reply) => {
    return reply.send(
      await templateLibrary.exportTemplate(
        getTenantId(request),
        request.params.id,
        request.query.format ?? "manifest",
      ),
    );
  });

  app.post<{ Params: { id: string } }>(
    "/api/sim-author/templates/:id/validate",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.validateTemplate(
          getTenantId(request),
          request.params.id,
        ),
      );
    },
  );

  app.post<{ Body: { templateIds?: string[] } }>(
    "/api/sim-author/templates/validate/bulk",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.validateTemplatesBulk(
          getTenantId(request),
          request.body ?? {},
        ),
      );
    },
  );

  app.post<{
    Params: { id: string };
    Body: {
      title?: string;
      description?: string;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    };
  }>(
    "/api/sim-author/templates/preview/from-starter/:id",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.previewTemplateFromStarter(
          getTenantId(request),
          getUserId(request),
          request.params.id,
          request.body ?? {},
        ),
      );
    },
  );

  app.post<{
    Params: { id: string };
    Body: {
      title?: string;
      description?: string;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    };
  }>(
    "/api/sim-author/templates/preview/from-auto-preset/:id",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.previewTemplateFromAutoPreset(
          getTenantId(request),
          getUserId(request),
          request.params.id,
          request.body ?? {},
        ),
      );
    },
  );

  app.post<{
    Params: { id: string };
    Body: {
      title?: string;
      description?: string;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    };
  }>("/api/sim-author/templates/from-starter/:id", async (request, reply) => {
    const result = await templateLibrary.createTemplateFromStarter(
      getTenantId(request),
      getUserId(request),
      request.params.id,
      request.body ?? {},
    );
    return reply.code(201).send(result);
  });

  app.post<{
    Params: { id: string };
    Body: {
      title?: string;
      description?: string;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    };
  }>(
    "/api/sim-author/templates/from-auto-preset/:id",
    async (request, reply) => {
      const result = await templateLibrary.createTemplateFromAutoPreset(
        getTenantId(request),
        getUserId(request),
        request.params.id,
        request.body ?? {},
      );
      return reply.code(201).send(result);
    },
  );

  app.post<{
    Body: {
      starterIds: string[];
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    };
  }>("/api/sim-author/templates/bulk/from-starters", async (request, reply) => {
    const items = await templateLibrary.bulkCreateTemplatesFromStarters(
      getTenantId(request),
      getUserId(request),
      request.body,
    );
    return reply.code(201).send({ items, total: items.length });
  });

  app.post<{
    Body: {
      presetIds: string[];
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    };
  }>(
    "/api/sim-author/templates/bulk/from-auto-presets",
    async (request, reply) => {
      const items = await templateLibrary.bulkCreateTemplatesFromAutoPresets(
        getTenantId(request),
        getUserId(request),
        request.body,
      );
      return reply.code(201).send({ items, total: items.length });
    },
  );

  app.post<{
    Body: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limit?: number;
    };
  }>(
    "/api/sim-author/templates/seed/uncovered-starters",
    async (request, reply) => {
      const items = await templateLibrary.seedMissingStarterTemplates(
        getTenantId(request),
        getUserId(request),
        request.body ?? {},
      );
      return reply.code(201).send({ items, total: items.length });
    },
  );

  app.post<{
    Body: {
      domain?: string;
      source?: "legacy_auto" | "curated_starter";
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limit?: number;
    };
  }>(
    "/api/sim-author/templates/seed/uncovered-auto-presets",
    async (request, reply) => {
      const items = await templateLibrary.seedMissingAutoPresetTemplates(
        getTenantId(request),
        getUserId(request),
        request.body ?? {},
      );
      return reply.code(201).send({ items, total: items.length });
    },
  );

  app.post<{
    Params: { id: string };
    Body: { title?: string; description?: string };
  }>("/api/sim-author/templates/:id/clone", async (request, reply) => {
    const result = await templateLibrary.cloneTemplate(
      getTenantId(request),
      getUserId(request),
      request.params.id,
      request.body ?? {},
    );
    return reply.code(201).send(result);
  });

  app.post<{
    Params: { id: string };
    Body: { notes?: string };
  }>("/api/sim-author/templates/:id/submit-review", async (request, reply) => {
    const result = await templateLibrary.submitTemplateForReview(
      getTenantId(request),
      request.params.id,
      getUserId(request),
      request.body?.notes,
    );
    return reply.send(result);
  });

  app.get(
    "/api/sim-author/templates/reviews/pending",
    async (request, reply) => {
      const items = await templateLibrary.listPendingReviewTemplates(
        getTenantId(request),
      );
      return reply.send({ items, total: items.length });
    },
  );

  app.post<{
    Params: { id: string };
    Body: { action: "approve" | "reject"; notes?: string; publish?: boolean };
  }>("/api/sim-author/templates/:id/review", async (request, reply) => {
    const result = await templateLibrary.reviewTemplate(
      getTenantId(request),
      request.params.id,
      getUserId(request),
      request.body.action,
      {
        ...(request.body.notes ? { notes: request.body.notes } : {}),
        ...(request.body.publish !== undefined
          ? { publish: request.body.publish }
          : {}),
      },
    );
    return reply.send(result);
  });

  app.post<{ Params: { id: string } }>(
    "/api/sim-author/templates/:id/publish",
    async (request, reply) => {
      return reply.send(
        await templateLibrary.publishTemplate(
          getTenantId(request),
          request.params.id,
        ),
      );
    },
  );

  app.post<{
    Params: { id: string };
    Body: { reason: string };
  }>("/api/sim-author/templates/:id/deprecate", async (request, reply) => {
    const result = await templateLibrary.deprecateTemplate(
      getTenantId(request),
      request.params.id,
      request.body.reason,
    );
    return reply.send(result);
  });

  app.post<{
    Body: { templateIds: string[]; notes?: string };
  }>("/api/sim-author/templates/bulk/submit-review", async (request, reply) => {
    return reply.send(
      await templateLibrary.bulkSubmitTemplatesForReview(
        getTenantId(request),
        getUserId(request),
        request.body,
      ),
    );
  });

  app.post<{
    Body: {
      templateIds: string[];
      action: "approve" | "reject";
      notes?: string;
      publish?: boolean;
    };
  }>("/api/sim-author/templates/bulk/review", async (request, reply) => {
    return reply.send(
      await templateLibrary.bulkReviewTemplates(
        getTenantId(request),
        getUserId(request),
        request.body,
      ),
    );
  });

  app.post<{
    Body: { templateIds: string[]; reason: string };
  }>("/api/sim-author/templates/bulk/deprecate", async (request, reply) => {
    return reply.send(
      await templateLibrary.bulkDeprecateTemplates(
        getTenantId(request),
        request.body,
      ),
    );
  });

  app.post<{
    Body: { templateIds: string[] };
  }>("/api/sim-author/templates/bulk/publish", async (request, reply) => {
    return reply.send(
      await templateLibrary.bulkPublishTemplates(
        getTenantId(request),
        request.body,
      ),
    );
  });

  // Generate Manifest
  app.post<{ Body: Omit<GenerateManifestRequest, "tenantId" | "userId"> }>(
    "/api/sim-author/generate",
    async (request, reply) => {
      const authorService = getAuthorService(reply);
      if (!authorService) {
        return;
      }

      return authorService.generateManifest({
        ...request.body,
        tenantId: getTenantId(request) as TenantId,
        userId: getUserId(request) as UserId,
      });
    },
  );

  app.post<{
    Body: Omit<GenerateManifestRequest, "tenantId" | "userId"> & {
      template?: {
        title?: string;
        description?: string;
        difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
        tags?: string[];
        conceptId?: string;
        moduleId?: string;
      };
    };
  }>("/api/sim-author/generate/template", async (request, reply) => {
    const authorService = getAuthorService(reply);
    if (!authorService) {
      return;
    }

    const result = await authorService.generateManifest({
      ...request.body,
      tenantId: getTenantId(request) as TenantId,
      userId: getUserId(request) as UserId,
    });
    const template = await templateLibrary.createTemplateFromGeneratedManifest(
      getTenantId(request),
      getUserId(request),
      result.manifest,
      request.body.template ?? {},
    );

    return reply.code(201).send({
      generation: result,
      template,
    });
  });

  // Refine Manifest
  app.post<{ Body: Omit<RefineManifestRequest, "tenantId" | "userId"> }>(
    "/api/sim-author/refine",
    async (request, reply) => {
      const authorService = getAuthorService(reply);
      if (!authorService) {
        return;
      }

      return authorService.refineManifest({
        ...request.body,
        tenantId: getTenantId(request) as TenantId,
        userId: getUserId(request) as UserId,
      });
    },
  );

  app.post<{
    Params: { id: string };
    Body: {
      refinement: string;
      targetSteps?: string[];
      title?: string;
      description?: string;
      changeNote?: string;
    };
  }>("/api/sim-author/templates/:id/refine", async (request, reply) => {
    const authorService = getAuthorService(reply);
    if (!authorService) {
      return;
    }

    const existingTemplate = await templateLibrary.getTemplateById(
      getTenantId(request),
      request.params.id,
    );
    const manifestPayload = existingTemplate.manifest?.manifest;
    if (!manifestPayload) {
      return reply.code(404).send({ error: "Template manifest not found" });
    }

    const refined = await authorService.refineManifest({
      tenantId: getTenantId(request) as TenantId,
      userId: getUserId(request) as UserId,
      manifest: manifestPayload as unknown as SimulationManifest,
      refinement: request.body.refinement,
      ...(request.body.targetSteps?.length
        ? { targetSteps: request.body.targetSteps as never }
        : {}),
    });

    const template = await templateLibrary.reviseTemplateFromManifest(
      getTenantId(request),
      getUserId(request),
      request.params.id,
      refined.manifest,
      {
        ...(request.body.changeNote
          ? { changeNote: request.body.changeNote }
          : {}),
        ...(request.body.title ? { title: request.body.title } : {}),
        ...(request.body.description
          ? { description: request.body.description }
          : {}),
      },
    );

    return reply.send({
      refinement: refined,
      template,
    });
  });

  // Suggest Parameters
  app.post<{ Body: Omit<SuggestParametersRequest, "tenantId"> }>(
    "/api/sim-author/suggest",
    async (request, reply) => {
      const authorService = getAuthorService(reply);
      if (!authorService) {
        return;
      }

      return authorService.suggestParameters({
        ...request.body,
        tenantId: getTenantId(request) as TenantId,
      });
    },
  );

  // Create a new manifest record
  app.post<{ Body: Record<string, unknown> }>(
    "/api/sim-author/manifests",
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const body = request.body ?? {};

      // ── Parameter guardrails ────────────────────────────────────────────────
      const guardrail = applyManifestGuardrails(body);
      if (!guardrail.valid) {
        return reply.code(400).send({
          error: "Invalid manifest body",
          validationErrors: guardrail.validationErrors,
        });
      }
      const safeBody = guardrail.body;

      const manifestId =
        typeof safeBody.id === "string" && safeBody.id.length > 0
          ? safeBody.id
          : crypto.randomUUID();
      const title =
        typeof safeBody.title === "string" && safeBody.title.length > 0
          ? safeBody.title
          : "Untitled Simulation";
      const domain =
        typeof safeBody.domain === "string" && safeBody.domain.length > 0
          ? safeBody.domain
          : "PHYSICS";
      const version =
        typeof safeBody.version === "string" && safeBody.version.length > 0
          ? safeBody.version
          : "1.0.0";
      const description =
        typeof safeBody.description === "string" ? safeBody.description : null;

      const created = await app.prisma.simulationManifest.create({
        data: {
          id: manifestId,
          tenantId: tenantId as string,
          title,
          description,
          version,
          domain: domain as any,
          manifest: toInputJsonValue(safeBody),
        },
      });

      return reply.code(201).send({
        id: created.id,
        title: created.title,
        manifest: created.manifest,
        ...(guardrail.warnings.length > 0
          ? {
              warnings: guardrail.warnings.map((w) => ({
                parameter: w.parameterName,
                field: w.field,
                message: w.message,
              })),
            }
          : {}),
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

  app.get<{
    Params: { id: string };
    Querystring: { format?: "webxr" | "unity" };
  }>("/api/sim-author/manifests/:id/export", async (request, reply) => {
    const { id } = request.params;
    const tenantId = getTenantId(request);
    const record = await app.prisma.simulationManifest.findFirst({
      where: { id, tenantId: tenantId as string },
    });
    if (!record) {
      return reply.code(404).send({ error: "Manifest not found" });
    }

    const format = request.query.format ?? "webxr";
    if (format === "unity") {
      return reply.send(vrExporter.exportToUnity(record.manifest as any));
    }

    return reply.send(vrExporter.exportToWebXR(record.manifest as any));
  });

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

      // ── Parameter guardrails ──────────────────────────────────────────────
      const guardrail = applyManifestGuardrails(request.body);
      if (!guardrail.valid) {
        return reply.code(400).send({
          error: "Invalid manifest body",
          validationErrors: guardrail.validationErrors,
        });
      }
      const safeBody = guardrail.body;

      await app.prisma.simulationManifest.update({
        where: { id },
        data: {
          ...(typeof safeBody.title === "string" && safeBody.title.length > 0
            ? { title: safeBody.title }
            : {}),
          ...(typeof safeBody.description === "string"
            ? { description: safeBody.description }
            : {}),
          ...(typeof safeBody.version === "string" && safeBody.version.length > 0
            ? { version: safeBody.version }
            : {}),
          ...(typeof safeBody.domain === "string" && safeBody.domain.length > 0
            ? { domain: safeBody.domain as any }
            : {}),
          manifest: toInputJsonValue(safeBody),
        },
      });
      return reply.send({
        success: true,
        ...(guardrail.warnings.length > 0
          ? {
              warnings: guardrail.warnings.map((w) => ({
                parameter: w.parameterName,
                field: w.field,
                message: w.message,
              })),
            }
          : {}),
      });
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
        successCriteria: toInputJsonValue(successCriteria ?? {}),
        estimatedMinutes: estimatedMinutes ?? 10,
      },
      update: {
        simulationManifestId: id,
        interactionType: interactionType ?? "parameter_exploration",
        goal: goal ?? "",
        successCriteria: toInputJsonValue(successCriteria ?? {}),
        estimatedMinutes: estimatedMinutes ?? 10,
      },
    });

    return reply.code(201).send({
      success: true,
      simulationManifestId: id,
      claimRef: claim.claimRef,
    });
  });
}
