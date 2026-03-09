import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useSearchState } from "../../hooks/useSearch";

interface SearchBarProps {
    placeholder?: string;
    className?: string;
}

/**
 * Global search bar with autocomplete suggestions.
 */
export function SearchBar({
    placeholder = "Search modules, discussions...",
    className = ""
}: SearchBarProps) {
    const navigate = useNavigate();
    const [isOpen, setIsOpen] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    const {
        query,
        setQuery,
        suggestions,
        isLoadingSuggestions
    } = useSearchState();

    // Close dropdown when clicking outside
    useEffect(() => {
        function handleClickOutside(event: MouseEvent) {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        }

        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setQuery(e.target.value);
        setIsOpen(true);
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (query.trim()) {
            setIsOpen(false);
            navigate(`/search?q=${encodeURIComponent(query.trim())}`);
        }
    };

    const handleSuggestionClick = (suggestion: { text: string; type: string; id?: string }) => {
        setIsOpen(false);
        if (suggestion.type === "module" && suggestion.id) {
            navigate(`/modules/${suggestion.id}`);
        } else {
            setQuery(suggestion.text);
            navigate(`/search?q=${encodeURIComponent(suggestion.text)}`);
        }
    };

    return (
        <div ref={containerRef} className={`relative ${className}`}>
            <form onSubmit={handleSubmit}>
                <div className="relative">
                    <input
                        ref={inputRef}
                        type="text"
                        value={query}
                        onChange={handleInputChange}
                        onFocus={() => setIsOpen(true)}
                        placeholder={placeholder}
                        className="w-full pl-10 pr-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                    />
                    <svg
                        className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >
                        <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                        />
                    </svg>
                </div>
            </form>

            {/* Suggestions Dropdown */}
            {isOpen && query.length >= 2 && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-white border rounded-lg shadow-lg z-50 max-h-64 overflow-y-auto">
                    {isLoadingSuggestions ? (
                        <div className="p-4 text-center text-gray-500 dark:text-gray-300">
                            Searching...
                        </div>
                    ) : suggestions.length === 0 ? (
                        <div className="p-4 text-center text-gray-500 dark:text-gray-300">
                            No results found
                        </div>
                    ) : (
                        <ul>
                            {suggestions.map((suggestion, index) => (
                                <li key={`${suggestion.type}-${suggestion.text}-${index}`}>
                                    <button
                                        onClick={() => handleSuggestionClick(suggestion)}
                                        className="w-full text-left px-4 py-2 hover:bg-gray-100 flex items-center gap-3"
                                    >
                                        <SuggestionIcon type={suggestion.type} />
                                        <span className="flex-1">{suggestion.text}</span>
                                        <span className="text-xs text-gray-400 capitalize">
                                            {suggestion.type}
                                        </span>
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}

function SuggestionIcon({ type }: { type: string }) {
    switch (type) {
        case "module":
            return <span className="text-lg">📚</span>;
        case "category":
            return <span className="text-lg">📁</span>;
        case "tag":
            return <span className="text-lg">🏷️</span>;
        case "author":
            return <span className="text-lg">👤</span>;
        default:
            return <span className="text-lg">🔍</span>;
    }
}

/**
 * Full search page component.
 */
export function SearchPage() {
    const navigate = useNavigate();
    const searchParams = new URLSearchParams(window.location.search);
    const initialQuery = searchParams.get("q") ?? "";

    const [query] = useState(initialQuery);
    const { results, isLoading, filters, setFilters } = useSearchState();

    // Update URL when query changes
    useEffect(() => {
        if (query !== initialQuery) {
            navigate(`/search?q=${encodeURIComponent(query)}`, { replace: true });
        }
    }, [query, initialQuery, navigate]);

    return (
        <div className="max-w-5xl mx-auto p-6">
            <div className="mb-8">
                <SearchBar
                    className="max-w-2xl"
                    placeholder="Search for modules, topics..."
                />
            </div>

            <div className="flex gap-8">
                {/* Filters Sidebar */}
                <div className="w-48 shrink-0">
                    <h3 className="font-semibold mb-3">Filter by</h3>

                    <div className="space-y-4">
                        <FilterSection
                            title="Type"
                            options={[
                                { value: "module", label: "Modules" },
                                { value: "thread", label: "Discussions" },
                                { value: "learning_path", label: "Paths" }
                            ]}
                            selected={filters.type?.split(",") ?? []}
                            onChange={(values) => setFilters({ ...filters, type: values.join(",") })}
                        />

                        <FilterSection
                            title="Price"
                            options={[
                                { value: "free", label: "Free only" }
                            ]}
                            selected={filters.free ? ["free"] : []}
                            onChange={(values) => setFilters({ ...filters, free: values.includes("free") ? "true" : "" })}
                        />
                    </div>
                </div>

                {/* Results */}
                <div className="flex-1">
                    {isLoading ? (
                        <div className="space-y-4">
                            {[1, 2, 3].map((i) => (
                                <div key={i} className="h-24 bg-gray-100 rounded animate-pulse" />
                            ))}
                        </div>
                    ) : results?.results.length === 0 ? (
                        <div className="text-center py-12 text-gray-500 dark:text-gray-300">
                            No results found for "{query}"
                        </div>
                    ) : (
                        <>
                            <p className="text-sm text-gray-600 mb-4">
                                {results?.total ?? 0} results found
                            </p>
                            <div className="space-y-4">
                                {results?.results.map((result) => (
                                    <SearchResultCard
                                        key={result.id}
                                        result={result}
                                        onClick={() => {
                                            if (result.type === "module") {
                                                navigate(`/modules/${result.id}`);
                                            }
                                        }}
                                    />
                                ))}
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}

interface FilterSectionProps {
    title: string;
    options: Array<{ value: string; label: string }>;
    selected: string[];
    onChange: (values: string[]) => void;
}

function FilterSection({ title, options, selected, onChange }: FilterSectionProps) {
    const toggleOption = (value: string) => {
        if (selected.includes(value)) {
            onChange(selected.filter((v) => v !== value));
        } else {
            onChange([...selected, value]);
        }
    };

    return (
        <div>
            <h4 className="text-sm font-medium text-gray-700 mb-2">{title}</h4>
            <div className="space-y-1">
                {options.map((option) => (
                    <label key={option.value} className="flex items-center gap-2">
                        <input
                            type="checkbox"
                            checked={selected.includes(option.value)}
                            onChange={() => toggleOption(option.value)}
                            className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                        />
                        <span className="text-sm">{option.label}</span>
                    </label>
                ))}
            </div>
        </div>
    );
}

interface SearchResultCardProps {
    result: {
        id: string;
        type: string;
        title: string;
        description: string;
        score: number;
    };
    onClick: () => void;
}

function SearchResultCard({ result, onClick }: SearchResultCardProps) {
    return (
        <div
            onClick={onClick}
            className="p-4 border rounded-lg cursor-pointer hover:border-blue-300 hover:bg-blue-50 transition-colors"
        >
            <div className="flex items-start gap-3">
                <span className="text-2xl">
                    {result.type === "module" ? "📚" : result.type === "thread" ? "💬" : "🛤️"}
                </span>
                <div className="flex-1">
                    <h3 className="font-medium text-gray-900">{result.title}</h3>
                    <p className="text-sm text-gray-600 mt-1 line-clamp-2">
                        {result.description}
                    </p>
                    <span className="inline-block mt-2 text-xs text-gray-400 capitalize">
                        {result.type}
                    </span>
                </div>
            </div>
        </div>
    );
}
