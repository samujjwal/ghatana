/**
 * Page-level error boundary that uses runtime surface truth (P0.7).
 *
 * Unlike static fallback text, this boundary fetches the live runtime surface registry
 * and displays which subsystems are down or degraded, giving users actionable
 * context about why a page failed to load.
 *
 * @doc.type component
 * @doc.purpose Runtime-surface-aware error boundary
 * @doc.layer frontend
 */

import { AlertTriangle, RefreshCw } from "lucide-react";
import React from "react";
import { useSurfaceRegistry } from "../../api/surfaces.service";
import { emitDataCloudDiagnostic } from "../../diagnostics";

interface Props {
  children: React.ReactNode;
  /** Capability aliases this page depends on */
  requiredCapabilities?: string[];
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class RuntimeCapabilityErrorBoundary extends React.Component<
  Props,
  State
> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    if (import.meta.env.DEV) {
      emitDataCloudDiagnostic(
        "RuntimeCapabilityErrorBoundary",
        "error",
        "Runtime capability page failed",
        {
          error,
          componentStack: info.componentStack,
        },
      );
    }
  }

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <div className="flex items-center justify-center w-full min-h-64 p-6">
        <div className="text-center max-w-lg">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-rose-100 dark:bg-rose-900/30 mb-4">
            <AlertTriangle className="h-6 w-6 text-rose-600 dark:text-rose-300" />
          </div>
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
            This page could not be loaded
          </h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
            {this.state.error?.message ??
              "An unexpected error occurred while loading the page."}
          </p>

          {/* Live runtime surface status panel */}
          <CapabilityStatusPanel
            requiredCapabilities={this.props.requiredCapabilities}
          />

          <button
            type="button"
            onClick={() => window.location.reload()}
            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            <RefreshCw className="h-4 w-4" />
            Reload Page
          </button>
        </div>
      </div>
    );
  }
}

/**
 * Subcomponent that reads the live runtime surface registry and shows subsystem status.
 */
function CapabilityStatusPanel({
  requiredCapabilities,
}: {
  requiredCapabilities?: string[];
}): React.ReactElement {
  const { data: registry, isLoading } = useSurfaceRegistry();

  if (isLoading || !registry) {
    return (
      <div className="mb-4 p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700">
        <p className="text-xs text-gray-500 dark:text-gray-400">
          Loading runtime surface status...
        </p>
      </div>
    );
  }

  const relevant =
    requiredCapabilities && requiredCapabilities.length > 0
      ? registry.surfaces.filter((c) =>
          requiredCapabilities.some(
            (alias) => alias.toLowerCase() === c.key.toLowerCase(),
          ),
        )
      : registry.surfaces.filter((c) => c.status !== "LIVE");

  if (relevant.length === 0) {
    return (
      <div className="mb-4 p-3 rounded-lg bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800/50">
        <p className="text-xs text-emerald-700 dark:text-emerald-300">
          All required runtime surfaces are reported active. The error may be
          transient.
        </p>
      </div>
    );
  }

  return (
    <div className="mb-4 p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 text-left">
      <p className="text-xs font-semibold text-gray-700 dark:text-gray-300 mb-2 uppercase tracking-wide">
        Runtime surface status
      </p>
      <ul className="space-y-1">
        {relevant.map((cap) => (
          <li
            key={cap.key}
            className="flex items-center justify-between text-xs"
          >
            <span className="text-gray-600 dark:text-gray-400">
              {cap.label}
            </span>
            <span
              className={
                cap.status === "LIVE"
                  ? "text-emerald-600 dark:text-emerald-400 font-medium"
                  : cap.status === "DEGRADED" || cap.status === "PREVIEW"
                    ? "text-amber-600 dark:text-amber-400 font-medium"
                    : "text-rose-600 dark:text-rose-400 font-medium"
              }
            >
              {cap.summary}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
