/**
 * Module Graph Canvas Content
 * 
 * Module dependency graph for Code × System level.
 * Visualizes module relationships and circular dependencies.
 * 
 * @doc.type component
 * @doc.purpose Module dependency visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Switch,
  FormControlLabel,
  Surface as Paper,
} from '@ghatana/ui';

interface Module {
    id: string;
    name: string;
    path: string;
    type: 'app' | 'lib' | 'component' | 'util';
    imports: string[];
    exports: number;
    linesOfCode: number;
    position: { x: number; y: number };
}

// Mock module data
const MOCK_MODULES: Module[] = [
    {
        id: 'app',
        name: 'App',
        path: 'src/App.tsx',
        type: 'app',
        imports: ['router', 'store', 'theme'],
        exports: 1,
        linesOfCode: 120,
        position: { x: 50, y: 15 },
    },
    {
        id: 'router',
        name: 'Router',
        path: 'src/router/index.ts',
        type: 'lib',
        imports: ['pages', 'guards'],
        exports: 1,
        linesOfCode: 85,
        position: { x: 30, y: 35 },
    },
    {
        id: 'store',
        name: 'Store',
        path: 'src/store/index.ts',
        type: 'lib',
        imports: ['api', 'models'],
        exports: 5,
        linesOfCode: 200,
        position: { x: 50, y: 35 },
    },
    {
        id: 'theme',
        name: 'Theme',
        path: 'src/theme/index.ts',
        type: 'lib',
        imports: ['constants'],
        exports: 3,
        linesOfCode: 150,
        position: { x: 70, y: 35 },
    },
    {
        id: 'pages',
        name: 'Pages',
        path: 'src/pages/index.ts',
        type: 'component',
        imports: ['components', 'hooks', 'store'],
        exports: 8,
        linesOfCode: 450,
        position: { x: 20, y: 55 },
    },
    {
        id: 'guards',
        name: 'Guards',
        path: 'src/router/guards.ts',
        type: 'util',
        imports: ['store'],
        exports: 3,
        linesOfCode: 65,
        position: { x: 40, y: 55 },
    },
    {
        id: 'api',
        name: 'API',
        path: 'src/services/api.ts',
        type: 'lib',
        imports: ['http', 'models'],
        exports: 12,
        linesOfCode: 320,
        position: { x: 50, y: 55 },
    },
    {
        id: 'components',
        name: 'Components',
        path: 'src/components/index.ts',
        type: 'component',
        imports: ['hooks', 'utils'],
        exports: 25,
        linesOfCode: 1200,
        position: { x: 20, y: 75 },
    },
    {
        id: 'hooks',
        name: 'Hooks',
        path: 'src/hooks/index.ts',
        type: 'util',
        imports: ['store', 'utils'],
        exports: 15,
        linesOfCode: 380,
        position: { x: 35, y: 75 },
    },
    {
        id: 'models',
        name: 'Models',
        path: 'src/models/index.ts',
        type: 'lib',
        imports: [],
        exports: 20,
        linesOfCode: 280,
        position: { x: 60, y: 75 },
    },
    {
        id: 'utils',
        name: 'Utils',
        path: 'src/utils/index.ts',
        type: 'util',
        imports: [],
        exports: 18,
        linesOfCode: 220,
        position: { x: 75, y: 75 },
    },
    {
        id: 'http',
        name: 'HTTP',
        path: 'src/services/http.ts',
        type: 'lib',
        imports: [],
        exports: 5,
        linesOfCode: 95,
        position: { x: 50, y: 90 },
    },
    {
        id: 'constants',
        name: 'Constants',
        path: 'src/constants/index.ts',
        type: 'util',
        imports: [],
        exports: 10,
        linesOfCode: 45,
        position: { x: 80, y: 55 },
    },
];

const getModuleColor = (type: Module['type']) => {
    switch (type) {
        case 'app':
            return { bg: '#E3F2FD', border: '#2196F3' };
        case 'lib':
            return { bg: '#F3E5F5', border: '#9C27B0' };
        case 'component':
            return { bg: '#E8F5E9', border: '#4CAF50' };
        case 'util':
            return { bg: '#FFF3E0', border: '#FF9800' };
    }
};

const ModuleNode = ({
    module,
    onClick,
    isSelected,
    isHighlighted,
}: {
    module: Module;
    onClick: (id: string) => void;
    isSelected: boolean;
    isHighlighted: boolean;
}) => {
    const colors = getModuleColor(module.type);
    const size = Math.min(Math.max(module.linesOfCode / 20 + 30, 40), 100);

    return (
        <Paper
            elevation={isSelected ? 8 : isHighlighted ? 4 : 2}
            onClick={() => onClick(module.id)}
            className="absolute" style={{ left: `${module.position.x, color: 'colors.border' }}
        >
            <Typography
                variant="caption"
                className="font-semibold text-center text-[0.7rem]" >
                {module.name}
            </Typography>
            <Typography variant="caption" className="text-gray-500 dark:text-gray-400 text-[0.6rem]">
                {module.exports}
            </Typography>
        </Paper>
    );
};

const DependencyLine = ({
    from,
    to,
    isHighlighted,
    isCircular,
}: {
    from: Module;
    to: Module;
    isHighlighted: boolean;
    isCircular: boolean;
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
                    id={`arrow-${from.id}-${to.id}`}
                    markerWidth="8"
                    markerHeight="8"
                    refX="6"
                    refY="4"
                    orient="auto"
                >
                    <polygon
                        points="0 0, 8 4, 0 8"
                        fill={isCircular ? '#F44336' : isHighlighted ? '#2196F3' : '#ccc'}
                    />
                </marker>
            </defs>
            <line
                x1={`${x1}%`}
                y1={`${y1}%`}
                x2={`${x2}%`}
                y2={`${y2}%`}
                stroke={isCircular ? '#F44336' : isHighlighted ? '#2196F3' : '#ccc'}
                strokeWidth={isHighlighted ? '2.5' : isCircular ? '2' : '1.5'}
                markerEnd={`url(#arrow-${from.id}-${to.id})`}
            />
        </svg>
    );
};

export const ModuleGraphCanvas = () => {
    const [modules] = useState<Module[]>(MOCK_MODULES);
    const [selectedModule, setSelectedModule] = useState<string | null>(null);
    const [showCircular, setShowCircular] = useState(true);

    const hasContent = modules.length > 0;

    // Detect circular dependencies
    const circularDeps = useMemo(() => {
        const circular: Set<string> = new Set();
        const visited = new Set<string>();
        const recStack = new Set<string>();

        const detectCycle = (moduleId: string): boolean => {
            visited.add(moduleId);
            recStack.add(moduleId);

            const module = modules.find(m => m.id === moduleId);
            if (!module) return false;

            for (const depId of module.imports) {
                if (!visited.has(depId)) {
                    if (detectCycle(depId)) {
                        circular.add(`${moduleId}-${depId}`);
                        return true;
                    }
                } else if (recStack.has(depId)) {
                    circular.add(`${moduleId}-${depId}`);
                    return true;
                }
            }

            recStack.delete(moduleId);
            return false;
        };

        modules.forEach(m => {
            if (!visited.has(m.id)) {
                detectCycle(m.id);
            }
        });

        return circular;
    }, [modules]);

    const handleModuleClick = (id: string) => {
        setSelectedModule(id === selectedModule ? null : id);
    };

    // Build connections
    const connections: Array<{ from: Module; to: Module; isHighlighted: boolean; isCircular: boolean }> = [];
    modules.forEach(module => {
        module.imports.forEach(targetId => {
            const target = modules.find(m => m.id === targetId);
            if (target) {
                const isHighlighted = selectedModule === module.id || selectedModule === target.id;
                const isCircular = circularDeps.has(`${module.id}-${target.id}`);
                if (!showCircular && isCircular) return;
                connections.push({ from: module, to: target, isHighlighted, isCircular });
            }
        });
    });

    const selectedMod = modules.find(m => m.id === selectedModule);
    const stats = useMemo(() => {
        return {
            totalModules: modules.length,
            totalDependencies: modules.reduce((acc, m) => acc + m.imports.length, 0),
            circularCount: circularDeps.size,
            totalLOC: modules.reduce((acc, m) => acc + m.linesOfCode, 0),
        };
    }, [modules, circularDeps]);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Analyze Project',
                    onClick: () => {
                        console.log('Analyze project');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full bg-[#fafafa]" style={{ backgroundImage: '`
            radial-gradient(circle, backgroundSize: '20px 20px' }} >
                {/* Dependency lines */}
                {connections.map((conn, i) => (
                    <DependencyLine
                        key={i}
                        from={conn.from}
                        to={conn.to}
                        isHighlighted={conn.isHighlighted}
                        isCircular={conn.isCircular}
                    />
                ))}

                {/* Module nodes */}
                {modules.map(module => {
                    const isHighlighted =
                        selectedModule === module.id ||
                        (selectedMod && selectedMod.imports.includes(module.id)) ||
                        (selectedMod && module.imports.includes(selectedMod.id));

                    return (
                        <ModuleNode
                            key={module.id}
                            module={module}
                            onClick={handleModuleClick}
                            isSelected={module.id === selectedModule}
                            isHighlighted={isHighlighted || false}
                        />
                    );
                })}

                {/* Stats panel */}
                <Box
                    className="absolute rounded top-[16px] left-[16px] bg-white p-4 shadow min-w-[220px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Module Graph Stats
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Modules: {stats.totalModules}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Dependencies: {stats.totalDependencies}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total LOC: {stats.totalLOC.toLocaleString()}
                    </Typography>
                    {circularDeps.size > 0 && (
                        <Box className="flex items-center gap-1 mt-2">
                            <Typography className="text-base">⚠️</Typography>
                            <Typography variant="caption" color="error.main">
                                {circularDeps.size} circular dep{circularDeps.size !== 1 ? 's' : ''}
                            </Typography>
                        </Box>
                    )}
                    <FormControlLabel
                        control={
                            <Switch
                                checked={showCircular}
                                onChange={(e) => setShowCircular(e.target.checked)}
                                size="small"
                            />
                        }
                        label={<Typography variant="caption">Show circular</Typography>}
                        className="mt-2"
                    />
                </Box>

                {/* Module details */}
                {selectedMod && (
                    <Box
                        className="absolute rounded top-[16px] right-[16px] bg-white p-4 shadow min-w-[280px]"
                    >
                        <Typography variant="subtitle2" gutterBottom className="font-semibold">
                            {selectedMod.name}
                        </Typography>
                        <Chip
                            label={selectedMod.type.toUpperCase()}
                            size="small"
                            className="text-white mb-2" style={{ backgroundColor: getModuleColor(selectedMod.type).border }}
                        />
                        <Typography variant="caption" display="block" className="font-mono mb-2">
                            {selectedMod.path}
                        </Typography>
                        <Typography variant="caption" display="block" color="text.secondary">
                            Exports: {selectedMod.exports}
                        </Typography>
                        <Typography variant="caption" display="block" color="text.secondary">
                            Lines: {selectedMod.linesOfCode}
                        </Typography>
                        {selectedMod.imports.length > 0 && (
                            <Box className="mt-2">
                                <Typography variant="caption" className="font-semibold" display="block">
                                    Imports ({selectedMod.imports.length}):
                                </Typography>
                                {selectedMod.imports.map(imp => (
                                    <Typography
                                        key={imp}
                                        variant="caption"
                                        display="block"
                                        className="text-blue-600 font-mono"
                                    >
                                        → {imp}
                                    </Typography>
                                ))}
                            </Box>
                        )}
                    </Box>
                )}

                {/* Legend */}
                <Box
                    className="absolute rounded bottom-[16px] left-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" gutterBottom>
                        Module Types
                    </Typography>
                    {(['app', 'lib', 'component', 'util'] as const).map(type => {
                        const colors = getModuleColor(type);
                        return (
                            <Box key={type} className="flex items-center gap-2 mt-1">
                                <Box
                                    className="w-[16px] h-[16px] rounded-full" style={{ border: `2px solid ${colors.border }}
                                />
                                <Typography variant="caption">
                                    {type}: {modules.filter(m => m.type === type).length}
                                </Typography>
                            </Box>
                        );
                    })}
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default ModuleGraphCanvas;
