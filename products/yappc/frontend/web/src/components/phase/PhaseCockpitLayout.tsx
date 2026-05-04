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
  /** Additional content slots */
  children?: ReactNode;
  /** Custom className */
  className?: string;
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
  children,
  className = '',
}) => {
  const [showGovernance, setShowGovernance] = React.useState(false);
  const [showAdvanced, setShowAdvanced] = React.useState(false);

  return (
    <div className={`phase-cockpit-layout ${className}`}>
      {/* Phase Header */}
      <header className="mb-6">
        <div className="flex items-center gap-3 mb-2">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">
            {phaseName}
          </h1>
        </div>
        <p className="text-gray-600 dark:text-gray-400">
          {phaseDescription}
        </p>
      </header>

      {/* Primary Action - Always Visible */}
      <section className="mb-8">
        {primaryAction}
      </section>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        {/* Left Column */}
        <div className="space-y-6">
          {blockers && (
            <section>
              {blockers}
            </section>
          )}
          {evidence && (
            <section>
              {evidence}
            </section>
          )}
        </div>

        {/* Right Column */}
        <div className="space-y-6">
          {suggestedAutomation && (
            <section>
              {suggestedAutomation}
            </section>
          )}
          {children && (
            <section>
              {children}
            </section>
          )}
        </div>
      </div>

      {/* Collapsible Governance Trace */}
      {governanceTrace && (
        <section className="mb-6 border-t border-gray-200 dark:border-gray-700 pt-6">
          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer list-none">
              <h2 className="text-sm font-medium text-gray-700 dark:text-gray-300">
                Governance Trace
              </h2>
              <span className="transform group-open:rotate-180 transition-transform">
                ▼
              </span>
            </summary>
            <div className="mt-4">
              {governanceTrace}
            </div>
          </details>
        </section>
      )}

      {/* Collapsible Advanced Tools */}
      {advancedTools && (
        <section className="border-t border-gray-200 dark:border-gray-700 pt-6">
          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer list-none">
              <h2 className="text-sm font-medium text-gray-700 dark:text-gray-300">
                Advanced Tools
              </h2>
              <span className="transform group-open:rotate-180 transition-transform">
                ▼
              </span>
            </summary>
            <div className="mt-4">
              {advancedTools}
            </div>
          </details>
        </section>
      )}
    </div>
  );
};

export default PhaseCockpitLayout;
