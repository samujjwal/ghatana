/**
 * Bootstrap Integration Node Component
 *
 * Canvas node representing an external integration/third-party service.
 * Shows integration type, authentication method, and usage details.
 *
 * @doc.type component
 * @doc.purpose Bootstrap integration visualization in project graph
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  Plug,
  CreditCard,
  Mail,
  MessageSquare as ChatIcon,
  Map,
  BarChart3,
  Cloud,
  Shield,
  Video,
  Phone,
  Bell,
  FileText,
  Key,
  MoreHorizontal,
  Edit2,
  Trash2,
  ChevronDown,
  ChevronUp,
  Link,
  MessageSquare,
  ExternalLink,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type IntegrationType =
  | 'payment'
  | 'email'
  | 'sms'
  | 'chat'
  | 'maps'
  | 'analytics'
  | 'storage'
  | 'auth'
  | 'video'
  | 'notification'
  | 'document'
  | 'other';

export type AuthMethod = 'api_key' | 'oauth2' | 'jwt' | 'basic' | 'webhook' | 'none';

export interface IntegrationCapability {
  readonly name: string;
  readonly description?: string;
  readonly required: boolean;
}

export interface IntegrationNodeData extends Record<string, unknown> {
  readonly label: string;
  readonly description?: string;
  readonly integrationType: IntegrationType;
  readonly provider: string; // e.g., "Stripe", "SendGrid", "Twilio"
  readonly authMethod: AuthMethod;
  readonly capabilities: readonly IntegrationCapability[];
  readonly webhooksEnabled: boolean;
  readonly sandboxAvailable: boolean;
  readonly pricingModel?: string;
  readonly documentationUrl?: string;
  readonly notes?: string;
  readonly status?: 'configured' | 'pending' | 'error';
  readonly commentCount?: number;
  readonly nodeId?: string;
  // Callbacks
  readonly onEdit?: (nodeId: string) => void;
  readonly onDelete?: (nodeId: string) => void;
  readonly onConfigure?: (nodeId: string) => void;
  readonly onOpenComments?: (nodeId: string) => void;
  readonly onOpenDocs?: (url: string) => void;
}

type IntegrationCanvasNode = Node<IntegrationNodeData>;

export interface IntegrationNodeProps extends NodeProps<IntegrationCanvasNode> {}

// =============================================================================
// Constants
// =============================================================================

const INTEGRATION_CONFIG: Record<IntegrationType, { label: string; icon: typeof Plug; color: string; bgColor: string }> = {
  payment: {
    label: 'Payment',
    icon: CreditCard,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
  email: {
    label: 'Email',
    icon: Mail,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  sms: {
    label: 'SMS',
    icon: Phone,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  chat: {
    label: 'Chat',
    icon: ChatIcon,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  maps: {
    label: 'Maps',
    icon: Map,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg border-destructive-border',
  },
  analytics: {
    label: 'Analytics',
    icon: BarChart3,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  storage: {
    label: 'Storage',
    icon: Cloud,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  auth: {
    label: 'Auth',
    icon: Shield,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  video: {
    label: 'Video',
    icon: Video,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  notification: {
    label: 'Push',
    icon: Bell,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  document: {
    label: 'Document',
    icon: FileText,
    color: 'text-fg',
    bgColor: 'bg-surface-muted border-border',
  },
  other: {
    label: 'Integration',
    icon: Plug,
    color: 'text-fg',
    bgColor: 'bg-surface-muted border-border',
  },
};

const AUTH_LABELS: Record<AuthMethod, string> = {
  api_key: 'API Key',
  oauth2: 'OAuth 2.0',
  jwt: 'JWT',
  basic: 'Basic Auth',
  webhook: 'Webhook',
  none: 'None',
};

const STATUS_CONFIG: Record<string, { icon: typeof CheckCircle2; color: string }> = {
  configured: { icon: CheckCircle2, color: 'text-success-color' },
  pending: { icon: AlertCircle, color: 'text-warning-color' },
  error: { icon: AlertCircle, color: 'text-destructive' },
};

// =============================================================================
// Component
// =============================================================================

export const IntegrationNode = memo<IntegrationNodeProps>(({ id, data, selected }) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const config = INTEGRATION_CONFIG[data.integrationType];
  const IntegrationIcon = config.icon;
  const statusConfig = data.status ? STATUS_CONFIG[data.status] : null;
  const StatusIcon = statusConfig?.icon;

  const handleToggleExpand = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setExpanded((prev) => !prev);
  }, []);

  const handleEdit = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onEdit?.(id);
    },
    [id, data]
  );

  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onDelete?.(id);
    },
    [id, data]
  );

  const handleOpenDocs = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      if (data.documentationUrl) {
        data.onOpenDocs?.(data.documentationUrl);
      }
    },
    [data]
  );

  return (
    <div
      className={cn(
        'min-w-[220px] max-w-[300px] rounded-lg border-2 transition-all duration-200',
        config.bgColor,
        selected && 'ring-2 ring-primary ring-offset-2 shadow-lg',
        !selected && 'shadow-sm hover:shadow-md'
      )}
    >
      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />

      {/* Header */}
      <div className="px-3 py-2 flex items-center justify-between border-b border-current/10">
        <div className="flex items-center gap-2">
          <div className={cn('p-1.5 rounded', config.bgColor)}>
            <IntegrationIcon className={cn('w-4 h-4', config.color)} />
          </div>
          <div>
            <span className={cn('text-xs font-semibold', config.color)}>{data.provider}</span>
            <span className="text-xs text-muted-foreground ml-1.5">{config.label}</span>
          </div>
        </div>
        <div className="flex items-center gap-1">
          {StatusIcon && <StatusIcon className={cn('w-4 h-4', statusConfig.color)} />}
          <div className="relative">
            <button
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              className="p-1 hover:bg-black/5 rounded"
            >
              <MoreHorizontal className="w-4 h-4 text-muted-foreground" />
            </button>
            {showMenu && (
              <div className="absolute right-0 top-full mt-1 bg-surface rounded-lg shadow-lg border border-border py-1 z-50 min-w-[130px]">
                <button
                  onClick={handleEdit}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted/40 flex items-center gap-2"
                >
                  <Edit2 className="w-3.5 h-3.5" /> Edit
                </button>
                <button
                  onClick={() => data.onConfigure?.(id)}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted/40 flex items-center gap-2"
                >
                  <Key className="w-3.5 h-3.5" /> Configure
                </button>
                {data.documentationUrl && (
                  <button
                    onClick={handleOpenDocs}
                    className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted/40 flex items-center gap-2"
                  >
                    <ExternalLink className="w-3.5 h-3.5" /> Docs
                  </button>
                )}
                <hr className="my-1" />
                <button
                  onClick={handleDelete}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-destructive-bg text-destructive flex items-center gap-2"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Remove
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="px-3 py-2">
        <h3 className="text-sm font-medium text-fg">{data.label}</h3>
        {data.description && (
          <p className={cn('text-xs text-fg-muted mt-1', expanded ? '' : 'line-clamp-2')}>
            {data.description}
          </p>
        )}
      </div>

      {/* Quick Info */}
      <div className="px-3 py-2 border-t border-current/10 flex items-center justify-between text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <span className="flex items-center gap-1">
            <Key className="w-3.5 h-3.5" />
            {AUTH_LABELS[data.authMethod]}
          </span>
          {data.webhooksEnabled && (
            <span className="px-1.5 py-0.5 bg-info-bg text-info-color text-[10px] rounded">Webhooks</span>
          )}
          {data.sandboxAvailable && (
            <span className="px-1.5 py-0.5 bg-warning-bg text-warning-color text-[10px] rounded">Sandbox</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {data.commentCount && data.commentCount > 0 && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                data.onOpenComments?.(id);
              }}
              className="flex items-center gap-1 hover:text-primary"
            >
              <MessageSquare className="w-3.5 h-3.5" />
              {data.commentCount}
            </button>
          )}
          {data.capabilities.length > 0 && (
            <button onClick={handleToggleExpand} className="hover:text-primary">
              {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
            </button>
          )}
        </div>
      </div>

      {/* Expanded Capabilities */}
      {expanded && (
        <div className="px-3 py-2 border-t border-current/10 space-y-2">
          {/* Capabilities */}
          {data.capabilities.length > 0 && (
            <div>
              <span className="text-xs font-medium text-fg">Capabilities:</span>
              <div className="mt-1 space-y-1">
                {data.capabilities.map((cap) => (
                  <div key={cap.name} className="flex items-start gap-2 py-1 px-2 bg-surface rounded">
                    <CheckCircle2 className={cn('w-3.5 h-3.5 mt-0.5 flex-shrink-0', cap.required ? 'text-success-color' : 'text-muted-foreground')} />
                    <div className="min-w-0">
                      <span className="text-xs font-medium text-fg">{cap.name}</span>
                      {cap.required && <span className="text-[10px] text-destructive ml-1">Required</span>}
                      {cap.description && <p className="text-[10px] text-muted-foreground">{cap.description}</p>}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Pricing */}
          {data.pricingModel && (
            <div>
              <span className="text-xs font-medium text-fg">Pricing:</span>
              <p className="text-xs text-fg-muted mt-0.5">{data.pricingModel}</p>
            </div>
          )}

          {/* Notes */}
          {data.notes && (
            <div>
              <span className="text-xs font-medium text-fg">Notes:</span>
              <p className="text-xs text-fg-muted mt-0.5">{data.notes}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

IntegrationNode.displayName = 'IntegrationNode';

export default IntegrationNode;
