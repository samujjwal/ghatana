/**
 * Test Execution Edge Component
 * 
 * Animated edge component that visualizes test execution flow.
 * Shows pulse animation during execution and color-coded results.
 * Follows Journey 4.1 Step 3 (QA - Test Execution Visualization).
 * 
 * Features:
 * - Pulse animation during test execution (blue)
 * - Success state with green pulse
 * - Failure state with red pulse
 * - Default state (gray)
 * 
 * @doc.type component
 * @doc.purpose Visual feedback for test execution on edges
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useMemo } from 'react';
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@xyflow/react';

/**
 * Test execution status for edges
 */
export type TestExecutionStatus = 'idle' | 'running' | 'passed' | 'failed';

/**
 * Extended edge data with test execution status
 */
export interface TestExecutionEdgeData {
    /** Test execution status */
    testStatus?: TestExecutionStatus;
    /** Original edge label */
    label?: string;
}

/**
 * CSS animation keyframes for pulse effect
 */
const pulseAnimationStyles = `
@keyframes edge-pulse-running {
    0%, 100% { 
        stroke-opacity: 0.6;
        stroke-width: 2;
    }
    50% { 
        stroke-opacity: 1;
        stroke-width: 3;
    }
}

@keyframes edge-pulse-passed {
    0%, 100% { 
        stroke-opacity: 0.7;
        stroke-width: 2;
    }
    50% { 
        stroke-opacity: 1;
        stroke-width: 3.5;
    }
}

@keyframes edge-pulse-failed {
    0%, 100% { 
        stroke-opacity: 0.7;
        stroke-width: 2;
    }
    50% { 
        stroke-opacity: 1;
        stroke-width: 3.5;
    }
}

.edge-running {
    animation: edge-pulse-running 1.5s ease-in-out infinite;
    stroke: #3b82f6 !important;
}

.edge-passed {
    animation: edge-pulse-passed 2s ease-in-out 3;
    stroke: #22c55e !important;
}

.edge-failed {
    animation: edge-pulse-failed 2s ease-in-out 3;
    stroke: #ef4444 !important;
}

.edge-idle {
    stroke: #94a3b8;
}
`;

// Inject styles on first render
if (typeof document !== 'undefined') {
    const styleId = 'test-execution-edge-styles';
    if (!document.getElementById(styleId)) {
        const style = document.createElement('style');
        style.id = styleId;
        style.textContent = pulseAnimationStyles;
        document.head.appendChild(style);
    }
}

/**
 * TestExecutionEdge Component
 * 
 * Renders an animated edge with test execution status.
 * 
 * @param props - React Flow edge props
 */
export function TestExecutionEdge({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    style = {},
    data,
    markerEnd,
}: EdgeProps<TestExecutionEdgeData>) {
    const testStatus = data?.testStatus || 'idle';

    // Calculate bezier path
    const [edgePath, labelX, labelY] = getBezierPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
    });

    // Determine edge styling based on test status
    const edgeStyle = useMemo(() => {
        const baseStyle = {
            ...style,
            strokeWidth: 2,
        };

        switch (testStatus) {
            case 'running':
                return {
                    ...baseStyle,
                    stroke: '#3b82f6',
                    strokeOpacity: 0.8,
                    animation: 'edge-pulse-running 1.5s ease-in-out infinite',
                };
            case 'passed':
                return {
                    ...baseStyle,
                    stroke: '#22c55e',
                    strokeOpacity: 0.8,
                    animation: 'edge-pulse-passed 2s ease-in-out 3',
                };
            case 'failed':
                return {
                    ...baseStyle,
                    stroke: '#ef4444',
                    strokeOpacity: 0.8,
                    animation: 'edge-pulse-failed 2s ease-in-out 3',
                };
            default:
                return {
                    ...baseStyle,
                    stroke: '#94a3b8',
                };
        }
    }, [testStatus, style]);

    // Status badge styling
    const statusBadgeStyle = useMemo(() => {
        const baseStyle: React.CSSProperties = {
            padding: '4px 8px',
            borderRadius: '4px',
            fontSize: '10px',
            fontWeight: 600,
            textTransform: 'uppercase' as const,
            pointerEvents: 'none' as const,
            boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        };

        switch (testStatus) {
            case 'running':
                return {
                    ...baseStyle,
                    background: '#3b82f6',
                    color: '#ffffff',
                };
            case 'passed':
                return {
                    ...baseStyle,
                    background: '#22c55e',
                    color: '#ffffff',
                };
            case 'failed':
                return {
                    ...baseStyle,
                    background: '#ef4444',
                    color: '#ffffff',
                };
            default:
                return null;
        }
    }, [testStatus]);

    // Show status badge only during active states
    const showStatusBadge = testStatus !== 'idle';

    return (
        <>
            <BaseEdge
                id={id}
                path={edgePath}
                markerEnd={markerEnd}
                style={edgeStyle}
            />
            {showStatusBadge && (
                <EdgeLabelRenderer>
                    <div
                        style={{
                            position: 'absolute',
                            transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
                            pointerEvents: 'all',
                        }}
                    >
                        <div style={statusBadgeStyle!}>
                            {testStatus === 'running' && '⚡ Running'}
                            {testStatus === 'passed' && '✓ Passed'}
                            {testStatus === 'failed' && '✗ Failed'}
                        </div>
                    </div>
                </EdgeLabelRenderer>
            )}
        </>
    );
}

/**
 * Default export for React Flow edge types
 */
export default TestExecutionEdge;
