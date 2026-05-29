import type React from 'react';

interface YappcPageShellProps {
  readonly title: string;
  readonly description?: string;
  readonly eyebrow?: string;
  readonly actions?: React.ReactNode;
  readonly children: React.ReactNode;
  readonly testId?: string;
}

export function YappcPageShell({
  title,
  description,
  eyebrow,
  actions,
  children,
  testId,
}: YappcPageShellProps): React.ReactNode {
  return (
    <main className="min-h-screen bg-surface" data-testid={testId}>
      <div className="mx-auto w-full max-w-7xl space-y-6 px-6 py-6">
        <header className="space-y-2">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1">
              {eyebrow ? <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted" data-testid="page-eyebrow">{eyebrow}</p> : null}
              <h1 className="text-2xl font-semibold text-fg" data-testid="page-title">{title}</h1>
              {description ? <p className="text-sm text-fg-muted" data-testid="page-description">{description}</p> : null}
            </div>
            {actions ? <div className="shrink-0">{actions}</div> : null}
          </div>
        </header>
        <section className="space-y-6">{children}</section>
      </div>
    </main>
  );
}
