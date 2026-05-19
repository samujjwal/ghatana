import type { ReactElement } from "react";
import { useMemo, useState } from "react";
import { Badge, Select } from "@ghatana/design-system";
import type { ProductUnit } from "@ghatana/kernel-product-contracts";
import { createStudioRuntimeTruthSummary } from "../runtimeTruth";
import { useStudioLifecycleData } from "../data/StudioLifecycleDataContext";
import { useStudioTranslation } from "../i18n/studioTranslations";

const OPENING_PILOTS = [
  {
    id: "digital-marketing",
    name: "Digital Marketing",
    failedGate: "bridge-compliance",
    nextAction: "Inspect bridge, consent, and minimization gate evidence.",
  },
  {
    id: "phr",
    name: "PHR",
    failedGate: "consent",
    nextAction:
      "Inspect healthcare gate evidence for consent, PII, audit, FHIR, and tenant sovereignty.",
  },
] as const;

const PILOT_FILTERS = [
  "all",
  "digital-marketing",
  "phr",
  "blocked",
  "failed",
  "requires-approval",
] as const;

type PilotFilter = (typeof PILOT_FILTERS)[number];

interface PilotViewModel {
  readonly productUnitId: string;
  readonly name: string;
  readonly lifecycleStatus: string;
  readonly runId: string;
  readonly phase: string;
  readonly runStatus: string;
  readonly gateStatus: string;
  readonly gateReason: string;
  readonly artifactRef: string;
  readonly deploymentRef: string;
  readonly healthStatus: string;
  readonly evidenceRefs: readonly string[];
  readonly nextAction: string;
  readonly requiresApproval: boolean;
  readonly isBlocked: boolean;
  readonly isFailed: boolean;
}

export default function OpeningPilotsPage(): ReactElement {
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const [filter, setFilter] = useState<PilotFilter>("all");
  const runtimeTruth = createStudioRuntimeTruthSummary(
    lifecycleData.snapshot,
  );
  const pilots = useMemo(
    () =>
      OPENING_PILOTS.map((pilot): PilotViewModel =>
        createPilotViewModel({
          pilot,
          productUnit: lifecycleData.snapshot.availableProductUnits.find(
            (candidate) => candidate.id === pilot.id,
          ),
          selectedProductUnitId: lifecycleData.selectedProductUnitId,
          snapshotProductUnitId: lifecycleData.snapshot.productUnit?.id,
          runtimeTruth,
          selectedRunStatus: lifecycleData.snapshot.selectedRun?.status,
          pendingApprovalCount: lifecycleData.snapshot.pendingApprovals.filter(
            (approval) => approval.productUnitId === pilot.id,
          ).length,
        }),
      ),
    [
      lifecycleData.selectedProductUnitId,
      lifecycleData.snapshot.availableProductUnits,
      lifecycleData.snapshot.pendingApprovals,
      lifecycleData.snapshot.productUnit?.id,
      lifecycleData.snapshot.selectedRun?.status,
      runtimeTruth,
    ],
  );
  const visiblePilots = pilots.filter((pilot) =>
    pilotMatchesFilter(pilot, filter),
  );

  return (
    <section className="space-y-6" aria-labelledby="opening-pilots-title">
      <div className="space-y-2">
        <Badge tone="info" variant="soft">
          {lifecycleData.selectedProviderMode}
        </Badge>
        <h2
          id="opening-pilots-title"
          className="text-2xl font-semibold text-gray-950"
        >
          {t("studio.route.openingPilots.title")}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t("studio.route.openingPilots.description")}
        </p>
        <p className="text-xs text-gray-500">
          {t("studio.route.openingPilots.bootstrapTruth")}
        </p>
      </div>

      <div className="max-w-xs">
        <label
          className="mb-1 block text-sm font-medium text-gray-900"
          htmlFor="opening-pilot-filter"
        >
          {t("studio.route.openingPilots.filterLabel")}
        </label>
        <Select
          id="opening-pilot-filter"
          value={filter}
          onChange={(event) => setFilter(event.target.value as PilotFilter)}
        >
          {PILOT_FILTERS.map((candidate) => (
            <option key={candidate} value={candidate}>
              {candidate}
            </option>
          ))}
        </Select>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        {visiblePilots.map((pilot) => (
          <article
            key={pilot.productUnitId}
            className="studio-card space-y-4"
            aria-label={`${pilot.name} opening pilot`}
            tabIndex={0}
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="text-lg font-semibold text-gray-950">
                  {pilot.name}
                </h3>
                <p className="text-xs text-gray-500">{pilot.productUnitId}</p>
              </div>
              <Badge tone={pilot.isBlocked ? "danger" : "success"} variant="soft">
                {pilot.lifecycleStatus}
              </Badge>
            </div>

            <dl className="grid gap-3 text-sm md:grid-cols-2">
              <PilotFact
                title={t("studio.route.openingPilots.latestRunTitle")}
                value={pilot.runId}
                detail={pilot.runStatus}
              />
              <PilotFact
                title={t("studio.route.openingPilots.phaseStatusTitle")}
                value={pilot.phase}
                detail={pilot.runStatus}
              />
              <PilotFact
                title={t("studio.route.openingPilots.gateStatusTitle")}
                value={pilot.gateStatus}
                detail={pilot.gateReason}
              />
              <PilotFact
                title={t("studio.route.openingPilots.healthTitle")}
                value={pilot.healthStatus}
                detail={lifecycleData.selectedProviderMode}
              />
              <PilotFact
                title={t("studio.route.openingPilots.artifactTitle")}
                value={pilot.artifactRef}
                detail="artifact-manifest"
              />
              <PilotFact
                title={t("studio.route.openingPilots.deploymentTitle")}
                value={pilot.deploymentRef}
                detail="deployment-manifest"
              />
            </dl>

            <section aria-label={`${pilot.name} evidence links`}>
              <h4 className="text-sm font-semibold text-gray-950">
                {t("studio.route.openingPilots.evidenceTitle")}
              </h4>
              <ul className="mt-2 space-y-1 text-xs text-gray-700">
                {pilot.evidenceRefs.map((evidenceRef) => (
                  <li key={evidenceRef}>
                    <code className="rounded bg-gray-100 px-1 py-0.5">
                      {evidenceRef}
                    </code>
                  </li>
                ))}
              </ul>
            </section>

            <section
              className="rounded-md border border-gray-200 bg-gray-50 p-3"
              aria-label={`${pilot.name} next required action`}
            >
              <h4 className="text-sm font-semibold text-gray-950">
                {t("studio.route.openingPilots.nextActionTitle")}
              </h4>
              <p className="mt-1 text-sm text-gray-700">{pilot.nextAction}</p>
            </section>
          </article>
        ))}
      </div>
    </section>
  );
}

