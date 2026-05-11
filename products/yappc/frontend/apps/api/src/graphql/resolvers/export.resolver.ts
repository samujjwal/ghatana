/**
 * Export Resolver
 *
 * Handles creation and retrieval of project export artifacts.
 * Export generation is asynchronous: the mutation queues a job and returns
 * immediately with PENDING status. A background worker (not shown) updates the
 * artifact to READY or FAILED once generation completes.
 *
 * @doc.type resolver
 * @doc.purpose GraphQL mutations and queries for project export artifacts
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient } from '../../database/client';
import type { ResolverContext } from '../types';

const prisma = getPrismaClient();

// ---------------------------------------------------------------------------
// Arg shapes
// ---------------------------------------------------------------------------

type ExportFormat = 'JSON' | 'MARKDOWN' | 'PDF' | 'ZIP' | 'HTML';

interface CreateExportArgs {
  projectId: string;
  format: ExportFormat;
  includeRequirements?: boolean;
  includeDiagrams?: boolean;
  includeCode?: boolean;
}

interface ExportArtifactsArgs {
  projectId: string;
}

interface ExportArtifactArgs {
  id: string;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function requireUserId(context: ResolverContext): string {
  if (!context.userId) {
    throw new Error('Authentication required');
  }
  return context.userId;
}

// ---------------------------------------------------------------------------
// Resolvers
// ---------------------------------------------------------------------------

export const exportResolvers = {
  Query: {
    /**
     * List all export artifacts for a project, newest first.
     */
    exportArtifacts: async (
      _parent: unknown,
      args: ExportArtifactsArgs,
      context: ResolverContext
    ) => {
      requireUserId(context);

      return prisma.exportArtifact.findMany({
        where: { projectId: args.projectId },
        orderBy: { createdAt: 'desc' },
      });
    },

    /**
     * Get a single export artifact by ID.
     */
    exportArtifact: async (
      _parent: unknown,
      args: ExportArtifactArgs,
      context: ResolverContext
    ) => {
      requireUserId(context);

      return prisma.exportArtifact.findUnique({
        where: { id: args.id },
      });
    },
  },

  Mutation: {
    /**
     * createExport
     *
     * Queues a new export job for the specified project.
     * The artifact starts in PENDING state; a background worker updates it
     * to PROCESSING → READY (or FAILED) once generation completes.
     *
     * In development the artifact is immediately set to READY with a synthetic
     * download URL so the UI flow can be validated end-to-end.
     */
    createExport: async (
      _parent: unknown,
      args: CreateExportArgs,
      context: ResolverContext
    ) => {
      const userId = requireUserId(context);

      const isDevelopment = process.env['NODE_ENV'] !== 'production';

      // In production: set PENDING and hand off to a worker queue.
      // In development: resolve immediately so the UI can be exercised.
      const initialStatus = isDevelopment ? 'READY' : 'PENDING';
      const downloadUrl = isDevelopment
        ? `/api/exports/dev-placeholder/${args.format.toLowerCase()}`
        : null;

      const artifact = await prisma.exportArtifact.create({
        data: {
          projectId: args.projectId,
          createdById: userId,
          format: args.format,
          status: initialStatus,
          includeRequirements: args.includeRequirements ?? true,
          includeDiagrams: args.includeDiagrams ?? true,
          includeCode: args.includeCode ?? false,
          downloadUrl,
          completedAt: isDevelopment ? new Date() : null,
        },
      });

      return artifact;
    },
  },

  // Serialize Date fields for the ExportArtifact type
  ExportArtifact: {
    createdAt: (parent: { createdAt: Date | string }) =>
      parent.createdAt instanceof Date
        ? parent.createdAt.toISOString()
        : parent.createdAt,
    completedAt: (parent: { completedAt?: Date | string | null }) =>
      parent.completedAt instanceof Date
        ? parent.completedAt.toISOString()
        : (parent.completedAt ?? null),
  },
};
