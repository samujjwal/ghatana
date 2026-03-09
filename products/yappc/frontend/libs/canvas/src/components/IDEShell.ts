/**
 * IDE Shell Components - Migration Bridge
 * 
 * These components are being migrated from @ghatana/yappc-ide to @ghatana/yappc-canvas
 * as part of the library consolidation effort.
 * 
 * This file bridges IDE components to the new CanvasChromeLayout system.
 * 
 * @deprecated Import directly from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect } from 'react';

/**
 * Migration guide mapping:
 * - IDEShell → CanvasChromeLayout (use leftRail for sidebar, inspector for panel)
 * - ProfessionalIDELayout → Use CanvasChromeLayout with full configuration
 */

export interface IDEShellProps {
  children: React.ReactNode;
  className?: string;
  /** @deprecated Use CanvasChromeLayout directly with header prop */
  header?: React.ReactNode;
  /** Maps to CanvasChromeLayout leftRail */
  sidebar?: React.ReactNode;
  /** Maps to CanvasChromeLayout inspector */
  panel?: React.ReactNode;
  /** @deprecated Use CanvasChromeLayout statusBar pattern */
  statusBar?: React.ReactNode;
  showToolbar?: boolean;
  toolbar?: React.ReactNode;
}

export interface ProfessionalIDELayoutProps {
  /** Maps to CanvasChromeLayout leftRail */
  sidebar?: React.ReactNode;
  /** Main editor content - use as CanvasChromeLayout children */
  editor: React.ReactNode;
  /** Maps to CanvasChromeLayout inspector */
  panel?: React.ReactNode;
  /** @deprecated Use CanvasChromeLayout statusBar pattern */
  statusBar?: React.ReactNode;
}

/**
 * Deprecation warning hook
 */
export function useIDEDeprecationWarning(componentName: string): void {
  useEffect(() => {
    console.warn(
      `[MIGRATION] ${componentName} is deprecated. ` +
      `Import ${componentName === 'IDEShell' ? 'CanvasChromeLayout' : 'CanvasChromeLayout with full config'} ` +
      `from @ghatana/yappc-canvas. See LIBRARY_CONSOLIDATION_PLAN.md`
    );
  }, [componentName]);
}

/**
 * IDEShell - Bridge Component
 * 
 * Maps IDE shell features to Canvas chrome layout.
 * This is a bridge component during migration.
 */
export const IDEShell: React.FC<IDEShellProps> = ({
  children,
}) => {
  useIDEDeprecationWarning('IDEShell');
  // Return fragment wrapper for type compatibility
  return React.createElement(React.Fragment, {}, children);
};

/**
 * ProfessionalIDELayout - Bridge Component
 */
export const ProfessionalIDELayout: React.FC<ProfessionalIDELayoutProps> = ({
  editor,
}) => {
  useIDEDeprecationWarning('ProfessionalIDELayout');
  // Return fragment wrapper for type compatibility
  return React.createElement(React.Fragment, {}, editor);
};

// Migration helper for gradual transition
export interface IDEShellMigrationHelperProps {
  /** Use new CanvasChromeLayout */
  useNewLayout?: boolean;
  children: React.ReactNode;
}

/**
 * Migration helper for gradual transition
 */
export const IDEShellMigrationHelper: React.FC<IDEShellMigrationHelperProps> = ({
  useNewLayout = false,
  children,
}) => {
  useEffect(() => {
    console.info(
      '[MIGRATION] IDEShellMigrationHelper: ' + (useNewLayout ? 'Using new layout' : 'Using legacy layout')
    );
  }, [useNewLayout]);

  // Return fragment wrapper for type compatibility
  return React.createElement(React.Fragment, {}, children);
};
