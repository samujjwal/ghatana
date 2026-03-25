import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type {
    HybridSearchResult,
    NextStepSuggestion,
    RelatedAssetsResponse,
} from "@tutorputor/contracts/v1/content-studio";
import { apiClient } from "../../api/tutorputorClient";
import {
    useAssetNextSteps,
    useAssetRecommendations,
    useSearchState,
    useSearchStateWithInitialQuery,
} from "../../hooks/useSearch";

interface SearchBarProps {
    placeholder?: string;
    className?: string;
}

/**
 * Global search bar with autocomplete suggestions.
 */
export function SearchBar({
    placeholder = "Search explainers, simulations, examples, and assessments...",
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
        setQuery(suggestion.text);
        navigate(`/search?q=${encodeURIComponent(suggestion.text)}`);
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
        case "simulation":
            return <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-sky-100 text-xs font-semibold text-sky-700">SIM</span>;
        case "animation":
            return <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-amber-100 text-xs font-semibold text-amber-700">ANI</span>;
        case "assessment":
            return <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-rose-100 text-xs font-semibold text-rose-700">ASM</span>;
        case "worked_example":
            return <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-emerald-100 text-xs font-semibold text-emerald-700">EX</span>;
        case "explainer":
            return <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-indigo-100 text-xs font-semibold text-indigo-700">EXP</span>;
        default:
            return <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-700">AST</span>;
    }
}

/**
 * Full search page component.
 */
