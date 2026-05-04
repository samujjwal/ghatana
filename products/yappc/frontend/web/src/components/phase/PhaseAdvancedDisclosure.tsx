/**
 * Phase Advanced Disclosure Component
 *
 * Collapsible panel for advanced tools and configuration.
 * Advanced tools are collapsed by default to reduce cognitive load.
 *
 * @doc.type component
 * @doc.purpose Collapsible advanced tools panel for phase cockpits
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React, { ReactNode } from 'react';

export interface AdvancedSection {
  id: string;
  title: string;
  content: ReactNode;
  icon?: ReactNode;
}

export interface PhaseAdvancedDisclosureProps {
  /** Advanced tool sections */
  sections: AdvancedSection[];
  /** Default open state */
  defaultOpen?: boolean;
  /** Custom className */
  className?: string;
}

/**
 * Phase Advanced Disclosure Component
 *
 * Displays advanced tools with:
 * - Collapsible sections
 * - Optional icons
 * - Multiple sections support
 * - Default collapsed state
 */
export const PhaseAdvancedDisclosure: React.FC<PhaseAdvancedDisclosureProps> = ({
  sections,
  defaultOpen = false,
  className = '',
}) => {
  const [openSections, setOpenSections] = React.useState<Set<string>>(
    defaultOpen ? new Set(sections.map(s => s.id)) : new Set()
  );

  const toggleSection = (id: string) => {
    setOpenSections(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  if (sections.length === 0) {
    return null;
  }

  return (
    <div className={`phase-advanced-disclosure ${className}`}>
      {sections.map((section) => {
        const isOpen = openSections.has(section.id);
        return (
          <details
            key={section.id}
            open={isOpen}
            onToggle={(e) => {
              // Prevent default to control state manually
              e.preventDefault();
              toggleSection(section.id);
            }}
            className="group border border-gray-200 dark:border-gray-700 rounded-lg mb-3 last:mb-0"
          >
            <summary className="flex items-center justify-between p-4 cursor-pointer list-none hover:bg-gray-50 dark:hover:bg-gray-900/30 transition-colors">
              <div className="flex items-center gap-3">
                {section.icon && (
                  <span className="text-lg" aria-hidden="true">
                    {section.icon}
                  </span>
                )}
                <h4 className="font-medium text-gray-900 dark:text-gray-100">
                  {section.title}
                </h4>
              </div>
              <span
                className={`transform transition-transform ${isOpen ? 'rotate-180' : ''}`}
                aria-hidden="true"
              >
                ▼
              </span>
            </summary>
            <div className="p-4 pt-0 border-t border-gray-200 dark:border-gray-700 mt-0">
              {section.content}
            </div>
          </details>
        );
      })}
    </div>
  );
};

export default PhaseAdvancedDisclosure;
