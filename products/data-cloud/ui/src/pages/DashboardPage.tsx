/**
 * Dashboard Page
 * 
 * Enhanced dashboard with comprehensive overview of collections, workflows, and executions.
 * Features real-time data visualization, quick actions, and system health monitoring.
 *
 * @doc.type page
 * @doc.purpose Main dashboard with enhanced UI/UX
 * @doc.layer frontend
 */

import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { dataCloudApi, type Execution } from '../lib/api/data-cloud-api';
import type { Collection } from '../lib/api/collections';
import type { Workflow } from '../lib/api/workflows';
import { governanceService, type AuditLog as GovernanceAuditLog } from '../api/governance.service';
import {
  Database,
  Workflow as WorkflowIcon,
  ListChecks,
  BookOpen,
  PlusCircle,
  Clock,
  CheckCircle,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  FileText,
  Shield,
  BarChart2,
  Server,
  Users,
  Settings,
  Bell,
  Plus,
  ArrowRight,
  Info
} from 'lucide-react';
import { DashboardCard, DashboardKPI } from '../components/cards/DashboardCard';

// Import our new components
import { EmptyState } from '../components/common/EmptyState';

interface AuditLog {
  id: string;
  action: string;
  entityType: 'workflow' | 'collection' | 'user' | 'system';
  entityId: string;
  entityName: string;
  userId: string;
  userName: string;
  timestamp: string;
  status: 'success' | 'failed' | 'warning';
  details?: string;
  ipAddress?: string;
}

interface ComplianceStatus {
  id: string;
  name: string;
  status: 'compliant' | 'non-compliant' | 'warning' | 'not-applicable';
  lastChecked: string;
  nextCheck: string;
  details?: string;
}

interface DashboardState {
  collections: Collection[];
  workflows: Workflow[];
  executions: Execution[];
  auditLogs: AuditLog[];
  complianceStatuses: ComplianceStatus[];
  loading: boolean;
  error: string | null;
  stats: {
    totalWorkflows: number;
    activeWorkflows: number;
    totalExecutions: number;
    successRate: number;
    avgExecutionTime: string;
    auditEvents24h: number;
    complianceScore: number;
  };
  recentActivity: Array<{
    id: string;
    title: string;
    description: string;
    status: 'success' | 'error' | 'warning' | 'info';
    timestamp: string;
    link?: string;
  }>;
}

/**
 * Dashboard Page Component
 *
 * @returns JSX element
 */
// Format time duration
const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
};

