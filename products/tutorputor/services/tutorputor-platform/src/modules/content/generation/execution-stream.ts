/**
 * Generation Execution Stream Protocol
 *
 * Shared message envelope for execution SSE subscribers and worker-side
 * telemetry publishers.
 *
 * @doc.type module
 * @doc.purpose Define the execution streaming protocol shared across platform and workers
 * @doc.layer product
 * @doc.pattern StreamProtocol
 */

import type {
  GenerationExecutionSnapshot,
  GenerationExecutionWorkerTelemetry,
} from "@tutorputor/contracts/v1/content-studio";
import type {
  ExecutionSummary,
  JobExecutionResult,
} from "./execution-service.js";

export interface GenerationExecutionStreamMessage {
  kind: "snapshot" | "job_result" | "summary" | "telemetry";
  requestId: string;
  at: string;
  snapshot?: GenerationExecutionSnapshot;
  jobResult?: JobExecutionResult;
  summary?: ExecutionSummary;
  telemetry?: GenerationExecutionWorkerTelemetry;
}

export function getGenerationExecutionChannel(requestId: string): string {
  return `tutorputor:generation-execution:${requestId}`;
}
