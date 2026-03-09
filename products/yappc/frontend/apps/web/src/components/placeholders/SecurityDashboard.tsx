/**
 * SecurityDashboard Placeholder Component
 * 
 * Temporary placeholder until the actual component is implemented in @ghatana/yappc-ui
 */

import React from 'react';

interface SecurityDashboardProps {
  projectId?: string;
  className?: string;
}

export const SecurityDashboard: React.FC<SecurityDashboardProps> = ({ projectId }) => {
  return (
    <div className="p-8 bg-zinc-900 rounded-lg border border-zinc-800">
      <div className="text-center">
        <h3 className="text-lg font-semibold text-white mb-2">Security Dashboard</h3>
        <p className="text-zinc-400">Component implementation pending</p>
        {projectId && <p className="text-xs text-zinc-500 mt-2">Project: {projectId}</p>}
      </div>
    </div>
  );
};
