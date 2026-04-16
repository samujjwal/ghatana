/**
 * Bootstrap Database Node Component
 *
 * Canvas node representing a database/data store identified during bootstrapping.
 * Shows database type, tables/collections, and relationships.
 *
 * @doc.type component
 * @doc.purpose Bootstrap database visualization in project graph
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  Database,
  Table2,
  HardDrive,
  Layers,
  Search,
  FileJson,
  MoreHorizontal,
  Edit2,
  Trash2,
  ChevronDown,
  ChevronUp,
  Link,
  MessageSquare,
  Lock,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type DatabaseType = 'postgresql' | 'mysql' | 'mongodb' | 'redis' | 'elasticsearch' | 'dynamodb' | 's3' | 'sqlite';

export interface DatabaseEntity {
  readonly name: string;
  readonly type: 'table' | 'collection' | 'bucket' | 'index';
  readonly fields?: readonly string[];
}

export interface DatabaseNodeData {
  readonly label: string;
  readonly description?: string;
  readonly databaseType: DatabaseType;
  readonly version?: string;
  readonly entities: readonly DatabaseEntity[];
  readonly isManaged: boolean; // Cloud managed vs self-hosted
  readonly hasReplication: boolean;
  readonly backupEnabled: boolean;
  readonly notes?: string;
  readonly commentCount?: number;
  readonly nodeId?: string;
  // Callbacks
  readonly onEdit?: (nodeId: string) => void;
  readonly onDelete?: (nodeId: string) => void;
  readonly onAddEntity?: (nodeId: string) => void;
  readonly onOpenComments?: (nodeId: string) => void;
}

export interface DatabaseNodeProps extends NodeProps<DatabaseNodeData> {}

// =============================================================================
// Constants
// =============================================================================

const DATABASE_CONFIG: Record<DatabaseType, { label: string; icon: typeof Database; color: string; bgColor: string }> = {
  postgresql: {
    label: 'PostgreSQL',
    icon: Database,
    color: 'text-blue-700',
    bgColor: 'bg-blue-50 border-blue-300',
  },
  mysql: {
    label: 'MySQL',
    icon: Database,
    color: 'text-orange-700',
    bgColor: 'bg-orange-50 border-orange-300',
  },
  mongodb: {
    label: 'MongoDB',
    icon: Layers,
    color: 'text-green-700',
    bgColor: 'bg-green-50 border-green-300',
  },
  redis: {
    label: 'Redis',
    icon: HardDrive,
    color: 'text-red-700',
    bgColor: 'bg-red-50 border-red-300',
  },
  elasticsearch: {
    label: 'Elasticsearch',
    icon: Search,
    color: 'text-yellow-700',
    bgColor: 'bg-yellow-50 border-yellow-300',
  },
  dynamodb: {
    label: 'DynamoDB',
    icon: FileJson,
    color: 'text-amber-700',
    bgColor: 'bg-amber-50 border-amber-300',
  },
  s3: {
    label: 'S3 Storage',
    icon: HardDrive,
    color: 'text-orange-700',
    bgColor: 'bg-orange-50 border-orange-300',
  },
  sqlite: {
    label: 'SQLite',
    icon: Database,
    color: 'text-cyan-700',
    bgColor: 'bg-cyan-50 border-cyan-300',
  },
};

const ENTITY_ICONS: Record<string, typeof Table2> = {
  table: Table2,
  collection: Layers,
  bucket: HardDrive,
  index: Search,
};

// =============================================================================
// Component
// =============================================================================

export const DatabaseNode = memo<DatabaseNodeProps>(({ id, data, selected }) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const config = DATABASE_CONFIG[data.databaseType];
  const DbIcon = config.icon;

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
        'min-w-[200px] max-w-[280px] rounded-lg border-2 transition-all duration-200',
        config.bgColor,
        selected && 'ring-2 ring-primary ring-offset-2 shadow-lg',
        !selected && 'shadow-sm hover:shadow-md'
      )}
    >
      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-indigo-500 !border-2 !border-white"
      />
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-indigo-500 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-indigo-500 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-indigo-500 !border-2 !border-white"
      />

      {/* Header */}
      <div className="px-3 py-2 flex items-center justify-between border-b border-current/10">
        <div className="flex items-center gap-2">
          <div className={cn('p-1.5 rounded', config.bgColor)}>
            <DbIcon className={cn('w-4 h-4', config.color)} />
          </div>
          <div>
            <span className={cn('text-xs font-semibold', config.color)}>{config.label}</span>
            {data.version && <span className="text-xs text-gray-500 ml-1">v{data.version}</span>}
          </div>
        </div>
        <div className="flex items-center gap-1">
          {data.isManaged && (
            <span className="px-1.5 py-0.5 bg-emerald-100 text-emerald-700 text-[10px] rounded" title="Cloud Managed">
              Managed
            </span>
          )}
          {data.backupEnabled && <HardDrive className="w-3.5 h-3.5 text-blue-600" title="Backups Enabled" />}
          {data.hasReplication && <Layers className="w-3.5 h-3.5 text-purple-600" title="Replication" />}
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
                  onClick={() => data.onAddEntity?.(id)}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-gray-50 flex items-center gap-2"
                >
                  <Table2 className="w-3.5 h-3.5" /> Add Table
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

      {/* Entity Count */}
      <div className="px-3 py-2 border-t border-current/10 flex items-center justify-between text-xs text-gray-500">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <Table2 className="w-3.5 h-3.5" />
            {data.entities.length} {data.entities.length === 1 ? 'entity' : 'entities'}
          </span>
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
        {data.entities.length > 0 && (
          <button onClick={handleToggleExpand} className="hover:text-primary">
            {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
        )}
      </div>

      {/* Expanded Entities */}
      {expanded && data.entities.length > 0 && (
        <div className="px-3 py-2 border-t border-current/10">
          <span className="text-xs font-medium text-gray-700">
            {data.databaseType === 'mongodb' ? 'Collections' : data.databaseType === 's3' ? 'Buckets' : 'Tables'}:
          </span>
          <div className="mt-1 space-y-1 max-h-[150px] overflow-y-auto">
            {data.entities.map((entity) => {
              const EntityIcon = ENTITY_ICONS[entity.type] || Table2;
              return (
                <div key={entity.name} className="flex items-start gap-2 py-1 px-2 bg-white/50 rounded">
                  <EntityIcon className="w-3.5 h-3.5 text-gray-500 mt-0.5 flex-shrink-0" />
                  <div className="min-w-0">
                    <span className="text-xs font-medium text-gray-800">{entity.name}</span>
                    {entity.fields && entity.fields.length > 0 && (
                      <div className="text-[10px] text-gray-500 truncate">
                        {entity.fields.slice(0, 4).join(', ')}
                        {entity.fields.length > 4 && ` +${entity.fields.length - 4} more`}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
          {data.notes && (
            <div className="mt-2">
              <span className="text-xs font-medium text-gray-700">Notes:</span>
              <p className="text-xs text-gray-600 mt-0.5">{data.notes}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

DatabaseNode.displayName = 'DatabaseNode';

export default DatabaseNode;
