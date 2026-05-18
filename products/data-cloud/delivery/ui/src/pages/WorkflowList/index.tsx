import React from "react";
import { Navigate } from "react-router";

/**
 * WorkflowList legacy compatibility redirect.
 *
 * Redirects deep-links from the old /workflow-list route to the canonical
 * Pipelines surface at /pipelines.
 */
export const WorkflowList: React.FC = () => (
  <Navigate to="/pipelines" replace />
);

export default WorkflowList;
