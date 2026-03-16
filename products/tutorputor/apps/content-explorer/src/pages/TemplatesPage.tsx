/**
 * Template Gallery — browse, preview, fork, and apply content templates.
 *
 * Surfaces the full template library to content authors so they can scaffold
 * new content items from proven blueprints rather than starting from scratch.
 */

import React, { useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useSetAtom } from "jotai";
import {
  Plus,
  Search,
  Trash2,
  Copy,
  Sparkles,
  BookOpen,
  FlaskConical,
  Film,
  ChevronRight,
  Star,
  X,
} from "lucide-react";
import { useTemplates, useDeleteTemplate, useApplyTemplate } from "@/hooks/useTemplates";
import type { ContentTemplate } from "@/types/templates";
import type { ContentType } from "@/types/content";
import { templatePrefillAtom } from "@/stores/explorerStore";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const TYPE_ICON: Record<ContentType, React.ReactNode> = {
  lesson: <BookOpen className="h-4 w-4" />,
  quiz: <Star className="h-4 w-4" />,
  exercise: <ChevronRight className="h-4 w-4" />,
  explanation: <BookOpen className="h-4 w-4" />,
  summary: <BookOpen className="h-4 w-4" />,
  flashcard: <Copy className="h-4 w-4" />,
  simulation: <FlaskConical className="h-4 w-4" />,
};

const DIFFICULTY_COLOR: Record<string, string> = {
  beginner: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400",
  intermediate: "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400",
  advanced: "bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400",
  expert: "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400",
};

// ---------------------------------------------------------------------------
// TemplateCard
// ---------------------------------------------------------------------------

