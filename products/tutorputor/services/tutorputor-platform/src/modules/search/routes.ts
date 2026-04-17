/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for search operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from "fastify";
import { z } from "zod";
import { SearchServiceImpl } from "./service";
import type { TenantId } from "@tutorputor/contracts";
import type { ModuleId } from "@tutorputor/contracts";
import type { SearchFilters } from "./service";
import { getTenantId } from "../../core/http/requestContext.js";

const SEARCH_TYPES = [
  "module",
  "thread",
  "learning_path",
  "classroom",
] as const;
type SearchType = NonNullable<SearchFilters["type"]>[number];

const SearchQuerySchema = z.object({
  q: z.string().min(1, 'Query parameter "q" is required'),
  limit: z.coerce.number().int().positive().max(100).optional(),
  offset: z.coerce.number().int().min(0).optional(),
  sortBy: z.enum(["relevance", "newest", "rating", "popularity"]).optional(),
  type: z.string().optional(),
  category: z.string().optional(),
  minPrice: z.coerce.number().min(0).optional(),
  maxPrice: z.coerce.number().min(0).optional(),
  free: z.coerce.boolean().optional(),
});

const AutocompleteQuerySchema = z.object({
  q: z.string().min(1, 'Query parameter "q" is required'),
  limit: z.coerce.number().int().positive().max(50).optional(),
});

const PopularQuerySchema = z.object({
  limit: z.coerce.number().int().positive().max(50).optional(),
});

const SimilarParamsSchema = z.object({
  moduleId: z.string().min(1),
});

const SimilarQuerySchema = z.object({
  limit: z.coerce.number().int().positive().max(50).optional(),
});

function isSearchType(value: string): value is SearchType {
  return (SEARCH_TYPES as readonly string[]).includes(value);
}

function createValidationErrorResponse(error: z.ZodError) {
  return {
    error: "Validation Error",
    message: error.issues[0]?.message ?? "Invalid request",
    details: error.issues.map((issue) => ({
      path: issue.path.join("."),
      message: issue.message,
    })),
  };
}

export const searchRoutes: FastifyPluginAsync<{ service?: SearchServiceImpl }> = async (
  app,
  options,
) => {
  const searchService = options.service ?? new SearchServiceImpl(app.prisma);

  /**
   * GET /search
   * Search across modules, threads, etc.
   */
  app.get<{
    Querystring: {
      q: string;
      limit?: number;
      offset?: number;
      sortBy?: "relevance" | "newest" | "rating" | "popularity";
      type?: string; // Comma separated
      category?: string; // Comma separated
      minPrice?: number;
      maxPrice?: number;
      free?: boolean;
    };
  }>("/", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;

    const parsedQuery = SearchQuerySchema.safeParse(request.query);
    if (!parsedQuery.success) {
      return reply.code(400).send(createValidationErrorResponse(parsedQuery.error));
    }

    const {
      q,
      limit,
      offset,
      sortBy,
      type,
      category,
      minPrice,
      maxPrice,
      free,
    } = parsedQuery.data;

    const filters: SearchFilters = {};
    if (type) {
      filters.type = type.split(",").filter(isSearchType);
    }
    if (category) {
      filters.category = category.split(",");
    }
    if (
      minPrice !== undefined ||
      maxPrice !== undefined ||
      free !== undefined
    ) {
      filters.price = {
        ...(typeof minPrice === "number" ? { min: minPrice } : {}),
        ...(typeof maxPrice === "number" ? { max: maxPrice } : {}),
        ...(typeof free === "boolean" ? { free } : {}),
      };
    }

    try {
      const results = await searchService.search({
        tenantId,
        query: q,
        ...(limit ? { limit: Number(limit) } : {}),
        ...(offset ? { offset: Number(offset) } : {}),
        ...(sortBy ? { sortBy } : {}),
        filters,
      });
      return reply.send(results);
    } catch (error) {
      app.log.error(error, "Search failed");
      return reply.code(500).send({
        error: "Search failed",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /search/autocomplete
   * Get autocomplete suggestions
   */
  app.get<{
    Querystring: {
      q: string;
      limit?: number;
    };
  }>("/autocomplete", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;

    const parsedQuery = AutocompleteQuerySchema.safeParse(request.query);
    if (!parsedQuery.success) {
      return reply.code(400).send(createValidationErrorResponse(parsedQuery.error));
    }

    const { q, limit } = parsedQuery.data;

    try {
      const suggestions = await searchService.autocomplete(
        tenantId,
        q || "",
        limit ? Number(limit) : undefined,
      );
      return reply.send(suggestions);
    } catch (error) {
      app.log.error(error, "Autocomplete failed");
      return reply.code(500).send({
        error: "Autocomplete failed",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /search/popular
   * Get popular search terms (or modules)
   */
  app.get<{
    Querystring: {
      limit?: number;
    };
  }>("/popular", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;

    const parsedQuery = PopularQuerySchema.safeParse(request.query);
    if (!parsedQuery.success) {
      return reply.code(400).send(createValidationErrorResponse(parsedQuery.error));
    }

    const { limit } = parsedQuery.data;

    try {
      const popular = await searchService.getPopularSearches(
        tenantId,
        limit ? Number(limit) : undefined,
      );
      return reply.send(popular);
    } catch (error) {
      app.log.error(error, "Failed to get popular searches");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /search/similar/:moduleId
   * Get similar modules
   */
  app.get<{
    Params: { moduleId: string };
    Querystring: { limit?: number };
  }>("/similar/:moduleId", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;

    const parsedParams = SimilarParamsSchema.safeParse(request.params);
    if (!parsedParams.success) {
      return reply.code(400).send(createValidationErrorResponse(parsedParams.error));
    }

    const parsedQuery = SimilarQuerySchema.safeParse(request.query);
    if (!parsedQuery.success) {
      return reply.code(400).send(createValidationErrorResponse(parsedQuery.error));
    }

    const { moduleId } = parsedParams.data;
    const { limit } = parsedQuery.data;

    try {
      const similar = await searchService.getSimilar(
        tenantId,
        moduleId as ModuleId,
        limit ? Number(limit) : undefined,
      );
      return reply.send(similar);
    } catch (error) {
      app.log.error(error, "Failed to get similar items");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });
};
