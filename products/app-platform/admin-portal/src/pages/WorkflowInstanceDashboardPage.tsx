/**
 * STORY-W01-008: Workflow Instance Dashboard
 * List workflow instances with filters (workflow, status, date).
 * Instance detail: step-by-step execution timeline with duration, input/output per step.
 * RUNNING instances show real-time current step. Visual diagram with step highlighting.
 * Manual intervention: send signal, cancel instance, retry failed step.
 */

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

type InstanceStatus = "PENDING" | "RUNNING" | "WAITING" | "COMPLETED" | "FAILED" | "CANCELLED";

interface WorkflowInstance {
  instanceId: string;
  workflowId: string;
  workflowName: string;
  version: string;
  status: InstanceStatus;
  currentStep?: string;
  context: Record<string, unknown>;
  startedAt: string;
  completedAt?: string;
  durationMs?: number;
  triggeredBy: string;
}

interface StepExecution {
  stepId: string;
  stepName: string;
  stepType: "TASK" | "DECISION" | "PARALLEL" | "WAIT" | "SUB_WORKFLOW";
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "SKIPPED";
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  input?: Record<string, unknown>;
  output?: Record<string, unknown>;
  errorMessage?: string;
}

interface WorkflowDefinitionSummary {
  workflowId: string;
  name: string;
}

// ── API ───────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20;

const api = {
  listInstances: async (params: {
    workflowId?: string;
    status?: string;
    from?: string;
    to?: string;
    page: number;
  }): Promise<{ instances: WorkflowInstance[]; total: number }> => {
    const url = new URL("/api/admin/workflow/instances", window.location.origin);
    url.searchParams.set("page", String(params.page));
    url.searchParams.set("size", String(PAGE_SIZE));
    if (params.workflowId) url.searchParams.set("workflowId", params.workflowId);
    if (params.status) url.searchParams.set("status", params.status);
    if (params.from) url.searchParams.set("from", params.from);
    if (params.to) url.searchParams.set("to", params.to);
    const r = await fetch(url.toString());
    if (!r.ok) throw new Error("Failed to fetch instances");
    return r.json();
  },
  getSteps: async (instanceId: string): Promise<StepExecution[]> => {
    const r = await fetch(`/api/admin/workflow/instances/${instanceId}/steps`);
    if (!r.ok) throw new Error("Failed to fetch steps");
    return r.json();
  },
  listWorkflows: async (): Promise<WorkflowDefinitionSummary[]> => {
    const r = await fetch("/api/admin/workflow/definitions");
    if (!r.ok) throw new Error("Failed to fetch workflows");
    return r.json();
  },
  sendSignal: async (instanceId: string, signal: string): Promise<void> => {
    const r = await fetch(`/api/admin/workflow/instances/${instanceId}/signal`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ signal }),
    });
    if (!r.ok) throw new Error("Failed to send signal");
  },
  cancelInstance: async (instanceId: string): Promise<void> => {
    const r = await fetch(`/api/admin/workflow/instances/${instanceId}/cancel`, { method: "POST" });
    if (!r.ok) throw new Error("Failed to cancel instance");
  },
  retryStep: async (instanceId: string, stepId: string): Promise<void> => {
    const r = await fetch(`/api/admin/workflow/instances/${instanceId}/steps/${stepId}/retry`, { method: "POST" });
    if (!r.ok) throw new Error("Failed to retry step");
  },
};

// ── Status colors ─────────────────────────────────────────────────────────────

const STATUS_COLOR: Record<InstanceStatus, string> = {
  PENDING: "bg-gray-100 text-gray-600",
  RUNNING: "bg-blue-100 text-blue-700",
  WAITING: "bg-yellow-100 text-yellow-700",
  COMPLETED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-red-700",
  CANCELLED: "bg-gray-100 text-gray-400",
};

const STEP_STATUS_COLOR: Record<StepExecution["status"], string> = {
  PENDING: "bg-gray-100 text-gray-500",
  RUNNING: "bg-blue-100 text-blue-700 animate-pulse",
  COMPLETED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-red-700",
  SKIPPED: "bg-gray-50 text-gray-400",
};

// ── Instance detail panel ─────────────────────────────────────────────────────

