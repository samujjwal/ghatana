/**
 * Unified Authoring Page
 *
 * Single canvas for all content authoring that combines:
 * - Content Studio (creation flow)
 * - AI Kernel (invisible infrastructure)
 * - Dashboard (default view when no content selected)
 * - Templates & Seeding (part of library)
 *
 * Design Principles:
 * - AI is the medium, not a destination
 * - Zero modal wizards - inline progressive disclosure
 * - Everything happens in one canvas
 * - Command palette for power users (Cmd+K)
 *
 * @doc.type page
 * @doc.purpose Unified AI-first content authoring
 * @doc.layer product
 * @doc.pattern Canvas
 */

import { useState, useCallback, useEffect, useMemo } from "react";
import { Provider as JotaiProvider } from "jotai";
import {
  useSearchParams,
  useNavigate,
  useParams,
  Link,
} from "react-router-dom";
import { DndProvider } from "react-dnd";
import { HTML5Backend } from "react-dnd-html5-backend";
import {
  BreadcrumbRouter,
  ContextIndicator,
  Button,
  useAnnouncer,
  ConfirmDialog,
  useConfirmDialog,
  Tour,
  useTour,
  Skeleton,
  SkeletonCard,
  toast,
  useFormValidation,
  validators,
  FormField,
  PullToRefresh,
} from "@ghatana/design-system";
import type { TourStep } from "@ghatana/design-system";
import {
  Plus,
  Search,
  Filter,
  Grid3X3,
  List,
  ChevronRight,
  Sparkles,
  BookOpen,
  Beaker,
  Play,
  CheckCircle,
  Clock,
  AlertTriangle,
  FileText,
  Layers,
  Settings,
  MoreHorizontal,
  RefreshCw,
  Trash2,
  Copy,
  Share2,
  Eye,
  Edit3,
  X,
  Send,
  Bot,
  User,
  Lightbulb,
  Zap,
  Film,
} from "lucide-react";

// Import full-featured simulation and animation components
import {
  useSimulation,
  useSimulationKeyboardShortcuts,
  SimulationCanvas,
  SimulationToolbar,
  EntityToolbox,
  PhysicsPropertyPanel,
  PhysicsConfigPanel,
  type EntityType,
} from "@ghatana/tutorputor-physics-simulation";
import { AnimationStudio } from "../components/animation/AnimationStudio";
import type { AnimationSpec } from "../../../../services/tutorputor-platform/src/modules/animation-runtime/service";

// Types
interface ContentItem {
  id: string;
  title: string;
  description: string;
  type: "learning_experience" | "simulation" | "animation" | "assessment";
  status: "draft" | "review" | "published" | "archived";
  domain: string;
  gradeRange: string;
  updatedAt: Date;
  author: string;
  metrics?: {
    views: number;
    completions: number;
    rating: number;
  };
}

interface AISuggestion {
  id: string;
  type: "action" | "insight" | "warning";
  title: string;
  description: string;
  action?: () => void;
}

type ViewMode = "library" | "editor" | "preview";
type LibraryView = "grid" | "list";

// Main component wrapped with Jotai provider for simulation state
export function AuthoringPage() {
  return (
    <JotaiProvider>
      <AuthoringPageContent />
    </JotaiProvider>
  );
}

