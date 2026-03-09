/**
 * Dependency Edge Component
 * 
 * Custom ReactFlow edge showing artifact relationships and dependencies.
 * 
 * @doc.type component
 * @doc.purpose Dependency visualization edge
 * @doc.layer product
 * @doc.pattern Edge
 */

import * as React from 'react';
import { EdgeProps, getBezierPath, EdgeLabelRenderer, BaseEdge } from '@xyflow/react';
import { Box, Chip, Tooltip } from '@ghatana/ui';
import { Ban as BlockIcon, ChevronRight as RequiresIcon, Link as ReferenceIcon } from 'lucide-react';

export type DependencyType = 'requires' | 'blocks' | 'references';

export interface DependencyEdgeData {
    type: DependencyType;
    label?: string;
    isBlocked?: boolean;
}

const DEPENDENCY_CONFIG = {
    requires: {
        color: '#1976d2', // primary
        icon: <RequiresIcon size={16} />,
        label: 'Requires',
        strokeDasharray: '0',
    },
    blocks: {
        color: '#d32f2f', // error
        icon: <BlockIcon size={16} />,
        label: 'Blocks',
        strokeDasharray: '5,5',
    },
    references: {
        color: '#757575', // grey
        icon: <ReferenceIcon size={16} />,
        label: 'References',
        strokeDasharray: '2,2',
    },
};

export const DependencyEdge: React.FC<EdgeProps<DependencyEdgeData>> = ({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    markerEnd,
    selected,
}) => {
    const dependencyType = data?.type || 'references';
    const config = DEPENDENCY_CONFIG[dependencyType];
    const isBlocked = data?.isBlocked || false;

    const [edgePath, labelX, labelY] = getBezierPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
    });

    const edgeColor = isBlocked ? DEPENDENCY_CONFIG.blocks.color : config.color;
    const strokeWidth = selected ? 3 : isBlocked ? 2.5 : 2;

    return (
        <>
            {/* Main Edge Path */}
            <BaseEdge
                id={id}
                path={edgePath}
                markerEnd={markerEnd}
                style={{
                    stroke: edgeColor,
                    strokeWidth: strokeWidth.toString(),
                    strokeDasharray: config.strokeDasharray,
                    opacity: selected ? '1' : '0.7',
                    filter: selected ? 'drop-shadow(0 2px 4px rgba(0,0,0,0.2))' : 'none',
                } as React.CSSProperties}
            />

            {/* Edge Label */}
            <EdgeLabelRenderer>
                <Box
                    className="absolute"
                    style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
                >
                    <Tooltip
                        title={
                            <Box>
                                <Box className="font-bold mb-1">{config.label}</Box>
                                {data?.label && <Box>{data.label}</Box>}
                                {isBlocked && <Box className="mt-1 text-red-400" >⚠️ Blocking</Box>}
                            </Box>
                        }
                        arrow
                    >
                        <Chip
                            icon={config.icon}
                            label={data?.label || config.label}
                            size="sm"
                            className="bg-white dark:bg-gray-900 border text-[0.7rem] h-[24px] cursor-pointer transition-all duration-200 hover:text-white" style={{ borderColor: edgeColor, color: edgeColor }}
                        />
                    </Tooltip>
                </Box>
            </EdgeLabelRenderer>
        </>
    );
};
