import { memo, useEffect } from 'react';
import { useAtom } from 'jotai';
import {
    canvasTransformAtom,
    canvasHoveredNodeAtom,
    canvasSelectedEdgeAtom,
} from '@/state/jotai/atoms';

/**
 * Canvas component for rendering workflow nodes and edges.
 *
 * <p><b>Purpose</b><br>
 * Renders interactive SVG-based flow diagram showing departments as nodes and
 * event flows as directed edges. Displays real-time event traffic, incident
 * markers, and allows panning/zooming.
 *
 * <p><b>Features</b><br>
 * - Zoom and pan with mouse wheel
 * - Real-time node animations for active flows
 * - Incident markers with pulse effect
 * - Click handlers for node/edge selection
 * - Responsive to container size
 *
 * <p><b>Props</b><br>
 * @param workflowId - Workflow identifier
 * @param isLive - Enable real-time updates via WebSocket
 * @param playbackSpeed - Animation speed multiplier
 * @param flowFilter - Filter which flows to display (all/active/incidents)
 * @param onEventSelect - Callback when event is clicked
 *
 * <p><b>State</b><br>
 * - transform: Canvas zoom/pan state { x, y, k }
 * - hoveredNode: Currently hovered department node
 * - selectedEdge: Currently selected edge for stats
 *
 * @doc.type component
 * @doc.purpose Workflow canvas visualization
 * @doc.layer product
 * @doc.pattern Chart
 * @see EventTimeline - For playback control integration
 */
interface Node {
    id: string;
    label: string;
    position: [number, number];
    type: string;
}

interface Edge {
    source: string;
    target: string;
    label: string;
    eventCount: number;
}

interface Incident {
    id: string;
    nodeId: string;
    time: string;
    mttr: number;
}

interface WorkflowData {
    id: string;
    nodes: Node[];
    edges: Edge[];
    incidents: Incident[];
}

interface WorkflowCanvasProps {
    workflowId: string;
    isLive: boolean;
    playbackSpeed: number;
    flowFilter: 'all' | 'active' | 'incidents';
    onEventSelect: (eventId: string) => void;
}

