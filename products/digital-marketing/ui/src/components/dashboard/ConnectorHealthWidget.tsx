/**
 * P1-015: Connector Health Widget for DMOS Command Center.
 *
 * Displays health status of external connectors (Google Ads, Meta, etc.).
 *
 * @doc.type component
 * @doc.purpose Connector health status widget for dashboard
 * @doc.layer frontend
 */
import React from 'react';

interface ConnectorHealth {
  name: string;
  status: 'healthy' | 'degraded' | 'unhealthy' | 'not_configured';
  lastSync?: string;
}

interface ConnectorHealthWidgetProps {
  workspaceId: string;
  connectors?: ConnectorHealth[];
  isLoading?: boolean;
  isError?: boolean;
}

export function ConnectorHealthWidget({
  workspaceId,
  connectors = [],
  isLoading = false,
  isError = false,
}: ConnectorHealthWidgetProps): React.ReactElement {
  if (isLoading) {
    return (
      <div
        data-testid="connector-health-widget"
        className="bg-white border border-gray-200 rounded-lg p-4"
      >
        <h2 className="text-sm font-semibold text-gray-700 mb-3">Connector Health</h2>
        <div className="animate-pulse h-20 bg-gray-100 rounded" />
      </div>
    );
  }

  if (isError) {
    return (
      <div
        data-testid="connector-health-widget"
        className="bg-white border border-gray-200 rounded-lg p-4"
      >
        <h2 className="text-sm font-semibold text-gray-700 mb-3">Connector Health</h2>
        <div className="text-sm text-red-600">Failed to load connector status</div>
      </div>
    );
  }

  const getStatusColor = (status: ConnectorHealth['status']) => {
    switch (status) {
      case 'healthy':
        return 'text-green-600';
      case 'degraded':
        return 'text-yellow-600';
      case 'unhealthy':
        return 'text-red-600';
      case 'not_configured':
        return 'text-gray-400';
      default:
        return 'text-gray-600';
    }
  };

  const getStatusDot = (status: ConnectorHealth['status']) => {
    switch (status) {
      case 'healthy':
        return 'bg-green-500';
      case 'degraded':
        return 'bg-yellow-500';
      case 'unhealthy':
        return 'bg-red-500';
      case 'not_configured':
        return 'bg-gray-400';
      default:
        return 'bg-gray-300';
    }
  };

  return (
    <div
      data-testid="connector-health-widget"
      className="bg-white border border-gray-200 rounded-lg p-4"
    >
      <h2 className="text-sm font-semibold text-gray-700 mb-3">Connector Health</h2>
      <div className="space-y-2">
        {connectors.length === 0 ? (
          <p className="text-xs text-gray-500">No connectors configured</p>
        ) : (
          connectors.map((connector) => (
            <div key={connector.name} className="flex justify-between items-center">
              <div className="flex items-center gap-2">
                <div
                  className={`w-2 h-2 rounded-full ${getStatusDot(connector.status)}`}
                />
                <span className="text-xs text-gray-700">{connector.name}</span>
              </div>
              <span
                className={`text-xs font-medium ${getStatusColor(connector.status)}`}
              >
                {connector.status.replace('_', ' ')}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
