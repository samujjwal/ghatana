/**
 * Autonomy Timeline Component
 *
 * Displays a timeline of autonomy controller actions.
 * Part of Journey 1: The Morning Briefing (Reviewing Autonomy)
 *
 * @doc.type component
 * @doc.purpose Display autonomy action timeline
 * @doc.layer frontend
 */

import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertCircle,
  CheckCircle,
  ChevronRight,
  Clock,
  XCircle,
} from "lucide-react";
import { useState } from "react";
import { brainService, type AutonomyAction } from "../../api/brain.service";
import BaseCard from "../cards/BaseCard";
import StatusBadge from "../common/StatusBadge";

interface AutonomyTimelineProps {
  maxItems?: number;
  showFilters?: boolean;
}

export function AutonomyTimeline({
  maxItems = 10,
  showFilters = true,
}: AutonomyTimelineProps) {
  const [selectedLog, setSelectedLog] = useState<AutonomyAction | null>(null);
  const [filter, setFilter] = useState<string>("all");

  const {
    data: logs,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["autonomy-timeline", maxItems],
    queryFn: () => brainService.getAutonomyTimeline(undefined, maxItems),
    refetchInterval: 30000,
  });

  const getOutcomeIcon = (status: AutonomyAction["status"]) => {
    if (status === "SUCCESS") {
      return <CheckCircle className="h-5 w-5 text-green-500" />;
    }
    if (status === "FAILED") {
      return <XCircle className="h-5 w-5 text-red-500" />;
    }
    if (status === "ADVISORY") {
      return <AlertCircle className="h-5 w-5 text-yellow-500" />;
    }
    return <Clock className="h-5 w-5 text-gray-400" />;
  };

  const getOutcomeColor = (status: AutonomyAction["status"]) => {
    if (status === "SUCCESS") {
      return "bg-green-100 border-green-300";
    }
    if (status === "FAILED") {
      return "bg-red-100 border-red-300";
    }
    if (status === "ADVISORY") {
      return "bg-yellow-100 border-yellow-300";
    }
    return "bg-gray-100 border-gray-300";
  };

  const filteredLogs =
    logs
      ?.filter((log) => {
        if (filter === "all") return true;
        if (filter === "success") return log.status === "SUCCESS";
        if (filter === "advisory") return log.status === "ADVISORY";
        if (filter === "blocked") return log.status === "FAILED";
        return true;
      })
      .slice(0, maxItems) || [];

  if (isLoading) {
    return (
      <BaseCard>
        <div className="animate-pulse">
          <div className="h-8 bg-gray-200 rounded w-1/2 mb-4" />
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-16 bg-gray-200 rounded" />
            ))}
          </div>
        </div>
      </BaseCard>
    );
  }

  if (error) {
    return (
      <BaseCard>
        <div className="text-red-600">
          <XCircle className="h-6 w-6 mb-2" />
          <p>Failed to load autonomy timeline</p>
        </div>
      </BaseCard>
    );
  }

  return (
    <>
      <BaseCard>
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <Activity className="h-6 w-6 text-blue-500" />
            <h2 className="text-xl font-bold text-gray-900">Autonomy Log</h2>
          </div>
          {logs && (
            <span className="text-sm text-gray-500">{logs.length} actions</span>
          )}
        </div>

        {showFilters && (
          <div className="flex gap-2 mb-4">
            <button
              onClick={() => setFilter("all")}
              className={`px-3 py-1 text-sm rounded-full ${
                filter === "all"
                  ? "bg-blue-500 text-white"
                  : "bg-gray-100 text-gray-700 hover:bg-gray-200"
              }`}
            >
              All
            </button>
            <button
              onClick={() => setFilter("success")}
              className={`px-3 py-1 text-sm rounded-full ${
                filter === "success"
                  ? "bg-green-500 text-white"
                  : "bg-gray-100 text-gray-700 hover:bg-gray-200"
              }`}
            >
              Success
            </button>
            <button
              onClick={() => setFilter("advisory")}
              className={`px-3 py-1 text-sm rounded-full ${
                filter === "advisory"
                  ? "bg-yellow-500 text-white"
                  : "bg-gray-100 text-gray-700 hover:bg-gray-200"
              }`}
            >
              Advisory
            </button>
            <button
              onClick={() => setFilter("blocked")}
              className={`px-3 py-1 text-sm rounded-full ${
                filter === "blocked"
                  ? "bg-red-500 text-white"
                  : "bg-gray-100 text-gray-700 hover:bg-gray-200"
              }`}
            >
              Blocked
            </button>
          </div>
        )}

        {filteredLogs.length === 0 ? (
          <div className="text-center py-8 text-gray-500">
            <p>No autonomy actions recorded</p>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredLogs.map((log) => (
              <div
                key={log.id}
                onClick={() => setSelectedLog(log)}
                className={`
                  relative p-3 rounded-lg border cursor-pointer
                  transition-all hover:shadow-md
                  ${getOutcomeColor(log.status)}
                `}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    event.currentTarget.click();
                  }
                }}
                role="button"
                tabIndex={0}
              >
                <div className="flex items-start gap-3">
                  <div className="mt-0.5">{getOutcomeIcon(log.status)}</div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-semibold text-gray-900">
                        {log.action}
                      </span>
                      <StatusBadge status={log.status} />
                      {log.outcome && (
                        <span className="text-xs text-gray-600 px-2 py-0.5 bg-white rounded-full">
                          {log.outcome}
                        </span>
                      )}
                    </div>

                    <p className="text-xs text-gray-700 mb-1">{log.domain}</p>

                    <div className="flex items-center gap-3 mt-2 text-xs text-gray-500">
                      <span>{new Date(log.timestamp).toLocaleString()}</span>
                      <div className="h-3 w-px bg-gray-300" />
                      <span>
                        Confidence: {(log.confidence * 100).toFixed(0)}%
                      </span>
                    </div>
                  </div>

                  <ChevronRight className="h-4 w-4 text-gray-400 mt-1" />
                </div>
              </div>
            ))}
          </div>
        )}
      </BaseCard>

      {selectedLog && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={() => setSelectedLog(null)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              event.currentTarget.click();
            }
          }}
          role="button"
          tabIndex={0}
        >
          <div
            className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 p-6"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                event.currentTarget.click();
              }
            }}
            role="button"
            tabIndex={0}
          >
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                {getOutcomeIcon(selectedLog.status)}
                <h3 className="text-lg font-bold text-gray-900">
                  Decision Context
                </h3>
              </div>
              <button
                onClick={() => setSelectedLog(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                ×
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <div className="text-sm font-medium text-gray-700">Action</div>
                <p className="text-sm text-gray-900 mt-1">
                  {selectedLog.action}
                </p>
              </div>

              <div>
                <div className="text-sm font-medium text-gray-700">Domain</div>
                <p className="text-sm text-gray-900 mt-1">
                  {selectedLog.domain}
                </p>
              </div>

              <div>
                <div className="text-sm font-medium text-gray-700">Status</div>
                <div className="flex items-center gap-2 mt-1">
                  <StatusBadge status={selectedLog.status} />
                  {selectedLog.outcome && (
                    <span className="text-sm text-gray-600">
                      Outcome: {selectedLog.outcome}
                    </span>
                  )}
                </div>
              </div>

              <div>
                <div className="text-sm font-medium text-gray-700">
                  Confidence
                </div>
                <div className="flex items-center gap-2 mt-1">
                  <div className="flex-1 bg-gray-200 rounded-full h-2">
                    <div
                      className="bg-blue-500 h-2 rounded-full"
                      style={{ width: `${selectedLog.confidence * 100}%` }}
                    />
                  </div>
                  <span className="text-sm text-gray-900">
                    {(selectedLog.confidence * 100).toFixed(1)}%
                  </span>
                </div>
              </div>

              {Object.keys(selectedLog.metadata).length > 0 && (
                <div>
                  <div className="text-sm font-medium text-gray-700">
                    Metadata
                  </div>
                  <div className="mt-1 bg-gray-50 rounded p-3 text-xs font-mono">
                    <pre>{JSON.stringify(selectedLog.metadata, null, 2)}</pre>
                  </div>
                </div>
              )}

              <div>
                <div className="text-sm font-medium text-gray-700">
                  Timestamp
                </div>
                <p className="text-sm text-gray-900 mt-1">
                  {new Date(selectedLog.timestamp).toLocaleString()}
                </p>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                onClick={() => setSelectedLog(null)}
                className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default AutonomyTimeline;
