// ============================================================================
// DashboardNode - Canvas node for dashboard/widget visualization
// 
// Displays:
// - Dashboard name and description
// - Widget grid preview
// - Time range selector
// - Refresh interval
// - Variable count
// - Widget summary
// ============================================================================

import { memo } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { 
  LayoutDashboard, 
  BarChart, 
  LineChart, 
  PieChart,
  Gauge,
  Table2,
  FileText,
  AlignLeft,
  Bell,
  Grid3X3,
  Clock,
  RefreshCw,
  Variable,
  User
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// TYPES
// ============================================================================

export type DashboardWidgetType = 
  | 'LINE_CHART'
  | 'BAR_CHART'
  | 'PIE_CHART'
  | 'GAUGE'
  | 'STAT'
  | 'TABLE'
  | 'LOGS'
  | 'HEATMAP'
  | 'TEXT'
  | 'ALERT_LIST';

export type TimeRange = 
  | 'LAST_5M'
  | 'LAST_15M'
  | 'LAST_30M'
  | 'LAST_1H'
  | 'LAST_3H'
  | 'LAST_6H'
  | 'LAST_12H'
  | 'LAST_24H'
  | 'LAST_7D'
  | 'LAST_30D'
  | 'CUSTOM';

export interface DashboardWidgetSummary {
  type: DashboardWidgetType;
  count: number;
}

export interface DashboardNodeData extends Record<string, unknown> {
  id: string;
  name: string;
  description?: string;
  layout: {
    columns: number;
    rows: number;
  };
  widgetCount: number;
  widgetSummary: DashboardWidgetSummary[];
  variableCount: number;
  timeRange: TimeRange;
  refreshInterval?: number;
  isDefault: boolean;
  createdBy: {
    id: string;
    name: string;
    avatarUrl?: string;
  };
  lastViewed?: string;
  viewCount?: number;
}

type DashboardCanvasNode = Node<DashboardNodeData>;

export type DashboardNodeProps = NodeProps<DashboardCanvasNode>;

// ============================================================================
// CONSTANTS
// ============================================================================

const WIDGET_TYPE_CONFIG: Record<DashboardWidgetType, { 
  label: string; 
  Icon: typeof LineChart;
  color: string;
}> = {
  LINE_CHART: { label: 'Line', Icon: LineChart, color: 'text-info-color' },
  BAR_CHART: { label: 'Bar', Icon: BarChart, color: 'text-success-color' },
  PIE_CHART: { label: 'Pie', Icon: PieChart, color: 'text-info-color' },
  GAUGE: { label: 'Gauge', Icon: Gauge, color: 'text-warning-color' },
  STAT: { label: 'Stat', Icon: AlignLeft, color: 'text-info-color' },
  TABLE: { label: 'Table', Icon: Table2, color: 'text-fg-muted' },
  LOGS: { label: 'Logs', Icon: FileText, color: 'text-warning-color' },
  HEATMAP: { label: 'Heatmap', Icon: Grid3X3, color: 'text-destructive' },
  TEXT: { label: 'Text', Icon: AlignLeft, color: 'text-fg-muted' },
  ALERT_LIST: { label: 'Alerts', Icon: Bell, color: 'text-warning-color' },
};

const TIME_RANGE_LABELS: Record<TimeRange, string> = {
  LAST_5M: '5m',
  LAST_15M: '15m',
  LAST_30M: '30m',
  LAST_1H: '1h',
  LAST_3H: '3h',
  LAST_6H: '6h',
  LAST_12H: '12h',
  LAST_24H: '24h',
  LAST_7D: '7d',
  LAST_30D: '30d',
  CUSTOM: 'Custom',
};

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

function WidgetGrid({ layout, widgetCount }: { 
  layout: { columns: number; rows: number }; 
  widgetCount: number;
}) {
  const cells = layout.columns * layout.rows;
  const filledCells = Math.min(widgetCount, cells);
  
  return (
    <div 
      className="grid gap-0.5 w-full"
      style={{ 
        gridTemplateColumns: `repeat(${layout.columns}, 1fr)`,
        gridTemplateRows: `repeat(${Math.min(layout.rows, 3)}, 1fr)`,
      }}
    >
      {Array.from({ length: Math.min(cells, 9) }).map((_, index) => (
        <div
          key={index}
          className={cn(
            'aspect-video rounded-sm border',
            index < filledCells 
              ? 'bg-info-bg border-info-border' 
              : 'bg-surface-muted border-border border-dashed'
          )}
        />
      ))}
    </div>
  );
}

function WidgetTypeSummary({ summary }: { summary: DashboardWidgetSummary[] }) {
  const displayTypes = summary.slice(0, 4);
  const overflow = summary.length - 4;
  
  return (
    <div className="flex items-center gap-2">
      {displayTypes.map((item) => {
        const config = WIDGET_TYPE_CONFIG[item.type];
        const Icon = config.Icon;
        return (
          <div 
            key={item.type}
            className="flex items-center gap-1"
            title={`${item.count} ${config.label} widget${item.count > 1 ? 's' : ''}`}
          >
            <Icon className={cn('w-3 h-3', config.color)} />
            <span className="text-[10px] text-fg-muted">{item.count}</span>
          </div>
        );
      })}
      {overflow > 0 && (
        <span className="text-[10px] text-fg-muted">+{overflow} types</span>
      )}
    </div>
  );
}

// ============================================================================
// MAIN COMPONENT
// ============================================================================

function DashboardNodeComponent({ data, selected }: DashboardNodeProps) {
  return (
    <div
      className={cn(
        'min-w-[240px] max-w-[300px] rounded-lg border bg-white shadow-md transition-all',
        data.isDefault ? 'border-info-border' : 'border-border',
        selected && 'ring-2 ring-info-border ring-offset-2'
      )}
    >
      {/* Connection Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />
      <Handle
        type="target"
        position={Position.Left}
        id="metric"
        className="!w-3 !h-3 !bg-success-color !border-2 !border-surface"
      />
      <Handle
        type="source"
        position={Position.Right}
        id="share"
        className="!w-3 !h-3 !bg-info-color !border-2 !border-surface"
      />
      
      {/* Header */}
      <div className={cn(
        'px-3 py-2 rounded-t-lg',
        data.isDefault ? 'bg-info-bg' : 'bg-surface-muted'
      )}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <LayoutDashboard className={cn(
              'w-4 h-4',
              data.isDefault ? 'text-info-color' : 'text-fg-muted'
            )} />
            <span className={cn(
              'text-xs font-semibold',
              data.isDefault ? 'text-info-color' : 'text-fg'
            )}>
              Dashboard
            </span>
          </div>
          {data.isDefault && (
            <span className="px-1.5 py-0.5 text-[10px] font-medium bg-info-bg text-info-color rounded">
              Default
            </span>
          )}
        </div>
      </div>
      
      {/* Dashboard Name */}
      <div className="px-3 py-2 border-b border-border">
        <h3 className="text-sm font-medium text-fg line-clamp-1">{data.name}</h3>
        {data.description && (
          <p className="text-xs text-fg-muted mt-0.5 line-clamp-1">{data.description}</p>
        )}
      </div>
      
      {/* Widget Grid Preview */}
      <div className="px-3 py-3 border-b border-border">
        <WidgetGrid layout={data.layout} widgetCount={data.widgetCount} />
        <div className="mt-2 flex items-center justify-between">
          <span className="text-xs text-fg-muted">
            {data.widgetCount} widget{data.widgetCount !== 1 ? 's' : ''} • {data.layout.columns}×{data.layout.rows} grid
          </span>
        </div>
      </div>
      
      {/* Widget Types */}
      {data.widgetSummary.length > 0 && (
        <div className="px-3 py-2 border-b border-border">
          <WidgetTypeSummary summary={data.widgetSummary} />
        </div>
      )}
      
      {/* Settings Row */}
      <div className="px-3 py-2 border-b border-border flex items-center gap-3">
        <div className="flex items-center gap-1 text-xs text-fg-muted">
          <Clock className="w-3 h-3" />
          <span>{TIME_RANGE_LABELS[data.timeRange]}</span>
        </div>
        {data.refreshInterval && (
          <div className="flex items-center gap-1 text-xs text-fg-muted">
            <RefreshCw className="w-3 h-3" />
            <span>{data.refreshInterval}s</span>
          </div>
        )}
        {data.variableCount > 0 && (
          <div className="flex items-center gap-1 text-xs text-fg-muted">
            <Variable className="w-3 h-3" />
            <span>{data.variableCount}</span>
          </div>
        )}
      </div>
      
      {/* Footer */}
      <div className="px-3 py-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div
            className="w-5 h-5 rounded-full bg-muted flex items-center justify-center text-[10px] font-medium text-fg-muted"
            title={data.createdBy.name}
          >
            {data.createdBy.avatarUrl ? (
              <img src={data.createdBy.avatarUrl} alt={data.createdBy.name} className="w-full h-full rounded-full" />
            ) : (
              data.createdBy.name.substring(0, 2).toUpperCase()
            )}
          </div>
          <span className="text-[10px] text-fg-muted">{data.createdBy.name}</span>
        </div>
        
        {data.viewCount !== undefined && (
          <div className="flex items-center gap-1 text-[10px] text-fg-muted">
            <User className="w-3 h-3" />
            <span>{data.viewCount} views</span>
          </div>
        )}
      </div>
    </div>
  );
}

export const DashboardNode = memo(DashboardNodeComponent);
export default DashboardNode;