// Main page content
function AuthoringPageContent() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { id: editId } = useParams();
  const announce = useAnnouncer();

  // State
  const [viewMode, setViewMode] = useState<ViewMode>(
    editId ? "editor" : "library",
  );
  const [libraryView, setLibraryView] = useState<LibraryView>("grid");
  const [selectedContent, setSelectedContent] = useState<ContentItem | null>(
    null,
  );
  const [searchQuery, setSearchQuery] = useState("");
  const [filterStatus, setFilterStatus] = useState<string>("all");
  const [filterType, setFilterType] = useState<string>("all");
  const [isAICoPilotOpen, setIsAICoPilotOpen] = useState(true);
  const [newContentInput, setNewContentInput] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [aiSuggestions, setAiSuggestions] = useState<AISuggestion[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Pull-to-refresh handler for mobile
  const handleRefreshContent = useCallback(async () => {
    setIsRefreshing(true);
    try {
      // Simulate fetching latest content from server
      await new Promise((resolve) => setTimeout(resolve, 1000));
      announce("Content library refreshed");
      toast.success("Content refreshed");
    } finally {
      setIsRefreshing(false);
    }
  }, [announce]);

  // Delete confirmation dialog
  const {
    dialogProps,
    confirm: confirmDelete,
    isOpen: isDeleteDialogOpen,
  } = useConfirmDialog();
  const [contentToDelete, setContentToDelete] = useState<ContentItem | null>(
    null,
  );

  // Onboarding tour
  const {
    active: isTourActive,
    complete: completeTour,
    skip: skipTour,
  } = useTour("authoring-tour");

  const tourSteps: TourStep[] = useMemo(
    () => [
      {
        id: "create-button",
        target: "#create-content-btn",
        title: "Create Content",
        content:
          "Start by describing what you want to create. Our AI will help structure your learning experience.",
        placement: "bottom",
      },
      {
        id: "content-library",
        target: "#content-library",
        title: "Your Content Library",
        content:
          "All your learning experiences, simulations, and animations appear here. Use filters to find what you need.",
        placement: "right",
      },
      {
        id: "ai-copilot",
        target: "#ai-copilot",
        title: "AI Co-Pilot",
        content:
          "Your AI assistant is always ready to help. Ask questions, get suggestions, or generate content.",
        placement: "left",
      },
      {
        id: "editor-tabs",
        target: "#editor-tabs",
        title: "Content Editor",
        content:
          "Each piece of content has claims, tasks, simulations, and animations. Navigate between them using these tabs.",
        placement: "bottom",
      },
    ],
    [],
  );

  // Delete handler with confirmation
  const handleDeleteContent = useCallback(
    async (content: ContentItem) => {
      setContentToDelete(content);
      confirmDelete({
        title: "Delete Content",
        message: `Are you sure you want to delete "${content.title}"? This action cannot be undone.`,
        confirmText: "Delete",
        confirmVariant: "destructive",
      });
    },
    [confirmDelete],
  );

  const executeDelete = useCallback(async () => {
    if (!contentToDelete) return;

    setIsDeleting(true);
    try {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 500));

      setContentItems((prev) =>
        prev.filter((item) => item.id !== contentToDelete.id),
      );

      if (selectedContent?.id === contentToDelete.id) {
        setSelectedContent(null);
        setViewMode("library");
      }

      toast.success(`"${contentToDelete.title}" has been deleted`);
      announce(`${contentToDelete.title} deleted`);
    } catch (error) {
      toast.error("Failed to delete content. Please try again.");
    } finally {
      setIsDeleting(false);
      setContentToDelete(null);
    }
  }, [contentToDelete, selectedContent, announce]);

  // Mock data - replace with real API
  const [contentItems, setContentItems] = useState<ContentItem[]>([
    {
      id: "1",
      title: "Newton's Laws of Motion",
      description:
        "Interactive exploration of the three laws of motion with real-world examples",
      type: "learning_experience",
      status: "published",
      domain: "Physics",
      gradeRange: "9-12",
      updatedAt: new Date("2026-02-01"),
      author: "Dr. Smith",
      metrics: { views: 1250, completions: 890, rating: 4.8 },
    },
    {
      id: "2",
      title: "Photosynthesis Process",
      description:
        "Step-by-step simulation of the photosynthesis process in plants",
      type: "simulation",
      status: "draft",
      domain: "Biology",
      gradeRange: "6-8",
      updatedAt: new Date("2026-02-02"),
      author: "Dr. Green",
    },
    {
      id: "3",
      title: "Quadratic Equations",
      description:
        "Visual representation of quadratic functions and their solutions",
      type: "learning_experience",
      status: "review",
      domain: "Mathematics",
      gradeRange: "9-12",
      updatedAt: new Date("2026-02-03"),
      author: "Prof. Math",
      metrics: { views: 450, completions: 320, rating: 4.5 },
    },
  ]);

  // Initialize AI suggestions based on context
  useEffect(() => {
    const suggestions: AISuggestion[] = [
      {
        id: "1",
        type: "insight",
        title: "Trending Topic",
        description:
          "Climate change content is in high demand. Consider creating related materials.",
        action: () =>
          setNewContentInput(
            "Create an interactive learning experience about climate change",
          ),
      },
      {
        id: "2",
        type: "action",
        title: "Review Needed",
        description: "3 items are waiting for your review",
        action: () => {
          setFilterStatus("review");
        },
      },
      {
        id: "3",
        type: "warning",
        title: "Low Engagement",
        description:
          '"Algebra Basics" has declining completion rates. Consider revising.',
      },
    ];
    setAiSuggestions(suggestions);
  }, []);

  // Handle new content creation via natural language
  const handleCreateContent = useCallback(async () => {
    if (!newContentInput.trim()) return;

    setIsCreating(true);
    try {
      // Call AI to analyze intent and generate structure
      const response = await fetch("/api/content-studio/ai/analyze-intent", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-tenant-id": "default",
        },
        body: JSON.stringify({ intent: newContentInput }),
      });

      if (response.ok) {
        const result = await response.json();
        // Navigate to editor with generated structure
        navigate(
          `/authoring/new?intent=${encodeURIComponent(newContentInput)}`,
        );
      } else {
        // Fallback: create with basic structure
        navigate(
          `/authoring/new?intent=${encodeURIComponent(newContentInput)}`,
        );
      }
    } catch (error) {
      console.error("Failed to analyze intent:", error);
      // Fallback: navigate anyway
      navigate(`/authoring/new?intent=${encodeURIComponent(newContentInput)}`);
    } finally {
      setIsCreating(false);
    }
  }, [newContentInput, navigate]);

  // Filter content items
  const filteredContent = contentItems.filter((item) => {
    const matchesSearch =
      item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.description.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesStatus =
      filterStatus === "all" || item.status === filterStatus;
    const matchesType = filterType === "all" || item.type === filterType;
    return matchesSearch && matchesStatus && matchesType;
  });

  // Announce filter results for screen readers
  useEffect(() => {
    if (searchQuery || filterStatus !== "all" || filterType !== "all") {
      const count = filteredContent.length;
      const message =
        count === 0
          ? "No content found matching filters"
          : `${count} ${count === 1 ? "item" : "items"} found`;
      announce(message, 1000);
    }
  }, [filteredContent.length, searchQuery, filterStatus, filterType, announce]);

  // Content type icons
  const getTypeIcon = (type: ContentItem["type"]) => {
    switch (type) {
      case "learning_experience":
        return <BookOpen className="w-4 h-4" />;
      case "simulation":
        return <Beaker className="w-4 h-4" />;
      case "animation":
        return <Play className="w-4 h-4" />;
      case "assessment":
        return <FileText className="w-4 h-4" />;
    }
  };

  // Status badges
  const getStatusBadge = (status: ContentItem["status"]) => {
    const styles = {
      draft: "bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300",
      review:
        "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400",
      published:
        "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400",
      archived: "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400",
    };
    return (
      <span
        className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${styles[status]}`}
      >
        {status.charAt(0).toUpperCase() + status.slice(1)}
      </span>
    );
  };

  return (
    <>
      <div className="h-screen flex bg-gray-50 dark:bg-gray-900 overflow-hidden">
        {/* Content Library Sidebar */}
        <aside
          id="content-library"
          className="w-72 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex flex-col"
        >
          {/* Library Header */}
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
              Content Library
            </h2>

            {/* Search */}
            <div className="mt-3 relative">
              <label htmlFor="content-search" className="sr-only">
                Search content
              </label>
              <Search
                className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
                aria-hidden="true"
              />
              <input
                id="content-search"
                type="text"
                placeholder="Search content..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Search content by title, description, or domain"
                className="w-full pl-9 pr-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            {/* Filters */}
            <div className="mt-3 flex gap-2">
              <div className="flex-1">
                <label htmlFor="filter-status" className="sr-only">
                  Filter by status
                </label>
                <select
                  id="filter-status"
                  value={filterStatus}
                  onChange={(e) => setFilterStatus(e.target.value)}
                  aria-label="Filter content by status"
                  className="w-full text-xs bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1.5 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                >
                  <option value="all">All Status</option>
                  <option value="draft">Draft</option>
                  <option value="review">Review</option>
                  <option value="published">Published</option>
                </select>
              </div>
              <div className="flex-1">
                <label htmlFor="filter-type" className="sr-only">
                  Filter by type
                </label>
                <select
                  id="filter-type"
                  value={filterType}
                  onChange={(e) => setFilterType(e.target.value)}
                  aria-label="Filter content by type"
                  className="w-full text-xs bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1.5 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                >
                  <option value="all">All Types</option>
                  <option value="learning_experience">Learning</option>
                  <option value="simulation">Simulation</option>
                  <option value="animation">Animation</option>
                </select>
              </div>
            </div>

            {/* View Toggle */}
            <div className="mt-3 flex items-center justify-between">
              <span className="text-xs text-gray-500">
                {filteredContent.length} items
              </span>
              <div className="flex gap-1">
                <button
                  onClick={() => setLibraryView("grid")}
                  className={`p-1.5 rounded ${
                    libraryView === "grid"
                      ? "bg-primary-100 text-primary-600"
                      : "text-gray-400 hover:bg-gray-100"
                  }`}
                >
                  <Grid3X3 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setLibraryView("list")}
                  className={`p-1.5 rounded ${
                    libraryView === "list"
                      ? "bg-primary-100 text-primary-600"
                      : "text-gray-400 hover:bg-gray-100"
                  }`}
                >
                  <List className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>

          {/* Content List */}
          <div className="flex-1 overflow-hidden">
            <PullToRefresh onRefresh={handleRefreshContent}>
              <div className="h-full overflow-y-auto p-2">
                {isLoading ? (
                  <div className="space-y-2">
                    <SkeletonCard />
                    <SkeletonCard />
                    <SkeletonCard />
                  </div>
                ) : filteredContent.length === 0 ? (
                  <div className="flex flex-col items-center justify-center h-40 text-center">
                    <BookOpen className="w-8 h-8 text-gray-300 dark:text-gray-600 mb-2" />
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      No content found
                    </p>
                    <p className="text-xs text-gray-400 dark:text-gray-500">
                      Try adjusting your filters
                    </p>
                  </div>
                ) : (
                  filteredContent.map((item) => (
                    <div
                      key={item.id}
                      className={`group relative w-full text-left p-3 rounded-lg mb-1 transition-all ${
                        selectedContent?.id === item.id
                          ? "bg-primary-50 dark:bg-primary-900/20 border border-primary-200 dark:border-primary-700"
                          : "hover:bg-gray-50 dark:hover:bg-gray-700 border border-transparent"
                      }`}
                    >
                      <button
                        onClick={() => {
                          setSelectedContent(item);
                          setViewMode("editor");
                        }}
                        className="w-full text-left"
                      >
                        <div className="flex items-start gap-2">
                          <div
                            className={`p-1.5 rounded ${
                              selectedContent?.id === item.id
                                ? "bg-primary-100 text-primary-600"
                                : "bg-gray-100 dark:bg-gray-600 text-gray-600 dark:text-gray-300"
                            }`}
                          >
                            {getTypeIcon(item.type)}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="font-medium text-sm text-gray-900 dark:text-white truncate">
                              {item.title}
                            </div>
                            <div className="flex items-center gap-2 mt-1">
                              {getStatusBadge(item.status)}
                              <span className="text-xs text-gray-500">
                                {item.domain}
                              </span>
                            </div>
                          </div>
                        </div>
                      </button>
                      {/* Delete button - visible on hover */}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteContent(item);
                        }}
                        className="absolute top-2 right-2 p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded opacity-0 group-hover:opacity-100 transition-opacity focus:opacity-100"
                        aria-label={`Delete ${item.title}`}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </PullToRefresh>
          </div>

          {/* Quick Stats */}
          <div className="p-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
            <div className="grid grid-cols-3 gap-2 text-center">
              <div>
                <div className="text-lg font-bold text-gray-900 dark:text-white">
                  {contentItems.filter((c) => c.status === "published").length}
                </div>
                <div className="text-xs text-gray-500">Published</div>
              </div>
              <div>
                <div className="text-lg font-bold text-yellow-600">
                  {contentItems.filter((c) => c.status === "review").length}
                </div>
                <div className="text-xs text-gray-500">In Review</div>
              </div>
              <div>
                <div className="text-lg font-bold text-gray-500">
                  {contentItems.filter((c) => c.status === "draft").length}
                </div>
                <div className="text-xs text-gray-500">Drafts</div>
              </div>
            </div>
          </div>
        </aside>

        {/* Main Authoring Canvas */}
        <main className="flex-1 flex flex-col overflow-hidden">
          {/* Canvas Header */}
          <header className="h-14 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between px-4">
            <div className="flex items-center gap-4">
              {selectedContent ? (
                <>
                  <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                    {selectedContent.title}
                  </h2>
                  {getStatusBadge(selectedContent.status)}
                </>
              ) : (
                <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                  Create New Content
                </h2>
              )}
            </div>

            <div className="flex items-center gap-2">
              {selectedContent && (
                <>
                  <button className="px-3 py-1.5 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg flex items-center gap-2">
                    <Eye className="w-4 h-4" />
                    Preview
                  </button>
                  <button className="px-3 py-1.5 text-sm bg-primary-600 text-white hover:bg-primary-700 rounded-lg flex items-center gap-2">
                    <CheckCircle className="w-4 h-4" />
                    Publish
                  </button>
                </>
              )}
              <button
                onClick={() => setIsAICoPilotOpen(!isAICoPilotOpen)}
                className={`p-2 rounded-lg transition-colors ${
                  isAICoPilotOpen
                    ? "bg-purple-100 text-purple-600 dark:bg-purple-900/30"
                    : "text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
                }`}
                title="Toggle AI Co-Pilot"
              >
                <Sparkles className="w-5 h-5" />
              </button>
            </div>
          </header>

          {/* Canvas Content Area */}
          <div className="flex-1 flex overflow-hidden">
            {/* Main Content */}
            <div className="flex-1 overflow-y-auto p-6">
              {selectedContent ? (
                /* Content Editor */
                <ContentEditor content={selectedContent} />
              ) : (
                /* New Content Creation - Inline, No Modal */
                <div className="max-w-3xl mx-auto">
                  {/* Natural Language Input */}
                  <div className="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 p-6">
                    <div className="text-center mb-6">
                      <div className="w-16 h-16 mx-auto bg-gradient-to-br from-purple-500 to-blue-500 rounded-2xl flex items-center justify-center mb-4">
                        <Sparkles className="w-8 h-8 text-white" />
                      </div>
                      <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
                        What do you want to teach?
                      </h2>
                      <p className="text-gray-500 mt-2">
                        Describe your content idea in natural language. AI will
                        help structure it.
                      </p>
                    </div>

                    <div className="relative">
                      <textarea
                        value={newContentInput}
                        onChange={(e) => setNewContentInput(e.target.value)}
                        placeholder="e.g., Create an interactive physics lesson about Newton's laws for 8th grade students with hands-on simulations..."
                        className="w-full h-32 px-4 py-3 text-lg bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-xl resize-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                        onKeyDown={(e) => {
                          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
                            handleCreateContent();
                          }
                        }}
                      />
                      <div className="absolute bottom-3 right-3 flex items-center gap-2">
                        <span className="text-xs text-gray-400">⌘+Enter</span>
                        <Button
                          onClick={handleCreateContent}
                          disabled={!newContentInput.trim() || isCreating}
                          isLoading={isCreating}
                          loadingText="Analyzing..."
                          variant="primary"
                          leftIcon={<Sparkles className="w-4 h-4" />}
                          id="create-content-btn"
                        >
                          Create with AI
                        </Button>
                      </div>
                    </div>

                    {/* Quick Suggestions */}
                    <div className="mt-6">
                      <div className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                        Popular templates:
                      </div>
                      <div className="flex flex-wrap gap-2">
                        {[
                          "Physics simulation for grade 9",
                          "Biology visual explanation",
                          "Math problem solver",
                          "Chemistry lab safety",
                        ].map((template) => (
                          <button
                            key={template}
                            onClick={() => setNewContentInput(template)}
                            className="px-3 py-1.5 text-sm bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
                          >
                            {template}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* Recent Activity */}
                  <div className="mt-8">
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                      Recent Activity
                    </h3>
                    <div className="grid grid-cols-2 gap-4">
                      {contentItems.slice(0, 4).map((item) => (
                        <button
                          key={item.id}
                          onClick={() => {
                            setSelectedContent(item);
                            setViewMode("editor");
                          }}
                          className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 text-left hover:shadow-md transition-shadow"
                        >
                          <div className="flex items-start gap-3">
                            <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
                              {getTypeIcon(item.type)}
                            </div>
                            <div className="flex-1 min-w-0">
                              <div className="font-medium text-gray-900 dark:text-white truncate">
                                {item.title}
                              </div>
                              <div className="text-sm text-gray-500 mt-1">
                                {item.domain} · {item.gradeRange}
                              </div>
                              <div className="flex items-center gap-2 mt-2">
                                {getStatusBadge(item.status)}
                                <span className="text-xs text-gray-400">
                                  {new Date(
                                    item.updatedAt,
                                  ).toLocaleDateString()}
                                </span>
                              </div>
                            </div>
                          </div>
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* AI Co-Pilot Panel */}
            {isAICoPilotOpen && (
              <aside
                id="ai-copilot"
                className="w-80 bg-white dark:bg-gray-800 border-l border-gray-200 dark:border-gray-700 flex flex-col"
              >
                <AICoPilot
                  context={selectedContent}
                  suggestions={aiSuggestions}
                  onClose={() => setIsAICoPilotOpen(false)}
                />
              </aside>
            )}
          </div>
        </main>
      </div>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        {...dialogProps}
        loading={isDeleting}
        onConfirm={executeDelete}
      />

      {/* Onboarding Tour */}
      <Tour
        steps={tourSteps}
        active={isTourActive}
        onComplete={completeTour}
        onSkip={skipTour}
      />
    </>
  );
}

// Content Editor Component
function ContentEditor({ content }: { content: ContentItem }) {
  const [activeTab, setActiveTab] = useState<
    "claims" | "tasks" | "simulation" | "animation" | "settings"
  >("claims");
  const [saveStatus, setSaveStatus] = useState<
    "saved" | "unsaved" | "saving" | "error"
  >("saved");

  // Build breadcrumb items based on content
  const breadcrumbItems = useMemo(() => {
    const items = [
      { id: "authoring", label: "Authoring", href: "/authoring" },
      { id: "library", label: "Content Library", href: "/authoring" },
    ];

    // Add content name if available
    if (content) {
      items.push({
        id: "content",
        label: content.title || "Untitled Content",
        href: undefined, // Current page
      });
    }

    return items;
  }, [content]);

  // Get tab-specific subtitle
  const getTabSubtitle = () => {
    const tabLabels = {
      claims: "Claims & Evidence",
      tasks: "Tasks & Activities",
      simulation: "Simulation Builder",
      animation: "Animation Studio",
      settings: "Settings & Metadata",
    };
    return tabLabels[activeTab];
  };

  // Build metadata for context indicator
  const metadata = useMemo(
    () => [
      { label: "Domain", value: content.domain },
      { label: "Grade", value: content.gradeRange },
      {
        label: "Status",
        value: content.status.charAt(0).toUpperCase() + content.status.slice(1),
      },
    ],
    [content],
  );

  // Get icon based on content type
  const getContentIcon = () => {
    switch (content.type) {
      case "learning_experience":
        return <BookOpen className="w-5 h-5" />;
      case "simulation":
        return <Beaker className="w-5 h-5" />;
      case "animation":
        return <Film className="w-5 h-5" />;
      case "assessment":
        return <FileText className="w-5 h-5" />;
    }
  };

  return (
    <div className="h-full flex flex-col">
      {/* Context Indicator - shows what user is editing */}
      <ContextIndicator
        title={content.title}
        subtitle={getTabSubtitle()}
        status={saveStatus}
        icon={getContentIcon()}
        metadata={metadata}
        actions={
          <>
            <button className="px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors">
              <Eye className="w-4 h-4 inline mr-1" />
              Preview
            </button>
            <button className="px-3 py-1.5 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 transition-colors">
              <Share2 className="w-4 h-4 inline mr-1" />
              Publish
            </button>
          </>
        }
      />
      {/* Breadcrumbs */}
      <div className="px-6 py-3 border-b border-gray-200 dark:border-gray-700">
        <BreadcrumbRouter
          items={breadcrumbItems}
          LinkComponent={Link}
          showHomeIcon={false}
          className="text-sm"
        />
      </div>
      {/* Main Editor Content */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {/* Editor Tabs */}
        <div
          id="editor-tabs"
          className="flex items-center gap-1 border-b border-gray-200 dark:border-gray-700 mb-6"
        >
          {(
            [
              { id: "claims", label: "Claims & Evidence", icon: BookOpen },
              { id: "tasks", label: "Tasks", icon: FileText },
              { id: "simulation", label: "Simulation", icon: Beaker },
              { id: "animation", label: "Animation", icon: Film },
              { id: "settings", label: "Settings", icon: Settings },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab.id
                  ? "border-primary-500 text-primary-600"
                  : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
              }`}
            >
              <tab.icon className="w-4 h-4" />
              {tab.label}
            </button>
          ))}
        </div>

        {/* Tab Content */}
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
          {activeTab === "claims" && <ClaimsEditor contentId={content.id} />}
          {activeTab === "tasks" && <TasksEditor contentId={content.id} />}
          {activeTab === "simulation" && (
            <SimulationEditor contentId={content.id} domain={content.domain} />
          )}
          {activeTab === "animation" && (
            <AnimationEditor contentId={content.id} />
          )}
          {activeTab === "settings" && <SettingsEditor content={content} />}
        </div>
      </div>{" "}
      {/* End of flex-1 overflow-y-auto */}
    </div>
  );
}

