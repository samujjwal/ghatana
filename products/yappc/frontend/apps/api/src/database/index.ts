/**
 * Database module exports.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized access to database client and repositories.
 *
 * @doc.type module
 * @doc.purpose Database module exports
 * @doc.layer product
 * @doc.pattern Module
 */

export {
  getPrismaClient,
  initializeDatabase,
  disconnectDatabase,
  withTransaction,
  Prisma,
  PrismaClient,
} from './client';

export type { DatabasePrismaClient } from './client';

export {
  BaseRepository,
  ProjectRepository,
  CanvasRepository,
  PageRepository,
} from './repositories';
