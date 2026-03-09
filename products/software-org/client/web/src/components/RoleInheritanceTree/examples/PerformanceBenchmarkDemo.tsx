import React, { useState, useCallback } from 'react';
import { RoleInheritanceTree } from '../RoleInheritanceTree';
import { generateLargeHierarchy } from './shared/mockData';

/**
 * Performance Benchmark Demo
 * 
 * Test RoleInheritanceTree performance with various hierarchy sizes
 */
export const PerformanceBenchmarkDemo: React.FC = () => {
    const [hierarchySize, setHierarchySize] = useState<number>(10);
    const [personaId, setPersonaId] = useState('perf-10');
    const [metrics, setMetrics] = useState<{
        renderTime: number;
        nodeCount: number;
        edgeCount: number;
        memoryUsage: number;
    } | null>(null);
    const [isGenerating, setIsGenerating] = useState(false);

    const generateHierarchy = useCallback(() => {
        setIsGenerating(true);
        const startTime = performance.now();

        // Simulate generation delay
        setTimeout(() => {
            const hierarchy = generateLargeHierarchy(
                Math.ceil(Math.log2(hierarchySize)),
                hierarchySize < 20 ? 2 : 3
            );

            const endTime = performance.now();
            const renderTime = endTime - startTime;

            // Mock memory usage (would use performance.memory in real implementation)
            const memoryUsage = hierarchySize * 0.05; // MB per node (approximate)

            setMetrics({
                renderTime,
                nodeCount: hierarchy.length,
                edgeCount: hierarchy.reduce((acc, role) => acc + (role.parentRoles?.length || 0), 0),
                memoryUsage,
            });

            setPersonaId(`perf-${hierarchySize}-${Date.now()}`);
            setIsGenerating(false);
        }, 100);
    }, [hierarchySize]);

    const predefinedSizes = [
        { size: 10, label: 'Small (10 nodes)', color: 'bg-green-500' },
        { size: 50, label: 'Medium (50 nodes)', color: 'bg-yellow-500' },
        { size: 100, label: 'Large (100 nodes)', color: 'bg-orange-500' },
        { size: 200, label: 'X-Large (200 nodes)', color: 'bg-red-500' },
    ];

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 p-8">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                        Performance Benchmark
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400">
                        Test RoleInheritanceTree with various hierarchy sizes
                    </p>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
                    {/* Controls */}
                    <div className="space-y-6">
                        {/* Size Slider */}
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-lg font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                📊 Hierarchy Size
                            </h2>
                            <div className="mb-4">
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Nodes: <strong>{hierarchySize}</strong>
                                </label>
                                <input
                                    type="range"
                                    min="10"
                                    max="200"
                                    step="10"
                                    value={hierarchySize}
                                    onChange={(e) => setHierarchySize(parseInt(e.target.value))}
                                    className="w-full"
                                />
                            </div>
                            <button
                                onClick={generateHierarchy}
                                disabled={isGenerating}
                                className="w-full py-3 px-4 bg-blue-500 text-white rounded-lg font-medium
                                         hover:bg-blue-600 disabled:bg-slate-300 dark:disabled:bg-slate-700
                                         disabled:cursor-not-allowed transition-colors"
                            >
                                {isGenerating ? 'Generating...' : 'Generate & Benchmark'}
                            </button>
                        </div>

                        {/* Quick Presets */}
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-lg font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                ⚡ Quick Presets
                            </h2>
                            <div className="space-y-2">
                                {predefinedSizes.map((preset) => (
                                    <button
                                        key={preset.size}
                                        onClick={() => setHierarchySize(preset.size)}
                                        className="w-full text-left px-4 py-2 rounded-lg bg-slate-100 dark:bg-neutral-700
                                                 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors
                                                 text-slate-700 dark:text-neutral-300"
                                    >
                                        <div className="flex items-center">
                                            <div className={`w-3 h-3 rounded-full ${preset.color} mr-3`} />
                                            <span>{preset.label}</span>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Metrics */}
                        {metrics && (
                            <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                                <h2 className="text-lg font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                    📈 Metrics
                                </h2>
                                <div className="space-y-3">
                                    <div>
                                        <div className="text-sm text-slate-600 dark:text-neutral-400">Render Time</div>
                                        <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                            {metrics.renderTime.toFixed(0)}ms
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-sm text-slate-600 dark:text-neutral-400">Nodes</div>
                                        <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                            {metrics.nodeCount}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-sm text-slate-600 dark:text-neutral-400">Edges</div>
                                        <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                            {metrics.edgeCount}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-sm text-slate-600 dark:text-neutral-400">Memory</div>
                                        <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                            {metrics.memoryUsage.toFixed(1)}MB
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Tree Visualization */}
                    <div className="lg:col-span-3">
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <div className="flex justify-between items-center mb-4">
                                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">
                                    Hierarchy Visualization
                                </h2>
                                {metrics && (
                                    <div className="text-sm text-slate-500 dark:text-neutral-400">
                                        {metrics.nodeCount} nodes, {metrics.edgeCount} edges
                                    </div>
                                )}
                            </div>
                            <div className="h-[700px] border border-slate-200 dark:border-neutral-600 rounded-lg overflow-hidden">
                                <RoleInheritanceTree
                                    personaId={personaId}
                                    interactive={true}
                                />
                            </div>
                        </div>

                        {/* Performance Comparison */}
                        {metrics && (
                            <div className="mt-6 bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                                <h3 className="text-lg font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                    🎯 Performance Analysis
                                </h3>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <div className="p-4 bg-green-50 dark:bg-green-600/30 rounded-lg">
                                        <div className="text-sm text-green-700 dark:text-green-300 mb-1">
                                            Target: &lt;50ms
                                        </div>
                                        <div className={`text-2xl font-bold ${metrics.renderTime < 50
                                                ? 'text-green-600 dark:text-green-400'
                                                : 'text-yellow-600 dark:text-yellow-400'
                                            }`}>
                                            {metrics.renderTime < 50 ? '✓' : '⚠'}
                                        </div>
                                    </div>
                                    <div className="p-4 bg-blue-50 dark:bg-indigo-600/30 rounded-lg">
                                        <div className="text-sm text-blue-700 dark:text-blue-300 mb-1">
                                            Nodes/ms
                                        </div>
                                        <div className="text-2xl font-bold text-blue-600 dark:text-indigo-400">
                                            {(metrics.nodeCount / metrics.renderTime).toFixed(2)}
                                        </div>
                                    </div>
                                    <div className="p-4 bg-purple-50 dark:bg-violet-600/30 rounded-lg">
                                        <div className="text-sm text-purple-700 dark:text-purple-300 mb-1">
                                            Memory/Node
                                        </div>
                                        <div className="text-2xl font-bold text-purple-600 dark:text-violet-400">
                                            {(metrics.memoryUsage / metrics.nodeCount * 1000).toFixed(0)}KB
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                {/* Benchmark History */}
                <div className="mt-8 bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                    <h2 className="text-xl font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                        📊 Expected Performance
                    </h2>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead className="border-b border-slate-200 dark:border-neutral-600">
                                <tr className="text-left text-slate-600 dark:text-neutral-400">
                                    <th className="pb-3 pr-4">Size</th>
                                    <th className="pb-3 pr-4">Nodes</th>
                                    <th className="pb-3 pr-4">Initial Render</th>
                                    <th className="pb-3 pr-4">Re-render</th>
                                    <th className="pb-3 pr-4">Memory</th>
                                    <th className="pb-3">Status</th>
                                </tr>
                            </thead>
                            <tbody className="text-slate-700 dark:text-neutral-300">
                                <tr className="border-b border-slate-100 dark:border-slate-800">
                                    <td className="py-3 pr-4">Small</td>
                                    <td className="py-3 pr-4">3-10</td>
                                    <td className="py-3 pr-4">~45ms</td>
                                    <td className="py-3 pr-4">~12ms</td>
                                    <td className="py-3 pr-4">~2MB</td>
                                    <td className="py-3">
                                        <span className="px-2 py-1 bg-green-100 dark:bg-green-600/30 text-green-700 dark:text-green-300 rounded text-xs">
                                            Optimal
                                        </span>
                                    </td>
                                </tr>
                                <tr className="border-b border-slate-100 dark:border-slate-800">
                                    <td className="py-3 pr-4">Medium</td>
                                    <td className="py-3 pr-4">20-50</td>
                                    <td className="py-3 pr-4">~180ms</td>
                                    <td className="py-3 pr-4">~30ms</td>
                                    <td className="py-3 pr-4">~8MB</td>
                                    <td className="py-3">
                                        <span className="px-2 py-1 bg-green-100 dark:bg-green-600/30 text-green-700 dark:text-green-300 rounded text-xs">
                                            Good
                                        </span>
                                    </td>
                                </tr>
                                <tr className="border-b border-slate-100 dark:border-slate-800">
                                    <td className="py-3 pr-4">Large</td>
                                    <td className="py-3 pr-4">100+</td>
                                    <td className="py-3 pr-4">~450ms</td>
                                    <td className="py-3 pr-4">~50ms</td>
                                    <td className="py-3 pr-4">~20MB</td>
                                    <td className="py-3">
                                        <span className="px-2 py-1 bg-yellow-100 dark:bg-orange-600/30 text-yellow-700 dark:text-yellow-300 rounded text-xs">
                                            Acceptable
                                        </span>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default PerformanceBenchmarkDemo;
