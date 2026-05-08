/**
 * IndexedDB-backed offline sync queue for durable mutation storage.
 * 
 * Stores request body, method, headers, idempotency key, conflict policy version,
 * created timestamp, and replay status for offline mutations.
 * 
 * @doc.type module
 * @doc.purpose Durable offline mutation queue using IndexedDB
 * @doc.layer product
 * @doc.pattern IndexedDB Repository
 */

const DB_NAME = 'tutorputor-offline-sync';
const DB_VERSION = 1;
const STORE_NAME = 'mutations';

export type OfflineMutationType =
  | "module.progress"
  | "simulation.capture"
  | "assessment.attempt"
  | "ai.disabled-state"
  | "telemetry.batch";

export type OfflineSyncStatus = "pending" | "synced" | "conflict" | "failed";

export interface OfflineSyncMetadata {
  clientMutationId: string;
  entityKey: string;
  localVersion: number;
  baseServerVersion?: number;
  createdAt: string;
  updatedAt: string;
  status: OfflineSyncStatus;
  retryCount: number;
}

export interface OfflineMutationRequest {
  url: string;
  method: string;
  headers: Record<string, string>;
  body: string | null;
  idempotencyKey: string;
  conflictPolicyVersion: string;
}

export interface ModuleProgressPayload {
  moduleId: string;
  lessonId?: string;
  progressPercent: number;
  timeSpentSeconds: number;
  completedAt?: string;
  updatedAt: string;
}

export interface SimulationCapturePayload {
  simulationRunId: string;
  captureId: string;
  deterministicHash: string;
  claimId: string;
  evidenceId: string;
  taskId: string;
  outputState: Record<string, number | string | boolean>;
  processFeatures: Record<string, number | string | boolean>;
  capturedAt: string;
}

export interface AssessmentAttemptPayload {
  assessmentId: string;
  attemptId: string;
  status: "draft" | "submitted";
  submittedAt?: string;
  answers: Array<{
    itemId: string;
    response: string | string[] | number | boolean;
    confidence: "low" | "medium" | "high";
    updatedAt: string;
  }>;
}

export interface AiDisabledStatePayload {
  learnerId: string;
  moduleId?: string;
  disabled: boolean;
  reason: "missing_consent" | "revoked_consent" | "offline" | "policy";
  updatedAt: string;
}

export interface OfflineTelemetryBatchPayload {
  batchId: string;
  events: Array<{
    id: string;
    type: string;
    timestamp: string;
    attemptId?: string;
    runId?: string;
  }>;
}

export type OfflineMutationPayload =
  | ModuleProgressPayload
  | SimulationCapturePayload
  | AssessmentAttemptPayload
  | AiDisabledStatePayload
  | OfflineTelemetryBatchPayload;

export interface OfflineSyncRecord {
  id?: number; // IndexedDB auto-increment key
  type: OfflineMutationType;
  payload: OfflineMutationPayload;
  request: OfflineMutationRequest;
  metadata: OfflineSyncMetadata;
}

export interface SyncConflict {
  entityKey: string;
  type: OfflineMutationType;
  reason: "server-newer" | "submitted-attempt-changed" | "hash-mismatch" | "schema-mismatch";
  local: OfflineMutationPayload;
  server: OfflineMutationPayload;
}

export interface ConflictResolution {
  payload: OfflineMutationPayload;
  status: OfflineSyncStatus;
  conflict?: SyncConflict;
}

/**
 * Open IndexedDB database
 */
function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
        store.createIndex('status', 'metadata.status', { unique: false });
        store.createIndex('entityKey', 'metadata.entityKey', { unique: false });
        store.createIndex('createdAt', 'metadata.createdAt', { unique: false });
      }
    };
  });
}

/**
 * Initialize the database
 */
let dbPromise: Promise<IDBDatabase> | null = null;

function getDB(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = openDB();
  }
  return dbPromise;
}

/**
 * Generate unique mutation ID
 */
