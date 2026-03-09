import React, { useState, useMemo } from 'react';
import { RoleInheritanceTree } from '../RoleInheritanceTree';
import { RoleDefinition } from '@/types/role';

/**
 * Permission Explorer Demo
 * 
 * Interactive demo showcasing permission search and highlighting
 */
export const PermissionExplorerDemo: React.FC = () => {
    const [searchQuery, setSearchQuery] = useState('');
    const [permissionType, setPermissionType] = useState<'all' | 'read' | 'write' | 'admin'>('all');
    const [highlightPermission, setHighlightPermission] = useState<string>();
    const [matchingRoles, setMatchingRoles] = useState<RoleDefinition[]>([]);

    const personaId = 'permission-explorer-demo';

    // Debounced search
    React.useEffect(() => {
        const timer = setTimeout(() => {
            if (searchQuery.trim()) {
                setHighlightPermission(searchQuery.trim());
            } else {
                setHighlightPermission(undefined);
            }
        }, 300);

        return () => clearTimeout(timer);
    }, [searchQuery]);

    const permissionStats = useMemo(() => {
        // Mock stats - would come from actual role data
        return {
            total: 42,
            read: 15,
            write: 12,
            admin: 8,
            other: 7,
        };
    }, []);

    const handleSearch = (query: string) => {
        setSearchQuery(query);
    };

    const handlePermissionTypeChange = (type: 'all' | 'read' | 'write' | 'admin') => {
        setPermissionType(type);
        if (type !== 'all') {
            setSearchQuery(type);
        } else {
            setSearchQuery('');
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 p-8">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                        Permission Explorer
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400">
                        Search and explore permissions across role hierarchy
                    </p>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
                    {/* Sidebar */}
                    <div className="space-y-6">
                        {/* Search */}
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-lg font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                🔍 Search Permissions
                            </h2>
                            <input
                                type="text"
                                placeholder="e.g., write:code"
                                value={searchQuery}
                                onChange={(e) => handleSearch(e.target.value)}
                                className="w-full px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg
                                         bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100
                                         focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                            />
                            {searchQuery && (
                                <p className="mt-2 text-sm text-slate-500 dark:text-neutral-400">
                                    Highlighting: <strong>{searchQuery}</strong>
                                </p>
                            )}
                        </div>

                        {/* Permission Type Filter */}
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-lg font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                📁 Filter by Type
                            </h2>
                            <div className="space-y-2">
                                {[
                                    { id: 'all', label: 'All Permissions', count: permissionStats.total },
                                    { id: 'read', label: 'Read Only', count: permissionStats.read },
                                    { id: 'write', label: 'Write Access', count: permissionStats.write },
                                    { id: 'admin', label: 'Admin Rights', count: permissionStats.admin },
                                ].map((type) => (
                                    <button
                                        key={type.id}
                                        onClick={() => handlePermissionTypeChange(type.id as any)}
                                        className={`w-full text-left px-4 py-2 rounded-lg transition-colors ${permissionType === type.id
                                                ? 'bg-blue-500 text-white'
                                                : 'bg-slate-100 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-600'
                                            }`}
                                    >
                                        <div className="flex justify-between items-center">
                                            <span>{type.label}</span>
                                            <span className="text-sm opacity-75">{type.count}</span>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Stats */}
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-lg font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                📊 Statistics
                            </h2>
                            <div className="space-y-3">
                                <div className="flex justify-between">
                                    <span className="text-slate-600 dark:text-neutral-400">Total Roles</span>
                                    <span className="font-semibold text-slate-900 dark:text-neutral-100">8</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-slate-600 dark:text-neutral-400">Matching Roles</span>
                                    <span className="font-semibold text-blue-600 dark:text-indigo-400">
                                        {matchingRoles.length}
                                    </span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-slate-600 dark:text-neutral-400">Unique Permissions</span>
                                    <span className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {permissionStats.total}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Tree Visualization */}
                    <div className="lg:col-span-3">
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <div className="flex justify-between items-center mb-4">
                                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">
                                    Role Hierarchy
                                </h2>
                                {highlightPermission && (
                                    <div className="flex items-center space-x-2">
                                        <span className="text-sm text-slate-500 dark:text-neutral-400">
                                            Highlighted roles have permission:
                                        </span>
                                        <span className="px-3 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-full text-sm font-medium">
                                            {highlightPermission}
                                        </span>
                                    </div>
                                )}
                            </div>
                            <div className="h-[700px] border border-slate-200 dark:border-neutral-600 rounded-lg overflow-hidden">
                                <RoleInheritanceTree
                                    personaId={personaId}
                                    highlightPermission={highlightPermission}
                                    interactive={true}
                                    onNodeClick={(role) => {
                                        console.log('Selected role:', role);
                                    }}
                                />
                            </div>
                        </div>

                        {/* Permission Inheritance Explanation */}
                        {highlightPermission && (
                            <div className="mt-6 bg-blue-50 dark:bg-indigo-600/30 rounded-lg p-6">
                                <h3 className="text-lg font-semibold text-blue-900 dark:text-blue-100 mb-3">
                                    💡 Permission Inheritance
                                </h3>
                                <p className="text-blue-800 dark:text-blue-200 mb-3">
                                    Roles highlighted in blue have the "{highlightPermission}" permission either:
                                </p>
                                <ul className="space-y-2 text-blue-800 dark:text-blue-200">
                                    <li className="flex items-start">
                                        <span className="mr-2">•</span>
                                        <span><strong>Directly</strong> - Permission is explicitly assigned to the role</span>
                                    </li>
                                    <li className="flex items-start">
                                        <span className="mr-2">•</span>
                                        <span><strong>Inherited</strong> - Permission comes from a parent role</span>
                                    </li>
                                    <li className="flex items-start">
                                        <span className="mr-2">•</span>
                                        <span><strong>Wildcard</strong> - Role has "*" (all permissions)</span>
                                    </li>
                                </ul>
                            </div>
                        )}
                    </div>
                </div>

                {/* Quick Actions */}
                <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
                    <button
                        onClick={() => handleSearch('read')}
                        className="p-4 bg-white dark:bg-neutral-800 rounded-lg shadow hover:shadow-lg transition-shadow"
                    >
                        <div className="text-2xl mb-2">👁️</div>
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-1">View Read Permissions</h3>
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            See all roles with read access
                        </p>
                    </button>
                    <button
                        onClick={() => handleSearch('write')}
                        className="p-4 bg-white dark:bg-neutral-800 rounded-lg shadow hover:shadow-lg transition-shadow"
                    >
                        <div className="text-2xl mb-2">✏️</div>
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-1">View Write Permissions</h3>
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            See all roles with write access
                        </p>
                    </button>
                    <button
                        onClick={() => handleSearch('admin')}
                        className="p-4 bg-white dark:bg-neutral-800 rounded-lg shadow hover:shadow-lg transition-shadow"
                    >
                        <div className="text-2xl mb-2">🔐</div>
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-1">View Admin Rights</h3>
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            See all roles with admin privileges
                        </p>
                    </button>
                </div>
            </div>
        </div>
    );
};

export default PermissionExplorerDemo;
