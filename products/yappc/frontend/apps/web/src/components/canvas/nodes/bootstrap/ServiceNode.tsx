/**
 * Bootstrap Service Node Component
 *
 * Canvas node representing a backend service identified during bootstrapping.
 * Shows service type, API endpoints, and database connections.
 *
 * @doc.type component
 * @doc.purpose Bootstrap service visualization in project graph
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  Server,
  Database,
  Cloud,
  Cog,
  Globe,
  Lock,
  Zap,
  MoreHorizontal,
  Edit2,
  Trash2,
  ChevronDown,
  ChevronUp,
  Link,
  MessageSquare,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type ServiceType = 'api' | 'worker' | 'gateway' | 'scheduler' | 'websocket' | 'microservice';

export interface ServiceEndpoint {
  readonly method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  readonly path: string;
  readonly description?: string;
}

export interface ServiceNodeData {
  readonly label: string;
  readonly description?: string;
  readonly serviceType: ServiceType;
  readonly technology: string;
  readonly endpoints?: readonly ServiceEndpoint[];
  readonly databaseConnections?: readonly string[];
  readonly externalServices?: readonly string[];
  readonly isPublic: boolean;
  readonly requiresAuth: boolean;
  readonly notes?: string;
  readonly commentCount?: number;
  readonly nodeId?: string;
  // Callbacks
  readonly onEdit?: (nodeId: string) => void;
  readonly onDelete?: (nodeId: string) => void;
  readonly onAddConnection?: (nodeId: string) => void;
  readonly onOpenComments?: (nodeId: string) => void;
}

export interface ServiceNodeProps extends NodeProps<ServiceNodeData> {}

// =============================================================================
// Constants
// =============================================================================

const SERVICE_TYPE_CONFIG: Record<ServiceType, { label: string; icon: typeof Server; color: string; bgColor: string }> = {
  api: {
    label: 'REST API',
    icon: Globe,
    color: 'text-blue-700',
    bgColor: 'bg-blue-50 border-blue-200',
  },
  worker: {
    label: 'Worker',
    icon: Cog,
    color: 'text-orange-700',
    bgColor: 'bg-orange-50 border-orange-200',
  },
  gateway: {
    label: 'Gateway',
    icon: Cloud,
    color: 'text-purple-700',
    bgColor: 'bg-purple-50 border-purple-200',
  },
  scheduler: {
    label: 'Scheduler',
    icon: Zap,
    color: 'text-yellow-700',
    bgColor: 'bg-yellow-50 border-yellow-200',
  },
  websocket: {
    label: 'WebSocket',
    icon: Zap,
    color: 'text-green-700',
    bgColor: 'bg-green-50 border-green-200',
  },
  microservice: {
    label: 'Microservice',
    icon: Server,
    color: 'text-indigo-700',
    bgColor: 'bg-indigo-50 border-indigo-200',
  },
};

const METHOD_COLORS: Record<string, string> = {
  GET: 'bg-green-100 text-green-700',
  POST: 'bg-blue-100 text-blue-700',
  PUT: 'bg-yellow-100 text-yellow-700',
  DELETE: 'bg-red-100 text-red-700',
  PATCH: 'bg-purple-100 text-purple-700',
};

// =============================================================================
// Component
// =============================================================================

export const ServiceNode = memo<ServiceNodeProps>(({ id, data, selected }) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const config = SERVICE_TYPE_CONFIG[data.serviceType];
  const ServiceIcon = config.icon;

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

  return (
    <div
      className={cn(
        'min-w-[240px] max-w-[320px] rounded-lg border-2 transition-all duration-200',
        config.bgColor,
        selected && 'ring-2 ring-primary ring-offset-2 shadow-lg',
        !selected && 'shadow-sm hover:shadow-md'
      )}
    >
      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-blue-500 !border-2 !border-white"
      />
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-blue-500 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-blue-500 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-blue-500 !border-2 !border-white"
      />

      {/* Header */}
      <div className="px-3 py-2 flex items-center justify-between border-b border-current/10">
        <div className="flex items-center gap-2">
          <div className={cn('p-1.5 rounded', config.bgColor)}>
            <ServiceIcon className={cn('w-4 h-4', config.color)} />
          </div>
          <div>
            <span className={cn('text-xs font-semibold', config.color)}>{config.label}</span>
            <span className="text-xs text-gray-500 ml-2">{data.technology}</span>
          </div>
        </div>
        <div className="flex items-center gap-1">
          {data.requiresAuth && <Lock className="w-3.5 h-3.5 text-amber-600" title="Requires Auth" />}
          {data.isPublic && <Globe className="w-3.5 h-3.5 text-green-600" title="Public" />}
          <div className="relative">
            <button
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              className="p-1 hover:bg-black/5 rounded"
            >
              <MoreHorizontal className="w-4 h-4 text-gray-500" />
            </button>
            {showMenu && (
              <div className="absolute right-0 top-full mt-1 bg-white rounded-lg shadow-lg border py-1 z-50 min-w-[120px]">
                <button
                  onClick={handleEdit}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-gray-50 flex items-center gap-2"
                >
                  <Edit2 className="w-3.5 h-3.5" /> Edit
                </button>
                <button
                  onClick={() => data.onAddConnection?.(id)}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-gray-50 flex items-center gap-2"
                >
                  <Link className="w-3.5 h-3.5" /> Connect
                </button>
                <hr className="my-1" />
                <button
                  onClick={handleDelete}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-red-50 text-red-600 flex items-center gap-2"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Delete
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="px-3 py-2">
        <h3 className="text-sm font-medium text-gray-900">{data.label}</h3>
        {data.description && (
          <p className={cn('text-xs text-gray-600 mt-1', expanded ? '' : 'line-clamp-2')}>
            {data.description}
          </p>
        )}
      </div>

      {/* Quick Stats */}
      <div className="px-3 py-2 border-t border-current/10 flex items-center justify-between text-xs text-gray-500">
        <div className="flex items-center gap-3">
          {data.endpoints && data.endpoints.length > 0 && (
            <span className="flex items-center gap-1">
              <Globe className="w-3.5 h-3.5" />
              {data.endpoints.length} endpoints
            </span>
          )}
          {data.databaseConnections && data.databaseConnections.length > 0 && (
            <span className="flex items-center gap-1">
              <Database className="w-3.5 h-3.5" />
              {data.databaseConnections.length}
            </span>
          )}
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
        </div>
        {(data.endpoints?.length || data.databaseConnections?.length || data.externalServices?.length) && (
          <button onClick={handleToggleExpand} className="hover:text-primary">
            {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
        )}
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div className="px-3 py-2 border-t border-current/10 space-y-3">
          {/* Endpoints */}
          {data.endpoints && data.endpoints.length > 0 && (
            <div>
              <span className="text-xs font-medium text-gray-700">Endpoints:</span>
              <div className="mt-1 space-y-1 max-h-[120px] overflow-y-auto">
                {data.endpoints.map((endpoint, idx) => (
                  <div key={idx} className="flex items-center gap-2 text-xs">
                    <span className={cn('px-1.5 py-0.5 rounded text-[10px] font-medium', METHOD_COLORS[endpoint.method])}>
                      {endpoint.method}
                    </span>
                    <code className="text-gray-700 font-mono">{endpoint.path}</code>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Database Connections */}
          {data.databaseConnections && data.databaseConnections.length > 0 && (
            <div>
              <span className="text-xs font-medium text-gray-700">Databases:</span>
              <div className="flex flex-wrap gap-1 mt-1">
                {data.databaseConnections.map((db) => (
                  <span key={db} className="px-1.5 py-0.5 bg-gray-100 rounded text-xs text-gray-600 flex items-center gap-1">
                    <Database className="w-3 h-3" />
                    {db}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* External Services */}
          {data.externalServices && data.externalServices.length > 0 && (
            <div>
              <span className="text-xs font-medium text-gray-700">External:</span>
              <div className="flex flex-wrap gap-1 mt-1">
                {data.externalServices.map((svc) => (
                  <span key={svc} className="px-1.5 py-0.5 bg-purple-50 rounded text-xs text-purple-700 flex items-center gap-1">
                    <Cloud className="w-3 h-3" />
                    {svc}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Notes */}
          {data.notes && (
            <div>
              <span className="text-xs font-medium text-gray-700">Notes:</span>
              <p className="text-xs text-gray-600 mt-0.5">{data.notes}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

ServiceNode.displayName = 'ServiceNode';

export default ServiceNode;