export function SearchPage() {
    const navigate = useNavigate();
    const searchParams = new URLSearchParams(window.location.search);
    const initialQuery = searchParams.get("q") ?? "";
    const lastImpressionKeyRef = useRef("");
    const lastReformulationRef = useRef(initialQuery);
    const [selectedAssetId, setSelectedAssetId] = useState<string | undefined>();

    const { query, setQuery, results, isLoading, filters, setFilters } =
        useSearchStateWithInitialQuery(initialQuery);
    const { data: relatedAssets } = useAssetRecommendations(selectedAssetId, 4);
    const { data: nextSteps } = useAssetNextSteps(selectedAssetId, 4);

    useEffect(() => {
        if (initialQuery && initialQuery !== query) {
            setQuery(initialQuery);
        }
    }, [initialQuery, query, setQuery]);

    useEffect(() => {
        if (query !== initialQuery) {
            navigate(`/search?q=${encodeURIComponent(query)}`, { replace: true });
        }
    }, [query, initialQuery, navigate]);

    useEffect(() => {
        const currentResults = results?.results ?? [];
        if (!selectedAssetId || !currentResults.some((result) => result.asset.id === selectedAssetId)) {
            setSelectedAssetId(currentResults[0]?.asset.id);
        }
    }, [results, selectedAssetId]);

    useEffect(() => {
        if (!query || !results || results.results.length === 0) {
            return;
        }

        const assetIds = results.results.map((item) => item.asset.id).join(",");
        const impressionKey = `${query}:${assetIds}`;
        if (lastImpressionKeyRef.current === impressionKey) {
            return;
        }

        lastImpressionKeyRef.current = impressionKey;
        void apiClient.trackExplorerEvents({
            events: results.results.slice(0, 8).map((item, index) => ({
                eventType: "impression",
                query,
                assetId: item.asset.id,
                assetType: item.asset.assetType,
                position: index,
                score: item.ranking.score,
            })),
        });
    }, [query, results]);

    useEffect(() => {
        if (!query || !results) {
            return;
        }

        if (lastReformulationRef.current && lastReformulationRef.current !== query) {
            void apiClient.trackExplorerEvent({
                eventType: "query_reformulation",
                query,
                metadata: { previousQuery: lastReformulationRef.current },
            });
        }

        lastReformulationRef.current = query;
    }, [query, results]);

    return (
        <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
            <div className="rounded-3xl border border-slate-200 bg-[radial-gradient(circle_at_top_left,_rgba(14,116,144,0.10),_transparent_35%),radial-gradient(circle_at_bottom_right,_rgba(234,179,8,0.12),_transparent_30%),white] p-6 shadow-sm">
                <div className="max-w-3xl">
                    <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">Canonical Explorer</p>
                    <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-900">Discover governed learning assets</h1>
                    <p className="mt-2 text-sm text-slate-600">
                        Search one canonical asset index for explainers, simulations, worked examples, animations, and assessments. Related content and next-step rails stay live as you inspect results.
                    </p>
                </div>
                <div className="mt-5 max-w-3xl">
                    <SearchBar className="w-full" />
                </div>
                <div className="mt-5 flex flex-wrap gap-2">
                    <FilterChipGroup
                        title="Asset types"
                        options={[
                            { value: "explainer", label: "Explainers" },
                            { value: "worked_example", label: "Worked Examples" },
                            { value: "simulation", label: "Simulations" },
                            { value: "animation", label: "Animations" },
                            { value: "assessment", label: "Assessments" },
                        ]}
                        selected={filters.assetTypes?.split(",").filter(Boolean) ?? []}
                        onChange={(values) =>
                            setFilters({
                                ...filters,
                                assetTypes: values.join(","),
                            })
                        }
                    />
                </div>
            </div>

            <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.8fr)_minmax(320px,1fr)]">
                <section className="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="mb-4 flex items-center justify-between gap-3">
                        <div>
                            <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500">Results</h2>
                            <p className="mt-1 text-sm text-slate-600">
                                {results?.total ?? 0} canonical assets matched
                            </p>
                        </div>
                    </div>

                    {isLoading ? (
                        <div className="space-y-4">
                            {[1, 2, 3].map((i) => (
                                <div key={i} className="h-32 animate-pulse rounded-2xl bg-slate-100" />
                            ))}
                        </div>
                    ) : results?.results.length === 0 ? (
                        <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-6 py-14 text-center text-sm text-slate-500">
                            No governed assets matched "{query}".
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {results?.results.map((result, index) => (
                                <SearchResultCard
                                    key={result.asset.id}
                                    result={result}
                                    selected={result.asset.id === selectedAssetId}
                                    onClick={() => {
                                        setSelectedAssetId(result.asset.id);
                                        void apiClient.trackExplorerEvent({
                                            eventType: "click",
                                            query,
                                            assetId: result.asset.id,
                                            assetType: result.asset.assetType,
                                            position: index,
                                            score: result.ranking.score,
                                        });
                                    }}
                                />
                            ))}
                        </div>
                    )}
                </section>

                <RecommendationRailPanel
                    selectedResult={results?.results.find((item) => item.asset.id === selectedAssetId)}
                    relatedAssets={relatedAssets}
                    nextSteps={nextSteps}
                    onSelectSuggestion={(suggestion, source) => {
                        setSelectedAssetId(suggestion.asset.id);
                        void apiClient.trackExplorerEvent({
                            eventType: "next_step_select",
                            query,
                            assetId: suggestion.asset.id,
                            assetType: suggestion.asset.assetType,
                            metadata: { source },
                        });
                    }}
                />
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

function FilterChipGroup({ title, options, selected, onChange }: FilterSectionProps) {
    const toggleOption = (value: string) => {
        if (selected.includes(value)) {
            onChange(selected.filter((v) => v !== value));
        } else {
            onChange([...selected, value]);
        }
    };

    return (
        <div>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">{title}</h4>
            <div className="flex flex-wrap gap-2">
                {options.map((option) => (
                    <button
                        key={option.value}
                        type="button"
                        onClick={() => toggleOption(option.value)}
                        className={`rounded-full border px-3 py-1.5 text-sm transition-colors ${selected.includes(option.value)
                            ? "border-slate-900 bg-slate-900 text-white"
                            : "border-slate-300 bg-white text-slate-700 hover:border-slate-400"
                            }`}
                    >
                        {option.label}
                    </button>
                ))}
            </div>
        </div>
    );
}

interface SearchResultCardProps {
    result: HybridSearchResult;
    selected: boolean;
    onClick: () => void;
}

