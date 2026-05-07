import React from 'react';
import { ArrowLeft, Workflow as WorkflowIcon } from 'lucide-react';
import { Link } from 'react-router';
import { WorkflowCanvas } from '../../features/workflow/components/WorkflowCanvas';

/**
 * Workflow Designer Page
 *
 * Renders the workflow canvas for creating or editing a workflow.
 * Workflow identity is managed via the feature workflow store.
 */
export const WorkflowDesigner: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-50 px-6 py-6 dark:bg-gray-950">
      <div className="mx-auto max-w-7xl">
        <div className="mb-4 flex items-center gap-3">
          <Link
            to="/pipelines"
            className="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-800"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to pipelines
          </Link>
        </div>

        <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-900 dark:bg-blue-950/30">
          <div className="flex items-start gap-3">
            <div className="rounded-lg bg-blue-100 p-2 dark:bg-blue-900/50">
              <WorkflowIcon className="h-5 w-5 text-blue-600 dark:text-blue-300" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-gray-900 dark:text-white">Advanced Pipeline Editor</h1>
              <p className="mt-1 text-sm text-blue-900/80 dark:text-blue-100/80">
                Use this canvas when the flow itself needs structural changes. Day-to-day triage, recent run review, and next-step decisions should stay in the calmer pipelines list.
              </p>
            </div>
          </div>
        </div>

        <WorkflowCanvas />
      </div>
    </div>
  );
};

export default WorkflowDesigner;
