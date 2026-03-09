/**
 * Mind Map Canvas Content
 * 
 * Mind mapping for Brainstorm × System level.
 * Visualizes ideas and their hierarchical relationships.
 * 
 * @doc.type component
 * @doc.purpose Mind map visualization for brainstorming
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface MindMapNode {
    id: string;
    label: string;
    level: number;
    parentId: string | null;
    children: string[];
    color: string;
    notes?: string;
    tags?: string[];
    position: { x: number; y: number };
}

// Mock mind map data
const MOCK_NODES: MindMapNode[] = [
    {
        id: 'root',
        label: 'E-Commerce Platform',
        level: 0,
        parentId: null,
        children: ['auth', 'products', 'orders', 'payments'],
        color: '#6366F1',
        notes: 'Central platform concept',
        tags: ['core', 'system'],
        position: { x: 50, y: 15 },
    },
    {
        id: 'auth',
        label: 'Authentication',
        level: 1,
        parentId: 'root',
        children: ['login', 'register', 'social'],
        color: '#8B5CF6',
        notes: 'User identity management',
        tags: ['security', 'user'],
        position: { x: 20, y: 35 },
    },
    {
        id: 'login',
        label: 'Login Flow',
        level: 2,
        parentId: 'auth',
        children: [],
        color: '#A78BFA',
        notes: 'Email/password authentication',
        position: { x: 10, y: 55 },
    },
    {
        id: 'register',
        label: 'Registration',
        level: 2,
        parentId: 'auth',
        children: [],
        color: '#A78BFA',
        notes: 'New user signup',
        position: { x: 20, y: 55 },
    },
    {
        id: 'social',
        label: 'Social Login',
        level: 2,
        parentId: 'auth',
        children: [],
        color: '#A78BFA',
        notes: 'OAuth integration',
        tags: ['oauth', 'third-party'],
        position: { x: 30, y: 55 },
    },
    {
        id: 'products',
        label: 'Product Catalog',
        level: 1,
        parentId: 'root',
        children: ['search', 'categories', 'recommendations'],
        color: '#10B981',
        notes: 'Product browsing and discovery',
        tags: ['catalog', 'inventory'],
        position: { x: 40, y: 35 },
    },
    {
        id: 'search',
        label: 'Search Engine',
        level: 2,
        parentId: 'products',
        children: [],
        color: '#34D399',
        notes: 'Full-text search with filters',
        tags: ['elasticsearch'],
        position: { x: 35, y: 55 },
    },
    {
        id: 'categories',
        label: 'Categories',
        level: 2,
        parentId: 'products',
        children: [],
        color: '#34D399',
        notes: 'Hierarchical product organization',
        position: { x: 42, y: 55 },
    },
    {
        id: 'recommendations',
        label: 'AI Recommendations',
        level: 2,
        parentId: 'products',
        children: [],
        color: '#34D399',
        notes: 'Personalized product suggestions',
        tags: ['ml', 'ai'],
        position: { x: 48, y: 55 },
    },
    {
        id: 'orders',
        label: 'Order Management',
        level: 1,
        parentId: 'root',
        children: ['cart', 'checkout', 'tracking'],
        color: '#F59E0B',
        notes: 'End-to-end order processing',
        tags: ['transactions', 'fulfillment'],
        position: { x: 60, y: 35 },
    },
    {
        id: 'cart',
        label: 'Shopping Cart',
        level: 2,
        parentId: 'orders',
        children: [],
        color: '#FBBF24',
        notes: 'Temporary item storage',
        position: { x: 55, y: 55 },
    },
    {
        id: 'checkout',
        label: 'Checkout Flow',
        level: 2,
        parentId: 'orders',
        children: [],
        color: '#FBBF24',
        notes: 'Multi-step purchase process',
        position: { x: 62, y: 55 },
    },
    {
        id: 'tracking',
        label: 'Order Tracking',
        level: 2,
        parentId: 'orders',
        children: [],
        color: '#FBBF24',
        notes: 'Real-time shipment updates',
        tags: ['notifications'],
        position: { x: 68, y: 55 },
    },
    {
        id: 'payments',
        label: 'Payment Gateway',
        level: 1,
        parentId: 'root',
        children: ['stripe', 'paypal', 'crypto'],
        color: '#EF4444',
        notes: 'Secure payment processing',
        tags: ['pci-dss', 'security'],
        position: { x: 80, y: 35 },
    },
    {
        id: 'stripe',
        label: 'Stripe Integration',
        level: 2,
        parentId: 'payments',
        children: [],
        color: '#F87171',
        notes: 'Credit card payments',
        position: { x: 75, y: 55 },
    },
    {
        id: 'paypal',
        label: 'PayPal',
        level: 2,
        parentId: 'payments',
        children: [],
        color: '#F87171',
        notes: 'Alternative payment method',
        position: { x: 82, y: 55 },
    },
    {
        id: 'crypto',
        label: 'Crypto Payments',
        level: 2,
        parentId: 'payments',
        children: [],
        color: '#F87171',
        notes: 'Bitcoin, Ethereum support',
        tags: ['web3'],
        position: { x: 88, y: 55 },
    },
];

const MindMapNodeComponent = ({
    node,
    onClick,
    isSelected,
    isConnected,
}: {
    node: MindMapNode;
    onClick: (id: string) => void;
    isSelected: boolean;
    isConnected: boolean;
}) => {
    const size = node.level === 0 ? 160 : node.level === 1 ? 130 : 110;
    const fontSize = node.level === 0 ? '1rem' : node.level === 1 ? '0.875rem' : '0.75rem';

    return (
        <Box
            onClick={() => onClick(node.id)}
            className="absolute" style={{ left: `${node.position.x }}
        >
            <Paper
                elevation={isSelected ? 8 : isConnected ? 4 : 2}
                className="w-full h-full" style={{ backgroundColor: isSelected ? node.color : isConnected ? `${node.color }}
            >
                <Typography
                    variant="subtitle2"
                    style={{
                        fontSize,
                        fontWeight: node.level === 0 ? 700 : 600,
                        textAlign: 'center',
                        color: isSelected ? 'white' : node.color,
                        lineHeight: 1.2,
                    }}
                >
                    {node.label}
                </Typography>

                {node.tags && node.tags.length > 0 && (
                    <Box className="flex gap-1 flex-wrap justify-center">
                        {node.tags.slice(0, 2).map(tag => (
                            <Chip
                                key={tag}
                                label={tag}
                                size="small"
                                className="h-[16px] text-[0.6rem]" style={{ backgroundColor: isSelected ? 'white' : `${node.color }}
                            />
                        ))}
                    </Box>
                )}

                {node.children.length > 0 && (
                    <Typography
                        variant="caption"
                        className="text-[0.65rem] opacity-[0.8]" style={{ color: isSelected ? 'white' : 'text.secondary' }}
                    >
                        {node.children.length} subtopic{node.children.length !== 1 ? 's' : ''}
                    </Typography>
                )}
            </Paper>
        </Box>
    );
};

const ConnectionLine = ({
    from,
    to,
    isHighlighted,
}: {
    from: { x: number; y: number };
    to: { x: number; y: number };
    isHighlighted: boolean;
}) => {
    return (
        <line
            x1={`${from.x}%`}
            y1={`${from.y}%`}
            x2={`${to.x}%`}
            y2={`${to.y}%`}
            stroke={isHighlighted ? '#6366F1' : '#CBD5E1'}
            strokeWidth={isHighlighted ? 3 : 2}
            strokeDasharray={isHighlighted ? undefined : '5,5'}
            opacity={isHighlighted ? 1 : 0.6}
        />
    );
};

export const MindMapCanvas = () => {
    const [nodes] = useState<MindMapNode[]>(MOCK_NODES);
    const [selectedNode, setSelectedNode] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const filteredNodes = useMemo(() => {
        if (!searchQuery) return nodes;
        return nodes.filter(
            node =>
                node.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
                node.notes?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                node.tags?.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()))
        );
    }, [nodes, searchQuery]);

    const connections = useMemo(() => {
        const conns: Array<{ from: { x: number; y: number }; to: { x: number; y: number }; isHighlighted: boolean }> =
            [];
        nodes.forEach(node => {
            if (node.parentId) {
                const parent = nodes.find(n => n.id === node.parentId);
                if (parent) {
                    const isHighlighted = Boolean(
                        selectedNode === node.id ||
                        selectedNode === parent.id ||
                        (selectedNode && parent.children.includes(selectedNode))
                    );
                    conns.push({
                        from: parent.position,
                        to: node.position,
                        isHighlighted,
                    });
                }
            }
        });
        return conns;
    }, [nodes, selectedNode]);

    const connectedNodeIds = useMemo(() => {
        if (!selectedNode) return new Set<string>();
        const selected = nodes.find(n => n.id === selectedNode);
        if (!selected) return new Set<string>();

        const ids = new Set<string>([selectedNode]);
        ids.add(selected.parentId || '');
        selected.children.forEach(childId => ids.add(childId));

        return ids;
    }, [nodes, selectedNode]);

    const stats = useMemo(() => {
        return {
            total: nodes.length,
            levels: Math.max(...nodes.map(n => n.level)) + 1,
            branches: nodes.filter(n => n.level === 1).length,
            leaves: nodes.filter(n => n.children.length === 0).length,
        };
    }, [nodes]);

    const hasContent = nodes.length > 0;

    const selectedNodeData = nodes.find(n => n.id === selectedNode);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Create Mind Map',
                    onClick: () => {
                        console.log('Create Mind Map');
                    },
                },
                secondaryAction: {
                    label: 'Import from Markdown',
                    onClick: () => {
                        console.log('Import from Markdown');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full flex flex-col bg-[#fafafa]"
            >
                {/* Top toolbar */}
                <Box
                    className="z-[10] p-4 bg-white" style={{ borderBottom: '1px solid rgba(0 }} >
                    <Box className="flex gap-4 items-center">
                        <TextField
                            size="small"
                            placeholder="Search topics, notes, tags..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            Add Topic
                        </Button>
                        <Button variant="outlined" size="small">
                            Export
                        </Button>
                    </Box>
                </Box>

                {/* Canvas area */}
                <Box
                    className="flex-1 relative overflow-hidden bg-[#F8FAFC]"
                >
                    {/* SVG for connection lines */}
                    <svg
                        style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            width: '100%',
                            height: '100%',
                            pointerEvents: 'none',
                            zIndex: 1,
                        }}
                    >
                        {connections.map((conn, idx) => (
                            <ConnectionLine key={idx} from={conn.from} to={conn.to} isHighlighted={conn.isHighlighted} />
                        ))}
                    </svg>

                    {/* Nodes */}
                    {filteredNodes.map(node => (
                        <MindMapNodeComponent
                            key={node.id}
                            node={node}
                            onClick={setSelectedNode}
                            isSelected={node.id === selectedNode}
                            isConnected={connectedNodeIds.has(node.id)}
                        />
                    ))}

                    {filteredNodes.length === 0 && (
                        <Box className="flex justify-center items-center h-full">
                            <Typography color="text.secondary">No topics match your search</Typography>
                        </Box>
                    )}
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded bottom-[16px] left-[16px] bg-white p-4 shadow min-w-[180px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Mind Map Stats
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total Topics: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Levels: {stats.levels}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Main Branches: {stats.branches}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Leaf Nodes: {stats.leaves}
                    </Typography>
                </Box>

                {/* Node details */}
                {selectedNodeData && (
                    <Box
                        className="absolute top-[80px] right-[16px] bg-white p-4 rounded shadow-lg min-w-[280px] max-w-[350px]" style={{ border: `3px solid ${selectedNodeData.color }}
                    >
                        <Typography variant="subtitle2" className="font-semibold mb-2">
                            {selectedNodeData.label}
                        </Typography>

                        {selectedNodeData.notes && (
                            <Typography variant="body2" className="text-[0.85rem] mb-2 text-gray-500 dark:text-gray-400">
                                {selectedNodeData.notes}
                            </Typography>
                        )}

                        {selectedNodeData.tags && selectedNodeData.tags.length > 0 && (
                            <Box className="flex flex-wrap gap-1 mb-2">
                                {selectedNodeData.tags.map(tag => (
                                    <Chip key={tag} label={tag} size="small" variant="outlined" />
                                ))}
                            </Box>
                        )}

                        <Typography variant="caption" display="block" color="text.secondary">
                            Level: {selectedNodeData.level}
                        </Typography>
                        {selectedNodeData.parentId && (
                            <Typography variant="caption" display="block" color="text.secondary">
                                Parent: {nodes.find(n => n.id === selectedNodeData.parentId)?.label}
                            </Typography>
                        )}
                        {selectedNodeData.children.length > 0 && (
                            <Typography variant="caption" display="block" color="text.secondary">
                                Children: {selectedNodeData.children.length}
                            </Typography>
                        )}
                    </Box>
                )}

                {/* Legend */}
                <Box
                    className="absolute rounded bottom-[16px] right-[16px] bg-white p-3 shadow min-w-[140px]"
                >
                    <Typography variant="caption" className="font-semibold block mb-1">
                        Level Colors
                    </Typography>
                    <Box className="flex items-center gap-2 mb-[2.4px]">
                        <Box className="rounded w-[16px] h-[16px] bg-[#6366F1]" />
                        <Typography variant="caption" color="text.secondary">
                            Core (0)
                        </Typography>
                    </Box>
                    <Box className="flex items-center gap-2 mb-[2.4px]">
                        <Box className="rounded w-[16px] h-[16px] bg-[#10B981]" />
                        <Typography variant="caption" color="text.secondary">
                            Branch (1)
                        </Typography>
                    </Box>
                    <Box className="flex items-center gap-2">
                        <Box className="rounded w-[16px] h-[16px] bg-[#34D399]" />
                        <Typography variant="caption" color="text.secondary">
                            Leaf (2+)
                        </Typography>
                    </Box>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default MindMapCanvas;
