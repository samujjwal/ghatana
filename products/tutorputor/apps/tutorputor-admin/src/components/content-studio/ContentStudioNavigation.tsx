/**
 * Content Studio Navigation
 *
 * Navigation tabs for the Unified Content Studio.
 *
 * @doc.type component
 * @doc.purpose Navigation tabs for content studio
 * @doc.layer product
 * @doc.pattern UI Component
 */

import type { LucideIcon } from "lucide-react";
import { BarChart3, Cpu, TrendingUp, FileText, Zap, Sparkles } from "lucide-react";

export interface NavigationTab {
  id: string;
  name: string;
  icon: LucideIcon;
  description: string;
}

export interface ContentStudioNavigationProps {
  currentView: string;
  onNavigate: (view: string) => void;
}

export const navigationTabs: NavigationTab[] = [
  {
    id: "dashboard",
    name: "Dashboard",
    icon: BarChart3,
    description: "Content creation overview",
  },
  {
    id: "infrastructure",
    name: "Infrastructure",
    icon: Cpu,
    description: "AI models & systems",
  },
  {
    id: "analytics",
    name: "Analytics",
    icon: TrendingUp,
    description: "Content metrics",
  },
  {
    id: "templates",
    name: "Templates",
    icon: FileText,
    description: "Template library",
  },
  {
    id: "automation",
    name: "Automation",
    icon: Zap,
    description: "Automated generation",
  },
  {
    id: "create",
    name: "Create Content",
    icon: Sparkles,
    description: "AI content creation",
  },
];

export function ContentStudioNavigation({
  currentView,
  onNavigate,
}: ContentStudioNavigationProps) {
  return (
    <div className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <div className="flex space-x-8 px-6">
        {navigationTabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => onNavigate(tab.id)}
            className={`flex items-center gap-2 py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
              currentView === tab.id
                ? "border-purple-500 text-purple-600 dark:text-purple-400"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300"
            }`}
          >
            <tab.icon className="h-5 w-5" />
            <span>{tab.name}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
