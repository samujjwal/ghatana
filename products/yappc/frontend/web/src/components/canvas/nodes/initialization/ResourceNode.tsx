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
import type { Node, NodeProps } from '@xyflow/react';
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
import { Button } from '../../../ui/Button';

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

type ProvisioningStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'ROLLBACK_PENDING'
  | 'ROLLED_BACK';

type EnvironmentType = 'DEVELOPMENT' | 'STAGING' | 'PRODUCTION' | 'PREVIEW';

interface ResourceCredentials {
  encrypted: boolean;
  accessedAt?: string;
}

interface ResourceCostBreakdownItem {
  item: string;
  amount: number;
}

interface ResourceCost {
  amount: number;
  currency: string;
  period: string;
  breakdown?: ResourceCostBreakdownItem[];
}

interface ProvisionedResource {
  id: string;
  name: string;
  type: ResourceType;
  provider: ProviderType;
  status: ProvisioningStatus;
  environment?: EnvironmentType;
  externalId?: string;
  url?: string;
  connectionString?: string;
  credentials?: ResourceCredentials;
  cost?: ResourceCost;
  metadata?: Record<string, unknown>;
  provisionedAt?: string;
  lastSyncedAt?: string;
}

export interface ResourceNodeData extends Record<string, unknown> {
  resource: ProvisionedResource;
  onSync?: () => void;
  onDelete?: () => void;
  onOpenExternal?: () => void;
  onCopyConnectionString?: () => void;
  showCredentials?: boolean;
  onToggleCredentials?: () => void;
}

type ResourceCanvasNode = Node<ResourceNodeData, 'resource'>;

const resourceConfig: Record<
  ResourceType,
  { icon: typeof Database; color: string; bgColor: string; label: string }
> = {
  REPOSITORY: { icon: GitBranch, color: 'text-fg-muted', bgColor: 'bg-muted', label: 'Repository' },
  HOSTING: { icon: Globe, color: 'text-info-color', bgColor: 'bg-info-bg', label: 'Hosting' },
  DATABASE: { icon: Database, color: 'text-info-color', bgColor: 'bg-info-bg', label: 'Database' },
  CACHE: { icon: Server, color: 'text-destructive', bgColor: 'bg-destructive-bg', label: 'Cache' },
  STORAGE: { icon: HardDrive, color: 'text-success-color', bgColor: 'bg-success-bg', label: 'Storage' },
  CDN: { icon: Globe, color: 'text-warning-color', bgColor: 'bg-warning-bg', label: 'CDN' },
  DOMAIN: { icon: Globe, color: 'text-info-color', bgColor: 'bg-info-bg', label: 'Domain' },
  SSL: { icon: Shield, color: 'text-success-color', bgColor: 'bg-success-bg', label: 'SSL Certificate' },
  CI_CD: { icon: Settings, color: 'text-warning-color', bgColor: 'bg-warning-bg', label: 'CI/CD Pipeline' },
  SECRETS: { icon: Shield, color: 'text-destructive', bgColor: 'bg-destructive-bg', label: 'Secrets' },
  MONITORING: { icon: Settings, color: 'text-info-color', bgColor: 'bg-info-bg', label: 'Monitoring' },
  LOGGING: { icon: Settings, color: 'text-fg-muted', bgColor: 'bg-muted', label: 'Logging' },
};

const statusConfig: Record<
  ProvisioningStatus,
  { color: string; bgColor: string; label: string; icon: typeof Check }
> = {
  PENDING: { color: 'text-fg-muted', bgColor: 'bg-muted', label: 'Pending', icon: Clock },
  IN_PROGRESS: { color: 'text-info-color', bgColor: 'bg-info-bg', label: 'Provisioning', icon: Loader2 },
  COMPLETED: { color: 'text-success-color', bgColor: 'bg-success-bg', label: 'Active', icon: Check },
  FAILED: { color: 'text-destructive', bgColor: 'bg-destructive-bg', label: 'Failed', icon: X },
  ROLLBACK_PENDING: { color: 'text-warning-color', bgColor: 'bg-warning-bg', label: 'Rollback Pending', icon: RotateCcw },
  ROLLED_BACK: { color: 'text-warning-color', bgColor: 'bg-warning-bg', label: 'Rolled Back', icon: RotateCcw },
};

