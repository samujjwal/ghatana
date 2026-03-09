/**
 * Portal Element Component - Special node for sub-canvas linking
 * Enables hierarchical drill-down navigation between canvases
 */

import { Handle, Position } from '@xyflow/react';
import clsx from 'clsx';
import React from 'react';

/**
 *
 */
export interface PortalElementProps {
  id: string;
  data: {
    label: string;
    targetCanvasId: string;
    targetCanvasName?: string;
    description?: string;
    portalType: 'canvas' | 'component' | 'page';
    color?: string;
    icon?: React.ReactNode;
  };
  selected?: boolean;
  onNavigate?: (targetCanvasId: string) => void;
}

export const PortalElement: React.FC<PortalElementProps> = ({
  id,
  data,
  selected,
  onNavigate,
}) => {
  const handlePortalClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onNavigate && data.targetCanvasId) {
      onNavigate(data.targetCanvasId);
    }
  };

  const getPortalIcon = () => {
    if (data.icon) return data.icon;

    switch (data.portalType) {
      case 'canvas':
        return <span className="text-sm">🌐</span>;
      case 'component':
        return <span className="text-sm">🔗</span>;
      case 'page':
        return <span className="text-sm">📄</span>;
      default:
        return <span className="text-sm">🌐</span>;
    }
  };

  const portalColor = data.color || '#8b5cf6'; // Default purple

  return (
    <div
      className={clsx(
        'relative bg-white border-2 rounded-lg shadow-lg transition-all duration-200',
        'min-w-[180px] min-h-[100px] p-3',
        selected
          ? 'border-blue-500 shadow-blue-200'
          : 'border-gray-300 hover:border-gray-400',
        'cursor-pointer hover:shadow-md'
      )}
      style={{
        borderLeftColor: portalColor,
        borderLeftWidth: '4px',
      }}
      onClick={handlePortalClick}
      data-testid={`portal-element-${id}`}
    >
      {/* Input Handles */}
      <Handle
        type="target"
        position={Position.Left}
        className="h-3 w-3 bg-gray-400 border-2 border-white"
        style={{ left: -6 }}
      />
      <Handle
        type="target"
        position={Position.Top}
        className="h-3 w-3 bg-gray-400 border-2 border-white"
        style={{ top: -6 }}
      />

      {/* Portal Content */}
      <div className="flex items-start space-x-3">
        <div
          className="flex-shrink-0 p-2 rounded-md"
          style={{ backgroundColor: `${portalColor}20` }}
        >
          {getPortalIcon()}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center space-x-2">
            <h3 className="text-sm font-semibold text-gray-900 truncate">
              {data.label}
            </h3>
            <span className="text-xs text-gray-400">↗</span>
          </div>

          {data.targetCanvasName && (
            <p className="text-xs text-gray-500 mt-1 truncate">
              → {data.targetCanvasName}
            </p>
          )}

          {data.description && (
            <p className="text-xs text-gray-600 mt-1 line-clamp-2">
              {data.description}
            </p>
          )}

          <div className="flex items-center mt-2 space-x-1">
            <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded">
              {data.portalType}
            </span>
          </div>
        </div>
      </div>

      {/* Output Handles */}
      <Handle
        type="source"
        position={Position.Right}
        className="h-3 w-3 bg-gray-400 border-2 border-white"
        style={{ right: -6 }}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="h-3 w-3 bg-gray-400 border-2 border-white"
        style={{ bottom: -6 }}
      />

      {/* Portal Indicator */}
      <div className="absolute top-1 right-1">
        <div
          className="h-2 w-2 rounded-full animate-pulse"
          style={{ backgroundColor: portalColor }}
        />
      </div>
    </div>
  );
};

export default PortalElement;