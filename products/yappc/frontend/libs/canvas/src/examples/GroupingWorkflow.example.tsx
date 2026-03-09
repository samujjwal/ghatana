/**
 * Grouping Workflow Example
 * 
 * Demonstrates Journey 1.1 PM Handoff Workflow:
 * 1. PM selects multiple nodes on canvas
 * 2. Groups them with custom label
 * 3. Sets status to "Pending Review" (Orange border)
 * 4. Architect reviews and marks "Ready for Dev" (Green border)
 * 
 * @doc.type example
 * @doc.purpose Demonstrate node grouping workflow for PM handoff
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useCallback, useState } from 'react';
import { ReactFlow, ReactFlowProvider, Panel } from '@xyflow/react';
import { Background } from '@reactflow/background';
import { Controls } from '@reactflow/controls';
import { Box, Typography } from '@ghatana/ui';
import { GroupingToolbar } from '../components/GroupingToolbar';
import { NodeGroup } from '../components/NodeGroup';
import { useNodeGrouping } from '../hooks/useNodeGrouping';

import type { Node, Edge } from '@xyflow/react';

// Example: PM has created these nodes for a Profile feature
const initialNodes: Node[] = [
    {
        id: 'login-screen',
        type: 'default',
        position: { x: 100, y: 100 },
        data: { label: 'Login Screen' },
    },
    {
        id: 'dashboard',
        type: 'default',
        position: { x: 100, y: 200 },
        data: { label: 'Dashboard' },
    },
    {
        id: 'profile-view',
        type: 'default',
        position: { x: 100, y: 300 },
        data: { label: 'Profile View' },
    },
    {
        id: 'edit-mode',
        type: 'default',
        position: { x: 100, y: 400 },
        data: { label: 'Edit Mode' },
    },
    {
        id: 'sticky-note',
        type: 'default',
        position: { x: 300, y: 400 },
        data: { label: 'Note: Must support cropping' },
    },
];

const initialEdges: Edge[] = [
    { id: 'e1', source: 'login-screen', target: 'dashboard' },
    { id: 'e2', source: 'dashboard', target: 'profile-view' },
    { id: 'e3', source: 'profile-view', target: 'edit-mode' },
];

// Node types including our NodeGroup
const nodeTypes = {
    nodeGroup: NodeGroup,
};

/**
 * GroupingWorkflowExample Component
 */
export const GroupingWorkflowExample: React.FC = () => {
    const [nodes, setNodes] = useState<Node[]>(initialNodes);
    const [edges, setEdges] = useState<Edge[]>(initialEdges);
    const [statusLog, setStatusLog] = useState<string[]>([
        'PM: Created 5 nodes for Profile feature',
    ]);

    // Use the grouping hook
    const {
        selectedNodes,
        groupSelectedNodes,
        ungroupSelectedNodes,
        changeGroupStatus,
    } = useNodeGrouping({
        onGroupCreated: (group) => {
            setStatusLog((prev) => [
                ...prev,
                `✅ PM: Created group "${group.data.label}" with ${group.data.children?.length || 0} nodes`,
            ]);
        },
        onGroupRemoved: () => {
            setStatusLog((prev) => [...prev, '🗑️ PM: Removed group']);
        },
        onStatusChanged: (groupId, newStatus) => {
            const statusLabels = {
                unknown: 'Unknown',
                pending: 'Pending Review',
                ready: 'Ready for Dev',
                inProgress: 'In Progress',
                blocked: 'Blocked',
                completed: 'Completed',
            };
            const persona = newStatus === 'pending' ? 'PM' : 'Architect';
            setStatusLog((prev) => [
                ...prev,
                `🔄 ${persona}: Changed status to "${statusLabels[newStatus]}"`,
            ]);
        },
    });

    const handleNodesChange = useCallback((changes: unknown) => {
        // Handle node changes (selection, position, etc.)
    }, []);

    const handleEdgesChange = useCallback((changes: unknown) => {
        // Handle edge changes
    }, []);

    return (
        <Box className="w-full flex gap-4 h-[600px]">
            {/* Canvas Area */}
            <Box className="flex-1 rounded border border-solid border-[#e0e0e0]">
                <ReactFlowProvider>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={handleNodesChange}
                        onEdgesChange={handleEdgesChange}
                        nodeTypes={nodeTypes}
                        fitView
                        proOptions={{ hideAttribution: true }}
                    >
                        <Background />
                        <Controls />

                        {/* Grouping Toolbar in top-right */}
                        <Panel position="top-right">
                            <GroupingToolbar
                                selectedNodes={selectedNodes}
                                onGroup={() => groupSelectedNodes('Profile Module', 'pending')}
                                onUngroup={ungroupSelectedNodes}
                                onChangeStatus={changeGroupStatus}
                            />
                        </Panel>
                    </ReactFlow>
                </ReactFlowProvider>
            </Box>

            {/* Status Log Panel */}
            <Box
                className="rounded p-4 overflow-y-auto w-[300px] border border-solid border-[#e0e0e0] bg-[#fafafa]"
            >
                <Typography as="h6" gutterBottom>
                    Workflow Log
                </Typography>
                <Box className="flex flex-col gap-2">
                    {statusLog.map((log, index) => (
                        <Typography
                            key={index}
                            as="p" className="text-sm"
                            className="p-2 bg-white rounded-sm" style={{ borderLeft: '3px solid #1976d2' }} >
                            {log}
                        </Typography>
                    ))}
                </Box>

                {/* Instructions */}
                <Box className="mt-6 p-4 rounded bg-[#e3f2fd]">
                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                        🎯 Try This Workflow:
                    </Typography>
                    <Typography as="p" className="text-sm" component="div">
                        <ol style={{ margin: 0, paddingLeft: 20, backgroundColor: 'status.color' }}>
                            <li>Select 2+ nodes (Shift+Click or drag-select)</li>
                            <li>Click "Group" button</li>
                            <li>Group appears with gray border (Unknown status)</li>
                            <li>Status auto-set to "Pending Review" (Orange)</li>
                            <li>Click "Status" → "Ready for Dev" (Green)</li>
                            <li>Select group and click "Ungroup" to remove</li>
                        </ol>
                    </Typography>
                </Box>

                {/* Status Reference */}
                <Box className="mt-4 p-4 rounded bg-[#fff]">
                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                        Status Colors:
                    </Typography>
                    <Box className="flex flex-col gap-1">
                        {[
                            { label: 'Unknown', color: '#9e9e9e' },
                            { label: 'Pending Review', color: '#ff9800' },
                            { label: 'Ready for Dev', color: '#4caf50' },
                            { label: 'In Progress', color: '#2196f3' },
                            { label: 'Blocked', color: '#f44336' },
                            { label: 'Completed', color: '#9c27b0' },
                        ].map((status) => (
                            <Box key={status.label} className="flex items-center gap-2">
                                <Box
                                    className="rounded-full w-[16px] h-[16px]" />
                                <Typography as="span" className="text-xs text-gray-500">{status.label}</Typography>
                            </Box>
                        ))}
                    </Box>
                </Box>
            </Box>
        </Box>
    );
};

/**
 * Example usage in a route:
 * 
 * ```tsx
 * import { GroupingWorkflowExample } from '@ghatana/yappc-canvas/examples/GroupingWorkflow.example';
 * 
 * export default function GroupingDemo() {
 *   return <GroupingWorkflowExample />;
 * }
 * ```
 */