const environmentColors: Record<EnvironmentType, { color: string; bgColor: string }> = {
  DEVELOPMENT: { color: 'text-info-color', bgColor: 'bg-info-bg' },
  STAGING: { color: 'text-warning-color', bgColor: 'bg-warning-bg' },
  PRODUCTION: { color: 'text-success-color', bgColor: 'bg-success-bg' },
  PREVIEW: { color: 'text-info-color', bgColor: 'bg-info-bg' },
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

function ResourceNode({ data }: NodeProps<ResourceCanvasNode>) {
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
        'bg-surface rounded-lg border shadow-xl min-w-[320px] max-w-[380px] transition-all',
        isActive ? 'border-border' : isFailed ? 'border-destructive-border' : 'border-border',
        isProvisioning && 'ring-2 ring-info-border'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-success-color" />
      <Handle type="source" position={Position.Right} className="!bg-success-color" />

      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-3">
          <div className={cn('p-2 rounded-lg', resourceInfo.bgColor)}>
            <ResourceIcon className={cn('w-5 h-5', resourceInfo.color)} />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-fg">{resource.name}</h3>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-xs text-fg-muted">{resourceInfo.label}</span>
              <span className="text-xs text-fg-muted">•</span>
              <span className="text-xs text-fg-muted">{providerLogos[resource.provider]}</span>
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
        <div className="px-4 py-2 border-b border-border bg-surface-muted">
          <div className="flex items-center justify-between">
            <span className="text-xs text-fg-muted">External ID</span>
            <span className="text-xs font-mono text-fg">{resource.externalId}</span>
          </div>
        </div>
      )}

      {/* URL */}
      {resource.url && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-fg-muted">URL</span>
            {onOpenExternal && (
              <Button variant="ghost" size="sm"
                onClick={onOpenExternal}
                className="p-1 hover:bg-muted rounded text-fg-muted hover:text-fg transition-colors"
              >
                <ExternalLink className="w-3 h-3" />
              </Button>
            )}
          </div>
          <a
            href={resource.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-info-color hover:opacity-80 break-all"
          >
            {resource.url}
          </a>
        </div>
      )}

      {/* Connection String */}
      {resource.connectionString && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-fg-muted">Connection String</span>
            <div className="flex items-center gap-1">
              {onToggleCredentials && (
                <Button variant="ghost" size="sm"
                  onClick={onToggleCredentials}
                  className="p-1 hover:bg-muted rounded text-fg-muted hover:text-fg transition-colors"
                >
                  {showCredentials ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                </Button>
              )}
              {onCopyConnectionString && (
                <Button variant="ghost" size="sm"
                  onClick={onCopyConnectionString}
                  className="p-1 hover:bg-muted rounded text-fg-muted hover:text-fg transition-colors"
                >
                  <Copy className="w-3 h-3" />
                </Button>
              )}
            </div>
          </div>
          <code className="text-xs font-mono text-fg break-all block">
            {showCredentials
              ? resource.connectionString
              : resource.connectionString.replace(/(:\/\/[^:]+:)[^@]+(@)/, '$1••••••$2')}
          </code>
        </div>
      )}

      {/* Credentials */}
      {resource.credentials && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-center gap-1.5 text-xs text-fg-muted">
            <Shield className="w-3 h-3" />
            <span>Credentials {resource.credentials.encrypted ? 'encrypted' : 'stored'}</span>
            {resource.credentials.accessedAt && (
              <span className="text-fg-muted">
                • Last accessed {new Date(resource.credentials.accessedAt).toLocaleDateString()}
              </span>
            )}
          </div>
        </div>
      )}

      {/* Cost */}
      {resource.cost && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-center justify-between">
            <span className="text-xs text-fg-muted flex items-center gap-1">
              <DollarSign className="w-3 h-3" />
              Cost ({resource.cost.period.toLowerCase()})
            </span>
            <span className="text-sm font-medium text-fg">
              {formatCurrency(resource.cost.amount, resource.cost.currency)}
            </span>
          </div>
          {resource.cost.breakdown && resource.cost.breakdown.length > 0 && (
            <div className="mt-2 space-y-1">
              {resource.cost.breakdown.map((item: ResourceCostBreakdownItem, index: number) => (
                <div key={index} className="flex items-center justify-between text-xs">
                  <span className="text-fg-muted">{item.item}</span>
                  <span className="text-fg-muted">
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
        <div className="px-4 py-3 border-b border-border">
          <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">
            Metadata
          </h4>
          <div className="space-y-1">
            {Object.entries(resource.metadata).slice(0, 4).map(([key, value]) => (
              <div key={key} className="flex items-center justify-between text-xs">
                <span className="text-fg-muted">{key}</span>
                <span className="text-fg truncate max-w-[150px]">{String(value)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Timestamps */}
      <div className="px-4 py-3 border-b border-border bg-surface-muted">
        <div className="grid grid-cols-2 gap-2 text-xs">
          {resource.provisionedAt && (
            <div>
              <span className="text-fg-muted">Provisioned</span>
              <div className="text-fg">
                {new Date(resource.provisionedAt).toLocaleString()}
              </div>
            </div>
          )}
          {resource.lastSyncedAt && (
            <div>
              <span className="text-fg-muted">Last synced</span>
              <div className="text-fg">
                {new Date(resource.lastSyncedAt).toLocaleString()}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Actions */}
      <div className="px-4 py-3 flex items-center justify-between">
        {isFailed && (
          <div className="flex items-center gap-1 text-xs text-destructive">
            <AlertTriangle className="w-3 h-3" />
            Provisioning failed
          </div>
        )}
        {!isFailed && <div />}
        <div className="flex items-center gap-2">
          {isActive && onSync && (
            <Button variant="ghost" size="sm"
              onClick={onSync}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-muted hover:opacity-90 rounded-lg text-xs text-fg transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Sync
            </Button>
          )}
          {onDelete && (
            <Button variant="ghost" size="sm"
              onClick={onDelete}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-destructive hover:opacity-90 rounded-lg text-xs text-white transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
              Delete
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

export default memo(ResourceNode);