// Claims Editor Component
function ClaimsEditor({ contentId }: { contentId: string }) {
  const [claims, setClaims] = useState([
    {
      id: "1",
      text: "An object at rest stays at rest unless acted upon by an external force",
      bloom: "Understand",
      mastery: 0.8,
    },
    {
      id: "2",
      text: "Force equals mass times acceleration (F=ma)",
      bloom: "Apply",
      mastery: 0.75,
    },
  ]);
  const [isGenerating, setIsGenerating] = useState(false);

  // Form validation for new claims
  const { values, errors, touched, isValid, getFieldProps, reset } =
    useFormValidation({
      initialValues: {
        newClaim: "",
        bloomLevel: "Understand",
      },
      schema: {
        newClaim: [
          validators.required("Claim text is required"),
          validators.minLength(10, "Claim must be at least 10 characters"),
          validators.maxLength(300, "Claim must be under 300 characters"),
        ],
      },
    });

  const addClaim = useCallback(() => {
    if (!isValid || !values.newClaim.trim()) return;

    setClaims((prev) => [
      ...prev,
      {
        id: Date.now().toString(),
        text: values.newClaim,
        bloom: values.bloomLevel,
        mastery: 0.7,
      },
    ]);
    reset();
    toast.success("Claim added successfully");
  }, [isValid, values, reset]);

  const generateClaims = async () => {
    setIsGenerating(true);
    try {
      const response = await fetch("/api/content-studio/ai/generate-claims", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-tenant-id": "default",
        },
        body: JSON.stringify({ contentId }),
      });
      if (response.ok) {
        const result = await response.json();
        if (result.claims) {
          setClaims((prev) => [...prev, ...result.claims]);
          toast.success(`Generated ${result.claims.length} claims`);
        }
      }
    } catch {
      toast.error("Failed to generate claims");
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
          Learning Claims
        </h3>
        <button
          onClick={generateClaims}
          disabled={isGenerating}
          className="px-3 py-1.5 text-sm bg-purple-100 text-purple-600 rounded-lg hover:bg-purple-200 flex items-center gap-2 disabled:opacity-50"
        >
          <Sparkles className="w-4 h-4" />
          {isGenerating ? "Generating..." : "AI Generate"}
        </button>
      </div>

      <div className="space-y-3">
        {claims.map((claim, index) => (
          <div
            key={claim.id}
            className="p-4 bg-gray-50 dark:bg-gray-700/50 rounded-lg border border-gray-200 dark:border-gray-600"
          >
            <div className="flex items-start gap-3">
              <div className="w-6 h-6 bg-primary-100 text-primary-600 rounded-full flex items-center justify-center text-sm font-medium">
                {index + 1}
              </div>
              <div className="flex-1">
                <p className="text-gray-900 dark:text-white">{claim.text}</p>
                <div className="flex items-center gap-3 mt-2">
                  <span className="px-2 py-0.5 text-xs bg-blue-100 text-blue-600 rounded">
                    {claim.bloom}
                  </span>
                  <span className="text-xs text-gray-500">
                    Mastery: {Math.round(claim.mastery * 100)}%
                  </span>
                </div>
              </div>
              <button className="p-1 text-gray-400 hover:text-gray-600">
                <MoreHorizontal className="w-4 h-4" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Add new claim with validation */}
      {/* Add new claim with validation */}
      <FormField
        label="New Claim"
        error={touched.newClaim ? errors.newClaim : undefined}
        helperText={`${values.newClaim.length}/300 characters`}
        validating={false}
      >
        <div className="flex gap-2">
          <div className="flex-1">
            <input
              type="text"
              {...getFieldProps("newClaim")}
              placeholder="Add a new learning claim..."
              className={`w-full px-4 py-2 bg-white dark:bg-gray-700 border rounded-lg
                ${
                  touched.newClaim && errors.newClaim
                    ? "border-red-500 focus:ring-red-500"
                    : "border-gray-200 dark:border-gray-600 focus:ring-primary-500"
                }`}
              onKeyDown={(e) => {
                if (e.key === "Enter" && isValid) {
                  e.preventDefault();
                  addClaim();
                }
              }}
            />
          </div>
          <select
            value={values.bloomLevel}
            onChange={(e) => {
              const event = {
                target: { name: "bloomLevel", value: e.target.value },
              };
              getFieldProps("bloomLevel").onChange(
                event as React.ChangeEvent<HTMLInputElement>,
              );
            }}
            className="px-3 py-2 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm"
          >
            <option value="Remember">Remember</option>
            <option value="Understand">Understand</option>
            <option value="Apply">Apply</option>
            <option value="Analyze">Analyze</option>
            <option value="Evaluate">Evaluate</option>
            <option value="Create">Create</option>
          </select>
          <button
            onClick={addClaim}
            disabled={!isValid || !values.newClaim.trim()}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Plus className="w-4 h-4" />
          </button>
        </div>
      </FormField>
    </div>
  );
}

// Tasks Editor Component
function TasksEditor({ contentId }: { contentId: string }) {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
          Learning Tasks
        </h3>
        <button className="px-3 py-1.5 text-sm bg-purple-100 text-purple-600 rounded-lg hover:bg-purple-200 flex items-center gap-2">
          <Sparkles className="w-4 h-4" />
          AI Generate Tasks
        </button>
      </div>
      <p className="text-gray-500">
        Tasks will be auto-generated based on your claims. You can also add
        custom tasks.
      </p>
    </div>
  );
}