function SearchResultCard({ result, selected, onClick }: SearchResultCardProps) {
    const highlights = result.highlights.slice(0, 2);

    return (
        <div
            onClick={onClick}
            className={`cursor-pointer rounded-2xl border p-4 transition-colors ${selected
                ? "border-slate-900 bg-slate-50"
                : "border-slate-200 hover:border-slate-400 hover:bg-slate-50"
                }`}
        >
            <div className="flex items-start gap-4">
                <SuggestionIcon type={result.asset.assetType} />
                <div className="flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-base font-semibold text-slate-900">{result.asset.title}</h3>
                        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium uppercase tracking-wide text-slate-600">
                            {result.asset.assetType.replace(/_/g, " ")}
                        </span>
                        {result.asset.domain && (
                            <span className="rounded-full bg-cyan-50 px-2.5 py-1 text-xs font-medium uppercase tracking-wide text-cyan-700">
                                {result.asset.domain}
                            </span>
                        )}
                    </div>
                    <p className="mt-2 text-sm text-slate-600 line-clamp-2">
                        {highlights[0]?.snippet ?? result.ranking.matchReason}
                    </p>
                    <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                        <span className="rounded-full bg-slate-100 px-2.5 py-1">
                            score {(result.ranking.score * 100).toFixed(0)}
                        </span>
                        {result.asset.difficultyLevel && (
                            <span className="rounded-full bg-slate-100 px-2.5 py-1">
                                {result.asset.difficultyLevel.toLowerCase()}
                            </span>
                        )}
                        {result.asset.semanticIndexStatus && (
                            <span className="rounded-full bg-slate-100 px-2.5 py-1">
                                index {result.asset.semanticIndexStatus}
                            </span>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

interface RecommendationRailPanelProps {
    selectedResult?: HybridSearchResult;
    relatedAssets?: RelatedAssetsResponse;
    nextSteps?: NextStepSuggestion[];
    onSelectSuggestion: (suggestion: NextStepSuggestion, source: string) => void;
}

function RecommendationRailPanel({
    selectedResult,
    relatedAssets,
    nextSteps,
    onSelectSuggestion,
}: RecommendationRailPanelProps) {
    const sections = [
        {
            title: "Related assets",
            source: "related",
            items: relatedAssets?.related ?? [],
        },
        {
            title: "Prerequisites",
            source: "prerequisite",
            items: relatedAssets?.prerequisites ?? [],
        },
        {
            title: "Alternatives",
            source: "alternative",
            items: relatedAssets?.alternatives ?? [],
        },
        {
            title: "Next steps",
            source: "next_step",
            items: nextSteps ?? [],
        },
    ];

    return (
        <aside className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Discovery rails</p>
                <h2 className="mt-2 text-lg font-semibold text-slate-900">
                    {selectedResult ? selectedResult.asset.title : "Select a result"}
                </h2>
                <p className="mt-1 text-sm text-slate-600">
                    {selectedResult
                        ? "Live recommendation rails are rendered from canonical asset and edge APIs."
                        : "Choose a search result to inspect related content and next-step guidance."}
                </p>
            </div>

            <div className="mt-5 space-y-5">
                {sections.map((section) => (
                    <div key={section.title}>
                        <h3 className="mb-2 text-sm font-semibold text-slate-800">{section.title}</h3>
                        {section.items.length === 0 ? (
                            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500">
                                No live items available.
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {section.items.map((item) => (
                                    <button
                                        key={`${section.source}-${item.asset.id}`}
                                        type="button"
                                        onClick={() => onSelectSuggestion(item, section.source)}
                                        className="w-full rounded-2xl border border-slate-200 px-4 py-3 text-left transition-colors hover:border-slate-400 hover:bg-slate-50"
                                    >
                                        <div className="flex items-start gap-3">
                                            <SuggestionIcon type={item.asset.assetType} />
                                            <div className="min-w-0 flex-1">
                                                <div className="text-sm font-semibold text-slate-900">{item.asset.title}</div>
                                                <div className="mt-1 text-xs uppercase tracking-wide text-slate-500">
                                                    {item.asset.assetType.replace(/_/g, " ")}
                                                </div>
                                                <p className="mt-2 text-sm text-slate-600 line-clamp-2">{item.reason}</p>
                                            </div>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </aside>
    );
}
