/**
 * useSimulationTemplates Hook
 *
 * Hook for fetching and managing simulation templates from the marketplace.
 *
 * @doc.type hook
 * @doc.purpose Marketplace template data fetching and state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from "react";
import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from "@tanstack/react-query";
import type {
  SimulationTemplate,
  TemplateFilters,
  TemplateSort,
  TemplatesResponse,
  TemplateDetailResponse,
  TemplateSortField,
} from "../types";

// =============================================================================
// Query Keys
// =============================================================================

export const templateQueryKeys = {
  all: ["templates"] as const,
  lists: () => [...templateQueryKeys.all, "list"] as const,
  list: (filters: TemplateFilters, sort: TemplateSort) =>
    [...templateQueryKeys.lists(), { filters, sort }] as const,
  details: () => [...templateQueryKeys.all, "detail"] as const,
  detail: (id: string) => [...templateQueryKeys.details(), id] as const,
  featured: () => [...templateQueryKeys.all, "featured"] as const,
  collections: () => [...templateQueryKeys.all, "collections"] as const,
  favorites: (userId: string) => [...templateQueryKeys.all, "favorites", userId] as const,
};

// =============================================================================
// API Functions
// =============================================================================

const API_BASE_URL = "/api/v1/marketplace";

function getRequestHeaders(): Record<string, string> {
  const token = localStorage.getItem("auth_token");
  const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Tenant-ID": tenantId,
    "X-Correlation-ID": crypto.randomUUID(),
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return headers;
}

const STUB_TEMPLATES: SimulationTemplate[] = [
  {
    id: "stub-physics-lab",
    title: "Physics Lab: Harmonic Oscillator",
    description: "Explore mass-spring systems and visualize simple harmonic motion.",
    domain: "PHYSICS",
    difficulty: "beginner",
    tags: ["physics", "oscillation", "lab"],
    thumbnailUrl: "https://placehold.co/600x400?text=Physics+Lab",
    manifestId: "manifest-stub-1",
    author: {
      id: "author-1",
      name: "Dr. Ada Lovelace",
      isVerified: true,
      avatarUrl: "https://placehold.co/64x64?text=A",
      organization: "TutorPutor Labs",
    },
    publishedAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    version: "1.0.0",
    stats: {
      views: 1200,
      uses: 350,
      favorites: 80,
      rating: 4.7,
      ratingCount: 45,
      completionRate: 0.82,
      avgTimeMinutes: 25,
    },
    isPremium: false,
    isVerified: true,
    license: "free",
  },
  {
    id: "stub-chemistry-simulation",
    title: "Chemistry: Reaction Kinetics Studio",
    description: "Simulate reaction rates, concentration curves, and activation energy.",
    domain: "CHEMISTRY",
    difficulty: "intermediate",
    tags: ["chemistry", "kinetics", "simulation"],
    thumbnailUrl: "https://placehold.co/600x400?text=Chemistry+Studio",
    manifestId: "manifest-stub-2",
    author: {
      id: "author-2",
      name: "Prof. Marie Curie",
      isVerified: true,
      avatarUrl: "https://placehold.co/64x64?text=M",
      organization: "TutorPutor Labs",
    },
    publishedAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    version: "1.0.0",
    stats: {
      views: 980,
      uses: 260,
      favorites: 60,
      rating: 4.5,
      ratingCount: 32,
      completionRate: 0.76,
      avgTimeMinutes: 30,
    },
    isPremium: true,
    isVerified: true,
    license: "proprietary",
  },
];

function createTemplatesStub(page: number, pageSize: number): TemplatesResponse {
  const templates = STUB_TEMPLATES.slice(0, pageSize);
  return {
    templates,
    total: STUB_TEMPLATES.length,
    page,
    pageSize,
    hasMore: false,
  };
}

async function fetchTemplates(
  filters: TemplateFilters,
  sort: TemplateSort,
  page: number,
  pageSize: number
): Promise<TemplatesResponse> {
  const params = new URLSearchParams();

  if (filters.domains?.length) {
    params.set("domains", filters.domains.join(","));
  }
  if (filters.difficulties?.length) {
    params.set("difficulties", filters.difficulties.join(","));
  }
  if (filters.tags?.length) {
    params.set("tags", filters.tags.join(","));
  }
  if (filters.isPremium !== undefined) {
    params.set("isPremium", String(filters.isPremium));
  }
  if (filters.isVerified !== undefined) {
    params.set("isVerified", String(filters.isVerified));
  }
  if (filters.minRating !== undefined) {
    params.set("minRating", String(filters.minRating));
  }
  if (filters.search) {
    params.set("search", filters.search);
  }

  params.set("sortBy", sort.field);
  params.set("sortOrder", sort.order);
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));

  try {
    const response = await fetch(`${API_BASE_URL}/templates?${params.toString()}`, {
      headers: getRequestHeaders(),
    });

    if (!response.ok) {
      console.error(
        `Failed to fetch templates: ${response.status} ${response.statusText}`
      );
      return createTemplatesStub(page, pageSize);
    }

    return response.json();
  } catch (error) {
    console.error("Failed to fetch templates", error);
    return createTemplatesStub(page, pageSize);
  }
}

async function fetchTemplateById(id: string): Promise<TemplateDetailResponse> {
  try {
    const response = await fetch(`${API_BASE_URL}/templates/${id}`, {
      headers: getRequestHeaders(),
    });

    if (!response.ok) {
      console.error(
        `Failed to fetch template: ${response.status} ${response.statusText}`
      );
      const fallback = STUB_TEMPLATES[0];
      return {
        template: fallback,
        relatedTemplates: STUB_TEMPLATES.slice(1),
        userFavorited: false,
        userRating: undefined,
      };
    }

    return response.json();
  } catch (error) {
    console.error("Failed to fetch template", error);
    const fallback = STUB_TEMPLATES[0];
    return {
      template: fallback,
      relatedTemplates: STUB_TEMPLATES.slice(1),
      userFavorited: false,
      userRating: undefined,
    };
  }
}

async function fetchFeaturedTemplates(): Promise<SimulationTemplate[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/templates/featured`, {
      headers: getRequestHeaders(),
    });

    if (!response.ok) {
      console.error(
        `Failed to fetch featured templates: ${response.status} ${response.statusText}`
      );
      return STUB_TEMPLATES.slice(0, 3);
    }

    return response.json();
  } catch (error) {
    console.error("Failed to fetch featured templates", error);
    return STUB_TEMPLATES.slice(0, 3);
  }
}

async function toggleFavorite(templateId: string): Promise<{ favorited: boolean }> {
  const response = await fetch(`${API_BASE_URL}/templates/${templateId}/favorite`, {
    method: "POST",
    headers: getRequestHeaders(),
  });

  if (!response.ok) {
    throw new Error(`Failed to toggle favorite: ${response.statusText}`);
  }

  return response.json();
}

async function rateTemplate(
  templateId: string,
  rating: number,
  review?: string
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/templates/${templateId}/rate`, {
    method: "POST",
    headers: getRequestHeaders(),
    body: JSON.stringify({ rating, review }),
  });

  if (!response.ok) {
    throw new Error(`Failed to rate template: ${response.statusText}`);
  }
}

async function useTemplate(templateId: string): Promise<{ manifestId: string }> {
  const response = await fetch(`${API_BASE_URL}/templates/${templateId}/use`, {
    method: "POST",
    headers: getRequestHeaders(),
  });

  if (!response.ok) {
    throw new Error(`Failed to use template: ${response.statusText}`);
  }

  return response.json();
}

// =============================================================================
// Hook Options
// =============================================================================

export interface UseSimulationTemplatesOptions {
  initialFilters?: TemplateFilters;
  initialSort?: TemplateSort;
  pageSize?: number;
  enabled?: boolean;
}

export interface UseSimulationTemplatesReturn {
  // Data
  templates: SimulationTemplate[];
  total: number;
  hasMore: boolean;
  isLoading: boolean;
  isLoadingMore: boolean;
  error: Error | null;

  // Pagination
  loadMore: () => void;
  refresh: () => void;

  // Filters
  filters: TemplateFilters;
  setFilters: (filters: TemplateFilters) => void;
  updateFilter: <K extends keyof TemplateFilters>(
    key: K,
    value: TemplateFilters[K]
  ) => void;
  clearFilters: () => void;

  // Sorting
  sort: TemplateSort;
  setSort: (sort: TemplateSort) => void;
  setSortField: (field: TemplateSortField) => void;
  toggleSortOrder: () => void;

  // Search
  search: string;
  setSearch: (search: string) => void;
}

// =============================================================================
// Main Hook
// =============================================================================

const DEFAULT_FILTERS: TemplateFilters = {};
const DEFAULT_SORT: TemplateSort = { field: "popularity", order: "desc" };
const DEFAULT_PAGE_SIZE = 12;

export function useSimulationTemplates({
  initialFilters = DEFAULT_FILTERS,
  initialSort = DEFAULT_SORT,
  pageSize = DEFAULT_PAGE_SIZE,
  enabled = true,
}: UseSimulationTemplatesOptions = {}): UseSimulationTemplatesReturn {
  const [filters, setFilters] = useState<TemplateFilters>(initialFilters);
  const [sort, setSort] = useState<TemplateSort>(initialSort);

  // Infinite query for paginated templates
  const {
    data,
    isLoading,
    isFetchingNextPage,
    error,
    fetchNextPage,
    hasNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: templateQueryKeys.list(filters, sort),
    queryFn: ({ pageParam = 1 }) => fetchTemplates(filters, sort, pageParam, pageSize),
    getNextPageParam: (lastPage) =>
      lastPage.hasMore ? lastPage.page + 1 : undefined,
    initialPageParam: 1,
    enabled,
  });

  // Flatten pages into single array
  const templates: SimulationTemplate[] = useMemo(() => {
    if (!data?.pages) return [];

    return data.pages.flatMap((page) => {
      if (!page || !Array.isArray(page.templates)) {
        return [] as SimulationTemplate[];
      }

      return page.templates.filter((t): t is SimulationTemplate => Boolean(t));
    });
  }, [data?.pages]);

  const total = data?.pages[0]?.total ?? 0;

  // Filter helpers
  const updateFilter = useCallback(
    <K extends keyof TemplateFilters>(key: K, value: TemplateFilters[K]) => {
      setFilters((prev) => ({ ...prev, [key]: value }));
    },
    []
  );

  const clearFilters = useCallback(() => {
    setFilters(DEFAULT_FILTERS);
  }, []);

  // Sort helpers
  const setSortField = useCallback((field: TemplateSortField) => {
    setSort((prev) => ({ ...prev, field }));
  }, []);

  const toggleSortOrder = useCallback(() => {
    setSort((prev) => ({
      ...prev,
      order: prev.order === "asc" ? "desc" : "asc",
    }));
  }, []);

  // Search helper
  const search = filters.search ?? "";
  const setSearch = useCallback((value: string) => {
    setFilters((prev) => ({ ...prev, search: value || undefined }));
  }, []);

  // Actions
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const refresh = useCallback(() => {
    refetch();
  }, [refetch]);

  return {
    templates,
    total,
    hasMore: hasNextPage ?? false,
    isLoading,
    isLoadingMore: isFetchingNextPage,
    error: error as Error | null,

    loadMore,
    refresh,

    filters,
    setFilters,
    updateFilter,
    clearFilters,

    sort,
    setSort,
    setSortField,
    toggleSortOrder,

    search,
    setSearch,
  };
}

// =============================================================================
// Single Template Hook
// =============================================================================

export interface UseTemplateDetailOptions {
  templateId: string;
  enabled?: boolean;
}

export interface UseTemplateDetailReturn {
  template: SimulationTemplate | null;
  relatedTemplates: SimulationTemplate[];
  userFavorited: boolean;
  userRating: number | null;
  isLoading: boolean;
  error: Error | null;

  // Actions
  toggleFavorite: () => Promise<void>;
  rate: (rating: number, review?: string) => Promise<void>;
  useTemplate: () => Promise<string>;
  isFavoriting: boolean;
  isRating: boolean;
  isUsing: boolean;
}

export function useTemplateDetail({
  templateId,
  enabled = true,
}: UseTemplateDetailOptions): UseTemplateDetailReturn {
  const queryClient = useQueryClient();

  // Fetch template details
  const { data, isLoading, error } = useQuery({
    queryKey: templateQueryKeys.detail(templateId),
    queryFn: () => fetchTemplateById(templateId),
    enabled: enabled && !!templateId,
  });

  // Toggle favorite mutation
  const favoriteMutation = useMutation({
    mutationFn: () => toggleFavorite(templateId),
    onSuccess: (result) => {
      queryClient.setQueryData(
        templateQueryKeys.detail(templateId),
        (old: TemplateDetailResponse | undefined) =>
          old ? { ...old, userFavorited: result.favorited } : old
      );
    },
  });

  // Rate mutation
  const rateMutation = useMutation({
    mutationFn: ({ rating, review }: { rating: number; review?: string }) =>
      rateTemplate(templateId, rating, review),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: templateQueryKeys.detail(templateId) });
    },
  });

  // Use template mutation
  const useMutation_ = useMutation({
    mutationFn: () => useTemplate(templateId),
    onSuccess: () => {
      // Update usage count
      queryClient.invalidateQueries({ queryKey: templateQueryKeys.detail(templateId) });
    },
  });

  return {
    template: data?.template ?? null,
    relatedTemplates: data?.relatedTemplates ?? [],
    userFavorited: data?.userFavorited ?? false,
    userRating: data?.userRating ?? null,
    isLoading,
    error: error as Error | null,

    toggleFavorite: async () => {
      await favoriteMutation.mutateAsync();
    },
    rate: async (rating: number, review?: string) => {
      await rateMutation.mutateAsync({ rating, review });
    },
    useTemplate: async () => {
      const result = await useMutation_.mutateAsync();
      return result.manifestId;
    },
    isFavoriting: favoriteMutation.isPending,
    isRating: rateMutation.isPending,
    isUsing: useMutation_.isPending,
  };
}

// =============================================================================
// Featured Templates Hook
// =============================================================================

export function useFeaturedTemplates() {
  const { data, isLoading, error } = useQuery({
    queryKey: templateQueryKeys.featured(),
    queryFn: fetchFeaturedTemplates,
  });

  return {
    templates: data ?? [],
    isLoading,
    error: error as Error | null,
  };
}

// =============================================================================
// Toggle Favorite Hook
// =============================================================================

/**
 * Hook for toggling a template's favorite status with optimistic UI support.
 * Invalidates affected query cache entries on success.
 *
 * @doc.type hook
 * @doc.purpose Toggle favorite status for a simulation template
 * @doc.layer product
 * @doc.pattern Mutation
 */
export function useToggleFavorite() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: toggleFavorite,
    onSuccess: () => {
      // Invalidate list and detail queries so counts/flags refresh
      void queryClient.invalidateQueries({ queryKey: templateQueryKeys.all });
    },
    onError: (error: Error) => {
      console.error("Failed to toggle template favorite:", error.message);
    },
  });
}
