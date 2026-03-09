/**
 * Runbook Execution and Lifecycle Management
 * 
 * Functions for executing runbook steps, managing state transitions, and handling rollbacks.
 */

import type { Runbook, RunbookStep, ResourceChange, StepStatus, ExecutionStatus, ExecutionHistory } from './types';

/**
 * Execute a runbook step
 * 
 * @param runbook - The runbook containing the step
 * @param stepId - ID of the step to execute
 * @param executor - Async function that executes the step and returns result
 * @returns Updated runbook with step marked as running
 */
export function executeStep(
  runbook: Runbook,
  stepId: string,
  executor: (step: RunbookStep) => Promise<{ 
    success: boolean; 
    output?: string; 
    error?: string; 
    changes?: ResourceChange[] 
  }>
): Runbook {
  const step = runbook.steps.find(s => s.id === stepId);
  
  if (!step) {
    throw new Error(`Step ${stepId} not found`);
  }
  
  // Check dependencies
  const dependenciesMet = step.dependsOn.every(depId => {
    const depStep = runbook.steps.find(s => s.id === depId);
    return depStep?.metadata.status === 'success';
  });
  
  if (!dependenciesMet) {
    throw new Error(`Dependencies not met for step ${stepId}`);
  }
  
  // Update step status to running
  step.metadata.status = 'running';
  step.metadata.startTime = new Date();
  
  return {
    ...runbook,
    metadata: {
      ...runbook.metadata,
      status: 'running',
    },
  };
}

/**
 * Complete a runbook step with result
 * 
 * @param runbook - The runbook containing the step
 * @param stepId - ID of the step to complete
 * @param result - Execution result (success, output, error, changes)
 * @returns Updated runbook with step marked as complete and overall status updated
 */
export function completeStep(
  runbook: Runbook,
  stepId: string,
  result: { success: boolean; output?: string; error?: string; changes?: ResourceChange[] }
): Runbook {
  const status: StepStatus = result.success ? 'success' : 'failed';
  const now = new Date();
  
  const updatedSteps = runbook.steps.map(step => {
    if (step.id === stepId) {
      const duration = step.metadata.startTime 
        ? now.getTime() - new Date(step.metadata.startTime).getTime()
        : undefined;
        
      return {
        ...step,
        metadata: {
          ...step.metadata,
          status,
          endTime: now,
          duration,
          output: result.output,
          error: result.error,
          changes: result.changes,
        },
      };
    }
    return step;
  });

  const completedSteps = updatedSteps.filter(s => s.metadata.status === 'success').length;
  const failedSteps = updatedSteps.filter(s => s.metadata.status === 'failed').length;
  const allStepsCompleted = updatedSteps.every(s => 
    s.metadata.status === 'success' || 
    s.metadata.status === 'failed' || 
    s.metadata.status === 'skipped'
  );
  
  const runbookStatus: ExecutionStatus = allStepsCompleted 
    ? (failedSteps > 0 ? 'failed' : 'completed')
    : 'running';

  const updatedRunbook: Runbook = {
    ...runbook,
    steps: updatedSteps,
    metadata: {
      ...runbook.metadata,
      status: runbookStatus,
      completedSteps,
      failedSteps,
      endTime: allStepsCompleted ? now : runbook.metadata?.endTime,
      duration: allStepsCompleted 
        ? (runbook.metadata?.startTime ? now.getTime() - new Date(runbook.metadata.startTime).getTime() : undefined) 
        : runbook.metadata?.duration,
    },
  };

  // Set start time if this is the first step
  if (runbook.metadata?.startTime === undefined) {
    updatedRunbook.metadata.startTime = now;
  }

  return updatedRunbook;
}

/**
 * Rollback a runbook execution
 * 
 * Creates rollback steps for all completed steps in reverse order.
 * 
 * @param runbook - The runbook to rollback
 * @returns Updated runbook with rollback steps added
 */
export function rollbackRunbook(runbook: Runbook): Runbook {
  const completedSteps = runbook.steps
    .filter(s => s.metadata.status === 'success' && s.rollbackStep)
    .reverse();

  const rollbackSteps: RunbookStep[] = completedSteps.map((step, index) => ({
    id: `rollback-${step.id}`,
    name: `Rollback: ${step.name}`,
    type: 'task',
    command: `rollback ${step.command}`,
    dependsOn: index > 0 ? [`rollback-${completedSteps[index - 1].id}`] : [],
    metadata: {
      status: 'pending',
    },
  }));

  return {
    ...runbook,
    steps: [...runbook.steps, ...rollbackSteps],
    metadata: {
      ...runbook.metadata,
      status: 'rolled-back',
      rollbackSteps: rollbackSteps.length,
    },
  };
}

/**
 * Get execution history for a runbook
 * 
 * @param runbook - The runbook to get history for
 * @returns Execution history record
 */
export function getExecutionHistory(runbook: Runbook): ExecutionHistory {
  const allChanges: ResourceChange[] = [];
  runbook.steps.forEach(step => {
    if (step.metadata.changes) {
      allChanges.push(...step.metadata.changes);
    }
  });

  return {
    id: `history-${runbook.id}-${Date.now()}`,
    runbookId: runbook.id,
    runbookName: runbook.name,
    status: runbook.metadata.status || 'pending',
    startTime: runbook.metadata.startTime || new Date(),
    endTime: runbook.metadata.endTime,
    duration: runbook.metadata.duration,
    triggeredBy: runbook.metadata.author || 'system',
    steps: runbook.steps,
    approvals: runbook.approvalGates,
    variables: runbook.variables,
    changes: allChanges,
  };
}