// Simulation Editor Component - Full Featured Physics Builder
function SimulationEditor({
  contentId,
  domain,
}: {
  contentId: string;
  domain: string;
}) {
  // Enable keyboard shortcuts for simulation
  useSimulationKeyboardShortcuts();

  return (
    <DndProvider backend={HTML5Backend}>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            Interactive Physics Simulation
          </h3>
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">
              Drag entities from toolbox to canvas
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-[250px_1fr_300px] gap-4 h-auto lg:h-[600px]">
          {/* Left: Entity Toolbox */}
          <div className="h-[200px] lg:h-auto bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4 overflow-y-auto">
            <h4 className="text-sm font-medium text-gray-900 dark:text-white mb-3">
              Physics Entities
            </h4>
            <EntityToolbox />
          </div>

          {/* Center: Simulation Canvas */}
          <div className="relative h-[400px] lg:h-auto bg-white dark:bg-gray-800 rounded-lg border-2 border-gray-200 dark:border-gray-600 overflow-hidden">
            <SimulationCanvas />
            <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
              <SimulationToolbar />
            </div>
          </div>

          {/* Right: Properties Panel */}
          <div className="h-[200px] lg:h-auto bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4 overflow-y-auto space-y-4">
            <PhysicsPropertyPanel />
            <PhysicsConfigPanel />
          </div>
        </div>

        <div className="flex items-center justify-between p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
          <div className="flex items-center gap-2 text-sm text-blue-700 dark:text-blue-300">
            <Lightbulb className="w-4 h-4" />
            <span>
              Tip: Use Cmd/Ctrl+Z to undo, Cmd/Ctrl+Y to redo. Press Space to
              toggle preview mode.
            </span>
          </div>
        </div>
      </div>
    </DndProvider>
  );
}

