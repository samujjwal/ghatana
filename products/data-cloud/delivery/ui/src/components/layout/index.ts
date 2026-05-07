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
  PageHeader,
  PageContent,
  PageSection,
  ContextPanel,
  SuggestionCard,
  EmptyState,
  StatCard,
} from "./PageLayout";

export type {
  PageHeaderProps,
  PageContentProps,
  PageSectionProps,
  ContextPanelProps,
  SuggestionCardProps,
  EmptyStateProps,
  StatCardProps,
} from "./PageLayout";
