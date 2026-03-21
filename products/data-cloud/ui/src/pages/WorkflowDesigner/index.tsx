import React from 'react';
import { WorkflowCanvas } from '../../features/workflow/components/WorkflowCanvas';

/**
 * Workflow Designer Page
 *
 * Renders the workflow canvas for creating or editing a workflow.
 * Workflow identity is managed via the feature workflow store.
 */
export const WorkflowDesigner: React.FC = () => {
  return <WorkflowCanvas />;
};

export default WorkflowDesigner;