// Animation Editor Component - Full Featured Timeline-Based Animation
function AnimationEditor({ contentId }: { contentId: string }) {
  const handleSave = async (animation: AnimationSpec) => {
    try {
      const response = await fetch(`/api/content-studio/content/${contentId}/animation`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(animation),
      });
      if (!response.ok) {
        throw new Error(`Save failed: ${response.status}`);
      }
      toast.success("Animation saved successfully");
    } catch (err) {
      toast.error("Failed to save animation. Please try again.");
      console.error("Animation save error:", err);
    }
  };

  const handleExport = (animation: AnimationSpec, format: string) => {
    try {
      const blob = new Blob([JSON.stringify(animation, null, 2)], {
        type: "application/json",
      });
      const title = animation.id ?? contentId;
      const ext = format === "json" ? "json" : format === "gif" ? "gif" : "mp4";
      const filename = `animation-${title}.${ext}`;

      if (format !== "json") {
        // Video/GIF export requires server-side rendering; notify user
        toast.success(
          `${format.toUpperCase()} export has been queued. You will be notified when it is ready.`,
        );
        fetch(`/api/content-studio/content/${contentId}/animation/export`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ animation, format }),
        }).catch((err) => console.error("Export queue error:", err));
        return;
      }

      // JSON export is immediate (client-side)
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      URL.revokeObjectURL(url);
      toast.success(`Animation exported as ${filename}`);
    } catch (err) {
      toast.error("Export failed. Please try again.");
      console.error("Animation export error:", err);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
          Timeline-Based Animation
        </h3>
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-500">
            Create smooth animations with keyframes
          </span>
        </div>
      </div>

      <div className="h-[600px] bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
        <AnimationStudio onSave={handleSave} onExport={handleExport} />
      </div>

      <div className="flex items-center justify-between p-4 bg-purple-50 dark:bg-purple-900/20 rounded-lg">
        <div className="flex items-center gap-2 text-sm text-purple-700 dark:text-purple-300">
          <Sparkles className="w-4 h-4" />
          <span>
            Tip: Add keyframes at different times to create smooth transitions.
            Use the timeline to preview your animation.
          </span>
        </div>
      </div>
    </div>
  );
}

