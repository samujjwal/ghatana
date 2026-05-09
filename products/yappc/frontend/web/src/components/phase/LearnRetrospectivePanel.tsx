/**
 * Learn Retrospective Panel
 *
 * Phase-native panel for the Learn phase. Captures lessons learned,
 * classifies their impact, and promotes durable patterns for reuse.
 *
 * @doc.type component
 * @doc.purpose Retrospective and lesson capture surface for the Learn phase
 * @doc.layer product
 * @doc.pattern Phase Panel
 */

import React, { useCallback, useState } from 'react';
import { Button, Card, CardContent } from '@ghatana/design-system';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Textarea } from '../ui/Textarea';
import { useI18n } from '../../i18n/I18nProvider';

export type LessonCategory =
  | 'design'
  | 'implementation'
  | 'testing'
  | 'observability'
  | 'collaboration'
  | 'tooling'
  | 'process'
  | 'other';

export type LessonImpact = 'high' | 'medium' | 'low';

export interface Lesson {
  readonly id: string;
  readonly title: string;
  readonly description: string;
  readonly category: LessonCategory;
  readonly impact: LessonImpact;
  readonly appliedPhase?: string;
  readonly patternId?: string;
  readonly createdAt: string;
  readonly createdBy?: string;
}

export interface ReusablePattern {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly applicableTo: readonly string[];
  readonly sourceUrl?: string;
}

export interface NewLessonDraft {
  readonly title: string;
  readonly description: string;
  readonly category: LessonCategory;
  readonly impact: LessonImpact;
}

export interface LearnRetrospectivePanelProps {
  /** Captured lessons from this project cycle */
  readonly lessons: readonly Lesson[];
  /** Reusable patterns promoted from lessons */
  readonly patterns: readonly ReusablePattern[];
  /** Whether lessons are fully reviewed / retrospective is complete */
  readonly retrospectiveComplete: boolean;
  /** Called to add a new lesson */
  readonly onAddLesson: (draft: NewLessonDraft) => void;
  /** Called to promote a lesson to a reusable pattern */
  readonly onPromotePattern: (lessonId: string) => void;
  /** Called to mark the retrospective as complete */
  readonly onMarkComplete: () => void;
  /** Whether the current user can add lessons */
  readonly canAddLessons?: boolean;
  /** Custom className */
  readonly className?: string;
}

const CATEGORY_LABELS: Record<LessonCategory, string> = {
  design: 'Design',
  implementation: 'Implementation',
  testing: 'Testing',
  observability: 'Observability',
  collaboration: 'Collaboration',
  tooling: 'Tooling',
  process: 'Process',
  other: 'Other',
};

const IMPACT_STYLE: Record<LessonImpact, { label: string; className: string }> = {
  high: { label: 'High impact', className: 'bg-destructive-bg text-destructive border-destructive-border' },
  medium: { label: 'Medium impact', className: 'bg-warning-bg text-warning-color border-warning-border' },
  low: { label: 'Low impact', className: 'bg-info-bg text-info-color border-info-border' },
};

const EMPTY_DRAFT: NewLessonDraft = {
  title: '',
  description: '',
  category: 'implementation',
  impact: 'medium',
};

/**
 * Learn Retrospective Panel
 *
 * Provides structured retrospective capture with:
 * - Lesson list with category, impact, and phase context
 * - Pattern promotion (lesson → reusable pattern)
 * - New lesson form with category and impact classification
 * - Retrospective completion gate
 */
