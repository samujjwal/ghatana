/**
 * Workflow status widget.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for active workflow status
 * @doc.layer frontend
 */
import React from 'react';

export const WorkflowStatusWidget: React.FC = () => (
  <article
    aria-labelledby="workflow-status-title"
    data-testid="workflow-status-widget"
    className="border rounded-lg p-4"
  >
    <h2
      id="workflow-status-title"
      className="text-sm font-semibold text-gray-700"
    >
      Workflow Status
    </h2>
    <p className="text-xs text-gray-400 mt-2">No active workflows</p>
  </article>
);
