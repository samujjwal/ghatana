/**
 * ContextExplorerPage
 *
 * Context Plane is target-only and not implemented as an active module.
 * This page renders a disabled state indicating the surface is unavailable.
 *
 * @doc.type page
 * @doc.purpose Disabled page for target-only Context Plane
 * @doc.layer frontend
 * @doc.pattern Page
 */

import { AlertCircle, Info } from "lucide-react";
import React from "react";

export function ContextExplorerPage(): React.ReactElement {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="max-w-2xl rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="flex items-start gap-4">
          <div className="rounded-full bg-amber-50 p-3 text-amber-600">
            <AlertCircle className="h-6 w-6" />
          </div>
          <div className="flex-1">
            <h1 className="text-2xl font-semibold text-slate-950">
              Context Plane Not Available
            </h1>
            <p className="mt-2 text-slate-600">
              The Context Plane is currently target-only and not implemented as an
              active module. Context-related functionality (lineage, provenance,
              freshness, semantic context) remains in the owning modules until
              the package-level cleanup phase.
            </p>
            <div className="mt-6 rounded-lg bg-slate-50 p-4">
              <div className="flex items-start gap-3">
                <Info className="h-5 w-5 text-slate-500" />
                <div>
                  <p className="text-sm font-medium text-slate-700">
                    Owner Plane
                  </p>
                  <p className="mt-1 text-sm text-slate-600">
                    Context Plane code will be separated from Data, Intelligence,
                    and Action Plane internals in a future implementation phase.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ContextExplorerPage;
