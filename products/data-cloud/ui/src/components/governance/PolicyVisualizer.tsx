/**
 * Policy Visualizer Component
 *
 * Visualizes governance policies as guardrails wrapping the AI Brain.
 * Part of Journey 5: Governance Guardian and Journey 10: Governance & Compliance
 *
 * @doc.type component
 * @doc.purpose Visual policy constraint representation
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Shield, AlertTriangle, Lock, CheckCircle, XCircle, Eye } from 'lucide-react';
import { governanceService, Policy } from '../../api/governance.service';
import BaseCard from '../cards/BaseCard';

interface PolicyVisualizerProps {
  showFilters?: boolean;
  highlightViolations?: boolean;
}

export function PolicyVisualizer({
  showFilters = true,
  highlightViolations = true
}: PolicyVisualizerProps) {
  const [filterType, setFilterType] = useState<string | undefined>();

  // Fetch policies
  const { data: policies, isLoading } = useQuery({
    queryKey: ['policies', filterType],
    queryFn: () => governanceService.getPolicies(filterType),
    refetchInterval: 30000,
  });

  // Fetch violations if highlighting enabled
  const { data: violations } = useQuery({
    queryKey: ['violations'],
    queryFn: () => governanceService.getViolations(),
    enabled: highlightViolations,
    refetchInterval: 10000,
  });

  const policyTypes = [
    { type: 'SECURITY', label: 'Security', color: 'red', icon: Shield },
    { type: 'PRIVACY', label: 'Privacy', color: 'purple', icon: Lock },
    { type: 'RETENTION', label: 'Retention', color: 'blue', icon: Eye },
    { type: 'ACCESS', label: 'Access', color: 'orange', icon: Lock },
    { type: 'QUALITY', label: 'Quality', color: 'green', icon: CheckCircle },
  ];

  const getPolicyColor = (type: string): string => {
    const policyType = policyTypes.find(pt => pt.type === type);
    return policyType?.color || 'gray';
  };

  const getPolicyIcon = (type: string) => {
    const policyType = policyTypes.find(pt => pt.type === type);
    const Icon = policyType?.icon || Shield;
    return <Icon className="h-4 w-4" />;
  };

  const hasViolations = (policyId: string): boolean => {
    return violations?.some(v => v.policyId === policyId) || false;
  };

  if (isLoading) {
    return (
      <BaseCard title="Policy Guardrails">
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="h-16 bg-gray-200 animate-pulse rounded"></div>
          ))}
        </div>
      </BaseCard>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header with Filters */}
      {showFilters && (
        <div className="flex items-center gap-4">
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Filter by Type
            </label>
            <div className="flex flex-wrap gap-2">
              <button
                onClick={() => setFilterType(undefined)}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  !filterType
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                All
              </button>
              {policyTypes.map(({ type, label, color }) => (
                <button
                  key={type}
                  onClick={() => setFilterType(type)}
                  className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                    filterType === type
                      ? `bg-${color}-600 text-white`
                      : `bg-${color}-100 text-${color}-700 hover:bg-${color}-200`
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Policy Guardrails Visualization */}
      <BaseCard
        title="Policy Guardrails"
        subtitle={`${policies?.length || 0} active policies protecting your data`}
      >
        <div className="relative">
          {/* Brain Representation */}
          <div className="mx-auto w-64 h-64 relative">
            {/* Center Brain Icon */}
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="w-32 h-32 bg-gradient-to-br from-purple-400 to-blue-500 rounded-full flex items-center justify-center shadow-lg">
                <Shield className="h-16 w-16 text-white" />
              </div>
            </div>

            {/* Concentric Policy Rings */}
            {policies && policies.length > 0 && (
              <>
                {[...Array(Math.min(3, Math.ceil(policies.length / 5)))].map((_, ringIndex) => {
                  const ringPolicies = policies.slice(ringIndex * 5, (ringIndex + 1) * 5);
                  const radius = 96 + (ringIndex * 24);

                  return (
                    <div
                      key={ringIndex}
                      className="absolute inset-0 flex items-center justify-center"
                    >
                      <div
                        className="absolute rounded-full border-2 border-dashed"
                        style={{
                          width: `${radius * 2}px`,
                          height: `${radius * 2}px`,
                          borderColor: 'rgba(59, 130, 246, 0.3)',
                        }}
                      />

                      {/* Policy badges around the ring */}
                      {ringPolicies.map((policy, index) => {
                        const angle = (index / ringPolicies.length) * 2 * Math.PI;
                        const x = Math.cos(angle) * radius;
                        const y = Math.sin(angle) * radius;
                        const color = getPolicyColor(policy.type);
                        const hasViolation = hasViolations(policy.id);

                        return (
                          <div
                            key={policy.id}
                            className="absolute"
                            style={{
                              left: '50%',
                              top: '50%',
                              transform: `translate(calc(-50% + ${x}px), calc(-50% + ${y}px))`,
                            }}
                          >
                            <div
                              className={`relative group w-12 h-12 rounded-full flex items-center justify-center shadow-md cursor-pointer transition-all hover:scale-110 ${
                                policy.enabled
                                  ? `bg-${color}-100 border-2 border-${color}-500`
                                  : 'bg-gray-200 border-2 border-gray-400'
                              }`}
                            >
                              <div className={`${policy.enabled ? `text-${color}-600` : 'text-gray-500'}`}>
                                {getPolicyIcon(policy.type)}
                              </div>

                              {/* Violation indicator */}
                              {hasViolation && (
                                <div className="absolute -top-1 -right-1 w-4 h-4 bg-red-500 rounded-full flex items-center justify-center">
                                  <AlertTriangle className="h-3 w-3 text-white" />
                                </div>
                              )}

                              {/* Disabled indicator */}
                              {!policy.enabled && (
                                <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-gray-400 rounded-full flex items-center justify-center">
                                  <XCircle className="h-3 w-3 text-white" />
                                </div>
                              )}

                              {/* Tooltip */}
                              <div className="absolute bottom-full mb-2 hidden group-hover:block z-10">
                                <div className="bg-gray-900 text-white text-xs rounded py-1 px-2 whitespace-nowrap">
                                  {policy.name}
                                  {hasViolation && (
                                    <span className="text-red-400 ml-1">• Violation</span>
                                  )}
                                </div>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  );
                })}
              </>
            )}
          </div>

          {/* Center Label */}
          <div className="text-center mt-4">
            <p className="text-sm font-semibold text-gray-900">AI Brain</p>
            <p className="text-xs text-gray-600">Protected by {policies?.length || 0} policies</p>
          </div>
        </div>

        {/* Policy List */}
        <div className="mt-8 space-y-2">
          {policies?.map((policy) => {
            const color = getPolicyColor(policy.type);
            const hasViolation = hasViolations(policy.id);

            return (
              <div
                key={policy.id}
                className={`flex items-center justify-between p-3 rounded-lg border-2 transition-colors ${
                  hasViolation
                    ? 'border-red-300 bg-red-50'
                    : policy.enabled
                    ? `border-${color}-200 bg-${color}-50`
                    : 'border-gray-200 bg-gray-50'
                }`}
              >
                <div className="flex items-center gap-3 flex-1">
                  <div className={`p-2 rounded-lg ${
                    policy.enabled ? `bg-${color}-100` : 'bg-gray-200'
                  }`}>
                    {getPolicyIcon(policy.type)}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-gray-900 truncate">
                        {policy.name}
                      </span>
                      <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                        `bg-${color}-100 text-${color}-700`
                      }`}>
                        {policy.type}
                      </span>
                    </div>
                    <div className="text-xs text-gray-600 mt-1">
                      {policy.rules.length} rule(s) • Created {new Date(policy.createdAt).toLocaleDateString()}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {hasViolation && (
                    <div className="px-2 py-1 bg-red-100 text-red-700 rounded text-xs font-semibold flex items-center gap-1">
                      <AlertTriangle className="h-3 w-3" />
                      Violation
                    </div>
                  )}

                  <div className={`w-3 h-3 rounded-full ${
                    policy.enabled ? 'bg-green-500' : 'bg-gray-400'
                  }`} />
                </div>
              </div>
            );
          })}

          {(!policies || policies.length === 0) && (
            <div className="text-center py-8 text-gray-500">
              <Shield className="h-12 w-12 mx-auto mb-2 opacity-50" />
              <p>No policies configured</p>
            </div>
          )}
        </div>

        {/* Legend */}
        <div className="mt-6 p-4 bg-gray-50 rounded-lg">
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-xs">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-green-500" />
              <span className="text-gray-700">Enabled</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-gray-400" />
              <span className="text-gray-700">Disabled</span>
            </div>
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-3 w-3 text-red-500" />
              <span className="text-gray-700">Has Violations</span>
            </div>
          </div>
        </div>
      </BaseCard>
    </div>
  );
}

export default PolicyVisualizer;