function mutationId(): string {
  return `offline-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Get current ISO timestamp
 */
function nowIso(): string {
  return new Date().toISOString();
}

/**
 * Generate entity key for a mutation type and payload
 */
export function entityKeyFor(type: OfflineMutationType, payload: OfflineMutationPayload): string {
  switch (type) {
    case "module.progress": {
      const progress = payload as ModuleProgressPayload;
      return `${progress.moduleId}:${progress.lessonId ?? "module"}`;
    }
    case "simulation.capture": {
      const capture = payload as SimulationCapturePayload;
      return `${capture.simulationRunId}:${capture.captureId}`;
    }
    case "assessment.attempt": {
      const attempt = payload as AssessmentAttemptPayload;
      return attempt.attemptId;
    }
    case "ai.disabled-state": {
      const state = payload as AiDisabledStatePayload;
      return `${state.learnerId}:${state.moduleId ?? "global"}`;
    }
    case "telemetry.batch": {
      const batch = payload as OfflineTelemetryBatchPayload;
      return batch.batchId;
    }
  }
}

/**
 * Create an offline sync record
 */
export function createOfflineSyncRecord(
  type: OfflineMutationType,
  payload: OfflineMutationPayload,
  request: OfflineMutationRequest,
  options: { baseServerVersion?: number; localVersion?: number } = {},
): Omit<OfflineSyncRecord, 'id'> {
  const timestamp = nowIso();
  return {
    type,
    payload,
    request,
    metadata: {
      clientMutationId: mutationId(),
      entityKey: entityKeyFor(type, payload),
      localVersion: options.localVersion ?? 1,
      baseServerVersion: options.baseServerVersion,
      createdAt: timestamp,
      updatedAt: timestamp,
      status: "pending",
      retryCount: 0,
    },
  };
}

/**
 * Queue an offline mutation record in IndexedDB
 */
export async function queueOfflineMutation(
  type: OfflineMutationType,
  payload: OfflineMutationPayload,
  request: OfflineMutationRequest,
  options: { baseServerVersion?: number; localVersion?: number } = {},
): Promise<number> {
  const db = await getDB();
  const record = createOfflineSyncRecord(type, payload, request, options);

  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readwrite');
    const store = transaction.objectStore(STORE_NAME);

    // Remove any existing record for the same entity
    const index = store.index('entityKey');
    const deleteRequest = index.openCursor(IDBKeyRange.only(record.metadata.entityKey));

    deleteRequest.onsuccess = () => {
      const cursor = deleteRequest.result;
      if (cursor) {
        cursor.delete();
        cursor.continue();
      }
    };

    // Add the new record
    const addRequest = store.add(record);

    addRequest.onsuccess = () => resolve(addRequest.result as number);
    addRequest.onerror = () => reject(addRequest.error);
  });
}

/**
 * Load all pending mutations from IndexedDB
 */
export async function loadPendingMutations(): Promise<OfflineSyncRecord[]> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readonly');
    const store = transaction.objectStore(STORE_NAME);
    const index = store.index('status');
    const request = index.getAll(IDBKeyRange.only('pending'));

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Mark a mutation as synced and remove it from the queue
 */
export async function markMutationSynced(recordId: number): Promise<void> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readwrite');
    const store = transaction.objectStore(STORE_NAME);
    const request = store.delete(recordId);

    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

/**
 * Mark a mutation as failed with updated retry count
 */
export async function markMutationFailed(
  recordId: number,
  error: string,
): Promise<void> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readwrite');
    const store = transaction.objectStore(STORE_NAME);
    
    const getRequest = store.get(recordId);
    getRequest.onsuccess = () => {
      const record = getRequest.result as OfflineSyncRecord;
      if (record) {
        record.metadata.status = 'failed';
        record.metadata.retryCount = (record.metadata.retryCount || 0) + 1;
        record.metadata.updatedAt = nowIso();
        
        const putRequest = store.put(record);
        putRequest.onsuccess = () => resolve();
        putRequest.onerror = () => reject(putRequest.error);
      } else {
        resolve();
      }
    };
    getRequest.onerror = () => reject(getRequest.error);
  });
}

/**
 * Mark a mutation as conflicted
 */
export async function markMutationConflicted(
  recordId: number,
  conflict: SyncConflict,
): Promise<void> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readwrite');
    const store = transaction.objectStore(STORE_NAME);
    
    const getRequest = store.get(recordId);
    getRequest.onsuccess = () => {
      const record = getRequest.result as OfflineSyncRecord;
      if (record) {
        record.metadata.status = 'conflict';
        record.metadata.updatedAt = nowIso();
        
        // Store conflict in a separate store or as part of the record
        // For now, we'll add it to a conflicts object if it exists
        const putRequest = store.put(record);
        putRequest.onsuccess = () => resolve();
        putRequest.onerror = () => reject(putRequest.error);
      } else {
        resolve();
      }
    };
    getRequest.onerror = () => reject(getRequest.error);
  });
}

/**
 * Get mutation count by status
 */
export async function getMutationCountByStatus(status: OfflineSyncStatus): Promise<number> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readonly');
    const store = transaction.objectStore(STORE_NAME);
    const index = store.index('status');
    const request = index.count(IDBKeyRange.only(status));

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Clear all mutations (for testing or reset)
 */
export async function clearAllMutations(): Promise<void> {
  const db = await getDB();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readwrite');
    const store = transaction.objectStore(STORE_NAME);
    const request = store.clear();

    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

/**
 * Conflict resolution logic (reused from offlineSync.ts)
 */
export function resolveOfflineConflict(
  record: OfflineSyncRecord,
  serverPayload: OfflineMutationPayload | null,
  serverVersion?: number,
): ConflictResolution {
  if (!serverPayload) {
    return { payload: record.payload, status: "pending" };
  }

  if (
    serverVersion !== undefined &&
    record.metadata.baseServerVersion !== undefined &&
    serverVersion > record.metadata.baseServerVersion &&
    record.type !== "telemetry.batch"
  ) {
    const conflict: SyncConflict = {
      entityKey: record.metadata.entityKey,
      type: record.type,
      reason: "server-newer",
      local: record.payload,
      server: serverPayload,
    };
    return { payload: record.payload, status: "conflict", conflict };
  }

  switch (record.type) {
    case "module.progress":
      return {
        payload: mergeModuleProgress(record.payload as ModuleProgressPayload, serverPayload as ModuleProgressPayload),
        status: "pending",
      };
    case "simulation.capture":
      return resolveSimulationCapture(record.payload as SimulationCapturePayload, serverPayload as SimulationCapturePayload);
    case "assessment.attempt":
      return resolveAssessmentAttempt(record.payload as AssessmentAttemptPayload, serverPayload as AssessmentAttemptPayload);
    case "ai.disabled-state":
      return {
        payload: mergeAiDisabledState(record.payload as AiDisabledStatePayload, serverPayload as AiDisabledStatePayload),
        status: "pending",
      };
    case "telemetry.batch":
      return {
        payload: mergeTelemetryBatch(record.payload as OfflineTelemetryBatchPayload, serverPayload as OfflineTelemetryBatchPayload),
        status: "pending",
      };
  }
}

function mergeModuleProgress(
  local: ModuleProgressPayload,
  server: ModuleProgressPayload,
): ModuleProgressPayload {
  return {
    ...server,
    ...local,
    progressPercent: Math.max(local.progressPercent, server.progressPercent),
    timeSpentSeconds: Math.max(local.timeSpentSeconds, server.timeSpentSeconds),
    completedAt: local.completedAt ?? server.completedAt,
    updatedAt: local.updatedAt > server.updatedAt ? local.updatedAt : server.updatedAt,
  };
}

function resolveSimulationCapture(
  local: SimulationCapturePayload,
  server: SimulationCapturePayload,
): ConflictResolution {
  if (local.deterministicHash !== server.deterministicHash) {
    return {
      payload: local,
      status: "conflict",
      conflict: {
        entityKey: `${local.simulationRunId}:${local.captureId}`,
        type: "simulation.capture",
        reason: "hash-mismatch",
        local,
        server,
      },
    };
  }

  return { payload: server.capturedAt > local.capturedAt ? server : local, status: "pending" };
}

function resolveAssessmentAttempt(
  local: AssessmentAttemptPayload,
  server: AssessmentAttemptPayload,
): ConflictResolution {
  if (server.status === "submitted" && JSON.stringify(server.answers) !== JSON.stringify(local.answers)) {
    return {
      payload: local,
      status: "conflict",
      conflict: {
        entityKey: local.attemptId,
        type: "assessment.attempt",
        reason: "submitted-attempt-changed",
        local,
        server,
      },
    };
  }

  if (local.status === "submitted" || local.submittedAt) {
    return { payload: { ...server, ...local, status: "submitted" }, status: "pending" };
  }

  return { payload: local, status: "pending" };
}

function mergeAiDisabledState(
  local: AiDisabledStatePayload,
  server: AiDisabledStatePayload,
): AiDisabledStatePayload {
  if (local.disabled || server.disabled) {
    const stricter = local.disabled ? local : server;
    return { ...stricter, disabled: true };
  }
  return local.updatedAt > server.updatedAt ? local : server;
}

function mergeTelemetryBatch(
  local: OfflineTelemetryBatchPayload,
  server: OfflineTelemetryBatchPayload,
): OfflineTelemetryBatchPayload {
  const byId = new Map<string, OfflineTelemetryBatchPayload["events"][number]>();
  for (const event of [...server.events, ...local.events]) {
    byId.set(event.id, event);
  }
  return {
    batchId: local.batchId,
    events: [...byId.values()].sort((a, b) => a.timestamp.localeCompare(b.timestamp)),
  };
}
