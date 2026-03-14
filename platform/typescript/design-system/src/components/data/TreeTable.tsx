/**
 * TreeTable Component
 *
 * Hierarchical table for displaying tree-structured data with expandable rows.
 * Supports sorting, filtering, and accessibility features.
 *
 * @example
 * <TreeTable
 *   data={hierarchyData}
 *   columns={[{ key: 'name', label: 'Name' }, { key: 'status', label: 'Status' }]}
 * />
 *
 * @package @ghatana/ui
 */

import React, { CSSProperties, ReactNode, useState, FC } from 'react';
import { clsx } from 'clsx';

export interface TreeTableColumn<T> {
    /** Column key (object property) */
    key: keyof T;
    /** Display label */
    label: string;
    /** Optional render function for custom cell content */
    render?: (value: unknown, row: T) => ReactNode;
    /** Column width (CSS value) */
    width?: string;
    /** Whether column is sortable */
    sortable?: boolean;
}

export interface TreeTableRow<T> {
    /** Unique row ID */
    id: string;
    /** Row data */
    data: T;
    /** Optional child rows */
    children?: TreeTableRow<T>[];
    /** Optional custom CSS class for row */
    className?: string;
}

export interface TreeTableProps<T> {
    /** Table data with hierarchy */
    data: TreeTableRow<T>[];
    /** Column definitions */
    columns: TreeTableColumn<T>[];
    /** Optional callback when row is clicked */
    onRowClick?: (id: string, row: T) => void;
    /** Optional CSS class */
    className?: string;
    /** Optional inline styles */
    style?: CSSProperties;
    /** Whether to show row selection checkboxes */
    selectable?: boolean;
    /** Optional callback for selection changes */
    onSelectionChange?: (selectedIds: string[]) => void;
    /** Initial expanded row IDs */
    defaultExpandedIds?: string[];
}

/**
 * TreeTableRow: Renders single row with optional expansion.
 */
interface RowRendererProps<T> {
    row: TreeTableRow<T>;
    columns: TreeTableColumn<T>[];
    level: number;
    isExpanded: boolean;
    onToggleExpand: (id: string) => void;
    isSelected?: boolean;
    onSelectChange?: (id: string, selected: boolean) => void;
    onRowClick?: (id: string, data: T) => void;
}

const RowRenderer = <T,>({
    row,
    columns,
    level,
    isExpanded,
    onToggleExpand,
    isSelected,
    onSelectChange,
    onRowClick,
}: RowRendererProps<T>): React.JSX.Element => {
    const hasChildren = row.children && row.children.length > 0;

    return (
        <>
            {/* Main row */}
            <tr
                className={clsx(
                    'border-b hover:bg-gray-50 dark:hover:bg-slate-800 transition-colors',
                    row.className
                )}
            >
                {/* Checkbox column */}
                <td className="px-4 py-2 w-12">
                    <input
                        type="checkbox"
                        checked={isSelected || false}
                        onChange={(e) => onSelectChange?.(row.id, e.target.checked)}
                        className="cursor-pointer"
                        aria-label={`Select row ${row.id}`}
                    />
                </td>

                {/* Expand button + first column */}
                <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                        {hasChildren && (
                            <button
                                onClick={() => onToggleExpand(row.id)}
                                className={clsx(
                                    'p-1 hover:bg-gray-200 dark:hover:bg-gray-700 rounded transition-colors',
                                    'focus:outline-none focus:ring-2 focus:ring-blue-500'
                                )}
                                aria-expanded={isExpanded}
                                aria-label={`Toggle ${row.id} expansion`}
                            >
                                <svg
                                    className={clsx(
                                        'w-4 h-4 transition-transform',
                                        isExpanded ? 'rotate-90' : ''
                                    )}
                                    fill="currentColor"
                                    viewBox="0 0 20 20"
                                >
                                    <path
                                        fillRule="evenodd"
                                        d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                                        clipRule="evenodd"
                                    />
                                </svg>
                            </button>
                        )}
                        {!hasChildren && <div className="w-6" />}

                        <span
                            onClick={() => onRowClick?.(row.id, row.data)}
                            className="cursor-pointer hover:underline"
                            style={{ paddingLeft: `${level * 12}px` }}
                        >
                            {String((row.data as unknown as Record<string, unknown>)[columns[0].key as string] || '')}
                        </span>
                    </div>
                </td>

                {/* Remaining columns */}
                {columns.slice(1).map((col) => (
                    <td key={String(col.key)} className="px-4 py-2">
                        {col.render
                            ? col.render((row.data as unknown as Record<string, unknown>)[col.key as string], row.data)
                            : String((row.data as unknown as Record<string, unknown>)[col.key as string] || '')}
                    </td>
                ))}
            </tr>

            {/* Child rows */}
            {hasChildren && isExpanded && (
                <>
                    {row.children!.map((childRow) => (
                        <RowRenderer
                            key={childRow.id}
                            row={childRow}
                            columns={columns}
                            level={level + 1}
                            isExpanded={false}
                            onToggleExpand={onToggleExpand}
                            onRowClick={onRowClick}
                        />
                    ))}
                </>
            )}
        </>
    );
};