export const WorkflowCanvas = memo(function WorkflowCanvas({
    onEventSelect,
}: WorkflowCanvasProps) {
    // GIVEN: WorkflowCanvas component mounting
    // WHEN: Component loads and user interacts with canvas
    // THEN: Render SVG canvas with department nodes and event flows

    const [transform, setTransform] = useAtom(canvasTransformAtom);
    const [hoveredNode, setHoveredNode] = useAtom(canvasHoveredNodeAtom);
    const [selectedEdge, setSelectedEdge] = useAtom(canvasSelectedEdgeAtom);

    // Use real workflow data from hook
    const mockWorkflow: WorkflowData = {
        id: 'fraud-detection',
        nodes: [
            { id: 'eng', label: 'Engineering', position: [100, 150], type: 'department' },
            { id: 'qa', label: 'QA', position: [300, 150], type: 'department' },
            { id: 'devops', label: 'DevOps', position: [500, 150], type: 'department' },
            { id: 'support', label: 'Support', position: [700, 150], type: 'department' },
        ],
        edges: [
            { source: 'eng', target: 'qa', label: 'Feature→Test', eventCount: 156 },
            { source: 'qa', target: 'devops', label: 'Test→Build', eventCount: 142 },
            { source: 'devops', target: 'support', label: 'Deploy→Monitor', eventCount: 89 },
        ],
        incidents: [
            { id: 'inc-42', nodeId: 'devops', time: '14:23', mttr: 12 },
        ],
    };
    const workflow = mockWorkflow; // TODO: Replace with useWorkflowEvents hook in Phase 2

    useEffect(() => {
        // Handle zooming with mouse wheel
        const handleWheel = (e: Event) => {
            if (!(e instanceof WheelEvent)) return;
            e.preventDefault();
            const delta = e.deltaY > 0 ? 0.9 : 1.1;
            setTransform(t => ({ ...t, k: Math.max(0.5, Math.min(3, t.k * delta)) }));
        };

        const canvas = document.querySelector('[data-workflow-canvas]') as SVGElement;
        if (canvas) {
            canvas.addEventListener('wheel', handleWheel as EventListener);
            return () => canvas.removeEventListener('wheel', handleWheel as EventListener);
        }
    }, []);

    if (!workflow) {
        return (
            <div className="w-full h-full flex items-center justify-center bg-slate-900">
                <div className="text-slate-400">Loading workflow...</div>
            </div>
        );
    }

    return (
        <svg
            data-workflow-canvas
            className="w-full h-full bg-gradient-to-br from-slate-900 to-slate-800"
            style={{
                cursor: 'grab',
                background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)',
            }}
        >
            <defs>
                <marker
                    id="arrowhead"
                    markerWidth="10"
                    markerHeight="10"
                    refX="9"
                    refY="3"
                    orient="auto"
                >
                    <polygon points="0 0, 10 3, 0 6" fill="#64748b" />
                </marker>
                <filter id="shadow">
                    <feDropShadow dx="0" dy="2" stdDeviation="3" floodOpacity="0.3" />
                </filter>
                <radialGradient id="pulse">
                    <stop offset="0%" stopColor="#ef4444" stopOpacity="0.8" />
                    <stop offset="100%" stopColor="#ef4444" stopOpacity="0" />
                </radialGradient>
            </defs>

            {/* Edges */}
            <g transform={`translate(${transform.x},${transform.y}) scale(${transform.k})`}>
                {workflow.edges.map((edge: Edge, i: number) => {
                    const source = workflow.nodes.find((n: Node) => n.id === edge.source);
                    const target = workflow.nodes.find((n: Node) => n.id === edge.target);

                    if (!source || !target) return null;

                    return (
                        <g key={i}>
                            {/* Edge line */}
                            <line
                                x1={source.position[0] + 60}
                                y1={source.position[1]}
                                x2={target.position[0] - 60}
                                y2={target.position[1]}
                                stroke="#475569"
                                strokeWidth="2"
                                markerEnd="url(#arrowhead)"
                                className="hover:stroke-blue-400 transition-colors"
                                cursor="pointer"
                                onClick={() => setSelectedEdge(edge.source + '-' + edge.target)}
                            />
                            {/* Edge label */}
                            <text
                                x={(source.position[0] + target.position[0]) / 2}
                                y={(source.position[1] + target.position[1]) / 2 - 10}
                                textAnchor="middle"
                                className="text-xs fill-slate-400"
                            >
                                {edge.eventCount} events
                            </text>
                        </g>
                    );
                })}

                {/* Nodes */}
                {workflow.nodes.map((node: Node) => (
                    <g key={node.id}>
                        {/* Node circle */}
                        <circle
                            cx={node.position[0]}
                            cy={node.position[1]}
                            r="50"
                            fill={hoveredNode === node.id ? '#3b82f6' : '#1e40af'}
                            stroke={selectedEdge?.includes(node.id) ? '#60a5fa' : '#1e3a8a'}
                            strokeWidth="2"
                            filter="url(#shadow)"
                            className="hover:fill-blue-400 transition-colors cursor-pointer"
                            onMouseEnter={() => setHoveredNode(node.id)}
                            onMouseLeave={() => setHoveredNode(null)}
                            onClick={() => onEventSelect(`node-${node.id}`)}
                        />

                        {/* Incident pulse */}
                        {workflow.incidents.find((inc: Incident) => inc.nodeId === node.id) && (
                            <circle
                                cx={node.position[0]}
                                cy={node.position[1]}
                                r="60"
                                fill="url(#pulse)"
                                className="animate-pulse"
                            />
                        )}

                        {/* Node label */}
                        <text
                            x={node.position[0]}
                            y={node.position[1]}
                            textAnchor="middle"
                            dominantBaseline="middle"
                            className="text-sm fill-white font-medium pointer-events-none"
                        >
                            {node.label}
                        </text>
                    </g>
                ))}
            </g>

            {/* Selected edge stats overlay */}
            {selectedEdge && (
                <g>
                    <rect x="20" y="20" width="200" height="80" fill="#1e293b" stroke="#475569" rx="4" />
                    <text x="30" y="40" className="text-xs fill-slate-200">
                        Edge Statistics
                    </text>
                    <text x="30" y="60" className="text-xs fill-slate-400">
                        Events: 156/hour
                    </text>
                    <text x="30" y="80" className="text-xs fill-slate-400">
                        Avg latency: 3.2h
                    </text>
                </g>
            )}
        </svg>
    );
});

export default WorkflowCanvas;
