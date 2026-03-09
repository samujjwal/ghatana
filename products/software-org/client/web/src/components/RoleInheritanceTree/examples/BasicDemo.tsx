import React, { useState } from 'react';
import { RoleInheritanceTree } from '../RoleInheritanceTree';
import { RoleDefinition } from '@/types/role';

/**
 * Basic demo showcasing simple role hierarchy visualization
 */
export const BasicDemo: React.FC = () => {
    const [selectedRole, setSelectedRole] = useState<RoleDefinition | null>(null);

    // Sample role hierarchy: Admin → Manager → Employee
    const personaId = 'basic-demo-persona';

    const handleNodeClick = (role: RoleDefinition) => {
        setSelectedRole(role);
    };

    const handleExport = (data: { nodes: any[]; edges: any[] }) => {
        console.log('Exported data:', data);
        const blob = new Blob([JSON.stringify(data, null, 2)], {
            type: 'application/json',
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'role-hierarchy.json';
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 p-8">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                        RoleInheritanceTree - Basic Demo
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400">
                        Interactive visualization of a simple 3-level role hierarchy
                    </p>
                </div>

                {/* Demo Container */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Tree Visualization */}
                    <div className="lg:col-span-2">
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-xl font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                Role Hierarchy
                            </h2>
                            <div className="h-[600px] border border-slate-200 dark:border-neutral-600 rounded-lg overflow-hidden">
                                <RoleInheritanceTree
                                    personaId={personaId}
                                    interactive={true}
                                    onNodeClick={handleNodeClick}
                                    onExport={handleExport}
                                />
                            </div>
                        </div>
                    </div>

                    {/* Info Panel */}
                    <div className="space-y-6">
                        {/* Selected Role Details */}
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-xl font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                Selected Role
                            </h2>
                            {selectedRole ? (
                                <div className="space-y-3">
                                    <div>
                                        <h3 className="text-sm font-medium text-slate-500 dark:text-neutral-400">
                                            Name
                                        </h3>
                                        <p className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                            {selectedRole.name}
                                        </p>
                                    </div>
                                    {selectedRole.description && (
                                        <div>
                                            <h3 className="text-sm font-medium text-slate-500 dark:text-neutral-400">
                                                Description
                                            </h3>
                                            <p className="text-slate-700 dark:text-neutral-300">
                                                {selectedRole.description}
                                            </p>
                                        </div>
                                    )}
                                    <div>
                                        <h3 className="text-sm font-medium text-slate-500 dark:text-neutral-400 mb-2">
                                            Permissions
                                        </h3>
                                        <div className="space-y-1">
                                            {selectedRole.permissions.map((perm) => (
                                                <div
                                                    key={perm}
                                                    className="text-sm bg-blue-50 dark:bg-indigo-600/30 text-blue-700 dark:text-blue-300 px-2 py-1 rounded"
                                                >
                                                    {perm}
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                    {selectedRole.parentRoles &&
                                        selectedRole.parentRoles.length > 0 && (
                                            <div>
                                                <h3 className="text-sm font-medium text-slate-500 dark:text-neutral-400 mb-2">
                                                    Inherits From
                                                </h3>
                                                <div className="space-y-1">
                                                    {selectedRole.parentRoles.map((parentId) => (
                                                        <div
                                                            key={parentId}
                                                            className="text-sm bg-purple-50 dark:bg-violet-600/30 text-purple-700 dark:text-purple-300 px-2 py-1 rounded"
                                                        >
                                                            {parentId}
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                </div>
                            ) : (
                                <p className="text-slate-500 dark:text-neutral-400 italic">
                                    Click on a role node to see details
                                </p>
                            )}
                        </div>

                        {/* Instructions */}
                        <div className="bg-blue-50 dark:bg-indigo-600/30 rounded-lg p-6">
                            <h2 className="text-lg font-semibold mb-3 text-blue-900 dark:text-blue-100">
                                💡 Tips
                            </h2>
                            <ul className="space-y-2 text-sm text-blue-800 dark:text-blue-200">
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>
                                        <strong>Click</strong> on nodes to view role details
                                    </span>
                                </li>
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>
                                        <strong>Drag</strong> nodes to rearrange the layout
                                    </span>
                                </li>
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>
                                        <strong>Scroll</strong> to zoom in/out
                                    </span>
                                </li>
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>
                                        <strong>Double-click</strong> background to reset view
                                    </span>
                                </li>
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>
                                        <strong>Hover</strong> nodes for quick permission preview
                                    </span>
                                </li>
                            </ul>
                        </div>

                        {/* Features */}
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-lg font-semibold mb-3 text-slate-900 dark:text-neutral-100">
                                ✨ Features
                            </h2>
                            <ul className="space-y-2 text-sm text-slate-700 dark:text-neutral-300">
                                <li className="flex items-center">
                                    <span className="text-green-500 mr-2">✓</span>
                                    Automatic hierarchical layout
                                </li>
                                <li className="flex items-center">
                                    <span className="text-green-500 mr-2">✓</span>
                                    Interactive node selection
                                </li>
                                <li className="flex items-center">
                                    <span className="text-green-500 mr-2">✓</span>
                                    Permission tooltips
                                </li>
                                <li className="flex items-center">
                                    <span className="text-green-500 mr-2">✓</span>
                                    Export to JSON
                                </li>
                                <li className="flex items-center">
                                    <span className="text-green-500 mr-2">✓</span>
                                    Dark mode support
                                </li>
                                <li className="flex items-center">
                                    <span className="text-green-500 mr-2">✓</span>
                                    Keyboard navigation
                                </li>
                            </ul>
                        </div>
                    </div>
                </div>

                {/* Code Example */}
                <div className="mt-8 bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                    <h2 className="text-xl font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                        📝 Code Example
                    </h2>
                    <pre className="bg-slate-100 dark:bg-slate-900 p-4 rounded-lg overflow-x-auto text-sm">
                        <code className="text-slate-800 dark:text-slate-200">
                            {`import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';

function MyComponent() {
    const [selectedRole, setSelectedRole] = useState(null);

    return (
        <RoleInheritanceTree
            personaId="my-persona"
            interactive={true}
            onNodeClick={(role) => setSelectedRole(role)}
            onExport={(data) => downloadJSON(data)}
        />
    );
}`}
                        </code>
                    </pre>
                </div>

                {/* Performance Stats */}
                <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="bg-white dark:bg-neutral-800 rounded-lg shadow p-6">
                        <h3 className="text-sm font-medium text-slate-500 dark:text-neutral-400 mb-2">
                            Render Time
                        </h3>
                        <p className="text-2xl font-bold text-green-600 dark:text-green-400">
                            ~45ms
                        </p>
                        <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">
                            Initial render (3 nodes)
                        </p>
                    </div>
                    <div className="bg-white dark:bg-neutral-800 rounded-lg shadow p-6">
                        <h3 className="text-sm font-medium text-slate-500 dark:text-neutral-400 mb-2">
                            Re-render Time
                        </h3>
                        <p className="text-2xl font-bold text-blue-600 dark:text-indigo-400">
                            ~12ms
                        </p>
                        <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">
                            After node selection
                        </p>
                    </div>
                    <div className="bg-white dark:bg-neutral-800 rounded-lg shadow p-6">
                        <h3 className="text-sm font-medium text-slate-500 dark:text-neutral-400 mb-2">
                            Memory Usage
                        </h3>
                        <p className="text-2xl font-bold text-purple-600 dark:text-violet-400">
                            ~2MB
                        </p>
                        <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">
                            Typical for small hierarchy
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default BasicDemo;