export function DashboardPage(): React.ReactElement {
  const [state, setState] = useState<DashboardState>({
    collections: [],
    workflows: [],
    executions: [],
    auditLogs: [],
    complianceStatuses: [],
    loading: true,
    error: null,
    stats: {
      totalWorkflows: 0,
      activeWorkflows: 0,
      totalExecutions: 0,
      successRate: 0,
      avgExecutionTime: '0s',
      auditEvents24h: 0,
      complianceScore: 0,
    },
    recentActivity: [],
  });

  const { loading, error, stats, recentActivity } = state;

  useEffect(() => {
    loadDashboardData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadDashboardData = async () => {
    try {
      setState((prev) => ({ ...prev, loading: true, error: null }));

      // Fetch collections, workflows, audit logs, and compliance report in parallel
      const [collectionsRes, workflowsRes, auditLogsData, complianceReport] = await Promise.all([
        dataCloudApi.getCollections(),
        dataCloudApi.getWorkflows(),
        governanceService.getAuditLogs(undefined, undefined, 10).catch(() => [] as GovernanceAuditLog[]),
        governanceService.getComplianceReport().catch(() => null),
      ]);

      // Get executions for each workflow
      const executionsPromises = workflowsRes.data.map((workflow: Workflow) =>
        dataCloudApi.getWorkflowExecutions(workflow.id).catch(() => ({ data: { items: [] } }))
      );

      const executionsResults = await Promise.all(executionsPromises);
      const allExecutions = executionsResults.flatMap(res => res.data.items || []);

      const activeWorkflows = workflowsRes.data.filter((w) => w.status === 'active').length;
      const totalExecutions = allExecutions.length;

      const successRate = allExecutions.length > 0
        ? (allExecutions.filter((e) => e.status === 'completed').length / allExecutions.length) * 100
        : 0;

      const avgExecutionTime = allExecutions.length > 0
        ? formatDuration(
          allExecutions
            .map((e) => e.duration || 0)
            .reduce((a: number, b: number) => a + b, 0) / allExecutions.length
        )
        : '0s';

      // Map governance audit logs to local AuditLog type
      const auditLogs: AuditLog[] = auditLogsData.map((log) => ({
        id: log.id,
        action: log.action,
        entityType: (log.resourceType?.toLowerCase() as AuditLog['entityType']) || 'system',
        entityId: log.resourceId || '',
        entityName: log.resourceId || log.action,
        userId: log.userId,
        userName: log.userName,
        timestamp: log.timestamp,
        status: log.outcome === 'SUCCESS' ? 'success' : log.outcome === 'BLOCKED' ? 'failed' : 'failed',
        details: typeof log.details === 'string' ? log.details : undefined,
      }));

      // Derive compliance status entries from the compliance report
      const now = new Date().toISOString();
      const complianceStatuses: ComplianceStatus[] = complianceReport
        ? [
            {
              id: 'pii-scan',
              name: 'PII Compliance',
              status: (complianceReport.details.piiScans.violations ?? 0) > 0 ? 'non-compliant' : 'compliant',
              lastChecked: complianceReport.generatedAt,
              nextCheck: now,
            },
            {
              id: 'access-audit',
              name: 'Access Control',
              status: (complianceReport.details.accessAudits.unauthorizedAttempts ?? 0) > 0 ? 'warning' : 'compliant',
              lastChecked: complianceReport.generatedAt,
              nextCheck: now,
            },
            {
              id: 'data-retention',
              name: 'Data Retention',
              status: (complianceReport.details.retentionCompliance.datasetsViolating ?? 0) > 0 ? 'non-compliant' : 'compliant',
              lastChecked: complianceReport.generatedAt,
              nextCheck: now,
            },
          ]
        : [];

      const complianceScore = complianceReport?.summary.complianceScore ?? 0;

      // Derive recent activity from real executions
      const recentActivity = allExecutions
        .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())
        .slice(0, 5)
        .map((e) => ({
          id: e.id,
          title: e.status === 'completed' ? 'Workflow completed' : e.status === 'failed' ? 'Workflow failed' : 'Workflow running',
          description: `Workflow execution ${e.id.slice(0, 8)}… ${e.status}`,
          status: (e.status === 'completed' ? 'success' : e.status === 'failed' ? 'error' : 'info') as 'success' | 'error' | 'warning' | 'info',
          timestamp: e.startedAt,
          link: `/executions/${e.id}`,
        }));

      setState((prev) => ({
        ...prev,
        collections: collectionsRes.data,
        workflows: workflowsRes.data,
        executions: allExecutions,
        auditLogs,
        complianceStatuses,
        stats: {
          totalWorkflows: workflowsRes.data.length,
          activeWorkflows,
          totalExecutions,
          successRate: parseFloat(successRate.toFixed(1)),
          avgExecutionTime,
          auditEvents24h: auditLogs.length,
          complianceScore,
        },
        recentActivity,
        loading: false,
      }));
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
      setState((prev) => ({
        ...prev,
        error: err instanceof Error ? err.message : 'Failed to load dashboard',
        loading: false,
      }));
    }
  };

  if (state.loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="text-center">
          <div className="inline-block">
            <div className="w-16 h-16 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
          </div>
          <h2 className="mt-6 text-2xl font-semibold text-gray-800">Loading Dashboard</h2>
          <p className="mt-2 text-gray-500">Gathering your data...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex justify-between items-center">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
              <p className="mt-1 text-sm text-gray-500">Overview of your workflows and executions</p>
            </div>
            <div className="flex space-x-3">
              <Link
                to="/workflows/new"
                className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
              >
                <Plus className="-ml-1 mr-2 h-5 w-5" />
                New Workflow
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {state.error && (
          <div className="bg-red-50 border-l-4 border-red-400 p-4 mb-6">
            <div className="flex">
              <div className="flex-shrink-0">
                <XCircle className="h-5 w-5 text-red-400" aria-hidden="true" />
              </div>
              <div className="ml-3">
                <p className="text-sm text-red-700">{state.error}</p>
              </div>
            </div>
          </div>
        )}

        {/* Stats Grid */}
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 mb-8">
          <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
            <Link to="/workflows" className="block h-full">
              <div className="px-4 py-5 sm:p-6 hover:bg-gray-50 transition-colors duration-150 h-full">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-primary-500 rounded-md p-3">
                    <WorkflowIcon className="h-6 w-6 text-white" />
                  </div>
                  <div className="ml-5 w-0 flex-1">
                    <dt className="text-sm font-medium text-gray-500 truncate">
                      Total Workflows
                    </dt>
                    <dd className="flex items-baseline">
                      <div className="text-2xl font-semibold text-gray-900">
                        {state.stats.totalWorkflows}
                      </div>
                    </dd>
                  </div>
                </div>
              </div>
            </Link>
          </div>

          <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
            <Link to="/workflows?status=active" className="block h-full">
              <div className="px-4 py-5 sm:p-6 hover:bg-gray-50 transition-colors duration-150 h-full">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-green-500 rounded-md p-3">
                    <Clock className="h-6 w-6 text-white" />
                  </div>
                  <div className="ml-5 w-0 flex-1">
                    <dt className="text-sm font-medium text-gray-500 truncate">
                      Active Workflows
                    </dt>
                    <dd className="flex items-baseline">
                      <div className="text-2xl font-semibold text-gray-900">
                        {state.stats.activeWorkflows}
                      </div>
                    </dd>
                  </div>
                </div>
              </div>
            </Link>
          </div>

          <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
            <Link to="/executions" className="block h-full">
              <div className="px-4 py-5 sm:p-6 hover:bg-gray-50 transition-colors duration-150 h-full">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-indigo-500 rounded-md p-3">
                    <ListChecks className="h-6 w-6 text-white" />
                  </div>
                  <div className="ml-5 w-0 flex-1">
                    <dt className="text-sm font-medium text-gray-500 truncate">
                      Total Executions
                    </dt>
                    <dd className="flex items-baseline">
                      <div className="text-2xl font-semibold text-gray-900">
                        {state.stats.totalExecutions}
                      </div>
                    </dd>
                  </div>
                </div>
              </div>
            </Link>
          </div>

          <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
            <Link to="/executions?status=completed" className="block h-full">
              <div className="px-4 py-5 sm:p-6 hover:bg-gray-50 transition-colors duration-150 h-full">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-green-100 rounded-md p-3">
                    <CheckCircle className="h-6 w-6 text-green-600" />
                  </div>
                  <div className="ml-5 w-0 flex-1">
                    <dt className="text-sm font-medium text-gray-500 truncate">
                      Success Rate
                    </dt>
                    <dd className="flex items-baseline">
                      <div className="text-2xl font-semibold text-gray-900">
                        {state.stats.successRate}%
                      </div>
                    </dd>
                  </div>
                </div>
              </div>
            </Link>
          </div>

          <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
            <Link to="/executions" className="block h-full">
              <div className="px-4 py-5 sm:p-6 hover:bg-gray-50 transition-colors duration-150 h-full">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-purple-100 rounded-md p-3">
                    <Clock className="h-6 w-6 text-purple-600" />
                  </div>
                  <div className="ml-5 w-0 flex-1">
                    <dt className="text-sm font-medium text-gray-500 truncate">
                      Avg. Execution Time
                    </dt>
                    <dd className="flex items-baseline">
                      <div className="text-2xl font-semibold text-gray-900">
                        {state.stats.avgExecutionTime}
                      </div>
                    </dd>
                  </div>
                </div>
              </div>
            </Link>
          </div>
        </div>

        {/* Collections and Workflows */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
          {/* Collections Section */}
          <DashboardCard
            title="Collections"
            viewAllLink="/collections"
          >
            <DashboardKPI
              icon={
                <div className="p-3 rounded-full bg-indigo-100">
                  <Database className="h-6 w-6 text-indigo-600" />
                </div>
              }
              title="Total Collections"
              value={state.collections.length}
              secondaryValue={
                state.collections[0]
                  ? new Date(state.collections[0].updatedAt || Date.now()).toLocaleDateString()
                  : 'N/A'
              }
              link="/collections"
            />
            <div className="space-y-3 mt-6">
              <h3 className="text-sm font-medium text-gray-700">Recent Collections</h3>
              {state.collections.slice(0, 3).map((collection) => (
                <Link
                  key={collection.id}
                  to={`/collections/${collection.id}`}
                  className="block group hover:bg-gray-50 -mx-3 px-3 py-2 rounded-md transition-colors duration-150"
                >
                  <div className="flex items-center">
                    <div className="flex-shrink-0 bg-indigo-100 p-2 rounded-md">
                      <Database className="h-4 w-4 text-indigo-600" />
                    </div>
                    <div className="ml-3 flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate group-hover:text-primary-600">
                        {collection.name}
                      </p>
                      <p className="text-xs text-gray-500">
                        {collection.entityCount} entities • Updated {new Date(collection.updatedAt || Date.now()).toLocaleDateString()}
                      </p>
                    </div>
                    <div className="ml-4 flex-shrink-0 flex">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${collection.status === 'active'
                          ? 'bg-green-100 text-green-800 group-hover:bg-green-50'
                          : 'bg-gray-100 text-gray-800 group-hover:bg-gray-50'
                        }`}>
                        {collection.status || 'Inactive'}
                      </span>
                      <ArrowRight className="ml-2 h-4 w-4 text-gray-400 group-hover:text-primary-500" />
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          </DashboardCard>

          {/* Workflows Section */}
          <DashboardCard
            title="Workflows"
            viewAllLink="/workflows"
          >
            <div>
              <DashboardKPI
                icon={
                  <div className="p-3 rounded-full bg-blue-100">
                    <WorkflowIcon className="h-6 w-6 text-blue-600" />
                  </div>
                }
                title="Active Workflows"
                value={`${state.workflows.filter((w) => w.status === 'active').length} / ${state.workflows.length}`}
                secondaryValue={
                  state.workflows[0]?.lastExecutedAt
                    ? new Date(state.workflows[0].lastExecutedAt).toLocaleString()
                    : 'N/A'
                }
                link="/workflows"
              />
              <div className="space-y-3 mt-6">
                <h3 className="text-sm font-medium text-gray-700">Recent Activities</h3>
                {state.workflows.slice(0, 3).map((workflow) => {
                  const lastExecution = state.executions
                    .filter((e) => e.workflowId === workflow.id)
                    .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())[0];

                  return (
                    <Link
                      key={workflow.id}
                      to={`/workflows/${workflow.id}`}
                      className="block group hover:bg-gray-50 -mx-6 px-6 py-2 transition-colors duration-150"
                    >
                      <div className="flex items-center">
                        <div className="flex-shrink-0 bg-blue-100 p-2 rounded-md">
                          <WorkflowIcon className="h-4 w-4 text-blue-600" />
                        </div>
                        <div className="ml-3 flex-1 min-w-0">
                          <p className="text-sm font-medium text-gray-900 truncate group-hover:text-primary-600">
                            {workflow.name}
                          </p>
                          <p className="text-xs text-gray-500">
                            {lastExecution ? (
                              <>
                                Last run {new Date(lastExecution.startedAt).toLocaleDateString()} •{' '}
                                <span className={`${lastExecution.status === 'completed' ? 'text-green-600' :
                                    lastExecution.status === 'failed' ? 'text-red-600' : 'text-yellow-600'
                                  }`}>
                                  {lastExecution.status}
                                </span>
                              </>
                            ) : (
                              <span>Not run yet</span>
                            )}
                          </p>
                        </div>
                        <ArrowRight className="ml-2 h-4 w-4 text-gray-400 group-hover:text-primary-500 flex-shrink-0" />
                      </div>
                    </Link>
                  );
                })}
              </div>

              <div className="mt-6">
                <h3 className="text-sm font-medium text-gray-700 mb-3">Recent Executions</h3>
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b bg-gray-50">
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-700">
                          Workflow
                        </th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-700">
                          Status
                        </th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-700">
                          Duration
                        </th>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-700">
                          Time
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {state.workflows
                        .filter((w) => w.lastExecutedAt)
                        .slice(0, 3)
                        .map((workflow) => (
                          <tr key={workflow.id} className="border-b hover:bg-gray-50">
                            <td className="px-6 py-4">
                              <p className="font-medium text-gray-900">{workflow.name}</p>
                            </td>
                            <td className="px-6 py-4">
                              <span className="px-2 py-1 rounded text-xs font-medium bg-green-100 text-green-800">
                                Completed
                              </span>
                            </td>
                            <td className="px-6 py-4 text-sm text-gray-600">~150ms</td>
                            <td className="px-6 py-4 text-sm text-gray-600">
                              {workflow.lastExecutedAt
                                ? new Date(workflow.lastExecutedAt).toLocaleString()
                                : 'N/A'}
                            </td>
                          </tr>
                        ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </DashboardCard>
        </div>
        {/* Audit & Compliance Overview */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
          {/* Audit Logs Summary */}
          <DashboardCard
            title="Audit Logs"
            viewAllLink="/audit"
          >
            <DashboardKPI
              icon={
                <div className="p-3 rounded-full bg-purple-100">
                  <FileText className="h-6 w-6 text-purple-600" />
                </div>
              }
              title="Last 24h"
              value={state.stats.auditEvents24h}
              secondaryValue={
                state.auditLogs[0]
                  ? new Date(state.auditLogs[0].timestamp).toLocaleString()
                  : 'N/A'
              }
              link="/audit"
            />
            <div className="space-y-4 mt-6">
              <h3 className="text-sm font-medium text-gray-700">Recent Activities</h3>
              {state.auditLogs.slice(0, 3).map((log) => {
                // Generate appropriate link based on entity type
                let link = '/audit';
                if (log.entityType === 'workflow') {
                  link = `/workflows/${log.entityId}`;
                } else if (log.entityType === 'collection') {
                  link = `/collections/${log.entityId}`;
                } else if (log.entityType === 'user') {
                  link = `/users/${log.userId}`;
                }

                return (
                  <Link
                    key={log.id}
                    to={link}
                    className="block hover:bg-gray-50 -mx-6 px-6 py-2 transition-colors duration-150"
                  >
                    <div className="flex items-start">
                      <div className={`flex-shrink-0 mt-1 ${log.status === 'success' ? 'text-green-500' :
                          log.status === 'warning' ? 'text-yellow-500' : 'text-red-500'
                        }`}>
                        {log.status === 'success' ? (
                          <CheckCircle2 className="h-4 w-4" />
                        ) : log.status === 'warning' ? (
                          <AlertTriangle className="h-4 w-4" />
                        ) : (
                          <XCircle className="h-4 w-4" />
                        )}
                      </div>
                      <div className="ml-3">
                        <p className="text-sm font-medium text-gray-900 group-hover:text-primary-600">
                          {log.action} {log.entityType} <span className="text-primary-500">{log.entityName}</span>
                        </p>
                        <p className="text-xs text-gray-500">
                          {log.userName} • {new Date(log.timestamp).toLocaleTimeString()}
                        </p>
                      </div>
                      <div className="ml-auto self-center">
                        <ArrowRight className="h-4 w-4 text-gray-400 group-hover:text-primary-500" />
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>
          </DashboardCard>

          {/* Compliance Status */}
          <DashboardCard
            title="Compliance"
            viewAllLink="/compliance"
            className="border-l-4 border-green-500"
          >
            <DashboardKPI
              icon={
                <div className="p-3 rounded-full bg-green-100">
                  <Shield className="h-6 w-6 text-green-600" />
                </div>
              }
              title="Compliance Score"
              value={`${state.stats.complianceScore}%`}
              secondaryValue={
                state.complianceStatuses[0]
                  ? new Date(state.complianceStatuses[0].lastChecked).toLocaleDateString()
                  : 'N/A'
              }
              link="/compliance"
            />
            <div className="space-y-3">
              <h3 className="text-sm font-medium text-gray-700">Status Overview</h3>
              {state.complianceStatuses.slice(0, 3).map((status) => (
                <Link
                  key={status.id}
                  to={`/compliance#${status.id}`}
                  className="block group hover:bg-gray-50 -mx-6 px-6 py-2 transition-colors duration-150"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-gray-900 group-hover:text-primary-600">
                      {status.name}
                    </span>
                    <div className="flex items-center">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${status.status === 'compliant'
                          ? 'bg-green-100 text-green-800 group-hover:bg-green-50'
                          : status.status === 'warning'
                            ? 'bg-yellow-100 text-yellow-800 group-hover:bg-yellow-50'
                            : 'bg-red-100 text-red-800 group-hover:bg-red-50'
                        }`}>
                        {status.status.split('-').map(word =>
                          word.charAt(0).toUpperCase() + word.slice(1)
                        ).join('-')}
                      </span>
                      <ArrowRight className="ml-2 h-3.5 w-3.5 text-gray-400 group-hover:text-primary-500" />
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          </DashboardCard>
        </div>

        {/* Quick Actions */}
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 mb-8">
          <Link to="/workflows/new" className="block">
            <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
              <div className="p-6">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-primary-100 rounded-md p-3">
                    <PlusCircle className="h-6 w-6 text-primary-600" />
                  </div>
                  <div className="ml-5">
                    <h3 className="text-lg font-medium text-gray-900">Create New Workflow</h3>
                    <p className="mt-1 text-sm text-gray-500">Design and deploy a new workflow from scratch or template</p>
                  </div>
                </div>
              </div>
            </div>
          </Link>

          <Link to="/executions" className="block">
            <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
              <div className="p-6">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-indigo-100 rounded-md p-3">
                    <ListChecks className="h-6 w-6 text-indigo-600" />
                  </div>
                  <div className="ml-5">
                    <h3 className="text-lg font-medium text-gray-900">View All Executions</h3>
                    <p className="mt-1 text-sm text-gray-500">Monitor and manage all workflow executions</p>
                  </div>
                </div>
              </div>
            </div>
          </Link>

          <Link to="/api-docs" className="block">
            <div className="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 h-full">
              <div className="p-6">
                <div className="flex items-center">
                  <div className="flex-shrink-0 bg-green-100 rounded-md p-3">
                    <BookOpen className="h-6 w-6 text-green-600" />
                  </div>
                  <div className="ml-5">
                    <h3 className="text-lg font-medium text-gray-900">Explore API Docs</h3>
                    <p className="mt-1 text-sm text-gray-500">Learn how to integrate with our API</p>
                  </div>
                </div>
              </div>
            </div>
          </Link>
        </div>
      </main>
    </div>
  );
};
/**
 * KPI Card Component
 *
 * @param title - Card title
 * @param value - KPI value
 * @param icon - Icon emoji
 * @param color - Background color class
 * @returns JSX element
 */
interface KPICardProps {
  title: string;
  value: string | number;
  icon: string;
  color: string;
}

function KPICard({ title, value, icon, color }: KPICardProps): React.ReactElement {
  return (
    <div className={`${color} rounded-lg p-6`}>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-gray-600 text-sm font-medium">{title}</p>
          <p className="text-3xl font-bold text-gray-900 mt-2">{value}</p>
        </div>
        <div className="text-4xl">{icon}</div>
      </div>
    </div>
  );
}

/**
 * Quick Action Card Component
 *
 * @param title - Action title
 * @param description - Action description
 * @param icon - Icon emoji
 * @param link - Link URL
 * @returns JSX element
 */
interface QuickActionCardProps {
  title: string;
  description: string;
  icon: string;
  link: string;
}

function QuickActionCard({
  title,
  description,
  icon,
  link,
}: QuickActionCardProps): React.ReactElement {
  return (
    <Link to={link}>
      <div className="bg-white rounded-lg shadow-sm p-6 hover:shadow-md transition-shadow cursor-pointer">
        <div className="text-3xl mb-3">{icon}</div>
        <h3 className="font-semibold text-gray-900">{title}</h3>
        <p className="text-sm text-gray-600 mt-1">{description}</p>
      </div>
    </Link>
  );
}

export default DashboardPage;