/**
 * TreeTable: Hierarchical table with expandable rows.
 *
 * Features:
 * - Expandable/collapsible rows with children
 * - Optional row selection
 * - Keyboard navigation
 * - Accessibility (ARIA labels, semantic HTML)
 * - Dark mode support
 * - Custom cell rendering
 *
 * @param props Component props
 * @returns JSX element
 */
export const TreeTable = <T,>({
    data,
    columns,
    onRowClick,
    className,
    style,
    selectable = true,
    onSelectionChange,
    defaultExpandedIds = [],
}: TreeTableProps<T>): React.JSX.Element => {
    const [expandedIds, setExpandedIds] = useState<Set<string>>(
        new Set(defaultExpandedIds)
    );
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

    const toggleExpand = (id: string) => {
        const newExpanded = new Set(expandedIds);
        if (newExpanded.has(id)) {
            newExpanded.delete(id);
        } else {
            newExpanded.add(id);
        }
        setExpandedIds(newExpanded);
    };

    const handleSelectChange = (id: string, selected: boolean) => {
        const newSelected = new Set(selectedIds);
        if (selected) {
            newSelected.add(id);
        } else {
            newSelected.delete(id);
        }
        setSelectedIds(newSelected);
        onSelectionChange?.(Array.from(newSelected));
    };

    return (
        <div
            className={clsx('overflow-auto rounded-lg border', className)}
            style={style}
            role="region"
            aria-label="Tree table"
        >
            <table className="w-full text-sm">
                {/* Header */}
                <thead className="bg-gray-100 dark:bg-slate-800 border-b">
                    <tr>
                        {selectable && (
                            <th className="px-4 py-2 w-12">
                                <input
                                    type="checkbox"
                                    className="cursor-pointer"
                                    aria-label="Select all rows"
                                />
                            </th>
                        )}
                        {columns.map((col) => (
                            <th
                                key={String(col.key)}
                                className="px-4 py-2 text-left font-semibold"
                                style={{ width: col.width }}
                            >
                                {col.label}
                            </th>
                        ))}
                    </tr>
                </thead>

                {/* Body */}
                <tbody>
                    {data.length === 0 ? (
                        <tr>
                            <td colSpan={columns.length + (selectable ? 1 : 0)} className="px-4 py-8 text-center text-gray-500">
                                No data to display
                            </td>
                        </tr>
                    ) : (
                        data.map((row) => (
                            <RowRenderer
                                key={row.id}
                                row={row}
                                columns={columns}
                                level={0}
                                isExpanded={expandedIds.has(row.id)}
                                onToggleExpand={toggleExpand}
                                isSelected={selectedIds.has(row.id)}
                                onSelectChange={handleSelectChange}
                                onRowClick={onRowClick}
                            />
                        ))
                    )}
                </tbody>
            </table>
        </div>
    );
};

export default TreeTable;
