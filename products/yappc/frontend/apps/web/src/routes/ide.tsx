/**
 * IDE Route
 *
 * @deprecated ORPHANED — This route is NOT wired in the application router.
 * The IDE has been migrated to the Canvas system (@ghatana/yappc-canvas).
 * This file is retained only as a migration reference and will be deleted
 * after the sunset date (2026-06-06).
 *
 * @migration Migrated from @ghatana/yappc-ide to @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React from 'react';
import { Provider } from 'jotai';
import { IDEShell } from '@ghatana/yappc-canvas';

/**
 * IDE Page Component
 */
export default function IDEPage() {
  return (
    <Provider>
      <div className="w-full h-screen">
        <IDEShell>
          <div className="ide-content-placeholder">
            IDE Content migrated to CanvasChromeLayout
          </div>
        </IDEShell>
      </div>
    </Provider>
  );
}
