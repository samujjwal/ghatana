/**
 * STORY-K13-014: Maker-Checker Task Center
 * Centralized task center for all pending maker-checker approvals across the platform.
 * Tasks from: config changes (K-02), trade approvals (D-01), rebalances (D-03),
 * regulatory reports (D-10), plugin deployments (K-04).
 * Approve/reject with one-click + comment. Priority sorting by SLA deadline and business impact.
 */

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

type TaskEntityType =
  | "CONFIG_CHANGE"
  | "TRADE_APPROVAL"
  | "PORTFOLIO_REBALANCE"
  | "REGULATORY_REPORT"
  | "PLUGIN_DEPLOYMENT"
  | "ROLE_CHANGE"
  | "API_KEY_ROTATION";

type TaskStatus = "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED";
type TaskPriority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

interface MakerCheckerTask {
  taskId: string;
  entityType: TaskEntityType;
  entityId: string;
  summary: string;
  maker: string;
  makerRole: string;
  submittedAt: string;
  slaDeadline: string;
  priority: TaskPriority;
  status: TaskStatus;
  impactScore: number;   // 0–100
  details: Record<string, unknown>;
}

// ── API ───────────────────────────────────────────────────────────────────────

const api = {
  listPendingTasks: async (): Promise<MakerCheckerTask[]> => {
    const r = await fetch("/api/admin/maker-checker/tasks?status=PENDING");
    if (!r.ok) throw new Error("Failed to fetch tasks");
    return r.json();
  },
  approveTask: async (taskId: string, comment: string): Promise<void> => {
    const r = await fetch(`/api/admin/maker-checker/tasks/${taskId}/approve`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ comment }),
    });
    if (!r.ok) throw new Error("Failed to approve task");
  },
  rejectTask: async (taskId: string, reason: string): Promise<void> => {
    const r = await fetch(`/api/admin/maker-checker/tasks/${taskId}/reject`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason }),
    });
    if (!r.ok) throw new Error("Failed to reject task");
  },
};

// ── Labels and colors ─────────────────────────────────────────────────────────

const ENTITY_LABEL: Record<TaskEntityType, string> = {
  CONFIG_CHANGE: "Config Change",
  TRADE_APPROVAL: "Trade Approval",
  PORTFOLIO_REBALANCE: "Rebalance",
  REGULATORY_REPORT: "Reg. Report",
  PLUGIN_DEPLOYMENT: "Plugin Deploy",
  ROLE_CHANGE: "Role Change",
  API_KEY_ROTATION: "API Key",
};

const ENTITY_COLOR: Record<TaskEntityType, string> = {
  CONFIG_CHANGE: "bg-blue-100 text-blue-700",
  TRADE_APPROVAL: "bg-green-100 text-green-700",
  PORTFOLIO_REBALANCE: "bg-teal-100 text-teal-700",
  REGULATORY_REPORT: "bg-orange-100 text-orange-700",
  PLUGIN_DEPLOYMENT: "bg-purple-100 text-purple-700",
  ROLE_CHANGE: "bg-pink-100 text-pink-700",
  API_KEY_ROTATION: "bg-amber-100 text-amber-700",
};

const PRIORITY_COLOR: Record<TaskPriority, string> = {
  CRITICAL: "text-red-700 font-bold",
  HIGH: "text-orange-700 font-semibold",
  MEDIUM: "text-amber-700",
  LOW: "text-gray-500",
};

// ── SLA countdown ─────────────────────────────────────────────────────────────

function SlaCountdown({ deadline }: { deadline: string }) {
  const diff = new Date(deadline).getTime() - Date.now();
  if (diff <= 0) return <span className="text-xs text-red-600 font-semibold">OVERDUE</span>;
  const hours = Math.floor(diff / 3600000);
  const minutes = Math.floor((diff % 3600000) / 60000);
  const urgent = hours < 2;
  return (
    <span className={`text-xs font-medium ${urgent ? "text-red-600" : hours < 24 ? "text-amber-600" : "text-gray-500"}`}>
      {hours > 0 ? `${hours}h ` : ""}{minutes}m remaining
    </span>
  );
}

