import React from 'react';
import EnhancedPageUsageDashboard from '../dashboard/EnhancedPageUsageDashboard';

/**
 * Page component that renders the enhanced usage dashboard
 */
export const PageUsagePage: React.FC = () => (
  <div className="space-y-6">
    <EnhancedPageUsageDashboard variant="full" title="Analytics Dashboard" />
  </div>
);
