/**
 * RoleNode Component
 * 
 * Displays an individual role in the inheritance tree with permissions count
 * and visual indicators for highlighted states.
 */

import { memo } from 'react';
import { Handle, Position } from 'reactflow';
import type { NodeProps } from 'reactflow';
import type { RoleNodeData } from './types';

/**
 * RoleNode - Individual role display in the inheritance tree
 * 
 * Features:
 * - Shows role name and permission count
 * - Highlights when role contains specific permission
 * - Handles for connecting to parent/child nodes
 * - Hover states and visual feedback
 */
export const RoleNode = memo<NodeProps<RoleNodeData>>(({ data, selected }) => {
    const { label, permissions, isHighlighted, permissionCount, level = 0 } = data;

    // Color scheme based on level (darker for root, lighter for descendants)
    const levelColors = [
        'bg-blue-500 border-blue-600',
        'bg-blue-400 border-blue-500',
        'bg-blue-300 border-blue-400',
        'bg-blue-200 border-blue-300',
    ];

    const colorClass = levelColors[Math.min(level, levelColors.length - 1)];

    return (
        <div className="relative">
            {/* Top handle for parent connections */}
            <Handle
                type="target"
                position={Position.Top}
                className="w-3 h-3 !bg-blue-500"
            />

            {/* Main node content */}
            <div
                className={`
                    ${colorClass}
                    ${selected ? 'ring-4 ring-blue-600' : ''}
                    ${isHighlighted ? 'shadow-xl ring-2 ring-yellow-400' : 'shadow-lg'}
                    min-w-[180px] rounded-lg p-4 border-2
                    hover:shadow-xl transition-all duration-200
                    cursor-pointer
                `}
            >
                {/* Highlight indicator */}
                {isHighlighted && (
                    <div className="absolute -top-2 -right-2 bg-yellow-400 rounded-full w-5 h-5 animate-pulse border-2 border-white" />
                )}

                {/* Role name */}
                <div className="font-bold text-white text-base mb-1 text-center">
                    {label}
                </div>

                {/* Permission count */}
                <div className="text-sm text-white/90 text-center">
                    {permissionCount ?? permissions.length} permission{(permissionCount ?? permissions.length) !== 1 ? 's' : ''}
                </div>

                {/* Level indicator (for debugging) */}
                {process.env.NODE_ENV === 'development' && (
                    <div className="text-xs text-white/70 text-center mt-1">
                        Level {level}
                    </div>
                )}
            </div>

            {/* Bottom handle for child connections */}
            <Handle
                type="source"
                position={Position.Bottom}
                className="w-3 h-3 !bg-blue-500"
            />
        </div>
    );
});

RoleNode.displayName = 'RoleNode';
