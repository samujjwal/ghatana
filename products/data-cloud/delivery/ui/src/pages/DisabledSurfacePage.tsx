/**
 * Disabled Surface Page
 *
 * Shown when a capability-gated route surface is disabled or not available
 * in the current Data Cloud configuration. Provides a clear explanation
 * and next action instead of a generic 404.
 *
 * <p><b>DC-UI-002 Enhancement:</b> This page now includes dependency information,
 * specific status, next action, and remediation/runbook link for every unavailable surface.
 *
 * @doc.type component
 * @doc.purpose User-facing unavailable surface page for capability-gated routes
 * @doc.layer frontend
 * @doc.pattern Page
 */

import React from 'react';
import { useNavigate } from 'react-router';
import { Lock, AlertCircle, ExternalLink, CheckCircle, XCircle, Settings } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/theme';

/**
 * Surface status types.
 */
export type SurfaceStatus = 'DISABLED' | 'DEGRADED' | 'UNAVAILABLE' | 'MISCONFIGURED';

/**
 * Surface dependency information.
 */
export interface SurfaceDependency {
  /** Name of the dependency (e.g., "Event Log Store", "Database") */
  name: string;
  /** Current status of the dependency */
  status: 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'MISSING';
  /** Optional description of the dependency issue */
  description?: string;
}

export interface DisabledSurfacePageProps {
  /** Primary surface name shown to the user (e.g. "Alerts", "Memory Plane") */
  surfaceName?: string;
  /** Optional description of what this surface provides */
  surfaceDescription?: string;
  /** Current status of the surface */
  status?: SurfaceStatus;
  /** List of dependencies that are causing the surface to be unavailable */
  dependencies?: SurfaceDependency[];
  /** Specific next action to remediate the issue */
  nextAction?: string;
  /** Link to remediation documentation or runbook */
  remediationLink?: string;
  /** Optional action hint shown below the message (legacy, use nextAction instead) */
  actionHint?: string;
  className?: string;
  'data-testid'?: string;
}

/**
 * Renders a meaningful "surface unavailable" state for capability-gated routes
 * that are disabled or not provisioned in the current deployment profile.
 *
 * This component is the canonical fallback for RuntimeCapabilityRouteGate
 * to avoid silent 404s for legitimately unavailable features.
 */
