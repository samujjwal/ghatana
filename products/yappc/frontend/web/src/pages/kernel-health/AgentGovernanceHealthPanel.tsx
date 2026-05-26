/**
 * Agent Governance Health Panel
 *
 * Displays agent governance status, learning level, learning evidence,
 * policy blocks, and promotion queue status for a ProductUnit.
 */

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/badge';
import { CheckCircle, XCircle, Shield, Brain, AlertTriangle, TrendingUp } from 'lucide-react';

export interface AgentGovernance {
  agentId: string;
  agentName: string;
  learningLevel: 'L0' | 'L1' | 'L2' | 'L3';
  governanceState: 'ready' | 'requires_approval' | 'requires_verification' | 'obsolete' | 'quarantined';
  learningEvidence: {
    semanticFacts: number;
    negativeKnowledge: number;
    episodicCaptures: number;
  };
  policyBlocks: string[];
  promotionQueue: {
    position: number;
    estimatedTime: string;
  } | null;
}

interface AgentGovernanceHealthPanelProps {
  productUnitId: string;
  agents: AgentGovernance[];
}

export const AgentGovernanceHealthPanel: React.FC<AgentGovernanceHealthPanelProps> = ({
  productUnitId,
  agents,
}) => {
  const getGovernanceStateIcon = (state: string) => {
    switch (state) {
      case 'ready':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'quarantined':
      case 'obsolete':
        return <XCircle className="h-4 w-4 text-red-500" />;
      case 'requires_approval':
      case 'requires_verification':
        return <AlertTriangle className="h-4 w-4 text-orange-500" />;
      default:
        return <Shield className="h-4 w-4 text-gray-500" />;
    }
  };

  const getGovernanceStateBadgeVariant = (state: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (state) {
      case 'ready':
        return 'default';
      case 'quarantined':
      case 'obsolete':
        return 'destructive';
      case 'requires_approval':
      case 'requires_verification':
        return 'secondary';
      default:
        return 'outline';
    }
  };

  const getLearningLevelBadgeVariant = (level: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (level) {
      case 'L3':
        return 'default';
      case 'L2':
        return 'secondary';
      case 'L1':
        return 'outline';
      default:
        return 'outline';
    }
  };

  if (agents.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Agent Governance</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">
            No agent governance data available for {productUnitId}
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Agent Governance</h3>
        <Badge variant="outline">{agents.length} agents</Badge>
      </div>

      <div className="space-y-3">
        {agents.map((agent) => (
          <div key={agent.agentId} className="p-4 border rounded-lg space-y-4">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2">
                <Brain className="h-4 w-4" />
                <div>
                  <h4 className="font-semibold">{agent.agentName}</h4>
                  <p className="text-sm text-muted-foreground">{agent.agentId}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={getLearningLevelBadgeVariant(agent.learningLevel)}>
                  {agent.learningLevel}
                </Badge>
                <Badge variant={getGovernanceStateBadgeVariant(agent.governanceState)}>
                  {agent.governanceState}
                </Badge>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4 text-sm">
              <div className="text-center p-2 bg-muted rounded">
                <p className="text-muted-foreground">Semantic Facts</p>
                <p className="font-semibold text-lg">{agent.learningEvidence.semanticFacts}</p>
              </div>
              <div className="text-center p-2 bg-muted rounded">
                <p className="text-muted-foreground">Negative Knowledge</p>
                <p className="font-semibold text-lg">{agent.learningEvidence.negativeKnowledge}</p>
              </div>
              <div className="text-center p-2 bg-muted rounded">
                <p className="text-muted-foreground">Episodic Captures</p>
                <p className="font-semibold text-lg">{agent.learningEvidence.episodicCaptures}</p>
              </div>
            </div>

            {agent.policyBlocks.length > 0 && (
              <div className="space-y-2">
                <p className="text-sm font-medium flex items-center gap-2">
                  <Shield className="h-4 w-4" />
                  Policy Blocks
                </p>
                <div className="space-y-1">
                  {agent.policyBlocks.map((block, index) => (
                    <div key={index} className="flex items-center gap-2 text-sm text-muted-foreground p-2 bg-muted rounded">
                      <XCircle className="h-3 w-3 text-red-500" />
                      {block}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {agent.promotionQueue && (
              <div className="flex items-center gap-2 p-3 bg-muted rounded">
                <TrendingUp className="h-4 w-4" />
                <div className="flex-1">
                  <p className="text-sm font-medium">Promotion Queue</p>
                  <p className="text-sm text-muted-foreground">
                    Position {agent.promotionQueue.position} · Est. {agent.promotionQueue.estimatedTime}
                  </p>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default AgentGovernanceHealthPanel;
