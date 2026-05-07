import type { LearningTelemetryEvent } from "@tutorputor/contracts/v1/telemetry-events";
import { createOfflineSyncRecord, queueOfflineSyncRecord } from "../offline/offlineSync";

export interface LearningTelemetryBatchResponse {
  count: number;
}

export async function emitLearningTelemetryBatch(
  events: LearningTelemetryEvent[],
  token?: string | null,
): Promise<LearningTelemetryBatchResponse> {
  let response: Response;
  try {
    response = await fetch("/api/v1/telemetry/learning/batch", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ events }),
    });
  } catch (error) {
    queueTelemetryBatch(events);
    throw error;
  }

  if (response.status === 202 || response.headers.get("X-TutorPutor-Offline-Queued") === "true") {
    queueTelemetryBatch(events);
    return { count: events.length };
  }

  if (!response.ok) {
    if (response.status === 503) {
      queueTelemetryBatch(events);
    }
    const error = await response
      .json()
      .catch(() => ({ error: "Telemetry ingest failed" }));
    throw new Error(error.error || `Telemetry ingest failed: ${response.status}`);
  }

  return response.json();
}

function queueTelemetryBatch(events: LearningTelemetryEvent[]): void {
  const record = createOfflineSyncRecord("telemetry.batch", {
    batchId: `telemetry-${Date.now()}`,
    events: events.map((event, index) => ({
      id: `${event.type}:${event.timestamp}:${index}`,
      type: event.type,
      timestamp: event.timestamp,
      attemptId: "object" in event && "attemptId" in event.object ? String(event.object.attemptId) : undefined,
      runId: "object" in event && "runId" in event.object ? String(event.object.runId) : undefined,
    })),
  });
  queueOfflineSyncRecord(record);
}
