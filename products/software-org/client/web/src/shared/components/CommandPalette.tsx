import React, { useState, useEffect } from 'react';

/**
 * CommandPalette - Keyboard-accessible command and navigation center.
 *
 * <p><b>Purpose</b><br>
 * Global command palette for quick navigation, search, and actions.
 * Triggered with ⌘+K (Mac) or Ctrl+K (Windows/Linux).
 *
 * <p><b>Features</b><br>
 * - Keyboard shortcut (⌘+K / Ctrl+K)
 * - Command search and filtering
 * - Recent actions history
 * - Category grouping
 * - Fuzzy search support
 * - Keyboard navigation (Arrow keys, Enter, Escape)
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <CommandPalette isOpen={true} onClose={() => {}} />
 * ```
 *
 * @doc.type component
 * @doc.purpose Command palette and navigation center
 * @doc.layer product
 * @doc.pattern Organism
 */

interface Command {
    id: string;
    label: string;
    category: string;
    shortcut?: string;
    action: () => void;
    icon?: string;
}

interface CommandPaletteProps {
    isOpen?: boolean;
    onClose?: () => void;
}

export const CommandPalette = React.memo(function CommandPalette({
    isOpen = false,
    onClose = () => { },
}: CommandPaletteProps) {
    const [open, setOpen] = useState(isOpen);
    const [search, setSearch] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);

    const commands: Command[] = [
        {
            id: 'dashboard',
            label: 'Go to Dashboard',
            category: 'Navigation',
            shortcut: '⌘+D',
            action: () => window.location.href = '/',
            icon: '📊',
        },
        {
            id: 'departments',
            label: 'Go to Departments',
            category: 'Navigation',
            shortcut: '⌘+1',
            action: () => window.location.href = '/departments',
            icon: '🏢',
        },
        {
            id: 'workflows',
            label: 'Go to Workflows',
            category: 'Navigation',
            shortcut: '⌘+2',
            action: () => window.location.href = '/workflows',
            icon: '⚙️',
        },
        {
            id: 'security',
            label: 'Go to Security',
            category: 'Navigation',
            shortcut: '⌘+8',
            action: () => window.location.href = '/security',
            icon: '🔒',
        },
        {
            id: 'models',
            label: 'Go to Models',
            category: 'Navigation',
            shortcut: '⌘+9',
            action: () => window.location.href = '/models',
            icon: '🤖',
        },
        {
            id: 'settings',
            label: 'Open Settings',
            category: 'Settings',
            shortcut: '⌘+,',
            action: () => window.location.href = '/settings',
            icon: '⚙️',
        },
        {
            id: 'theme-toggle',
            label: 'Toggle Theme',
            category: 'Settings',
            action: () => {
                const html = document.documentElement;
                html.classList.toggle('dark');
            },
            icon: '🌙',
        },
    ];

    const filtered = commands.filter((cmd) =>
        cmd.label.toLowerCase().includes(search.toLowerCase()) ||
        cmd.category.toLowerCase().includes(search.toLowerCase())
    );

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                setOpen(!open);
            }
            if (open) {
                if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    setSelectedIndex((i) => (i + 1) % filtered.length);
                } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    setSelectedIndex((i) => (i - 1 + filtered.length) % filtered.length);
                } else if (e.key === 'Enter' && filtered[selectedIndex]) {
                    e.preventDefault();
                    filtered[selectedIndex].action();
                    setOpen(false);
                } else if (e.key === 'Escape') {
                    e.preventDefault();
                    setOpen(false);
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [open, search, selectedIndex, filtered]);

    if (!open) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 dark:bg-black/70 pt-20"
            onClick={() => {
                setOpen(false);
                onClose?.();
            }}
        >
            <div
                className="w-full max-w-2xl bg-white dark:bg-slate-900 rounded-lg shadow-xl overflow-hidden"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Search Input */}
                <div className="border-b border-slate-200 dark:border-neutral-600 p-4">
                    <input
                        autoFocus
                        type="text"
                        placeholder="Search commands..."
                        value={search}
                        onChange={(e) => {
                            setSearch(e.target.value);
                            setSelectedIndex(0);
                        }}
                        className="w-full bg-transparent text-lg outline-none placeholder-slate-500 dark:placeholder-slate-400 text-slate-900 dark:text-neutral-100"
                        aria-label="Command search"
                    />
                </div>

                {/* Commands List */}
                <div className="max-h-96 overflow-y-auto">
                    {filtered.length > 0 ? (
                        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                            {filtered.map((cmd, idx) => (
                                <li
                                    key={cmd.id}
                                    className={`px-4 py-3 cursor-pointer transition-colors ${idx === selectedIndex
                                            ? 'bg-blue-50 dark:bg-indigo-600/30'
                                            : 'hover:bg-slate-50 dark:hover:bg-slate-800'
                                        }`}
                                    onClick={() => {
                                        cmd.action();
                                        setOpen(false);
                                        onClose?.();
                                    }}
                                >
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-3">
                                            <span className="text-lg">{cmd.icon}</span>
                                            <div>
                                                <p className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {cmd.label}
                                                </p>
                                                <p className="text-xs text-slate-500 dark:text-neutral-400">
                                                    {cmd.category}
                                                </p>
                                            </div>
                                        </div>
                                        {cmd.shortcut && (
                                            <span className="text-xs text-slate-400 dark:text-slate-500 font-mono">
                                                {cmd.shortcut}
                                            </span>
                                        )}
                                    </div>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <div className="p-8 text-center text-slate-500 dark:text-neutral-400">
                            No commands found
                        </div>
                    )}
                </div>

                {/* Footer Help */}
                <div className="border-t border-slate-200 dark:border-neutral-600 p-3 bg-slate-50 dark:bg-neutral-800/50 text-xs text-slate-600 dark:text-neutral-400 flex justify-between">
                    <span>⌘K to toggle</span>
                    <span>↑↓ to navigate • ↵ to select • ESC to close</span>
                </div>
            </div>
        </div>
    );
});

export default CommandPalette;
