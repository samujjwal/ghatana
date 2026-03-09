/**
 * Helper Functions for Runbook Execution
 * 
 * Utility functions for configuration, risk analysis, and styling.
 */

import type { RunbookConfig, RunbookStep, Runbook, ResourceAction } from './types';

/**
 * Create default runbook configuration with optional overrides
 */
export function createRunbookConfig(overrides?: Partial<RunbookConfig>): RunbookConfig {
  return {
    layout: 'sequential',
    showTiming: true,
    showApprovals: true,
    enableRollback: true,
    highlightFailures: true,
    groupByStage: false,
    maxRetries: 3,
    ...overrides,
  };
}

/**
 * Map Terraform action string to ResourceAction type
 */
export function mapTerraformAction(action: string): ResourceAction {
  switch (action) {
    case 'create':
      return 'create';
    case 'update':
    case 'replace':
      return 'update';
    case 'delete':
      return 'delete';
    default:
      return 'no-change';
  }
}

/**
 * Calculate risk level for a resource change
 */
export function calculateResourceRisk(action: string, resourceType: string): 'low' | 'medium' | 'high' {
  const highRiskTypes = [
    'aws_db_instance',
    'aws_rds_cluster',
    'aws_s3_bucket',
    'aws_iam_role',
    'aws_security_group',
  ];
  
  if (action === 'delete') {
    return 'high';
  }
  
  if (highRiskTypes.some(type => resourceType.includes(type))) {
    return action === 'create' ? 'medium' : 'high';
  }
  
  return action === 'create' ? 'low' : 'medium';
}

/**
 * Calculate risk level for a runbook step
 */
export function calculateStepRisk(step: RunbookStep): 'low' | 'medium' | 'high' | 'critical' {
  const changes = step.metadata.changes || [];
  
  if (changes.length === 0) {
    return 'low';
  }
  
  const hasHighRisk = changes.some(c => c.risk === 'high');
  const hasDelete = changes.some(c => c.action === 'delete');
  
  if (hasHighRisk && hasDelete) {
    return 'critical';
  }
  
  if (hasHighRisk) {
    return 'high';
  }
  
  if (hasDelete) {
    return 'high';
  }
  
  const hasMediumRisk = changes.some(c => c.risk === 'medium');
  return hasMediumRisk ? 'medium' : 'low';
}

/**
 * Calculate step positions for layout
 */
export function calculateStepPositions(
  steps: RunbookStep[],
  config: RunbookConfig
): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>();
  const STEP_WIDTH = 200;
  const STEP_HEIGHT = 100;
  const HORIZONTAL_GAP = 80;
  const VERTICAL_GAP = 60;

  if (config.layout === 'sequential') {
    steps.forEach((step, index) => {
      positions.set(step.id, {
        x: 50 + index * (STEP_WIDTH + HORIZONTAL_GAP),
        y: 100,
      });
    });
  } else if (config.layout === 'dag') {
    // Simple DAG layout: level-based positioning
    const levels = new Map<string, number>();
    const calculateLevel = (stepId: string, visited = new Set<string>()): number => {
      if (levels.has(stepId)) {
        return levels.get(stepId)!;
      }
      
      if (visited.has(stepId)) {
        return 0; // Cycle detected
      }
      
      const step = steps.find(s => s.id === stepId);
      if (!step || step.dependsOn.length === 0) {
        levels.set(stepId, 0);
        return 0;
      }
      
      visited.add(stepId);
      const maxParentLevel = Math.max(
        ...step.dependsOn.map(depId => calculateLevel(depId, new Set(visited)))
      );
      const level = maxParentLevel + 1;
      levels.set(stepId, level);
      return level;
    };

    steps.forEach(step => calculateLevel(step.id));
    
    const levelCounts = new Map<number, number>();
    steps.forEach(step => {
      const level = levels.get(step.id) || 0;
      const count = levelCounts.get(level) || 0;
      positions.set(step.id, {
        x: 50 + level * (STEP_WIDTH + HORIZONTAL_GAP),
        y: 50 + count * (STEP_HEIGHT + VERTICAL_GAP),
      });
      levelCounts.set(level, count + 1);
    });
  }

  return positions;
}

/**
 * Get styling for a step based on its status and configuration
 */
export function getStepStyle(step: RunbookStep, config: RunbookConfig): {
  backgroundColor: string;
  borderColor: string;
  borderWidth: number;
  fontSize: number;
  fontFamily: string;
} {
  const status = step.metadata.status || 'pending';
  const risk = calculateStepRisk(step);
  
  let backgroundColor = '#f3f4f6'; // gray-100
  let borderColor = '#d1d5db'; // gray-300
  
  // Status-based colors
  if (status === 'running') {
    backgroundColor = '#dbeafe'; // blue-100
    borderColor = '#3b82f6'; // blue-500
  } else if (status === 'success') {
    backgroundColor = '#d1fae5'; // green-100
    borderColor = '#10b981'; // green-500
  } else if (status === 'failed' && config.highlightFailures) {
    backgroundColor = '#fee2e2'; // red-100
    borderColor = '#ef4444'; // red-500
  } else if (status === 'skipped') {
    backgroundColor = '#f3f4f6'; // gray-100
    borderColor = '#9ca3af'; // gray-400
  }
  
  // Risk-based border width
  let borderWidth = 2;
  if (risk === 'high' || risk === 'critical') {
    borderWidth = 3;
  }
  
  return {
    backgroundColor,
    borderColor,
    borderWidth,
    fontSize: 12,
    fontFamily: 'Inter, system-ui, sans-serif',
  };
}

/**
 * Analyze runbook complexity and risk
 */
export function analyzeRunbook(runbook: Runbook): {
  complexity: 'low' | 'medium' | 'high';
  risk: 'low' | 'medium' | 'high' | 'critical';
  estimatedDuration: number; // minutes
  criticalSteps: string[];
  recommendations: string[];
} {
  const stepCount = runbook.steps.length;
  const approvalCount = runbook.approvalGates.length;
  const criticalSteps: string[] = [];
  const recommendations: string[] = [];
  
  // Analyze steps
  let maxRisk: 'low' | 'medium' | 'high' | 'critical' = 'low';
  runbook.steps.forEach(step => {
    const stepRisk = calculateStepRisk(step);
    if (stepRisk === 'critical' || stepRisk === 'high') {
      criticalSteps.push(step.id);
    }
    if (stepRisk === 'critical') {
      maxRisk = 'critical';
    } else if (stepRisk === 'high' && maxRisk !== 'critical') {
      maxRisk = 'high';
    } else if (stepRisk === 'medium' && maxRisk === 'low') {
      maxRisk = 'medium';
    }
  });
  
  // Complexity assessment
  let complexity: 'low' | 'medium' | 'high' = 'low';
  if (stepCount > 50 || approvalCount > 5) {
    complexity = 'high';
  } else if (stepCount > 20 || approvalCount > 2) {
    complexity = 'medium';
  }
  
  // Estimated duration (rough estimate)
  const estimatedDuration = Math.ceil(stepCount * 0.5 + approvalCount * 5);
  
  // Recommendations
  if (criticalSteps.length > 0) {
    recommendations.push(`${criticalSteps.length} critical step(s) require careful review`);
  }
  if (approvalCount === 0 && maxRisk !== 'low') {
    recommendations.push('Consider adding approval gates for high-risk changes');
  }
  if (stepCount > 30) {
    recommendations.push('Consider breaking down into smaller runbooks');
  }
  
  return {
    complexity,
    risk: maxRisk,
    estimatedDuration,
    criticalSteps,
    recommendations,
  };
}
