/**
 * Component Diagram Canvas Content
 * 
 * Component relationship visualization for Diagram × Component level.
 * Displays frontend/backend components and their dependencies.
 * 
 * @doc.type component
 * @doc.purpose Component diagram for application structure
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
import { LayoutGrid as ViewModule, Braces as DataObject, Code, FileCode as Class } from 'lucide-react';

interface Component {
    id: string;
    name: string;
    type: 'ui' | 'service' | 'util' | 'hook';
    dependencies: string[];
    props?: string[];
    exports?: string[];
    position: { x: number; y: number };
    linesOfCode?: number;
}

// Mock component data
const MOCK_COMPONENTS: Component[] = [
    {
        id: 'app',
        name: 'App',
        type: 'ui',
        dependencies: ['router', 'auth-provider'],
        exports: ['App'],
        position: { x: 50, y: 10 },
        linesOfCode: 45,
    },
    {
        id: 'router',
        name: 'Router',
        type: 'ui',
        dependencies: ['dashboard', 'profile', 'settings'],
        exports: ['Router'],
        position: { x: 50, y: 30 },
        linesOfCode: 120,
    },
    {
        id: 'dashboard',
        name: 'Dashboard',
        type: 'ui',
        dependencies: ['api-service', 'use-data'],
        props: ['userId', 'onRefresh'],
        exports: ['Dashboard'],
        position: { x: 20, y: 50 },
        linesOfCode: 230,
    },
    {
        id: 'profile',
        name: 'Profile',
        type: 'ui',
        dependencies: ['api-service', 'use-auth'],
        props: ['userId'],
        exports: ['Profile'],
        position: { x: 50, y: 50 },
        linesOfCode: 180,
    },
    {
        id: 'settings',
        name: 'Settings',
        type: 'ui',
        dependencies: ['api-service', 'use-settings'],
        props: ['userId', 'onChange'],
        exports: ['Settings'],
        position: { x: 80, y: 50 },
        linesOfCode: 150,
    },
    {
        id: 'api-service',
        name: 'ApiService',
        type: 'service',
        dependencies: ['http-client', 'auth-util'],
        exports: ['get', 'post', 'put', 'delete'],
        position: { x: 35, y: 70 },
        linesOfCode: 320,
    },
    {
        id: 'auth-provider',
        name: 'AuthProvider',
        type: 'service',
        dependencies: ['use-auth', 'storage-util'],
        exports: ['AuthProvider', 'useAuth'],
        position: { x: 65, y: 30 },
        linesOfCode: 200,
    },
    {
        id: 'use-data',
        name: 'useData',
        type: 'hook',
        dependencies: ['api-service'],
        exports: ['useData'],
        position: { x: 15, y: 80 },
        linesOfCode: 85,
    },
    {
        id: 'use-auth',
        name: 'useAuth',
        type: 'hook',
        dependencies: [],
        exports: ['useAuth'],
        position: { x: 50, y: 80 },
        linesOfCode: 95,
    },
    {
        id: 'use-settings',
        name: 'useSettings',
        type: 'hook',
        dependencies: ['storage-util'],
        exports: ['useSettings'],
        position: { x: 85, y: 80 },
        linesOfCode: 60,
    },
];

const getComponentIcon = (type: Component['type']) => {
    switch (type) {
        case 'ui':
            return <ViewModule />;
        case 'service':
            return <DataObject />;
        case 'util':
            return <Code />;
        case 'hook':
            return <Class />;
    }
};

const getComponentColor = (type: Component['type']) => {
    switch (type) {
        case 'ui':
            return '#2196F3';
        case 'service':
            return '#4CAF50';
        case 'util':
            return '#FF9800';
        case 'hook':
            return '#9C27B0';
    }
};

const ComponentNode = ({
    component,
    onClick,
    isSelected,
}: {
    component: Component;
    onClick: (id: string) => void;
    isSelected: boolean;
}) => {
    return (
        <Paper
            elevation={isSelected ? 8 : 3}
            onClick={() => onClick(component.id)}
            className="absolute" style={{ left: `${component.position.x, backgroundColor: getComponentColor(component.type), color: getComponentColor(component.type) }}
        >
            <Box className="flex items-center mb-ning sx: color: getComponentColor(component.type) */>
                    {getComponentIcon(component.type)}
                </Box>
                <Typography variant="body2" className="flex-1 font-semibold text-[0.85rem]">
                    {component.name}
                </Typography>
            </Box>

            <Chip
                label={component.type.toUpperCase()}
                size="small"
                className="h-[18px] text-white text-[0.6rem]" />

            {component.linesOfCode && (
                <Typography variant="caption" display="block" color="text.secondary" className="text-[0.7rem] mt-1">
                    {component.linesOfCode} LOC
                </Typography>
            )}

            {component.dependencies.length > 0 && (
                <Typography variant="caption" color="text.secondary" display="block" className="text-[0.7rem] mt-1">
                    → {component.dependencies.length} deps
                </Typography>
            )}
        </Paper>
    );
};

