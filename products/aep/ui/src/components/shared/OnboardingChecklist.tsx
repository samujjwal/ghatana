/**
 * OnboardingChecklist — readiness checklist for new AEP tenants.
 *
 * Guides users through first-time setup:
 *   - Authentication configured
 *   - First tenant selected
 *   - First pipeline created
 *   - First agent registered
 *   - Compliance scan run
 *
 * @doc.type component
 * @doc.purpose First-use onboarding and readiness verification
 * @doc.layer frontend
 * @doc.pattern Guided Setup
 */
import React from 'react';
import { CheckCircle2, Circle } from 'lucide-react';
import { Button } from '@ghatana/design-system';

interface ChecklistItem {
  label: string;
  done: boolean;
  action?: { label: string; href: string };
}

interface OnboardingChecklistProps {
  items: ChecklistItem[];
  onDismiss?: () => void;
  className?: string;
}

export function OnboardingChecklist({
  items,
  onDismiss,
  className = '',
}: OnboardingChecklistProps): React.ReactElement {
  const completed = items.filter((i) => i.done).length;
  const total = items.length;

  return (
    <div
      className={[
        'rounded-xl border border-indigo-200 bg-indigo-50 px-5 py-4 dark:border-indigo-900 dark:bg-indigo-950/30',
        className,
      ].join(' ')}
      role="region"
      aria-label="Onboarding checklist"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-indigo-900 dark:text-indigo-200">
          Getting started
        </h2>
        <span className="text-xs font-medium text-indigo-700 dark:text-indigo-400">
          {completed}/{total} complete
        </span>
      </div>

      <ul className="mt-3 space-y-2">
        {items.map((item) => (
          <li key={item.label} className="flex items-start gap-2 text-sm">
            {item.done ? (
              <CheckCircle2 className="h-4 w-4 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" aria-hidden />
            ) : (
              <Circle className="h-4 w-4 text-indigo-400 dark:text-indigo-600 flex-shrink-0 mt-0.5" aria-hidden />
            )}
            <span className={item.done ? 'text-gray-500 dark:text-gray-400 line-through' : 'text-gray-800 dark:text-gray-200'}>
              {item.label}
            </span>
            {!item.done && item.action && (
              <a
                href={item.action.href}
                className="ml-auto text-xs font-medium text-indigo-700 hover:text-indigo-900 dark:text-indigo-400 dark:hover:text-indigo-300"
              >
                {item.action.label} →
              </a>
            )}
          </li>
        ))}
      </ul>

      {onDismiss && completed === total && (
        <div className="mt-3 flex justify-end">
          <Button onClick={onDismiss} variant="text" className="text-xs text-indigo-700 dark:text-indigo-400">
            Dismiss
          </Button>
        </div>
      )}
    </div>
  );
}
