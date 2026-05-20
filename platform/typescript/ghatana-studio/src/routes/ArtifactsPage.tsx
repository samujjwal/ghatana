import { useState, useCallback } from "react";
import type { ReactElement } from "react";
import { useNavigate } from "react-router";
import { Badge } from "@ghatana/design-system";
import type { FidelityReport, ResidualIslandReport } from "@ghatana/artifact-contracts";
import { useStudioLifecycleData } from "../data/StudioLifecycleDataContext";
import type { StudioTranslationKey } from "../i18n/studioTranslations";
import { useStudioTranslation } from "../i18n/studioTranslations";
import {
  describeLifecycleDataStatus,
  formatBytes,
  lifecycleDataBadgeTone,
} from "./studioLifecycleRouteSupport";
import {
  type DecompileJobState,
  fidelityTrafficLight,
  fidelitySummaryText,
} from "../adapters/ArtifactStudioWorkflowAdapter";

type TranslateFn = (key: StudioTranslationKey) => string;

// ============================================================================
// Decompile Jobs Panel
// ============================================================================

interface DecompileJobsPanelProps {
  readonly jobs: readonly DecompileJobState[];
  readonly onViewFidelity: (report: FidelityReport) => void;
  readonly onViewResiduals: (report: ResidualIslandReport) => void;
  readonly onClear: (jobId: string) => void;
}

