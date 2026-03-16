import React, { useState, useEffect } from "react";
import { useAtom, useAtomValue, useSetAtom } from "jotai";
import { Sparkles, Plus, Minus, Loader2, CheckCircle2, XCircle } from "lucide-react";
import type { ContentType, DifficultyLevel } from "@/types/content";
import { generationFormAtom, templatePrefillAtom } from "@/stores/explorerStore";
import { useStartGeneration, useActiveGenerationJob, useRecentGenerationJobs } from "@/hooks/useContentGeneration";

const CONTENT_TYPES: ContentType[] = [
  "lesson", "quiz", "exercise", "explanation", "summary", "flashcard", "simulation",
];
const DIFFICULTIES: DifficultyLevel[] = ["beginner", "intermediate", "advanced", "expert"];
const GRADE_LEVELS = [
  "Grade K", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5",
  "Grade 6", "Grade 7", "Grade 8", "Grade 9", "Grade 10", "Grade 11",
  "Grade 12", "Undergraduate", "Graduate",
];

function FormField({ label, children, required }: { label: string; children: React.ReactNode; required?: boolean }) {
  return (
    <div className="space-y-1.5">
      <label className="text-sm font-medium text-foreground">
        {label}
        {required && <span className="ml-1 text-destructive">*</span>}
      </label>
      {children}
    </div>
  );
}

function Select({ value, onChange, options }: {
  value: string;
  onChange: (v: string) => void;
  options: string[];
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
    >
      {options.map((o) => (
        <option key={o} value={o}>{o}</option>
      ))}
    </select>
  );
}

