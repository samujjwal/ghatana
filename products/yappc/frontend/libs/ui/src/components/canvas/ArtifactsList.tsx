/**
 * Artifacts List Component
 *
 * @description Displays a list of generated artifacts from the bootstrapping
 * process including documents, code files, diagrams, and exports with
 * preview and download capabilities.
 *
 * @doc.type component
 * @doc.purpose Generated artifacts display
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  FileText,
  FileCode,
  Image,
  FileJson,
  FileSpreadsheet,
  Download,
  ExternalLink,
  Eye,
  ChevronDown,
  ChevronRight,
  Search,
  Filter,
  FolderOpen,
  Clock,
  CheckCircle2,
  AlertCircle,
  RefreshCw,
  Package,
  FileArchive,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

// =============================================================================
// Types
// =============================================================================

export type ArtifactType = 
  | 'document' 
  | 'code' 
  | 'diagram' 
  | 'config' 
  | 'data' 
  | 'image' 
  | 'archive'
  | 'other';

export type ArtifactStatus = 'generating' | 'ready' | 'error' | 'outdated';

export interface Artifact {
  id: string;
  name: string;
  type: ArtifactType;
  status: ArtifactStatus;
  format: string;
  size?: number;
  url?: string;
  previewUrl?: string;
  createdAt: string;
  updatedAt?: string;
  category?: string;
  description?: string;
  metadata?: Record<string, unknown>;
}

export interface ArtifactsListProps {
  /** List of artifacts */
  artifacts: Artifact[];
  /** Called when artifact is downloaded */
  onDownload: (artifact: Artifact) => void;
  /** Called when artifact is previewed */
  onPreview?: (artifact: Artifact) => void;
  /** Called when artifact is opened externally */
  onOpenExternal?: (artifact: Artifact) => void;
  /** Called when artifact is regenerated */
  onRegenerate?: (artifact: Artifact) => Promise<void>;
  /** Called when all artifacts are downloaded */
  onDownloadAll?: () => void;
  /** Show search */
  showSearch?: boolean;
  /** Show filters */
  showFilters?: boolean;
  /** Group by category */
  groupByCategory?: boolean;
  /** Collapsed by default */
  defaultCollapsed?: boolean;
  /** Loading state */
  loading?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const TYPE_CONFIG: Record<ArtifactType, {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  color: string;
}> = {
  document: {
    icon: FileText,
    label: 'Document',
    color: 'text-blue-500',
  },
  code: {
    icon: FileCode,
    label: 'Code',
    color: 'text-green-500',
  },
  diagram: {
    icon: Image,
    label: 'Diagram',
    color: 'text-purple-500',
  },
  config: {
    icon: FileJson,
    label: 'Config',
    color: 'text-amber-500',
  },
  data: {
    icon: FileSpreadsheet,
    label: 'Data',
    color: 'text-cyan-500',
  },
  image: {
    icon: Image,
    label: 'Image',
    color: 'text-pink-500',
  },
  archive: {
    icon: FileArchive,
    label: 'Archive',
    color: 'text-orange-500',
  },
  other: {
    icon: FileText,
    label: 'Other',
    color: 'text-neutral-500',
  },
};

const STATUS_CONFIG: Record<ArtifactStatus, {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  color: string;
}> = {
  generating: {
    icon: RefreshCw,
    label: 'Generating',
    color: 'text-blue-500',
  },
  ready: {
    icon: CheckCircle2,
    label: 'Ready',
    color: 'text-success-500',
  },
  error: {
    icon: AlertCircle,
    label: 'Error',
    color: 'text-error-500',
  },
  outdated: {
    icon: Clock,
    label: 'Outdated',
    color: 'text-warning-500',
  },
};

// =============================================================================
// Animation Variants
// =============================================================================

const listVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.03 },
  },
} as const;

const itemVariants = {
  hidden: { opacity: 0, x: -10 },
  visible: { opacity: 1, x: 0 },
} as const;

// =============================================================================
// Utility Functions
// =============================================================================

