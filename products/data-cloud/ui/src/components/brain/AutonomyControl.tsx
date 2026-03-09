/**
 * Autonomy Control Component
 *
 * Graduated autonomy sliders for controlling AI autonomy per domain.
 * Part of Journey 5: Governance Guardian (Autonomy & Rules)
 *
 * @doc.type component
 * @doc.purpose Per-domain autonomy control interface
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Shield, Activity, Database, Lock, AlertTriangle, CheckCircle } from 'lucide-react';
import { brainService, AutonomyState } from '../../api/brain.service';
import BaseCard from '../cards/BaseCard';
import { Button } from '../common/Button';

interface AutonomyControlProps {
  domains?: string[];
  onPolicyChange?: (domain: string, policy: AutonomyState) => void;
}

type AutonomyMode = 'ADVISORY' | 'CHATTY' | 'AUTONOMOUS';

const AUTONOMY_MODES: { mode: AutonomyMode; label: string; description: string; color: string }[] = [
  {
    mode: 'ADVISORY',
    label: 'Advisory',
    description: 'Human in the loop - System provides recommendations only',
    color: 'yellow',
  },
  {
    mode: 'CHATTY',
    label: 'Chatty',
    description: 'Semi-autonomous - System acts but notifies user',
    color: 'blue',
  },
  {
    mode: 'AUTONOMOUS',
    label: 'Autonomous',
    description: 'Self-driving - System acts independently',
    color: 'green',
  },
];

const DEFAULT_DOMAINS = [
  { id: 'ingestion', name: 'Data Ingestion', icon: Database, description: 'Automatic data pipeline scaling' },
  { id: 'quality', name: 'Data Quality', icon: CheckCircle, description: 'Quality issue detection and fixes' },
  { id: 'security', name: 'Security', icon: Shield, description: 'Access control and PII handling' },
  { id: 'optimization', name: 'Optimization', icon: Activity, description: 'Query and cost optimization' },
];

export function AutonomyControl({ domains, onPolicyChange }: AutonomyControlProps) {
  const queryClient = useQueryClient();
  const [expandedDomain, setExpandedDomain] = useState<string | null>(null);

  const activeDomains = domains || DEFAULT_DOMAINS.map(d => d.id);

  // Fetch autonomy states for all domains
  const domainStates = activeDomains.map(domainId =>
    useQuery({
      queryKey: ['autonomy-state', domainId],
      queryFn: () => brainService.getAutonomyState(domainId),
      staleTime: 30000,
    })
  );

  // Update autonomy policy mutation
  const updatePolicy = useMutation({
    mutationFn: ({ domain, policy }: { domain: string; policy: Partial<AutonomyState> }) =>
      brainService.updateAutonomyPolicy(domain, policy),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['autonomy-state', variables.domain] });
      onPolicyChange?.(variables.domain, data);
    },
  });

  const handleModeChange = (domainId: string, mode: AutonomyMode) => {
    updatePolicy.mutate({
      domain: domainId,
      policy: { mode },
    });
  };

  const handleEnabledToggle = (domainId: string, enabled: boolean) => {
    updatePolicy.mutate({
      domain: domainId,
      policy: { enabled },
    });
  };

  const handleConfidenceChange = (domainId: string, threshold: number) => {
    updatePolicy.mutate({
      domain: domainId,
      policy: { confidenceThreshold: threshold / 100 },
    });
  };

  const getModeColor = (mode: AutonomyMode): string => {
    switch (mode) {
      case 'ADVISORY':
        return 'bg-yellow-100 text-yellow-700 border-yellow-300';
      case 'CHATTY':
        return 'bg-blue-100 text-blue-700 border-blue-300';
      case 'AUTONOMOUS':
        return 'bg-green-100 text-green-700 border-green-300';
      default:
        return 'bg-gray-100 text-gray-700 border-gray-300';
    }
  };

  return (
    <BaseCard
      title="Autonomy Control Center"
      subtitle="Configure AI autonomy per domain"
    >
      <div className="space-y-4">
        {DEFAULT_DOMAINS.map((domain, index) => {
          const stateQuery = domainStates[index];
          const state = stateQuery?.data;
          const isExpanded = expandedDomain === domain.id;
          const DomainIcon = domain.icon;

          if (stateQuery?.isLoading) {
            return (
              <div key={domain.id} className="h-24 bg-gray-100 animate-pulse rounded-lg"></div>
            );
          }

          return (
            <div
              key={domain.id}
              className="border-2 border-gray-200 rounded-lg overflow-hidden transition-all hover:border-gray-300"
            >
              {/* Domain Header */}
              <div
                className="p-4 bg-gray-50 cursor-pointer"
                onClick={() => setExpandedDomain(isExpanded ? null : domain.id)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3 flex-1">
                    <div className={`p-2 rounded-lg ${
                      state?.enabled ? 'bg-primary-100' : 'bg-gray-200'
                    }`}>
                      <DomainIcon className={`h-5 w-5 ${
                        state?.enabled ? 'text-primary-600' : 'text-gray-400'
                      }`} />
                    </div>

                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <h3 className="text-sm font-semibold text-gray-900">
                          {domain.name}
                        </h3>
                        {!state?.enabled && (
                          <Lock className="h-4 w-4 text-gray-400" />
                        )}
                      </div>
                      <p className="text-xs text-gray-600 mt-0.5">
                        {domain.description}
                      </p>
                    </div>
                  </div>

                  {/* Current Mode Badge */}
                  {state && (
                    <div className={`px-3 py-1 rounded-full text-xs font-semibold border-2 ${
                      getModeColor(state.mode)
                    }`}>
                      {state.mode}
                    </div>
                  )}
                </div>
              </div>

              {/* Expanded Controls */}
              {isExpanded && state && (
                <div className="p-4 space-y-4 bg-white">
                  {/* Enable/Disable Toggle */}
                  <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                    <div className="flex items-center gap-2">
                      <Activity className="h-4 w-4 text-gray-600" />
                      <span className="text-sm font-medium text-gray-900">
                        Domain Enabled
                      </span>
                    </div>
                    <button
                      onClick={() => handleEnabledToggle(domain.id, !state.enabled)}
                      className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                        state.enabled ? 'bg-primary-600' : 'bg-gray-300'
                      }`}
                    >
                      <span
                        className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                          state.enabled ? 'translate-x-6' : 'translate-x-1'
                        }`}
                      />
                    </button>
                  </div>

                  {/* Mode Selection */}
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Autonomy Mode
                    </label>
                    <div className="grid grid-cols-3 gap-2">
                      {AUTONOMY_MODES.map((modeOption) => (
                        <button
                          key={modeOption.mode}
                          onClick={() => handleModeChange(domain.id, modeOption.mode)}
                          disabled={!state.enabled}
                          className={`p-3 rounded-lg border-2 text-left transition-all ${
                            state.mode === modeOption.mode
                              ? getModeColor(modeOption.mode)
                              : 'border-gray-200 bg-white hover:border-gray-300'
                          } ${!state.enabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                        >
                          <div className="text-sm font-semibold mb-1">
                            {modeOption.label}
                          </div>
                          <div className="text-xs text-gray-600">
                            {modeOption.description}
                          </div>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Confidence Threshold */}
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <label className="text-sm font-medium text-gray-700">
                        Confidence Threshold
                      </label>
                      <span className="text-sm font-semibold text-primary-600">
                        {Math.round((state.confidenceThreshold || 0.8) * 100)}%
                      </span>
                    </div>
                    <input
                      type="range"
                      min="50"
                      max="100"
                      step="5"
                      value={Math.round((state.confidenceThreshold || 0.8) * 100)}
                      onChange={(e) => handleConfidenceChange(domain.id, parseInt(e.target.value))}
                      disabled={!state.enabled || state.mode === 'ADVISORY'}
                      className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                    />
                    <div className="flex justify-between text-xs text-gray-500 mt-1">
                      <span>Low (50%)</span>
                      <span>High (100%)</span>
                    </div>
                  </div>

                  {/* Last Action */}
                  {state.lastAction && (
                    <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg">
                      <div className="flex items-start gap-2">
                        <CheckCircle className="h-4 w-4 text-blue-600 mt-0.5" />
                        <div className="flex-1">
                          <div className="text-xs font-semibold text-blue-900 mb-1">
                            Last Action
                          </div>
                          <div className="text-xs text-blue-800">
                            {state.lastAction}
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Security Warning for Security Domain */}
                  {domain.id === 'security' && state.mode === 'AUTONOMOUS' && (
                    <div className="p-3 bg-orange-50 border border-orange-200 rounded-lg">
                      <div className="flex items-start gap-2">
                        <AlertTriangle className="h-4 w-4 text-orange-600 mt-0.5" />
                        <div className="flex-1">
                          <div className="text-xs font-semibold text-orange-900 mb-1">
                            Security Advisory
                          </div>
                          <div className="text-xs text-orange-800">
                            Full autonomy in security domain requires careful monitoring.
                            Consider using CHATTY mode for sensitive operations.
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}

        {/* Info Banner */}
        <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <div className="flex items-start gap-3">
            <Shield className="h-5 w-5 text-blue-600 mt-0.5" />
            <div className="flex-1">
              <h4 className="text-sm font-semibold text-blue-900 mb-1">
                About Graduated Autonomy
              </h4>
              <p className="text-sm text-blue-800">
                Control is not binary. Slide between <strong>Advisory</strong> (human approval),{' '}
                <strong>Chatty</strong> (act with notification), and <strong>Autonomous</strong>{' '}
                (self-driving) modes per domain. Confidence thresholds ensure safety.
              </p>
            </div>
          </div>
        </div>
      </div>
    </BaseCard>
  );
}

export default AutonomyControl;

