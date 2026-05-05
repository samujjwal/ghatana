/**
 * Traceability Graph Component
 *
 * End-to-end traceability graph showing the complete lineage from intent to run.
 * Displays the flow through all 8 lifecycle phases with artifact connections.
 *
 * @doc.type component
 * @doc.purpose End-to-end traceability visualization
 * @doc.layer product
 * @doc.pattern Visualization Component
 */

import React from 'react';
import {
  Lightbulb as IntentIcon,
  PenTool as ShapeIcon,
  ShieldCheck as ValidateIcon,
  Code as GenerateIcon,
  Play as RunIcon,
  Eye as ObserveIcon,
  BookOpen as LearnIcon,
  RefreshCw as EvolveIcon,
  ChevronRight,
  CheckCircle,
  Clock,
  AlertCircle,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import type { LifecyclePhase } from '@/shared/types/lifecycle';

interface TraceabilityNode {
  id: string;
  phase: LifecyclePhase;
  label: string;
  description: string;
  status: 'completed' | 'in-progress' | 'pending' | 'blocked';
  artifacts: Array<{
    id: string;
    type: string;
    name: string;
    status: 'present' | 'missing' | 'invalid';
  }>;
  timestamp?: string;
}

interface TraceabilityEdge {
  from: string;
  to: string;
  status: 'valid' | 'broken' | 'pending';
}

interface TraceabilityGraphProps {
  projectId: string;
  className?: string;
}

export function TraceabilityGraph({ projectId, className }: TraceabilityGraphProps) {
  // Mock data - in production, this would come from the traceability API
  const mockNodes: TraceabilityNode[] = [
    {
      id: 'intent',
      phase: 'INTENT',
      label: 'Intent',
      description: 'Problem definition and value proposition',
      status: 'completed',
      artifacts: [
        { id: 'idea-1', type: 'idea_brief', name: 'Idea Brief', status: 'present' },
        { id: 'research-1', type: 'research_pack', name: 'Research Pack', status: 'present' },
        { id: 'problem-1', type: 'problem_statement', name: 'Problem Statement', status: 'present' },
      ],
      timestamp: '2026-04-27T10:00:00Z',
    },
    {
      id: 'shape',
      phase: 'SHAPE',
      label: 'Shape',
      description: 'Requirements, architecture, and UX',
      status: 'completed',
      artifacts: [
        { id: 'req-1', type: 'requirements', name: 'Requirements', status: 'present' },
        { id: 'adr-1', type: 'adr', name: 'Architecture Decision', status: 'present' },
        { id: 'ux-1', type: 'ux_spec', name: 'UX Specification', status: 'present' },
      ],
      timestamp: '2026-04-27T12:00:00Z',
    },
    {
      id: 'validate',
      phase: 'VALIDATE',
      label: 'Validate',
      description: 'Security, testing, and simulation',
      status: 'completed',
      artifacts: [
        { id: 'threat-1', type: 'threat_model', name: 'Threat Model', status: 'present' },
        { id: 'validation-1', type: 'validation_report', name: 'Validation Report', status: 'present' },
        { id: 'sim-1', type: 'simulation_results', name: 'Simulation Results', status: 'present' },
      ],
      timestamp: '2026-04-27T14:00:00Z',
    },
    {
      id: 'generate',
      phase: 'GENERATE',
      label: 'Generate',
      description: 'Code generation and scaffolding',
      status: 'in-progress',
      artifacts: [
        { id: 'delivery-1', type: 'delivery_plan', name: 'Delivery Plan', status: 'present' },
        { id: 'release-1', type: 'release_strategy', name: 'Release Strategy', status: 'missing' },
      ],
      timestamp: '2026-04-27T16:00:00Z',
    },
    {
      id: 'run',
      phase: 'RUN',
      label: 'Run',
      description: 'Deployment and execution',
      status: 'pending',
      artifacts: [
        { id: 'evidence-1', type: 'evidence_pack', name: 'Evidence Pack', status: 'missing' },
        { id: 'release-packet-1', type: 'release_packet', name: 'Release Packet', status: 'missing' },
      ],
    },
    {
      id: 'observe',
      phase: 'OBSERVE',
      label: 'Observe',
      description: 'Monitoring and incident response',
      status: 'pending',
      artifacts: [
        { id: 'ops-1', type: 'ops_baseline', name: 'Ops Baseline', status: 'missing' },
        { id: 'incident-1', type: 'incident_report', name: 'Incident Report', status: 'missing' },
      ],
    },
    {
      id: 'learn',
      phase: 'LEARN',
      label: 'Learn',
      description: 'Analytics and insights',
      status: 'pending',
      artifacts: [
        { id: 'enhance-1', type: 'enhancement_requests', name: 'Enhancement Requests', status: 'missing' },
      ],
    },
    {
      id: 'evolve',
      phase: 'EVOLVE',
      label: 'Evolve',
      description: 'Continuous improvement',
      status: 'pending',
      artifacts: [
        { id: 'learning-1', type: 'learning_record', name: 'Learning Record', status: 'missing' },
      ],
    },
  ];

  const mockEdges: TraceabilityEdge[] = [
    { from: 'intent', to: 'shape', status: 'valid' },
    { from: 'shape', to: 'validate', status: 'valid' },
    { from: 'validate', to: 'generate', status: 'valid' },
    { from: 'generate', to: 'run', status: 'pending' },
    { from: 'run', to: 'observe', status: 'pending' },
    { from: 'observe', to: 'learn', status: 'pending' },
    { from: 'learn', to: 'evolve', status: 'pending' },
  ];

  const getPhaseIcon = (phase: LifecyclePhase) => {
    const icons = {
      INTENT: IntentIcon,
      SHAPE: ShapeIcon,
      VALIDATE: ValidateIcon,
      GENERATE: GenerateIcon,
      RUN: RunIcon,
      OBSERVE: ObserveIcon,
      LEARN: LearnIcon,
      EVOLVE: EvolveIcon,
    };
    return icons[phase] || IntentIcon;
  };

  const getStatusColor = (status: TraceabilityNode['status']) => {
    switch (status) {
      case 'completed':
        return 'bg-emerald-500/10 border-emerald-500 text-emerald-400';
      case 'in-progress':
        return 'bg-info-bg/10 border-info-border text-info-color';
      case 'pending':
        return 'bg-surface border-border text-fg-muted';
      case 'blocked':
        return 'bg-destructive-bg/10 border-destructive-border text-destructive';
      default:
        return 'bg-surface border-border text-fg-muted';
    }
  };

  const getStatusIcon = (status: TraceabilityNode['status']) => {
    switch (status) {
      case 'completed':
        return <CheckCircle size={14} />;
      case 'in-progress':
        return <Clock size={14} />;
      case 'blocked':
        return <AlertCircle size={14} />;
      default:
        return null;
    }
  };

  const getArtifactStatusColor = (status: 'present' | 'missing' | 'invalid') => {
    switch (status) {
      case 'present':
        return 'text-emerald-400';
      case 'missing':
        return 'text-fg-muted';
      case 'invalid':
        return 'text-destructive';
      default:
        return 'text-fg-muted';
    }
  };

  return (
    <div className={cn('p-6', className)}>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-fg-muted mb-2">
          End-to-End Traceability
        </h1>
        <p className="text-sm text-fg-muted">
          Complete lineage from intent through all lifecycle phases to production run.
        </p>
      </div>

      {/* Traceability Graph */}
      <div className="space-y-4">
        {mockNodes.map((node, index) => {
          const Icon = getPhaseIcon(node.phase);
          const nextNode = mockNodes[index + 1];
          const edge = mockEdges.find(e => e.from === node.id && e.to === nextNode?.id);

          return (
            <React.Fragment key={node.id}>
              <div className={cn(
                'rounded-xl border p-5 transition-all',
                getStatusColor(node.status)
              )}>
                <div className="flex items-start gap-4">
                  {/* Phase Icon */}
                  <div className={cn(
                    'flex-shrink-0 w-12 h-12 rounded-lg flex items-center justify-center',
                    node.status === 'completed' ? 'bg-emerald-500/20' :
                    node.status === 'in-progress' ? 'bg-info-bg/20' :
                    'bg-surface-muted'
                  )}>
                    <Icon size={24} />
                  </div>

                  {/* Phase Content */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <h3 className="text-lg font-semibold">{node.label}</h3>
                      {getStatusIcon(node.status)}
                      {node.timestamp && (
                        <span className="text-xs text-fg-muted">
                          {new Date(node.timestamp).toLocaleString()}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-fg-muted mb-3">{node.description}</p>

                    {/* Artifacts */}
                    <div className="space-y-2">
                      <div className="text-xs font-medium text-fg-muted uppercase tracking-wider">
                        Artifacts ({node.artifacts.length})
                      </div>
                      <div className="flex flex-wrap gap-2">
                        {node.artifacts.map((artifact) => (
                          <div
                            key={artifact.id}
                            className={cn(
                              'inline-flex items-center gap-1.5 px-2 py-1 rounded bg-surface text-xs',
                              getArtifactStatusColor(artifact.status)
                            )}
                          >
                            <span className="font-medium">{artifact.name}</span>
                            {artifact.status === 'present' && <CheckCircle size={10} />}
                            {artifact.status === 'missing' && <span className="text-fg-muted">—</span>}
                            {artifact.status === 'invalid' && <AlertCircle size={10} />}
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Edge/Connector */}
              {edge && nextNode && (
                <div className="flex items-center justify-center py-2">
                  <div className={cn(
                    'flex items-center gap-2 text-xs',
                    edge.status === 'valid' ? 'text-emerald-400' :
                    edge.status === 'broken' ? 'text-destructive' :
                    'text-fg-muted'
                  )}>
                    {edge.status === 'valid' && <CheckCircle size={14} />}
                    {edge.status === 'broken' && <AlertCircle size={14} />}
                    {edge.status === 'pending' && <Clock size={14} />}
                    <ChevronRight size={16} />
                  </div>
                </div>
              )}
            </React.Fragment>
          );
        })}
      </div>

      {/* Summary */}
      <div className="mt-8 p-4 bg-surface border border-border rounded-xl">
        <h3 className="text-md font-semibold text-fg-muted mb-3">Traceability Summary</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <div className="text-fg-muted">Completed Phases</div>
            <div className="text-2xl font-bold text-emerald-400">
              {mockNodes.filter(n => n.status === 'completed').length}/8
            </div>
          </div>
          <div>
            <div className="text-fg-muted">Total Artifacts</div>
            <div className="text-2xl font-bold text-fg-muted">
              {mockNodes.reduce((sum, n) => sum + n.artifacts.length, 0)}
            </div>
          </div>
          <div>
            <div className="text-fg-muted">Present Artifacts</div>
            <div className="text-2xl font-bold text-emerald-400">
              {mockNodes.reduce((sum, n) => 
                sum + n.artifacts.filter(a => a.status === 'present').length, 0
              )}
            </div>
          </div>
          <div>
            <div className="text-fg-muted">Missing Artifacts</div>
            <div className="text-2xl font-bold text-warning-color">
              {mockNodes.reduce((sum, n) => 
                sum + n.artifacts.filter(a => a.status === 'missing').length, 0
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default TraceabilityGraph;