function DecompileJobsPanel({
  jobs,
  onViewFidelity,
  onViewResiduals,
  onClear,
}: DecompileJobsPanelProps): ReactElement {
  const navigate = useNavigate();

  if (jobs.length === 0) {
    return (
      <article className="studio-card space-y-2">
        <h3 className="text-base font-semibold text-gray-950">
          Decompile Jobs
        </h3>
        <p className="text-sm text-gray-600">
          No decompile jobs yet.{" "}
          <button
            type="button"
            onClick={() => void navigate("/import")}
            className="text-blue-600 underline hover:text-blue-800"
          >
            Import source files
          </button>{" "}
          to start.
        </p>
      </article>
    );
  }

  return (
    <article className="studio-card space-y-3" aria-labelledby="decompile-jobs-title">
      <div className="flex items-center justify-between">
        <h3 id="decompile-jobs-title" className="text-base font-semibold text-gray-950">
          Decompile Jobs ({jobs.length})
        </h3>
        <button
          type="button"
          onClick={() => void navigate("/import")}
          className="text-sm text-blue-600 underline hover:text-blue-800"
        >
          + Import new
        </button>
      </div>
      <ul className="space-y-3" role="list">
        {jobs.map((job) => {
          const light = job.result != null ? fidelityTrafficLight(job.result.fidelityReport) : null;
          const summary = job.result != null ? fidelitySummaryText(job.result.fidelityReport) : null;

          return (
            <li
              key={job.jobId}
              className="rounded-md border border-gray-200 bg-gray-50 p-3 space-y-2"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-medium text-gray-900 truncate">
                  {job.fileName}
                </span>
                <div className="flex items-center gap-2 shrink-0">
                  {job.status === "running" && (
                    <Badge tone="neutral" variant="soft">Running…</Badge>
                  )}
                  {job.status === "complete" && light !== null && (
                    <span className="text-sm" aria-label={`Fidelity: ${summary ?? ""}`}>
                      {light} {summary}
                    </span>
                  )}
                  {job.status === "failed" && (
                    <Badge tone="danger" variant="soft">Error</Badge>
                  )}
                  <button
                    type="button"
                    onClick={() => onClear(job.jobId)}
                    className="text-xs text-gray-400 hover:text-gray-700"
                    aria-label={`Remove job ${job.fileName}`}
                  >
                    ✕
                  </button>
                </div>
              </div>

              {job.status === "failed" && (job.result?.errors?.length ?? 0) > 0 && (
                <p className="text-xs text-red-700">{job.result?.errors?.[0] ?? ""}</p>
              )}

              {job.status === "complete" && job.result != null && (
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      if (job.result?.fidelityReport !== null && job.result?.fidelityReport !== undefined) {
                        onViewFidelity(job.result.fidelityReport);
                      }
                    }}
                    className="text-xs text-blue-600 underline hover:text-blue-800"
                  >
                    View Fidelity Report
                  </button>
                  {job.result.residualIslandReport !== null &&
                    job.result.residualIslandReport.totalCount > 0 && (
                    <button
                      type="button"
                      onClick={() => {
                        if (job.result?.residualIslandReport !== null && job.result?.residualIslandReport !== undefined) {
                          onViewResiduals(job.result.residualIslandReport);
                        }
                      }}
                      className="text-xs text-amber-600 underline hover:text-amber-800"
                    >
                      {job.result.residualIslandReport.totalCount} Residual Islands
                    </button>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </article>
  );
}

// ============================================================================
// Residual Review Queue
// ============================================================================

interface ResidualQueueProps {
  readonly jobs: readonly DecompileJobState[];
}

function ResidualReviewQueue({ jobs }: ResidualQueueProps): ReactElement | null {
  const navigate = useNavigate();
  const islands = jobs.flatMap((job) => {
    if (job.result == null || job.result.residualIslandReport === null) return [];
    return job.result.residualIslandReport.islands.map((island) => ({
      jobId: job.jobId,
      fileName: job.fileName,
      island,
    }));
  });

  if (islands.length === 0) return null;

  return (
    <article className="studio-card space-y-3" aria-labelledby="residual-queue-title">
      <h3 id="residual-queue-title" className="text-base font-semibold text-amber-900">
        ⚠ Residual Review Queue ({islands.length})
      </h3>
      <p className="text-sm text-amber-800">
        These code regions could not be fully modelled. Review and resolve before
        exporting.
      </p>
      <ul className="space-y-2" role="list">
        {islands.map(({ jobId, fileName, island }) => (
          <li
            key={`${jobId}-${island.id}`}
            className="rounded-md border border-amber-200 bg-amber-50 p-3 space-y-1"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-mono text-amber-900 truncate">
                {fileName} / {island.id}
              </span>
              <button
                type="button"
                onClick={() => void navigate("/import")}
                className="text-xs text-blue-600 underline hover:text-blue-800 shrink-0"
              >
                Re-import
              </button>
            </div>
            <p className="text-xs text-amber-800">{island.description}</p>
          </li>
        ))}
      </ul>
    </article>
  );
}

function artifactStatusLabel(found: boolean, t: TranslateFn): string {
  return found
    ? t("studio.route.artifacts.status.found")
    : t("studio.route.artifacts.status.missing");
}

export default function ArtifactsPage(): ReactElement {
  const navigate = useNavigate();
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  const snapshot = lifecycleData.snapshot;
  const artifacts = snapshot.artifactManifest?.artifacts ?? [];
  const artifactManifestStatus =
    snapshot.manifestLoadState.artifactManifest.status;
  const artifactManifestMessage =
    snapshot.manifestLoadState.artifactManifest.message;
  const lifecycleResultRef =
    snapshot.selectedRun?.manifestRefs?.["lifecycle-result"];
  const artifactManifestRef =
    snapshot.selectedRun?.manifestRefs?.["artifact-manifest"];
  const verifyHealthRef = snapshot.selectedRun?.healthSnapshotRef;
  const intelligence = resolveArtifactIntelligence(
    snapshot.productUnit?.metadata,
  );

  // Decompile jobs state — populated when user navigates back from ImportDecompilePage
  const [decompileJobs, setDecompileJobs] = useState<readonly DecompileJobState[]>([]);

  const handleClearJob = useCallback((jobId: string) => {
    setDecompileJobs((prev) => prev.filter((j) => j.jobId !== jobId));
  }, []);

  const handleViewFidelity = useCallback((report: FidelityReport) => {
    void navigate("/fidelity-report", { state: { report } });
  }, [navigate]);

  const handleViewResiduals = useCallback((_report: ResidualIslandReport) => {
    // Navigate to import page which shows residuals inline
    void navigate("/import");
  }, [navigate]);

  return (
    <section className="space-y-6" aria-labelledby="artifacts-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(snapshot.status)}
        </Badge>
        <h2
          id="artifacts-title"
          className="text-2xl font-semibold text-gray-950"
        >
          {t("studio.route.artifacts.title")}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t("studio.route.artifacts.description")}
        </p>
      </div>

      {/* Decompile workflow panels */}
      <DecompileJobsPanel
        jobs={decompileJobs}
        onViewFidelity={handleViewFidelity}
        onViewResiduals={handleViewResiduals}
        onClear={handleClearJob}
      />
      <ResidualReviewQueue jobs={decompileJobs} />

      {artifactManifestStatus === "missing" ||
      artifactManifestStatus === "unavailable" ||
      artifactManifestStatus === "corrupt" ||
      artifactManifestStatus === "unauthorized" ? (
        <article
          className="rounded-md border border-red-200 bg-red-50 p-4"
          aria-label="artifact-manifest-state"
        >
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-red-900">
              {t("studio.route.providerReadiness.blockedTitle")}
            </h3>
            <Badge tone="danger" variant="soft" className="text-xs">
              artifact-manifest: {artifactManifestStatus}
            </Badge>
          </div>
          <p className="mt-2 text-sm text-red-800">
            {t("studio.route.providerReadiness.blockedMessage")}
          </p>
          <div className="mt-3 space-y-1">
            <h4 className="text-xs font-medium text-red-900">
              {t("studio.route.providerReadiness.missingEvidence")}
            </h4>
            <ul className="space-y-1 text-xs text-red-700">
              {lifecycleResultRef === undefined ||
              lifecycleResultRef === "not reported" ? (
                <li className="font-mono">lifecycle-result: not reported</li>
              ) : null}
              {artifactManifestRef === undefined ||
              artifactManifestRef === "not reported" ? (
                <li className="font-mono">artifact-manifest: not reported</li>
              ) : null}
              {verifyHealthRef === undefined ||
              verifyHealthRef === "not reported" ? (
                <li className="font-mono">verify-health: not reported</li>
              ) : null}
            </ul>
          </div>
        </article>
      ) : artifactManifestStatus === "loaded" ? (
        <article
          className="studio-card space-y-2"
          aria-label="artifact-manifest-state"
        >
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-semibold text-gray-950">
              Manifest evidence state
            </h3>
            <Badge tone="success" variant="soft" className="text-xs">
              artifact-manifest: {artifactManifestStatus}
            </Badge>
          </div>
          {artifactManifestMessage !== undefined && (
            <p className="text-xs text-gray-600">{artifactManifestMessage}</p>
          )}
          <ul className="space-y-1 text-xs text-gray-600">
            <li className="font-mono">
              lifecycle-result: {lifecycleResultRef ?? "not reported"}
            </li>
            <li className="font-mono">
              artifact-manifest: {artifactManifestRef ?? "not reported"}
            </li>
            <li className="font-mono">
              verify-health: {verifyHealthRef ?? "not reported"}
            </li>
          </ul>
        </article>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        {artifacts.length === 0 ? (
          <article className="studio-card space-y-2">
            <h3 className="text-base font-semibold text-gray-950">
              {t("studio.route.artifacts.emptyTitle")}
            </h3>
            <p className="text-sm leading-6 text-gray-600">
              {t("studio.route.artifacts.emptyDescription")}
            </p>
          </article>
        ) : null}
        {artifacts.map((artifact) => (
          <article key={artifact.id} className="studio-card space-y-3">
            <div className="flex items-center justify-between gap-3">
              <h3 className="text-base font-semibold text-gray-950">
                {artifact.id}
              </h3>
              <Badge
                tone={artifact.found ? "success" : "danger"}
                variant="soft"
              >
                {artifactStatusLabel(artifact.found, t)}
              </Badge>
            </div>
            <dl className="grid gap-2 text-sm text-gray-600">
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Type</dt>
                <dd>{artifact.metadata.type}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Packaging</dt>
                <dd>{artifact.metadata.packaging}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Fingerprint</dt>
                <dd>{`${artifact.fingerprint.algorithm}:${artifact.fingerprint.hash}`}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="font-medium text-gray-900">Size</dt>
                <dd>{formatBytes(artifact.metadata.sizeBytes)}</dd>
              </div>
            </dl>
          </article>
        ))}
      </div>

      {(intelligence.residualIslands.length > 0 ||
        intelligence.riskHotspots.length > 0 ||
        intelligence.recommendations.length > 0 ||
        intelligence.evidenceRefs.length > 0) && (
        <article
          className="studio-card space-y-3"
          aria-labelledby="artifact-intelligence-title"
        >
          <h3
            id="artifact-intelligence-title"
            className="text-base font-semibold text-gray-950"
          >
            {t("studio.route.artifacts.intelligenceTitle")}
          </h3>
          {intelligence.residualIslands.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">
                {t("studio.route.artifacts.residualIslandsTitle")}
              </h4>
              <ul className="mt-1 space-y-1 text-sm text-gray-600">
                {intelligence.residualIslands.map((island) => (
                  <li key={island}>{island}</li>
                ))}
              </ul>
            </div>
          )}
          {intelligence.riskHotspots.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">
                {t("studio.route.artifacts.riskHotspotsTitle")}
              </h4>
              <ul className="mt-1 space-y-1 text-sm text-gray-600">
                {intelligence.riskHotspots.map((hotspot) => (
                  <li key={hotspot}>{hotspot}</li>
                ))}
              </ul>
            </div>
          )}
          {intelligence.recommendations.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">
                {t("studio.route.artifacts.recommendationsTitle")}
              </h4>
              <ul className="mt-1 space-y-1 text-sm text-gray-600">
                {intelligence.recommendations.map((recommendation) => (
                  <li key={recommendation}>{recommendation}</li>
                ))}
              </ul>
            </div>
          )}
          {intelligence.evidenceRefs.length > 0 && (
            <div>
              <h4 className="text-sm font-medium text-gray-900">
                {t("studio.route.artifacts.evidenceRefsTitle")}
              </h4>
              <ul className="mt-1 space-y-1 text-xs text-gray-600">
                {intelligence.evidenceRefs.map((evidenceRef) => (
                  <li key={evidenceRef} className="font-mono">
                    {evidenceRef}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </article>
      )}
    </section>
  );
}

interface ArtifactIntelligenceView {
  readonly residualIslands: readonly string[];
  readonly riskHotspots: readonly string[];
  readonly recommendations: readonly string[];
  readonly evidenceRefs: readonly string[];
}

function resolveArtifactIntelligence(
  metadata: unknown,
): ArtifactIntelligenceView {
  if (typeof metadata !== "object" || metadata === null) {
    return {
      residualIslands: [],
      riskHotspots: [],
      recommendations: [],
      evidenceRefs: [],
    };
  }

  const record = metadata as {
    readonly artifactIntelligence?: {
      readonly residualIslands?: unknown;
      readonly riskHotspots?: unknown;
      readonly recommendations?: unknown;
      readonly evidenceRefs?: unknown;
    };
    readonly residualIslands?: unknown;
    readonly riskHotspots?: unknown;
    readonly recommendations?: unknown;
    readonly evidenceRefs?: unknown;
  };

  const source = record.artifactIntelligence;
  return {
    residualIslands: coerceStringArray(
      source?.residualIslands ?? record.residualIslands,
    ),
    riskHotspots: coerceStringArray(
      source?.riskHotspots ?? record.riskHotspots,
    ),
    recommendations: coerceStringArray(
      source?.recommendations ?? record.recommendations,
    ),
    evidenceRefs: coerceStringArray(
      source?.evidenceRefs ?? record.evidenceRefs,
    ),
  };
}

function coerceStringArray(value: unknown): readonly string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(
    (entry): entry is string =>
      typeof entry === "string" && entry.trim().length > 0,
  );
}
