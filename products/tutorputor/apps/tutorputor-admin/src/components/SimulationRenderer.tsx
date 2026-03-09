/**
 * Simulation Renderer Component
 * 
 * Renders interactive simulations and visualizations based on their type.
 * Supports physics, chemistry, biology, and mathematical simulations.
 * 
 * @doc.type component
 * @doc.purpose Render educational simulations and visualizations
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import { Stage, Layer } from 'react-konva';
import { KonvaEntityRenderer, type PhysicsEntity } from '@ghatana/tutorputor-physics-simulation';

interface SimulationDefinition {
    id: string;
    conceptId: string;
    type: string;
    manifest: Record<string, unknown>;
    estimatedTimeMinutes: number;
    interactivityLevel: string;
    purpose: string;
    previewConfig?: Record<string, unknown>;
    status: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}

interface VisualizationDefinition {
    id: string;
    conceptId: string;
    type: string;
    config: Record<string, unknown>;
    dataSource: string;
    status: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}

interface SimulationRendererProps {
    simulation?: SimulationDefinition | null;
    visualization?: VisualizationDefinition | null;
    className?: string;
    interactive?: boolean;
}

/**
 * Physics simulation renderer for 2D simulations
 */
function Physics2DSimulation({ manifest, config }: { manifest: Record<string, unknown>; config?: Record<string, unknown> }) {
    const width = (config?.width as number) || 800;
    const height = (config?.height as number) || 600;

    // Cast manifest entities to PhysicsEntity[]
    // In a real app, we should validate this with Zod
    const entities = (manifest.entities as PhysicsEntity[]) || [];

    return (
        <div className="bg-gray-100 rounded border-2 border-gray-300 overflow-hidden">
            <Stage width={width} height={height}>
                <Layer>
                    {entities.map((entity) => (
                        <KonvaEntityRenderer
                            key={entity.id}
                            entity={entity}
                            isSelected={false}
                            isDraggable={false}
                            onSelect={() => { }}
                            onDragMove={() => { }}
                        />
                    ))}
                </Layer>
            </Stage>
            <p className="text-xs text-gray-500 p-2">
                Physics 2D Simulation - {(manifest.title as string) || 'Untitled'}
            </p>
        </div>
    );
}

/**
 * Graph visualization renderer for 2D/3D graphs
 */
function GraphVisualization({
    config,
    type
}: {
    config: Record<string, unknown>;
    type: string
}) {
    const width = (config?.width as number) || 600;
    const height = (config?.height as number) || 400;
    const is3D = type.includes('3d');

    return (
        <div className="bg-white rounded border-2 border-blue-300 overflow-hidden">
            <svg
                width={width}
                height={height}
                viewBox={`0 0 ${width} ${height}`}
                className="w-full h-auto"
                data-type={type}
            >
                {/* Grid */}
                <defs>
                    <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                        <path
                            d="M 40 0 L 0 0 0 40"
                            fill="none"
                            stroke="#f0f0f0"
                            strokeWidth="0.5"
                        />
                    </pattern>
                </defs>
                <rect width={width} height={height} fill="url(#grid)" />

                {/* Axes */}
                <line x1="50" y1={height - 50} x2={width - 50} y2={height - 50} stroke="black" strokeWidth="2" />
                <line x1="50" y1="50" x2="50" y2={height - 50} stroke="black" strokeWidth="2" />

                {/* Sample data point */}
                <circle cx={width / 2} cy={height / 2} r="5" fill="red" />

                <text
                    x={width / 2}
                    y={height - 20}
                    textAnchor="middle"
                    className="text-xs"
                    fill="black"
                >
                    X Axis
                </text>
                <text
                    x="20"
                    y={height / 2}
                    textAnchor="middle"
                    className="text-xs"
                    fill="black"
                >
                    Y Axis
                </text>
            </svg>
            <p className="text-xs text-gray-500 p-2">
                {is3D ? '3D' : '2D'} Graph Visualization
            </p>
        </div>
    );
}

/**
 * Interactive visualization renderer for charts and diagrams
 */
function InteractiveVisualization({
    config,
    type
}: {
    config: Record<string, unknown>;
    type: string
}) {
    const [selectedPoint, setSelectedPoint] = useState<number | null>(null);

    const dataPoints = (config?.dataPoints as number[]) || [10, 20, 30, 25, 35];
    const labels = (config?.labels as string[]) || ['A', 'B', 'C', 'D', 'E'];
    const maxValue = Math.max(...dataPoints);

    return (
        <div className="bg-gradient-to-br from-blue-50 to-indigo-50 rounded border-2 border-indigo-300 p-4">
            <h3 className="text-sm font-semibold mb-4 text-gray-700">
                {type === 'chart' ? 'Bar Chart' : 'Interactive Diagram'}
            </h3>

            <div className="flex items-end justify-around h-48 gap-2 mb-4">
                {dataPoints.map((value, index) => (
                    <div
                        key={index}
                        className="flex flex-col items-center cursor-pointer"
                        onClick={() => setSelectedPoint(selectedPoint === index ? null : index)}
                    >
                        <div
                            className={`w-12 transition-all rounded-t ${selectedPoint === index ? 'bg-red-500' : 'bg-blue-500'
                                } hover:bg-blue-600`}
                            style={{
                                height: `${(value / maxValue) * 150}px`,
                                transition: 'all 0.2s ease',
                            }}
                        />
                        <label className="text-xs font-medium mt-2">{labels[index]}</label>
                        {selectedPoint === index && (
                            <span className="text-xs text-red-600 font-bold">{value}</span>
                        )}
                    </div>
                ))}
            </div>

            {selectedPoint !== null && (
                <div className="bg-white p-3 rounded border border-gray-200">
                    <p className="text-sm">
                        <strong>{labels[selectedPoint]}</strong>: {dataPoints[selectedPoint]} units
                    </p>
                </div>
            )}

            <p className="text-xs text-gray-500 mt-2">
                Click on bars to see values
            </p>
        </div>
    );
}

