// Re-export ValidationPanel from the canonical features implementation to avoid duplication.
// The features/workflow implementation is the authoritative source.
export { ValidationPanel, type ValidationPanelProps } from '../../features/workflow/components/ValidationPanel';
export { ValidationPanel as default } from '../../features/workflow/components/ValidationPanel';
