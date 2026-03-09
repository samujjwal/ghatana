/**
 * Enhanced Governance Page
 *
 * Part of Journey 10: Governance & Compliance
 * Complete implementation with policy visualization, compliance, and autonomy control
 *
 * @doc.type page
 * @doc.purpose Governance and compliance management dashboard
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Shield, AlertTriangle, CheckCircle, FileText, Users } from 'lucide-react';
import { governanceService } from '../api/governance.service';
import { PolicyVisualizer } from '../components/governance/PolicyVisualizer';
import { AutonomyControl } from '../components/brain/AutonomyControl';
import { DashboardKPI } from '../components/cards/DashboardCard';
import { BaseCard } from '../components/cards/BaseCard';

/** @deprecated Use TrustCenter instead. Routes now redirect /governance → TrustCenter. */
export function GovernancePageEnhanced() {
  const [activeTab, setActiveTab] = useState<'policies' | 'compliance' | 'autonomy'>('policies');

  // Fetch policies
  const { data: policies } = useQuery({
    queryKey: ['policies'],
    queryFn: () => governanceService.getPolicies(),
  });

  // Fetch violations
  const { data: violations } = useQuery({
    queryKey: ['violations'],
    queryFn: () => governanceService.getViolations(undefined, 50),
  });

  // Fetch compliance report
  const { data: complianceReport } = useQuery({
    queryKey: ['compliance-report', '30d'],
    queryFn: () => governanceService.getComplianceReport('30d'),
  });

  // Fetch access requests
  const { data: accessRequests } = useQuery({
    queryKey: ['access-requests'],
    queryFn: () => governanceService.getAccessRequests(),
  });

  const activePolicies = policies?.filter(p => p.enabled).length || 0;
  const pendingViolations = violations?.filter(v => v.status === 'PENDING_APPROVAL').length || 0;
  const pendingRequests = accessRequests?.filter(r => r.status === 'PENDING').length || 0;

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-gradient-to-br from-red-500 to-purple-500 rounded-lg">
              <Shield className="h-8 w-8 text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-gray-900">
                Governance & Compliance
              </h1>
              <p className="text-sm text-gray-600 mt-1">
                Policy management, compliance monitoring, and autonomy control
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* KPI Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <DashboardKPI
            title="Active Policies"
            value={activePolicies}
            subtitle={`of ${policies?.length || 0} total`}
            icon={<Shield className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="blue"
          />
          <DashboardKPI
            title="Violations"
            value={violations?.length || 0}
            subtitle={`${pendingViolations} pending`}
            icon={<AlertTriangle className="h-6 w-6" />}
            trend={{ value: 2, direction: 'down' }}
            color="red"
          />
          <DashboardKPI
            title="Compliance Score"
            value={`${(complianceReport?.summary.complianceScore || 0).toFixed(1)}%`}
            icon={<CheckCircle className="h-6 w-6" />}
            trend={{ value: 1.5, direction: 'up' }}
            color="green"
          />
          <DashboardKPI
            title="Access Requests"
            value={accessRequests?.length || 0}
            subtitle={`${pendingRequests} pending`}
            icon={<Users className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="orange"
          />
        </div>

        {/* Tabs */}
        <div className="mb-6 border-b border-gray-200">
          <div className="flex gap-8">
            <button
              onClick={() => setActiveTab('policies')}
              className={`pb-4 text-sm font-medium transition-colors ${
                activeTab === 'policies'
                  ? 'text-primary-600 border-b-2 border-primary-600'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              Policy Guardrails
            </button>
            <button
              onClick={() => setActiveTab('compliance')}
              className={`pb-4 text-sm font-medium transition-colors ${
                activeTab === 'compliance'
                  ? 'text-primary-600 border-b-2 border-primary-600'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              Compliance Reports
            </button>
            <button
              onClick={() => setActiveTab('autonomy')}
              className={`pb-4 text-sm font-medium transition-colors ${
                activeTab === 'autonomy'
                  ? 'text-primary-600 border-b-2 border-primary-600'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              Autonomy Control
            </button>
          </div>
        </div>

        {/* Tab Content */}
        {activeTab === 'policies' && (
          <div className="space-y-8">
            <PolicyVisualizer showFilters={true} highlightViolations={true} />

            {/* Recent Violations */}
            {violations && violations.length > 0 && (
              <BaseCard
                title="Recent Violations"
                subtitle="Latest policy violations requiring attention"
              >
                <div className="space-y-2">
                  {violations.slice(0, 5).map((violation) => (
                    <div
                      key={violation.id}
                      className="flex items-start justify-between p-3 bg-red-50 border border-red-200 rounded-lg"
                    >
                      <div className="flex items-start gap-3 flex-1">
                        <AlertTriangle className="h-5 w-5 text-red-600 mt-0.5" />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium text-gray-900">
                              {violation.policyName}
                            </span>
                            <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                              violation.status === 'BLOCKED'
                                ? 'bg-red-100 text-red-700'
                                : 'bg-yellow-100 text-yellow-700'
                            }`}>
                              {violation.status}
                            </span>
                          </div>
                          <p className="text-sm text-gray-700 mt-1">
                            {violation.details}
                          </p>
                          <div className="text-xs text-gray-500 mt-1">
                            User: {violation.userId} • Dataset: {violation.datasetId} •{' '}
                            {new Date(violation.timestamp).toLocaleString()}
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </BaseCard>
            )}
          </div>
        )}

        {activeTab === 'compliance' && complianceReport && (
          <div className="space-y-8">
            {/* Compliance Summary */}
            <BaseCard title="Compliance Summary" subtitle={`Period: ${complianceReport.period}`}>
              <div className="grid grid-cols-2 md:grid-cols-3 gap-6">
                <div>
                  <div className="text-3xl font-bold text-gray-900">
                    {complianceReport.summary.totalPolicies}
                  </div>
                  <div className="text-sm text-gray-600 mt-1">Total Policies</div>
                </div>
                <div>
                  <div className="text-3xl font-bold text-green-600">
                    {complianceReport.summary.activePolicies}
                  </div>
                  <div className="text-sm text-gray-600 mt-1">Active Policies</div>
                </div>
                <div>
                  <div className="text-3xl font-bold text-red-600">
                    {complianceReport.summary.violations}
                  </div>
                  <div className="text-sm text-gray-600 mt-1">Violations</div>
                </div>
                <div>
                  <div className="text-3xl font-bold text-blue-600">
                    {complianceReport.summary.remediations}
                  </div>
                  <div className="text-sm text-gray-600 mt-1">Remediations</div>
                </div>
                <div>
                  <div className="text-3xl font-bold text-purple-600">
                    {complianceReport.summary.complianceScore.toFixed(1)}%
                  </div>
                  <div className="text-sm text-gray-600 mt-1">Compliance Score</div>
                </div>
              </div>
            </BaseCard>

            {/* Detailed Reports */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
              {/* PII Scans */}
              <BaseCard title="PII Scans" icon={<Shield className="h-5 w-5" />}>
                <div className="space-y-3">
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Total Datasets</span>
                    <span className="text-sm font-semibold text-gray-900">
                      {complianceReport.details.piiScans.totalDatasets}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Datasets with PII</span>
                    <span className="text-sm font-semibold text-gray-900">
                      {complianceReport.details.piiScans.datasetsWithPII}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Violations</span>
                    <span className="text-sm font-semibold text-red-600">
                      {complianceReport.details.piiScans.violations}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Remediated</span>
                    <span className="text-sm font-semibold text-green-600">
                      {complianceReport.details.piiScans.remediated}
                    </span>
                  </div>
                </div>
              </BaseCard>

              {/* Access Audits */}
              <BaseCard title="Access Audits" icon={<Users className="h-5 w-5" />}>
                <div className="space-y-3">
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Total Accesses</span>
                    <span className="text-sm font-semibold text-gray-900">
                      {complianceReport.details.accessAudits.totalAccesses.toLocaleString()}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Unauthorized Attempts</span>
                    <span className="text-sm font-semibold text-orange-600">
                      {complianceReport.details.accessAudits.unauthorizedAttempts}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">Blocked Accesses</span>
                    <span className="text-sm font-semibold text-red-600">
                      {complianceReport.details.accessAudits.blockedAccesses}
                    </span>
                  </div>
                </div>
              </BaseCard>
            </div>
          </div>
        )}

        {activeTab === 'autonomy' && (
          <div className="space-y-8">
            <AutonomyControl />

            <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
              <div className="flex items-start gap-3">
                <AlertTriangle className="h-5 w-5 text-yellow-600 mt-0.5" />
                <div className="flex-1">
                  <h4 className="text-sm font-semibold text-yellow-900 mb-1">
                    Governance & Autonomy Integration
                  </h4>
                  <p className="text-sm text-yellow-800">
                    Autonomy settings respect policy constraints. If a policy blocks an action,
                    it overrides autonomy settings. Security domain is locked to ADVISORY mode
                    when sensitive policies are active.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Access Requests Section */}
        {accessRequests && accessRequests.length > 0 && (
          <div className="mt-8">
            <BaseCard
              title="Pending Access Requests"
              subtitle="Requests requiring approval"
              icon={<Users className="h-5 w-5" />}
            >
              <div className="space-y-2">
                {accessRequests.filter(r => r.status === 'PENDING').map((request) => (
                  <div
                    key={request.id}
                    className="flex items-center justify-between p-3 bg-blue-50 border border-blue-200 rounded-lg"
                  >
                    <div className="flex-1">
                      <div className="text-sm font-medium text-gray-900">
                        {request.datasetName}
                      </div>
                      <div className="text-sm text-gray-700 mt-1">
                        Requested by: {request.requestedBy}
                      </div>
                      <div className="text-xs text-gray-600 mt-1">
                        Reason: {request.reason}
                      </div>
                      <div className="text-xs text-gray-500 mt-1">
                        {new Date(request.requestedAt).toLocaleString()}
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <button className="px-3 py-1 bg-green-600 text-white text-sm font-medium rounded hover:bg-green-700">
                        Approve
                      </button>
                      <button className="px-3 py-1 bg-red-600 text-white text-sm font-medium rounded hover:bg-red-700">
                        Reject
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </BaseCard>
          </div>
        )}
      </div>
    </div>
  );
}

export default GovernancePageEnhanced;

