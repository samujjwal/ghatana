/**
 * Phase Cockpit Layout Component
 *
 * Reusable layout for phase-specific cockpits with consistent structure:
 * 1. Current phase purpose
 * 2. One primary next action
 * 3. Current blockers
 * 4. Evidence supporting the next action
 * 5. Suggested automation
 * 6. Governance/provenance on demand
 * 7. Advanced tools collapsed by default
 *
 * @doc.type component
 * @doc.purpose Layout shell for phase-specific cockpits
 * @doc.layer product
 * @doc.pattern Layout Component
 */

import React, { ReactNode } from 'react';

import { Button, Card, CardContent } from '@ghatana/design-system';

export interface PhaseCockpitLayoutProps {
  /** Current phase name */
  phaseName: string;
  /** Phase description/purpose */
  phaseDescription: string;
  /** Primary action card content */
  primaryAction: ReactNode;
  /** Blocker panel content (optional) */
  blockers?: ReactNode;
  /** Evidence panel content (optional) */
  evidence?: ReactNode;
  /** Suggested automation content (optional) */
  suggestedAutomation?: ReactNode;
  /** Governance trace content (optional, collapsed by default) */
  governanceTrace?: ReactNode;
  /** Advanced tools content (optional, collapsed by default) */
  advancedTools?: ReactNode;
  /** Title for the native phase details panel */
  supportingTitle?: string;
  /** Label for the advanced tools disclosure */
  advancedToolsLabel?: string;
  /** Helper text explaining when/why to open advanced tools */
  advancedToolsDescription?: string;
  /** Additional content slots */
  children?: ReactNode;
  /** Custom className */
  className?: string;
  /** Optional test id for the mounted cockpit */
  testId?: string;
}

/**
 * Phase Cockpit Layout Component
 *
 * Provides a consistent layout structure for all phase cockpits with:
 * - Header with phase name and description
 * - Primary action section (always visible)
 * - Collapsible panels for blockers, evidence, automation, governance, and advanced tools
 * - Responsive grid layout
 */
export const PhaseCockpitLayout: React.FC<PhaseCockpitLayoutProps> = ({
  phaseName,
  phaseDescription,
  primaryAction,
  blockers,
  evidence,
  suggestedAutomation,
  governanceTrace,
  advancedTools,
  supportingTitle = 'Details',
  advancedToolsLabel = 'Open supporting workspace',
  advancedToolsDescription = 'Open this panel only when the phase summary does not provide enough context for the next decision.',
  children,
  className = '',
  testId,
}) => {
  const [governanceOpen, setGovernanceOpen] = React.useState(false);
  const [advancedOpen, setAdvancedOpen] = React.useState(false);

  return (
    <div
      className={`phase-cockpit-layout ${className}`}
      data-testid={testId}
      role="region"
      aria-label={`${phaseName} cockpit`}
    >
      {/* Phase Header */}
      <header className="mb-6" data-testid="phase-purpose">
        <div className="flex items-center gap-3 mb-2">
          <h1 className="text-2xl font-semibold text-fg dark:text-fg-muted">
            {phaseName}
          </h1>
        </div>
        <p className="text-fg-muted dark:text-fg-muted">
          {phaseDescription}
        </p>
      </header>

      {/* Primary Action - Always Visible */}
      <section className="mb-8" data-testid="primary-next-action">
        {primaryAction}
      </section>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        {/* Left Column */}
        <div className="space-y-6">
          {blockers && (
            <section data-testid="blockers-panel">
              {blockers}
            </section>
          )}
          {evidence && (
            <section data-testid="evidence-panel">
              {evidence}
            </section>
          )}
        </div>

        {/* Right Column */}
        <div className="space-y-6">
          {suggestedAutomation && (
            <section data-testid="suggested-automation-panel">
              {suggestedAutomation}
            </section>
          )}
          {children && (
            <section aria-label={supportingTitle} data-testid="supporting-surface-panel">
              <Card variant="outlined">
                <CardContent className="space-y-4 p-4">
                  <div>
                    <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-fg-muted">
                      {supportingTitle}
                    </h2>
                    <p className="mt-2 text-sm text-fg-muted">
                      Review the current phase summary first; open the supporting workspace only when you need deeper context.
                    </p>
                  </div>
                  {children}
                </CardContent>
              </Card>
            </section>
          )}
        </div>
      </div>

      {governanceTrace && (
        <section
          className="mb-6 border-t border-border dark:border-border pt-6"
          data-testid="governance-trace-panel"
        >
          <Button
            variant="ghost"
            className="w-full justify-between px-0"
            aria-expanded={governanceOpen}
            onClick={() => setGovernanceOpen((value) => !value)}
          >
            <span>Governance Trace</span>
            <span aria-hidden="true" className={`transition-transform ${governanceOpen ? 'rotate-180' : ''}`}>
              ▼
            </span>
          </Button>
          {governanceOpen && (
            <Card variant="outlined" className="mt-4">
              <CardContent className="p-4">{governanceTrace}</CardContent>
            </Card>
          )}
        </section>
      )}

      {advancedTools && (
        <section
          className="border-t border-border dark:border-border pt-6"
          data-testid="advanced-tools-panel"
        >
          <Button
            variant="ghost"
            className="w-full justify-between px-0"
            aria-expanded={advancedOpen}
            onClick={() => setAdvancedOpen((value) => !value)}
          >
            <span>{advancedToolsLabel}</span>
            <span aria-hidden="true" className={`transition-transform ${advancedOpen ? 'rotate-180' : ''}`}>
              ▼
            </span>
          </Button>
          <p className="mt-1 text-sm text-fg-muted" data-testid="advanced-tools-description">
            {advancedToolsDescription}
          </p>
          {advancedOpen && (
            <Card variant="outlined" className="mt-4">
              <CardContent className="p-4">{advancedTools}</CardContent>
            </Card>
          )}
        </section>
      )}
    </div>
  );
};

export default PhaseCockpitLayout;