function TemplateCard({
  template,
  onApply,
  onDelete,
  onPreview,
}: {
  template: ContentTemplate;
  onApply: (template: ContentTemplate) => void;
  onDelete?: (id: string) => void;
  onPreview: (template: ContentTemplate) => void;
}) {
  const isBuiltIn = !template.id.startsWith("tpl-user-");

  return (
    <article
      className="flex flex-col rounded-xl border border-border bg-card shadow-sm hover:shadow-md transition-shadow"
      aria-label={`Template: ${template.name}`}
    >
      {/* Header */}
      <div className="flex items-start gap-3 p-4 pb-3">
        <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          {template.animationTemplate ? (
            <Film className="h-4 w-4" />
          ) : template.simulationTemplate ? (
            <FlaskConical className="h-4 w-4" />
          ) : (
            TYPE_ICON[template.contentType]
          )}
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-foreground leading-tight">
            {template.name}
          </h3>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {template.domain} · {template.gradeLevel}
          </p>
        </div>
        {isBuiltIn && (
          <span className="text-[10px] font-medium uppercase tracking-wide text-primary bg-primary/10 rounded px-1.5 py-0.5">
            Built-in
          </span>
        )}
      </div>

      {/* Description */}
      <p className="flex-1 px-4 pb-3 text-xs text-muted-foreground leading-relaxed line-clamp-3">
        {template.description}
      </p>

      {/* Badges */}
      <div className="flex flex-wrap gap-1.5 px-4 pb-3">
        <span
          className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${DIFFICULTY_COLOR[template.difficulty] ?? ""}`}
        >
          {template.difficulty}
        </span>
        {template.metadata.tags.slice(0, 3).map((tag) => (
          <span
            key={tag}
            className="rounded-full bg-muted px-2 py-0.5 text-[10px] text-muted-foreground"
          >
            {tag}
          </span>
        ))}
      </div>

      {/* Footer */}
      <div className="flex items-center gap-2 border-t border-border px-4 py-2.5">
        <span className="flex-1 text-[11px] text-muted-foreground">
          Used {template.metadata.usageCount}×
        </span>
        <button
          onClick={() => onPreview(template)}
          className="rounded px-2 py-1 text-xs text-muted-foreground hover:bg-muted transition-colors"
        >
          Preview
        </button>
        {!isBuiltIn && onDelete && (
          <button
            onClick={() => onDelete(template.id)}
            className="rounded px-2 py-1 text-xs text-destructive hover:bg-destructive/10 transition-colors"
            aria-label={`Delete template ${template.name}`}
          >
            <Trash2 className="h-3 w-3" />
          </button>
        )}
        <button
          onClick={() => onApply(template)}
          className="flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
        >
          <Sparkles className="h-3 w-3" />
          Use
        </button>
      </div>
    </article>
  );
}

// ---------------------------------------------------------------------------
// TemplatePreviewModal
// ---------------------------------------------------------------------------

function TemplatePreviewModal({
  template,
  onClose,
  onApply,
}: {
  template: ContentTemplate;
  onClose: () => void;
  onApply: (template: ContentTemplate) => void;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`Preview template: ${template.name}`}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="relative w-full max-w-xl rounded-2xl bg-card shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-start gap-4 border-b border-border px-6 py-5">
          <div className="flex-1">
            <h2 className="text-base font-semibold text-foreground">
              {template.name}
            </h2>
            <p className="mt-0.5 text-xs text-muted-foreground">
              v{template.metadata.version} · {template.domain} · {template.gradeLevel}
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-muted-foreground hover:bg-muted transition-colors"
            aria-label="Close preview"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="overflow-y-auto max-h-[60vh] p-6 space-y-5">
          {/* Description */}
          <section>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">
              Description
            </h3>
            <p className="text-sm text-foreground leading-relaxed">
              {template.description}
            </p>
          </section>

          {/* Learning Objectives */}
          {template.defaultObjectives.length > 0 && (
            <section>
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">
                Default Learning Objectives
              </h3>
              <ul className="space-y-1">
                {template.defaultObjectives.map((obj, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm text-foreground">
                    <ChevronRight className="h-4 w-4 mt-0.5 flex-shrink-0 text-primary" />
                    {obj}
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* Animation settings */}
          {template.animationTemplate && (
            <section>
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">
                Animation Settings
              </h3>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                <dt className="text-muted-foreground">Duration</dt>
                <dd>{template.animationTemplate.durationMs / 1000}s</dd>
                <dt className="text-muted-foreground">FPS</dt>
                <dd>{template.animationTemplate.fps}</dd>
                <dt className="text-muted-foreground">Style</dt>
                <dd className="capitalize">{template.animationTemplate.style}</dd>
                {template.animationTemplate.notes && (
                  <>
                    <dt className="text-muted-foreground">Notes</dt>
                    <dd className="text-xs text-muted-foreground">{template.animationTemplate.notes}</dd>
                  </>
                )}
              </dl>
            </section>
          )}

          {/* Simulation settings */}
          {template.simulationTemplate && (
            <section>
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">
                Simulation Settings
              </h3>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                <dt className="text-muted-foreground">Physics preset</dt>
                <dd className="capitalize">{template.simulationTemplate.physicsPreset}</dd>
                <dt className="text-muted-foreground">Entities</dt>
                <dd>{template.simulationTemplate.entityCount}</dd>
                <dt className="text-muted-foreground">Goal types</dt>
                <dd>{template.simulationTemplate.goalTypes.join(", ")}</dd>
              </dl>
            </section>
          )}

          {/* Tags */}
          {template.metadata.tags.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {template.metadata.tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center gap-3 border-t border-border px-6 py-4">
          <button
            onClick={onClose}
            className="flex-1 rounded-lg border border-border px-4 py-2 text-sm text-foreground hover:bg-muted transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={() => {
              onApply(template);
              onClose();
            }}
            className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
          >
            <Sparkles className="h-4 w-4" />
            Use Template
          </button>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

export function TemplatesPage(): React.ReactElement {
  const navigate = useNavigate();
  const setPrefill = useSetAtom(templatePrefillAtom);
  const [search, setSearch] = useState("");
  const [domainFilter, setDomainFilter] = useState<string>("");
  const [typeFilter, setTypeFilter] = useState<ContentType | "">("");
  const [preview, setPreview] = useState<ContentTemplate | null>(null);

  const { data: templates = [], isLoading } = useTemplates(
    domainFilter || typeFilter
      ? {
          domain: domainFilter || undefined,
          contentType: (typeFilter as ContentType) || undefined,
        }
      : undefined,
  );

  const { mutate: deleteTemplate } = useDeleteTemplate();
  const applyTemplate = useApplyTemplate((req) => {
    setPrefill({
      type: req.type,
      subject: req.subject,
      topic: req.topic,
      gradeLevel: req.gradeLevel,
      difficulty: req.difficulty,
      learningObjectives: req.learningObjectives ?? [""],
      additionalInstructions: req.additionalInstructions ?? "",
    });
    navigate("/generate");
  });

  const handleApply = useCallback(
    (template: ContentTemplate) => {
      const topic = window.prompt(
        `Enter the topic for "${template.name}":`,
        "",
      );
      if (topic === null) return; // Cancelled
      applyTemplate(template, topic);
    },
    [applyTemplate],
  );

  const filtered = (templates as ContentTemplate[]).filter(
    (t: ContentTemplate) =>
      !search ||
      t.name.toLowerCase().includes(search.toLowerCase()) ||
      t.domain.toLowerCase().includes(search.toLowerCase()) ||
      t.description.toLowerCase().includes(search.toLowerCase()),
  );

  // Collect unique domains for filter chips
  const domains = Array.from(new Set<string>((templates as ContentTemplate[]).map((t: ContentTemplate) => t.domain))).sort();

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <header className="flex flex-wrap items-center gap-3 border-b border-border px-6 py-4">
        <h1 className="text-base font-semibold text-foreground mr-auto">
          Template Gallery
        </h1>
        {/* Search */}
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            type="search"
            placeholder="Search templates…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-9 rounded-lg border border-border bg-background pl-9 pr-3 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 w-56"
            aria-label="Search templates"
          />
        </div>
        {/* Type filter */}
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as ContentType | "")}
          className="h-9 rounded-lg border border-border bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
          aria-label="Filter by content type"
        >
          <option value="">All types</option>
          {(
            [
              "lesson",
              "quiz",
              "exercise",
              "simulation",
              "flashcard",
            ] as ContentType[]
          ).map((t) => (
            <option key={t} value={t} className="capitalize">
              {t}
            </option>
          ))}
        </select>
        <button className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors">
          <Plus className="h-4 w-4" />
          New Template
        </button>
      </header>

      {/* Domain filter chips */}
      {domains.length > 0 && (
        <div className="flex flex-wrap gap-2 border-b border-border px-6 py-3">
          <button
            onClick={() => setDomainFilter("")}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              !domainFilter
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80"
            }`}
          >
            All domains
          </button>
          {(domains as string[]).map((d: string) => (
            <button
              key={d}
              onClick={() => setDomainFilter(d === domainFilter ? "" : d)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                d === domainFilter
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              {d}
            </button>
          ))}
        </div>
      )}

      {/* Grid */}
      <main className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="h-52 animate-pulse rounded-xl bg-muted"
                aria-hidden
              />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <BookOpen className="h-10 w-10 text-muted-foreground/40 mb-3" />
            <p className="text-sm font-medium text-foreground">No templates found</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Try a different search term or create a new template.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {filtered.map((tpl) => (
              <TemplateCard
                key={tpl.id}
                template={tpl}
                onApply={handleApply}
                onDelete={
                  tpl.id.startsWith("tpl-user-")
                    ? (id) => deleteTemplate(id)
                    : undefined
                }
                onPreview={setPreview}
              />
            ))}
          </div>
        )}
      </main>

      {/* Preview modal */}
      {preview && (
        <TemplatePreviewModal
          template={preview}
          onClose={() => setPreview(null)}
          onApply={handleApply}
        />
      )}
    </div>
  );
}
