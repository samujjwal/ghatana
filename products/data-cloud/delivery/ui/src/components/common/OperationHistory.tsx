/**
 * OperationHistory Component
 *
 * Renders a compact timeline of client-side operation records produced by
 * useOperationHistory. Intended for embedding in page sidebars or panels
 * where users need visibility into what mutations they have performed.
 *
 * @doc.type component
 * @doc.purpose Audit trail / operation history visibility
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import { Button } from "@ghatana/design-system";
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Trash2,
  XCircle,
} from "lucide-react";
import React from "react";
import type { OperationRecord } from "../../hooks/useOperationHistory";
import { cn, textStyles } from "../../lib/theme";

interface OperationHistoryProps {
  records: OperationRecord[];
  onClear?: () => void;
  maxVisible?: number;
  className?: string;
}

function OutcomeIcon({ outcome }: { outcome: OperationRecord["outcome"] }) {
  if (outcome === "success") {
    return <CheckCircle2 className="h-4 w-4 text-green-500" />;
  }
  if (outcome === "failure") {
    return <XCircle className="h-4 w-4 text-red-500" />;
  }
  return <Clock className="h-4 w-4 text-amber-500 animate-pulse" />;
}

function outcomeLabel(outcome: OperationRecord["outcome"]): string {
  if (outcome === "success") return "Completed";
  if (outcome === "failure") return "Failed";
  return "In progress";
}

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

/**
 * Displays operation records in a compact, scrollable timeline.
 */
export function OperationHistory({
  records,
  onClear,
  maxVisible = 50,
  className,
}: OperationHistoryProps): React.ReactElement {
  const visible = records.slice(0, maxVisible);

  return (
    <div
      className={cn("flex flex-col gap-3", className)}
      data-testid="operation-history"
    >
      <div className="flex items-center justify-between">
        <span
          className={cn(
            textStyles.xs,
            "font-semibold uppercase tracking-wide text-gray-500",
          )}
        >
          Operation History
        </span>
        {onClear && records.length > 0 && (
          <Button
            variant="ghost"
            size="sm"
            tone="neutral"
            leadingIcon={<Trash2 className="h-3 w-3" />}
            onClick={onClear}
          >
            Clear
          </Button>
        )}
      </div>

      {visible.length === 0 ? (
        <p className={cn(textStyles.muted, "py-4 text-center text-xs")}>
          No operations yet in this session.
        </p>
      ) : (
        <ol className="relative border-l border-gray-200 dark:border-gray-700 pl-4 space-y-4">
          {visible.map((record) => (
            <li
              key={record.id}
              className="flex flex-col gap-0.5"
              data-testid="operation-history-entry"
            >
              <div className="absolute -left-2 flex items-center justify-center w-4 h-4 bg-white dark:bg-gray-900 rounded-full">
                <OutcomeIcon outcome={record.outcome} />
              </div>
              <span className="text-xs font-medium text-gray-900 dark:text-gray-100">
                {record.action}
              </span>
              <span className={textStyles.xs}>{record.resource}</span>
              {record.detail && (
                <span className="text-xs text-red-600 dark:text-red-400">
                  {record.detail}
                </span>
              )}
              <div className="flex items-center gap-2 mt-0.5">
                <span
                  className={cn(
                    "text-[10px] font-medium uppercase tracking-wide",
                    record.outcome === "success" &&
                      "text-green-600 dark:text-green-400",
                    record.outcome === "failure" &&
                      "text-red-600 dark:text-red-400",
                    record.outcome === "pending" &&
                      "text-amber-600 dark:text-amber-400",
                  )}
                >
                  {outcomeLabel(record.outcome)}
                </span>
                <span className="text-[10px] text-gray-400">
                  {formatTimestamp(record.timestamp)}
                </span>
                {record.user && (
                  <span className="text-[10px] text-gray-400">
                    by {record.user}
                  </span>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}

      {records.length > maxVisible && (
        <p className={cn(textStyles.xs, "text-center text-gray-400")}>
          Showing {maxVisible} of {records.length} operations
        </p>
      )}
    </div>
  );
}

/**
 * Renders a compact inline alert when there are pending or failed operations.
 */
export function OperationHistoryAlert({
  records,
}: {
  records: OperationRecord[];
}): React.ReactElement | null {
  const failed = records.filter((r) => r.outcome === "failure");
  const pending = records.filter((r) => r.outcome === "pending");

  if (failed.length === 0 && pending.length === 0) return null;

  return (
    <div
      className={cn(
        "flex items-start gap-2 rounded-lg px-3 py-2 text-sm",
        failed.length > 0
          ? "bg-red-50 text-red-800 dark:bg-red-950/30 dark:text-red-200"
          : "bg-amber-50 text-amber-900 dark:bg-amber-950/30 dark:text-amber-200",
      )}
      data-testid="operation-history-alert"
    >
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <span>
        {failed.length > 0
          ? `${failed.length} operation${failed.length > 1 ? "s" : ""} failed. Check Operation History for details.`
          : `${pending.length} operation${pending.length > 1 ? "s" : ""} in progress.`}
      </span>
    </div>
  );
}
