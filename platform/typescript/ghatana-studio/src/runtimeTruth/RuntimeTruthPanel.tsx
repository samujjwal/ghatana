import type { ReactElement } from "react";
import { Badge } from "@ghatana/design-system";
import type { StudioLifecycleSnapshot } from "../data/StudioLifecycleDataContext";
import { useStudioTranslation } from "../i18n/studioTranslations";
import {
  createStudioRuntimeTruthSummary,
  type StudioRuntimeTruthState,
} from "./runtimeTruthSummary";

function stateTone(
  state: StudioRuntimeTruthState,
): "success" | "warning" | "danger" | "neutral" | "info" {
  if (state === "ready") return "success";
  if (state === "loading") return "info";
  if (state === "blocked" || state === "error") return "danger";
  if (state === "degraded" || state === "unavailable" || state === "redacted")
    return "warning";
  return "neutral";
}

export function RuntimeTruthPanel(props: {
  readonly snapshot: StudioLifecycleSnapshot;
}): ReactElement {
  const t = useStudioTranslation();
  const summary = createStudioRuntimeTruthSummary(props.snapshot);
  const primaryGate = summary.gates[0];
  const primaryEvidence = summary.agentEvidence[0];

  return (
    <section className="space-y-3" aria-label="runtime-truth-summary">
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        <article
          className="rounded-md border border-gray-200 bg-white p-3"
          tabIndex={0}
          aria-label="lifecycle-run-detail"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-gray-950">
              {t("studio.runtimeTruth.lifecycleRun")}
            </h4>
            <Badge tone={stateTone(summary.lifecycleRun.state)} variant="soft">
              {summary.lifecycleRun.state}
            </Badge>
          </div>
          <dl className="mt-2 space-y-1 text-xs text-gray-700">
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.status")}</dt>
              <dd>{summary.lifecycleRun.status}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.phase")}</dt>
              <dd>{summary.lifecycleRun.phase}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.correlation")}</dt>
              <dd>{summary.lifecycleRun.correlationId}</dd>
            </div>
          </dl>
        </article>

        <article
          className="rounded-md border border-gray-200 bg-white p-3"
          tabIndex={0}
          aria-label="gate-result-view"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-gray-950">
              {t("studio.runtimeTruth.gateResult")}
            </h4>
            <Badge tone={stateTone(primaryGate.state)} variant="soft">
              {primaryGate.status}
            </Badge>
          </div>
          <dl className="mt-2 space-y-1 text-xs text-gray-700">
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.gate")}</dt>
              <dd>{primaryGate.gateId}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.reason")}</dt>
              <dd>{primaryGate.reason}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.nextAction")}</dt>
              <dd>{primaryGate.nextAction}</dd>
            </div>
          </dl>
        </article>

        <article
          className="rounded-md border border-gray-200 bg-white p-3"
          tabIndex={0}
          aria-label="artifact-manifest-view"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-gray-950">
              {t("studio.runtimeTruth.artifactManifest")}
            </h4>
            <Badge tone={stateTone(summary.artifact.state)} variant="soft">
              {summary.artifact.state}
            </Badge>
          </div>
          <dl className="mt-2 space-y-1 text-xs text-gray-700">
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.manifest")}</dt>
              <dd>{summary.artifact.manifestRef}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.artifacts")}</dt>
              <dd>{summary.artifact.artifactCount}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.digest")}</dt>
              <dd>{summary.artifact.primaryDigest}</dd>
            </div>
          </dl>
        </article>

        <article
          className="rounded-md border border-gray-200 bg-white p-3"
          tabIndex={0}
          aria-label="deployment-manifest-view"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-gray-950">
              {t("studio.runtimeTruth.deploymentManifest")}
            </h4>
            <Badge tone={stateTone(summary.deployment.state)} variant="soft">
              {summary.deployment.state}
            </Badge>
          </div>
          <dl className="mt-2 space-y-1 text-xs text-gray-700">
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.manifest")}</dt>
              <dd>{summary.deployment.manifestRef}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.target")}</dt>
              <dd>{summary.deployment.target}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.artifactDigest")}</dt>
              <dd>{summary.deployment.artifactDigest}</dd>
            </div>
          </dl>
        </article>

        <article
          className="rounded-md border border-gray-200 bg-white p-3"
          tabIndex={0}
          aria-label="health-snapshot-panel"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-gray-950">
              {t("studio.runtimeTruth.healthSnapshot")}
            </h4>
            <Badge tone={stateTone(summary.health.state)} variant="soft">
              {summary.health.status}
            </Badge>
          </div>
          <dl className="mt-2 space-y-1 text-xs text-gray-700">
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.providerMode")}</dt>
              <dd>{summary.health.providerMode}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.checked")}</dt>
              <dd>{summary.health.checkedAt}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.bridge")}</dt>
              <dd>{summary.providerBridgeHealth.status}</dd>
            </div>
          </dl>
        </article>

        <article
          className="rounded-md border border-gray-200 bg-white p-3"
          tabIndex={0}
          aria-label="agent-action-evidence-panel"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-gray-950">
              {t("studio.runtimeTruth.agentEvidence")}
            </h4>
            <Badge tone={stateTone(primaryEvidence.state)} variant="soft">
              {primaryEvidence.redacted ? "redacted" : primaryEvidence.state}
            </Badge>
          </div>
          <dl className="mt-2 space-y-1 text-xs text-gray-700">
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.agent")}</dt>
              <dd>{primaryEvidence.agentId}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.policy")}</dt>
              <dd>{primaryEvidence.policyDecision}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt>{t("studio.runtimeTruth.evidence")}</dt>
              <dd>{primaryEvidence.evidenceRef}</dd>
            </div>
          </dl>
        </article>
      </div>
    </section>
  );
}
