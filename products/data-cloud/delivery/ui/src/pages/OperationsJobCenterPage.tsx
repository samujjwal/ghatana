/**
 * Operations Job Center Page
 *
 * Consolidated view of in-flight and recently completed background jobs tracked
 * by OperationsContext. Provides a single place to audit operation outcomes
 * beyond the floating ActiveOperationsBar.
 *
 * @doc.type page
 * @doc.purpose Unified background operation visibility for operators/admins
 * @doc.layer frontend
 */

import { Button } from "@ghatana/design-system";
import { CheckCircle2, Clock3, Filter, RefreshCw, XCircle } from "lucide-react";
import React from "react";
import { Link } from "react-router";
import {
  useOperationTimeline,
  type OperationJob,
  type OperationKind,
} from "../api/operations.service";
import { useOperations } from "../contexts/OperationsContext";
import type { BackgroundJob } from "../contexts/OperationsContext";
import { cardStyles, cn, textStyles } from "../lib/theme";

function statusTone(status: "pending" | "success" | "failure"): string {
  if (status === "success") return "text-green-600 dark:text-green-300";
  if (status === "failure") return "text-red-600 dark:text-red-300";
  return "text-blue-600 dark:text-blue-300";
}

function StatusIcon({
  status,
}: {
  status: "pending" | "success" | "failure";
}): React.ReactElement {
  if (status === "success") {
    return <CheckCircle2 className="h-4 w-4 text-green-500" />;
  }

  if (status === "failure") {
    return <XCircle className="h-4 w-4 text-red-500" />;
  }

  return <RefreshCw className="h-4 w-4 animate-spin text-blue-500" />;
}

function mapOperationStatus(status: OperationJob["status"]): BackgroundJob["status"] {
  if (status === "SUCCEEDED" || status === "CANCELLED") return "success";
  if (status === "FAILED" || status === "BLOCKED") return "failure";
  return "pending";
}

// Pass 9: Cross-plane filtering types
type PlaneType = "all" | "data-cloud" | "aep" | "agents" | "media" | "connectors";
type StatusType = "all" | "INITIATED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED" | "BLOCKED";

const PLANE_FILTERS: { label: string; value: PlaneType; kinds: OperationKind[] }[] = [
  { label: "All Planes", value: "all", kinds: [] },
  { label: "Data Cloud", value: "data-cloud", kinds: ["PIPELINE_EXECUTION", "PIPELINE_CANCEL", "PIPELINE_RETRY", "PIPELINE_ROLLBACK", "PIPELINE_CHECKPOINT", "PIPELINE_RESTORE", "BACKGROUND_TASK"] },
  { label: "AEP", value: "aep", kinds: ["AEP_PATTERN_RUN"] },
  { label: "Agents", value: "agents", kinds: ["AGENT_RUN"] },
  { label: "Media", value: "media", kinds: ["MEDIA_PROCESSING", "MEDIA_RETENTION", "MEDIA_DELETE"] },
  { label: "Connectors", value: "connectors", kinds: ["CONNECTOR_SYNC", "CONNECTOR_TEST", "CONNECTOR_SCHEMA", "CONNECTOR_HEALTH", "CONNECTOR_CREDENTIAL_ROTATION"] },
];

const STATUS_FILTERS: { label: string; value: StatusType }[] = [
  { label: "All Statuses", value: "all" },
  { label: "Initiated", value: "INITIATED" },
  { label: "Running", value: "RUNNING" },
  { label: "Completed", value: "COMPLETED" },
  { label: "Failed", value: "FAILED" },
  { label: "Cancelled", value: "CANCELLED" },
  { label: "Blocked", value: "BLOCKED" },
];

function getPlaneFromKind(kind: OperationKind): PlaneType {
  if (PLANE_FILTERS.find((p) => p.value === "media")?.kinds.includes(kind)) return "media";
  if (PLANE_FILTERS.find((p) => p.value === "connectors")?.kinds.includes(kind)) return "connectors";
  if (PLANE_FILTERS.find((p) => p.value === "agents")?.kinds.includes(kind)) return "agents";
  if (PLANE_FILTERS.find((p) => p.value === "aep")?.kinds.includes(kind)) return "aep";
  if (PLANE_FILTERS.find((p) => p.value === "data-cloud")?.kinds.includes(kind)) return "data-cloud";
  return "all";
}

function operationToJob(operation: OperationJob): BackgroundJob {
  return {
    id: operation.operationId,
    name: operation.action,
    status: mapOperationStatus(operation.status),
    startedAt: operation.createdAt,
    completedAt: operation.completedAt || undefined,
    detail:
      operation.summary ||
      operation.detail ||
      [operation.resourceType, operation.resourceId].filter(Boolean).join(":"),
  };
}