// ── Action modal ──────────────────────────────────────────────────────────────

function ActionModal({
  task,
  mode,
  onClose,
}: {
  task: MakerCheckerTask;
  mode: "APPROVE" | "REJECT";
  onClose: () => void;
}) {
  const [comment, setComment] = useState("");
  const qc = useQueryClient();

  const mut = useMutation({
    mutationFn: () =>
      mode === "APPROVE"
        ? api.approveTask(task.taskId, comment)
        : api.rejectTask(task.taskId, comment),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["maker-checker-tasks"] });
      onClose();
    },
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg p-6">
        <h2 className="text-lg font-semibold mb-1">
          {mode === "APPROVE" ? "Approve" : "Reject"}: {task.summary}
        </h2>
        <p className="text-sm text-gray-500 mb-4">
          Submitted by {task.maker} · {ENTITY_LABEL[task.entityType]}
        </p>

        {/* Task details */}
        <div className="bg-gray-50 rounded p-3 mb-4 text-xs text-gray-700 space-y-1 max-h-40 overflow-y-auto">
          {Object.entries(task.details).map(([k, v]) => (
            <div key={k} className="flex gap-2">
              <span className="font-medium text-gray-500 w-28 shrink-0">{k}</span>
              <span className="break-words">{String(v)}</span>
            </div>
          ))}
        </div>

        <label className="block text-sm font-medium text-gray-700 mb-1">
          {mode === "APPROVE" ? "Approval Comment (optional)" : "Rejection Reason (required)"}
        </label>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          rows={3}
          placeholder={mode === "APPROVE" ? "Add any approval notes…" : "Explain why this is rejected…"}
          className="w-full border rounded px-3 py-2 text-sm mb-4"
        />

        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 text-sm border rounded text-gray-600 hover:bg-gray-50">
            Cancel
          </button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending || (mode === "REJECT" && !comment.trim())}
            className={`px-4 py-2 text-sm rounded text-white disabled:opacity-50
              ${mode === "APPROVE" ? "bg-green-600 hover:bg-green-700" : "bg-red-600 hover:bg-red-700"}`}
          >
            {mut.isPending ? "Processing…" : mode === "APPROVE" ? "Approve" : "Reject"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function MakerCheckerTaskCenterPage() {
  const [filterType, setFilterType] = useState<"ALL" | TaskEntityType>("ALL");
  const [filterPriority, setFilterPriority] = useState<"ALL" | TaskPriority>("ALL");
  const [actionTarget, setActionTarget] = useState<{ task: MakerCheckerTask; mode: "APPROVE" | "REJECT" } | null>(null);

  const { data: tasks = [], isLoading } = useQuery({
    queryKey: ["maker-checker-tasks"],
    queryFn: api.listPendingTasks,
    refetchInterval: 30000,
  });

  // Sort by: priority desc, then SLA deadline asc
  const priorityOrder: Record<TaskPriority, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
  const sorted = [...tasks].sort((a, b) => {
    const pDiff = priorityOrder[b.priority] - priorityOrder[a.priority];
    if (pDiff !== 0) return pDiff;
    return new Date(a.slaDeadline).getTime() - new Date(b.slaDeadline).getTime();
  });

  const filtered = sorted.filter((t) => {
    if (filterType !== "ALL" && t.entityType !== filterType) return false;
    if (filterPriority !== "ALL" && t.priority !== filterPriority) return false;
    return true;
  });

  const expiringSoon = tasks.filter((t) => {
    const diff = new Date(t.slaDeadline).getTime() - Date.now();
    return diff > 0 && diff < 2 * 3600000; // < 2 hours
  });

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Maker-Checker Task Center</h1>
          <p className="text-sm text-gray-500">
            All pending approvals across configuration, trading, compliance, and platform operations
          </p>
        </div>
        <div className="text-right">
          <p className="text-2xl font-bold text-blue-700">{tasks.length}</p>
          <p className="text-xs text-gray-500">Pending tasks</p>
        </div>
      </div>

      {/* Urgent banner */}
      {expiringSoon.length > 0 && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded text-sm text-red-700">
          ⚠ {expiringSoon.length} task{expiringSoon.length > 1 ? "s" : ""} expire within 2 hours. Review immediately.
        </div>
      )}

      {/* Stats by entity type */}
      <div className="flex flex-wrap gap-2 mb-4">
        {Object.entries(ENTITY_LABEL).map(([type, label]) => {
          const count = tasks.filter((t) => t.entityType === type).length;
          if (count === 0) return null;
          return (
            <button
              key={type}
              onClick={() => setFilterType(type === filterType ? "ALL" : type as TaskEntityType)}
              className={`px-3 py-1.5 rounded text-xs font-medium border
                ${filterType === type ? "ring-2 ring-blue-500" : ""}
                ${ENTITY_COLOR[type as TaskEntityType]}`}
            >
              {label}: {count}
            </button>
          );
        })}
      </div>

      {/* Priority filter */}
      <div className="flex gap-2 mb-6">
        {(["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW"] as const).map((p) => (
          <button
            key={p}
            onClick={() => setFilterPriority(p)}
            className={`px-3 py-1 text-xs rounded border
              ${filterPriority === p ? "bg-gray-800 text-white border-gray-800" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}
          >
            {p}
          </button>
        ))}
      </div>

      {/* Task table */}
      {isLoading ? (
        <p className="text-sm text-gray-500">Loading tasks…</p>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">✓</p>
          <p className="text-sm">No pending tasks match the selected filters.</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {["Priority", "Type", "Summary", "Maker", "Submitted", "SLA Deadline", "Impact", "Actions"].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map((task) => {
                const overdue = new Date(task.slaDeadline) < new Date();
                return (
                  <tr key={task.taskId} className={overdue ? "bg-red-50" : "hover:bg-gray-50"}>
                    <td className={`px-4 py-3 ${PRIORITY_COLOR[task.priority]}`}>{task.priority}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${ENTITY_COLOR[task.entityType]}`}>
                        {ENTITY_LABEL[task.entityType]}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <p className="font-medium">{task.summary}</p>
                      <p className="text-xs text-gray-400">ID: {task.entityId}</p>
                    </td>
                    <td className="px-4 py-3 text-gray-600">{task.maker}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {new Date(task.submittedAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-3">
                      <SlaCountdown deadline={task.slaDeadline} />
                      <p className="text-xs text-gray-400">
                        {new Date(task.slaDeadline).toLocaleString()}
                      </p>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        <div className="h-1.5 w-16 bg-gray-200 rounded-full overflow-hidden">
                          <div
                            className={`h-full rounded-full ${task.impactScore >= 70 ? "bg-red-500" : task.impactScore >= 40 ? "bg-amber-400" : "bg-green-400"}`}
                            style={{ width: `${task.impactScore}%` }}
                          />
                        </div>
                        <span className="text-xs text-gray-500">{task.impactScore}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2">
                        <button
                          onClick={() => setActionTarget({ task, mode: "APPROVE" })}
                          className="px-2.5 py-1 text-xs bg-green-600 text-white rounded hover:bg-green-700"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => setActionTarget({ task, mode: "REJECT" })}
                          className="px-2.5 py-1 text-xs border border-red-300 text-red-600 rounded hover:bg-red-50"
                        >
                          Reject
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {actionTarget && (
        <ActionModal
          task={actionTarget.task}
          mode={actionTarget.mode}
          onClose={() => setActionTarget(null)}
        />
      )}
    </div>
  );
}
