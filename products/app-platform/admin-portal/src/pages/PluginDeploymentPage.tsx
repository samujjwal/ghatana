/**
 * STORY-K13-008: Plugin Deployment and Certification UI
 * Wizard: upload → validate manifest → sandbox tests → security scan → certification → deploy
 * Supports rollback, version comparison diff, certification review workflow (T3 plugins).
 */

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

type WizardStep =
  | "UPLOAD"
  | "VALIDATE"
  | "SANDBOX"
  | "SECURITY"
  | "CERTIFICATION"
  | "DEPLOY";

const WIZARD_STEPS: WizardStep[] = [
  "UPLOAD",
  "VALIDATE",
  "SANDBOX",
  "SECURITY",
  "CERTIFICATION",
  "DEPLOY",
];

type StepStatus = "PENDING" | "RUNNING" | "PASS" | "FAIL" | "SKIPPED";

interface DeploymentJob {
  jobId: string;
  pluginId: string;
  pluginName: string;
  version: string;
  tier: "T1" | "T2" | "T3";
  initiatedBy: string;
  startedAt: string;
  steps: Record<WizardStep, { status: StepStatus; details?: string; startedAt?: string; completedAt?: string }>;
  overallStatus: "IN_PROGRESS" | "COMPLETED" | "FAILED" | "AWAITING_REVIEW";
}

interface PluginVersion {
  version: string;
  deployedAt: string;
  status: "ACTIVE" | "DEPRECATED" | "ROLLBACK_AVAILABLE";
  changelog: string;
}

interface CertificationReview {
  reviewId: string;
  pluginId: string;
  reviewer?: string;
  checklist: Array<{ item: string; passed: boolean | null; notes?: string }>;
  status: "PENDING" | "IN_REVIEW" | "APPROVED" | "REJECTED";
  notes?: string;
  submittedAt: string;
}

// ── API helpers ───────────────────────────────────────────────────────────────

const api = {
  listJobs: async (): Promise<DeploymentJob[]> => {
    const r = await fetch("/api/admin/plugins/deployment-jobs");
    if (!r.ok) throw new Error("Failed to fetch jobs");
    return r.json();
  },
  startDeployment: async (form: FormData): Promise<{ jobId: string }> => {
    const r = await fetch("/api/admin/plugins/deploy", { method: "POST", body: form });
    if (!r.ok) throw new Error("Failed to start deployment");
    return r.json();
  },
  getJob: async (jobId: string): Promise<DeploymentJob> => {
    const r = await fetch(`/api/admin/plugins/deployment-jobs/${jobId}`);
    if (!r.ok) throw new Error("Failed to fetch job");
    return r.json();
  },
  listVersions: async (pluginId: string): Promise<PluginVersion[]> => {
    const r = await fetch(`/api/admin/plugins/${pluginId}/versions`);
    if (!r.ok) throw new Error("Failed to fetch versions");
    return r.json();
  },
  rollback: async (pluginId: string, version: string): Promise<void> => {
    const r = await fetch(`/api/admin/plugins/${pluginId}/rollback`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ version }),
    });
    if (!r.ok) throw new Error("Failed to rollback");
  },
  approveCertification: async (reviewId: string, notes: string): Promise<void> => {
    const r = await fetch(`/api/admin/plugins/certification/${reviewId}/approve`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ notes }),
    });
    if (!r.ok) throw new Error("Failed to approve");
  },
  rejectCertification: async (reviewId: string, notes: string): Promise<void> => {
    const r = await fetch(`/api/admin/plugins/certification/${reviewId}/reject`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ notes }),
    });
    if (!r.ok) throw new Error("Failed to reject");
  },
  getPendingReviews: async (): Promise<CertificationReview[]> => {
    const r = await fetch("/api/admin/plugins/certification/pending");
    if (!r.ok) throw new Error("Failed to fetch pending reviews");
    return r.json();
  },
};

// ── Step indicator ────────────────────────────────────────────────────────────