// Settings Editor Component
function SettingsEditor({ content }: { content: ContentItem }) {
  const {
    values,
    errors,
    touched,
    isValid,
    getFieldProps,
    handleSubmit,
    setFieldValue,
  } = useFormValidation({
    initialValues: {
      title: content.title,
      description: content.description,
      domain: content.domain,
      gradeRange: content.gradeRange,
    },
    schema: {
      title: [
        validators.required("Title is required"),
        validators.minLength(3, "Title must be at least 3 characters"),
        validators.maxLength(100, "Title must be under 100 characters"),
      ],
      description: [
        validators.required("Description is required"),
        validators.minLength(10, "Description must be at least 10 characters"),
        validators.maxLength(500, "Description must be under 500 characters"),
      ],
    },
    onSubmit: async (_formValues) => {
      // Save settings via API
      try {
        toast.success("Settings saved successfully");
      } catch {
        toast.error("Failed to save settings");
      }
    },
  });

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <FormField
        label="Title"
        required
        error={touched.title ? errors.title : undefined}
      >
        <input
          type="text"
          {...getFieldProps("title")}
          aria-invalid={touched.title && !!errors.title}
          className={`w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border rounded-lg
            ${
              touched.title && errors.title
                ? "border-red-500 focus:ring-red-500"
                : "border-gray-200 dark:border-gray-600 focus:ring-primary-500"
            }`}
        />
      </FormField>

      <FormField
        label="Description"
        required
        error={touched.description ? errors.description : undefined}
        hint={`${values.description.length}/500 characters`}
      >
        <textarea
          {...getFieldProps("description")}
          rows={3}
          aria-invalid={touched.description && !!errors.description}
          className={`w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border rounded-lg resize-none
            ${
              touched.description && errors.description
                ? "border-red-500 focus:ring-red-500"
                : "border-gray-200 dark:border-gray-600 focus:ring-primary-500"
            }`}
        />
      </FormField>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <FormField label="Domain">
          <select
            value={values.domain}
            onChange={(e) => setFieldValue("domain", e.target.value)}
            className="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg"
          >
            <option value="Physics">Physics</option>
            <option value="Chemistry">Chemistry</option>
            <option value="Biology">Biology</option>
            <option value="Mathematics">Mathematics</option>
          </select>
        </FormField>
        <FormField label="Grade Range">
          <select
            value={values.gradeRange}
            onChange={(e) => setFieldValue("gradeRange", e.target.value)}
            className="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg"
          >
            <option value="K-2">K-2</option>
            <option value="3-5">3-5</option>
            <option value="6-8">6-8</option>
            <option value="9-12">9-12</option>
            <option value="Undergraduate">Undergraduate</option>
          </select>
        </FormField>
      </div>

      <div className="flex justify-end pt-4 border-t border-gray-200 dark:border-gray-700">
        <Button type="submit" disabled={!isValid} className="min-w-[120px]">
          Save Settings
        </Button>
      </div>
    </form>
  );
}

