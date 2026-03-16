import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft, Download, Star, ExternalLink, Loader2,
  BookOpen, GraduationCap, BarChart2, Tag, Bot,
} from "lucide-react";
import type { ContentType, DifficultyLevel } from "@/types/content";
import { exportContent } from "@/api/contentApi";
import { useContentDetail } from "@/hooks/useContent";

const DIFFICULTY_COLORS: Record<DifficultyLevel, string> = {
  beginner: "bg-green-100 text-green-700",
  intermediate: "bg-blue-100 text-blue-700",
  advanced: "bg-orange-100 text-orange-700",
  expert: "bg-red-100 text-red-700",
};

const TYPE_LABELS: Record<ContentType, string> = {
  lesson: "Lesson",
  quiz: "Quiz",
  exercise: "Exercise",
  explanation: "Explanation",
  summary: "Summary",
  flashcard: "Flashcard",
  simulation: "Simulation",
};

async function downloadExport(id: string, format: "pdf" | "html" | "json", title: string) {
  const blob = await exportContent(id, format);
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${title.replace(/\s+/g, "_")}.${format}`;
  a.click();
  URL.revokeObjectURL(url);
}

function MetaBadge({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${className ?? "bg-muted text-muted-foreground"}`}>
      {children}
    </span>
  );
}

function QualityBar({ label, value }: { label: string; value: number }) {
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-medium">{Math.round(value * 100)}%</span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-primary transition-all"
          style={{ width: `${value * 100}%` }}
        />
      </div>
    </div>
  );
}

