import React from 'react';

interface ConnectionStatusProps {
  config: any;
}

export const ConnectionStatus: React.FC<ConnectionStatusProps> = ({ config }) => {
  const source = config?.bootstrap?.source || {};
  const isConnected = true; // This would come from a real connection status

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold mb-2">Connection Status</h2>
        <div className="space-y-2">
          <div className="flex justify-between">
            <span className="text-sm text-gray-600">Source</span>
            <span className="text-sm font-medium">{source.sourceId || 'Not configured'}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-sm text-gray-600">Status</span>
            <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
              isConnected 
                ? 'bg-green-100 text-green-800' 
                : 'bg-red-100 text-red-800'
            }`}>
              {isConnected ? 'Connected' : 'Disconnected'}
            </span>
          </div>
          {source.connectionOptions && (
            <div className="flex justify-between">
              <span className="text-sm text-gray-600">Type</span>
              <span className="text-sm font-medium">{source.connectionOptions.type}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
