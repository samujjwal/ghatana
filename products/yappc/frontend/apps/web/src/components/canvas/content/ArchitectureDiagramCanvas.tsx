/**
 * Architecture Diagram Canvas Content
 * 
 * System architecture visualization for Diagram × System level.
 * Displays high-level system components and their relationships.
 * 
 * @doc.type component
 * @doc.purpose Architecture diagram for system visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Surface as Paper,
} from '@ghatana/ui';
import { HardDrive as Storage, Cloud, Plug as Api, Gauge as Speed, Cpu as Memory } from 'lucide-react';

interface SystemComponent {
    id: string;
    name: string;
    type: 'service' | 'database' | 'cache' | 'queue' | 'gateway' | 'storage';
    status: 'healthy' | 'warning' | 'error';
    connections: string[];
    position: { x: number; y: number };
    metrics?: {
        requests?: string;
        latency?: string;
        cpu?: string;
        memory?: string;
    };
}

// Mock architecture data
const MOCK_ARCHITECTURE: SystemComponent[] = [
    {
        id: 'api-gateway',
        name: 'API Gateway',
        type: 'gateway',
        status: 'healthy',
        connections: ['auth-service', 'user-service', 'order-service'],
        position: { x: 50, y: 10 },
        metrics: { requests: '1.2K/s', latency: '45ms' },
    },
    {
        id: 'auth-service',
        name: 'Auth Service',
        type: 'service',
        status: 'healthy',
        connections: ['user-db', 'redis-cache'],
        position: { x: 20, y: 35 },
        metrics: { cpu: '23%', memory: '512MB' },
    },
    {
        id: 'user-service',
        name: 'User Service',
        type: 'service',
        status: 'healthy',
        connections: ['user-db', 'redis-cache'],
        position: { x: 50, y: 35 },
        metrics: { cpu: '45%', memory: '1.2GB' },
    },
    {
        id: 'order-service',
        name: 'Order Service',
        type: 'service',
        status: 'warning',
        connections: ['order-db', 'message-queue'],
        position: { x: 80, y: 35 },
        metrics: { cpu: '78%', memory: '2.1GB' },
    },
    {
        id: 'user-db',
        name: 'User Database',
        type: 'database',
        status: 'healthy',
        connections: [],
        position: { x: 20, y: 65 },
    },
    {
        id: 'order-db',
        name: 'Order Database',
        type: 'database',
        status: 'healthy',
        connections: [],
        position: { x: 80, y: 65 },
    },
    {
        id: 'redis-cache',
        name: 'Redis Cache',
        type: 'cache',
        status: 'healthy',
        connections: [],
        position: { x: 35, y: 65 },
    },
    {
        id: 'message-queue',
        name: 'Message Queue',
        type: 'queue',
        status: 'healthy',
        connections: [],
        position: { x: 65, y: 65 },
    },
];

const getComponentIcon = (type: SystemComponent['type']) => {
    switch (type) {
        case 'service':
            return <Cloud />;
        case 'database':
            return <Storage />;
        case 'cache':
            return <Speed />;
        case 'queue':
            return <Memory />;
        case 'gateway':
            return <Api />;
        case 'storage':
            return <Storage />;
    }
};

const getComponentColor = (type: SystemComponent['type']) => {
    switch (type) {
        case 'service':
            return '#2196F3';
        case 'database':
            return '#4CAF50';
        case 'cache':
            return '#FF9800';
        case 'queue':
            return '#9C27B0';
        case 'gateway':
            return '#F44336';
        case 'storage':
            return '#607D8B';
    }
};

const getStatusColor = (status: SystemComponent['status']) => {
    switch (status) {
        case 'healthy':
            return '#4CAF50';
        case 'warning':
            return '#FF9800';
        case 'error':
            return '#F44336';
    }
};

const ComponentNode = ({
    component,
    onClick
}: {
    component: SystemComponent;
    onClick: (id: string) => void;
}) => {
    return (
        <Paper
            elevation={3}
            onClick={() => onClick(component.id)}
            className="absolute" style={{ left: `${component.position.x, backgroundColor: getComponentColor(component.type), backgroundColor: getStatusColor(component.status) }}
        >
            <Box className="flex items-center mb-2">
                <Box className="mr-2" style={{ color: getComponentColor(component.type) }}>
                    {getComponentIcon(component.type)}
                </Box>
                <Typography variant="subtitle2" className="flex-1 font-semibold">
                    {component.name}
                </Typography>
                <Box backgroundColor: getStatusColor(component.status) */
                />
            </Box>

            <Chip
                label={component.type.toUpperCase()}
                size="small"
                className="h-[20px] text-white text-[0.65rem] mb-2" />

            {component.metrics && (
                <Box className="mt-2">
                    {Object.entries(component.metrics).map(([key, value]) => (
                        <Typography key={key} variant="caption" display="block" color="text.secondary">
                            {key}: {value}
                        </Typography>
                    ))}
                </Box>
            )}

            {component.connections.length > 0 && (
                <Typography variant="caption" color="text.secondary" display="block" className="mt-2">
                    → {component.connections.length} connection{component.connections.length !== 1 ? 's' : ''}
                </Typography>
            )}
        </Paper>
    );
};

