import { useMemo } from 'react';
import type { Item, ItemFilter, ItemStatus, Priority } from '@/features/devsecops/types';

interface SearchBarProps {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    resultsCount?: number;
}

export function SearchBar({
    value,
    onChange,
    placeholder = 'Search...',
    resultsCount,
}: SearchBarProps) {
    return (
        <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <div className="flex items-center gap-3">
                <span className="text-slate-400">🔎</span>
                <input
                    value={value}
                    onChange={(event) => onChange(event.target.value)}
                    placeholder={placeholder}
                    className="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-500 dark:text-neutral-100 dark:placeholder:text-neutral-500"
                />
                {typeof resultsCount === 'number' ? (
                    <span className="text-xs text-slate-500 dark:text-neutral-400">{resultsCount}</span>
                ) : null}
            </div>
        </div>
    );
}

interface FilterPanelProps {
    filters: ItemFilter;
    onChange: (next: ItemFilter) => void;
    open: boolean;
    onClose: () => void;
    availableTags: string[];
    variant?: 'drawer' | 'inline';
}

const statusOptions: ItemStatus[] = ['not-started', 'in-progress', 'blocked', 'in-review', 'completed', 'archived'];
const priorityOptions: Priority[] = ['low', 'medium', 'high', 'critical'];

function toggleStringValue(values: string[] | undefined, value: string) {
    const current = values ?? [];
    return current.includes(value)
        ? current.filter((entry) => entry !== value)
        : [...current, value];
}

export function FilterPanel({
    filters,
    onChange,
    open,
    onClose,
    availableTags,
}: FilterPanelProps) {
    if (!open) {
        return null;
    }

    return (
        <div className="fixed inset-0 z-40 flex justify-end bg-slate-950/30 backdrop-blur-sm">
            <div className="h-full w-full max-w-md overflow-y-auto border-l border-slate-200 bg-white p-6 shadow-2xl dark:border-slate-800 dark:bg-slate-950">
                <div className="mb-6 flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">Filters</h3>
                        <p className="text-sm text-slate-500 dark:text-neutral-400">Narrow the current stage view.</p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-md px-3 py-1 text-sm text-slate-600 hover:bg-slate-100 dark:text-neutral-300 dark:hover:bg-slate-800"
                    >
                        Close
                    </button>
                </div>

                <div className="space-y-6">
                    <section className="space-y-3">
                        <h4 className="text-sm font-medium text-slate-900 dark:text-neutral-100">Status</h4>
                        <div className="flex flex-wrap gap-2">
                            {statusOptions.map((status) => {
                                const active = filters.status?.includes(status) ?? false;
                                return (
                                    <button
                                        key={status}
                                        type="button"
                                        onClick={() => onChange({ ...filters, status: toggleStringValue(filters.status, status) as ItemStatus[] })}
                                        className={`rounded-full px-3 py-1 text-xs font-medium ${active
                                            ? 'bg-blue-600 text-white'
                                            : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-neutral-300'
                                        }`}
                                    >
                                        {status}
                                    </button>
                                );
                            })}
                        </div>
                    </section>

                    <section className="space-y-3">
                        <h4 className="text-sm font-medium text-slate-900 dark:text-neutral-100">Priority</h4>
                        <div className="flex flex-wrap gap-2">
                            {priorityOptions.map((priority) => {
                                const active = filters.priority?.includes(priority) ?? false;
                                return (
                                    <button
                                        key={priority}
                                        type="button"
                                        onClick={() => onChange({ ...filters, priority: toggleStringValue(filters.priority, priority) as Priority[] })}
                                        className={`rounded-full px-3 py-1 text-xs font-medium ${active
                                            ? 'bg-emerald-600 text-white'
                                            : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-neutral-300'
                                        }`}
                                    >
                                        {priority}
                                    </button>
                                );
                            })}
                        </div>
                    </section>

                    <section className="space-y-3">
                        <h4 className="text-sm font-medium text-slate-900 dark:text-neutral-100">Tags</h4>
                        <div className="flex flex-wrap gap-2">
                            {availableTags.length === 0 ? (
                                <p className="text-sm text-slate-500 dark:text-neutral-400">No tags available.</p>
                            ) : (
                                availableTags.map((tag) => {
                                    const active = filters.tags?.includes(tag) ?? false;
                                    return (
                                        <button
                                            key={tag}
                                            type="button"
                                            onClick={() => onChange({ ...filters, tags: toggleStringValue(filters.tags, tag) })}
                                            className={`rounded-full px-3 py-1 text-xs font-medium ${active
                                                ? 'bg-violet-600 text-white'
                                                : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-neutral-300'
                                            }`}
                                        >
                                            {tag}
                                        </button>
                                    );
                                })
                            )}
                        </div>
                    </section>

                    <button
                        type="button"
                        onClick={() => onChange({ phaseIds: [], status: [], priority: [], tags: [] })}
                        className="w-full rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-neutral-200 dark:hover:bg-slate-900"
                    >
                        Reset filters
                    </button>
                </div>
            </div>
        </div>
    );
}

interface KanbanBoardProps {
    items: Item[];
    onItemClick?: (item: Item) => void;
    onItemMove?: (data: { itemId: string; status: ItemStatus }) => void;
}

