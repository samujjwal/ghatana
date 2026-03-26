/**
 * Versioning GraphQL Resolver
 *
 * Provides queries and mutations for canvas document version history.
 * Delegates all business logic to VersioningService.
 *
 * @doc.type module
 * @doc.purpose GraphQL resolvers for canvas versioning
 * @doc.layer product
 * @doc.pattern GraphQL Resolvers
 */

import { getVersioningService } from '../../services/versioning/versioning.service';

// ============================================================================
// Context shape
// ============================================================================

interface GraphQLContext {
  userId?: string;
}

// ============================================================================
// Helpers
// ============================================================================

function requireAuth(context: GraphQLContext): string {
  if (!context.userId) {
    throw new Error('Authentication required');
  }
  return context.userId;
}

// ============================================================================
// Resolver map
// ============================================================================

/**
 * Versioning resolver object — spread into the root resolver map.
 *
 * @doc.type object
 * @doc.purpose Canvas versioning query and mutation resolvers
 * @doc.layer product
 * @doc.pattern GraphQL Resolvers
 */
export const versioningResolvers = {
  Query: {
    /**
     * List all version snapshots for a given canvas document (newest first).
     */
    canvasVersions: async (
      _parent: unknown,
      args: { canvasId: string },
      context: GraphQLContext
    ) => {
      requireAuth(context);
      return getVersioningService().listVersions(args.canvasId);
    },

    /**
     * Get a specific version by canvas ID and version number.
     */
    canvasVersion: async (
      _parent: unknown,
      args: { canvasId: string; version: number },
      context: GraphQLContext
    ) => {
      requireAuth(context);
      return getVersioningService().getVersion(args.canvasId, args.version);
    },
  },

  Mutation: {
    /**
     * Manually save the current canvas content as a named snapshot.
     */
    saveCanvasVersion: async (
      _parent: unknown,
      args: { canvasId: string; content: unknown; changeSummary?: string },
      context: GraphQLContext
    ) => {
      const userId = requireAuth(context);
      return getVersioningService().createSnapshot({
        canvasId: args.canvasId,
        content: args.content,
        changeType: 'MANUAL_SAVE',
        changedBy: userId,
        changeSummary: args.changeSummary,
      });
    },

    /**
     * Restore the canvas to a previously saved version.
     */
    restoreCanvasVersion: async (
      _parent: unknown,
      args: { canvasId: string; version: number },
      context: GraphQLContext
    ) => {
      const userId = requireAuth(context);
      return getVersioningService().restore({
        canvasId: args.canvasId,
        targetVersion: args.version,
        restoredBy: userId,
      });
    },

    /**
     * Prune old version history, keeping only the most recent N snapshots.
     */
    pruneCanvasVersionHistory: async (
      _parent: unknown,
      args: { canvasId: string; keepCount: number },
      context: GraphQLContext
    ) => {
      requireAuth(context);
      return getVersioningService().pruneHistory(args.canvasId, args.keepCount);
    },
  },
};
