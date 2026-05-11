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
import { DataFreshnessBadge } from '@/components/dashboard/DataFreshnessBadge';
import { DashboardWidgetCard } from '@/components/dashboard/DashboardWidgetCard';
import { formatDateTime } from '@/lib/i18n/format';

interface ConnectorHealth {
  name: string;
  status: 'healthy' | 'degraded' | 'unhealthy' | 'not_configured';
  lastSync?: string;
  detail?: string;
}

interface ConnectorHealthWidgetProps {
  workspaceId: string;
  connectors?: ConnectorHealth[];
  isLoading?: boolean;
  isError?: boolean;
  unavailableReason?: string;
  source?: string;
  lastUpdated?: string | null;
}

export function ConnectorHealthWidget({
  workspaceId,
  connectors = [],
  isLoading = false,
  isError = false,
  unavailableReason,
  source,
  lastUpdated,
}: ConnectorHealthWidgetProps): React.ReactElement {
  if (isLoading) {
    return (
      <DashboardWidgetCard
        testId="connector-health-widget"
        title="Connector Health"
        state="loading"
      />
    );
  }

  if (isError) {
    return (
      <DashboardWidgetCard
        testId="connector-health-widget"
        title="Connector Health"
        state="error"
        message="Failed to load connector status"
      />
    );
  }

  if (connectors.length === 0 && unavailableReason) {
    return (
      <DashboardWidgetCard
        testId="connector-health-widget"
        title="Connector Health"
        state="unavailable"
        message={unavailableReason}
        stateMessageTestId="connector-health-unavailable"
        footer={<DataFreshnessBadge source={source} lastUpdated={lastUpdated} isPartial={false} />}
      />
    );
  }

  const getStatusColor = (status: ConnectorHealth['status']) => {
    switch (status) {
      case 'healthy':
        return 'text-green-800';
      case 'degraded':
        return 'text-yellow-600';
      case 'unhealthy':
        return 'text-red-600';
      case 'not_configured':
        return 'text-gray-700';
      default:
        return 'text-gray-800';
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
    <DashboardWidgetCard
      testId="connector-health-widget"
      title="Connector Health"
      footer={<DataFreshnessBadge source={source} lastUpdated={lastUpdated} isPartial={false} />}
    >
      <div className="space-y-2">
        {connectors.length === 0 ? (
          <p className="text-xs text-gray-700">No connectors configured</p>
        ) : (
          connectors.map((connector) => (
            <div key={connector.name} className="flex justify-between items-center">
              <div className="flex items-start gap-2">
                <div
                  className={`w-2 h-2 mt-1 rounded-full ${getStatusDot(connector.status)}`}
                />
                <div>
                  <span className="text-xs text-gray-900">{connector.name}</span>
                  {connector.detail ? (
                    <p className="text-[11px] text-gray-500">{connector.detail}</p>
                  ) : null}
                  {connector.lastSync ? (
                    <p className="text-[11px] text-gray-500">
                      Last sync: {formatDateTime(connector.lastSync)}
                    </p>
                  ) : null}
                </div>
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
    </DashboardWidgetCard>
  );
}
