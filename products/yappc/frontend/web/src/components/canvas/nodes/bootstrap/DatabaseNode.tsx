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
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import { Button } from '../../../ui/Button';
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

export interface DatabaseNodeData extends Record<string, unknown> {
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

type DatabaseCanvasNode = Node<DatabaseNodeData>;

export interface DatabaseNodeProps extends NodeProps<DatabaseCanvasNode> {}

// =============================================================================
// Constants
// =============================================================================

const DATABASE_CONFIG: Record<DatabaseType, { label: string; icon: typeof Database; color: string; bgColor: string }> = {
  postgresql: {
    label: 'PostgreSQL',
    icon: Database,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  mysql: {
    label: 'MySQL',
    icon: Database,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  mongodb: {
    label: 'MongoDB',
    icon: Layers,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
  redis: {
    label: 'Redis',
    icon: HardDrive,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg border-destructive-border',
  },
  elasticsearch: {
    label: 'Elasticsearch',
    icon: Search,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  dynamodb: {
    label: 'DynamoDB',
    icon: FileJson,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  s3: {
    label: 'S3 Storage',
    icon: HardDrive,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  sqlite: {
    label: 'SQLite',
    icon: Database,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
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
        selected && 'ring-2 ring-info-border ring-offset-2 shadow-lg',
        !selected && 'shadow-sm hover:shadow-md'
      )}
    >
      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />

      {/* Header */}
      <div className="px-3 py-2 flex items-center justify-between border-b border-current/10">
        <div className="flex items-center gap-2">
          <div className={cn('p-1.5 rounded', config.bgColor)}>
            <DbIcon className={cn('w-4 h-4', config.color)} />
          </div>
          <div>
            <span className={cn('text-xs font-semibold', config.color)}>{config.label}</span>
            {data.version && <span className="text-xs text-muted-foreground ml-1">v{data.version}</span>}
          </div>
        </div>
        <div className="flex items-center gap-1">
          {data.isManaged && (
            <span className="px-1.5 py-0.5 bg-success-bg text-success-color text-[10px] rounded" title="Cloud Managed">
              Managed
            </span>
          )}
          {data.backupEnabled && <span title="Backups Enabled"><HardDrive className="w-3.5 h-3.5 text-info-color" /></span>}
          {data.hasReplication && <span title="Replication"><Layers className="w-3.5 h-3.5 text-warning-color" /></span>}
          <div className="relative">
            <Button variant="ghost" size="sm"
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              className="p-1 hover:bg-black/5 rounded"
            >
              <MoreHorizontal className="w-4 h-4 text-muted-foreground" />
            </Button>
            {showMenu && (
              <div className="absolute right-0 top-full mt-1 bg-surface rounded-lg shadow-lg border border-border py-1 z-50 min-w-[120px]">
                <Button variant="ghost" size="sm"
                  onClick={handleEdit}
                  className="w-full justify-start px-3 py-1.5 text-left text-sm hover:bg-muted/40 flex items-center gap-2"
                >
                  <Edit2 className="w-3.5 h-3.5" /> Edit
                </Button>
                <Button variant="ghost" size="sm"
                  onClick={() => data.onAddEntity?.(id)}
                  className="w-full justify-start px-3 py-1.5 text-left text-sm hover:bg-muted/40 flex items-center gap-2"
                >
                  <Table2 className="w-3.5 h-3.5" /> Add Table
                </Button>
                <hr className="my-1" />
                <Button variant="ghost" size="sm"
                  onClick={handleDelete}
                  className="w-full justify-start px-3 py-1.5 text-left text-sm hover:bg-destructive-bg text-destructive flex items-center gap-2"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Delete
                </Button>
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

      {/* Entity Count */}
      <div className="px-3 py-2 border-t border-current/10 flex items-center justify-between text-xs text-muted-foreground">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <Table2 className="w-3.5 h-3.5" />
            {data.entities.length} {data.entities.length === 1 ? 'entity' : 'entities'}
          </span>
          {data.commentCount && data.commentCount > 0 && (
            <Button variant="ghost" size="sm"
              onClick={(e) => {
                e.stopPropagation();
                data.onOpenComments?.(id);
              }}
              className="flex items-center gap-1 hover:text-info-color"
            >
              <MessageSquare className="w-3.5 h-3.5" />
              {data.commentCount}
            </Button>
          )}
        </div>
        {data.entities.length > 0 && (
          <Button variant="ghost" size="sm" onClick={handleToggleExpand} className="hover:text-info-color">
            {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </Button>
        )}
      </div>

      {/* Expanded Entities */}
      {expanded && data.entities.length > 0 && (
        <div className="px-3 py-2 border-t border-current/10">
          <span className="text-xs font-medium text-fg">
            {data.databaseType === 'mongodb' ? 'Collections' : data.databaseType === 's3' ? 'Buckets' : 'Tables'}:
          </span>
          <div className="mt-1 space-y-1 max-h-[150px] overflow-y-auto">
            {data.entities.map((entity) => {
              const EntityIcon = ENTITY_ICONS[entity.type] || Table2;
              return (
                <div key={entity.name} className="flex items-start gap-2 py-1 px-2 bg-surface rounded">
                  <EntityIcon className="w-3.5 h-3.5 text-muted-foreground mt-0.5 flex-shrink-0" />
                  <div className="min-w-0">
                    <span className="text-xs font-medium text-fg">{entity.name}</span>
                    {entity.fields && entity.fields.length > 0 && (
                      <div className="text-[10px] text-muted-foreground truncate">
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
              <span className="text-xs font-medium text-fg">Notes:</span>
              <p className="text-xs text-fg-muted mt-0.5">{data.notes}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

DatabaseNode.displayName = 'DatabaseNode';

export default DatabaseNode;
