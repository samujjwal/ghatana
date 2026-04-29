/**
 * Templates View
 *
 * Templates view for the Unified Content Studio.
 *
 * @doc.type component
 * @doc.purpose Template management for content studio
 * @doc.layer product
 * @doc.pattern View Component
 */

import { Button } from "@ghatana/design-system";

export interface TemplateStats {
  totalTemplates: number;
  byDomain: Record<string, number>;
  usageStats: {
    mostUsed: string;
    leastUsed: string;
    avgUsage: number;
  };
  recentTemplates: Array<{
    id: string;
    name: string;
    domain: string;
    usageCount: number;
    lastUsed: string;
  }>;
}

export interface TemplatesViewProps {
  templateStats: TemplateStats | null;
}

export function TemplatesView({ templateStats }: TemplatesViewProps) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Recent Templates
            </h3>
            <div className="space-y-4">
              {templateStats?.recentTemplates.map((template) => (
                <div
                  key={template.id}
                  className="flex items-center justify-between p-4 border border-gray-200 dark:border-gray-700 rounded-lg"
                >
                  <div>
                    <h4 className="font-medium text-gray-900 dark:text-white">
                      {template.name}
                    </h4>
                    <p className="text-sm text-gray-600 dark:text-gray-400">
                      {template.domain} • Used {template.usageCount} times
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      {template.lastUsed}
                    </p>
                    <Button variant="outline" size="sm">
                      Use Template
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div>
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Template Stats
            </h3>
            <div className="space-y-4">
              <div>
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                  Total Templates
                </p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                  {templateStats?.totalTemplates}
                </p>
              </div>
              <div>
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                  Most Used
                </p>
                <p className="text-lg font-medium text-gray-900 dark:text-white">
                  {templateStats?.usageStats.mostUsed}
                </p>
              </div>
              <div>
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                  Avg Usage
                </p>
                <p className="text-lg font-medium text-gray-900 dark:text-white">
                  {templateStats?.usageStats.avgUsage} times
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