export function ViewerPage() {
  const { id } = useParams<{ id: string }>();
  const { data: content, isLoading, error } = useContentDetail(id ?? "");

  if (!id) {
    return <p className="p-6 text-sm text-destructive">Content ID is missing.</p>;
  }

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error || !content) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3">
        <p className="text-sm text-destructive">
          {(error as Error)?.message ?? "Content not found."}
        </p>
        <Link to="/explore" className="text-xs text-primary hover:underline">
          Back to explore
        </Link>
      </div>
    );
  }

  const qr = content.qualityReport;

  return (
    <div className="flex flex-1 gap-0 overflow-hidden">
      {/* Main content area */}
      <div className="flex flex-1 flex-col overflow-y-auto">
        {/* Header */}
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-border bg-card/80 px-6 py-3 backdrop-blur">
          <Link
            to="/explore"
            className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden />
            Explore
          </Link>
          <div className="flex items-center gap-2">
            {(["html", "pdf", "json"] as const).map((fmt) => (
              <button
                key={fmt}
                onClick={() => downloadExport(id, fmt, content.title)}
                className="flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs hover:bg-muted"
              >
                <Download className="h-3.5 w-3.5" aria-hidden />
                {fmt.toUpperCase()}
              </button>
            ))}
          </div>
        </div>

        {/* Content */}
        <div className="mx-auto w-full max-w-3xl px-6 py-8">
          {/* Meta badges */}
          <div className="mb-4 flex flex-wrap gap-2">
            <MetaBadge className="bg-primary/10 text-primary">
              <BookOpen className="h-3 w-3" aria-hidden />
              {TYPE_LABELS[content.type] ?? content.type}
            </MetaBadge>
            <MetaBadge className={DIFFICULTY_COLORS[content.difficulty]}>
              {content.difficulty}
            </MetaBadge>
            <MetaBadge>
              <GraduationCap className="h-3 w-3" aria-hidden />
              {content.gradeLevel}
            </MetaBadge>
            <MetaBadge>
              <BarChart2 className="h-3 w-3" aria-hidden />
              {content.subject}
            </MetaBadge>
            {content.aiGenerated && (
              <MetaBadge className="bg-violet-100 text-violet-700">
                <Bot className="h-3 w-3" aria-hidden />
                AI Generated
              </MetaBadge>
            )}
          </div>

          <h1 className="mb-2 text-2xl font-bold">{content.title}</h1>

          {content.description && (
            <p className="mb-6 text-muted-foreground">{content.description}</p>
          )}

          {/* Learning objectives */}
          {content.learningObjectives.length > 0 && (
            <div className="mb-6 rounded-lg border border-border bg-muted/40 p-4">
              <h2 className="mb-2 text-sm font-semibold">Learning Objectives</h2>
              <ul className="list-disc space-y-1 pl-4">
                {content.learningObjectives.map((obj, i) => (
                  <li key={i} className="text-sm text-muted-foreground">{obj}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Body */}
          <div className="prose prose-sm max-w-none leading-relaxed text-foreground">
            {content.body.split("\n").map((line, i) => (
              <p key={i}>{line}</p>
            ))}
          </div>

          {/* Tags */}
          {content.tags.length > 0 && (
            <div className="mt-6 flex flex-wrap gap-1.5">
              {content.tags.map((tag) => (
                <span
                  key={tag}
                  className="flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground"
                >
                  <Tag className="h-3 w-3" aria-hidden />
                  {tag}
                </span>
              ))}
            </div>
          )}

          {/* Related content */}
          {(content.relatedContentIds?.length ?? 0) > 0 && (
            <div className="mt-8">
              <h2 className="mb-3 text-sm font-semibold">Related Content</h2>
              <div className="flex flex-wrap gap-2">
                {content.relatedContentIds.map((rid) => (
                  <Link
                    key={rid}
                    to={`/view/${rid}`}
                    className="flex items-center gap-1.5 rounded-md border border-border px-3 py-1 text-xs hover:bg-muted"
                  >
                    <ExternalLink className="h-3 w-3" aria-hidden />
                    {rid}
                  </Link>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Sidebar: quality report */}
      {qr && (
        <div className="hidden w-64 shrink-0 overflow-y-auto border-l border-border bg-card p-4 xl:flex xl:flex-col">
          <h2 className="mb-4 flex items-center gap-1.5 text-sm font-semibold">
            <Star className="h-4 w-4 text-yellow-500" aria-hidden />
            Quality Report
          </h2>

          <div className="mb-4 flex items-center justify-center">
            <div className="relative flex h-20 w-20 items-center justify-center rounded-full border-4 border-primary">
              <span className="text-xl font-bold">{Math.round(qr.overallScore * 100)}</span>
              <span className="text-xs text-muted-foreground">/100</span>
            </div>
          </div>

          <div className="space-y-3">
            <QualityBar label="Accuracy" value={qr.dimensions.accuracy} />
            <QualityBar label="Clarity" value={qr.dimensions.clarity} />
            <QualityBar label="Engagement" value={qr.dimensions.engagement} />
            <QualityBar label="Grade Appropriateness" value={qr.dimensions.gradeAppropriateness} />
            <QualityBar label="Curriculum Alignment" value={qr.dimensions.curriculumAlignment} />
          </div>

          {qr.feedback?.strengths && qr.feedback.strengths.length > 0 && (
            <div className="mt-4">
              <p className="mb-1 text-xs font-medium text-green-600">Strengths</p>
              <ul className="list-disc space-y-0.5 pl-4">
                {qr.feedback.strengths.map((s, i) => (
                  <li key={i} className="text-xs text-muted-foreground">{s}</li>
                ))}
              </ul>
            </div>
          )}

          {qr.feedback?.improvements && qr.feedback.improvements.length > 0 && (
            <div className="mt-3">
              <p className="mb-1 text-xs font-medium text-orange-600">Improvements</p>
              <ul className="list-disc space-y-0.5 pl-4">
                {qr.feedback.improvements.map((s, i) => (
                  <li key={i} className="text-xs text-muted-foreground">{s}</li>
                ))}
              </ul>
            </div>
          )}

          <Link
            to="/quality"
            className="mt-6 block text-center text-xs text-primary hover:underline"
          >
            View review queue →
          </Link>
        </div>
      )}
    </div>
  );
}
