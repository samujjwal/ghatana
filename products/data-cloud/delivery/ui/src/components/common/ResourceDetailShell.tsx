/**
 * ResourceDetailShell Component
 *
 * Unified layout wrapper for resource detail pages — collections, entities,
 * plugins, workflows, contexts. Provides the standard header with back
 * navigation, resource icon, h1 title, status badge, optional metadata row,
 * and primary/secondary action slots. The content area is rendered via
 * `children`.
 *
 * Adopting this shell eliminates duplicated header markup across
 * DataExplorer, EntityBrowserPage, and PluginDetailsPage.
 *
 * @doc.type component
 * @doc.purpose Unified detail-page layout shell for data-cloud resource pages
 * @doc.layer shared
 * @doc.pattern Layout Shell
 *
 * @example
 * ```tsx
 * <ResourceDetailShell
 *   title={plugin.name}
 *   onBack={() => navigate('/plugins')}
 *   backLabel="Back to Plugins"
 *   icon={<Package className="h-10 w-10 text-white" />}
 *   statusBadge={<StatusBadge status="active" label="Active" />}
 *   metaItems={[
 *     { icon: <Users className="h-4 w-4" />, text: plugin.author },
 *   ]}
 *   actions={<Button onClick={uninstall}>Uninstall</Button>}
 * >
 *   {/* page content *\/}
 * </ResourceDetailShell>
 * ```
 */

import { ArrowLeft } from "lucide-react";
import React from "react";
import { bgStyles, buttonStyles, cn } from "../../lib/theme";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface ResourceDetailMetaItem {
  /** Icon rendered before the text */
  icon: React.ReactNode;
  /** Text content of the meta item */
  text: React.ReactNode;
}

export interface ResourceDetailShellProps {
  /** Page title — rendered as `<h1>` */
  title: string;
  /** Optional subtitle / description rendered below the title */
  subtitle?: string;
  /** Callback when the back button is clicked */
  onBack: () => void;
  /** Label for the back navigation button */
  backLabel: string;
  /**
   * Icon rendered in the coloured square to the left of the title block.
   * Wrap your icon in the desired colour class. When omitted, the icon
   * area is not rendered.
   */
  icon?: React.ReactNode;
  /**
   * Background gradient class for the icon container.
   * Defaults to a primary-500 → primary-700 gradient.
   */
  iconBgClassName?: string;
  /**
   * Badge element rendered next to the title — typically a `<StatusBadge>`.
   * Rendered inline in the title row when provided.
   */
  statusBadge?: React.ReactNode;
  /**
   * Optional metadata row items (author, version, dates …).
   * Each item is an `{ icon, text }` pair.
   */
  metaItems?: ResourceDetailMetaItem[];
  /**
   * Primary and secondary action elements rendered in the top-right corner
   * of the header. Accepts a single element or a fragment of elements.
   */
  actions?: React.ReactNode;
  /** Page body content */
  children: React.ReactNode;
  /** Optional className on the outermost container */
  className?: string;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * ResourceDetailShell
 *
 * Renders the standard data-cloud detail page layout:
 * - Back navigation button
 * - Optional icon block + h1 title + status badge
 * - Optional subtitle + metadata row
 * - Optional action slot (top-right)
 * - `children` in the content area below the header
 */
export function ResourceDetailShell({
  title,
  subtitle,
  onBack,
  backLabel,
  icon,
  iconBgClassName = "bg-gradient-to-br from-primary-500 to-primary-700",
  statusBadge,
  metaItems,
  actions,
  children,
  className,
}: ResourceDetailShellProps): React.ReactElement {
  return (
    <section className={cn("min-h-screen", bgStyles.page, className)}>
      {/* Page header */}
      <header className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <div className="px-6 py-4">
          {/* Back navigation */}
          <button
            type="button"
            onClick={onBack}
            className={cn(
              buttonStyles.ghost,
              "px-3 py-2 mb-4 flex items-center gap-2 text-sm",
            )}
            aria-label={backLabel}
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            {backLabel}
          </button>

          <div className="flex items-start justify-between gap-6">
            <div className="flex items-start gap-6 min-w-0">
              {/* Icon block */}
              {icon !== undefined && (
                <div
                  className={cn(
                    "w-20 h-20 rounded-xl flex items-center justify-center flex-shrink-0",
                    iconBgClassName,
                  )}
                  aria-hidden="true"
                >
                  {icon}
                </div>
              )}

              {/* Title / meta */}
              <div className="flex-1 min-w-0">
                {/* Title row */}
                <div className="flex items-center gap-3 mb-1 flex-wrap">
                  <h1 className="text-xl font-semibold text-gray-900 dark:text-white leading-tight">
                    {title}
                  </h1>
                  {statusBadge}
                </div>

                {/* Subtitle */}
                {subtitle !== undefined && (
                  <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
                    {subtitle}
                  </p>
                )}

                {/* Meta items */}
                {metaItems !== undefined && metaItems.length > 0 && (
                  <div className="flex items-center gap-6 text-sm flex-wrap">
                    {metaItems.map((item, index) => (
                      <div
                        key={index}
                        className="flex items-center gap-2 text-gray-600 dark:text-gray-400"
                      >
                        <span aria-hidden="true">{item.icon}</span>
                        <span>{item.text}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Actions */}
            {actions !== undefined && (
              <div className="flex items-center gap-2 flex-shrink-0">
                {actions}
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Page content */}
      <div className="p-6">{children}</div>
    </section>
  );
}

export default ResourceDetailShell;
