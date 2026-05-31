/**
 * Unified Data Page
 *
 * Consolidates Entity Browser, Context Explorer, and Data Fabric under a single Data surface
 * with tab navigation for simplified UX and reduced cognitive load.
 *
 * Tabs:
 * - Collections: Browse collections, datasets, data sources (original DataExplorer)
 * - Entities: Browse and manage entities with schema info (original EntityBrowserPage)
 * - Context: Collection-scoped context for schema, lineage, governance (original ContextExplorerPage)
 * - Fabric: Four-tier data fabric topology visualizer (original DataFabricPage)
 *
 * @doc.type page
 * @doc.purpose Unified data surface with tab navigation
 * @doc.layer frontend
 * @doc.pattern Page
 */

import { Database, Layers, Network, Table2 } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router";
import { cn } from "../lib/theme";

type DataTab = "collections" | "entities" | "context" | "fabric";

interface TabConfig {
  id: DataTab;
  label: string;
  labelKey: string;
  icon: React.ReactNode;
  description: string;
}

const TABS: TabConfig[] = [
  {
    id: "collections",
    label: "Collections",
    labelKey: "data.tab.collections",
    icon: <Database className="h-4 w-4" />,
    description: "Browse collections, datasets, and data sources",
  },
  {
    id: "entities",
    label: "Entities",
    labelKey: "data.tab.entities",
    icon: <Table2 className="h-4 w-4" />,
    description: "Browse and manage entities with schema info",
  },
  {
    id: "context",
    label: "Context",
    labelKey: "data.tab.context",
    icon: <Network className="h-4 w-4" />,
    description: "Collection-scoped context for schema, lineage, governance",
  },
  {
    id: "fabric",
    label: "Fabric",
    labelKey: "data.tab.fabric",
    icon: <Layers className="h-4 w-4" />,
    description: "Four-tier data fabric topology visualizer",
  },
];

function normalizeTab(tab: string | null): DataTab {
  if (tab && TABS.some((t) => t.id === tab)) {
    return tab as DataTab;
  }
  return "collections";
}

function TabButton({
  tab,
  isActive,
  onClick,
}: {
  tab: TabConfig;
  isActive: boolean;
  onClick: () => void;
}): React.ReactElement {
  const { t } = useTranslation();
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors",
        isActive
          ? "bg-primary-50 text-primary-700 dark:bg-primary-900/50 dark:text-primary-300"
          : "text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800",
      )}
      aria-selected={isActive}
      role="tab"
    >
      {tab.icon}
      <span>{t(tab.labelKey, { defaultValue: tab.label })}</span>
    </button>
  );
}

function TabContent({ activeTab }: { activeTab: DataTab }): React.ReactElement {
  const { t } = useTranslation();

  // Lazy load tab content to improve initial load performance
  const CollectionsTab = React.lazy(() =>
    import("./DataExplorer").then((m) => ({ default: m.default })),
  );
  const EntitiesTab = React.lazy(() =>
    import("./EntityBrowserPage").then((m) => ({
      default: m.EntityBrowserPage,
    })),
  );
  const ContextTab = React.lazy(() =>
    import("./ContextExplorerPage").then((m) => ({
      default: m.ContextExplorerPage,
    })),
  );
  const FabricTab = React.lazy(() =>
    import("./DataFabricPage").then((m) => ({ default: m.DataFabricPage })),
  );

  const contentMap: Record<DataTab, React.ReactNode> = {
    collections: <CollectionsTab />,
    entities: <EntitiesTab />,
    context: <ContextTab />,
    fabric: <FabricTab />,
  };

  return (
    <React.Suspense
      fallback={
        <div
          className="flex items-center justify-center py-12"
          role="status"
          aria-live="polite"
        >
          <div className="text-center">
            <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-solid border-current border-r-transparent align-[-0.125em] motion-reduce:animate-[spin_1.5s_linear_infinite]" />
            <p className="mt-4 text-sm text-gray-600 dark:text-gray-400">
              {t("common.loading")}
            </p>
          </div>
        </div>
      }
    >
      {contentMap[activeTab]}
    </React.Suspense>
  );
}

export default function DataPage(): React.ReactElement {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const _navigate = useNavigate();

  const activeTab = normalizeTab(searchParams.get("tab"));

  const handleTabChange = (tabId: DataTab) => {
    setSearchParams({ tab: tabId });
  };

  const activeTabConfig = TABS.find((tab) => tab.id === activeTab);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">
          {t("data.title")}
        </h1>
        <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
          {activeTabConfig?.description || t("data.description")}
        </p>
      </div>

      {/* Tab Navigation */}
      <div className="border-b border-gray-200 dark:border-gray-700">
        <nav className="flex space-x-2" role="tablist" aria-label="Data tabs">
          {TABS.map((tab) => (
            <TabButton
              key={tab.id}
              tab={tab}
              isActive={tab.id === activeTab}
              onClick={() => handleTabChange(tab.id)}
            />
          ))}
        </nav>
      </div>

      {/* Tab Content */}
      <div role="tabpanel" aria-labelledby={`tab-${activeTab}`}>
        <TabContent activeTab={activeTab} />
      </div>
    </div>
  );
}