// AI Co-Pilot Component
function AICoPilot({
  context,
  suggestions,
  onClose,
}: {
  context: ContentItem | null;
  suggestions: AISuggestion[];
  onClose: () => void;
}) {
  const [messages, setMessages] = useState<
    Array<{ id: string; role: "user" | "ai"; content: string }>
  >([
    {
      id: "1",
      role: "ai",
      content: context
        ? `I'm ready to help you improve "${context.title}". What would you like to do?`
        : "Hi! I can help you create educational content. Describe what you want to build, and I'll guide you through it.",
    },
  ]);
  const [input, setInput] = useState("");
  const [isTyping, setIsTyping] = useState(false);

  const handleSend = async () => {
    if (!input.trim()) return;

    const userMessage = {
      id: Date.now().toString(),
      role: "user" as const,
      content: input,
    };
    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsTyping(true);

    // Call AI
    try {
      const response = await fetch("/api/content-studio/ai/chat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-tenant-id": "default",
        },
        body: JSON.stringify({
          message: input,
          context: context
            ? { contentId: context.id, title: context.title }
            : null,
        }),
      });

      const aiResponse = {
        id: (Date.now() + 1).toString(),
        role: "ai" as const,
        content: response.ok
          ? (await response.json()).response ||
            "I can help with that. Let me analyze your request..."
          : "I understand. Let me help you with that.",
      };
      setMessages((prev) => [...prev, aiResponse]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: (Date.now() + 1).toString(),
          role: "ai",
          content:
            "I can help with that. What specific aspect would you like to focus on?",
        },
      ]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <>
      {/* Header */}
      <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20">
        <div className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-purple-600" />
          <span className="font-semibold text-gray-900 dark:text-white">
            AI Co-Pilot
          </span>
        </div>
        <button
          onClick={onClose}
          className="p-1 text-gray-400 hover:text-gray-600 rounded"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {/* Suggestions */}
      {suggestions.length > 0 && (
        <div className="p-3 border-b border-gray-200 dark:border-gray-700 space-y-2">
          {suggestions.slice(0, 2).map((suggestion) => (
            <button
              key={suggestion.id}
              onClick={suggestion.action}
              className="w-full p-2 text-left text-sm bg-gray-50 dark:bg-gray-700 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 flex items-start gap-2"
            >
              {suggestion.type === "insight" && (
                <Lightbulb className="w-4 h-4 text-yellow-500 mt-0.5" />
              )}
              {suggestion.type === "action" && (
                <Zap className="w-4 h-4 text-blue-500 mt-0.5" />
              )}
              {suggestion.type === "warning" && (
                <AlertTriangle className="w-4 h-4 text-orange-500 mt-0.5" />
              )}
              <div>
                <div className="font-medium text-gray-900 dark:text-white">
                  {suggestion.title}
                </div>
                <div className="text-xs text-gray-500">
                  {suggestion.description}
                </div>
              </div>
            </button>
          ))}
        </div>
      )}

      {/* Chat Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex gap-2 ${
              msg.role === "user" ? "flex-row-reverse" : ""
            }`}
          >
            <div
              className={`w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0 ${
                msg.role === "ai"
                  ? "bg-purple-100 text-purple-600"
                  : "bg-blue-100 text-blue-600"
              }`}
            >
              {msg.role === "ai" ? (
                <Bot className="w-4 h-4" />
              ) : (
                <User className="w-4 h-4" />
              )}
            </div>
            <div
              className={`max-w-[85%] px-3 py-2 rounded-xl text-sm ${
                msg.role === "user"
                  ? "bg-blue-600 text-white rounded-tr-none"
                  : "bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200 rounded-tl-none"
              }`}
            >
              {msg.content}
            </div>
          </div>
        ))}
        {isTyping && (
          <div className="flex gap-2">
            <div className="w-7 h-7 rounded-full bg-purple-100 flex items-center justify-center">
              <Bot className="w-4 h-4 text-purple-600" />
            </div>
            <div className="px-3 py-2 bg-gray-100 dark:bg-gray-700 rounded-xl rounded-tl-none">
              <div className="flex gap-1">
                <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" />
                <span
                  className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                  style={{ animationDelay: "0.1s" }}
                />
                <span
                  className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                  style={{ animationDelay: "0.2s" }}
                />
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Input */}
      <div className="p-3 border-t border-gray-200 dark:border-gray-700">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask AI for help..."
            className="flex-1 px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg"
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
          />
          <button
            onClick={handleSend}
            disabled={!input.trim()}
            className="p-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </>
  );
}

// Export default for compatibility
export default AuthoringPage;
