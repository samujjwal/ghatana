/**
 * Admin: Observability Route
 *
 * @doc.type route
 * @doc.purpose Admin observability and release-gate evidence page
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { ObservabilityDashboard, type HealthMetric, type ReleaseGateEvidence } from '../../../components/admin/ObservabilityDashboard';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { loadReleaseGateEvidence } from '../../../services/admin/releaseGateEvidenceApi';
import { useTranslation } from '@ghatana/i18n';
import { AdminRouteGate } from './AdminRouteGate';

function AdminGate({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation('common');

  return (
    <AdminRouteGate
      capability="admin:observability"
      deniedTestId="admin-observability-unavailable"
      messages={{
        permissionDenied: t('admin.observability.denied.permission'),
        loginRequired: t('admin.observability.denied.login'),
        unavailable: t('admin.observability.denied.unavailable'),
      }}
    >
      {children}
    </AdminRouteGate>
  );
}

function ObservabilityRouteContent() {
  const [refreshedAt, setRefreshedAt] = useState(() => new Date().toISOString());
  const [releaseGates, setReleaseGates] = useState<readonly ReleaseGateEvidence[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const { t } = useTranslation('common');

  const refreshEvidence = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);

    try {
      const records = await loadReleaseGateEvidence();
      setReleaseGates(records);
      setRefreshedAt(new Date().toISOString());
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setLoadError(detail);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load(): Promise<void> {
      setIsLoading(true);
      setLoadError(null);

      try {
        const records = await loadReleaseGateEvidence();
        if (!cancelled) {
          setReleaseGates(records);
          setRefreshedAt(new Date().toISOString());
        }
      } catch (error) {
        if (!cancelled) {
          const detail = error instanceof Error ? error.message : String(error);
          setLoadError(detail);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    void load();

    return () => {
      cancelled = true;
    };
  }, []);

  const unhealthyGateCount = releaseGates.filter((gate) => gate.status !== 'healthy').length;
  const metrics = useMemo<HealthMetric[]>(() => [
    {
      id: 'release-gates',
      label: t('admin.observability.metric.releaseGates'),
      value: t('admin.observability.metric.releaseGatesValue', { count: releaseGates.length }),
      status: unhealthyGateCount > 0 ? 'degraded' : 'healthy',
      refreshedAt,
    },
    {
      id: 'evidence-bundle',
      label: t('admin.observability.metric.evidenceBundle'),
      value: loadError ? t('admin.observability.metric.needsReview') : t('admin.observability.metric.published'),
      status: loadError ? 'down' : 'healthy',
      refreshedAt,
    },
  ], [loadError, refreshedAt, releaseGates.length, t, unhealthyGateCount]);

  return (
    <main className="min-h-screen bg-surface p-6">
      <ObservabilityDashboard
        metrics={metrics}
        releaseGates={[...releaseGates]}
        isLoading={isLoading}
        error={loadError ?? undefined}
        onRefresh={() => {
          void refreshEvidence();
        }}
      />
    </main>
  );
}

export function Component() {
  return (
    <AdminGate>
      <Suspense fallback={<RouteLoadingSpinner />}>
        <ObservabilityRouteContent />
      </Suspense>
    </AdminGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;
