/**
 * Search Panel Component
 *
 * Enhanced search with AI-powered semantic search including:
 * - Hybrid search (text + semantic)
 * - Search filters
 * - Result ranking display
 * - Recent searches
 * - Search suggestions
 *
 * @doc.type component
 * @doc.purpose Provide AI-powered semantic search interface
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search,
  X,
  Filter,
  Sparkles,
  Clock,
  TrendingUp,
  FileText,
  ChevronRight,
  Loader2,
} from "lucide-react";
import {
  Button,
  Input,
  Card,
  Badge,
  Popover,
  Command,
} from "@ghatana/design-system";

export interface SearchResult {
  contentId: string;
  contentType: string;
  title: string;
  description: string;
  semanticScore: number;
  textScore: number;
  combinedScore: number;
  metadata: {
    domain?: string;
    difficulty?: string;
    tags?: string[];
  };
}

interface SearchPanelProps {
  tenantId: string;
  onSearch: (query: string, filters: SearchFilters) => Promise<SearchResult[]>;
  recentSearches?: string[];
  popularSearches?: string[];
}

export interface SearchFilters {
  contentTypes: string[];
  difficulty?: string;
  domain?: string;
  semanticWeight: number;
}

const CONTENT_TYPE_OPTIONS = [
  { value: "lesson", label: "Lessons" },
  { value: "quiz", label: "Quizzes" },
  { value: "worksheet", label: "Worksheets" },
  { value: "simulation", label: "Simulations" },
  { value: "video", label: "Videos" },
];

const DIFFICULTY_OPTIONS = [
  { value: "beginner", label: "Beginner" },
  { value: "intermediate", label: "Intermediate" },
  { value: "advanced", label: "Advanced" },
];

export function SearchPanel({
  tenantId,
  onSearch,
  recentSearches = [],
  popularSearches = [],
}: SearchPanelProps) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showFilters, setShowFilters] = useState(false);
  const [filters, setFilters] = useState<SearchFilters>({
    contentTypes: [],
    semanticWeight: 0.6,
  });
  const [hasSearched, setHasSearched] = useState(false);

  const performSearch = useCallback(
    async (searchQuery: string) => {
      if (!searchQuery.trim()) return;

      setIsSearching(true);
      setHasSearched(true);

      try {
        const searchResults = await onSearch(searchQuery, filters);
        setResults(searchResults);
      } catch (error) {
        console.error("Search failed:", error);
      } finally {
        setIsSearching(false);
      }
    },
    [onSearch, filters],
  );

  useEffect(() => {
    const debounceTimer = setTimeout(() => {
      if (query.trim()) {
        performSearch(query);
      }
    }, 300);

    return () => clearTimeout(debounceTimer);
  }, [query, performSearch]);

  const handleResultClick = (result: SearchResult) => {
    navigate(`/content/${result.contentId}`);
  };

  const getScoreColor = (score: number) => {
    if (score >= 0.8) return "bg-green-500";
    if (score >= 0.6) return "bg-blue-500";
    if (score >= 0.4) return "bg-yellow-500";
    return "bg-gray-400";
  };

  const getScoreLabel = (semanticScore: number, textScore: number) => {
    if (semanticScore > 0.7 && textScore > 0.5) return "Highly Relevant";
    if (semanticScore > 0.5 || textScore > 0.5) return "Relevant";
    return "Partial Match";
  };

  return (
    <div className="w-full max-w-4xl mx-auto space-y-4">
      {/* Search Header */}
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search with AI... Try 'fractions for beginners' or 'photosynthesis explained'"
              className="pl-10 pr-10 py-6 text-lg"
            />
            {query && (
              <button
                onClick={() => setQuery("")}
                className="absolute right-3 top-1/2 -translate-y-1/2 p-1 hover:bg-gray-100 rounded-full"
              >
                <X className="w-4 h-4 text-gray-400" />
              </button>
            )}
          </div>
          <Button
            variant="outline"
            onClick={() => setShowFilters(!showFilters)}
            className={showFilters ? "bg-blue-50 border-blue-300" : ""}
          >
            <Filter className="w-4 h-4 mr-2" />
            Filters
          </Button>
        </div>

        {/* AI Search Badge */}
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <Sparkles className="w-4 h-4 text-purple-500" />
          <span>AI-powered semantic search enabled</span>
          <Badge variant="outline" className="text-xs">
            Semantic: {Math.round(filters.semanticWeight * 100)}%
          </Badge>
          <Badge variant="outline" className="text-xs">
            Text: {Math.round((1 - filters.semanticWeight) * 100)}%
          </Badge>
        </div>

        {/* Filters Panel */}
        {showFilters && (
          <Card className="p-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="text-sm font-medium mb-2 block">Content Type</label>
                <div className="flex flex-wrap gap-2">
                  {CONTENT_TYPE_OPTIONS.map((type) => (
                    <Badge
                      key={type.value}
                      variant={
                        filters.contentTypes.includes(type.value)
                          ? "default"
                          : "outline"
                      }
                      className="cursor-pointer"
                      onClick={() => {
                        setFilters((prev) => ({
                          ...prev,
                          contentTypes: prev.contentTypes.includes(type.value)
                            ? prev.contentTypes.filter((t) => t !== type.value)
                            : [...prev.contentTypes, type.value],
                        }));
                      }}
                    >
                      {type.label}
                    </Badge>
                  ))}
                </div>
              </div>

              <div>
                <label className="text-sm font-medium mb-2 block">Difficulty</label>
                <select
                  value={filters.difficulty ?? ""}
                  onChange={(e) =>
                    setFilters((prev) => ({
                      ...prev,
                      difficulty: e.target.value || undefined,
                    }))
                  }
                  className="w-full p-2 border rounded-md"
                >
                  <option value="">Any Difficulty</option>
                  {DIFFICULTY_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-sm font-medium mb-2 block">
                  AI vs Text Balance
                </label>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.1"
                  value={filters.semanticWeight}
                  onChange={(e) =>
                    setFilters((prev) => ({
                      ...prev,
                      semanticWeight: parseFloat(e.target.value),
                    }))
                  }
                  className="w-full"
                />
                <div className="flex justify-between text-xs text-gray-500 mt-1">
                  <span>More Text</span>
                  <span>More AI</span>
                </div>
              </div>
            </div>
          </Card>
        )}
      </div>

      {/* Search Suggestions (when no search yet) */}
      {!hasSearched && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {recentSearches.length > 0 && (
            <Card className="p-4">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <Clock className="w-4 h-4" />
                Recent Searches
              </h3>
              <div className="flex flex-wrap gap-2">
                {recentSearches.map((search, i) => (
                  <button
                    key={i}
                    onClick={() => {
                      setQuery(search);
                      performSearch(search);
                    }}
                    className="text-sm text-blue-600 hover:underline"
                  >
                    {search}
                  </button>
                ))}
              </div>
            </Card>
          )}

          {popularSearches.length > 0 && (
            <Card className="p-4">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <TrendingUp className="w-4 h-4" />
                Popular Searches
              </h3>
              <div className="flex flex-wrap gap-2">
                {popularSearches.map((search, i) => (
                  <button
                    key={i}
                    onClick={() => {
                      setQuery(search);
                      performSearch(search);
                    }}
                    className="text-sm text-blue-600 hover:underline"
                  >
                    {search}
                  </button>
                ))}
              </div>
            </Card>
          )}
        </div>
      )}

      {/* Search Results */}
      {hasSearched && (
        <div className="space-y-4">
          {isSearching ? (
            <div className="flex justify-center py-12">
              <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
            </div>
          ) : results.length === 0 ? (
            <Card className="p-8 text-center">
              <FileText className="w-12 h-12 mx-auto text-gray-400 mb-4" />
              <h3 className="text-lg font-medium text-gray-900">No results found</h3>
              <p className="text-gray-500 mt-1">
                Try adjusting your search terms or filters
              </p>
            </Card>
          ) : (
            <>
              <p className="text-sm text-gray-500">
                Found {results.length} results for "{query}"
              </p>

              <div className="space-y-3">
                {results.map((result) => (
                  <Card
                    key={result.contentId}
                    className="p-4 cursor-pointer hover:shadow-md transition-shadow"
                    onClick={() => handleResultClick(result)}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <h3 className="font-semibold text-gray-900">
                            {result.title}
                          </h3>
                          <Badge variant="outline" className="text-xs">
                            {result.contentType}
                          </Badge>
                          <Badge
                            className={`text-xs ${getScoreColor(
                              result.combinedScore,
                            )} text-white`}
                          >
                            {getScoreLabel(result.semanticScore, result.textScore)}
                          </Badge>
                        </div>

                        <p className="text-sm text-gray-600 line-clamp-2">
                          {result.description}
                        </p>

                        <div className="flex items-center gap-3 mt-2 text-xs text-gray-500">
                          {result.metadata.domain && (
                            <span>Domain: {result.metadata.domain}</span>
                          )}
                          {result.metadata.difficulty && (
                            <span>• {result.metadata.difficulty}</span>
                          )}
                          {result.metadata.tags && result.metadata.tags.length > 0 && (
                            <span>• {result.metadata.tags.slice(0, 3).join(", ")}</span>
                          )}
                        </div>

                        {/* Score Breakdown */}
                        <div className="flex items-center gap-4 mt-2 text-xs">
                          <div className="flex items-center gap-1">
                            <Sparkles className="w-3 h-3 text-purple-500" />
                            <span>AI Match:</span>
                            <span className="font-medium">
                              {Math.round(result.semanticScore * 100)}%
                            </span>
                          </div>
                          <div className="flex items-center gap-1">
                            <Search className="w-3 h-3 text-blue-500" />
                            <span>Text Match:</span>
                            <span className="font-medium">
                              {Math.round(result.textScore * 100)}%
                            </span>
                          </div>
                        </div>
                      </div>

                      <ChevronRight className="w-5 h-5 text-gray-400 ml-4 flex-shrink-0" />
                    </div>
                  </Card>
                ))}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

// Hook for semantic search
export function useSemanticSearch(tenantId: string) {
  const [recentSearches, setRecentSearches] = useState<string[]>([]);

  const performSearch = async (
    query: string,
    filters: SearchFilters,
  ): Promise<SearchResult[]> => {
    // Add to recent searches
    setRecentSearches((prev) => {
      const updated = [query, ...prev.filter((s) => s !== query)].slice(0, 5);
      localStorage.setItem(`recent_searches_${tenantId}`, JSON.stringify(updated));
      return updated;
    });

    // Call API
    const response = await fetch("/api/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query,
        tenantId,
        filters,
      }),
    });

    if (!response.ok) {
      throw new Error("Search failed");
    }

    const data = await response.json();
    return data.results;
  };

  // Load recent searches on mount
  useEffect(() => {
    const stored = localStorage.getItem(`recent_searches_${tenantId}`);
    if (stored) {
      setRecentSearches(JSON.parse(stored));
    }
  }, [tenantId]);

  return {
    performSearch,
    recentSearches,
    popularSearches: [
      "fractions explained",
      "photosynthesis basics",
      "linear equations",
      "world war 2 timeline",
      "python programming",
    ],
  };
}
