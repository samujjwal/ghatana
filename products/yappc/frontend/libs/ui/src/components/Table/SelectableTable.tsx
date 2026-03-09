/**
 * Selectable Table Component
 * 
 * Advanced table with multi-item selection, keyboard navigation, sorting,
 * and accessibility features. Integrates with the selection management hook
 * for comprehensive bulk operations support.
 */

import React, { useCallback } from 'react';

import { useSelection } from '../../hooks/useSelection';

import type { SelectionItem } from '../../hooks/useSelection';

/**
 *
 */
export interface TableColumn<T> {
    id: string;
    label: string;
    accessor: keyof T | ((item: T) => React.ReactNode);
    width?: string | number;
    sortable?: boolean;
    align?: 'left' | 'center' | 'right';
    headerAlign?: 'left' | 'center' | 'right';
    render?: (value: unknown, item: T, index: number) => React.ReactNode;
}

/**
 *
 */
export interface SelectableTableProps<T extends SelectionItem> {
    data: T[];
    columns: TableColumn<T>[];
    selectedIds?: string[];
    onSelectionChange?: (selectedIds: string[], selectedItems: T[]) => void;
    onRowClick?: (item: T, index: number, event: React.MouseEvent) => void;
    onSort?: (columnId: string, direction: 'asc' | 'desc') => void;
    sortBy?: string;
    sortDirection?: 'asc' | 'desc';
    className?: string;
    rowClassName?: string | ((item: T, index: number) => string);
    enableKeyboardNavigation?: boolean;
    enableSelection?: boolean;
    maxSelections?: number;
    emptyMessage?: string;
    loading?: boolean;
    stickyHeader?: boolean;
    dense?: boolean;
}

/**
 *
 */