export function GeneratePage() {
  const [form, setForm] = useAtom(generationFormAtom);
  const prefill = useAtomValue(templatePrefillAtom);
  const clearPrefill = useSetAtom(templatePrefillAtom);
  const { mutate: startGen, isPending } = useStartGeneration();
  const { job, activeJobId } = useActiveGenerationJob();
  const { data: recentJobs } = useRecentGenerationJobs();
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Apply template prefill once on mount (or when prefill changes)
  useEffect(() => {
    if (!prefill) return;
    setForm((prev) => ({ ...prev, ...prefill }));
    clearPrefill(null);
  }, [prefill, setForm, clearPrefill]);

  function updateObjective(index: number, value: string) {
    const updated = [...form.learningObjectives];
    updated[index] = value;
    setForm((prev) => ({ ...prev, learningObjectives: updated }));
  }

  function addObjective() {
    setForm((prev) => ({ ...prev, learningObjectives: [...prev.learningObjectives, ""] }));
  }

  function removeObjective(index: number) {
    setForm((prev) => ({
      ...prev,
      learningObjectives: prev.learningObjectives.filter((_, i) => i !== index),
    }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitError(null);
    const objectives = form.learningObjectives.filter((o) => o.trim() !== "");
    if (!form.subject.trim() || !form.topic.trim() || objectives.length === 0) {
      setSubmitError("Please fill in Subject, Topic, and at least one Learning Objective.");
      return;
    }
    startGen(
      { ...form, learningObjectives: objectives },
      { onError: (err) => setSubmitError((err as Error).message) },
    );
  }

  return (
    <div className="flex flex-1 gap-6 overflow-hidden p-6">
      {/* Generation form */}
      <div className="flex w-[480px] shrink-0 flex-col overflow-y-auto">
        <div className="mb-6 flex items-center gap-2">
          <Sparkles className="h-5 w-5 text-primary" aria-hidden />
          <h1 className="text-lg font-semibold">Generate Content</h1>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          <FormField label="Content type" required>
            <Select
              value={form.type}
              onChange={(v) => setForm((p) => ({ ...p, type: v as ContentType }))}
              options={CONTENT_TYPES}
            />
          </FormField>

          <FormField label="Subject" required>
            <input
              type="text"
              value={form.subject}
              onChange={(e) => setForm((p) => ({ ...p, subject: e.target.value }))}
              placeholder="e.g. Mathematics, Biology, History…"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            />
          </FormField>

          <FormField label="Topic" required>
            <input
              type="text"
              value={form.topic}
              onChange={(e) => setForm((p) => ({ ...p, topic: e.target.value }))}
              placeholder="e.g. Quadratic equations, Photosynthesis…"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            />
          </FormField>

          <div className="grid grid-cols-2 gap-4">
            <FormField label="Grade level" required>
              <Select
                value={form.gradeLevel}
                onChange={(v) => setForm((p) => ({ ...p, gradeLevel: v }))}
                options={GRADE_LEVELS}
              />
            </FormField>
            <FormField label="Difficulty" required>
              <Select
                value={form.difficulty}
                onChange={(v) => setForm((p) => ({ ...p, difficulty: v as DifficultyLevel }))}
                options={DIFFICULTIES}
              />
            </FormField>
          </div>

          <FormField label="Learning objectives" required>
            <div className="space-y-2">
              {form.learningObjectives.map((obj, i) => (
                <div key={i} className="flex gap-2">
                  <input
                    type="text"
                    value={obj}
                    onChange={(e) => updateObjective(i, e.target.value)}
                    placeholder={`Objective ${i + 1}…`}
                    className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                  {form.learningObjectives.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeObjective(i)}
                      className="rounded-md p-2 text-muted-foreground hover:text-destructive"
                      aria-label="Remove objective"
                    >
                      <Minus className="h-4 w-4" />
                    </button>
                  )}
                </div>
              ))}
              <button
                type="button"
                onClick={addObjective}
                className="flex items-center gap-1.5 text-xs text-primary hover:underline"
              >
                <Plus className="h-3.5 w-3.5" aria-hidden />
                Add objective
              </button>
            </div>
          </FormField>

          <FormField label="Additional instructions">
            <textarea
              value={form.additionalInstructions}
              onChange={(e) => setForm((p) => ({ ...p, additionalInstructions: e.target.value }))}
              placeholder="Any specific requirements, tone, examples to include…"
              rows={3}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            />
          </FormField>

          {submitError && (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {submitError}
            </p>
          )}

          <button
            type="submit"
            disabled={isPending || activeJobId !== null}
            className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
          >
            {isPending ? (
              <><Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Starting…</>
            ) : activeJobId ? (
              <><Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Generating…</>
            ) : (
              <><Sparkles className="h-4 w-4" aria-hidden /> Generate</>
            )}
          </button>
        </form>
      </div>

      {/* Active job progress + recent jobs */}
      <div className="flex flex-1 flex-col gap-6 overflow-y-auto">
        {/* Active job */}
        {job && (
          <div className="rounded-lg border border-border bg-card p-4">
            <h2 className="mb-3 text-sm font-semibold">Active Generation</h2>
            <div className="mb-2 flex items-center gap-2">
              {job.status === "completed" ? (
                <CheckCircle2 className="h-4 w-4 text-green-500" aria-hidden />
              ) : job.status === "failed" ? (
                <XCircle className="h-4 w-4 text-destructive" aria-hidden />
              ) : (
                <Loader2 className="h-4 w-4 animate-spin text-primary" aria-hidden />
              )}
              <span className="text-sm capitalize">{job.status}</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-primary transition-all duration-500"
                style={{ width: `${job.progress}%` }}
              />
            </div>
            <p className="mt-1 text-right text-xs text-muted-foreground">{job.progress}%</p>
            {job.error && (
              <p className="mt-2 text-xs text-destructive">{job.error}</p>
            )}
          </div>
        )}

        {/* Recent jobs */}
        <div className="rounded-lg border border-border bg-card p-4">
          <h2 className="mb-3 text-sm font-semibold">Recent Generations</h2>
          {!recentJobs?.length ? (
            <p className="text-xs text-muted-foreground">No recent generations</p>
          ) : (
            <ul className="divide-y divide-border">
              {recentJobs.slice(0, 10).map((j) => (
                <li key={j.jobId} className="flex items-center justify-between py-2">
                  <div>
                    <p className="text-xs font-medium">{j.request.topic}</p>
                    <p className="text-xs text-muted-foreground">
                      {j.request.type} · {j.request.subject}
                    </p>
                  </div>
                  <span
                    className={
                      j.status === "completed"
                        ? "text-xs text-green-600"
                        : j.status === "failed"
                          ? "text-xs text-destructive"
                          : "text-xs text-blue-600"
                    }
                  >
                    {j.status}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
