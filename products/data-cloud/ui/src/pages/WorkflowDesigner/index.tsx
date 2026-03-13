import React from 'react';
import { useParams } from 'react-router';
import { WorkflowCanvas } from '../../components/workflow/WorkflowCanvas';

/**
 * Workflow Designer Page
 *
 * Loads the workflow canvas for creating (id='new') or editing an existing workflow.
 * The workflow id is sourced from the URL parameter ':id'.
 */
export const WorkflowDesigner: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  return <WorkflowCanvas workflowId={id} />;
};

export default WorkflowDesigner;