export function SelectableTable<T extends SelectionItem>({
    data,
    columns,
    selectedIds,
    onSelectionChange,
    onRowClick,
    onSort,
    sortBy,
    sortDirection = 'asc',
    className,
    rowClassName,
    enableKeyboardNavigation = true,
    enableSelection = true,
    maxSelections,
    emptyMessage = 'No data available',
    loading = false,
    stickyHeader = false,
    dense = false
}: SelectableTableProps<T>) {

    // Selection management
    const selection = useSelection({
        items: data,
        selectedIds,
        onSelectionChange,
        maxSelections,
        enableKeyboardShortcuts: enableKeyboardNavigation
    });

    // Handle row click with selection
    const handleRowClick = useCallback((item: T, index: number, event: React.MouseEvent) => {
        if (enableSelection) {
            if (event.shiftKey && selection.selectedCount > 0) {
                // Range selection
                const lastSelectedIndex = data.findIndex(d => d.id === selection.selectedIds[selection.selectedIds.length - 1]);
                if (lastSelectedIndex !== -1) {
                    const start = Math.min(lastSelectedIndex, index);
                    const end = Math.max(lastSelectedIndex, index);
                    const rangeIds = data.slice(start, end + 1).map(d => d.id);
                    selection.selectMultiple(rangeIds);
                }
            } else if (event.ctrlKey || event.metaKey) {
                // Toggle selection
                selection.toggleItem(item.id);
            } else if (!selection.isSelected(item.id)) {
                // Single selection
                selection.selectItem(item.id);
            }
        }

        onRowClick?.(item, index, event);
    }, [enableSelection, selection, data, onRowClick]);

    // Handle sort
    const handleSort = useCallback((columnId: string) => {
        if (!onSort) return;

        const newDirection = sortBy === columnId && sortDirection === 'asc' ? 'desc' : 'asc';
        onSort(columnId, newDirection);
    }, [onSort, sortBy, sortDirection]);

    // Generate row class name
    const getRowClassName = useCallback((item: T, index: number) => {
        let classes = 'selectable-table-row';

        if (selection.isSelected(item.id)) {
            classes += ' selected';
        }

        if (typeof rowClassName === 'string') {
            classes += ` ${rowClassName}`;
        } else if (typeof rowClassName === 'function') {
            classes += ` ${rowClassName(item, index)}`;
        }

        return classes;
    }, [selection, rowClassName]);

    // Render cell content
    const renderCellContent = useCallback((column: TableColumn<T>, item: T, index: number) => {
        if (column.render) {
            const value = typeof column.accessor === 'function'
                ? column.accessor(item)
                : item[column.accessor];
            return column.render(value, item, index);
        }

        if (typeof column.accessor === 'function') {
            return column.accessor(item);
        }

        return item[column.accessor] as React.ReactNode;
    }, []);

    // Table styles
    const tableStyle: React.CSSProperties = {
        width: '100%',
        borderCollapse: 'collapse',
        backgroundColor: 'white',
        border: '1px solid var(--color-border, #e0e0e0)'
    };

    const headerStyle: React.CSSProperties = {
        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
        borderBottom: '2px solid var(--color-border, #e0e0e0)',
        position: stickyHeader ? 'sticky' : 'static',
        top: 0,
        zIndex: 10
    };

    const cellPadding = dense ? '0.5rem' : '0.75rem';

    const headerCellStyle = (column: TableColumn<T>): React.CSSProperties => ({
        padding: cellPadding,
        textAlign: column.headerAlign || column.align || 'left',
        fontWeight: '600',
        fontSize: '0.875rem',
        color: 'var(--color-text-primary, #333)',
        cursor: column.sortable ? 'pointer' : 'default',
        userSelect: 'none',
        width: column.width
    });

    const cellStyle = (column: TableColumn<T>): React.CSSProperties => ({
        padding: cellPadding,
        textAlign: column.align || 'left',
        borderBottom: '1px solid var(--color-border-light, #f0f0f0)',
        fontSize: '0.875rem',
        color: 'var(--color-text-primary, #333)'
    });

    const rowStyle = (item: T): React.CSSProperties => ({
        cursor: 'pointer',
        backgroundColor: selection.isSelected(item.id)
            ? 'var(--color-primary-light, #e3f2fd)'
            : 'transparent',
        transition: 'background-color 0.15s ease'
    });

    const getSortIcon = useCallback((columnId: string) => {
        if (sortBy !== columnId) return '⇅';
        return sortDirection === 'asc' ? '↑' : '↓';
    }, [sortBy, sortDirection]);

    // Loading state
    if (loading) {
        return (
            <div style={{
                padding: '2rem',
                textAlign: 'center',
                backgroundColor: 'white',
                border: '1px solid var(--color-border, #e0e0e0)',
                borderRadius: '8px'
            }}>
                <div style={{ marginBottom: '1rem', fontSize: '1.5rem' }}>⏳</div>
                <div>Loading data...</div>
            </div>
        );
    }

    // Empty state
    if (data.length === 0) {
        return (
            <div style={{
                padding: '3rem',
                textAlign: 'center',
                backgroundColor: 'white',
                border: '1px solid var(--color-border, #e0e0e0)',
                borderRadius: '8px',
                color: 'var(--color-text-secondary, #666)'
            }}>
                <div style={{ marginBottom: '1rem', fontSize: '2rem', opacity: 0.5 }}>📋</div>
                <div>{emptyMessage}</div>
            </div>
        );
    }

    return (
        <div
            className={className}
            style={{
                borderRadius: '8px',
                overflow: 'hidden',
                border: '1px solid var(--color-border, #e0e0e0)'
            }}
        >
            <table style={tableStyle}>
                <thead style={headerStyle}>
                    <tr>
                        {/* Selection header */}
                        {enableSelection && (
                            <th style={{
                                ...headerCellStyle({ id: 'selection', label: '', align: 'center' } as TableColumn<T>),
                                width: '50px'
                            }}>
                                <label style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    cursor: 'pointer'
                                }}>
                                    <input
                                        type="checkbox"
                                        checked={selection.isAllSelected}
                                        ref={(input) => {
                                            if (input) input.indeterminate = selection.isPartiallySelected;
                                        }}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                selection.selectAll();
                                            } else {
                                                selection.deselectAll();
                                            }
                                        }}
                                        style={{ cursor: 'pointer' }}
                                    />
                                </label>
                            </th>
                        )}

                        {/* Column headers */}
                        {columns.map((column) => (
                            <th
                                key={column.id}
                                style={headerCellStyle(column)}
                                onClick={() => column.sortable && handleSort(column.id)}
                            >
                                <div style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '0.5rem',
                                    justifyContent: column.headerAlign || column.align || 'left'
                                }}>
                                    <span>{column.label}</span>
                                    {column.sortable && (
                                        <span style={{
                                            fontSize: '0.75rem',
                                            opacity: sortBy === column.id ? 1 : 0.5
                                        }}>
                                            {getSortIcon(column.id)}
                                        </span>
                                    )}
                                </div>
                            </th>
                        ))}
                    </tr>
                </thead>

                <tbody>
                    {data.map((item, index) => (
                        <tr
                            key={item.id}
                            className={getRowClassName(item, index)}
                            style={rowStyle(item)}
                            onClick={(e) => handleRowClick(item, index, e)}
                            onMouseEnter={(e) => {
                                if (!selection.isSelected(item.id)) {
                                    e.currentTarget.style.backgroundColor = 'var(--color-background-hover, #f5f5f5)';
                                }
                            }}
                            onMouseLeave={(e) => {
                                if (!selection.isSelected(item.id)) {
                                    e.currentTarget.style.backgroundColor = 'transparent';
                                }
                            }}
                            onKeyDown={(e) => {
                                if (enableKeyboardNavigation) {
                                    selection.handleKeyDown(e, item.id);
                                }
                            }}
                            tabIndex={enableKeyboardNavigation ? 0 : undefined}
                        >
                            {/* Selection cell */}
                            {enableSelection && (
                                <td style={{
                                    ...cellStyle({ id: 'selection', label: '', align: 'center' } as TableColumn<T>),
                                    width: '50px'
                                }}>
                                    <label style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        cursor: 'pointer'
                                    }}>
                                        <input
                                            type="checkbox"
                                            checked={selection.isSelected(item.id)}
                                            onChange={(e) => {
                                                e.stopPropagation();
                                                selection.toggleItem(item.id);
                                            }}
                                            style={{ cursor: 'pointer' }}
                                        />
                                    </label>
                                </td>
                            )}

                            {/* Data cells */}
                            {columns.map((column) => (
                                <td
                                    key={column.id}
                                    style={cellStyle(column)}
                                >
                                    {renderCellContent(column, item, index)}
                                </td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>

            {/* Selection summary */}
            {enableSelection && selection.selectedCount > 0 && (
                <div style={{
                    padding: '0.75rem 1rem',
                    backgroundColor: 'var(--color-primary-light, #e3f2fd)',
                    borderTop: '1px solid var(--color-border, #e0e0e0)',
                    fontSize: '0.875rem',
                    color: 'var(--color-primary-dark, #1565c0)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between'
                }}>
                    <span>
                        {selection.selectedCount} of {data.length} items selected
                        {maxSelections && maxSelections < Infinity && (
                            <span> (max {maxSelections})</span>
                        )}
                    </span>
                    <button
                        onClick={() => selection.deselectAll()}
                        style={{
                            background: 'none',
                            border: 'none',
                            color: 'var(--color-primary-dark, #1565c0)',
                            cursor: 'pointer',
                            fontSize: '0.875rem',
                            textDecoration: 'underline'
                        }}
                    >
                        Clear selection
                    </button>
                </div>
            )}
        </div>
    );
}