/**
 * Simulation Templates Page
 *
 * Main page for browsing and discovering simulation templates in the marketplace.
 *
 * @doc.type page
 * @doc.purpose Simulation template marketplace page
 * @doc.layer product
 * @doc.pattern Page
 */

import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { SimulationTemplateGallery } from "../features/marketplace";
import type { SimulationTemplate } from "../features/marketplace";

// =============================================================================
// Component
// =============================================================================

export const SimulationTemplatesPage = () => {
  const navigate = useNavigate();

  const handleTemplateSelect = useCallback(
    (template: SimulationTemplate) => {
      // Navigate to template detail page
      navigate(`/templates/${template.id}`);
    },
    [navigate]
  );

  const handleTemplateUse = useCallback(
    (templateId: string) => {
      // Navigate to authoring with template
      navigate(`/author?template=${templateId}`);
    },
    [navigate]
  );

  return (
    <div className="flex flex-col h-full">
      {/* Page Header */}
      <header className="flex-shrink-0 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-6 py-4">
        <nav className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
          <a href="/" className="hover:text-gray-700 dark:hover:text-gray-200">
            Home
          </a>
          <span>/</span>
          <span className="text-gray-900 dark:text-white">Templates</span>
        </nav>
      </header>

      {/* Gallery */}
      <main className="flex-1 overflow-hidden">
        <SimulationTemplateGallery
          onTemplateSelect={handleTemplateSelect}
          onTemplateUse={handleTemplateUse}
          showFeatured={true}
          showFilters={true}
          gridColumns={3}
          className="h-full"
        />
      </main>
    </div>
  );
};

export default SimulationTemplatesPage;
