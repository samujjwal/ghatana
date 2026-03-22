import { useAtom } from "jotai";
import { LayoutGrid, List, ChevronLeft, ChevronRight } from "lucide-react";
import { ContentCard } from "@/components/content/ContentCard";
import { ContentFilters } from "@/components/content/ContentFilters";
import { useContentList } from "@/hooks/useContent";
import { currentPageAtom, pageSizeAtom, viewModeAtom } from "@/stores/explorerStore";
import { clsx } from "clsx";

export function ExplorePage() {
  const { data, isLoading, isError, error } = useContentList();
  const [page, setPage] = useAtom(currentPageAtom);
  const [pageSize] = useAtom(pageSizeAtom);
  const [viewMode, setViewMode] = useAtom(viewModeAtom);

  const totalPages = data ? Math.ceil(data.total / pageSize) : 1;

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* Page header */}
      <header className="shrink-0 border-b border-border px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-semibold">Content Library</h1>
            {data && (
              <p className="text-sm text-muted-foreground">
                {data.total.toLocaleString()} items
              </p>
            )}
          </div>

          {/* View mode toggle */}
          <div className="flex rounded-md border border-border">
            <button
              type="button"
              onClick={() => setViewMode("grid")}
              className={clsx(
                "rounded-l-md p-2 transition-colors",
                viewMode === "grid" ? "bg-muted text-foreground" : "text-muted-foreground hover:bg-muted/50",
              )}
              aria-label="Grid view"
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => setViewMode("list")}
              className={clsx(
                "rounded-r-md p-2 transition-colors",
                viewMode === "list" ? "bg-muted text-foreground" : "text-muted-foreground hover:bg-muted/50",
              )}
              aria-label="List view"
            >
              <List className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Filters */}
        <div className="mt-4">
          <ContentFilters />
        </div>
      </header>

      {/* Content grid / list */}
      <div className="flex-1 overflow-y-auto p-6">
        {isLoading && (
          <div
            className={clsx(
              "gap-4",
              viewMode === "grid"
                ? "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                : "flex flex-col",
            )}
          >
            {Array.from({ length: pageSize }).map((_, i) => (
              <div
                key={i}
                className="h-44 animate-pulse rounded-lg border border-border bg-muted"
              />
            ))}
          </div>
        )}

        {isError && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <p className="text-sm font-medium text-destructive">
              Failed to load content
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {(error as Error)?.message ?? "Unknown error"}
            </p>
          </div>
        )}

        {data && data.items.length === 0 && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <p className="text-sm font-medium text-muted-foreground">No content found</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Try adjusting your filters or generate new content.
            </p>
          </div>
        )}

        {data && data.items.length > 0 && (
          <div
            className={clsx(
              "gap-4",
              viewMode === "grid"
                ? "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                : "flex flex-col",
            )}
          >
            {data.items.map((item) => (
              <ContentCard key={item.id} item={item} />
            ))}
          </div>
        )}
      </div>

      {/* Pagination */}
      {data && totalPages > 1 && (
        <div className="flex shrink-0 items-center justify-between border-t border-border px-6 py-3">
          <p className="text-xs text-muted-foreground">
            Page {page} of {totalPages}
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
              className="flex items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium disabled:opacity-40"
            >
              <ChevronLeft className="h-3.5 w-3.5" aria-hidden />
              Prev
            </button>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page === totalPages}
              className="flex items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium disabled:opacity-40"
            >
              Next
              <ChevronRight className="h-3.5 w-3.5" aria-hidden />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
