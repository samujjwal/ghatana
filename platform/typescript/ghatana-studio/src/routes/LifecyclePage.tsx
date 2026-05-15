import type { ReactElement } from 'react';
import { useState } from 'react';
import { Badge, Button, Select, Switch } from '@ghatana/design-system';
import { useStudioLifecycleData } from '../data/StudioLifecycleDataContext';
import { useStudioTranslation } from '../i18n/studioTranslations';
import {
  describeLifecycleDataStatus,
  lifecycleDataBadgeTone,
} from './studioLifecycleRouteSupport';

const LIFECYCLE_PHASES = ['validate', 'test', 'build', 'package', 'deploy', 'verify'] as const;
const ENVIRONMENTS = ['local', 'dev', 'staging', 'production'] as const;
const PROVIDER_MODES = ['bootstrap', 'platform'] as const;

type LifecyclePhase = (typeof LIFECYCLE_PHASES)[number];
type Environment = (typeof ENVIRONMENTS)[number];
type ProviderMode = (typeof PROVIDER_MODES)[number];

export default function LifecyclePage(): ReactElement {
  const lifecycleData = useStudioLifecycleData();
  const t = useStudioTranslation();
  
  const [selectedPhase, setSelectedPhase] = useState<LifecyclePhase>('build');
  const [environment, setEnvironment] = useState<Environment>(lifecycleData.selectedEnvironment);
  const [providerMode, setProviderMode] = useState<ProviderMode>(lifecycleData.selectedProviderMode);
  const [dryRun, setDryRun] = useState(false);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(lifecycleData.selectedRunId);
  const [isExecuting, setIsExecuting] = useState(false);

  const selectedRun = selectedRunId
    ? lifecycleData.snapshot.lifecycleRuns.find((run) => run.runId === selectedRunId)
    : lifecycleData.snapshot.selectedRun;

  // Platform mode is disabled unless Data Cloud provider context is ready
  const platformModeDisabled = lifecycleData.snapshot.status === 'degraded' || lifecycleData.snapshot.status === 'unconfigured';

  const runSelectedPhase = async (): Promise<void> => {
    setIsExecuting(true);
    lifecycleData.setEnvironment(environment);
    lifecycleData.setProviderMode(providerMode);
    try {
      if (dryRun) {
        await lifecycleData.createPlan(selectedPhase, { dryRun: true, environment });
      } else {
        await lifecycleData.executePhase(selectedPhase, { dryRun: false, environment });
      }
      await lifecycleData.refresh();
    } finally {
      setIsExecuting(false);
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
                void event;
              }}
              disabled={lifecycleData.snapshot.status === 'loading'}
            >
              <option value="digital-marketing">Digital Marketing</option>
              {/* Additional product units would be loaded from the API */}
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
              value={environment}
              onChange={(e) => {
                const nextEnvironment = e.target.value as Environment;
                setEnvironment(nextEnvironment);
                lifecycleData.setEnvironment(nextEnvironment);
              }}
            >
              {ENVIRONMENTS.map((env) => (
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
              lifecycleData.snapshot.status === 'degraded'
            }
            onClick={() => {
              void runSelectedPhase();
            }}
          >
            {t('studio.route.lifecycle.executePhaseButton')}
          </Button>
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
                        <span className="font-medium text-gray-900">{run.phase ?? 'Unknown'}</span>
                        <Badge tone={lifecycleDataBadgeTone(run.status)} variant="soft" className="text-xs">
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
          <pre className="overflow-auto rounded-md bg-gray-950 p-4 text-xs leading-5 text-gray-100">
            {JSON.stringify(selectedRun, null, 2)}
          </pre>
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
              <ManifestTab title="Lifecycle Plan" content={JSON.stringify({ runId: selectedRun.runId, phase: selectedRun.phase }, null, 2)} />
              <ManifestTab title="Lifecycle Result" content={JSON.stringify(selectedRun, null, 2)} />
              <ManifestTab title="Gate Result Manifest" content={JSON.stringify(lifecycleData.snapshot.gateResultManifest, null, 2)} />
              <ManifestTab title="Artifact Manifest" content={JSON.stringify(lifecycleData.snapshot.artifactManifest, null, 2)} />
              <ManifestTab title="Deployment Manifest" content={JSON.stringify(lifecycleData.snapshot.deploymentManifest, null, 2)} />
              <ManifestTab title="Verify Health Report" content={JSON.stringify(lifecycleData.snapshot.verifyHealthReport, null, 2)} />
            </>
          )}
        </div>
      </article>

      {/* Approval queue panel */}
      <article className="studio-card space-y-3" aria-labelledby="approval-queue-title">
        <h3 id="approval-queue-title" className="text-base font-semibold text-gray-950">
          {t('studio.route.lifecycle.approvalQueueTitle')}
        </h3>
        <p className="text-sm text-gray-600">{t('studio.route.lifecycle.noPendingApprovals')}</p>
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
              <span className="text-red-900">{selectedRun.phase ?? 'Unknown'}</span>
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

interface ManifestTabProps {
  readonly title: string;
  readonly content: string;
}

function ManifestTab(props: ManifestTabProps): ReactElement {
  return (
    <div className="space-y-2">
      <h4 className="text-sm font-medium text-gray-900">{props.title}</h4>
      <pre className="overflow-auto rounded-md bg-gray-950 p-4 text-xs leading-5 text-gray-100">
        {props.content}
      </pre>
    </div>
  );
}