function PilotFact(props: {
  readonly title: string;
  readonly value: string;
  readonly detail: string;
}): ReactElement {
  return (
    <div className="rounded-md border border-gray-200 bg-white p-3">
      <dt className="text-xs font-semibold uppercase text-gray-500">
        {props.title}
      </dt>
      <dd className="mt-1 break-words text-sm font-medium text-gray-950">
        {props.value}
      </dd>
      <dd className="mt-1 break-words text-xs text-gray-600">
        {props.detail}
      </dd>
    </div>
  );
}

function createPilotViewModel(input: {
  readonly pilot: (typeof OPENING_PILOTS)[number];
  readonly productUnit: ProductUnit | undefined;
  readonly selectedProductUnitId: string;
  readonly snapshotProductUnitId: string | undefined;
  readonly runtimeTruth: ReturnType<typeof createStudioRuntimeTruthSummary>;
  readonly selectedRunStatus: string | undefined;
  readonly pendingApprovalCount: number;
}): PilotViewModel {
  const isSelected =
    input.selectedProductUnitId === input.pilot.id ||
    input.snapshotProductUnitId === input.pilot.id;
  const notReported = "not reported";
  const runStatus = isSelected
    ? input.runtimeTruth.lifecycleRun.status
    : "not selected";
  const gate = isSelected ? input.runtimeTruth.gates[0] : undefined;
  const lifecycleStatus = resolveLifecycleStatus(input.productUnit);
  const isBlocked =
    lifecycleStatus !== "enabled" ||
    runStatus === "blocked" ||
    gate?.state === "blocked";
  const isFailed = runStatus === "failed" || input.selectedRunStatus === "failed";

  return {
    productUnitId: input.pilot.id,
    name: input.productUnit?.name ?? input.pilot.name,
    lifecycleStatus,
    runId: isSelected ? input.runtimeTruth.lifecycleRun.runId : notReported,
    phase: isSelected ? input.runtimeTruth.lifecycleRun.phase : notReported,
    runStatus,
    gateStatus: gate?.gateId ?? input.pilot.failedGate,
    gateReason:
      gate === undefined
        ? `inspect ${input.pilot.failedGate}`
        : `${gate.status}: ${gate.reason}`,
    artifactRef: isSelected ? input.runtimeTruth.artifact.manifestRef : notReported,
    deploymentRef: isSelected
      ? input.runtimeTruth.deployment.manifestRef
      : notReported,
    healthStatus: isSelected ? input.runtimeTruth.health.status : notReported,
    evidenceRefs: [
      `kernel-product:${input.pilot.id}`,
      `gate:${input.pilot.failedGate}`,
      `runtime-truth:${input.pilot.id}`,
    ],
    nextAction:
      input.pendingApprovalCount > 0
        ? "Resolve pending approval before promotion."
        : input.pilot.nextAction,
    requiresApproval: input.pendingApprovalCount > 0,
    isBlocked,
    isFailed,
  };
}

function resolveLifecycleStatus(productUnit: ProductUnit | undefined): string {
  const productUnitRecord = productUnit as
    | {
        readonly lifecycleStatus?: string;
        readonly metadata?: {
          readonly lifecycleStatus?: string;
          readonly lifecycleExecutionAllowed?: boolean;
        };
      }
    | undefined;
  if (productUnitRecord?.lifecycleStatus !== undefined) {
    return productUnitRecord.lifecycleStatus;
  }
  if (productUnitRecord?.metadata?.lifecycleStatus !== undefined) {
    return productUnitRecord.metadata.lifecycleStatus;
  }
  if (productUnitRecord?.metadata?.lifecycleExecutionAllowed === true) {
    return "enabled";
  }
  return "not reported";
}

function pilotMatchesFilter(pilot: PilotViewModel, filter: PilotFilter): boolean {
  if (filter === "all") {
    return true;
  }
  if (filter === "blocked") {
    return pilot.isBlocked;
  }
  if (filter === "failed") {
    return pilot.isFailed;
  }
  if (filter === "requires-approval") {
    return pilot.requiresApproval;
  }
  return pilot.productUnitId === filter;
}
