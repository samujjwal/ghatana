/**
 * PageHeader — shared page title and action layout primitive.
 *
 * Products can compose product-specific breadcrumbs, back links, and action
 * buttons around this structure without re-implementing the base spacing,
 * heading, and disclosure layout.
 *
 * @doc.type component
 * @doc.purpose Shared page-level heading primitive for product UIs
 * @doc.layer platform
 * @doc.pattern Molecule
 */
import React from 'react';

function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}

export interface PageHeaderProps {
  readonly title: string;
  readonly description?: string;
  readonly eyebrow?: React.ReactNode;
  readonly actions?: React.ReactNode;
  readonly className?: string;
}

export function PageHeader({
  title,
  description,
  eyebrow,
  actions,
  className,
}: PageHeaderProps): React.ReactElement {
  return (
    <header className={cn('mb-8 flex flex-col gap-3', className)}>
      {eyebrow ? (
        <div className="text-sm text-gray-500 dark:text-gray-400">{eyebrow}</div>
      ) : null}

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-3xl font-bold tracking-tight text-gray-950 dark:text-white">
            {title}
          </h1>
          {description ? (
            <p className="mt-2 text-base text-gray-600 dark:text-gray-300">
              {description}
            </p>
          ) : null}
        </div>

        {actions ? (
          <div className="flex flex-wrap items-center gap-2">{actions}</div>
        ) : null}
      </div>
    </header>
  );
}
