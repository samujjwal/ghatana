import type { ReactElement } from 'react';
import { useState } from 'react';
import { Badge, Button, Select, Switch } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import type {
  GateResultManifest,
  LifecycleRun,
  VerifyHealthReport,
} from '../api/kernelLifecycleClient';
import type { ArtifactManifest } from '@ghatana/kernel-artifacts';
import type { DeploymentManifest } from '@ghatana/kernel-deployment';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

const LIFECYCLE_PHASES = ['validate', 'test', 'build', 'package', 'deploy', 'verify'] as const;
const DEFAULT_ENVIRONMENTS = ['local'] as const;
const PROVIDER_MODES = ['bootstrap', 'platform'] as const;

type LifecyclePhase = (typeof LIFECYCLE_PHASES)[number];
type ProviderMode = (typeof PROVIDER_MODES)[number];
type LifecycleBlockedReasonCode =
  | 'product-unit-unavailable'
  | 'phase-not-supported'
  | 'environment-not-supported'
  | 'provider-mode-unavailable'
  | 'lifecycle-execution-not-allowed';

interface LifecycleReadinessState {
  readonly lifecycleStatus: string;
  readonly lifecycleExecutionAllowed: boolean;
  readonly reasonCodes: readonly string[];
  readonly requiredGates: readonly string[];
  readonly nextRequiredWork: readonly string[];
}

function resolveLifecycleReadinessState(productUnit: unknown): LifecycleReadinessState {
  if (typeof productUnit !== 'object' || productUnit === null) {
    return {
      lifecycleStatus: 'unknown',
      lifecycleExecutionAllowed: false,
      reasonCodes: [],
      requiredGates: [],
      nextRequiredWork: [],
    };
  }

  const record = productUnit as {
    readonly lifecycleStatus?: unknown;
    readonly lifecycleExecutionAllowed?: unknown;
    readonly metadata?: {
      readonly lifecycleStatus?: unknown;
      readonly lifecycleExecutionAllowed?: unknown;
      readonly lifecycleReadiness?: {
        readonly reasonCodes?: unknown;
        readonly requiredGates?: unknown;
        readonly nextRequiredWork?: unknown;
      };
    };
  };

  const lifecycleReadiness = record.metadata?.lifecycleReadiness;
  return {
    lifecycleStatus:
      (typeof record.lifecycleStatus === 'string' && record.lifecycleStatus.trim().length > 0
        ? record.lifecycleStatus
        : typeof record.metadata?.lifecycleStatus === 'string' && record.metadata.lifecycleStatus.trim().length > 0
          ? record.metadata.lifecycleStatus
          : 'unknown'),
    lifecycleExecutionAllowed:
      (typeof record.lifecycleExecutionAllowed === 'boolean'
        ? record.lifecycleExecutionAllowed
        : typeof record.metadata?.lifecycleExecutionAllowed === 'boolean'
          ? record.metadata.lifecycleExecutionAllowed
          : false),
    reasonCodes: coerceStringArray(lifecycleReadiness?.reasonCodes),
    requiredGates: coerceStringArray(lifecycleReadiness?.requiredGates),
    nextRequiredWork: coerceStringArray(lifecycleReadiness?.nextRequiredWork),
  };
}

function coerceStringArray(value: unknown): readonly string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0);
}

function resolveEnvironmentOptions(metadata: unknown): readonly string[] {
  if (typeof metadata !== 'object' || metadata === null) {
    return DEFAULT_ENVIRONMENTS;
  }
  const record = metadata as {
    readonly environments?: unknown;
    readonly lifecycle?: { readonly environments?: unknown };
  };
  const fromRoot = coerceEnvironmentArray(record.environments);
  if (fromRoot.length > 0) {
    return fromRoot;
  }
  const fromLifecycle = coerceEnvironmentArray(record.lifecycle?.environments);
  if (fromLifecycle.length > 0) {
    return fromLifecycle;
  }
  return DEFAULT_ENVIRONMENTS;
}

function coerceEnvironmentArray(value: unknown): readonly string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const filtered = value.filter((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0);
  return filtered.length > 0 ? filtered : [];
}

