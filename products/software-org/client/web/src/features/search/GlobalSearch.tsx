import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router';
import { Search, X, Zap, Package, Users, Activity, AlertTriangle, Clock, FileText } from 'lucide-react';
import { Badge } from '@/components/ui';

/**
 * Global Search Component
 *
 * <p><b>Purpose</b><br>
 * Unified search across all modules with keyboard shortcuts (Cmd/Ctrl+K).
 * Provides quick navigation to resources across the platform.
 *
 * <p><b>Features</b><br>
 * - Search workflows, agents, services, incidents, metrics, reports
 * - Keyboard navigation (↑/↓ to navigate, Enter to select, Esc to close)
 * - Recent searches
 * - Category filtering
 *
 * @doc.type component
 * @doc.purpose Global search and navigation
 * @doc.layer product
 * @doc.pattern Search
 */

interface SearchResult {
    id: string;
    type: 'workflow' | 'agent' | 'service' | 'incident' | 'metric' | 'report' | 'queue-item';
    title: string;
    description: string;
    category: string;
    route: string;
}

export function GlobalSearch() {
    const [isOpen, setIsOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);
    const navigate = useNavigate();
    const inputRef = useRef<HTMLInputElement>(null);

    // Mock search results - in production, this would call API
    const mockResults: SearchResult[] = [
        {
            id: 'wf-001',
            type: 'workflow',
            title: 'Payment Processing',
            description: 'Process credit card payments with fraud detection',
            category: 'Build',
            route: '/build/workflows/wf-001',
        },
        {
            id: 'ag-001',
            type: 'agent',
            title: 'Payment Validator',
            description: 'Validates payment requests and detects anomalies',
            category: 'Build',
            route: '/build/agents/ag-001',
        },
        {
            id: 'svc-001',
            type: 'service',
            title: 'payment-gateway',
            description: 'External payment gateway integration',
            category: 'Admin',
            route: '/admin/services/svc-001',
        },
        {
            id: 'inc-001',
            type: 'incident',
            title: 'Payment Gateway Timeout',
            description: 'High latency in payment processing',
            category: 'Operate',
            route: '/operate/incident-detail/inc-001',
        },
        {
            id: 'met-001',
            type: 'metric',
            title: 'Payment Success Rate',
            description: 'Percentage of successful payment transactions',
            category: 'Observe',
            route: '/observe/metric-detail/met-001',
        },
        {
            id: 'rep-001',
            type: 'report',
            title: 'Monthly Reliability Report',
            description: 'System reliability and incident summary',
            category: 'Observe',
            route: '/observe/report-detail/rep-001',
        },
    ];

    const filteredResults = query.trim()
        ? mockResults.filter(
              (result) =>
                  result.title.toLowerCase().includes(query.toLowerCase()) ||
                  result.description.toLowerCase().includes(query.toLowerCase())
          )
        : [];

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Cmd+K or Ctrl+K to open search
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                setIsOpen(true);
            }
            // Escape to close
            if (e.key === 'Escape') {
                setIsOpen(false);
                setQuery('');
                setSelectedIndex(0);
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, []);

    useEffect(() => {
        if (isOpen && inputRef.current) {
            inputRef.current.focus();
        }
    }, [isOpen]);

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setSelectedIndex((prev) => Math.min(prev + 1, filteredResults.length - 1));
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setSelectedIndex((prev) => Math.max(prev - 1, 0));
        } else if (e.key === 'Enter' && filteredResults[selectedIndex]) {
            e.preventDefault();
            navigate(filteredResults[selectedIndex].route);
            setIsOpen(false);
            setQuery('');
            setSelectedIndex(0);
        }
    };

    const handleSelect = (result: SearchResult) => {
        navigate(result.route);
        setIsOpen(false);
        setQuery('');
        setSelectedIndex(0);
    };

    const getIcon = (type: SearchResult['type']) => {
        switch (type) {
            case 'workflow':
                return Zap;
            case 'agent':
                return Users;
            case 'service':
                return Package;
            case 'incident':
                return AlertTriangle;
            case 'metric':
                return Activity;
            case 'report':
                return FileText;
            case 'queue-item':
                return Clock;
            default:
                return Search;
        }
    };

    const getCategoryColor = (category: string): 'primary' | 'success' | 'warning' | 'danger' | 'neutral' => {
        switch (category) {
            case 'Admin':
                return 'primary';
            case 'Build':
                return 'success';
            case 'Observe':
                return 'warning';
            case 'Operate':
                return 'danger';
            default:
                return 'neutral';
        }
    };

    if (!isOpen) {
        return (
            <button
                onClick={() => setIsOpen(true)}
                className="flex items-center gap-2 px-3 py-2 bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
            >
                <Search className="h-4 w-4 text-slate-600 dark:text-neutral-400" />
                <span className="text-sm text-slate-600 dark:text-neutral-400">Search...</span>
                <kbd className="hidden sm:inline-flex items-center gap-1 px-1.5 py-0.5 text-xs font-mono bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-600 rounded">
                    ⌘K
                </kbd>
            </button>
        );
    }

    return (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-start justify-center pt-20">
            <div
                className="bg-white dark:bg-slate-900 rounded-lg shadow-xl border border-slate-200 dark:border-slate-800 w-full max-w-2xl mx-4"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Search Input */}
                <div className="flex items-center gap-3 p-4 border-b border-slate-200 dark:border-slate-800">
                    <Search className="h-5 w-5 text-slate-400" />
                    <input
                        ref={inputRef}
                        type="text"
                        value={query}
                        onChange={(e) => {
                            setQuery(e.target.value);
                            setSelectedIndex(0);
                        }}
                        onKeyDown={handleKeyDown}
                        placeholder="Search workflows, agents, services, incidents..."
                        className="flex-1 bg-transparent border-none outline-none text-slate-900 dark:text-neutral-100 placeholder-slate-400 dark:placeholder-neutral-500"
                    />
                    <button
                        onClick={() => {
                            setIsOpen(false);
                            setQuery('');
                            setSelectedIndex(0);
                        }}
                        className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-colors"
                    >
                        <X className="h-4 w-4 text-slate-400" />
                    </button>
                </div>

                {/* Results */}
                <div className="max-h-96 overflow-y-auto">
                    {query.trim() === '' ? (
                        <div className="p-8 text-center">
                            <Search className="h-12 w-12 text-slate-300 dark:text-slate-700 mx-auto mb-3" />
                            <p className="text-slate-600 dark:text-neutral-400 text-sm">
                                Start typing to search across workflows, agents, services, and more
                            </p>
                            <div className="mt-4 flex items-center justify-center gap-2 text-xs text-slate-500 dark:text-neutral-500">
                                <kbd className="px-2 py-1 bg-slate-100 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded">
                                    ↑↓
                                </kbd>
                                <span>to navigate</span>
                                <kbd className="px-2 py-1 bg-slate-100 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded">
                                    ↵
                                </kbd>
                                <span>to select</span>
                                <kbd className="px-2 py-1 bg-slate-100 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded">
                                    esc
                                </kbd>
                                <span>to close</span>
                            </div>
                        </div>
                    ) : filteredResults.length === 0 ? (
                        <div className="p-8 text-center">
                            <Search className="h-12 w-12 text-slate-300 dark:text-slate-700 mx-auto mb-3" />
                            <p className="text-slate-600 dark:text-neutral-400 text-sm">
                                No results found for "{query}"
                            </p>
                        </div>
                    ) : (
                        <div className="py-2">
                            {filteredResults.map((result, index) => {
                                const Icon = getIcon(result.type);
                                return (
                                    <button
                                        key={result.id}
                                        onClick={() => handleSelect(result)}
                                        className={`w-full flex items-start gap-3 px-4 py-3 text-left transition-colors ${
                                            index === selectedIndex
                                                ? 'bg-slate-100 dark:bg-slate-800'
                                                : 'hover:bg-slate-50 dark:hover:bg-slate-800/50'
                                        }`}
                                    >
                                        <div
                                            className={`p-2 rounded-lg ${
                                                index === selectedIndex
                                                    ? 'bg-blue-100 dark:bg-blue-900/30'
                                                    : 'bg-slate-100 dark:bg-slate-800'
                                            }`}
                                        >
                                            <Icon
                                                className={`h-5 w-5 ${
                                                    index === selectedIndex
                                                        ? 'text-blue-600 dark:text-blue-400'
                                                        : 'text-slate-600 dark:text-neutral-400'
                                                }`}
                                            />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2 mb-1">
                                                <span className="font-medium text-slate-900 dark:text-neutral-100 truncate">
                                                    {result.title}
                                                </span>
                                                <Badge variant={getCategoryColor(result.category)}>
                                                    {result.category}
                                                </Badge>
                                            </div>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400 truncate">
                                                {result.description}
                                            </p>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
