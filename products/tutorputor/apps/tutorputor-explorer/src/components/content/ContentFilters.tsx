import { useAtom } from "jotai";
import { Search, X, SlidersHorizontal } from "lucide-react";
import type { ContentFilters, ContentType, ContentStatus, DifficultyLevel } from "@/types/content";
import { EMPTY_FILTERS } from "@/types/content";
import { filtersAtom, currentPageAtom, hasActiveFiltersAtom } from "@/stores/explorerStore";

const CONTENT_TYPES: ContentType[] = [
  "lesson", "quiz", "exercise", "explanation", "summary", "flashcard", "simulation",
];
const STATUSES: ContentStatus[] = [
  "draft", "generating", "review", "approved", "published", "archived",
];
const DIFFICULTIES: DifficultyLevel[] = ["beginner", "intermediate", "advanced", "expert"];

function ToggleChip({
  label,
  active,
  onToggle,
}: {
  label: string;
  active: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={
        active
          ? "rounded-full bg-primary px-2.5 py-0.5 text-xs font-medium text-primary-foreground"
          : "rounded-full border border-border px-2.5 py-0.5 text-xs font-medium text-muted-foreground hover:border-primary hover:text-primary"
      }
    >
      {label}
    </button>
  );
}

export function ContentFilters() {
  const [filters, setFilters] = useAtom(filtersAtom);
  const [, setPage] = useAtom(currentPageAtom);
  const [hasActive] = useAtom(hasActiveFiltersAtom);

  function update(patch: Partial<ContentFilters>) {
    setFilters((prev) => ({ ...prev, ...patch }));
    setPage(1);
  }

  function toggleArray<T>(arr: T[], value: T): T[] {
    return arr.includes(value) ? arr.filter((v) => v !== value) : [...arr, value];
  }

  return (
    <div className="space-y-4">
      {/* Search bar */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="search"
          placeholder="Search content…"
          value={filters.search}
          onChange={(e) => update({ search: e.target.value })}
          className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-4 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
        />
      </div>

      {/* Filter rows */}
      <div className="flex flex-wrap items-center gap-3">
        <SlidersHorizontal className="h-4 w-4 text-muted-foreground" aria-hidden />

        {/* Type chips */}
        <div className="flex flex-wrap gap-1.5">
          {CONTENT_TYPES.map((type) => (
            <ToggleChip
              key={type}
              label={type}
              active={filters.types.includes(type)}
              onToggle={() => update({ types: toggleArray(filters.types, type) })}
            />
          ))}
        </div>

        <div className="h-4 w-px bg-border" aria-hidden />

        {/* Status chips */}
        <div className="flex flex-wrap gap-1.5">
          {STATUSES.map((status) => (
            <ToggleChip
              key={status}
              label={status}
              active={filters.statuses.includes(status)}
              onToggle={() => update({ statuses: toggleArray(filters.statuses, status) })}
            />
          ))}
        </div>

        <div className="h-4 w-px bg-border" aria-hidden />

        {/* Difficulty chips */}
        {DIFFICULTIES.map((d) => (
          <ToggleChip
            key={d}
            label={d}
            active={filters.difficulties.includes(d)}
            onToggle={() => update({ difficulties: toggleArray(filters.difficulties, d) })}
          />
        ))}

        <div className="h-4 w-px bg-border" aria-hidden />

        {/* AI generated toggle */}
        <label className="flex cursor-pointer items-center gap-2 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={filters.aiGeneratedOnly}
            onChange={(e) => update({ aiGeneratedOnly: e.target.checked })}
            className="rounded border-border"
          />
          AI generated only
        </label>

        {/* Clear all */}
        {hasActive && (
          <button
            type="button"
            onClick={() => {
              setFilters(EMPTY_FILTERS);
              setPage(1);
            }}
            className="ml-auto flex items-center gap-1 text-xs text-muted-foreground hover:text-destructive"
          >
            <X className="h-3.5 w-3.5" aria-hidden />
            Clear filters
          </button>
        )}
      </div>
    </div>
  );
}
