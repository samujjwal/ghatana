import type React from 'react';

interface YappcPageShellProps {
  readonly title: string;
  readonly description?: string;
  readonly actions?: React.ReactNode;
  readonly children: React.ReactNode;
  readonly testId?: string;
}

export function YappcPageShell({
  title,
  description,
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
              <h1 className="text-2xl font-semibold text-fg-muted">{title}</h1>
              {description ? <p className="text-sm text-fg-muted">{description}</p> : null}
            </div>
            {actions ? <div className="shrink-0">{actions}</div> : null}
          </div>
        </header>
        <section className="space-y-6">{children}</section>
      </div>
    </main>
  );
}