export function OperationsJobCenterPage(): React.ReactElement {
  const { jobs, dismissJob, dismissAllCompleted } = useOperations();
  const { data: operationTimeline, isLoading } = useOperationTimeline(100);
  const [selectedPlane, setSelectedPlane] = React.useState<PlaneType>("all");
  const [selectedStatus, setSelectedStatus] = React.useState<StatusType>("all");
  const [tenantFilter, setTenantFilter] = React.useState("");
  const [correlationIdFilter, setCorrelationIdFilter] = React.useState("");
  const [mediaJobIdFilter, setMediaJobIdFilter] = React.useState("");
  const [pipelineExecutionIdFilter, setPipelineExecutionIdFilter] = React.useState("");
  const [agentInvocationIdFilter, setAgentInvocationIdFilter] = React.useState("");

  const unifiedJobs = React.useMemo(() => {
    const byId = new Map<string, BackgroundJob>();
    for (const operation of operationTimeline?.items ?? []) {
      const job = operationToJob(operation);
      // Pass 9: Store plane info in metadata for filtering
      (job as BackgroundJob & { plane?: PlaneType }).plane = getPlaneFromKind(operation.kind);
      byId.set(operation.operationId, job);
    }
    for (const job of jobs) {
      byId.set(job.id, job);
    }
    return Array.from(byId.values()).sort(
      (a, b) =>
        new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
    );
  }, [jobs, operationTimeline]);

  // Pass 9: Filter by plane, status, tenant, correlation ID, media job ID, pipeline execution ID, agent invocation ID
  const filteredJobs = React.useMemo(() => {
    return unifiedJobs.filter((job) => {
      const plane = (job as BackgroundJob & { plane?: PlaneType }).plane;
      const operation = operationTimeline?.items.find((op) => op.operationId === job.id);
      
      // Plane filter
      if (selectedPlane !== "all" && plane !== selectedPlane) return false;
      
      // Status filter
      if (selectedStatus !== "all" && operation?.status !== selectedStatus) return false;
      
      // Tenant filter
      if (tenantFilter && operation?.tenantId && !operation.tenantId.toLowerCase().includes(tenantFilter.toLowerCase())) return false;
      
      // Correlation ID filter
      if (correlationIdFilter && operation?.correlationId && !operation.correlationId.toLowerCase().includes(correlationIdFilter.toLowerCase())) return false;
      
      // Media job ID filter
      if (mediaJobIdFilter && operation?.metadata?.mediaJobId && !String(operation.metadata.mediaJobId).toLowerCase().includes(mediaJobIdFilter.toLowerCase())) return false;
      
      // Pipeline execution ID filter
      if (pipelineExecutionIdFilter && operation?.metadata?.pipelineExecutionId && !String(operation.metadata.pipelineExecutionId).toLowerCase().includes(pipelineExecutionIdFilter.toLowerCase())) return false;
      
      // Agent invocation ID filter
      if (agentInvocationIdFilter && operation?.metadata?.agentInvocationId && !String(operation.metadata.agentInvocationId).toLowerCase().includes(agentInvocationIdFilter.toLowerCase())) return false;
      
      return true;
    });
  }, [unifiedJobs, selectedPlane, selectedStatus, tenantFilter, correlationIdFilter, mediaJobIdFilter, pipelineExecutionIdFilter, agentInvocationIdFilter, operationTimeline]);

  const pendingJobs = filteredJobs.filter((job) => job.status === "pending");
  const completedJobs = filteredJobs.filter((job) => job.status !== "pending");

  return (
    <section
      className="space-y-6"
      aria-label="Operations job center"
      data-testid="operations-job-center"
    >
      <header className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className={textStyles.h1}>Operations Job Center</h1>
            <p className={textStyles.muted}>
              Connector syncs, media processing, pipeline executions, agent
              runs, pattern runs, and background tasks.
            </p>
            {operationTimeline ? (
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Operation storage: {operationTimeline.storageMode}
              </p>
            ) : null}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {/* Pass 9: Plane filter */}
            <div className="flex items-center gap-2">
              <Filter className="h-4 w-4 text-gray-500" />
              <select
                value={selectedPlane}
                onChange={(e) => setSelectedPlane(e.target.value as PlaneType)}
                className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
                aria-label="Filter by plane"
              >
                {PLANE_FILTERS.map((filter) => (
                  <option key={filter.value} value={filter.value}>
                    {filter.label}
                  </option>
                ))}
              </select>
            </div>
            {/* Pass 9: Status filter */}
            <select
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value as StatusType)}
              className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
              aria-label="Filter by status"
            >
              {STATUS_FILTERS.map((filter) => (
                <option key={filter.value} value={filter.value}>
                  {filter.label}
                </option>
              ))}
            </select>
            {/* Pass 9: Tenant filter */}
            <input
              type="text"
              placeholder="Tenant ID"
              value={tenantFilter}
              onChange={(e) => setTenantFilter(e.target.value)}
              className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
              aria-label="Filter by tenant ID"
            />
            {/* Pass 9: Correlation ID filter */}
            <input
              type="text"
              placeholder="Correlation ID"
              value={correlationIdFilter}
              onChange={(e) => setCorrelationIdFilter(e.target.value)}
              className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
              aria-label="Filter by correlation ID"
            />
            {/* Pass 9: Media job ID filter */}
            <input
              type="text"
              placeholder="Media Job ID"
              value={mediaJobIdFilter}
              onChange={(e) => setMediaJobIdFilter(e.target.value)}
              className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
              aria-label="Filter by media job ID"
            />
            {/* Pass 9: Pipeline execution ID filter */}
            <input
              type="text"
              placeholder="Pipeline Execution ID"
              value={pipelineExecutionIdFilter}
              onChange={(e) => setPipelineExecutionIdFilter(e.target.value)}
              className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
              aria-label="Filter by pipeline execution ID"
            />
            {/* Pass 9: Agent invocation ID filter */}
            <input
              type="text"
              placeholder="Agent Invocation ID"
              value={agentInvocationIdFilter}
              onChange={(e) => setAgentInvocationIdFilter(e.target.value)}
              className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
              aria-label="Filter by agent invocation ID"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={dismissAllCompleted}
              disabled={completedJobs.length === 0}
            >
              Dismiss Completed
            </Button>
            <Link
              to="/operations"
              className="text-sm text-blue-600 hover:underline dark:text-blue-400"
            >
              Back to Operations Console
            </Link>
          </div>
        </div>
      </header>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className={cn(cardStyles.base, cardStyles.padded)}>
          <p className={textStyles.label}>Pending</p>
          <p className={textStyles.h2}>{pendingJobs.length}</p>
        </div>
        <div className={cn(cardStyles.base, cardStyles.padded)}>
          <p className={textStyles.label}>Completed</p>
          <p className={textStyles.h2}>{completedJobs.length}</p>
        </div>
        <div className={cn(cardStyles.base, cardStyles.padded)}>
          <p className={textStyles.label}>Failures</p>
          <p className={textStyles.h2}>
            {unifiedJobs.filter((job) => job.status === "failure").length}
          </p>
        </div>
      </div>

      <div className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className={textStyles.h3}>Job Timeline</h2>
          <span className="text-xs text-gray-500 dark:text-gray-400">
            {selectedPlane === "all"
              ? "Ordered by creation time"
              : `Filtered by: ${PLANE_FILTERS.find((f) => f.value === selectedPlane)?.label}`}
          </span>
        </div>

        {isLoading ? (
          <div className="rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <RefreshCw className="mx-auto mb-2 h-5 w-5 animate-spin" />
            Loading operation timeline.
          </div>
        ) : filteredJobs.length === 0 ? (
          <div className="rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <Clock3 className="mx-auto mb-2 h-5 w-5" />
            {selectedPlane === "all"
              ? "No operation jobs recorded yet."
              : `No jobs found for ${PLANE_FILTERS.find((f) => f.value === selectedPlane)?.label}.`}
          </div>
        ) : (
          <div className="space-y-2">
            {filteredJobs.map((job) => (
              <div
                key={job.id}
                className="flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 px-3 py-2 dark:border-gray-700"
              >
                <StatusIcon status={job.status} />
                <div className="min-w-52 flex-1">
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {job.name}
                  </p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    Started {new Date(job.startedAt).toLocaleString()}
                    {job.completedAt
                      ? ` • Completed ${new Date(job.completedAt).toLocaleString()}`
                      : ""}
                  </p>
                  {job.detail ? (
                    <p className="text-xs text-gray-600 dark:text-gray-300">
                      {job.detail}
                    </p>
                  ) : null}
                </div>
                <span
                  className={cn(
                    "text-xs font-semibold uppercase tracking-wide",
                    statusTone(job.status),
                  )}
                >
                  {job.status}
                </span>
                {job.status !== "pending" ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => dismissJob(job.id)}
                  >
                    Dismiss
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

export default OperationsJobCenterPage;
