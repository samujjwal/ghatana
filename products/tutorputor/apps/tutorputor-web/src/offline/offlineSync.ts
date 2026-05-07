export const OFFLINE_SYNC_STORAGE_KEY = "tutorputor.offline.syncQueue";

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

export interface OfflineSyncRecord<TPayload extends OfflineMutationPayload = OfflineMutationPayload> {
  type: OfflineMutationType;
  payload: TPayload;
  metadata: OfflineSyncMetadata;
}

export interface SyncConflict<TPayload extends OfflineMutationPayload = OfflineMutationPayload> {
  entityKey: string;
  type: OfflineMutationType;
  reason: "server-newer" | "submitted-attempt-changed" | "hash-mismatch" | "schema-mismatch";
  local: TPayload;
  server: TPayload;
}

export interface ConflictResolution<TPayload extends OfflineMutationPayload = OfflineMutationPayload> {
  payload: TPayload;
  status: OfflineSyncStatus;
  conflict?: SyncConflict<TPayload>;
}

function nowIso(): string {
  return new Date().toISOString();
}

function mutationId(): string {
  return `offline-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

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

export function createOfflineSyncRecord<TPayload extends OfflineMutationPayload>(
  type: OfflineMutationType,
  payload: TPayload,
  options: { baseServerVersion?: number; localVersion?: number } = {},
): OfflineSyncRecord<TPayload> {
  const timestamp = nowIso();
  return {
    type,
    payload,
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

export function resolveOfflineConflict<TPayload extends OfflineMutationPayload>(
  record: OfflineSyncRecord<TPayload>,
  serverPayload: TPayload | null,
  serverVersion?: number,
): ConflictResolution<TPayload> {
  if (!serverPayload) {
    return { payload: record.payload, status: "pending" };
  }

  if (
    serverVersion !== undefined &&
    record.metadata.baseServerVersion !== undefined &&
    serverVersion > record.metadata.baseServerVersion &&
    record.type !== "telemetry.batch"
  ) {
    const conflict: SyncConflict<TPayload> = {
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
        payload: mergeModuleProgress(record.payload as ModuleProgressPayload, serverPayload as ModuleProgressPayload) as TPayload,
        status: "pending",
      };
    case "simulation.capture":
      return resolveSimulationCapture(record.payload as SimulationCapturePayload, serverPayload as SimulationCapturePayload) as ConflictResolution<TPayload>;
    case "assessment.attempt":
      return resolveAssessmentAttempt(record.payload as AssessmentAttemptPayload, serverPayload as AssessmentAttemptPayload) as ConflictResolution<TPayload>;
    case "ai.disabled-state":
      return {
        payload: mergeAiDisabledState(record.payload as AiDisabledStatePayload, serverPayload as AiDisabledStatePayload) as TPayload,
        status: "pending",
      };
    case "telemetry.batch":
      return {
        payload: mergeTelemetryBatch(record.payload as OfflineTelemetryBatchPayload, serverPayload as OfflineTelemetryBatchPayload) as TPayload,
        status: "pending",
      };
  }
}

export function mergeModuleProgress(
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

export function resolveSimulationCapture(
  local: SimulationCapturePayload,
  server: SimulationCapturePayload,
): ConflictResolution<SimulationCapturePayload> {
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

export function resolveAssessmentAttempt(
  local: AssessmentAttemptPayload,
  server: AssessmentAttemptPayload,
): ConflictResolution<AssessmentAttemptPayload> {
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

export function mergeAiDisabledState(
  local: AiDisabledStatePayload,
  server: AiDisabledStatePayload,
): AiDisabledStatePayload {
  if (local.disabled || server.disabled) {
    const stricter = local.disabled ? local : server;
    return { ...stricter, disabled: true };
  }
  return local.updatedAt > server.updatedAt ? local : server;
}

export function mergeTelemetryBatch(
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

export function loadOfflineSyncQueue(storage: Pick<Storage, "getItem"> = window.localStorage): OfflineSyncRecord[] {
  const raw = storage.getItem(OFFLINE_SYNC_STORAGE_KEY);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function saveOfflineSyncQueue(
  records: OfflineSyncRecord[],
  storage: Pick<Storage, "setItem"> = window.localStorage,
): void {
  storage.setItem(OFFLINE_SYNC_STORAGE_KEY, JSON.stringify(records));
}

export function queueOfflineSyncRecord(
  record: OfflineSyncRecord,
  storage: Pick<Storage, "getItem" | "setItem"> = window.localStorage,
): OfflineSyncRecord[] {
  const queue = loadOfflineSyncQueue(storage);
  const withoutDuplicate = queue.filter(
    (queued) => !(queued.type === record.type && queued.metadata.entityKey === record.metadata.entityKey),
  );
  const next = [...withoutDuplicate, record].sort((a, b) => a.metadata.createdAt.localeCompare(b.metadata.createdAt));
  saveOfflineSyncQueue(next, storage);
  return next;
}

export function markOfflineRecordSynced(
  clientMutationId: string,
  storage: Pick<Storage, "getItem" | "setItem"> = window.localStorage,
): OfflineSyncRecord[] {
  const next = loadOfflineSyncQueue(storage).filter(
    (record) => record.metadata.clientMutationId !== clientMutationId,
  );
  saveOfflineSyncQueue(next, storage);
  return next;
}
