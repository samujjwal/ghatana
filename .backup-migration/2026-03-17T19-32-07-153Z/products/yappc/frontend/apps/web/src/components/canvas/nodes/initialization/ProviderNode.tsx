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
import type { NodeProps } from '@xyflow/react';
import {
  Github,
  Gitlab,
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
import type {
  ProviderConnection,
  ProviderCapabilities,
  ProviderType,
  ProviderAuthStatus,
  ResourceType,
  InfrastructureTier,
} from '@ghatana/yappc-api';

export interface ProviderNodeData {
  connection?: ProviderConnection;
  capabilities?: ProviderCapabilities;
  provider: ProviderType;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onRefresh?: () => void;
  onViewDocs?: () => void;
}

const providerConfig: Record<
  ProviderType,
  {
    name: string;
    color: string;
    bgColor: string;
    icon: typeof Github;
    description: string;
  }
> = {
  GITHUB: {
    name: 'GitHub',
    color: 'text-white',
    bgColor: 'bg-[#24292e]',
    icon: Github,
    description: 'Code hosting & CI/CD',
  },
  GITLAB: {
    name: 'GitLab',
    color: 'text-orange-400',
    bgColor: 'bg-[#FC6D26]/20',
    icon: Gitlab,
    description: 'DevOps platform',
  },
  BITBUCKET: {
    name: 'Bitbucket',
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/20',
    icon: Github,
    description: 'Code collaboration',
  },
  VERCEL: {
    name: 'Vercel',
    color: 'text-white',
    bgColor: 'bg-slate-900',
    icon: Cloud,
    description: 'Frontend cloud',
  },
  NETLIFY: {
    name: 'Netlify',
    color: 'text-teal-400',
    bgColor: 'bg-teal-500/20',
    icon: Cloud,
    description: 'Web hosting & serverless',
  },
  RAILWAY: {
    name: 'Railway',
    color: 'text-purple-400',
    bgColor: 'bg-purple-500/20',
    icon: Server,
    description: 'Infrastructure platform',
  },
  RENDER: {
    name: 'Render',
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-500/20',
    icon: Server,
    description: 'Cloud application platform',
  },
  HEROKU: {
    name: 'Heroku',
    color: 'text-violet-400',
    bgColor: 'bg-violet-500/20',
    icon: Cloud,
    description: 'Container platform',
  },
  AWS: {
    name: 'AWS',
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/20',
    icon: Cloud,
    description: 'Amazon Web Services',
  },
  GCP: {
    name: 'Google Cloud',
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/20',
    icon: Cloud,
    description: 'Google Cloud Platform',
  },
  AZURE: {
    name: 'Azure',
    color: 'text-sky-400',
    bgColor: 'bg-sky-500/20',
    icon: Cloud,
    description: 'Microsoft Azure',
  },
  SUPABASE: {
    name: 'Supabase',
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-500/20',
    icon: Database,
    description: 'Open source Firebase alternative',
  },
  PLANETSCALE: {
    name: 'PlanetScale',
    color: 'text-white',
    bgColor: 'bg-slate-800',
    icon: Database,
    description: 'Serverless MySQL',
  },
  NEON: {
    name: 'Neon',
    color: 'text-green-400',
    bgColor: 'bg-green-500/20',
    icon: Database,
    description: 'Serverless Postgres',
  },
  UPSTASH: {
    name: 'Upstash',
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-500/20',
    icon: Database,
    description: 'Serverless Redis & Kafka',
  },
  CLOUDFLARE: {
    name: 'Cloudflare',
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/20',
    icon: Cloud,
    description: 'CDN & edge computing',
  },
  CUSTOM: {
    name: 'Custom',
    color: 'text-gray-400',
    bgColor: 'bg-gray-500/20',
    icon: Settings,
    description: 'Custom provider',
  },
};

const statusConfig: Record<
  ProviderAuthStatus,
  { color: string; bgColor: string; label: string; icon: typeof Check }
> = {
  NOT_CONNECTED: { color: 'text-gray-400', bgColor: 'bg-gray-500/20', label: 'Not Connected', icon: Unlink },
  CONNECTED: { color: 'text-green-400', bgColor: 'bg-green-500/20', label: 'Connected', icon: Check },
  EXPIRED: { color: 'text-yellow-400', bgColor: 'bg-yellow-500/20', label: 'Expired', icon: AlertTriangle },
  ERROR: { color: 'text-red-400', bgColor: 'bg-red-500/20', label: 'Error', icon: X },
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

function ProviderNode({ data }: NodeProps<ProviderNodeData>) {
  const { connection, capabilities, provider, onConnect, onDisconnect, onRefresh, onViewDocs } = data;

  const providerInfo = providerConfig[provider];
  const ProviderIcon = providerInfo.icon;

  const status: ProviderAuthStatus = connection?.status ?? 'NOT_CONNECTED';
  const statusInfo = statusConfig[status];
  const StatusIcon = statusInfo.icon;

  const isConnected = status === 'CONNECTED';
  const isExpired = status === 'EXPIRED';

  return (
    <div className="bg-slate-800 rounded-lg border border-slate-700 shadow-xl min-w-[320px] max-w-[380px]">
      <Handle type="target" position={Position.Left} className="!bg-cyan-500" />
      <Handle type="source" position={Position.Right} className="!bg-cyan-500" />

      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-700">
        <div className="flex items-center gap-3">
          <div className={cn('p-2 rounded-lg', providerInfo.bgColor)}>
            <ProviderIcon className={cn('w-6 h-6', providerInfo.color)} />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">{providerInfo.name}</h3>
            <p className="text-xs text-slate-400">{providerInfo.description}</p>
          </div>
        </div>
        <div className={cn('flex items-center gap-1.5 px-2 py-1 rounded-full text-xs', statusInfo.bgColor)}>
          <StatusIcon className={cn('w-3 h-3', statusInfo.color)} />
          <span className={statusInfo.color}>{statusInfo.label}</span>
        </div>
      </div>

      {/* Connection Info */}
      {connection && isConnected && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center justify-between">
            <span className="text-xs text-slate-400">Account</span>
            <span className="text-xs font-medium text-white">
              {connection.accountName || connection.accountId || '—'}
            </span>
          </div>
          {connection.expiresAt && (
            <div className="flex items-center justify-between mt-2">
              <span className="text-xs text-slate-400 flex items-center gap-1">
                <Clock className="w-3 h-3" />
                Expires
              </span>
              <span className="text-xs text-slate-300">
                {new Date(connection.expiresAt).toLocaleDateString()}
              </span>
            </div>
          )}
          {connection.lastUsedAt && (
            <div className="flex items-center justify-between mt-2">
              <span className="text-xs text-slate-400">Last used</span>
              <span className="text-xs text-slate-300">
                {new Date(connection.lastUsedAt).toLocaleDateString()}
              </span>
            </div>
          )}
        </div>
      )}

      {/* Scopes */}
      {connection && connection.scopes.length > 0 && (
        <div className="px-4 py-3 border-b border-slate-700">
          <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2 flex items-center gap-1">
            <Shield className="w-3 h-3" />
            Permissions
          </h4>
          <div className="flex flex-wrap gap-1">
            {connection.scopes.slice(0, 6).map((scope) => (
              <span
                key={scope}
                className="text-xs px-2 py-0.5 bg-slate-700 text-slate-300 rounded"
              >
                {scope}
              </span>
            ))}
            {connection.scopes.length > 6 && (
              <span className="text-xs px-2 py-0.5 bg-slate-700 text-slate-400 rounded">
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
          <div className="px-4 py-3 border-b border-slate-700">
            <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">
              Supported Resources
            </h4>
            <div className="flex flex-wrap gap-1">
              {capabilities.resourceTypes.map((type) => (
                <span
                  key={type}
                  className="text-xs px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded"
                >
                  {resourceTypeLabels[type]}
                </span>
              ))}
            </div>
          </div>

          {/* Tiers */}
          <div className="px-4 py-3 border-b border-slate-700">
            <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">
              Available Tiers
            </h4>
            <div className="flex flex-wrap gap-1">
              {capabilities.tiers.map((tier) => (
                <span
                  key={tier}
                  className={cn(
                    'text-xs px-2 py-0.5 rounded',
                    tier === 'FREE'
                      ? 'bg-green-500/20 text-green-400'
                      : tier === 'ENTERPRISE'
                      ? 'bg-purple-500/20 text-purple-400'
                      : 'bg-slate-700 text-slate-300'
                  )}
                >
                  {tierLabels[tier]}
                </span>
              ))}
            </div>
          </div>

          {/* Regions */}
          {capabilities.regions.length > 0 && (
            <div className="px-4 py-3 border-b border-slate-700">
              <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">
                Regions
              </h4>
              <div className="flex flex-wrap gap-1">
                {capabilities.regions.slice(0, 5).map((region) => (
                  <span
                    key={region}
                    className="text-xs px-2 py-0.5 bg-slate-700 text-slate-300 rounded"
                  >
                    {region}
                  </span>
                ))}
                {capabilities.regions.length > 5 && (
                  <span className="text-xs px-2 py-0.5 bg-slate-700 text-slate-400 rounded">
                    +{capabilities.regions.length - 5} more
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Features */}
          {capabilities.features.length > 0 && (
            <div className="px-4 py-3 border-b border-slate-700">
              <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2 flex items-center gap-1">
                <Zap className="w-3 h-3" />
                Features
              </h4>
              <div className="space-y-1">
                {capabilities.features.slice(0, 4).map((feature) => (
                  <div
                    key={feature.name}
                    className="flex items-center justify-between text-xs"
                  >
                    <span className="text-slate-300">{feature.name}</span>
                    {feature.available ? (
                      <Check className="w-3 h-3 text-green-400" />
                    ) : feature.requiredTier ? (
                      <span className="text-yellow-400 text-[10px]">
                        {tierLabels[feature.requiredTier]}+
                      </span>
                    ) : (
                      <X className="w-3 h-3 text-slate-500" />
                    )}
                  </div>
                ))}
                {capabilities.features.length > 4 && (
                  <span className="text-xs text-slate-500">
                    +{capabilities.features.length - 4} more features
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Limits */}
          {capabilities.limits && (
            <div className="px-4 py-3 border-b border-slate-700">
              <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">
                Limits
              </h4>
              <div className="grid grid-cols-2 gap-2">
                {capabilities.limits.maxProjects && (
                  <div className="text-xs">
                    <span className="text-slate-400">Projects:</span>{' '}
                    <span className="text-white">{capabilities.limits.maxProjects}</span>
                  </div>
                )}
                {capabilities.limits.maxEnvironments && (
                  <div className="text-xs">
                    <span className="text-slate-400">Environments:</span>{' '}
                    <span className="text-white">{capabilities.limits.maxEnvironments}</span>
                  </div>
                )}
                {capabilities.limits.maxBandwidth && (
                  <div className="text-xs">
                    <span className="text-slate-400">Bandwidth:</span>{' '}
                    <span className="text-white">{capabilities.limits.maxBandwidth}</span>
                  </div>
                )}
                {capabilities.limits.maxStorage && (
                  <div className="text-xs">
                    <span className="text-slate-400">Storage:</span>{' '}
                    <span className="text-white">{capabilities.limits.maxStorage}</span>
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
          <button
            onClick={onViewDocs}
            className="flex items-center gap-1 text-xs text-slate-400 hover:text-white transition-colors"
          >
            <ExternalLink className="w-3 h-3" />
            View Docs
          </button>
        )}
        <div className="flex items-center gap-2">
          {isExpired && onRefresh && (
            <button
              onClick={onRefresh}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-yellow-600 hover:bg-yellow-500 rounded-lg text-xs text-white transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Refresh Token
            </button>
          )}
          {!isConnected && onConnect && (
            <button
              onClick={onConnect}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-500 rounded-lg text-xs text-white transition-colors"
            >
              <Link className="w-3.5 h-3.5" />
              Connect
            </button>
          )}
          {isConnected && onDisconnect && (
            <button
              onClick={onDisconnect}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-red-600/80 hover:bg-red-600 rounded-lg text-xs text-white transition-colors"
            >
              <Unlink className="w-3.5 h-3.5" />
              Disconnect
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default memo(ProviderNode);
