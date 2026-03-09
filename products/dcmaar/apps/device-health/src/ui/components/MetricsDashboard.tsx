import React from 'react';

interface MetricsDashboardProps {
  metrics: Record<string, any>;
  isLoading: boolean;
}

export const MetricsDashboard: React.FC<MetricsDashboardProps> = ({ 
  metrics, 
  isLoading 
}) => {
  if (isLoading) {
    return <div className="py-4 text-center text-gray-500">Loading metrics...</div>;
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Metrics</h2>
      {Object.keys(metrics).length > 0 ? (
        <div className="grid grid-cols-2 gap-4">
          {Object.entries(metrics).map(([key, value]) => (
            <div key={key} className="p-3 border rounded-lg bg-white">
              <div className="text-sm font-medium text-gray-500">
                {key.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())}
              </div>
              <div className="text-xl font-bold mt-1">
                {typeof value === 'number' ? value.toLocaleString() : String(value)}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="text-center py-8 text-gray-500">
          No metrics available. Start capturing data to see metrics here.
        </div>
      )}
    </div>
  );
};
