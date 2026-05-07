// Re-export WorkflowCanvas from the canonical features implementation to avoid duplication.
// The features/workflow implementation is the authoritative source: it includes StartNode,
// EndNode, full undo/redo history, and the complete Jotai action-atom API.
export { WorkflowCanvas, type WorkflowCanvasProps } from '../../features/workflow/components/WorkflowCanvas';
export { WorkflowCanvas as default } from '../../features/workflow/components/WorkflowCanvas';
