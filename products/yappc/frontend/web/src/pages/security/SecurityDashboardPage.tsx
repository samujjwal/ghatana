/**
 * Security Dashboard Page
 *
 * @description Security overview with vulnerability stats,
 * compliance status, and recent security alerts.
 */

// @ts-nocheck
import React from 'react';
import { NavLink, useParams } from 'react-router';
import { useAtomValue } from 'jotai';
import { motion } from 'framer-motion';
import {
  Shield,
  AlertTriangle,
  CheckCircle2,
  FileCheck,
  Eye,
  ArrowRight,
  TrendingUp,
  TrendingDown,
  AlertCircle,
  Key,
  FileText,
  Activity,
  Bug,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import {
  vulnerabilitiesAtom,
  complianceStatusAtom,
  securityScoreAtom,
  securityAlertsAtom,
} from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

interface SecurityMetric {
  label: string;
  value: string | number;
  change?: number;
  trend?: 'up' | 'down' | 'neutral';
  icon: React.ReactNode;
  href?: string;
}

// =============================================================================
// Security Dashboard Page Component
// =============================================================================

const SecurityDashboardPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const vulnerabilities = useAtomValue(vulnerabilitiesAtom);
  const compliance = useAtomValue(complianceStatusAtom);
  const securityScore = useAtomValue(securityScoreAtom);
  const alerts = useAtomValue(securityAlertsAtom);

  // Calculate stats
  const criticalVulns = vulnerabilities.filter((v) => v.severity === 'critical').length;
  const highVulns = vulnerabilities.filter((v) => v.severity === 'high').length;
  const openVulns = vulnerabilities.filter((v) => v.status === 'open').length;
  const unresolvedAlerts = alerts.filter((a) => !a.resolvedAt).length;

  const metrics: SecurityMetric[] = [
    {
      label: 'Security Score',
      value: securityScore?.overall ?? 0,
      change: 5,
      trend: 'up',
      icon: <Shield className="w-5 h-5" />,
    },
    {
      label: 'Open Vulnerabilities',
      value: openVulns,
      change: criticalVulns > 0 ? -15 : 10,
      trend: criticalVulns > 0 ? 'down' : 'up',
      icon: <Bug className="w-5 h-5" />,
      href: ROUTES.security.vulnerabilities(projectId || ''),
    },
    {
      label: 'Compliance',
      value: `${compliance?.overallScore || 0}%`,
      change: 3,
      trend: 'up',
      icon: <FileCheck className="w-5 h-5" />,
      href: ROUTES.security.compliance(projectId || ''),
    },
    {
      label: 'Active Alerts',
      value: unresolvedAlerts,
      trend: unresolvedAlerts > 0 ? 'down' : 'neutral',
      icon: <AlertCircle className="w-5 h-5" />,
      href: ROUTES.security.alerts(projectId || ''),
    },
  ];

  const quickLinks = [
    {
      label: 'Vulnerability Scanner',
      description: 'Run security scans',
      icon: <Bug className="w-5 h-5" />,
      href: ROUTES.security.scans(projectId || ''),
      color: 'bg-destructive-bg text-destructive',
    },
    {
      label: 'Compliance',
      description: 'View frameworks',
      icon: <FileCheck className="w-5 h-5" />,
      href: ROUTES.security.compliance(projectId || ''),
      color: 'bg-emerald-500/10 text-emerald-400',
    },
    {
      label: 'Secrets Manager',
      description: 'Manage credentials',
      icon: <Key className="w-5 h-5" />,
      href: ROUTES.security.secrets(projectId || ''),
      color: 'bg-warning-bg text-warning-color',
    },
    {
      label: 'Security Policies',
      description: 'Configure rules',
      icon: <Shield className="w-5 h-5" />,
      href: ROUTES.security.policies(projectId || ''),
      color: 'bg-violet-500/10 text-violet-400',
    },
    {
      label: 'Audit Logs',
      description: 'Activity history',
      icon: <FileText className="w-5 h-5" />,
      href: ROUTES.security.audit(projectId || ''),
      color: 'bg-info-bg text-info-color',
    },
    {
      label: 'Threat Model',
      description: 'Risk assessment',
      icon: <Activity className="w-5 h-5" />,
      href: ROUTES.security.threatModel(projectId || ''),
      color: 'bg-fuchsia-500/10 text-fuchsia-400',
    },
  ];

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white mb-2">Security</h1>
          <p className="text-fg-muted">Monitor vulnerabilities and compliance</p>
        </div>
        <div className="flex items-center gap-3">
          <NavLink
            to={ROUTES.security.scans(projectId || '')}
            className={cn(
              'flex items-center gap-2 px-4 py-2 rounded-lg font-medium',
              'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
            )}
          >
            <Eye className="w-4 h-4" />
            Run Scan
          </NavLink>
        </div>
      </div>

      {/* Critical Alert Banner */}
      {criticalVulns > 0 && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-6 p-4 rounded-xl bg-destructive-bg border border-destructive-border/30"
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="p-2 rounded-lg bg-destructive-bg">
                <AlertTriangle className="w-5 h-5 text-destructive" />
              </div>
              <div>
                <div className="font-medium text-white">
                  {criticalVulns} Critical Vulnerabilit{criticalVulns > 1 ? 'ies' : 'y'} Detected
                </div>
                <div className="text-sm text-fg-muted">
                  Immediate attention required
                </div>
              </div>
            </div>
            <NavLink
              to={ROUTES.security.vulnerabilities(projectId || '') + '?severity=critical'}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-destructive-bg text-white hover:bg-destructive-bg transition-colors"
            >
              Review Now
              <ArrowRight className="w-4 h-4" />
            </NavLink>
          </div>
        </motion.div>
      )}

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {metrics.map((metric) => {
          const content = (
            <div
              className={cn(
                'p-6 rounded-xl bg-surface border border-border',
                'hover:border-border transition-colors',
                metric.href && 'cursor-pointer'
              )}
            >
              <div className="flex items-start justify-between mb-4">
                <div className="p-2 rounded-lg bg-surface text-fg-muted">{metric.icon}</div>
                {metric.change !== undefined && (
                  <div
                    className={cn(
                      'flex items-center gap-1 text-sm',
                      metric.trend === 'up' && 'text-emerald-400',
                      metric.trend === 'down' && 'text-destructive',
                      metric.trend === 'neutral' && 'text-fg-muted'
                    )}
                  >
                    {metric.trend === 'up' && <TrendingUp className="w-4 h-4" />}
                    {metric.trend === 'down' && <TrendingDown className="w-4 h-4" />}
                    {metric.change > 0 ? '+' : ''}
                    {metric.change}%
                  </div>
                )}
              </div>
              <div className="text-3xl font-bold text-white mb-1">{metric.value}</div>
              <div className="text-sm text-fg-muted">{metric.label}</div>
            </div>
          );

          if (metric.href) {
            return (
              <NavLink key={metric.label} to={metric.href}>
                {content}
              </NavLink>
            );
          }

          return <div key={metric.label}>{content}</div>;
        })}
      </div>

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Vulnerability Summary */}
        <div className="lg:col-span-2">
          <div className="bg-surface border border-border rounded-xl p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-white">Vulnerability Summary</h2>
              <NavLink
                to={ROUTES.security.vulnerabilities(projectId || '')}
                className="text-sm text-violet-400 hover:text-violet-300 flex items-center gap-1"
              >
                View All
                <ArrowRight className="w-4 h-4" />
              </NavLink>
            </div>

            {/* Severity breakdown */}
            <div className="grid grid-cols-4 gap-4 mb-6">
              {[
                { label: 'Critical', count: criticalVulns, color: 'bg-destructive-bg' },
                { label: 'High', count: highVulns, color: 'bg-warning-bg' },
                {
                  label: 'Medium',
                  count: vulnerabilities.filter((v) => v.severity === 'medium').length,
                  color: 'bg-warning-bg',
                },
                {
                  label: 'Low',
                  count: vulnerabilities.filter((v) => v.severity === 'low').length,
                  color: 'bg-info-bg',
                },
              ].map((item) => (
                <div
                  key={item.label}
                  className="p-4 rounded-lg bg-surface/50 text-center"
                >
                  <div className={cn('w-3 h-3 rounded-full mx-auto mb-2', item.color)} />
                  <div className="text-2xl font-bold text-white">{item.count}</div>
                  <div className="text-xs text-fg-muted">{item.label}</div>
                </div>
              ))}
            </div>

            {/* Recent vulnerabilities */}
            <div className="space-y-3">
              {vulnerabilities.slice(0, 5).map((vuln) => (
                <NavLink
                  key={vuln.id}
                  to={ROUTES.security.vulnerabilities(projectId || '', vuln.id)}
                  className="flex items-center justify-between p-3 rounded-lg hover:bg-surface transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={cn(
                        'w-2 h-2 rounded-full',
                        vuln.severity === 'critical' && 'bg-destructive-bg',
                        vuln.severity === 'high' && 'bg-warning-bg',
                        vuln.severity === 'medium' && 'bg-warning-bg',
                        vuln.severity === 'low' && 'bg-info-bg'
                      )}
                    />
                    <div>
                      <div className="text-sm font-medium text-white">{vuln.title}</div>
                      <div className="text-xs text-fg-muted">
                        {vuln.cve || vuln.scanType} • {vuln.affectedComponent}
                      </div>
                    </div>
                  </div>
                  <span
                    className={cn(
                      'px-2 py-1 rounded text-xs font-medium',
                      vuln.status === 'open' && 'bg-destructive-bg text-destructive',
                      vuln.status === 'in-progress' && 'bg-warning-bg text-warning-color',
                      vuln.status === 'resolved' && 'bg-emerald-500/20 text-emerald-400'
                    )}
                  >
                    {vuln.status}
                  </span>
                </NavLink>
              ))}
            </div>
          </div>

          {/* Compliance Overview */}
          <div className="bg-surface border border-border rounded-xl p-6 mt-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-white">Compliance Status</h2>
              <NavLink
                to={ROUTES.security.compliance(projectId || '')}
                className="text-sm text-violet-400 hover:text-violet-300 flex items-center gap-1"
              >
                View Details
                <ArrowRight className="w-4 h-4" />
              </NavLink>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {(compliance?.frameworks || [
                { name: 'SOC 2', score: 92 },
                { name: 'GDPR', score: 88 },
                { name: 'HIPAA', score: 75 },
                { name: 'ISO 27001', score: 85 },
              ]).map((framework) => (
                <div
                  key={framework.name}
                  className="p-4 rounded-lg bg-surface/50 text-center"
                >
                  <div className="text-sm text-fg-muted mb-2">{framework.name}</div>
                  <div className="relative w-16 h-16 mx-auto">
                    <svg className="w-full h-full transform -rotate-90">
                      <circle
                        cx="32"
                        cy="32"
                        r="28"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="4"
                        className="text-fg"
                      />
                      <circle
                        cx="32"
                        cy="32"
                        r="28"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="4"
                        strokeDasharray={`${(framework.score / 100) * 176} 176`}
                        strokeLinecap="round"
                        className={cn(
                          framework.score >= 90
                            ? 'text-emerald-500'
                            : framework.score >= 70
                            ? 'text-warning-color'
                            : 'text-destructive'
                        )}
                      />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                      <span className="text-lg font-bold text-white">{framework.score}%</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Quick Links */}
          <div className="bg-surface border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">Quick Access</h2>
            <div className="space-y-2">
              {quickLinks.map((link) => (
                <NavLink
                  key={link.label}
                  to={link.href}
                  className="flex items-center gap-3 p-3 rounded-lg hover:bg-surface transition-colors group"
                >
                  <div className={cn('p-2 rounded-lg', link.color)}>{link.icon}</div>
                  <div className="flex-1">
                    <div className="text-sm font-medium text-white">{link.label}</div>
                    <div className="text-xs text-fg-muted">{link.description}</div>
                  </div>
                  <ArrowRight className="w-4 h-4 text-fg-muted group-hover:text-fg-muted transition-colors" />
                </NavLink>
              ))}
            </div>
          </div>

          {/* Recent Alerts */}
          <div className="bg-surface border border-border rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-white">Security Alerts</h2>
              <NavLink
                to={ROUTES.security.alerts(projectId || '')}
                className="text-sm text-violet-400 hover:text-violet-300"
              >
                View All
              </NavLink>
            </div>
            <div className="space-y-3">
              {alerts.slice(0, 4).map((alert) => (
                <div
                  key={alert.id}
                  className="p-3 rounded-lg bg-surface/50 hover:bg-surface transition-colors"
                >
                  <div className="flex items-start gap-3">
                    <div
                      className={cn(
                        'p-1.5 rounded',
                        alert.severity === 'critical' && 'bg-destructive-bg text-destructive',
                        alert.severity === 'high' && 'bg-warning-bg text-warning-color',
                        alert.severity === 'medium' && 'bg-warning-bg text-warning-color',
                        alert.severity === 'low' && 'bg-info-bg text-info-color'
                      )}
                    >
                      <AlertTriangle className="w-3 h-3" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-white truncate">
                        {alert.title}
                      </div>
                      <div className="text-xs text-fg-muted mt-0.5">
                        {alert.triggeredAt || 'Recently'}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
              {alerts.length === 0 && (
                <div className="text-center py-6">
                  <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto mb-2" />
                  <p className="text-sm text-fg-muted">No active alerts</p>
                </div>
              )}
            </div>
          </div>

          {/* Security Score Breakdown */}
          <div className="bg-surface border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">Score Breakdown</h2>
            <div className="space-y-4">
              {[
                { label: 'Code Security', score: 85 },
                { label: 'Dependencies', score: 72 },
                { label: 'Infrastructure', score: 90 },
                { label: 'Access Control', score: 95 },
              ].map((item) => (
                <div key={item.label}>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm text-fg-muted">{item.label}</span>
                    <span className="text-sm font-medium text-white">{item.score}%</span>
                  </div>
                  <div className="h-2 bg-surface rounded-full overflow-hidden">
                    <div
                      className={cn(
                        'h-full rounded-full transition-all',
                        item.score >= 90
                          ? 'bg-emerald-500'
                          : item.score >= 70
                          ? 'bg-warning-bg'
                          : 'bg-destructive-bg'
                      )}
                      style={{ width: `${item.score}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SecurityDashboardPage;