/**
 * Molecule/Structure visualization renderer
 */
function MoleculeVisualization({ config }: { config: Record<string, unknown> }) {
    const atoms = (config?.atoms as Array<{ symbol: string; x: number; y: number }>) || [
        { symbol: 'C', x: 150, y: 150 },
        { symbol: 'H', x: 100, y: 100 },
        { symbol: 'H', x: 200, y: 100 },
        { symbol: 'H', x: 100, y: 200 },
        { symbol: 'H', x: 200, y: 200 },
    ];

    return (
        <div className="bg-white rounded border-2 border-purple-300 p-4">
            <svg width="300" height="300" viewBox="0 0 300 300" className="w-full h-auto">
                {/* Draw bonds */}
                {atoms.slice(1).map((atom, index) => (
                    <line
                        key={`bond-${index}`}
                        x1={atoms[0].x}
                        y1={atoms[0].y}
                        x2={atom.x}
                        y2={atom.y}
                        stroke="#333"
                        strokeWidth="2"
                    />
                ))}

                {/* Draw atoms */}
                {atoms.map((atom, index) => (
                    <g key={`atom-${index}`}>
                        <circle
                            cx={atom.x}
                            cy={atom.y}
                            r="15"
                            fill={atom.symbol === 'C' ? '#333' : '#fff'}
                            stroke="#333"
                            strokeWidth="2"
                        />
                        <text
                            x={atom.x}
                            y={atom.y}
                            textAnchor="middle"
                            dy="0.3em"
                            fill={atom.symbol === 'C' ? '#fff' : '#333'}
                            className="font-bold text-xs"
                        >
                            {atom.symbol}
                        </text>
                    </g>
                ))}
            </svg>
            <p className="text-xs text-gray-500 text-center mt-2">Molecular Structure</p>
        </div>
    );
}

/**
 * Main SimulationRenderer component
 */
export function SimulationRenderer({
    simulation,
    visualization,
    className = '',
    interactive = true,
}: SimulationRendererProps) {
    // No simulation or visualization
    if (!simulation && !visualization) {
        return (
            <div className={`bg-gray-50 rounded border-2 border-dashed border-gray-300 p-6 text-center ${className}`}>
                <p className="text-gray-500">No simulation or visualization available</p>
            </div>
        );
    }

    return (
        <div className={`space-y-4 ${className}`}>
            {simulation && (
                <div className="border rounded-lg p-4 bg-white">
                    <div className="mb-3">
                        <h3 className="font-semibold text-lg">Simulation</h3>
                        <p className="text-sm text-gray-600">{simulation.purpose}</p>
                        <div className="text-xs text-gray-500 mt-2 space-y-1">
                            <p>Type: <code className="bg-gray-100 px-1 rounded">{simulation.type}</code></p>
                            <p>Interactivity: {simulation.interactivityLevel}</p>
                            <p>Est. Time: {simulation.estimatedTimeMinutes} minutes</p>
                        </div>
                    </div>

                    <div className="border-t pt-3 mt-3">
                        {simulation.type === 'physics-2D' && (
                            <Physics2DSimulation
                                manifest={simulation.manifest}
                                config={simulation.previewConfig}
                            />
                        )}
                        {!simulation.type.includes('physics') && (
                            <div className="bg-gray-100 rounded p-4 text-center text-gray-600">
                                <p>Simulation type: <code>{simulation.type}</code></p>
                                <p className="text-xs mt-2">Preview renderer for this type coming soon</p>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {visualization && (
                <div className="border rounded-lg p-4 bg-white">
                    <div className="mb-3">
                        <h3 className="font-semibold text-lg">Visualization</h3>
                        <div className="text-xs text-gray-500 mt-2 space-y-1">
                            <p>Type: <code className="bg-gray-100 px-1 rounded">{visualization.type}</code></p>
                            <p>Data Source: {visualization.dataSource}</p>
                        </div>
                    </div>

                    <div className="border-t pt-3 mt-3">
                        {visualization.type === 'graph-2d' && (
                            <GraphVisualization config={visualization.config} type={visualization.type} />
                        )}
                        {visualization.type === 'graph-3d' && (
                            <GraphVisualization config={visualization.config} type={visualization.type} />
                        )}
                        {(visualization.type === 'chart' || visualization.type === 'diagram') && (
                            <InteractiveVisualization
                                config={visualization.config}
                                type={visualization.type}
                            />
                        )}
                        {visualization.type === 'molecule' && (
                            <MoleculeVisualization config={visualization.config} />
                        )}
                        {!['graph-2d', 'graph-3d', 'chart', 'diagram', 'molecule'].includes(visualization.type) && (
                            <div className="bg-gray-100 rounded p-4 text-center text-gray-600">
                                <p>Visualization type: <code>{visualization.type}</code></p>
                                <p className="text-xs mt-2">Preview renderer for this type coming soon</p>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

export default SimulationRenderer;
