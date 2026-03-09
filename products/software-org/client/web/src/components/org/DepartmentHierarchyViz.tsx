import React, { useMemo } from 'react';

export interface Department {
    id: string;
    name: string;
    parentDepartmentId?: string | null;
    headcount?: number | null;
    budget?: number | null;
}

interface RestructureChange {
    type: 'merge' | 'split' | 'reorganize' | 'rename';
    departmentId: string;
    targetDepartmentId?: string;
    newName?: string;
    newParentId?: string;
}

interface DepartmentNode extends Department {
    children?: DepartmentNode[];
    level: number;
    impacted?: boolean;
}

interface DepartmentHierarchyVizProps {
    departments: Department[];
    proposedChanges?: RestructureChange[];
    editable?: boolean;
    onChangeProposed?: (change: RestructureChange) => void;
    showComparison?: boolean;
}

export const DepartmentHierarchyViz: React.FC<DepartmentHierarchyVizProps> = ({
    departments,
    proposedChanges = [],
    editable = false,
    onChangeProposed,
    showComparison = false,
}) => {
    // Build tree structure
    const departmentTree = useMemo(() => {
        const deptMap = new Map<string, DepartmentNode>();

        // First pass: create all nodes
        departments.forEach(dept => {
            deptMap.set(dept.id, {
                ...dept,
                children: [],
                level: 0,
                impacted: proposedChanges.some(
                    c => c.departmentId === dept.id || c.targetDepartmentId === dept.id
                ),
            });
        });

        // Second pass: build tree
        const roots: DepartmentNode[] = [];
        deptMap.forEach(node => {
            if (node.parentDepartmentId) {
                const parent = deptMap.get(node.parentDepartmentId);
                if (parent) {
                    parent.children = parent.children || [];
                    parent.children.push(node);
                    node.level = parent.level + 1;
                }
            } else {
                roots.push(node);
            }
        });

        return roots;
    }, [departments, proposedChanges]);

    // Calculate proposed tree after changes
    const proposedTree = useMemo(() => {
        if (!showComparison || proposedChanges.length === 0) return null;

        // Deep clone departments
        const clonedDepts = departments.map(d => ({ ...d }));

        // Apply changes
        proposedChanges.forEach(change => {
            const dept = clonedDepts.find(d => d.id === change.departmentId);
            if (!dept) return;

            switch (change.type) {
                case 'rename':
                    if (change.newName) dept.name = change.newName;
                    break;
                case 'reorganize':
                    if (change.newParentId) dept.parentDepartmentId = change.newParentId;
                    break;
                case 'merge':
                    // Mark as deleted (in real impl, would handle differently)
                    break;
                case 'split':
                    // Would create new departments
                    break;
            }
        });

        return buildTree(clonedDepts);
    }, [departments, proposedChanges, showComparison]);

    const buildTree = (depts: Department[]): DepartmentNode[] => {
        const deptMap = new Map<string, DepartmentNode>();

        depts.forEach(dept => {
            deptMap.set(dept.id, {
                ...dept,
                children: [],
                level: 0,
                impacted: false,
            });
        });

        const roots: DepartmentNode[] = [];
        deptMap.forEach(node => {
            if (node.parentDepartmentId) {
                const parent = deptMap.get(node.parentDepartmentId);
                if (parent) {
                    parent.children = parent.children || [];
                    parent.children.push(node);
                    node.level = parent.level + 1;
                }
            } else {
                roots.push(node);
            }
        });

        return roots;
    };

    const renderNode = (node: DepartmentNode, isProposed = false) => {
        const hasChildren = node.children && node.children.length > 0;

        return (
            <div
                key={node.id}
                className="relative"
                style={{ marginLeft: node.level > 0 ? '2rem' : 0 }}
            >
                {/* Department card */}
                <div
                    className={`
            group relative rounded-lg border-2 p-4 mb-3 transition-all
            ${node.impacted && !isProposed ? 'border-yellow-400 bg-yellow-50 dark:bg-yellow-900/20' : 'border-gray-200 dark:border-gray-700'}
            ${editable ? 'cursor-move hover:shadow-lg' : ''}
            ${isProposed ? 'bg-blue-50 dark:bg-blue-900/20 border-blue-400' : 'bg-white dark:bg-gray-800'}
          `}
                    draggable={editable}
                    onDragStart={(e) => {
                        if (editable && onChangeProposed) {
                            e.dataTransfer.setData('departmentId', node.id);
                        }
                    }}
                    onDragOver={(e) => {
                        if (editable) {
                            e.preventDefault();
                            e.currentTarget.classList.add('ring-2', 'ring-blue-500');
                        }
                    }}
                    onDragLeave={(e) => {
                        e.currentTarget.classList.remove('ring-2', 'ring-blue-500');
                    }}
                    onDrop={(e) => {
                        e.preventDefault();
                        e.currentTarget.classList.remove('ring-2', 'ring-blue-500');

                        if (editable && onChangeProposed) {
                            const draggedDeptId = e.dataTransfer.getData('departmentId');
                            if (draggedDeptId !== node.id) {
                                onChangeProposed({
                                    type: 'reorganize',
                                    departmentId: draggedDeptId,
                                    newParentId: node.id,
                                });
                            }
                        }
                    }}
                >
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            {/* Icon based on department type */}
                            <div className="text-2xl">
                                {node.name.includes('Engineering') && '⚙️'}
                                {node.name.includes('Product') && '📦'}
                                {node.name.includes('Design') && '🎨'}
                                {node.name.includes('Marketing') && '📢'}
                                {node.name.includes('Sales') && '💰'}
                                {node.name.includes('HR') && '👥'}
                                {!node.name.match(/(Engineering|Product|Design|Marketing|Sales|HR)/) && '🏢'}
                            </div>

                            <div>
                                <h3 className="font-semibold text-gray-900 dark:text-gray-100">
                                    {node.name}
                                </h3>
                                <div className="flex items-center gap-4 mt-1 text-sm text-gray-600 dark:text-gray-400">
                                    <span>👤 {node.headcount || 0} people</span>
                                    {node.budget && (
                                        <span>💵 ${(node.budget / 1000000).toFixed(1)}M</span>
                                    )}
                                </div>
                            </div>
                        </div>

                        {node.impacted && !isProposed && (
                            <div className="flex items-center gap-2">
                                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800 dark:bg-yellow-900/50 dark:text-yellow-200">
                                    ⚠️ Impacted
                                </span>
                            </div>
                        )}
                    </div>

                    {/* Edit actions (shown on hover if editable) */}
                    {editable && (
                        <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity flex gap-2">
                            <button
                                type="button"
                                className="p-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600"
                                onClick={() => onChangeProposed?.({
                                    type: 'rename',
                                    departmentId: node.id,
                                    newName: prompt('New name:', node.name) || node.name,
                                })}
                            >
                                ✏️
                            </button>
                        </div>
                    )}
                </div>

                {/* Connector line to children */}
                {hasChildren && (
                    <div className="relative ml-8">
                        {node.children?.map(child => renderNode(child, isProposed))}
                    </div>
                )}
            </div>
        );
    };

    if (showComparison && proposedTree) {
        return (
            <div className="grid grid-cols-2 gap-6">
                {/* Before */}
                <div>
                    <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-gray-100">
                        Current Structure
                    </h3>
                    <div className="space-y-2">
                        {departmentTree.map(root => renderNode(root, false))}
                    </div>
                </div>

                {/* After */}
                <div>
                    <h3 className="text-lg font-semibold mb-4 text-blue-900 dark:text-blue-100">
                        Proposed Structure
                    </h3>
                    <div className="space-y-2">
                        {proposedTree.map(root => renderNode(root, true))}
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-2">
            {departmentTree.map(root => renderNode(root))}
        </div>
    );
};
