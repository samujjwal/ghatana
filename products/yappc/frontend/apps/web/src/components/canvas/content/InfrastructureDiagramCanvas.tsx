/**
 * Infrastructure Diagram Canvas Content
 * 
 * Infrastructure topology for Deploy × System level.
 * Visualize deployment architecture.
 * 
 * @doc.type component
 * @doc.purpose Infrastructure topology visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Surface as Paper,
} from '@ghatana/ui';

interface InfraNode {
    id: string;
    name: string;
    type: 'load-balancer' | 'server' | 'database' | 'cache' | 'storage' | 'cdn';
    status: 'healthy' | 'warning' | 'error';
    region: string;
    connections: string[];
    x: number;
    y: number;
    metrics: {
        cpu: number;
        memory: number;
        requests?: number;
    };
}

const MOCK_NODES: InfraNode[] = [
    {
        id: 'lb1',
        name: 'Load Balancer',
        type: 'load-balancer',
        status: 'healthy',
        region: 'us-east-1',
        connections: ['srv1', 'srv2'],
        x: 200,
        y: 50,
        metrics: { cpu: 12, memory: 25, requests: 5400 },
    },
    {
        id: 'srv1',
        name: 'API Server 1',
        type: 'server',
        status: 'healthy',
        region: 'us-east-1',
        connections: ['db1', 'cache1'],
        x: 100,
        y: 200,
        metrics: { cpu: 45, memory: 62 },
    },
    {
        id: 'srv2',
        name: 'API Server 2',
        type: 'server',
        status: 'healthy',
        region: 'us-east-1',
        connections: ['db1', 'cache1'],
        x: 300,
        y: 200,
        metrics: { cpu: 38, memory: 58 },
    },
    {
        id: 'db1',
        name: 'PostgreSQL Primary',
        type: 'database',
        status: 'healthy',
        region: 'us-east-1',
        connections: ['db2'],
        x: 200,
        y: 350,
        metrics: { cpu: 28, memory: 72 },
    },
    {
        id: 'db2',
        name: 'PostgreSQL Replica',
        type: 'database',
        status: 'healthy',
        region: 'us-west-2',
        connections: [],
        x: 350,
        y: 400,
        metrics: { cpu: 15, memory: 68 },
    },
    {
        id: 'cache1',
        name: 'Redis Cache',
        type: 'cache',
        status: 'healthy',
        region: 'us-east-1',
        connections: [],
        x: 50,
        y: 350,
        metrics: { cpu: 8, memory: 34 },
    },
    {
        id: 'cdn1',
        name: 'CloudFront CDN',
        type: 'cdn',
        status: 'healthy',
        region: 'global',
        connections: ['lb1'],
        x: 200,
        y: -100,
        metrics: { cpu: 5, memory: 12 },
    },
    {
        id: 'stor1',
        name: 'S3 Storage',
        type: 'storage',
        status: 'healthy',
        region: 'us-east-1',
        connections: [],
        x: 450,
        y: 200,
        metrics: { cpu: 2, memory: 8 },
    },
];

const getNodeColor = (type: InfraNode['type']) => {
    switch (type) {
        case 'load-balancer':
            return '#3B82F6';
        case 'server':
            return '#10B981';
        case 'database':
            return '#8B5CF6';
        case 'cache':
            return '#F59E0B';
        case 'storage':
            return '#EC4899';
        case 'cdn':
            return '#06B6D4';
    }
};

const getStatusColor = (status: InfraNode['status']) => {
    switch (status) {
        case 'healthy':
            return '#10B981';
        case 'warning':
            return '#F59E0B';
        case 'error':
            return '#EF4444';
    }
};

const getNodeIcon = (type: InfraNode['type']) => {
    switch (type) {
        case 'load-balancer':
            return '⚖️';
        case 'server':
            return '🖥️';
        case 'database':
            return '💾';
        case 'cache':
            return '⚡';
        case 'storage':
            return '📦';
        case 'cdn':
            return '🌐';
    }
};

export const InfrastructureDiagramCanvas = () => {
    const [nodes] = useState<InfraNode[]>(MOCK_NODES);
    const [selectedNode, setSelectedNode] = useState<string | null>(null);

    const stats = useMemo(() => {
        return {
            total: nodes.length,
            healthy: nodes.filter(n => n.status === 'healthy').length,
            warning: nodes.filter(n => n.status === 'warning').length,
            error: nodes.filter(n => n.status === 'error').length,
            avgCpu: Math.round(nodes.reduce((sum, n) => sum + n.metrics.cpu, 0) / nodes.length),
            avgMemory: Math.round(nodes.reduce((sum, n) => sum + n.metrics.memory, 0) / nodes.length),
        };
    }, [nodes]);

    const hasContent = nodes.length > 0;

    const selectedNodeData = nodes.find(n => n.id === selectedNode);

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col relative bg-[#fafafa]">
                <Box className="flex-1 overflow-auto relative">
                    <svg width="600" height="600" style={{ display: 'block', margin: '0 auto' }}>
                        {/* Connections */}
                        {nodes.map(node =>
                            node.connections.map(targetId => {
                                const target = nodes.find(n => n.id === targetId);
                                if (!target) return null;
                                return (
                                    <line
                                        key={`${node.id}-${targetId}`}
                                        x1={node.x + 200}
                                        y1={node.y + 200}
                                        x2={target.x + 200}
                                        y2={target.y + 200}
                                        stroke="#94A3B8"
                                        strokeWidth="2"
                                        strokeDasharray={node.id === selectedNode || targetId === selectedNode ? '0' : '4'}
                                        opacity={selectedNode && node.id !== selectedNode && targetId !== selectedNode ? 0.2 : 0.6}
                                    />
                                );
                            })
                        )}

                        {/* Nodes */}
                        {nodes.map(node => (
                            <g key={node.id} onClick={() => setSelectedNode(node.id === selectedNode ? null : node.id)}>
                                <circle
                                    cx={node.x + 200}
                                    cy={node.y + 200}
                                    r={selectedNode === node.id ? 45 : 40}
                                    fill={getNodeColor(node.type)}
                                    stroke={selectedNode === node.id ? '#1E293B' : 'white'}
                                    strokeWidth={selectedNode === node.id ? 3 : 2}
                                    opacity={selectedNode && selectedNode !== node.id ? 0.4 : 1}
                                    style={{ cursor: 'pointer', transition: 'all 0.2s' }}
                                />
                                <circle
                                    cx={node.x + 200 + 25}
                                    cy={node.y + 200 - 25}
                                    r={8}
                                    fill={getStatusColor(node.status)}
                                    stroke="white"
                                    strokeWidth="2"
                                />
                                <text
                                    x={node.x + 200}
                                    y={node.y + 200}
                                    textAnchor="middle"
                                    dominantBaseline="middle"
                                    fill="white"
                                    fontSize="24"
                                    style={{ pointerEvents: 'none', userSelect: 'none', alignItems: 'start' }}
                                >
                                    {getNodeIcon(node.type)}
                                </text>
                                <text
                                    x={node.x + 200}
                                    y={node.y + 200 + 55}
                                    textAnchor="middle"
                                    fill="#1E293B"
                                    fontSize="12"
                                    fontWeight="600"
                                    style={{ pointerEvents: 'none', userSelect: 'none', gridTemplateColumns: 'repeat(2 }}
                                >
                                    {node.name}
                                </text>
                            </g>
                        ))}
                    </svg>
                </Box>

                {selectedNodeData && (
                    <Box
                        className="absolute rounded bottom-[16px] left-[16px] w-[300px] bg-white p-4 shadow-md"
                    >
                        <Box classNining sx: alignItems: 'start' */>
                            <Typography variant="subtitle2" className="font-semibold">
                                {selectedNodeData.name}
                            </Typography>
                            <Chip
                                label={selectedNodeData.status}
                                size="small"
                                className="text-white" style={{ backgroundColor: getStatusColor(selectedNodeData.status) }}
                            />
                        </Box>
                        <Typography variant="caption" display="block" color="text.secondary" className="mb-2">
                            Type: {selectedNodeData.type} • Region: {selectedNodeData.region}
                        </Typography>
                        <Box className="grid gap-2" >
                            <Paper className="p-2 bg-[#F8FAFC]">
                                <Typography variant="caption" color="text.secondary" display="block">
                                    CPU
                                </Typography>
                                <Typography variant="body2" className="font-semibold">
                                    {selectedNodeData.metrics.cpu}%
                                </Typography>
                            </Paper>
                            <Paper className="p-2 bg-[#F8FAFC]">
                                <Typography variant="caption" color="text.secondary" display="block">
                                    Memory
                                </Typography>
                                <Typography variant="body2" className="font-semibold">
                                    {selectedNodeData.metrics.memory}%
                                </Typography>
                            </Paper>
                        </Box>
                        {selectedNodeData.metrics.requests && (
                            <Typography variant="caption" display="block" className="mt-2">
                                Requests: {selectedNodeData.metrics.requests}/min
                            </Typography>
                        )}
                        <Typography variant="caption" display="block" className="mt-2">
                            Connections: {selectedNodeData.connections.length}
                        </Typography>
                    </Box>
                )}

                <Box
                    className="absolute rounded top-[16px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        Infrastructure Stats
                    </Typography>
                    <Typography variant="caption" display="block">
                        Total Nodes: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('healthy') }}>
                        Healthy: {stats.healthy}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('warning') }}>
                        Warning: {stats.warning}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('error') }}>
                        Error: {stats.error}
                    </Typography>
                    <Typography variant="caption" display="block" className="mt-2">
                        Avg CPU: {stats.avgCpu}%
                    </Typography>
                    <Typography variant="caption" display="block">
                        Avg Memory: {stats.avgMemory}%
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default InfrastructureDiagramCanvas;