function resolveSupportedPhases(metadata: unknown): readonly LifecyclePhase[] {
  if (typeof metadata !== 'object' || metadata === null) {
    return LIFECYCLE_PHASES;
  }
  const record = metadata as {
    readonly phases?: unknown;
    readonly lifecycle?: { readonly phases?: unknown };
  };
  const fromRoot = coercePhaseArray(record.phases);
  if (fromRoot.length > 0) {
    return fromRoot;
  }
  const fromLifecycle = coercePhaseArray(record.lifecycle?.phases);
  if (fromLifecycle.length > 0) {
    return fromLifecycle;
  }
  return LIFECYCLE_PHASES;
}

function coercePhaseArray(value: unknown): readonly LifecyclePhase[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter((entry): entry is LifecyclePhase =>
      typeof entry === 'string' && (LIFECYCLE_PHASES as readonly string[]).includes(entry),
    );
}

function lifecycleRunTone(status: string): 'success' | 'warning' | 'danger' | 'neutral' | 'info' {
  if (status === 'healthy' || status === 'ready') {
    return 'success';
  }
  if (status === 'running' || status === 'loading') {
    return 'info';
  }
  if (status === 'degraded' || status === 'pending approval' || status === 'requires verification') {
    return 'warning';
  }
  if (status === 'failed' || status === 'blocked' || status === 'quarantined') {
    return 'danger';
  }
  return 'neutral';
}