function InstanceDetailPanel({
  instance,
  onClose,
}: {
  instance: WorkflowInstance;
  onClose: () => void;
}) {
  const [signalInput, setSignalInput] = useState("");
  const [expandedStepId, setExpandedStepId] = useState<string | null>(null);
  const qc = useQueryClient();

  const isActive = instance.status === "RUNNING" || instance.status === "WAITING";

  const { data: steps = [] } = useQuery({
    queryKey: ["workflow-steps", instance.instanceId],
    queryFn: () => api.getSteps(instance.instanceId),
    refetchInterval: isActive ? 3000 : false,
  });

  const cancelMut = useMutation({
    mutationFn: () => api.cancelInstance(instance.instanceId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["workflow-instances"] }),
  });

  const signalMut = useMutation({
    mutationFn: () => api.sendSignal(instance.instanceId, signalInput),
    onSuccess: () => { setSignalInput(""); qc.invalidateQueries({ queryKey: ["workflow-steps", instance.instanceId] }); },
  });

  const retryMut = useMutation({
    mutationFn: (stepId: string) => api.retryStep(instance.instanceId, stepId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["workflow-steps", instance.instanceId] }),
  });

  const formatDuration = (ms?: number) =>
    !ms ? "—" : ms < 1000 ? `${ms}ms` : ms < 60000 ? `${(ms / 1000).toFixed(1)}s` : `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;

  return (
    <div className="fixed inset-y-0 right-0 w-[540px] bg-white shadow-2xl overflow-y-auto z-40">
      <div className="sticky top-0 bg-white border-b px-5 py-4 flex items-center justify-between">
        <div>
          <p className="font-semibold">{instance.workflowName} <span className="text-xs text-gray-400">v{instance.version}</span></p>
          <p className="text-xs text-gray-500 font-mono">{instance.instanceId}</p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">✕</button>
      </div>

      <div className="p-5 space-y-5">
        {/* Instance metadata */}
        <div className="grid grid-cols-2 gap-3 text-sm">
          <div><span className="text-gray-500">Status</span>
            <span className={`ml-2 px-2 py-0.5 rounded text-xs font-medium ${STATUS_COLOR[instance.status]}`}>{instance.status}</span>
          </div>
          <div><span className="text-gray-500">Duration</span><span className="ml-2">{formatDuration(instance.durationMs)}</span></div>
          <div><span className="text-gray-500">Triggered by</span><span className="ml-2">{instance.triggeredBy}</span></div>
          <div><span className="text-gray-500">Started</span><span className="ml-2">{new Date(instance.startedAt).toLocaleString()}</span></div>
        </div>

        {/* Manual intervention */}
        {isActive && (
          <div className="border rounded p-3 space-y-2">
            <p className="text-sm font-medium">Manual Intervention</p>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Signal name (e.g. APPROVED)"
                value={signalInput}
                onChange={(e) => setSignalInput(e.target.value)}
                className="border rounded px-2 py-1 text-sm flex-1"
              />
              <button
                onClick={() => signalMut.mutate()}
                disabled={!signalInput.trim() || signalMut.isPending}
                className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
              >
                Send Signal
              </button>
            </div>
            <button
              onClick={() => { if (window.confirm("Cancel this workflow instance?")) cancelMut.mutate(); }}
              disabled={cancelMut.isPending}
              className="text-xs text-red-600 hover:underline"
            >
              Cancel Instance
            </button>
          </div>
        )}

        {/* Step timeline */}
        <div>
          <p className="text-sm font-semibold mb-3">Execution Timeline</p>
          <div className="relative">
            {/* Vertical line */}
            <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-gray-200" />
            <div className="space-y-3">
              {steps.map((step) => (
                <div key={step.stepId} className="relative ml-9 cursor-pointer" onClick={() => setExpandedStepId(expandedStepId === step.stepId ? null : step.stepId)}>
                  {/* Dot */}
                  <div className={`absolute -left-[22px] w-3.5 h-3.5 rounded-full border-2 border-white top-1
                    ${step.status === "COMPLETED" ? "bg-green-500" :
                      step.status === "RUNNING" ? "bg-blue-500 animate-pulse" :
                      step.status === "FAILED" ? "bg-red-500" : "bg-gray-300"}`}
                  />
                  <div className="border rounded p-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium">{step.stepName}</span>
                        <span className="text-xs text-gray-400">[{step.stepType}]</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className={`px-1.5 py-0.5 rounded text-xs ${STEP_STATUS_COLOR[step.status]}`}>{step.status}</span>
                        {step.durationMs && <span className="text-xs text-gray-400">{formatDuration(step.durationMs)}</span>}
                      </div>
                    </div>

                    {step.errorMessage && (
                      <p className="mt-1 text-xs text-red-600">{step.errorMessage}</p>
                    )}

                    {expandedStepId === step.stepId && (
                      <div className="mt-2 space-y-2 text-xs">
                        {step.input && Object.keys(step.input).length > 0 && (
                          <div>
                            <p className="font-medium text-gray-500 mb-1">Input</p>
                            <pre className="bg-gray-50 p-2 rounded overflow-x-auto">{JSON.stringify(step.input, null, 2)}</pre>
                          </div>
                        )}
                        {step.output && Object.keys(step.output).length > 0 && (
                          <div>
                            <p className="font-medium text-gray-500 mb-1">Output</p>
                            <pre className="bg-gray-50 p-2 rounded overflow-x-auto">{JSON.stringify(step.output, null, 2)}</pre>
                          </div>
                        )}
                        {step.status === "FAILED" && (
                          <button
                            onClick={(e) => { e.stopPropagation(); retryMut.mutate(step.stepId); }}
                            disabled={retryMut.isPending}
                            className="text-blue-600 hover:underline"
                          >
                            Retry Step
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function WorkflowInstanceDashboardPage() {
  const [page, setPage] = useState(0);
  const [wfFilter, setWfFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [selected, setSelected] = useState<WorkflowInstance | null>(null);

  const { data: workflowDefs = [] } = useQuery({
    queryKey: ["workflow-definitions"],
    queryFn: api.listWorkflows,
  });

  const { data, isLoading } = useQuery({
    queryKey: ["workflow-instances", wfFilter, statusFilter, fromDate, toDate, page],
    queryFn: () => api.listInstances({
      workflowId: wfFilter || undefined,
      status: statusFilter || undefined,
      from: fromDate || undefined,
      to: toDate || undefined,
      page,
    }),
    placeholderData: (prev) => prev,
    refetchInterval: 10000,
  });

  const instances = data?.instances ?? [];
  const total = data?.total ?? 0;
  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Workflow Instances</h1>
        <p className="text-sm text-gray-500">Monitor, intervene, and manage all workflow executions</p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-4">
        <select
          value={wfFilter}
          onChange={(e) => { setWfFilter(e.target.value); setPage(0); }}
          className="border rounded px-3 py-2 text-sm"
        >
          <option value="">All Workflows</option>
          {workflowDefs.map((w) => <option key={w.workflowId} value={w.workflowId}>{w.name}</option>)}
        </select>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          className="border rounded px-3 py-2 text-sm"
        >
          <option value="">All Statuses</option>
          {["PENDING", "RUNNING", "WAITING", "COMPLETED", "FAILED", "CANCELLED"].map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <input type="date" value={fromDate} onChange={(e) => { setFromDate(e.target.value); setPage(0); }}
          className="border rounded px-3 py-2 text-sm" title="From date" />
        <input type="date" value={toDate} onChange={(e) => { setToDate(e.target.value); setPage(0); }}
          className="border rounded px-3 py-2 text-sm" title="To date" />
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-500">Loading instances…</p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  {["Instance ID", "Workflow", "Version", "Status", "Current Step", "Started", "Duration", "Triggered By"].map((h) => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {instances.map((inst) => (
                  <tr
                    key={inst.instanceId}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => setSelected(inst)}
                  >
                    <td className="px-4 py-3 font-mono text-xs text-blue-600">{inst.instanceId.slice(0, 12)}…</td>
                    <td className="px-4 py-3 font-medium">{inst.workflowName}</td>
                    <td className="px-4 py-3 text-gray-400 text-xs">{inst.version}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${STATUS_COLOR[inst.status]}`}>
                        {inst.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">{inst.currentStep ?? "—"}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">{new Date(inst.startedAt).toLocaleString()}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {inst.durationMs ? (inst.durationMs < 1000 ? `${inst.durationMs}ms` : `${(inst.durationMs / 1000).toFixed(1)}s`) : "—"}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">{inst.triggeredBy}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4 text-sm">
              <span className="text-gray-500">{total.toLocaleString()} total instances</span>
              <div className="flex gap-2">
                <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
                  className="px-3 py-1 border rounded disabled:opacity-40 hover:bg-gray-50">← Prev</button>
                <span className="px-3 py-1">{page + 1} / {totalPages}</span>
                <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page === totalPages - 1}
                  className="px-3 py-1 border rounded disabled:opacity-40 hover:bg-gray-50">Next →</button>
              </div>
            </div>
          )}
        </>
      )}

      {selected && (
        <InstanceDetailPanel instance={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  );
}
