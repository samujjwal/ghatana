/**
 * ActiveOperationsBar
 *
 * A persistent floating bar that surfaces in-flight and recently completed
 * background mutations. Displayed at the bottom of the screen, only rendered
 * when there is at least one job to show.
 *
 * @doc.type component
 * @doc.purpose Persistent background job visibility indicator
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import { IconButton } from "@ghatana/design-system";
import { CheckCircle2, Loader2, X, XCircle } from "lucide-react";
import React from "react";
import {
  useOperationTimeline,
  type OperationJob,
} from "../../api/operations.service";
import {
  useOperations,
  type BackgroundJob,
} from "../../contexts/OperationsContext";
import { cn } from "../../lib/theme";

function JobStatusIcon({ status }: { status: BackgroundJob["status"] }) {
  if (status === "success") {
    return <CheckCircle2 className="h-4 w-4 text-green-500 shrink-0" />;
  }
  if (status === "failure") {
    return <XCircle className="h-4 w-4 text-red-500 shrink-0" />;
  }
  return <Loader2 className="h-4 w-4 text-blue-500 shrink-0 animate-spin" />;
}

function mapOperationStatus(status: OperationJob["status"]): BackgroundJob["status"] {
  if (status === "SUCCEEDED" || status === "CANCELLED") return "success";
  if (status === "FAILED" || status === "BLOCKED") return "failure";
  return "pending";
}

function operationToJob(operation: OperationJob): BackgroundJob {
  return {
    id: operation.operationId,
    name: operation.action,
    status: mapOperationStatus(operation.status),
    startedAt: operation.createdAt,
    completedAt: operation.completedAt || undefined,
    detail: operation.summary || operation.detail || operation.resourceId,
  };
}

function JobRow({
  job,
  onDismiss,
}: {
  job: BackgroundJob;
  onDismiss: (id: string) => void;
}) {
  const isCompleted = job.status !== "pending";

  return (
    <div
      className={cn(
        "flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm",
        job.status === "failure" && "bg-red-50 dark:bg-red-950/30",
        job.status === "success" && "bg-green-50 dark:bg-green-950/30",
        job.status === "pending" && "bg-blue-50 dark:bg-blue-950/30",
      )}
      data-testid="active-operations-job"
    >
      <JobStatusIcon status={job.status} />
      <span className="flex-1 text-gray-800 dark:text-gray-200 truncate max-w-xs">
        {job.name}
      </span>
      {job.detail && (
        <span className="text-xs text-gray-500 truncate max-w-[10rem]">
          {job.detail}
        </span>
      )}
      {isCompleted && (
        <IconButton
          icon={<X className="h-3 w-3" />}
          label={`Dismiss ${job.name}`}
          variant="ghost"
          tone="neutral"
          size="sm"
          onClick={() => onDismiss(job.id)}
          edge="end"
        />
      )}
    </div>
  );
}

/**
 * Floating status bar. Mount once in DefaultLayout.
 * Only renders when at least one job exists.
 */
export function ActiveOperationsBar(): React.ReactElement | null {
  const { jobs, dismissJob, dismissAllCompleted } = useOperations();
  const { data: operationTimeline } = useOperationTimeline(20);
  const serverJobs = React.useMemo(
    () => operationTimeline?.items.map(operationToJob) ?? [],
    [operationTimeline],
  );
  const visibleJobs = React.useMemo(() => {
    const byId = new Map<string, BackgroundJob>();
    for (const job of serverJobs) byId.set(job.id, job);
    for (const job of jobs) byId.set(job.id, job);
    return Array.from(byId.values());
  }, [jobs, serverJobs]);

  if (visibleJobs.length === 0) return null;

  const hasCompleted = jobs.some((j) => j.status !== "pending");
  const pendingCount = visibleJobs.filter((j) => j.status === "pending").length;
  const failedCount = visibleJobs.filter((j) => j.status === "failure").length;

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label="Background operations status"
      data-testid="active-operations-bar"
      className={cn(
        "fixed bottom-4 right-4 z-50 w-80 flex flex-col gap-1",
        "bg-white dark:bg-gray-900 border rounded-xl shadow-lg p-3",
        failedCount > 0
          ? "border-red-200 dark:border-red-800"
          : "border-gray-200 dark:border-gray-700",
      )}
    >
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          {pendingCount > 0
            ? `${pendingCount} operation${pendingCount > 1 ? "s" : ""} running`
            : "Operations"}
        </span>
        {hasCompleted && (
          <button
            className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 underline"
            onClick={dismissAllCompleted}
          >
            Dismiss all
          </button>
        )}
      </div>
      {visibleJobs.map((job) => (
        <JobRow key={job.id} job={job} onDismiss={dismissJob} />
      ))}
    </div>
  );
}
