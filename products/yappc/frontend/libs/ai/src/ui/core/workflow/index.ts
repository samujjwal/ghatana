/**
 * Workflow Step Components Index
 *
 * Exports all workflow step components for the AI-powered development wizard.
 *
 * @doc.type module
 * @doc.purpose Module exports
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Step Components
export { IntentStep, type IntentStepProps, type IntentStepData, type ParsedIntent } from './IntentStep';
export { ContextStep, type ContextStepProps, type ContextStepData, type ContextFile, type CodebaseAnalysis } from './ContextStep';
export { PlanStep, type PlanStepProps, type PlanStepData, type PlanStep as PlanStepItem } from './PlanStep';
export { CodeStep, type CodeStepProps, type CodeStepData, type GeneratedFile } from './CodeStep';
export { TestStep, type TestStepProps, type TestStepData, type GeneratedTest } from './TestStep';
export { PreviewStep, type PreviewStepProps, type PreviewStepData } from './PreviewStep';
export { DeployStep, type DeployStepProps, type DeployStepData } from './DeployStep';
export { CompleteStep, type CompleteStepProps, type WorkflowSummary } from './CompleteStep';

// Re-export types for convenience
export type WorkflowStep =
    | 'intent'
    | 'context'
    | 'plan'
    | 'code'
    | 'test'
    | 'preview'
    | 'deploy'
    | 'complete';

export const WORKFLOW_STEPS: WorkflowStep[] = [
    'intent',
    'context',
    'plan',
    'code',
    'test',
    'preview',
    'deploy',
    'complete',
];

export const WORKFLOW_STEP_LABELS: Record<WorkflowStep, string> = {
    intent: 'Describe Intent',
    context: 'Gather Context',
    plan: 'Review Plan',
    code: 'Generate Code',
    test: 'Generate Tests',
    preview: 'Preview Changes',
    deploy: 'Deploy',
    complete: 'Complete',
};
