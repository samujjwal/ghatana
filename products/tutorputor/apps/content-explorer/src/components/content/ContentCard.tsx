import { Link } from "react-router-dom";
import { clsx } from "clsx";
import { Bot, Clock, Star } from "lucide-react";
import type { ContentItem } from "@/types/content";

const STATUS_COLOURS: Record<string, string> = {
  draft: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800",
  generating: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  review: "bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300",
  approved: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  published: "bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300",
  archived: "bg-zinc-100 text-zinc-500 dark:bg-zinc-800",
};

const DIFFICULTY_COLOURS: Record<string, string> = {
  beginner: "text-green-600",
  intermediate: "text-yellow-600",
  advanced: "text-orange-600",
  expert: "text-red-600",
};

interface ContentCardProps {
  item: ContentItem;
  className?: string;
}

export function ContentCard({ item, className }: ContentCardProps) {
  return (
    <Link
      to={`/view/${item.id}`}
      className={clsx(
        "group flex flex-col rounded-lg border border-border bg-card p-4 shadow-sm",
        "transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        className,
      )}
    >
      {/* Header row */}
      <div className="mb-2 flex items-start justify-between gap-2">
        <span
          className={clsx(
            "inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium",
            STATUS_COLOURS[item.status] ?? "bg-zinc-100 text-zinc-600",
          )}
        >
          {item.status}
        </span>

        {item.aiGenerated && (
          <Bot className="h-4 w-4 shrink-0 text-muted-foreground" title="AI generated" />
        )}
      </div>

      {/* Title */}
      <h3 className="mb-1 line-clamp-2 text-sm font-semibold leading-snug text-foreground group-hover:text-primary">
        {item.title}
      </h3>

      {/* Subject / type */}
      <p className="mb-3 text-xs text-muted-foreground">
        {item.subject} · {item.type}
      </p>

      {/* Preview text */}
      {item.previewText && (
        <p className="mb-3 line-clamp-2 text-xs text-muted-foreground">{item.previewText}</p>
      )}

      {/* Footer row */}
      <div className="mt-auto flex items-center justify-between text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <Clock className="h-3 w-3" aria-hidden />
          <span>{item.estimatedMinutes ?? "—"} min</span>
        </div>

        <div className="flex items-center gap-1">
          <Star className="h-3 w-3 text-yellow-500" aria-hidden />
          <span>{item.qualityScore !== null ? item.qualityScore : "—"}</span>
        </div>

        <span className={clsx("font-medium", DIFFICULTY_COLOURS[item.difficulty])}>
          {item.difficulty}
        </span>
      </div>
    </Link>
  );
}
