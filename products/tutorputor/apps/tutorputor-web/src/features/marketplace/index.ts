/**
 * Marketplace Feature Barrel Export
 *
 * @doc.type barrel
 * @doc.purpose Export marketplace feature public API
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Components
export {
  SimulationTemplateCard,
  TemplateFilterPanel,
  SimulationTemplateGallery,
} from "./components";
export type {
  SimulationTemplateCardProps,
  TemplateFilterPanelProps,
  SimulationTemplateGalleryProps,
} from "./components";

// Hooks
export {
  useSimulationTemplates,
  useFeaturedTemplates,
} from "./hooks";
export type {
  UseSimulationTemplatesOptions,
  UseSimulationTemplatesReturn,
} from "./hooks";

// Types
export type {
  SimulationTemplate,
  TemplateFilters,
  TemplateSort,
  TemplateStats,
  TemplateAuthor,
  SimulationDomain,
  SimulationManifest,
  DifficultyLevel,
  TemplateDifficulty,
  TemplateSortField,
  TemplateSortOrder,
  SortDirection,
} from "./types";
