// ============================================================================
// ResourceNode - Canvas node for provisioned resource visualization
//
// Features:
// - Resource type and provider display
// - Status indicator with provisioning state
// - Connection details (URL, connection string)
// - Cost information
// - Sync and management actions
// - Environment badge
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import {
  Database,
  Globe,
  GitBranch,
  Server,
  HardDrive,
  Shield,
  Settings,
  Check,
  X,
  Loader2,
  Clock,
  RefreshCw,
  Trash2,
  ExternalLink,
  Copy,
  Eye,
  EyeOff,
  DollarSign,
  AlertTriangle,
  RotateCcw,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import type {
  ProvisionedResource,
  ResourceType,
  ProviderType,
  ProvisioningStatus,
  EnvironmentType,
} from '@ghatana/yappc-api';

export interface ResourceNodeData {
  resource: ProvisionedResource;
  onSync?: () => void;
  onDelete?: () => void;
  onOpenExternal?: () => void;
  onCopyConnectionString?: () => void;
  showCredentials?: boolean;
  onToggleCredentials?: () => void;
}

const resourceConfig: Record<
  ResourceType,
  { icon: typeof Database; color: string; bgColor: string; label: string }
> = {
  REPOSITORY: { icon: GitBranch, color: 'text-gray-400', bgColor: 'bg-gray-500/20', label: 'Repository' },
  HOSTING: { icon: Globe, color: 'text-blue-400', bgColor: 'bg-blue-500/20', label: 'Hosting' },
  DATABASE: { icon: Database, color: 'text-purple-400', bgColor: 'bg-purple-500/20', label: 'Database' },
  CACHE: { icon: Server, color: 'text-red-400', bgColor: 'bg-red-500/20', label: 'Cache' },
  STORAGE: { icon: HardDrive, color: 'text-green-400', bgColor: 'bg-green-500/20', label: 'Storage' },
  CDN: { icon: Globe, color: 'text-orange-400', bgColor: 'bg-orange-500/20', label: 'CDN' },
  DOMAIN: { icon: Globe, color: 'text-cyan-400', bgColor: 'bg-cyan-500/20', label: 'Domain' },
  SSL: { icon: Shield, color: 'text-emerald-400', bgColor: 'bg-emerald-500/20', label: 'SSL Certificate' },
  CI_CD: { icon: Settings, color: 'text-yellow-400', bgColor: 'bg-yellow-500/20', label: 'CI/CD Pipeline' },
  SECRETS: { icon: Shield, color: 'text-pink-400', bgColor: 'bg-pink-500/20', label: 'Secrets' },
  MONITORING: { icon: Settings, color: 'text-indigo-400', bgColor: 'bg-indigo-500/20', label: 'Monitoring' },
  LOGGING: { icon: Settings, color: 'text-teal-400', bgColor: 'bg-teal-500/20', label: 'Logging' },
};

const statusConfig: Record<
  ProvisioningStatus,
  { color: string; bgColor: string; label: string; icon: typeof Check }
> = {
  PENDING: { color: 'text-gray-400', bgColor: 'bg-gray-500/20', label: 'Pending', icon: Clock },
  IN_PROGRESS: { color: 'text-blue-400', bgColor: 'bg-blue-500/20', label: 'Provisioning', icon: Loader2 },
  COMPLETED: { color: 'text-green-400', bgColor: 'bg-green-500/20', label: 'Active', icon: Check },
  FAILED: { color: 'text-red-400', bgColor: 'bg-red-500/20', label: 'Failed', icon: X },
  ROLLBACK_PENDING: { color: 'text-yellow-400', bgColor: 'bg-yellow-500/20', label: 'Rollback Pending', icon: RotateCcw },
  ROLLED_BACK: { color: 'text-orange-400', bgColor: 'bg-orange-500/20', label: 'Rolled Back', icon: RotateCcw },
};

const environmentColors: Record<EnvironmentType, { color: string; bgColor: string }> = {
  DEVELOPMENT: { color: 'text-blue-400', bgColor: 'bg-blue-500/20' },
  STAGING: { color: 'text-yellow-400', bgColor: 'bg-yellow-500/20' },
  PRODUCTION: { color: 'text-green-400', bgColor: 'bg-green-500/20' },
  PREVIEW: { color: 'text-purple-400', bgColor: 'bg-purple-500/20' },
};

const providerLogos: Record<ProviderType, string> = {
  GITHUB: 'GH',
  GITLAB: 'GL',
  BITBUCKET: 'BB',
  VERCEL: 'V',
  NETLIFY: 'N',
  RAILWAY: 'RW',
  RENDER: 'R',
  HEROKU: 'H',
  AWS: 'AWS',
  GCP: 'GCP',
  AZURE: 'AZ',
  SUPABASE: 'SB',
  PLANETSCALE: 'PS',
  NEON: 'N',
  UPSTASH: 'UP',
  CLOUDFLARE: 'CF',
  CUSTOM: '?',
};

function ResourceNode({ data }: NodeProps<ResourceNodeData>) {
  const {
    resource,
    onSync,
    onDelete,
    onOpenExternal,
    onCopyConnectionString,
    showCredentials = false,
    onToggleCredentials,
  } = data;

  const resourceInfo = resourceConfig[resource.type];
  const ResourceIcon = resourceInfo.icon;
  const statusInfo = statusConfig[resource.status];
  const StatusIcon = statusInfo.icon;
  const envInfo = resource.environment ? environmentColors[resource.environment] : null;

  const isActive = resource.status === 'COMPLETED';
  const isFailed = resource.status === 'FAILED';
  const isProvisioning = resource.status === 'IN_PROGRESS';

  const formatCurrency = (amount: number, currency: string) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
    }).format(amount);
  };

  return (
    <div
      className={cn(
        'bg-slate-800 rounded-lg border shadow-xl min-w-[320px] max-w-[380px] transition-all',
        isActive ? 'border-slate-700' : isFailed ? 'border-red-500/50' : 'border-slate-700',
        isProvisioning && 'ring-2 ring-blue-500/30'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-emerald-500" />
      <Handle type="source" position={Position.Right} className="!bg-emerald-500" />

      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-700">
        <div className="flex items-center gap-3">
          <div className={cn('p-2 rounded-lg', resourceInfo.bgColor)}>
            <ResourceIcon className={cn('w-5 h-5', resourceInfo.color)} />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">{resource.name}</h3>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-xs text-slate-400">{resourceInfo.label}</span>
              <span className="text-xs text-slate-500">•</span>
              <span className="text-xs text-slate-400">{providerLogos[resource.provider]}</span>
            </div>
          </div>
        </div>
        <div className="flex flex-col items-end gap-1">
          <div className={cn('flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs', statusInfo.bgColor)}>
            <StatusIcon
              className={cn('w-3 h-3', statusInfo.color, isProvisioning && 'animate-spin')}
            />
            <span className={statusInfo.color}>{statusInfo.label}</span>
          </div>
          {envInfo && resource.environment && (
            <span className={cn('text-xs px-1.5 py-0.5 rounded', envInfo.bgColor, envInfo.color)}>
              {resource.environment}
            </span>
          )}
        </div>
      </div>

      {/* External ID */}
      {resource.externalId && (
        <div className="px-4 py-2 border-b border-slate-700 bg-slate-700/30">
          <div className="flex items-center justify-between">
            <span className="text-xs text-slate-400">External ID</span>
            <span className="text-xs font-mono text-slate-300">{resource.externalId}</span>
          </div>
        </div>
      )}

      {/* URL */}
      {resource.url && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-slate-400">URL</span>
            {onOpenExternal && (
              <button
                onClick={onOpenExternal}
                className="p-1 hover:bg-slate-700 rounded text-slate-400 hover:text-white transition-colors"
              >
                <ExternalLink className="w-3 h-3" />
              </button>
            )}
          </div>
          <a
            href={resource.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-blue-400 hover:text-blue-300 break-all"
          >
            {resource.url}
          </a>
        </div>
      )}

      {/* Connection String */}
      {resource.connectionString && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-slate-400">Connection String</span>
            <div className="flex items-center gap-1">
              {onToggleCredentials && (
                <button
                  onClick={onToggleCredentials}
                  className="p-1 hover:bg-slate-700 rounded text-slate-400 hover:text-white transition-colors"
                >
                  {showCredentials ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                </button>
              )}
              {onCopyConnectionString && (
                <button
                  onClick={onCopyConnectionString}
                  className="p-1 hover:bg-slate-700 rounded text-slate-400 hover:text-white transition-colors"
                >
                  <Copy className="w-3 h-3" />
                </button>
              )}
            </div>
          </div>
          <code className="text-xs font-mono text-slate-300 break-all block">
            {showCredentials
              ? resource.connectionString
              : resource.connectionString.replace(/(:\/\/[^:]+:)[^@]+(@)/, '$1••••••$2')}
          </code>
        </div>
      )}

      {/* Credentials */}
      {resource.credentials && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <Shield className="w-3 h-3" />
            <span>Credentials {resource.credentials.encrypted ? 'encrypted' : 'stored'}</span>
            {resource.credentials.accessedAt && (
              <span className="text-slate-500">
                • Last accessed {new Date(resource.credentials.accessedAt).toLocaleDateString()}
              </span>
            )}
          </div>
        </div>
      )}

      {/* Cost */}
      {resource.cost && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center justify-between">
            <span className="text-xs text-slate-400 flex items-center gap-1">
              <DollarSign className="w-3 h-3" />
              Cost ({resource.cost.period.toLowerCase()})
            </span>
            <span className="text-sm font-medium text-white">
              {formatCurrency(resource.cost.amount, resource.cost.currency)}
            </span>
          </div>
          {resource.cost.breakdown && resource.cost.breakdown.length > 0 && (
            <div className="mt-2 space-y-1">
              {resource.cost.breakdown.map((item, index) => (
                <div key={index} className="flex items-center justify-between text-xs">
                  <span className="text-slate-500">{item.item}</span>
                  <span className="text-slate-400">
                    {formatCurrency(item.amount, resource.cost!.currency)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Metadata */}
      {resource.metadata && Object.keys(resource.metadata).length > 0 && (
        <div className="px-4 py-3 border-b border-slate-700">
          <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">
            Metadata
          </h4>
          <div className="space-y-1">
            {Object.entries(resource.metadata).slice(0, 4).map(([key, value]) => (
              <div key={key} className="flex items-center justify-between text-xs">
                <span className="text-slate-500">{key}</span>
                <span className="text-slate-300 truncate max-w-[150px]">{String(value)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Timestamps */}
      <div className="px-4 py-3 border-b border-slate-700 bg-slate-700/20">
        <div className="grid grid-cols-2 gap-2 text-xs">
          {resource.provisionedAt && (
            <div>
              <span className="text-slate-500">Provisioned</span>
              <div className="text-slate-300">
                {new Date(resource.provisionedAt).toLocaleString()}
              </div>
            </div>
          )}
          {resource.lastSyncedAt && (
            <div>
              <span className="text-slate-500">Last synced</span>
              <div className="text-slate-300">
                {new Date(resource.lastSyncedAt).toLocaleString()}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Actions */}
      <div className="px-4 py-3 flex items-center justify-between">
        {isFailed && (
          <div className="flex items-center gap-1 text-xs text-red-400">
            <AlertTriangle className="w-3 h-3" />
            Provisioning failed
          </div>
        )}
        {!isFailed && <div />}
        <div className="flex items-center gap-2">
          {isActive && onSync && (
            <button
              onClick={onSync}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-700 hover:bg-slate-600 rounded-lg text-xs text-slate-300 transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Sync
            </button>
          )}
          {onDelete && (
            <button
              onClick={onDelete}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-red-600/80 hover:bg-red-600 rounded-lg text-xs text-white transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
              Delete
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default memo(ResourceNode);