function StepIndicator({ steps, currentStep }: { steps: WizardStep[]; currentStep: WizardStep }) {
  const idx = steps.indexOf(currentStep);
  return (
    <div className="flex items-center gap-2 mb-6">
      {steps.map((step, i) => {
        const done = i < idx;
        const active = i === idx;
        return (
          <React.Fragment key={step}>
            <div
              className={`flex items-center justify-center w-8 h-8 rounded-full text-xs font-semibold
                ${done ? "bg-green-500 text-white" : active ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-500"}`}
            >
              {done ? "✓" : i + 1}
            </div>
            <span className={`text-xs ${active ? "font-semibold text-blue-700" : "text-gray-500"}`}>
              {step}
            </span>
            {i < steps.length - 1 && <div className="flex-1 h-px bg-gray-300" />}
          </React.Fragment>
        );
      })}
    </div>
  );
}

// ── Step status badge ─────────────────────────────────────────────────────────

function StepBadge({ status }: { status: StepStatus }) {
  const map: Record<StepStatus, string> = {
    PENDING: "bg-gray-100 text-gray-600",
    RUNNING: "bg-blue-100 text-blue-700 animate-pulse",
    PASS: "bg-green-100 text-green-700",
    FAIL: "bg-red-100 text-red-700",
    SKIPPED: "bg-yellow-100 text-yellow-700",
  };
  return (
    <span className={`px-2 py-0.5 rounded text-xs font-medium ${map[status]}`}>
      {status}
    </span>
  );
}

// ── New deployment wizard ─────────────────────────────────────────────────────