const ConnectionLine = ({
    from,
    to,
    isHighlighted,
}: {
    from: Component;
    to: Component;
    isHighlighted: boolean;
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
                zIndex: isHighlighted ? 1 : 0,
            }}
        >
            <defs>
                <marker
                    id={`arrow-${isHighlighted ? 'highlight' : 'normal'}`}
                    markerWidth="8"
                    markerHeight="8"
                    refX="7"
                    refY="3"
                    orient="auto"
                >
                    <polygon points="0 0, 8 3, 0 6" fill={isHighlighted ? '#2196F3' : '#ccc'} />
                </marker>
            </defs>
            <line
                x1={`${x1}%`}
                y1={`${y1}%`}
                x2={`${x2}%`}
                y2={`${y2}%`}
                stroke={isHighlighted ? '#2196F3' : '#ccc'}
                strokeWidth={isHighlighted ? '2.5' : '1.5'}
                markerEnd={`url(#arrow-${isHighlighted ? 'highlight' : 'normal'})`}
            />
        </svg>
    );
};

export const ComponentDiagramCanvas = () => {
    const [components] = useState<Component[]>(MOCK_COMPONENTS);
    const [selectedComponent, setSelectedComponent] = useState<string | null>(null);

    const hasContent = components.length > 0;

    const handleComponentClick = (id: string) => {
        setSelectedComponent(id === selectedComponent ? null : id);
    };

    // Build connections
    const connections: Array<{ from: Component; to: Component; isHighlighted: boolean }> = [];
    components.forEach(component => {
        component.dependencies.forEach(targetId => {
            const target = components.find(c => c.id === targetId);
            if (target) {
                const isHighlighted = selectedComponent === component.id || selectedComponent === target.id;
                connections.push({ from: component, to: target, isHighlighted });
            }
        });
    });

    const selectedComp = components.find(c => c.id === selectedComponent);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Analyze Components',
                    onClick: () => {
                        console.log('Analyze components');
                    },
                },
                secondaryAction: {
                    label: 'Import from Code',
                    onClick: () => {
                        console.log('Import from code');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full bg-[#fafafa]" style={{ backgroundImage: '`
            radial-gradient(circle, backgroundSize: '20px 20px' }} >
                {/* Connection lines */}
                {connections.map((conn, i) => (
                    <ConnectionLine key={i} from={conn.from} to={conn.to} isHighlighted={conn.isHighlighted} />
                ))}

                {/* Component nodes */}
                {components.map(component => (
                    <ComponentNode
                        key={component.id}
                        component={component}
                        onClick={handleComponentClick}
                        isSelected={component.id === selectedComponent}
                    />
                ))}

                {/* Legend */}
                <Box
                    className="absolute rounded bottom-[16px] left-[16px] bg-white p-4 shadow min-w-[180px]"
                >
                    <Typography variant="subtitle2" gutterBottom>
                        Component Types
                    </Typography>
                    {(['ui', 'service', 'hook', 'util'] as const).map(type => (
                        <Box key={type} className="flex items-center gap-2 mt-1">
                            <Box className="text-base" style={{ color: getComponentColor(type) }} >
                                {getComponentIcon(type)}
                            </Box>
                            <Typography variant="caption">
                                {type.toUpperCase()}: {components.filter(c => c.type === type).length}
                            </Typography>
                        </Box>
                    ))}
                </Box>

                {/* Component details */}
                {selectedComp && (
                    <Box
                        className="absolute rounded top-[16px] right-[16px] bg-white p-4 shadow min-w-[280px] max-w-[350px]"
                    >
                        <Typography variant="subtitle2" gutterBottom className="font-semibold">
                            {selectedComp.name}
                        </Typography>
                        <Chip
                            label={selectedComp.type.toUpperCase()}
                            size="small"
                            className="text-white mb-2" style={{ backgroundColor: getComponentColor(selectedComp.type) }}
                        />

                        {selectedComp.props && selectedComp.props.length > 0 && (
                            <Box className="mt-2">
                                <Typography variant="caption" className="font-semibold" display="block">
                                    Props:
                                </Typography>
                                {selectedComp.props.map(prop => (
                                    <Typography key={prop} variant="caption" display="block" className="text-gray-500 dark:text-gray-400 font-mono">
                                        • {prop}
                                    </Typography>
                                ))}
                            </Box>
                        )}

                        {selectedComp.exports && selectedComp.exports.length > 0 && (
                            <Box className="mt-2">
                                <Typography variant="caption" className="font-semibold" display="block">
                                    Exports:
                                </Typography>
                                {selectedComp.exports.map(exp => (
                                    <Typography key={exp} variant="caption" display="block" className="text-gray-500 dark:text-gray-400 font-mono">
                                        • {exp}
                                    </Typography>
                                ))}
                            </Box>
                        )}

                        {selectedComp.dependencies.length > 0 && (
                            <Box className="mt-2">
                                <Typography variant="caption" className="font-semibold" display="block">
                                    Dependencies ({selectedComp.dependencies.length}):
                                </Typography>
                                {selectedComp.dependencies.map(dep => (
                                    <Typography key={dep} variant="caption" display="block" className="text-blue-600 font-mono">
                                        → {dep}
                                    </Typography>
                                ))}
                            </Box>
                        )}

                        {selectedComp.linesOfCode && (
                            <Typography variant="caption" display="block" color="text.secondary" className="mt-2">
                                Lines of Code: {selectedComp.linesOfCode}
                            </Typography>
                        )}
                    </Box>
                )}

                {/* Instructions */}
                {!selectedComponent && (
                    <Box
                        className="absolute rounded top-[16px] right-[16px] p-3 shadow-sm" style={{ backgroundColor: 'rgba(255' }} >
                        <Typography variant="caption" color="text.secondary">
                            Click on components to view details
                        </Typography>
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default ComponentDiagramCanvas;