const ConnectionLine = ({
    from,
    to
}: {
    from: SystemComponent;
    to: SystemComponent;
}) => {
    const x1 = from.position.x;
    const y1 = from.position.y;
    const x2 = to.position.x;
    const y2 = to.position.y;

    return (
        <svg
            style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                pointerEvents: 'none',
                zIndex: 0,
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
                    <polygon points="0 0, 10 3, 0 6" fill="#999" />
                </marker>
            </defs>
            <line
                x1={`${x1}%`}
                y1={`${y1}%`}
                x2={`${x2}%`}
                y2={`${y2}%`}
                stroke="#999"
                strokeWidth="2"
                strokeDasharray="5,5"
                markerEnd="url(#arrowhead)"
            />
        </svg>
    );
};

export const ArchitectureDiagramCanvas = () => {
    const [components] = useState<SystemComponent[]>(MOCK_ARCHITECTURE);
    const [selectedComponent, setSelectedComponent] = useState<string | null>(null);

    const hasContent = components.length > 0;

    const handleComponentClick = (id: string) => {
        setSelectedComponent(id);
        console.log('Selected component:', id);
    };

    // Build connections
    const connections: Array<{ from: SystemComponent; to: SystemComponent }> = [];
    components.forEach(component => {
        component.connections.forEach(targetId => {
            const target = components.find(c => c.id === targetId);
            if (target) {
                connections.push({ from: component, to: target });
            }
        });
    });

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Create Architecture',
                    onClick: () => {
                        console.log('Create architecture');
                    },
                },
                secondaryAction: {
                    label: 'Import Existing',
                    onClick: () => {
                        console.log('Import architecture');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full bg-[#fafafa]" style={{ backgroundImage: '`
            linear-gradient(rgba(0, backgroundSize: '20px 20px' }} >
                {/* Connection lines */}
                {connections.map((conn, i) => (
                    <ConnectionLine key={i} from={conn.from} to={conn.to} />
                ))}

                {/* Component nodes */}
                {components.map(component => (
                    <ComponentNode
                        key={component.id}
                        component={component}
                        onClick={handleComponentClick}
                    />
                ))}

                {/* Legend */}
                <Box
                    className="absolute rounded bottom-[16px] right-[16px] bg-white p-4 shadow min-w-[200px]"
                >
                    <Typography variant="subtitle2" gutterBottom>
                        System Status
                    </Typography>
                    <Box className="flex items-center gap-2 mt-2">
                        <Box className="rounded-full w-[12px] h-[12px] bg-[#4CAF50]" />
                        <Typography variant="caption">Healthy: {components.filter(c => c.status === 'healthy').length}</Typography>
                    </Box>
                    <Box className="flex items-center gap-2 mt-1">
                        <Box className="rounded-full w-[12px] h-[12px] bg-[#FF9800]" />
                        <Typography variant="caption">Warning: {components.filter(c => c.status === 'warning').length}</Typography>
                    </Box>
                    <Box className="flex items-center gap-2 mt-1">
                        <Box className="rounded-full w-[12px] h-[12px] bg-[#F44336]" />
                        <Typography variant="caption">Error: {components.filter(c => c.status === 'error').length}</Typography>
                    </Box>
                </Box>

                {/* Selected component info */}
                {selectedComponent && (
                    <Box
                        className="absolute rounded top-[16px] right-[16px] bg-white p-4 shadow min-w-[250px]"
                    >
                        <Typography variant="subtitle2" gutterBottom>
                            Selected: {components.find(c => c.id === selectedComponent)?.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            Click on other components to view their details
                        </Typography>
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default ArchitectureDiagramCanvas;
