/**
 * Integration Hub Component
 *
 * Displays registered integrations with connection status, health,
 * and management actions. Follows RecommendationCard composition pattern.
 *
 * @doc.type component
 * @doc.purpose Third-party integration management UI
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { type ReactNode } from 'react';
import {
  Plug,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Clock,
  Trash2,
  RefreshCw,
  Link2,
  Unlink,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent } from '@ghatana/design-system';
import type {
  Integration,
  IntegrationStatus,
  IntegrationCategory,
} from '../../services/integrations/IntegrationService';

// ============================================================================
// Types
// ============================================================================

export interface IntegrationHubProps {
  integrations: Integration[];
  onConnect?: (id: string) => void;
  onDisconnect?: (id: string) => void;
  onRemove?: (id: string) => void;
  onRefresh?: () => void;
  className?: string;
}

// ============================================================================
// Sub-components
// ============================================================================

interface StatusBadgeProps {
  status: IntegrationStatus;
}

const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const config: Record<IntegrationStatus, { icon: ReactNode; color: string; label: string }> = {
    connected: { icon: <CheckCircle className="w-3.5 h-3.5" />, color: 'text-success-color', label: 'Connected' },
    disconnected: { icon: <XCircle className="w-3.5 h-3.5" />, color: 'text-fg-muted', label: 'Disconnected' },
    error: { icon: <AlertTriangle className="w-3.5 h-3.5" />, color: 'text-destructive', label: 'Error' },
    pending: { icon: <Clock className="w-3.5 h-3.5" />, color: 'text-warning-color', label: 'Pending' },
  };

  const { icon, color, label } = config[status];

  return (
    <span className={`inline-flex items-center gap-1 text-xs font-medium ${color}`}>
      {icon}
      {label}
    </span>
  );
};

const categoryLabels: Record<IntegrationCategory, string> = {
  vcs: 'Version Control',
  'ci-cd': 'CI / CD',
  chat: 'Chat',
  monitoring: 'Monitoring',
  custom: 'Custom',
};

interface IntegrationCardProps {
  integration: Integration;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onRemove?: () => void;
}

const IntegrationCard: React.FC<IntegrationCardProps> = ({
  integration,
  onConnect,
  onDisconnect,
  onRemove,
}) => (
  <Card className="mb-2">
    <CardContent className="p-3">
      <Box className="flex items-start justify-between mb-2">
        <Box className="flex items-center gap-2">
          <Plug className="w-4 h-4 text-info-color" />
          <Box>
            <Typography className="font-medium text-sm">{integration.name}</Typography>
            <Typography className="text-xs text-fg-muted">
              {categoryLabels[integration.category]}
            </Typography>
          </Box>
        </Box>
        <StatusBadge status={integration.status} />
      </Box>

      <Typography className="text-xs text-fg-muted dark:text-fg-muted mb-3">
        {integration.description}
      </Typography>

      {integration.lastSyncAt && (
        <Typography className="text-xs text-fg-muted mb-2">
          Last sync {new Date(integration.lastSyncAt).toLocaleString()}
        </Typography>
      )}

      <Box className="flex gap-2">
        {(integration.status === 'disconnected' || integration.status === 'pending') && onConnect && (
          <Button size="sm" variant="contained" onClick={onConnect}>
            <Link2 className="w-3.5 h-3.5 mr-1" />
            Connect
          </Button>
        )}
        {integration.status === 'connected' && onDisconnect && (
          <Button size="sm" variant="outlined" onClick={onDisconnect}>
            <Unlink className="w-3.5 h-3.5 mr-1" />
            Disconnect
          </Button>
        )}
        {onRemove && (
          <Button size="sm" variant="text" onClick={onRemove}>
            <Trash2 className="w-3.5 h-3.5" />
          </Button>
        )}
      </Box>
    </CardContent>
  </Card>
);

// ============================================================================
// Main Component
// ============================================================================

export const IntegrationHub: React.FC<IntegrationHubProps> = ({
  integrations,
  onConnect,
  onDisconnect,
  onRemove,
  onRefresh,
  className = '',
}) => {
  const connected = integrations.filter((i) => i.status === 'connected').length;

  return (
    <Box className={`space-y-4 ${className}`}>
      {/* Header */}
      <Box className="flex items-center justify-between">
        <Box className="flex items-center gap-2">
          <Plug className="w-5 h-5 text-info-color" />
          <Typography className="font-semibold">Integrations</Typography>
        </Box>
        <Box className="flex items-center gap-2">
          <Typography className="text-sm text-fg-muted">
            {connected}/{integrations.length} connected
          </Typography>
          {onRefresh && (
            <Button size="sm" variant="text" onClick={onRefresh}>
              <RefreshCw className="w-4 h-4" />
            </Button>
          )}
        </Box>
      </Box>

      {/* Integration List */}
      {integrations.length === 0 ? (
        <Typography className="text-sm text-fg-muted text-center py-8">
          No integrations configured yet.
        </Typography>
      ) : (
        integrations.map((integration) => (
          <IntegrationCard
            key={integration.id}
            integration={integration}
            onConnect={onConnect ? () => onConnect(integration.id) : undefined}
            onDisconnect={onDisconnect ? () => onDisconnect(integration.id) : undefined}
            onRemove={onRemove ? () => onRemove(integration.id) : undefined}
          />
        ))
      )}
    </Box>
  );
};

export default IntegrationHub;
