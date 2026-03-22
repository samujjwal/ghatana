/**
 * SecurityDashboard Component
 *
 * Compact security overview panel showing vulnerability counts, security score,
 * and recent open alerts. Designed to be embedded in project overviews and
 * composite dashboards.
 *
 * @doc.type component
 * @doc.purpose Embeddable security health summary widget
 * @doc.layer product
 * @doc.pattern UI Component
 */

import React from 'react';
import { useAtomValue } from 'jotai';
import { NavLink, useParams } from 'react-router';
import {
  Shield,
  AlertTriangle,
  XCircle,
  Info,
  ArrowRight,
  CheckCircle2,
} from 'lucide-react';

import { vulnerabilitiesAtom, securityScoreAtom, securityAlertsAtom } from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

interface SecurityDashboardProps {
  projectId?: string;
  className?: string;
}

// =============================================================================
// Score Ring
// =============================================================================

const ScoreRing: React.FC<{ score: number }> = ({ score }) => {
  const clamped = Math.max(0, Math.min(100, score));
  const radius = 28;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference - (clamped / 100) * circumference;

  const color =
    clamped >= 80
      ? '#34d399' // emerald
      : clamped >= 60
      ? '#fbbf24' // yellow
      : '#f87171'; // red

  return (
    <div className="relative w-20 h-20 flex items-center justify-center flex-shrink-0">
      <svg
        width="80"
        height="80"
        viewBox="0 0 72 72"
        className="-rotate-90"
        aria-hidden="true"
      >
        {/* Track */}
        <circle
          cx="36"
          cy="36"
          r={radius}
          fill="none"
          stroke="#27272a"
          strokeWidth="8"
        />
        {/* Progress */}
        <circle
          cx="36"
          cy="36"
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth="8"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={dashOffset}
          className="transition-all duration-700"
        />
      </svg>
      <span
        className="absolute text-lg font-bold"
        style={{ color }}
      >
        {clamped}
      </span>
    </div>
  );
};

// =============================================================================
// Severity Badge
// =============================================================================

const SEV_STYLES = {
  critical: { text: 'text-red-400', bg: 'bg-red-500/10', icon: XCircle },
  high: { text: 'text-orange-400', bg: 'bg-orange-500/10', icon: AlertTriangle },
  medium: { text: 'text-yellow-400', bg: 'bg-yellow-500/10', icon: AlertTriangle },
  low: { text: 'text-blue-400', bg: 'bg-blue-500/10', icon: Info },
} as const;

// =============================================================================
// SecurityDashboard Component
// =============================================================================

export const SecurityDashboard: React.FC<SecurityDashboardProps> = ({
  projectId,
  className = '',
}) => {
  const { projectId: paramProjectId } = useParams<{ projectId: string }>();
  const activeProjectId = projectId ?? paramProjectId;

  const vulnerabilities = useAtomValue(vulnerabilitiesAtom);
  const securityScore = useAtomValue(securityScoreAtom);
  const alerts = useAtomValue(securityAlertsAtom);

  const openVulns = vulnerabilities.filter((v) => v.status === 'open');
  const bySeverity = {
    critical: openVulns.filter((v) => v.severity === 'critical').length,
    high: openVulns.filter((v) => v.severity === 'high').length,
    medium: openVulns.filter((v) => v.severity === 'medium').length,
    low: openVulns.filter((v) => v.severity === 'low').length,
  };

  const recentAlerts = alerts
    .filter((a) => !a.resolvedAt)
    .slice(0, 4);

  const score = securityScore?.overall ?? 0;

  const securityPath = activeProjectId
    ? ROUTES.project.security(activeProjectId)
    : '#';

  return (
    <div className={`bg-zinc-900 rounded-xl border border-zinc-800 overflow-hidden ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-800">
        <div className="flex items-center gap-2">
          <Shield className="w-4 h-4 text-violet-400" />
          <h3 className="text-sm font-semibold text-white">Security Overview</h3>
        </div>
        <NavLink
          to={securityPath}
          className="flex items-center gap-1 text-xs text-violet-400 hover:text-violet-300 transition-colors"
        >
          View all
          <ArrowRight className="w-3 h-3" />
        </NavLink>
      </div>

      <div className="p-5 space-y-5">
        {/* Score + Vuln Counts */}
        <div className="flex items-center gap-5">
          {/* Score ring */}
          <div className="flex flex-col items-center gap-1">
            <ScoreRing score={score} />
            <span className="text-[10px] text-zinc-500">Security Score</span>
          </div>

          {/* Severity breakdown */}
          <div className="flex-1 grid grid-cols-2 gap-2">
            {(Object.entries(bySeverity) as [keyof typeof bySeverity, number][]).map(
              ([severity, count]) => {
                const cfg = SEV_STYLES[severity];
                const Icon = cfg.icon;
                return (
                  <div
                    key={severity}
                    className={`flex items-center gap-2 px-3 py-2 rounded-lg ${cfg.bg}`}
                  >
                    <Icon className={`w-3.5 h-3.5 ${cfg.text}`} />
                    <div>
                      <div className={`text-sm font-bold ${cfg.text}`}>{count}</div>
                      <div className="text-[10px] text-zinc-500 capitalize">{severity}</div>
                    </div>
                  </div>
                );
              }
            )}
          </div>
        </div>

        {/* Recent Alerts */}
        <div>
          <p className="text-xs font-medium text-zinc-500 uppercase tracking-wide mb-2">
            Open Alerts
          </p>
          {recentAlerts.length === 0 ? (
            <div className="flex items-center gap-2 py-3 text-xs text-zinc-500">
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              No open alerts
            </div>
          ) : (
            <ul className="space-y-1.5">
              {recentAlerts.map((alert) => {
                const sev = (alert.severity as keyof typeof SEV_STYLES) ?? 'medium';
                const cfg = SEV_STYLES[sev] ?? SEV_STYLES.medium;
                const Icon = cfg.icon;
                return (
                  <li
                    key={alert.id}
                    className="flex items-start gap-2 p-2 rounded-lg bg-zinc-800/60"
                  >
                    <Icon className={`w-3.5 h-3.5 mt-0.5 flex-shrink-0 ${cfg.text}`} />
                    <span className="text-xs text-zinc-300 line-clamp-1">
                      {alert.title ?? alert.message ?? 'Security alert'}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
};