export const LearnRetrospectivePanel: React.FC<LearnRetrospectivePanelProps> = ({
  lessons,
  patterns,
  retrospectiveComplete,
  onAddLesson,
  onPromotePattern,
  onMarkComplete,
  canAddLessons = true,
  className = '',
}) => {
  const { t } = useI18n();
  const [showForm, setShowForm] = useState(false);
  const [draft, setDraft] = useState<NewLessonDraft>(EMPTY_DRAFT);
  const [formError, setFormError] = useState<string | null>(null);

  const highImpactLessons = lessons.filter((l) => l.impact === 'high');

  const handleToggleForm = useCallback(() => {
    setShowForm((prev) => !prev);
    setDraft(EMPTY_DRAFT);
    setFormError(null);
  }, []);

  const handleFieldChange = useCallback(
    <K extends keyof NewLessonDraft>(field: K, value: NewLessonDraft[K]) => {
      setDraft((prev) => ({ ...prev, [field]: value }));
    },
    [],
  );

  const handleSubmit = useCallback(
    (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      if (draft.title.trim().length === 0) {
        setFormError('Title is required.');
        return;
      }
      if (draft.description.trim().length === 0) {
        setFormError('Description is required.');
        return;
      }
      onAddLesson({
        title: draft.title.trim(),
        description: draft.description.trim(),
        category: draft.category,
        impact: draft.impact,
      });
      setDraft(EMPTY_DRAFT);
      setShowForm(false);
      setFormError(null);
    },
    [draft, onAddLesson],
  );

  return (
    <section
      className={`learn-retrospective-panel space-y-6 ${className}`}
      aria-label={t('phase.learn.panel')}
      data-testid="learn-retrospective-panel"
    >
      {/* Status Header */}
      <Card variant="outlined">
        <CardContent className="p-5">
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div>
              <h3 className="text-base font-semibold text-fg">Retrospective</h3>
              <p className="text-sm text-fg-muted mt-0.5">
                {lessons.length} lesson{lessons.length !== 1 ? 's' : ''} captured ·{' '}
                {patterns.length} pattern{patterns.length !== 1 ? 's' : ''} promoted
              </p>
            </div>
            <span
              className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${
                retrospectiveComplete
                  ? 'bg-success-bg border-success-border text-success-color'
                  : 'bg-surface-muted border-border text-fg-muted'
              }`}
            >
              {retrospectiveComplete ? 'Complete' : 'In progress'}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* High-impact callout */}
      {highImpactLessons.length > 0 && (
        <div className="rounded-lg border border-warning-border bg-warning-bg p-3">
          <p className="text-sm text-warning-color font-medium">
            {highImpactLessons.length} high-impact lesson{highImpactLessons.length !== 1 ? 's' : ''} captured — consider promoting to reusable patterns.
          </p>
        </div>
      )}

      {/* Lessons List */}
      <section aria-label={t('phase.learn.capturedLessons')}>
        <div className="flex items-center justify-between mb-3">
          <h4 className="text-sm font-medium text-fg">Lessons ({lessons.length})</h4>
          {canAddLessons && !retrospectiveComplete && (
            <Button variant="outline" size="sm" onClick={handleToggleForm} aria-expanded={showForm}>
              {showForm ? 'Cancel' : 'Add lesson'}
            </Button>
          )}
        </div>

        {showForm && (
          <Card variant="outlined" className="mb-4">
            <CardContent className="p-4">
              <form onSubmit={handleSubmit} noValidate aria-label={t('phase.learn.addNewLesson')}>
                <div className="space-y-4">
                  <div>
                    <label htmlFor="lesson-title" className="block text-sm font-medium text-fg mb-1">
                      Title <span aria-hidden="true" className="text-destructive">*</span>
                    </label>
                    <Input
                      id="lesson-title"
                      type="text"
                      value={draft.title}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        handleFieldChange('title', e.target.value)
                      }
                      className="w-full rounded-md border border-border bg-surface text-fg text-sm px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-ring"
                      placeholder={t('phase.learn.lessonTitlePlaceholder')}
                      required
                      maxLength={200}
                    />
                  </div>
                  <div>
                    <label htmlFor="lesson-description" className="block text-sm font-medium text-fg mb-1">
                      Description <span aria-hidden="true" className="text-destructive">*</span>
                    </label>
                    <Textarea
                      id="lesson-description"
                      value={draft.description}
                      onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) =>
                        handleFieldChange('description', e.target.value)
                      }
                      className="w-full rounded-md border border-border bg-surface text-fg text-sm px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-ring resize-y min-h-[80px]"
                      placeholder={t('phase.learn.lessonDescriptionPlaceholder')}
                      required
                      maxLength={2000}
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label htmlFor="lesson-category" className="block text-sm font-medium text-fg mb-1">
                        Category
                      </label>
                      <Select
                        id="lesson-category"
                        value={draft.category}
                        onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                          handleFieldChange('category', e.target.value as LessonCategory)
                        }
                        className="w-full rounded-md border border-border bg-surface text-fg text-sm px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-ring"
                      >
                        {(Object.keys(CATEGORY_LABELS) as LessonCategory[]).map((cat) => (
                          <option key={cat} value={cat}>
                            {CATEGORY_LABELS[cat]}
                          </option>
                        ))}
                      </Select>
                    </div>
                    <div>
                      <label htmlFor="lesson-impact" className="block text-sm font-medium text-fg mb-1">
                        Impact
                      </label>
                      <Select
                        id="lesson-impact"
                        value={draft.impact}
                        onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                          handleFieldChange('impact', e.target.value as LessonImpact)
                        }
                        className="w-full rounded-md border border-border bg-surface text-fg text-sm px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-ring"
                      >
                        {(Object.keys(IMPACT_STYLE) as LessonImpact[]).map((imp) => (
                          <option key={imp} value={imp}>
                            {IMPACT_STYLE[imp].label}
                          </option>
                        ))}
                      </Select>
                    </div>
                  </div>
                  {formError && (
                    <p role="alert" className="text-sm text-destructive">
                      {formError}
                    </p>
                  )}
                  <div className="flex gap-2">
                    <Button variant="solid" type="submit">
                      Save lesson
                    </Button>
                    <Button variant="outline" type="button" onClick={handleToggleForm}>
                      Cancel
                    </Button>
                  </div>
                </div>
              </form>
            </CardContent>
          </Card>
        )}

        {lessons.length === 0 ? (
          <div className="rounded-lg border border-border bg-surface-muted p-6 text-center">
            <p className="text-sm text-fg-muted">No lessons captured yet.</p>
            <p className="text-xs text-fg-muted mt-1">Add the first lesson to start the retrospective.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {lessons.map((lesson) => {
              const impactStyle = IMPACT_STYLE[lesson.impact];
              return (
                <Card key={lesson.id} variant="outlined">
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="text-sm font-medium text-fg">{lesson.title}</p>
                          <span
                            className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs ${impactStyle.className}`}
                          >
                            {impactStyle.label}
                          </span>
                          <span className="text-xs text-fg-muted">
                            {CATEGORY_LABELS[lesson.category]}
                          </span>
                        </div>
                        <p className="text-xs text-fg-muted mt-1">{lesson.description}</p>
                        <p className="text-xs text-fg-muted mt-1">
                          <time dateTime={lesson.createdAt}>
                            {new Date(lesson.createdAt).toLocaleDateString()}
                          </time>
                          {lesson.createdBy && ` · ${lesson.createdBy}`}
                          {lesson.appliedPhase && ` · Phase: ${lesson.appliedPhase}`}
                        </p>
                      </div>
                      {!lesson.patternId && lesson.impact === 'high' && !retrospectiveComplete && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onPromotePattern(lesson.id)}
                          aria-label={`Promote lesson "${lesson.title}" to reusable pattern`}
                        >
                          Promote
                        </Button>
                      )}
                      {lesson.patternId && (
                        <span className="text-xs text-success-color font-medium flex-shrink-0">
                          Pattern
                        </span>
                      )}
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </section>

      {/* Promoted Patterns */}
      {patterns.length > 0 && (
        <section aria-label={t('phase.learn.reusablePatterns')}>
          <h4 className="text-sm font-medium text-fg mb-3">
            Reusable patterns ({patterns.length})
          </h4>
          <div className="space-y-2">
            {patterns.map((pattern) => (
              <Card key={pattern.id} variant="outlined">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-fg">{pattern.name}</p>
                      <p className="text-xs text-fg-muted mt-0.5">{pattern.description}</p>
                      {pattern.applicableTo.length > 0 && (
                        <div className="flex flex-wrap gap-1 mt-2">
                          {pattern.applicableTo.map((tag) => (
                            <span
                              key={tag}
                              className="text-xs bg-surface-muted border border-border text-fg-muted rounded px-1.5 py-0.5"
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                    {pattern.sourceUrl && (
                      <a
                        href={pattern.sourceUrl}
                        target="_blank"
                        rel="noreferrer noopener"
                        className="text-xs text-info-color hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-ring flex-shrink-0"
                        aria-label={`View pattern ${pattern.name}`}
                      >
                        View →
                      </a>
                    )}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      )}

      {/* Retrospective Complete Gate */}
      {!retrospectiveComplete && lessons.length > 0 && (
        <section aria-label={t('phase.learn.completeRetrospective')}>
          <Button
            variant="solid"
            onClick={onMarkComplete}
            aria-label={t('phase.learn.markComplete')}
            className="w-full sm:w-auto"
          >
            Mark retrospective complete
          </Button>
        </section>
      )}
    </section>
  );
};

export default LearnRetrospectivePanel;
