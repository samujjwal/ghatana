import React from 'react';
import { AlertTriangle, EyeOff, ShieldAlert, Telescope } from 'lucide-react';
import { cn, cardStyles, textStyles } from '../../lib/theme';

export type UnsupportedSurfaceState = 'temporarily-unavailable' | 'operator-only' | 'not-in-deployment' | 'preview';

interface UnsupportedSurfaceBoundaryProps {
  title: string;
  summary: string;
  details?: string[];
  state: UnsupportedSurfaceState;
  className?: string;
  showTitle?: boolean;
}

const stateCopy: Record<
  UnsupportedSurfaceState,
  { label: string; icon: React.ElementType; panelClassName: string; textClassName: string }
> = {
  'temporarily-unavailable': {
    label: 'Temporarily unavailable',
    icon: AlertTriangle,
    panelClassName: 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200',
    textClassName: 'text-amber-700 dark:text-amber-300',
  },
  'operator-only': {
    label: 'Operator-only surface',
    icon: ShieldAlert,
    panelClassName: 'border-sky-300 bg-sky-50 text-sky-900 dark:border-sky-800 dark:bg-sky-950/40 dark:text-sky-200',
    textClassName: 'text-sky-700 dark:text-sky-300',
  },
  'not-in-deployment': {
    label: 'Unavailable in current deployment',
    icon: EyeOff,
    panelClassName: 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200',
    textClassName: 'text-amber-700 dark:text-amber-300',
  },
  preview: {
    label: 'Preview surface',
    icon: Telescope,
    panelClassName: 'border-blue-300 bg-blue-50 text-blue-900 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-200',
    textClassName: 'text-blue-700 dark:text-blue-300',
  },
};

export function UnsupportedSurfaceBoundary({
  title,
  summary,
  details = [],
  state,
  className,
  showTitle = true,
}: UnsupportedSurfaceBoundaryProps): React.ReactElement {
  const stateMeta = stateCopy[state];
  const Icon = stateMeta.icon;

  return (
    <div className={cn(cardStyles.base, cardStyles.padded, className)}>
      <div className={cn('rounded-lg border p-4', stateMeta.panelClassName)}>
        <div className="flex items-start gap-3">
          <Icon className="mt-0.5 h-5 w-5" />
          <div className="space-y-2">
            <div>
              <p className="text-sm font-semibold uppercase tracking-wide">{stateMeta.label}</p>
              {showTitle && <h2 className={textStyles.h3}>{title}</h2>}
            </div>
            <p className="text-sm leading-6">{summary}</p>
          </div>
        </div>
      </div>

      {details.length > 0 && (
        <div className="mt-4">
          <h3 className={cn(textStyles.h3, 'mb-3')}>Current boundary</h3>
          <ul className={cn('list-disc pl-5 space-y-2 text-sm', stateMeta.textClassName)}>
            {details.map((detail) => (
              <li key={detail}>{detail}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default UnsupportedSurfaceBoundary;