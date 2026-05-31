/**
 * Layout Components Module Index
 *
 * Exports all layout-related components:
 * - AppShell: Global application wrapper
 * - PageLayout: Unified page structure components
 *
 * @doc.type module
 * @doc.purpose Layout components exports
 * @doc.layer frontend
 */

// App Shell - Global wrapper with search, shortcuts, AI assistant
export * from "./AppShell";
export { default as AppShell } from "./AppShell";

// Page Layout - Unified page structure components
export {
  ContextPanel,
  EmptyState,
  PageContent,
  PageHeader,
  PageSection,
  StatCard,
  SuggestionCard,
} from "./PageLayout";

export type {
  ContextPanelProps,
  EmptyStateProps,
  PageContentProps,
  PageHeaderProps,
  PageSectionProps,
  StatCardProps,
  SuggestionCardProps,
} from "./PageLayout";
