/**
 * Trust Center Page
 *
 * Simplified governance and compliance page.
 * Replaces complex GovernancePage with AI-assisted policy management.
 *
 * Features:
 * - One-command policy application
 * - Visual compliance status
 * - AI-suggested policies
 * - Audit trail
 *
 * @doc.type page
 * @doc.purpose Simplified governance and compliance
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Shield,
  CheckCircle,
  AlertTriangle,
  Lock,
  Eye,
  FileText,
  Clock,
  ChevronRight,
  Plus,
  Sparkles,
  Search,
  Filter,
  MoreVertical,
  RefreshCw,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { CommandBar, CommandBarTrigger, AmbientIntelligenceBar } from '../components/core';

/**
 * Compliance status
 */
type ComplianceStatus = 'compliant' | 'warning' | 'non-compliant' | 'pending';

/**
 * Policy interface
 */
interface Policy {
  id: string;
  name: string;
  description: string;
  type: 'GDPR' | 'HIPAA' | 'SOC2' | 'PCI' | 'CUSTOM';
  status: ComplianceStatus;
  appliedTo: number; // number of resources
  lastChecked: string;
  aiSuggested?: boolean;
}

/**
 * Audit event interface
 */
interface AuditEvent {
  id: string;
  action: string;
  resource: string;
  user: string;
  timestamp: string;
  status: 'success' | 'failed' | 'pending';
}

/**
 * Status badge configurations
 */
const STATUS_CONFIG: Record<
  ComplianceStatus,
  { icon: React.ReactNode; color: string; label: string }
> = {
  compliant: {
    icon: <CheckCircle className="h-4 w-4" />,
    color: 'text-green-600 bg-green-100 dark:bg-green-900/30 dark:text-green-400',
    label: 'Compliant',
  },
  warning: {
    icon: <AlertTriangle className="h-4 w-4" />,
    color: 'text-amber-600 bg-amber-100 dark:bg-amber-900/30 dark:text-amber-400',
    label: 'Warning',
  },
  'non-compliant': {
    icon: <AlertTriangle className="h-4 w-4" />,
    color: 'text-red-600 bg-red-100 dark:bg-red-900/30 dark:text-red-400',
    label: 'Non-Compliant',
  },
  pending: {
    icon: <Clock className="h-4 w-4" />,
    color: 'text-gray-600 bg-gray-100 dark:bg-gray-800 dark:text-gray-400',
    label: 'Pending',
  },
};

/**
 * Policy Type Badges
 */
const POLICY_TYPE_COLORS: Record<string, string> = {
  GDPR: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  HIPAA: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  SOC2: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  PCI: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  CUSTOM: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
};

/**
 * Compliance Score Card
 */
