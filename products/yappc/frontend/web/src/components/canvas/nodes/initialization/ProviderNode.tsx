// ============================================================================
// ProviderNode - Canvas node for provider connection visualization
//
// Features:
// - Provider branding and logo
// - Connection status indicator
// - Capabilities and features display
// - Account information
// - Connect/disconnect actions
// - Scope permissions
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';
import {
  GitBranch,
  Cloud,
  Database,
  Server,
  Check,
  X,
  AlertTriangle,
  Link,
  Unlink,
  RefreshCw,
  Shield,
  Clock,
  Settings,
  ExternalLink,
  Box,
  Zap,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import { Button } from '../../../ui/Button';

type ProviderType =
  | 'GITHUB'
  | 'GITLAB'
  | 'BITBUCKET'
  | 'VERCEL'
  | 'NETLIFY'
  | 'RAILWAY'
  | 'RENDER'
  | 'HEROKU'
  | 'AWS'
  | 'GCP'
  | 'AZURE'
  | 'SUPABASE'
  | 'PLANETSCALE'
  | 'NEON'
  | 'UPSTASH'
  | 'CLOUDFLARE'
  | 'CUSTOM';

type ProviderAuthStatus = 'NOT_CONNECTED' | 'CONNECTED' | 'EXPIRED' | 'ERROR';

type ResourceType =
  | 'REPOSITORY'
  | 'HOSTING'
  | 'DATABASE'
  | 'CACHE'
  | 'STORAGE'
  | 'CDN'
  | 'DOMAIN'
  | 'SSL'
  | 'CI_CD'
  | 'SECRETS'
  | 'MONITORING'
  | 'LOGGING';

type InfrastructureTier = 'FREE' | 'HOBBY' | 'PRO' | 'TEAM' | 'ENTERPRISE';

interface ProviderFeature {
  name: string;
  available: boolean;
  requiredTier?: InfrastructureTier;
}

interface ProviderLimits {
  maxProjects?: number;
  maxEnvironments?: number;
  maxBandwidth?: string;
  maxStorage?: string;
}

interface ProviderConnection {
  status: ProviderAuthStatus;
  accountName?: string;
  accountId?: string;
  expiresAt?: string;
  lastUsedAt?: string;
  scopes: string[];
}

interface ProviderCapabilities {
  resourceTypes: ResourceType[];
  tiers: InfrastructureTier[];
  regions: string[];
  features: ProviderFeature[];
  limits?: ProviderLimits;
}

export interface ProviderNodeData extends Record<string, unknown> {
  connection?: ProviderConnection;
  capabilities?: ProviderCapabilities;
  provider: ProviderType;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onRefresh?: () => void;
  onViewDocs?: () => void;
}

type ProviderCanvasNode = Node<ProviderNodeData, 'provider'>;

const providerConfig: Record<
  ProviderType,
  {
    name: string;
    color: string;
    bgColor: string;
    icon: typeof GitBranch;
    description: string;
  }
> = {
  GITHUB: {
    name: 'GitHub',
    color: 'text-white',
    bgColor: 'bg-[#24292e]',
    icon: GitBranch,
    description: 'Code hosting & CI/CD',
  },
  GITLAB: {
    name: 'GitLab',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    icon: GitBranch,
    description: 'DevOps platform',
  },
  BITBUCKET: {
    name: 'Bitbucket',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    icon: GitBranch,
    description: 'Code collaboration',
  },
  VERCEL: {
    name: 'Vercel',
    color: 'text-white',
    bgColor: 'bg-surface-inverse',
    icon: Cloud,
    description: 'Frontend cloud',
  },
  NETLIFY: {
    name: 'Netlify',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    icon: Cloud,
    description: 'Web hosting & serverless',
  },
  RAILWAY: {
    name: 'Railway',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    icon: Server,
    description: 'Infrastructure platform',
  },
  RENDER: {
    name: 'Render',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    icon: Server,
    description: 'Cloud application platform',
  },
  HEROKU: {
    name: 'Heroku',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    icon: Cloud,
    description: 'Container platform',
  },
  AWS: {
    name: 'AWS',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    icon: Cloud,
    description: 'Amazon Web Services',
  },
  GCP: {
    name: 'Google Cloud',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    icon: Cloud,
    description: 'Google Cloud Platform',
  },
  AZURE: {
    name: 'Azure',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    icon: Cloud,
    description: 'Microsoft Azure',
  },
  SUPABASE: {
    name: 'Supabase',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    icon: Database,
    description: 'Open source Firebase alternative',
  },
  PLANETSCALE: {
    name: 'PlanetScale',
    color: 'text-white',
    bgColor: 'bg-surface',
    icon: Database,
    description: 'Serverless MySQL',
  },
  NEON: {
    name: 'Neon',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    icon: Database,
    description: 'Serverless Postgres',
  },
  UPSTASH: {
    name: 'Upstash',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    icon: Database,
    description: 'Serverless Redis & Kafka',
  },
  CLOUDFLARE: {
    name: 'Cloudflare',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    icon: Cloud,
    description: 'CDN & edge computing',
  },
  CUSTOM: {
    name: 'Custom',
    color: 'text-fg-muted',
    bgColor: 'bg-muted',
    icon: Settings,
    description: 'Custom provider',
  },
};

const statusConfig: Record<
  ProviderAuthStatus,
  { color: string; bgColor: string; label: string; icon: typeof Check }
> = {
  NOT_CONNECTED: { color: 'text-fg-muted', bgColor: 'bg-muted', label: 'Not Connected', icon: Unlink },
  CONNECTED: { color: 'text-success-color', bgColor: 'bg-success-bg', label: 'Connected', icon: Check },
  EXPIRED: { color: 'text-warning-color', bgColor: 'bg-warning-bg', label: 'Expired', icon: AlertTriangle },
  ERROR: { color: 'text-destructive', bgColor: 'bg-destructive-bg', label: 'Error', icon: X },
};

const resourceTypeLabels: Record<ResourceType, string> = {
  REPOSITORY: 'Repository',
  HOSTING: 'Hosting',
  DATABASE: 'Database',
  CACHE: 'Cache',
  STORAGE: 'Storage',
  CDN: 'CDN',
  DOMAIN: 'Domain',
  SSL: 'SSL',
  CI_CD: 'CI/CD',
  SECRETS: 'Secrets',
  MONITORING: 'Monitoring',
  LOGGING: 'Logging',
};

const tierLabels: Record<InfrastructureTier, string> = {
  FREE: 'Free',
  HOBBY: 'Hobby',
  PRO: 'Pro',
  TEAM: 'Team',
  ENTERPRISE: 'Enterprise',
};

function ProviderNode({ data }: NodeProps<ProviderCanvasNode>) {
  const { connection, capabilities, provider, onConnect, onDisconnect, onRefresh, onViewDocs } = data;

  const providerInfo = providerConfig[provider];
  const ProviderIcon = providerInfo.icon;

  const status: ProviderAuthStatus = connection?.status ?? 'NOT_CONNECTED';
  const statusInfo = statusConfig[status];
  const StatusIcon = statusInfo.icon;

  const isConnected = status === 'CONNECTED';
  const isExpired = status === 'EXPIRED';

  return (
    <div className="bg-surface rounded-lg border border-border shadow-xl min-w-[320px] max-w-[380px]">
      <Handle type="target" position={Position.Left} className="!bg-info-color" />
      <Handle type="source" position={Position.Right} className="!bg-info-color" />

      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-3">
          <div className={cn('p-2 rounded-lg', providerInfo.bgColor)}>
            <ProviderIcon className={cn('w-6 h-6', providerInfo.color)} />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-fg">{providerInfo.name}</h3>
            <p className="text-xs text-fg-muted">{providerInfo.description}</p>
          </div>
        </div>
        <div className={cn('flex items-center gap-1.5 px-2 py-1 rounded-full text-xs', statusInfo.bgColor)}>
          <StatusIcon className={cn('w-3 h-3', statusInfo.color)} />
          <span className={statusInfo.color}>{statusInfo.label}</span>
        </div>
      </div>

      {/* Connection Info */}
      {connection && isConnected && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-center justify-between">
            <span className="text-xs text-fg-muted">Account</span>
            <span className="text-xs font-medium text-fg">
              {connection.accountName || connection.accountId || '—'}
            </span>
          </div>
          {connection.expiresAt && (
            <div className="flex items-center justify-between mt-2">
              <span className="text-xs text-fg-muted flex items-center gap-1">
                <Clock className="w-3 h-3" />
                Expires
              </span>
              <span className="text-xs text-fg">
                {new Date(connection.expiresAt).toLocaleDateString()}
              </span>
            </div>
          )}
          {connection.lastUsedAt && (
            <div className="flex items-center justify-between mt-2">
              <span className="text-xs text-fg-muted">Last used</span>
              <span className="text-xs text-fg">
                {new Date(connection.lastUsedAt).toLocaleDateString()}
              </span>
            </div>
          )}
        </div>
      )}

      {/* Scopes */}
      {connection && connection.scopes.length > 0 && (
        <div className="px-4 py-3 border-b border-border">
          <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2 flex items-center gap-1">
            <Shield className="w-3 h-3" />
            Permissions
          </h4>
          <div className="flex flex-wrap gap-1">
            {connection.scopes.slice(0, 6).map((scope: string) => (
              <span
                key={scope}
                className="text-xs px-2 py-0.5 bg-muted text-fg rounded"
              >
                {scope}
              </span>
            ))}
            {connection.scopes.length > 6 && (
              <span className="text-xs px-2 py-0.5 bg-muted text-fg-muted rounded">
                +{connection.scopes.length - 6} more
              </span>
            )}
          </div>
        </div>
      )}

      {/* Capabilities */}
      {capabilities && (
        <>
          {/* Resource Types */}
          <div className="px-4 py-3 border-b border-border">
            <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">
              Supported Resources
            </h4>
            <div className="flex flex-wrap gap-1">
              {capabilities.resourceTypes.map((type: ResourceType) => (
                <span
                  key={type}
                  className="text-xs px-2 py-0.5 bg-info-bg text-info-color rounded"
                >
                  {resourceTypeLabels[type]}
                </span>
              ))}
            </div>
          </div>

          {/* Tiers */}
          <div className="px-4 py-3 border-b border-border">
            <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">
              Available Tiers
            </h4>
            <div className="flex flex-wrap gap-1">
              {capabilities.tiers.map((tier: InfrastructureTier) => (
                <span
                  key={tier}
                  className={cn(
                    'text-xs px-2 py-0.5 rounded',
                    tier === 'FREE'
                      ? 'bg-success-bg text-success-color'
                      : tier === 'ENTERPRISE'
                      ? 'bg-info-bg text-info-color'
                      : 'bg-muted text-fg'
                  )}
                >
                  {tierLabels[tier]}
                </span>
              ))}
            </div>
          </div>

          {/* Regions */}
          {capabilities.regions.length > 0 && (
            <div className="px-4 py-3 border-b border-border">
              <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">
                Regions
              </h4>
              <div className="flex flex-wrap gap-1">
                {capabilities.regions.slice(0, 5).map((region: string) => (
                  <span
                    key={region}
                    className="text-xs px-2 py-0.5 bg-muted text-fg rounded"
                  >
                    {region}
                  </span>
                ))}
                {capabilities.regions.length > 5 && (
                  <span className="text-xs px-2 py-0.5 bg-muted text-fg-muted rounded">
                    +{capabilities.regions.length - 5} more
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Features */}
          {capabilities.features.length > 0 && (
            <div className="px-4 py-3 border-b border-border">
              <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2 flex items-center gap-1">
                <Zap className="w-3 h-3" />
                Features
              </h4>
              <div className="space-y-1">
                {capabilities.features.slice(0, 4).map((feature: ProviderFeature) => (
                  <div
                    key={feature.name}
                    className="flex items-center justify-between text-xs"
                  >
                    <span className="text-fg">{feature.name}</span>
                    {feature.available ? (
                      <Check className="w-3 h-3 text-success-color" />
                    ) : feature.requiredTier ? (
                      <span className="text-warning-color text-[10px]">
                        {tierLabels[feature.requiredTier]}+
                      </span>
                    ) : (
                      <X className="w-3 h-3 text-fg-muted" />
                    )}
                  </div>
                ))}
                {capabilities.features.length > 4 && (
                  <span className="text-xs text-fg-muted">
                    +{capabilities.features.length - 4} more features
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Limits */}
          {capabilities.limits && (
            <div className="px-4 py-3 border-b border-border">
              <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">
                Limits
              </h4>
              <div className="grid grid-cols-2 gap-2">
                {capabilities.limits.maxProjects && (
                  <div className="text-xs">
                    <span className="text-fg-muted">Projects:</span>{' '}
                    <span className="text-fg">{capabilities.limits.maxProjects}</span>
                  </div>
                )}
                {capabilities.limits.maxEnvironments && (
                  <div className="text-xs">
                    <span className="text-fg-muted">Environments:</span>{' '}
                    <span className="text-fg">{capabilities.limits.maxEnvironments}</span>
                  </div>
                )}
                {capabilities.limits.maxBandwidth && (
                  <div className="text-xs">
                    <span className="text-fg-muted">Bandwidth:</span>{' '}
                    <span className="text-fg">{capabilities.limits.maxBandwidth}</span>
                  </div>
                )}
                {capabilities.limits.maxStorage && (
                  <div className="text-xs">
                    <span className="text-fg-muted">Storage:</span>{' '}
                    <span className="text-fg">{capabilities.limits.maxStorage}</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      {/* Actions */}
      <div className="px-4 py-3 flex items-center justify-between">
        {onViewDocs && (
          <Button variant="ghost" size="sm"
            onClick={onViewDocs}
            className="flex items-center gap-1 text-xs text-fg-muted hover:text-fg transition-colors"
          >
            <ExternalLink className="w-3 h-3" />
            View Docs
          </Button>
        )}
        <div className="flex items-center gap-2">
          {isExpired && onRefresh && (
            <Button variant="ghost" size="sm"
              onClick={onRefresh}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-warning-color hover:opacity-90 rounded-lg text-xs text-white transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Refresh Token
            </Button>
          )}
          {!isConnected && onConnect && (
            <Button variant="ghost" size="sm"
              onClick={onConnect}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-info-color hover:opacity-90 rounded-lg text-xs text-white transition-colors"
            >
              <Link className="w-3.5 h-3.5" />
              Connect
            </Button>
          )}
          {isConnected && onDisconnect && (
            <Button variant="ghost" size="sm"
              onClick={onDisconnect}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-destructive hover:opacity-90 rounded-lg text-xs text-white transition-colors"
            >
              <Unlink className="w-3.5 h-3.5" />
              Disconnect
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

export default memo(ProviderNode);
