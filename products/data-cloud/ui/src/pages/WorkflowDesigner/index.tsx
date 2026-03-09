import React from 'react';
import { WorkflowCanvas } from '../../components/workflow/WorkflowCanvas';

/**
 * Workflow Designer Page
 * 
 * Displays the workflow canvas for creating and editing workflows.
 */
export const WorkflowDesigner: React.FC = () => {
  return <WorkflowCanvas workflowId="current" />;
};

export default WorkflowDesigner;