function ComplianceScoreCard({
  score,
  total,
  compliant,
  warnings,
}: {
  score: number;
  total: number;
  compliant: number;
  warnings: number;
}) {
  return (
    <div className="bg-gradient-to-br from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 border border-green-200 dark:border-green-800 rounded-2xl p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500 mb-1">Overall Compliance Score</p>
          <div className="flex items-baseline gap-2">
            <span className="text-4xl font-bold text-green-600 dark:text-green-400">
              {score}%
            </span>
            <span className="text-sm text-gray-500">
              {compliant}/{total} policies compliant
            </span>
          </div>
        </div>
        <div className="flex items-center justify-center w-20 h-20 rounded-full bg-white dark:bg-gray-800 shadow-lg">
          <Shield className="h-10 w-10 text-green-500" />
        </div>
      </div>
      {warnings > 0 && (
        <div className="mt-4 pt-4 border-t border-green-200 dark:border-green-800">
          <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
            <AlertTriangle className="h-4 w-4" />
            <span className="text-sm">{warnings} warning{warnings > 1 ? 's' : ''} need attention</span>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Policy Card
 */
function PolicyCard({ policy, onApply }: { policy: Policy; onApply: () => void }) {
  const statusConfig = STATUS_CONFIG[policy.status];

  return (
    <div
      className={cn(
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-xl p-4',
        'hover:shadow-md transition-shadow'
      )}
    >
      <div className="flex items-start gap-4">
        <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
          <Shield className="h-5 w-5 text-gray-600 dark:text-gray-400" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="font-medium text-gray-900 dark:text-gray-100">
              {policy.name}
            </h3>
            {policy.aiSuggested && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs rounded">
                <Sparkles className="h-3 w-3" />
                AI Suggested
              </span>
            )}
          </div>
          <p className="text-sm text-gray-500 mb-2">{policy.description}</p>
          <div className="flex items-center gap-3">
            <span
              className={cn('px-2 py-0.5 rounded text-xs font-medium', POLICY_TYPE_COLORS[policy.type])}
            >
              {policy.type}
            </span>
            <span className="text-xs text-gray-400">
              Applied to {policy.appliedTo} resources
            </span>
            <span className="text-xs text-gray-400">
              Checked {policy.lastChecked}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={cn(
              'inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium',
              statusConfig.color
            )}
          >
            {statusConfig.icon}
            {statusConfig.label}
          </span>
          <button
            onClick={onApply}
            className="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
          >
            <MoreVertical className="h-4 w-4 text-gray-400" />
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Quick Apply Command Card
 */
function QuickApplyCard({
  title,
  description,
  icon,
  onClick,
}: {
  title: string;
  description: string;
  icon: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex items-center gap-3 p-4',
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-xl text-left',
        'hover:border-primary-300 dark:hover:border-primary-700',
        'hover:shadow-md transition-all'
      )}
    >
      <div className="p-2 bg-primary-100 dark:bg-primary-900/30 rounded-lg">
        {icon}
      </div>
      <div className="flex-1">
        <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100">
          {title}
        </h3>
        <p className="text-xs text-gray-500">{description}</p>
      </div>
      <ChevronRight className="h-5 w-5 text-gray-300" />
    </button>
  );
}

/**
 * Audit Log Item
 */
function AuditLogItem({ event }: { event: AuditEvent }) {
  const statusColors = {
    success: 'text-green-500',
    failed: 'text-red-500',
    pending: 'text-gray-400',
  };

  return (
    <div className="flex items-center gap-3 py-2">
      {event.status === 'success' ? (
        <CheckCircle className={cn('h-4 w-4', statusColors[event.status])} />
      ) : event.status === 'failed' ? (
        <AlertTriangle className={cn('h-4 w-4', statusColors[event.status])} />
      ) : (
        <Clock className={cn('h-4 w-4', statusColors[event.status])} />
      )}
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-900 dark:text-gray-100 truncate">
          {event.action}
        </p>
        <p className="text-xs text-gray-500">
          {event.resource} • {event.user}
        </p>
      </div>
      <span className="text-xs text-gray-400">{event.timestamp}</span>
    </div>
  );
}

/**
 * Trust Center Page
 */
export function TrustCenter() {
  const [searchQuery, setSearchQuery] = useState('');

  // Mock policies
  const policies: Policy[] = [
    {
      id: '1',
      name: 'GDPR Data Protection',
      description: 'Personal data handling and retention policies',
      type: 'GDPR',
      status: 'compliant',
      appliedTo: 12,
      lastChecked: '2h ago',
    },
    {
      id: '2',
      name: 'PII Masking',
      description: 'Automatic PII detection and masking',
      type: 'CUSTOM',
      status: 'compliant',
      appliedTo: 8,
      lastChecked: '1h ago',
      aiSuggested: true,
    },
    {
      id: '3',
      name: 'Access Control',
      description: 'Role-based access control policies',
      type: 'SOC2',
      status: 'warning',
      appliedTo: 24,
      lastChecked: '30m ago',
    },
    {
      id: '4',
      name: 'Data Encryption',
      description: 'Encryption at rest and in transit',
      type: 'PCI',
      status: 'compliant',
      appliedTo: 18,
      lastChecked: '1h ago',
    },
  ];

  // Mock audit events
  const auditEvents: AuditEvent[] = [
    {
      id: '1',
      action: 'Policy applied: GDPR Data Protection',
      resource: 'customer_events',
      user: 'system',
      timestamp: '5m ago',
      status: 'success',
    },
    {
      id: '2',
      action: 'Access granted to role: analyst',
      resource: 'orders',
      user: 'admin@example.com',
      timestamp: '1h ago',
      status: 'success',
    },
    {
      id: '3',
      action: 'PII scan completed',
      resource: 'user_profiles',
      user: 'system',
      timestamp: '2h ago',
      status: 'success',
    },
    {
      id: '4',
      action: 'Policy violation detected',
      resource: 'raw_logs',
      user: 'system',
      timestamp: '3h ago',
      status: 'failed',
    },
  ];

  // Calculate compliance stats
  const compliantCount = policies.filter((p) => p.status === 'compliant').length;
  const warningCount = policies.filter((p) => p.status === 'warning').length;
  const complianceScore = Math.round((compliantCount / policies.length) * 100);

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <header className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              Trust Center
            </h1>
            <p className="text-sm text-gray-500 mt-0.5">
              Governance, compliance, and data protection
            </p>
          </div>
          <div className="flex items-center gap-3">
            <CommandBarTrigger />
            <button
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg',
                'bg-primary-600 hover:bg-primary-700',
                'text-white text-sm font-medium',
                'transition-colors'
              )}
            >
              <Plus className="h-4 w-4" />
              Add Policy
            </button>
          </div>
        </div>

        {/* Search */}
        <div className="flex items-center gap-2">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder='Search policies or try "Apply GDPR to all collections"'
              className={cn(
                'w-full pl-9 pr-4 py-2 rounded-lg',
                'bg-gray-100 dark:bg-gray-800',
                'border border-transparent focus:border-primary-500',
                'text-sm text-gray-900 dark:text-gray-100',
                'placeholder-gray-400',
                'outline-none transition-colors'
              )}
            />
          </div>
          <button className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg">
            <Filter className="h-4 w-4 text-gray-400" />
          </button>
          <button className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg">
            <RefreshCw className="h-4 w-4 text-gray-400" />
          </button>
        </div>
      </header>

      {/* Content */}
      <main className="flex-1 overflow-y-auto p-6">
        {/* Compliance Score */}
        <section className="mb-8">
          <ComplianceScoreCard
            score={complianceScore}
            total={policies.length}
            compliant={compliantCount}
            warnings={warningCount}
          />
        </section>

        {/* Quick Apply */}
        <section className="mb-8">
          <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-4">
            Quick Apply
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <QuickApplyCard
              title="Apply GDPR"
              description="Apply to all collections"
              icon={<Shield className="h-5 w-5 text-blue-600" />}
              onClick={() => alert('GDPR policy applied!')}
            />
            <QuickApplyCard
              title="Enable PII Masking"
              description="Auto-detect and mask PII"
              icon={<Eye className="h-5 w-5 text-purple-600" />}
              onClick={() => alert('PII masking enabled!')}
            />
            <QuickApplyCard
              title="Run Compliance Scan"
              description="Check all resources"
              icon={<FileText className="h-5 w-5 text-green-600" />}
              onClick={() => alert('Scan started!')}
            />
            <QuickApplyCard
              title="Configure Access"
              description="Set up RBAC policies"
              icon={<Lock className="h-5 w-5 text-amber-600" />}
              onClick={() => alert('Access configuration opened!')}
            />
          </div>
        </section>

        {/* Two Column Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Policies */}
          <section className="lg:col-span-2">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
                Active Policies
              </h2>
              <button className="text-xs text-primary-600 hover:underline">
                View all
              </button>
            </div>
            <div className="space-y-4">
              {policies.map((policy) => (
                <PolicyCard
                  key={policy.id}
                  policy={policy}
                  onApply={() => {}}
                />
              ))}
            </div>
          </section>

          {/* Audit Log */}
          <section>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
                Audit Log
              </h2>
              <button className="text-xs text-primary-600 hover:underline">
                View all
              </button>
            </div>
            <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
              <div className="divide-y divide-gray-100 dark:divide-gray-700">
                {auditEvents.map((event) => (
                  <AuditLogItem key={event.id} event={event} />
                ))}
              </div>
            </div>
          </section>
        </div>
      </main>

      {/* Ambient Intelligence Bar */}
      <AmbientIntelligenceBar />

      {/* Command Bar Modal */}
      <CommandBar />
    </div>
  );
}

export default TrustCenter;