export const DisabledSurfacePage = React.memo(function DisabledSurfacePage({
  surfaceName = 'This surface',
  surfaceDescription,
  status = 'DISABLED',
  dependencies = [],
  nextAction,
  remediationLink,
  actionHint,
  className,
  'data-testid': testId,
}: DisabledSurfacePageProps): React.ReactElement {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const getStatusIcon = () => {
    switch (status) {
      case 'DEGRADED':
        return <AlertCircle className="h-8 w-8 text-amber-600 dark:text-amber-400" />;
      case 'UNAVAILABLE':
        return <XCircle className="h-8 w-8 text-red-600 dark:text-red-400" />;
      case 'MISCONFIGURED':
        return <Settings className="h-8 w-8 text-purple-600 dark:text-purple-400" />;
      default:
        return <Lock className="h-8 w-8 text-amber-600 dark:text-amber-400" />;
    }
  };

  const getStatusColor = () => {
    switch (status) {
      case 'DEGRADED':
        return 'bg-amber-100 dark:bg-amber-900/30';
      case 'UNAVAILABLE':
        return 'bg-red-100 dark:bg-red-900/30';
      case 'MISCONFIGURED':
        return 'bg-purple-100 dark:bg-purple-900/30';
      default:
        return 'bg-amber-100 dark:bg-amber-900/30';
    }
  };

  const getStatusText = () => {
    switch (status) {
      case 'DEGRADED':
        return t('disabledSurface.degraded');
      case 'UNAVAILABLE':
        return t('disabledSurface.unavailable');
      case 'MISCONFIGURED':
        return t('disabledSurface.misconfigured');
      default:
        return t('disabledSurface.disabled');
    }
  };

  const getDependencyIcon = (depStatus: SurfaceDependency['status']) => {
    switch (depStatus) {
      case 'HEALTHY':
        return <CheckCircle className="h-4 w-4 text-green-600 dark:text-green-400" />;
      case 'DEGRADED':
        return <AlertCircle className="h-4 w-4 text-amber-600 dark:text-amber-400" />;
      case 'DOWN':
      case 'MISSING':
        return <XCircle className="h-4 w-4 text-red-600 dark:text-red-400" />;
    }
  };

  return (
    <div
      className={cn(
        'flex min-h-[60vh] flex-col items-center justify-center px-4 py-12 text-center',
        className
      )}
      role="status"
      aria-live="polite"
      data-testid={testId ?? 'disabled-surface-page'}
    >
      <div className="mx-auto max-w-md">
        <div className="flex justify-center">
          <span
            className={cn(
              'inline-flex h-16 w-16 items-center justify-center rounded-full',
              getStatusColor()
            )}
            aria-hidden="true"
          >
            {getStatusIcon()}
          </span>
        </div>

        <h1 className="mt-4 text-2xl font-semibold text-gray-900 dark:text-white">
          {surfaceName} {getStatusText()}
        </h1>

        {surfaceDescription && (
          <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
            {surfaceDescription}
          </p>
        )}

        <p className="mt-3 text-sm text-gray-500 dark:text-gray-500">
          {status === 'DISABLED' && t('disabledSurface.disabledMessage') + ' '}
          {status === 'DEGRADED' && t('disabledSurface.degradedMessage') + ' '}
          {status === 'UNAVAILABLE' && t('disabledSurface.unavailableMessage') + ' '}
          {status === 'MISCONFIGURED' && t('disabledSurface.misconfiguredMessage') + ' '}
          {nextAction || actionHint || t('disabledSurface.contactAdmin')}
        </p>

        {dependencies.length > 0 && (
          <div className="mt-6 rounded-lg bg-gray-50 dark:bg-gray-800 p-4 text-left">
            <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-3">
              {t('disabledSurface.affectedDependencies')}
            </h3>
            <ul className="space-y-2">
              {dependencies.map((dep, index) => (
                <li key={index} className="flex items-start gap-2 text-sm">
                  <span className="mt-0.5">{getDependencyIcon(dep.status)}</span>
                  <div>
                    <span className="font-medium text-gray-900 dark:text-white">{dep.name}</span>
                    <span className="text-gray-600 dark:text-gray-400 ml-2">({dep.status})</span>
                    {dep.description && (
                      <p className="text-xs text-gray-500 dark:text-gray-500 mt-0.5">
                        {dep.description}
                      </p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        )}

        {nextAction && (
          <div className="mt-4 rounded-lg bg-blue-50 dark:bg-blue-900/20 p-4">
            <h3 className="text-sm font-medium text-blue-900 dark:text-blue-300 mb-1">
              {t('disabledSurface.nextAction')}
            </h3>
            <p className="text-sm text-blue-800 dark:text-blue-400">{nextAction}</p>
          </div>
        )}

        {remediationLink && (
          <div className="mt-4">
            <a
              href={remediationLink}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-sm text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300"
            >
              <ExternalLink className="h-4 w-4" />
              {t('disabledSurface.viewRemediation')}
            </a>
          </div>
        )}

        <div className="mt-6 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-4 py-2',
              'text-sm font-medium text-gray-700 dark:text-gray-300',
              'bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600',
              'hover:bg-gray-50 dark:hover:bg-gray-700',
              'transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500'
            )}
          >
            {t('disabledSurface.goBack')}
          </button>
          <button
            type="button"
            onClick={() => navigate('/')}
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-4 py-2',
              'text-sm font-medium text-white',
              'bg-primary-600 hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-400',
              'transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500'
            )}
          >
            {t('disabledSurface.goToHome')}
          </button>
        </div>
      </div>
    </div>
  );
});

DisabledSurfacePage.displayName = 'DisabledSurfacePage';