const formatFileSize = (bytes?: number): string => {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const formatDate = (date: string): string => {
  const d = new Date(date);
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(minutes / 60);
  
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  if (hours < 24) return `${hours}h ago`;
  return d.toLocaleDateString();
};

// =============================================================================
// Artifact Item
// =============================================================================

interface ArtifactItemProps {
  artifact: Artifact;
  onDownload: () => void;
  onPreview?: () => void;
  onOpenExternal?: () => void;
  onRegenerate?: () => void;
}

const ArtifactItem: React.FC<ArtifactItemProps> = ({
  artifact,
  onDownload,
  onPreview,
  onOpenExternal,
  onRegenerate,
}) => {
  const typeConfig = TYPE_CONFIG[artifact.type];
  const statusConfig = STATUS_CONFIG[artifact.status];
  const TypeIcon = typeConfig.icon;
  const StatusIcon = statusConfig.icon;

  const isGenerating = artifact.status === 'generating';
  const hasError = artifact.status === 'error';

  return (
    <motion.div
      variants={itemVariants}
      className={cn(
        'group flex items-center gap-3 rounded-lg border p-3 transition-all',
        'hover:border-primary-300 hover:bg-primary-50/50',
        'dark:hover:border-primary-700 dark:hover:bg-primary-950/20',
        hasError && 'border-error-200 bg-error-50/30 dark:border-error-800 dark:bg-error-950/20',
        'dark:border-neutral-700'
      )}
    >
      {/* Icon */}
      <div
        className={cn(
          'flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg',
          'bg-neutral-100 dark:bg-neutral-800',
          isGenerating && 'animate-pulse'
        )}
      >
        <TypeIcon className={cn('h-5 w-5', typeConfig.color)} />
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-medium text-neutral-900 dark:text-neutral-100 truncate">
            {artifact.name}
          </span>
          <Badge variant="outline" className="text-xs shrink-0">
            {artifact.format.toUpperCase()}
          </Badge>
        </div>
        <div className="mt-0.5 flex items-center gap-2 text-xs text-neutral-500">
          {artifact.size && (
            <span>{formatFileSize(artifact.size)}</span>
          )}
          <span>•</span>
          <span>{formatDate(artifact.createdAt)}</span>
          <span>•</span>
          <span className={cn('flex items-center gap-1', statusConfig.color)}>
            {isGenerating ? (
              <motion.div
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
              >
                <StatusIcon className="h-3 w-3" />
              </motion.div>
            ) : (
              <StatusIcon className="h-3 w-3" />
            )}
            {statusConfig.label}
          </span>
        </div>
        {artifact.description && (
          <p className="mt-1 text-xs text-neutral-500 truncate">
            {artifact.description}
          </p>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {onPreview && artifact.previewUrl && artifact.status === 'ready' && (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="sm" onClick={onPreview}>
                <Eye className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Preview</TooltipContent>
          </Tooltip>
        )}
        {onOpenExternal && artifact.url && artifact.status === 'ready' && (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="sm" onClick={onOpenExternal}>
                <ExternalLink className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Open</TooltipContent>
          </Tooltip>
        )}
        {onRegenerate && (artifact.status === 'error' || artifact.status === 'outdated') && (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="sm" onClick={onRegenerate}>
                <RefreshCw className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Regenerate</TooltipContent>
          </Tooltip>
        )}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="sm"
              onClick={onDownload}
              disabled={artifact.status !== 'ready'}
            >
              <Download className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Download</TooltipContent>
        </Tooltip>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Category Group
// =============================================================================

interface CategoryGroupProps {
  category: string;
  artifacts: Artifact[];
  onDownload: (artifact: Artifact) => void;
  onPreview?: (artifact: Artifact) => void;
  onOpenExternal?: (artifact: Artifact) => void;
  onRegenerate?: (artifact: Artifact) => void;
}

const CategoryGroup: React.FC<CategoryGroupProps> = ({
  category,
  artifacts,
  onDownload,
  onPreview,
  onOpenExternal,
  onRegenerate,
}) => {
  const [expanded, setExpanded] = useState(true);

  return (
    <div className="space-y-2">
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center gap-2 text-left"
      >
        {expanded ? (
          <ChevronDown className="h-4 w-4 text-neutral-500" />
        ) : (
          <ChevronRight className="h-4 w-4 text-neutral-500" />
        )}
        <FolderOpen className="h-4 w-4 text-amber-500" />
        <span className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
          {category}
        </span>
        <Badge variant="outline" className="text-xs">
          {artifacts.length}
        </Badge>
      </button>

      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="ml-6 space-y-2 overflow-hidden"
          >
            {artifacts.map((artifact) => (
              <ArtifactItem
                key={artifact.id}
                artifact={artifact}
                onDownload={() => onDownload(artifact)}
                onPreview={onPreview ? () => onPreview(artifact) : undefined}
                onOpenExternal={onOpenExternal ? () => onOpenExternal(artifact) : undefined}
                onRegenerate={onRegenerate ? () => onRegenerate(artifact) : undefined}
              />
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const ArtifactsList: React.FC<ArtifactsListProps> = ({
  artifacts,
  onDownload,
  onPreview,
  onOpenExternal,
  onRegenerate,
  onDownloadAll,
  showSearch = true,
  showFilters = true,
  groupByCategory = false,
  defaultCollapsed = false,
  // loading - reserved for future loading state display
  className,
}) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilters, setTypeFilters] = useState<ArtifactType[]>([]);
  const [statusFilters, setStatusFilters] = useState<ArtifactStatus[]>([]);

  // Filter artifacts
  const filteredArtifacts = useMemo(() => {
    return artifacts.filter((artifact) => {
      // Search filter
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        const matchesSearch =
          artifact.name.toLowerCase().includes(query) ||
          artifact.description?.toLowerCase().includes(query) ||
          artifact.format.toLowerCase().includes(query);
        if (!matchesSearch) return false;
      }

      // Type filter
      if (typeFilters.length > 0 && !typeFilters.includes(artifact.type)) {
        return false;
      }

      // Status filter
      if (statusFilters.length > 0 && !statusFilters.includes(artifact.status)) {
        return false;
      }

      return true;
    });
  }, [artifacts, searchQuery, typeFilters, statusFilters]);

  // Group by category
  const groupedArtifacts = useMemo(() => {
    if (!groupByCategory) return null;

    const groups: Record<string, Artifact[]> = {};
    filteredArtifacts.forEach((artifact) => {
      const category = artifact.category || 'Uncategorized';
      if (!groups[category]) groups[category] = [];
      groups[category].push(artifact);
    });
    return groups;
  }, [filteredArtifacts, groupByCategory]);

  // Stats
  const stats = useMemo(() => {
    const ready = artifacts.filter((a) => a.status === 'ready').length;
    const generating = artifacts.filter((a) => a.status === 'generating').length;
    const errors = artifacts.filter((a) => a.status === 'error').length;
    return { ready, generating, errors, total: artifacts.length };
  }, [artifacts]);

  const toggleTypeFilter = (type: ArtifactType) => {
    setTypeFilters((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]
    );
  };

  const toggleStatusFilter = (status: ArtifactStatus) => {
    setStatusFilters((prev) =>
      prev.includes(status) ? prev.filter((s) => s !== status) : [...prev, status]
    );
  };

  return (
    <div
      className={cn(
        'rounded-lg border bg-white dark:border-neutral-700 dark:bg-neutral-900',
        className
      )}
    >
      {/* Header */}
      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        className="flex w-full items-center justify-between p-4 text-left hover:bg-neutral-50 dark:hover:bg-neutral-800/50"
      >
        <div className="flex items-center gap-3">
          <Package className="h-5 w-5 text-primary-500" />
          <div>
            <span className="font-medium text-neutral-900 dark:text-neutral-100">
              Generated Artifacts
            </span>
            <div className="mt-0.5 flex items-center gap-2 text-xs text-neutral-500">
              <span>{stats.ready} ready</span>
              {stats.generating > 0 && (
                <>
                  <span>•</span>
                  <span className="text-blue-500">{stats.generating} generating</span>
                </>
              )}
              {stats.errors > 0 && (
                <>
                  <span>•</span>
                  <span className="text-error-500">{stats.errors} errors</span>
                </>
              )}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {onDownloadAll && stats.ready > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={(e: React.MouseEvent) => {
                e.stopPropagation();
                onDownloadAll();
              }}
            >
              <Download className="mr-1 h-4 w-4" />
              Download All
            </Button>
          )}
          <motion.div
            animate={{ rotate: collapsed ? 0 : 180 }}
            transition={{ duration: 0.2 }}
          >
            <ChevronDown className="h-5 w-5 text-neutral-500" />
          </motion.div>
        </div>
      </button>

      {/* Content */}
      <AnimatePresence>
        {!collapsed && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="border-t p-4 dark:border-neutral-700">
              {/* Search and filters */}
              {(showSearch || showFilters) && (
                <div className="mb-4 flex items-center gap-2">
                  {showSearch && (
                    <div className="relative flex-1">
                      <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                      <Input
                        type="text"
                        placeholder="Search artifacts..."
                        value={searchQuery}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
                        className="pl-9"
                      />
                    </div>
                  )}
                  {showFilters && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="outline" size="sm">
                          <Filter className="mr-1 h-4 w-4" />
                          Filter
                          {(typeFilters.length > 0 || statusFilters.length > 0) && (
                            <Badge variant="solid" className="ml-1 h-5 w-5 p-0 text-xs">
                              {typeFilters.length + statusFilters.length}
                            </Badge>
                          )}
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="w-48">
                        <div className="px-2 py-1.5 text-xs font-medium text-neutral-500">
                          Type
                        </div>
                        {Object.entries(TYPE_CONFIG).map(([type, config]) => (
                          <DropdownMenuCheckboxItem
                            key={type}
                            checked={typeFilters.includes(type as ArtifactType)}
                            onCheckedChange={() => toggleTypeFilter(type as ArtifactType)}
                          >
                            {config.label}
                          </DropdownMenuCheckboxItem>
                        ))}
                        <DropdownMenuSeparator />
                        <div className="px-2 py-1.5 text-xs font-medium text-neutral-500">
                          Status
                        </div>
                        {Object.entries(STATUS_CONFIG).map(([status, config]) => (
                          <DropdownMenuCheckboxItem
                            key={status}
                            checked={statusFilters.includes(status as ArtifactStatus)}
                            onCheckedChange={() => toggleStatusFilter(status as ArtifactStatus)}
                          >
                            {config.label}
                          </DropdownMenuCheckboxItem>
                        ))}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </div>
              )}

              {/* Artifacts list */}
              <motion.div
                variants={listVariants}
                initial="hidden"
                animate="visible"
                className="space-y-2"
              >
                {groupedArtifacts ? (
                  // Grouped view
                  Object.entries(groupedArtifacts).map(([category, items]) => (
                    <CategoryGroup
                      key={category}
                      category={category}
                      artifacts={items}
                      onDownload={onDownload}
                      onPreview={onPreview}
                      onOpenExternal={onOpenExternal}
                      onRegenerate={onRegenerate ? (a) => onRegenerate(a) : undefined}
                    />
                  ))
                ) : (
                  // Flat view
                  filteredArtifacts.map((artifact) => (
                    <ArtifactItem
                      key={artifact.id}
                      artifact={artifact}
                      onDownload={() => onDownload(artifact)}
                      onPreview={onPreview ? () => onPreview(artifact) : undefined}
                      onOpenExternal={onOpenExternal ? () => onOpenExternal(artifact) : undefined}
                      onRegenerate={onRegenerate ? () => onRegenerate(artifact) : undefined}
                    />
                  ))
                )}

                {filteredArtifacts.length === 0 && (
                  <div className="py-8 text-center text-neutral-500">
                    <Package className="mx-auto h-8 w-8 opacity-50" />
                    <p className="mt-2 text-sm">
                      {searchQuery || typeFilters.length > 0 || statusFilters.length > 0
                        ? 'No artifacts match your filters'
                        : 'No artifacts generated yet'}
                    </p>
                  </div>
                )}
              </motion.div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default ArtifactsList;