export function KanbanBoard({ items, onItemClick, onItemMove }: KanbanBoardProps) {
    const grouped = useMemo(() => {
        return statusOptions.map((status) => ({
            status,
            items: items.filter((item) => item.status === status),
        }));
    }, [items]);

    return (
        <div className="grid gap-4 lg:grid-cols-3 xl:grid-cols-6">
            {grouped.map((column) => (
                <section key={column.status} className="rounded-xl border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/60">
                    <div className="mb-3 flex items-center justify-between">
                        <h3 className="text-sm font-semibold capitalize text-slate-900 dark:text-neutral-100">{column.status}</h3>
                        <span className="rounded-full bg-white px-2 py-0.5 text-xs text-slate-600 shadow-sm dark:bg-slate-950 dark:text-neutral-300">
                            {column.items.length}
                        </span>
                    </div>
                    <div className="space-y-3">
                        {column.items.map((item) => (
                            <button
                                key={item.id}
                                type="button"
                                onClick={() => onItemClick?.(item)}
                                className="block w-full rounded-lg border border-slate-200 bg-white p-3 text-left shadow-sm transition hover:border-blue-300 hover:shadow dark:border-slate-800 dark:bg-slate-950"
                            >
                                <div className="mb-2 flex items-center justify-between gap-2">
                                    <span className="text-xs uppercase tracking-wide text-slate-500 dark:text-neutral-400">{item.priority}</span>
                                    {onItemMove ? (
                                        <select
                                            value={item.status}
                                            onChange={(event) => onItemMove({ itemId: item.id, status: event.target.value as ItemStatus })}
                                            className="rounded border border-slate-200 bg-white px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-900"
                                            onClick={(event) => event.stopPropagation()}
                                        >
                                            {statusOptions.map((status) => (
                                                <option key={status} value={status}>{status}</option>
                                            ))}
                                        </select>
                                    ) : null}
                                </div>
                                <h4 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">{item.title}</h4>
                                {item.description ? (
                                    <p className="mt-1 text-xs text-slate-600 dark:text-neutral-400">{item.description}</p>
                                ) : null}
                                <div className="mt-3 flex flex-wrap gap-1">
                                    {item.tags.map((tag) => (
                                        <span key={tag} className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600 dark:bg-slate-800 dark:text-neutral-300">
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            </button>
                        ))}
                    </div>
                </section>
            ))}
        </div>
    );
}

interface DataTableColumn {
    id: string;
    label: string;
    field: keyof Item | string;
    sortable?: boolean;
    format?: (value: unknown, row: Item) => unknown;
}

interface DataTableProps {
    data: Item[];
    columns: DataTableColumn[];
    onRowClick?: (item: Item) => void;
    showPagination?: boolean;
    paginationConfig?: {
        page: number;
        rowsPerPage: number;
        totalRows: number;
    };
}

export function DataTable({
    data,
    columns,
    onRowClick,
    showPagination,
    paginationConfig,
}: DataTableProps) {
    const pagedData = useMemo(() => {
        if (!showPagination || !paginationConfig) {
            return data;
        }
        const start = paginationConfig.page * paginationConfig.rowsPerPage;
        return data.slice(start, start + paginationConfig.rowsPerPage);
    }, [data, showPagination, paginationConfig]);

    return (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950">
            <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
                <thead className="bg-slate-50 dark:bg-slate-900">
                    <tr>
                        {columns.map((column) => (
                            <th key={column.id} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-neutral-400">
                                {column.label}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-900">
                    {pagedData.map((row) => (
                        <tr
                            key={row.id}
                            onClick={() => onRowClick?.(row)}
                            className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/70"
                        >
                            {columns.map((column) => {
                                const rawValue = row[column.field as keyof Item];
                                const value = column.format ? column.format(rawValue, row) : rawValue;
                                return (
                                    <td key={column.id} className="px-4 py-3 text-sm text-slate-700 dark:text-neutral-300">
                                        {typeof value === 'string' || typeof value === 'number'
                                            ? value
                                            : value == null
                                                ? ''
                                                : String(value)}
                                    </td>
                                );
                            })}
                        </tr>
                    ))}
                </tbody>
            </table>
            {showPagination && paginationConfig ? (
                <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-xs text-slate-500 dark:border-slate-800 dark:text-neutral-400">
                    <span>
                        Showing {pagedData.length} of {paginationConfig.totalRows}
                    </span>
                    <span>
                        Page {paginationConfig.page + 1}
                    </span>
                </div>
            ) : null}
        </div>
    );
}

interface TimelineProps {
    items: Item[];
    viewMode?: 'day' | 'week' | 'month';
    height?: number;
    showToday?: boolean;
}

export function Timeline({ items, height = 300, showToday = true }: TimelineProps) {
    const sortedItems = useMemo(() => {
        return [...items].sort((left, right) => {
            return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
        });
    }, [items]);

    return (
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-950" style={{ minHeight: height }}>
            {showToday ? (
                <div className="mb-4 text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-neutral-400">
                    Today marker active
                </div>
            ) : null}
            <div className="space-y-4">
                {sortedItems.map((item) => (
                    <div key={item.id} className="flex gap-3">
                        <div className="flex flex-col items-center">
                            <span className="mt-1 h-2.5 w-2.5 rounded-full bg-blue-500" />
                            <span className="h-full w-px bg-slate-200 dark:bg-slate-800" />
                        </div>
                        <div className="pb-4">
                            <div className="flex items-center gap-2">
                                <h4 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">{item.title}</h4>
                                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] uppercase tracking-wide text-slate-600 dark:bg-slate-800 dark:text-neutral-300">
                                    {item.status}
                                </span>
                            </div>
                            <p className="mt-1 text-xs text-slate-500 dark:text-neutral-400">
                                {new Date(item.updatedAt).toLocaleString()}
                            </p>
                            {item.description ? (
                                <p className="mt-2 text-sm text-slate-600 dark:text-neutral-400">{item.description}</p>
                            ) : null}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
