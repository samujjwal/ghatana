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
} from "@ghatana/tutorputor-simulation-engine";
import type {
  GenerateManifestRequest,
  RefineManifestRequest,
  SuggestParametersRequest,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";
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
}