export default function LifecyclePage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const runtimeContextUserId = lifecycleData.authenticatedUserId;
  const t = useStudioTranslation();
  const readinessState = resolveLifecycleReadinessState(lifecycleData.snapshot.productUnit);
  const environmentOptions = resolveEnvironmentOptions(lifecycleData.snapshot.productUnit?.metadata);
  const supportedPhases = resolveSupportedPhases(lifecycleData.snapshot.productUnit?.metadata);
  
  const [selectedPhase, setSelectedPhase] = useState<LifecyclePhase>('build');
  const [environment, setEnvironment] = useState<string>(lifecycleData.selectedEnvironment);
  const [providerMode, setProviderMode] = useState<ProviderMode>(lifecycleData.selectedProviderMode);
  const [dryRun, setDryRun] = useState(false);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(lifecycleData.selectedRunId);
  const [isExecuting, setIsExecuting] = useState(false);
  const [approvalActionState, setApprovalActionState] = useState<Record<string, 'approve' | 'reject' | null>>({});

  const selectedRun = selectedRunId
    ? lifecycleData.snapshot.lifecycleRuns.find((run) => run.runId === selectedRunId)
    : lifecycleData.snapshot.selectedRun;
  const activeEnvironment = environmentOptions.includes(environment)
    ? environment
    : environmentOptions[0] ?? DEFAULT_ENVIRONMENTS[0];
  const environmentSupported = environmentOptions.includes(environment);
  const phaseSupported = supportedPhases.includes(selectedPhase);

  // Platform mode is disabled unless Data Cloud provider context is ready
  const platformModeDisabled = lifecycleData.snapshot.status === 'degraded' || lifecycleData.snapshot.status === 'unconfigured';
  const blockedReasonCodes: LifecycleBlockedReasonCode[] = [];
  if (lifecycleData.snapshot.productUnit === undefined) {
    blockedReasonCodes.push('product-unit-unavailable');
  }
  if (!phaseSupported) {
    blockedReasonCodes.push('phase-not-supported');
  }
  if (!environmentSupported) {
    blockedReasonCodes.push('environment-not-supported');
  }
  if (providerMode === 'platform' && platformModeDisabled) {
    blockedReasonCodes.push('provider-mode-unavailable');
  }
  if (!readinessState.lifecycleExecutionAllowed) {
    blockedReasonCodes.push('lifecycle-execution-not-allowed');
  }

  const runSelectedPhase = async (): Promise<void> => {
    setIsExecuting(true);
    lifecycleData.setEnvironment(activeEnvironment);
    lifecycleData.setProviderMode(providerMode);
    try {
      if (dryRun) {
        await lifecycleData.createPlan(selectedPhase, { dryRun: true, environment: activeEnvironment });
      } else {
        await lifecycleData.executePhase(selectedPhase, { dryRun: false, environment: activeEnvironment });
      }
      await lifecycleData.refresh();
    } finally {
      setIsExecuting(false);
    }
  };

  const submitApproval = async (approvalId: string, approved: boolean): Promise<void> => {
    const approvedBy = runtimeContextUserId;
    if (approvedBy === undefined) {
      return;
    }
    setApprovalActionState((current) => ({ ...current, [approvalId]: approved ? 'approve' : 'reject' }));
    try {
      await lifecycleData.submitApprovalDecision(approvalId, {
        approvalId,
        approved,
        approvedBy,
        reason: approved ? 'Approved from Studio lifecycle queue' : 'Rejected from Studio lifecycle queue',
        decidedAt: new Date().toISOString(),
      });
      await lifecycleData.refresh();
    } finally {
      setApprovalActionState((current) => ({ ...current, [approvalId]: null }));
    }
  };

  return (
    <section className="space-y-6" aria-labelledby="lifecycle-title">
      <div className="space-y-2">
        <Badge tone={lifecycleDataBadgeTone(lifecycleData.snapshot.status)} variant="soft">
          {describeLifecycleDataStatus(lifecycleData.snapshot.status)}
        </Badge>
        <h2 id="lifecycle-title" className="text-2xl font-semibold text-gray-950">
          {t('studio.route.lifecycle.title')}
        </h2>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          {t('studio.route.lifecycle.description')}
        </p>
      </div>

      {/* Controls */}
      <div className="grid gap-4 lg:grid-cols-[1.5fr_1fr]">
        <article className="studio-card space-y-4">
          <h3 className="text-base font-semibold text-gray-950">{t('studio.route.lifecycle.controlsTitle')}</h3>
          
          {/* ProductUnit selector */}
          <div className="space-y-2">
            <label htmlFor="product-unit-select" className="block text-sm font-medium text-gray-900">
              {t('studio.route.lifecycle.productUnitLabel')}
            </label>
            <Select
              id="product-unit-select"
              value={lifecycleData.snapshot.productUnit?.id ?? 'digital-marketing'}
              onChange={(event) => {
                lifecycleData.selectProductUnit(event.target.value);
              }}
              disabled={lifecycleData.snapshot.status === 'loading'}
            >
              {lifecycleData.snapshot.availableProductUnits.map((productUnit) => (
                <option key={productUnit.id} value={productUnit.id}>
                  {productUnit.name}
                </option>
              ))}
            </Select>
          </div>

          {/* Phase buttons */}
          <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-900">{t('studio.route.lifecycle.phaseLabel')}</label>
            <div className="flex flex-wrap gap-2">
              {LIFECYCLE_PHASES.map((phase) => (
                <Button
                  key={phase}
                  variant={selectedPhase === phase ? 'primary' : 'outline'}
                  size="sm"
                  onClick={() => setSelectedPhase(phase)}
                  aria-pressed={selectedPhase === phase}
                >
                  {phase.charAt(0).toUpperCase() + phase.slice(1)}
                </Button>
              ))}
            </div>
          </div>

          {/* Dry-run toggle */}
          <div className="flex items-center gap-3">
            <Switch
              id="dry-run-toggle"
              checked={dryRun}
              onToggle={(checked) => setDryRun(checked)}
              aria-label={t('studio.route.lifecycle.dryRunLabel')}
            />
            <label htmlFor="dry-run-toggle" className="text-sm text-gray-900">
              {t('studio.route.lifecycle.dryRunLabel')}
            </label>
          </div>

          {/* Environment selector */}
          <div className="space-y-2">
            <label htmlFor="environment-select" className="block text-sm font-medium text-gray-900">
              {t('studio.route.lifecycle.environmentLabel')}
            </label>
            <Select
              id="environment-select"
              value={activeEnvironment}
              onChange={(e) => {
                const nextEnvironment = e.target.value;
                setEnvironment(nextEnvironment);
                lifecycleData.setEnvironment(nextEnvironment);
              }}
            >
              {environmentOptions.map((env) => (
                <option key={env} value={env}>
                  {env.charAt(0).toUpperCase() + env.slice(1)}
                </option>
              ))}
            </Select>
          </div>

          {/* Provider-mode selector */}
          <div className="space-y-2">
            <label htmlFor="provider-mode-select" className="block text-sm font-medium text-gray-900">
              {t('studio.route.lifecycle.providerModeLabel')}
            </label>
            <Select
              id="provider-mode-select"
              value={providerMode}
              onChange={(e) => {
                const nextProviderMode = e.target.value as ProviderMode;
                setProviderMode(nextProviderMode);
                lifecycleData.setProviderMode(nextProviderMode);
              }}
              disabled={platformModeDisabled}
            >
              {PROVIDER_MODES.map((mode) => (
                <option key={mode} value={mode}>
                  {mode.charAt(0).toUpperCase() + mode.slice(1)}
                </option>
              ))}
            </Select>
            {platformModeDisabled && (
              <p className="text-xs text-gray-500">{t('studio.route.lifecycle.platformModeDisabledHint')}</p>
            )}
          </div>

          {/* Execute button */}
          <Button
            variant="primary"
            disabled={
              isExecuting ||
              lifecycleData.snapshot.status === 'loading' ||
              lifecycleData.snapshot.status === 'degraded' ||
              blockedReasonCodes.length > 0
            }
            onClick={() => {
              void runSelectedPhase();
            }}
          >
            {t('studio.route.lifecycle.executePhaseButton')}
          </Button>

          {blockedReasonCodes.length > 0 && (
            <div className="rounded-md border border-amber-300 bg-amber-50 p-3" aria-label="lifecycle-blocked-reasons">
              <div className="text-xs font-semibold text-amber-800">{t('studio.route.lifecycle.blockedReasonCodesTitle')}</div>
              <ul className="mt-2 space-y-1 text-xs text-amber-900">
                {blockedReasonCodes.map((code) => (
                  <li key={code}>{code}</li>
                ))}
              </ul>
            </div>
          )}

          {(!readinessState.lifecycleExecutionAllowed ||
            readinessState.requiredGates.length > 0 ||
            readinessState.nextRequiredWork.length > 0) && (
            <div className="rounded-md border border-red-300 bg-red-50 p-3" aria-label="lifecycle-readiness-state">
              <div className="text-xs font-semibold text-red-900">{t('studio.route.lifecycle.readinessTitle')}</div>
              <div className="mt-2 text-xs text-red-900">
                {t('studio.route.lifecycle.readinessStatusLabel')}: {readinessState.lifecycleStatus}
              </div>
              <div className="mt-1 text-xs text-red-900">
                {t('studio.route.lifecycle.readinessExecutionAllowedLabel')}: {readinessState.lifecycleExecutionAllowed ? t('studio.route.lifecycle.readinessAllowedValue') : t('studio.route.lifecycle.readinessBlockedValue')}
              </div>
              {readinessState.reasonCodes.length > 0 && (
                <>
                  <div className="mt-2 text-xs font-semibold text-red-900">{t('studio.route.lifecycle.readinessReasonCodesTitle')}</div>
                  <ul className="mt-1 space-y-1 text-xs text-red-900">
                    {readinessState.reasonCodes.map((reasonCode) => (
                      <li key={reasonCode}>{reasonCode}</li>
                    ))}
                  </ul>
                </>
              )}
              {readinessState.requiredGates.length > 0 && (
                <>
                  <div className="mt-2 text-xs font-semibold text-red-900">{t('studio.route.lifecycle.readinessRequiredGatesTitle')}</div>
                  <ul className="mt-1 space-y-1 text-xs text-red-900">
                    {readinessState.requiredGates.map((requiredGate) => (
                      <li key={requiredGate}>{requiredGate}</li>
                    ))}
                  </ul>
                </>
              )}
              {readinessState.nextRequiredWork.length > 0 && (
                <>
                  <div className="mt-2 text-xs font-semibold text-red-900">{t('studio.route.lifecycle.readinessNextRequiredWorkTitle')}</div>
                  <ul className="mt-1 space-y-1 text-xs text-red-900">
                    {readinessState.nextRequiredWork.map((workItem) => (
                      <li key={workItem}>{workItem}</li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          )}
        </article>

        {/* Lifecycle run list */}
        <article className="studio-card space-y-3" aria-labelledby="run-list-title">
          <h3 id="run-list-title" className="text-base font-semibold text-gray-950">
            {t('studio.route.lifecycle.runListTitle')}
          </h3>
          <div className="max-h-64 overflow-auto">
            {lifecycleData.snapshot.lifecycleRuns.length === 0 ? (
              <p className="text-sm text-gray-600">{t('studio.route.lifecycle.noRunsMessage')}</p>
            ) : (
              <ul className="space-y-2">
                {lifecycleData.snapshot.lifecycleRuns.map((run) => (
                  <li key={run.runId}>
                    <button
                      className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-left text-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      onClick={() => setSelectedRunId(run.runId)}
                      aria-pressed={selectedRunId === run.runId}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-gray-900">{run.phase ?? t('studio.route.lifecycle.unknownValue')}</span>
                        <Badge tone={lifecycleRunTone(run.status)} variant="soft" className="text-xs">
                          {run.status}
                        </Badge>
                      </div>
                      <div className="mt-1 text-xs text-gray-500">{run.correlationId}</div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </article>
      </div>

      {/* Selected run detail */}
      {selectedRun && (
        <article className="studio-card space-y-3" aria-labelledby="run-detail-title">
          <h3 id="run-detail-title" className="text-base font-semibold text-gray-950">
            {t('studio.route.lifecycle.runDetailTitle')}
          </h3>
          <RunDetailPanel run={selectedRun} />
        </article>
      )}

      {/* Manifest tabs */}
      <article className="studio-card space-y-3" aria-labelledby="manifest-tabs-title">
        <h3 id="manifest-tabs-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.lifecycle.manifestTabsTitle')}
        </h3>
        <div className="space-y-4">
          {selectedRun && (
            <>
              <ManifestTab
                title={t('studio.route.lifecycle.manifest.lifecyclePlanTitle')}
                content={
                  <LifecyclePlanPanel
                    runId={selectedRun.runId}
                    phase={selectedRun.phase}
                    correlationId={selectedRun.correlationId}
                    status={selectedRun.status}
                  />
                }
              />
              <ManifestTab
                title={t('studio.route.lifecycle.manifest.lifecycleResultTitle')}
                content={
                  <LifecycleResultPanel run={selectedRun} />
                }
              />
              <ManifestTab
                title={t('studio.route.lifecycle.manifest.gateResultTitle')}
                status={lifecycleData.snapshot.manifestLoadState.gateResultManifest.status}
                message={lifecycleData.snapshot.manifestLoadState.gateResultManifest.message}
                content={
                  <GateManifestPanel manifest={lifecycleData.snapshot.gateResultManifest} />
                }
              />
              <ManifestTab
                title={t('studio.route.lifecycle.manifest.artifactTitle')}
                status={lifecycleData.snapshot.manifestLoadState.artifactManifest.status}
                message={lifecycleData.snapshot.manifestLoadState.artifactManifest.message}
                content={
                  <ArtifactManifestPanel manifest={lifecycleData.snapshot.artifactManifest} />
                }
              />
              <ManifestTab
                title={t('studio.route.lifecycle.manifest.deploymentTitle')}
                status={lifecycleData.snapshot.manifestLoadState.deploymentManifest.status}
                message={lifecycleData.snapshot.manifestLoadState.deploymentManifest.message}
                content={
                  <DeploymentManifestPanel manifest={lifecycleData.snapshot.deploymentManifest} />
                }
              />
              <ManifestTab
                title={t('studio.route.lifecycle.manifest.verifyHealthTitle')}
                status={lifecycleData.snapshot.manifestLoadState.verifyHealthReport.status}
                message={lifecycleData.snapshot.manifestLoadState.verifyHealthReport.message}
                content={
                  <VerifyHealthPanel manifest={lifecycleData.snapshot.verifyHealthReport} />
                }
              />
            </>
          )}
        </div>
      </article>

      {/* Approval queue panel */}
      <article className="studio-card space-y-3" aria-labelledby="approval-queue-title">
        <h3 id="approval-queue-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.lifecycle.approvalQueueTitle')}
        </h3>
        {lifecycleData.snapshot.pendingApprovals.length === 0 ? (
          <p className="text-sm text-gray-600">{t('studio.route.lifecycle.noPendingApprovals')}</p>
        ) : (
          <ul className="space-y-2">
            {lifecycleData.snapshot.pendingApprovals.map((approval) => (
              <li key={approval.approvalId} className="space-y-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-gray-900">{approval.approvalId}</span>
                  <Badge tone="warning" variant="soft">{t('studio.lifecycle.approval.pending')}</Badge>
                </div>
                <div className="grid gap-1 text-xs text-gray-700">
                  <div>{t('studio.route.lifecycle.approval.productLabel')}: {approval.productUnitId}</div>
                  <div>{t('studio.route.lifecycle.approval.runLabel')}: {approval.runId ?? t('studio.route.lifecycle.notAvailableValue')}</div>
                  <div>{t('studio.route.lifecycle.approval.requestedByLabel')}: {approval.requestedBy}</div>
                  <div>{t('studio.route.lifecycle.approval.reasonLabel')}: {approval.reason}</div>
                  <div>{t('studio.route.lifecycle.approval.approversLabel')}: {approval.requiredApprovers.join(', ')}</div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    size="sm"
                    variant="primary"
                    disabled={runtimeContextUserId === undefined || (approvalActionState[approval.approvalId] !== null && approvalActionState[approval.approvalId] !== undefined)}
                    onClick={() => {
                      void submitApproval(approval.approvalId, true);
                    }}
                  >
                    {t('studio.lifecycle.action.submitApproval')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={runtimeContextUserId === undefined || (approvalActionState[approval.approvalId] !== null && approvalActionState[approval.approvalId] !== undefined)}
                    onClick={() => {
                      void submitApproval(approval.approvalId, false);
                    }}
                  >
                    {t('studio.lifecycle.action.rejectApproval')}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </article>

      {/* Failure diagnostics */}
      {selectedRun?.failureReasonCode && (
        <article className="studio-card space-y-3 border-red-200 bg-red-50" aria-labelledby="failure-diagnostics-title">
          <h3 id="failure-diagnostics-title" className="text-base font-semibold text-red-900">
            {t('studio.route.lifecycle.failureDiagnosticsTitle')}
          </h3>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="font-medium text-gray-900">{t('studio.route.lifecycle.reasonCodeLabel')}:</span>
              <span className="text-red-900">{selectedRun.failureReasonCode}</span>
            </div>
            <div className="flex justify-between">
              <span className="font-medium text-gray-900">{t('studio.route.lifecycle.failedPhaseLabel')}:</span>
              <span className="text-red-900">{selectedRun.phase ?? t('studio.route.lifecycle.unknownValue')}</span>
            </div>
            <div className="flex justify-between">
              <span className="font-medium text-gray-900">{t('studio.route.lifecycle.correlationIdLabel')}:</span>
              <span className="text-red-900">{selectedRun.correlationId}</span>
            </div>
          </div>
        </article>
      )}

      <article className="studio-card space-y-2" aria-labelledby="validation-command-title">
        <h3 id="validation-command-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.lifecycle.validationCommandTitle')}
        </h3>
        <code className="block rounded-md bg-gray-100 p-3 text-sm text-gray-900">
          pnpm check:digital-marketing-lifecycle-pilot --smoke
        </code>
      </article>
    </section>
  );
}

function LifecyclePlanPanel(props: {
  readonly runId: string;
  readonly phase: string | undefined;
  readonly correlationId: string;
  readonly status: string;
}): ReactElement {
  const t = useStudioTranslation();
  return (
    <dl className="grid grid-cols-1 gap-2 text-gray-100 sm:grid-cols-2">
      <div className="rounded bg-gray-900 px-2 py-1">
        <dt className="text-[11px] uppercase tracking-wide text-gray-400">{t('studio.route.lifecycle.runIdLabel')}</dt>
        <dd className="font-medium">{props.runId}</dd>
      </div>
      <div className="rounded bg-gray-900 px-2 py-1">
        <dt className="text-[11px] uppercase tracking-wide text-gray-400">{t('studio.route.lifecycle.phaseLabel')}</dt>
        <dd className="font-medium">{props.phase ?? t('studio.route.lifecycle.unknownValue')}</dd>
      </div>
      <div className="rounded bg-gray-900 px-2 py-1">
        <dt className="text-[11px] uppercase tracking-wide text-gray-400">{t('studio.route.lifecycle.correlationIdLabel')}</dt>
        <dd className="font-medium">{props.correlationId}</dd>
      </div>
      <div className="rounded bg-gray-900 px-2 py-1">
        <dt className="text-[11px] uppercase tracking-wide text-gray-400">{t('studio.route.lifecycle.statusLabel')}</dt>
        <dd className="font-medium">{props.status}</dd>
      </div>
    </dl>
  );
}

function LifecycleResultPanel(props: { readonly run: LifecycleRun }): ReactElement {
  const t = useStudioTranslation();
  const run = props.run;
  const manifestRefs = run.manifestRefs ?? {};
  const approvalRefs = run.approvalRefs ?? [];

  return (
    <div className="space-y-2">
      <div className="rounded bg-gray-900 px-2 py-1 text-gray-100">
        <span className="text-[11px] uppercase tracking-wide text-gray-400">{t('studio.route.lifecycle.outcomeLabel')}</span>
        <div className="mt-1 font-medium">
          {run.failureReasonCode === undefined ? t('studio.route.lifecycle.outcome.completedWithoutFailureReason') : `${t('studio.route.lifecycle.outcome.failedPrefix')}: ${run.failureReasonCode}`}
        </div>
      </div>
      <div className="rounded bg-gray-900 px-2 py-1 text-gray-100">
        <span className="text-[11px] uppercase tracking-wide text-gray-400">{t('studio.route.lifecycle.manifestRefsLabel')}</span>
        <div className="mt-1 text-sm">{Object.keys(manifestRefs).length}</div>
      </div>
      <div className="rounded bg-gray-900 px-2 py-1 text-gray-100">
        <span className="text-[11px] uppercase tracking-wide text-gray-400">{t('studio.route.lifecycle.approvalRefsLabel')}</span>
        <div className="mt-1 text-sm">{approvalRefs.length}</div>
      </div>
      {run.failureReasonCode !== undefined && (
        <div className="rounded bg-red-950/60 px-2 py-1 text-red-100">
          <span className="text-[11px] uppercase tracking-wide text-red-200">{t('studio.route.lifecycle.failureReasonLabel')}</span>
          <div className="mt-1 font-medium">{run.failureReasonCode}</div>
        </div>
      )}
    </div>
  );
}

function RunDetailPanel(props: { readonly run: LifecycleRun }): ReactElement {
  const t = useStudioTranslation();
  const run = props.run;

  return (
    <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
      <DetailRow label={t('studio.route.lifecycle.runIdLabel')} value={run.runId} />
      <DetailRow label={t('studio.route.lifecycle.productUnitLabel')} value={run.productUnitId} />
      <DetailRow label={t('studio.route.lifecycle.phaseLabel')} value={run.phase ?? t('studio.route.lifecycle.unknownValue')} />
      <DetailRow label={t('studio.route.lifecycle.statusLabel')} value={run.status} />
      <DetailRow label={t('studio.route.lifecycle.correlationIdLabel')} value={run.correlationId} />
      <DetailRow label={t('studio.route.lifecycle.healthRefLabel')} value={run.healthSnapshotRef ?? t('studio.route.lifecycle.notAvailableValue')} />
      <DetailRow label={t('studio.route.lifecycle.eventsRefLabel')} value={run.eventsRef ?? t('studio.route.lifecycle.notAvailableValue')} />
      <DetailRow
        label={t('studio.route.lifecycle.failureReasonLabel')}
        value={run.failureReasonCode ?? t('studio.route.lifecycle.noneValue')}
        tone={run.failureReasonCode === undefined ? 'neutral' : 'danger'}
      />
    </dl>
  );
}

function DetailRow(props: {
  readonly label: string;
  readonly value: string;
  readonly tone?: 'neutral' | 'danger';
}): ReactElement {
  return (
    <div className={
      props.tone === 'danger'
        ? 'rounded border border-red-200 bg-red-50 px-3 py-2'
        : 'rounded border border-gray-200 bg-gray-50 px-3 py-2'
    }>
      <dt className="text-[11px] uppercase tracking-wide text-gray-500">{props.label}</dt>
      <dd className={props.tone === 'danger' ? 'mt-1 font-medium text-red-900' : 'mt-1 font-medium text-gray-900'}>
        {props.value}
      </dd>
    </div>
  );
}

interface ManifestTabProps {
  readonly title: string;
  readonly status?: string;
  readonly message?: string;
  readonly content: ReactElement;
}

function ManifestTab(props: ManifestTabProps): ReactElement {
  const t = useStudioTranslation();
  const status = props.status;
  const statusTone = resolveManifestStatusTone(status);
  const remediationKey = resolveManifestRemediationKey(status);

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-sm font-medium text-gray-900">{props.title}</h4>
        {status && (
          <Badge tone={statusTone} variant="soft">
            {t(`studio.route.lifecycle.manifest.status.${status}` as never)}
          </Badge>
        )}
      </div>
      {status !== undefined && status !== 'loaded' && (
        <div className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900" aria-label="manifest-status-reason">
          <div className="font-semibold">{t('studio.route.lifecycle.manifest.reasonTitle')}</div>
          <div className="mt-1">{props.message ?? t('studio.route.lifecycle.manifest.reasonDefault')}</div>
          <div className="mt-2 font-semibold">{t('studio.route.lifecycle.manifest.remediationTitle')}</div>
          <div className="mt-1">{t(remediationKey as never)}</div>
        </div>
      )}
      <div className="rounded-md bg-gray-950 p-4 text-xs leading-5 text-gray-100">
        {props.content}
      </div>
    </div>
  );
}

function resolveManifestStatusTone(status: string | undefined): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'loaded':
      return 'success';
    case 'missing':
    case 'unavailable':
      return 'warning';
    case 'corrupt':
    case 'unauthorized':
      return 'danger';
    default:
      return 'neutral';
  }
}

function resolveManifestRemediationKey(status: string | undefined):
  | 'studio.route.lifecycle.manifest.remediation.missing'
  | 'studio.route.lifecycle.manifest.remediation.corrupt'
  | 'studio.route.lifecycle.manifest.remediation.unauthorized'
  | 'studio.route.lifecycle.manifest.remediation.unavailable'
  | 'studio.route.lifecycle.manifest.remediation.default' {
  switch (status) {
    case 'missing':
      return 'studio.route.lifecycle.manifest.remediation.missing';
    case 'corrupt':
      return 'studio.route.lifecycle.manifest.remediation.corrupt';
    case 'unauthorized':
      return 'studio.route.lifecycle.manifest.remediation.unauthorized';
    case 'unavailable':
      return 'studio.route.lifecycle.manifest.remediation.unavailable';
    default:
      return 'studio.route.lifecycle.manifest.remediation.default';
  }
}

function GateManifestPanel(props: { readonly manifest?: GateResultManifest }): ReactElement {
  const t = useStudioTranslation();
  const gates = props.manifest?.gates ?? [];
  if (gates.length === 0) {
    return <p className="text-gray-300">{t('studio.route.lifecycle.noGateEvidence')}</p>;
  }
  return (
    <ul className="space-y-2">
      {gates.map((gate) => (
        <li key={gate.gateId} className="flex items-center justify-between gap-2 rounded bg-gray-900 px-2 py-1">
          <span>{gate.gateId}</span>
          <span>{gate.status}</span>
        </li>
      ))}
    </ul>
  );
}

function ArtifactManifestPanel(props: { readonly manifest?: ArtifactManifest }): ReactElement {
  const t = useStudioTranslation();
  const artifacts = props.manifest?.artifacts ?? [];
  if (artifacts.length === 0) {
    return <p className="text-gray-300">{t('studio.route.lifecycle.noArtifactEvidence')}</p>;
  }
  return (
    <ul className="space-y-2">
      {artifacts.map((artifact) => (
        <li key={artifact.id} className="rounded bg-gray-900 px-2 py-1">
          <div className="font-medium">{artifact.id}</div>
          <div className="text-gray-300">{artifact.metadata.type} | {artifact.metadata.packaging}</div>
          <div className="text-gray-300">{artifact.found ? t('studio.lifecycle.artifact.found') : t('studio.lifecycle.artifact.missing')}</div>
        </li>
      ))}
    </ul>
  );
}

function DeploymentManifestPanel(props: { readonly manifest?: DeploymentManifest }): ReactElement {
  const t = useStudioTranslation();
  const manifest = props.manifest;
  if (manifest === undefined) {
    return <p className="text-gray-300">{t('studio.route.lifecycle.noDeploymentEvidence')}</p>;
  }
  return (
    <div className="space-y-1">
      <div>{t('studio.route.lifecycle.environmentLabel')}: {manifest.environment}</div>
      <div>{t('studio.route.lifecycle.deploymentIdLabel')}: {manifest.deploymentId}</div>
      <div>{t('studio.route.lifecycle.targetLabel')}: {'target' in manifest && typeof manifest.target === 'string' ? manifest.target : t('studio.route.lifecycle.notAvailableValue')}</div>
      <div>{t('studio.route.lifecycle.surfacesLabel')}: {Array.isArray(manifest.surfaces) ? String(manifest.surfaces.length) : '0'}</div>
    </div>
  );
}

function VerifyHealthPanel(props: { readonly manifest?: VerifyHealthReport }): ReactElement {
  const t = useStudioTranslation();
  const manifest = props.manifest;
  if (manifest === undefined) {
    return <p className="text-gray-300">{t('studio.route.lifecycle.noVerificationEvidence')}</p>;
  }
  return (
    <div className="space-y-1">
      <div>{t('studio.route.lifecycle.statusLabel')}: {manifest.status}</div>
      <div>{t('studio.route.lifecycle.runLabel')}: {manifest.runId}</div>
      <div>{t('studio.route.lifecycle.checkedLabel')}: {manifest.checkedAt ?? t('studio.route.lifecycle.notAvailableValue')}</div>
    </div>
  );
}
