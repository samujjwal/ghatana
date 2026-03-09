/**
 * FlashIt Mobile - Database Module Index
 *
 * @doc.type module
 * @doc.purpose Database module exports
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

// Database manager
export { database } from './database';
export type {
  MomentRecord,
  SphereRecord,
  MediaFileRecord,
  SyncQueueRecord,
} from './database';

// Repositories
export { momentRepository } from './momentRepository';
export type {
  Moment,
  CreateMomentInput,
  UpdateMomentInput,
  MomentFilterOptions,
} from './momentRepository';

export { sphereRepository } from './sphereRepository';
export type {
  Sphere,
  CreateSphereInput,
  UpdateSphereInput,
} from './sphereRepository';

// Sync service
export { databaseSyncService } from './syncService';
export type { SyncConfig, SyncResult } from './syncService';