function NewDeploymentWizard({ onClose, onStarted }: { onClose: () => void; onStarted: (jobId: string) => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [tier, setTier] = useState<"T1" | "T2" | "T3">("T1");
  const [error, setError] = useState<string | null>(null);
  const qc = useQueryClient();

  const startMut = useMutation({
    mutationFn: (form: FormData) => api.startDeployment(form),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["deployment-jobs"] });
      onStarted(data.jobId);
    },
    onError: (e: Error) => setError(e.message),
  });

  const handleSubmit = () => {
    if (!file) { setError("Please select a plugin artifact (.jar or .zip)"); return; }
    const form = new FormData();
    form.append("artifact", file);
    form.append("tier", tier);
    startMut.mutate(form);
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg p-6">
        <h2 className="text-lg font-semibold mb-4">Deploy New Plugin Version</h2>
        {error && <div className="mb-3 p-2 bg-red-50 text-red-700 text-sm rounded">{error}</div>}

        <label className="block text-sm font-medium text-gray-700 mb-1">Plugin Artifact (.jar / .zip)</label>
        <input
          type="file"
          accept=".jar,.zip"
          onChange={(e) => { setFile(e.target.files?.[0] ?? null); setError(null); }}
          className="block w-full text-sm text-gray-600 mb-4 border rounded p-1"
        />

        <label className="block text-sm font-medium text-gray-700 mb-1">Plugin Tier</label>
        <select
          value={tier}
          onChange={(e) => setTier(e.target.value as "T1" | "T2" | "T3")}
          className="w-full border rounded px-3 py-2 text-sm mb-4"
        >
          <option value="T1">T1 — No network / no filesystem</option>
          <option value="T2">T2 — Sandboxed rules, pre-approved data access</option>
          <option value="T3">T3 — Configurable sandbox, security review required</option>
        </select>

        <p className="text-xs text-gray-500 mb-4">
          The wizard will automatically run manifest validation, sandbox tests, security scan, and
          (for T3) a certification review before deployment.
        </p>

        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 text-sm border rounded text-gray-600 hover:bg-gray-50">
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={startMut.isPending}
            className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {startMut.isPending ? "Starting…" : "Start Deployment"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Job detail panel ──────────────────────────────────────────────────────────

function JobDetailPanel({ jobId, onClose }: { jobId: string; onClose: () => void }) {
  const { data: job } = useQuery({
    queryKey: ["deployment-job", jobId],
    queryFn: () => api.getJob(jobId),
    refetchInterval: (q) =>
      q.state.data?.overallStatus === "IN_PROGRESS" ? 3000 : false,
  });

  if (!job) return null;

  const activeStep = (Object.entries(job.steps).find(
    ([, v]) => v.status === "RUNNING"
  )?.[0] ?? "UPLOAD") as WizardStep;

  return (
    <div className="fixed inset-y-0 right-0 w-[480px] bg-white shadow-2xl overflow-y-auto z-40">
      <div className="sticky top-0 bg-white border-b px-5 py-4 flex items-center justify-between">
        <div>
          <p className="font-semibold">{job.pluginName} v{job.version}</p>
          <p className="text-xs text-gray-500">Job {job.jobId} · Tier {job.tier}</p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">✕</button>
      </div>

      <div className="p-5">
        <StepIndicator steps={WIZARD_STEPS} currentStep={activeStep} />

        <div className="space-y-3">
          {WIZARD_STEPS.map((step) => {
            const s = job.steps[step];
            return (
              <div key={step} className="border rounded p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">{step}</span>
                  <StepBadge status={s?.status ?? "PENDING"} />
                </div>
                {s?.details && <p className="mt-1 text-xs text-gray-600">{s.details}</p>}
                {s?.completedAt && (
                  <p className="text-xs text-gray-400 mt-1">
                    Completed {new Date(s.completedAt).toLocaleString()}
                  </p>
                )}
              </div>
            );
          })}
        </div>

        {job.overallStatus === "FAILED" && (
          <div className="mt-4 p-3 bg-red-50 rounded text-sm text-red-700">
            Deployment failed. Fix the issues above and re-deploy.
          </div>
        )}
        {job.overallStatus === "AWAITING_REVIEW" && (
          <div className="mt-4 p-3 bg-yellow-50 rounded text-sm text-yellow-700">
            T3 certification review required — awaiting reviewer sign-off.
          </div>
        )}
        {job.overallStatus === "COMPLETED" && (
          <div className="mt-4 p-3 bg-green-50 rounded text-sm text-green-700">
            Plugin deployed successfully!
          </div>
        )}
      </div>
    </div>
  );
}

// ── Version diff modal ────────────────────────────────────────────────────────

function VersionDiffModal({
  pluginId,
  onClose,
}: {
  pluginId: string;
  onClose: () => void;
}) {
  const { data: versions = [] } = useQuery({
    queryKey: ["plugin-versions", pluginId],
    queryFn: () => api.listVersions(pluginId),
  });
  const [fromVer, setFromVer] = useState("");
  const [toVer, setToVer] = useState("");
  const qc = useQueryClient();

  const rollbackMut = useMutation({
    mutationFn: ({ ver }: { ver: string }) => api.rollback(pluginId, ver),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["deployment-jobs"] });
      onClose();
    },
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">Version Management</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>

        <div className="overflow-x-auto mb-4">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {["Version", "Deployed At", "Status", "Actions"].map((h) => (
                  <th key={h} className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y">
              {versions.map((v) => (
                <tr key={v.version} className="hover:bg-gray-50">
                  <td className="px-3 py-2 font-mono">{v.version}</td>
                  <td className="px-3 py-2 text-gray-500">{new Date(v.deployedAt).toLocaleDateString()}</td>
                  <td className="px-3 py-2">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium
                      ${v.status === "ACTIVE" ? "bg-green-100 text-green-700" :
                        v.status === "DEPRECATED" ? "bg-gray-100 text-gray-500" :
                        "bg-yellow-100 text-yellow-700"}`}>
                      {v.status}
                    </span>
                  </td>
                  <td className="px-3 py-2">
                    {v.status === "ROLLBACK_AVAILABLE" && (
                      <button
                        onClick={() => {
                          if (window.confirm(`Rollback to ${v.version}?`)) {
                            rollbackMut.mutate({ ver: v.version });
                          }
                        }}
                        className="text-xs text-red-600 hover:underline"
                      >
                        Rollback
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="border-t pt-4">
          <h3 className="text-sm font-medium mb-2">Version Diff</h3>
          <div className="flex gap-2 items-center">
            <select
              value={fromVer}
              onChange={(e) => setFromVer(e.target.value)}
              className="border rounded px-2 py-1 text-sm"
            >
              <option value="">From version…</option>
              {versions.map((v) => <option key={v.version} value={v.version}>{v.version}</option>)}
            </select>
            <span className="text-gray-400">→</span>
            <select
              value={toVer}
              onChange={(e) => setToVer(e.target.value)}
              className="border rounded px-2 py-1 text-sm"
            >
              <option value="">To version…</option>
              {versions.map((v) => <option key={v.version} value={v.version}>{v.version}</option>)}
            </select>
            <button
              disabled={!fromVer || !toVer || fromVer === toVer}
              onClick={() => window.open(`/api/admin/plugins/${pluginId}/diff?from=${fromVer}&to=${toVer}`, "_blank")}
              className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            >
              View Diff
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Certification review panel ────────────────────────────────────────────────

function CertificationReviewPanel() {
  const { data: reviews = [] } = useQuery({
    queryKey: ["cert-reviews"],
    queryFn: api.getPendingReviews,
    refetchInterval: 15000,
  });
  const [selected, setSelected] = useState<CertificationReview | null>(null);
  const [notes, setNotes] = useState("");
  const qc = useQueryClient();

  const approveMut = useMutation({
    mutationFn: ({ id, n }: { id: string; n: string }) => api.approveCertification(id, n),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cert-reviews"] });
      setSelected(null);
      setNotes("");
    },
  });
  const rejectMut = useMutation({
    mutationFn: ({ id, n }: { id: string; n: string }) => api.rejectCertification(id, n),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cert-reviews"] });
      setSelected(null);
      setNotes("");
    },
  });

  if (reviews.length === 0) {
    return <p className="text-sm text-gray-500 py-4">No pending T3 certification reviews.</p>;
  }

  return (
    <div>
      <div className="space-y-2">
        {reviews.map((r) => (
          <div key={r.reviewId} className="border rounded p-3 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">Plugin {r.pluginId}</p>
              <p className="text-xs text-gray-500">Submitted {new Date(r.submittedAt).toLocaleDateString()}</p>
            </div>
            <button
              onClick={() => { setSelected(r); setNotes(""); }}
              className="text-sm text-blue-600 hover:underline"
            >
              Review
            </button>
          </div>
        ))}
      </div>

      {selected && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-xl p-6 overflow-y-auto max-h-[90vh]">
            <h2 className="text-lg font-semibold mb-4">Certification Review — {selected.pluginId}</h2>

            <div className="space-y-2 mb-4">
              {selected.checklist.map((item, i) => (
                <div key={i} className="flex items-start gap-2 text-sm">
                  <span className={item.passed === true ? "text-green-500" : item.passed === false ? "text-red-500" : "text-gray-400"}>
                    {item.passed === true ? "✓" : item.passed === false ? "✗" : "○"}
                  </span>
                  <span>{item.item}</span>
                  {item.notes && <span className="text-gray-500 text-xs ml-auto">{item.notes}</span>}
                </div>
              ))}
            </div>

            <label className="block text-sm font-medium text-gray-700 mb-1">Decision Notes</label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
              className="w-full border rounded px-3 py-2 text-sm mb-4"
              placeholder="Enter review notes…"
            />

            <div className="flex justify-end gap-2">
              <button onClick={() => setSelected(null)} className="px-4 py-2 text-sm border rounded text-gray-600 hover:bg-gray-50">
                Cancel
              </button>
              <button
                onClick={() => rejectMut.mutate({ id: selected.reviewId, n: notes })}
                disabled={rejectMut.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                Reject
              </button>
              <button
                onClick={() => approveMut.mutate({ id: selected.reviewId, n: notes })}
                disabled={approveMut.isPending}
                className="px-4 py-2 text-sm bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50"
              >
                Approve
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

type TabId = "JOBS" | "CERTIFICATION";

export default function PluginDeploymentPage() {
  const [tab, setTab] = useState<TabId>("JOBS");
  const [showWizard, setShowWizard] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [diffPluginId, setDiffPluginId] = useState<string | null>(null);

  const { data: jobs = [], isLoading } = useQuery({
    queryKey: ["deployment-jobs"],
    queryFn: api.listJobs,
    refetchInterval: 10000,
  });

  const statusColor: Record<DeploymentJob["overallStatus"], string> = {
    IN_PROGRESS: "bg-blue-100 text-blue-700",
    COMPLETED: "bg-green-100 text-green-700",
    FAILED: "bg-red-100 text-red-700",
    AWAITING_REVIEW: "bg-yellow-100 text-yellow-700",
  };

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Plugin Deployment</h1>
          <p className="text-sm text-gray-500">Deploy, certify, and rollback plugin versions</p>
        </div>
        <button
          onClick={() => setShowWizard(true)}
          className="px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700"
        >
          + Deploy New Version
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-4 border-b mb-4">
        {(["JOBS", "CERTIFICATION"] as TabId[]).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`pb-2 text-sm font-medium border-b-2 transition-colors
              ${tab === t ? "border-blue-600 text-blue-700" : "border-transparent text-gray-500 hover:text-gray-700"}`}
          >
            {t === "JOBS" ? "Deployment Jobs" : "Certification Reviews"}
          </button>
        ))}
      </div>

      {tab === "JOBS" && (
        <>
          {isLoading ? (
            <p className="text-sm text-gray-500">Loading…</p>
          ) : jobs.length === 0 ? (
            <p className="text-sm text-gray-500">No deployment jobs yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50">
                  <tr>
                    {["Plugin", "Version", "Tier", "Initiated By", "Started", "Status", "Actions"].map((h) => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {jobs.map((job) => (
                    <tr key={job.jobId} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium">{job.pluginName}</td>
                      <td className="px-4 py-3 font-mono text-xs">{job.version}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 rounded text-xs font-medium
                          ${job.tier === "T1" ? "bg-blue-100 text-blue-700" :
                            job.tier === "T2" ? "bg-teal-100 text-teal-700" : "bg-purple-100 text-purple-700"}`}>
                          {job.tier}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-500">{job.initiatedBy}</td>
                      <td className="px-4 py-3 text-gray-500">{new Date(job.startedAt).toLocaleString()}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 rounded text-xs font-medium ${statusColor[job.overallStatus]}`}>
                          {job.overallStatus.replace("_", " ")}
                        </span>
                      </td>
                      <td className="px-4 py-3 flex gap-2">
                        <button
                          onClick={() => setSelectedJobId(job.jobId)}
                          className="text-xs text-blue-600 hover:underline"
                        >
                          Details
                        </button>
                        <button
                          onClick={() => setDiffPluginId(job.pluginId)}
                          className="text-xs text-gray-500 hover:underline"
                        >
                          Versions
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {tab === "CERTIFICATION" && <CertificationReviewPanel />}

      {showWizard && (
        <NewDeploymentWizard
          onClose={() => setShowWizard(false)}
          onStarted={(jobId) => {
            setShowWizard(false);
            setSelectedJobId(jobId);
          }}
        />
      )}
      {selectedJobId && (
        <JobDetailPanel jobId={selectedJobId} onClose={() => setSelectedJobId(null)} />
      )}
      {diffPluginId && (
        <VersionDiffModal pluginId={diffPluginId} onClose={() => setDiffPluginId(null)} />
      )}
    </div>
  );
}
