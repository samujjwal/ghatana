/**
 * Storage Governance Module
 *
 * Centralized, typed, and governed browser storage access.
 * All new localStorage/sessionStorage reads/writes should go through `TypedStorage`.
 * Direct `localStorage.getItem('...')` is deprecated in mounted code.
 *
 * @doc.type module
 * @doc.purpose Govern all browser storage with classification, typing, and migration tracking
 * @doc.layer product
 */

export {
  STORAGE_REGISTRY,
  type StorageKeyMeta,
  type StorageSensitivity,
  type StorageBackend,
  type KnownStorageKey,
  getStorageMeta,
  isKnownStorageKey,
  getKeysRequiringMigration,
  getHighSensitivityKeys,
} from './StorageRegistry';

export {
  readStorage,
  writeStorage,
  removeStorage,
  readFlag,
  writeFlag,
  readNumber,
  readObject,
  subscribeStorage,
  type StorageChangeEvent,
} from './TypedStorage';
