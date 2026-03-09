/**
 * Validation Panel Component
 *
 * @description Real-time validation feedback panel showing errors, warnings,
 * suggestions, and auto-fix capabilities for the bootstrap session.
 *
 * @doc.type component
 * @doc.purpose Validation feedback
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  AlertCircle,
  AlertTriangle,
  Info,
  CheckCircle2,
  ChevronRight,
  ChevronDown,
  Wand2,
  RefreshCw,
  Filter,
  Search,
  ArrowUpDown,
  ExternalLink,
  FileCode,
  Sparkles,
  X,
  Check,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@ghatana/yappc-ui';
import { ScrollArea } from '@ghatana/yappc-ui';
import { Progress } from '@ghatana/ui';

import { validationStateAtom } from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export type ValidationSeverity = 'error' | 'warning' | 'info' | 'success';

export interface ValidationIssue {
  id: string;
  severity: ValidationSeverity;
  category: string;
  message: string;
  details?: string;
  location?: {
    nodeId?: string;
    path?: string;
    line?: number;
    column?: number;
  };
  rule?: string;
  autoFixable: boolean;
  autoFixDescription?: string;
  documentationUrl?: string;
  suggestions?: string[];
}

export interface ValidationReport {
  id: string;
  timestamp: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  progress?: number;
  issues: ValidationIssue[];
  summary: {
    errors: number;
    warnings: number;
    info: number;
    passed: number;
  };
  categories: Array<{
    name: string;
    issues: number;
    status: 'pass' | 'fail' | 'warn';
  }>;
}

interface ValidationPanelProps {
  sessionId: string;
  report?: ValidationReport;
  onRunValidation?: () => Promise<void>;
  onAutoFix?: (issueId: string) => Promise<void>;
  onAutoFixAll?: () => Promise<void>;
  onIssueClick?: (issue: ValidationIssue) => void;
  onDismissIssue?: (issueId: string) => void;
  isValidating?: boolean;
  className?: string;
}

// =============================================================================
// Subcomponents
// =============================================================================

const SeverityIcon = ({ severity, className }: { severity: ValidationSeverity; className?: string }) => {
  const icons: Record<ValidationSeverity, React.ComponentType<{ className?: string }>> = {
    error: AlertCircle,
    warning: AlertTriangle,
    info: Info,
    success: CheckCircle2,
  };
  const Icon = icons[severity];
  
  const colors: Record<ValidationSeverity, string> = {
    error: 'text-red-500',
    warning: 'text-amber-500',
    info: 'text-blue-500',
    success: 'text-green-500',
  };

  return <Icon className={cn('w-4 h-4', colors[severity], className)} />;
};

const IssueCard = React.memo(({
  issue,
  isExpanded,
  onToggle,
  onAutoFix,
  onClick,
  onDismiss,
  isFixing,
}: {
  issue: ValidationIssue;
  isExpanded: boolean;
  onToggle: () => void;
  onAutoFix?: () => void;
  onClick?: () => void;
  onDismiss?: () => void;
  isFixing?: boolean;
}) => {
  const severityColors: Record<ValidationSeverity, string> = {
    error: 'border-l-red-500 bg-red-500/5 hover:bg-red-500/10',
    warning: 'border-l-amber-500 bg-amber-500/5 hover:bg-amber-500/10',
    info: 'border-l-blue-500 bg-blue-500/5 hover:bg-blue-500/10',
    success: 'border-l-green-500 bg-green-500/5 hover:bg-green-500/10',
  };

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className={cn(
        'border-l-4 rounded-r-lg transition-colors',
        severityColors[issue.severity]
      )}
    >
      <Collapsible open={isExpanded} onOpenChange={onToggle}>
        <div className="flex items-start gap-3 p-3">
          <SeverityIcon severity={issue.severity} className="mt-0.5 flex-shrink-0" />
          
          <div className="flex-1 min-w-0">
            <div className="flex items-start justify-between gap-2">
              <div
                className="flex-1 cursor-pointer"
                onClick={onClick}
              >
                <p className="text-sm font-medium text-zinc-100 leading-tight">
                  {issue.message}
                </p>
                <div className="flex items-center gap-2 mt-1">
                  <Badge variant="outline" className="text-xs">
                    {issue.category}
                  </Badge>
                  {issue.rule && (
                    <span className="text-xs text-zinc-500">{issue.rule}</span>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-1 flex-shrink-0">
                {issue.autoFixable && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7"
                        onClick={(e) => {
                          e.stopPropagation();
                          onAutoFix?.();
                        }}
                        disabled={isFixing}
                      >
                        {isFixing ? (
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <Wand2 className="w-3.5 h-3.5" />
                        )}
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Auto-fix</TooltipContent>
                  </Tooltip>
                )}

                {onDismiss && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7"
                        onClick={(e) => {
                          e.stopPropagation();
                          onDismiss();
                        }}
                      >
                        <X className="w-3.5 h-3.5" />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Dismiss</TooltipContent>
                  </Tooltip>
                )}

                <CollapsibleTrigger asChild>
                  <Button variant="ghost" size="icon" className="h-7 w-7">
                    {isExpanded ? (
                      <ChevronDown className="w-3.5 h-3.5" />
                    ) : (
                      <ChevronRight className="w-3.5 h-3.5" />
                    )}
                  </Button>
                </CollapsibleTrigger>
              </div>
            </div>
          </div>
        </div>

        <CollapsibleContent>
          <div className="px-3 pb-3 pl-10 space-y-3">
            {issue.details && (
              <p className="text-sm text-zinc-400">{issue.details}</p>
            )}

            {issue.location && (
              <div className="flex items-center gap-2 text-xs text-zinc-500">
                <FileCode className="w-3.5 h-3.5" />
                {issue.location.nodeId && <span>Node: {issue.location.nodeId}</span>}
                {issue.location.path && <span>{issue.location.path}</span>}
                {issue.location.line && (
                  <span>
                    Line {issue.location.line}
                    {issue.location.column && `:${issue.location.column}`}
                  </span>
                )}
              </div>
            )}

            {issue.autoFixable && issue.autoFixDescription && (
              <div className="flex items-start gap-2 p-2 rounded bg-zinc-800/50">
                <Wand2 className="w-4 h-4 text-violet-400 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-xs font-medium text-violet-400">Auto-fix available</p>
                  <p className="text-xs text-zinc-400">{issue.autoFixDescription}</p>
                </div>
              </div>
            )}

            {issue.suggestions && issue.suggestions.length > 0 && (
              <div className="space-y-1">
                <p className="text-xs font-medium text-zinc-400">Suggestions:</p>
                <ul className="space-y-1">
                  {issue.suggestions.map((suggestion, index) => (
                    <li
                      key={index}
                      className="flex items-start gap-2 text-xs text-zinc-400"
                    >
                      <Sparkles className="w-3 h-3 text-violet-400 flex-shrink-0 mt-0.5" />
                      {suggestion}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {issue.documentationUrl && (
              <a
                href={issue.documentationUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-xs text-blue-400 hover:text-blue-300"
              >
                <ExternalLink className="w-3 h-3" />
                Learn more
              </a>
            )}
          </div>
        </CollapsibleContent>
      </Collapsible>
    </motion.div>
  );
});

IssueCard.displayName = 'IssueCard';

const CategorySummary = React.memo(({
  categories,
}: {
  categories: ValidationReport['categories'];
}) => (
  <div className="grid grid-cols-2 gap-2 p-3 bg-zinc-800/50 rounded-lg">
    {categories.map((category) => (
      <div
        key={category.name}
        className="flex items-center justify-between p-2 rounded bg-zinc-900/50"
      >
        <span className="text-xs text-zinc-400">{category.name}</span>
        <div className="flex items-center gap-1">
          {category.status === 'pass' && (
            <CheckCircle2 className="w-3.5 h-3.5 text-green-500" />
          )}
          {category.status === 'fail' && (
            <AlertCircle className="w-3.5 h-3.5 text-red-500" />
          )}
          {category.status === 'warn' && (
            <AlertTriangle className="w-3.5 h-3.5 text-amber-500" />
          )}
          {category.issues > 0 && (
            <span className="text-xs text-zinc-500">{category.issues}</span>
          )}
        </div>
      </div>
    ))}
  </div>
));

CategorySummary.displayName = 'CategorySummary';

// =============================================================================
// Main Component
// =============================================================================

export const ValidationPanel: React.FC<ValidationPanelProps> = ({
  sessionId,
  report,
  onRunValidation,
  onAutoFix,
  onAutoFixAll,
  onIssueClick,
  onDismissIssue,
  isValidating = false,
  className,
}) => {
  const validationState = useAtomValue(validationStateAtom);

  const [searchQuery, setSearchQuery] = useState('');
  const [expandedIssues, setExpandedIssues] = useState<Set<string>>(new Set());
  const [fixingIssueId, setFixingIssueId] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    errors: true,
    warnings: true,
    info: true,
    autoFixableOnly: false,
  });
  const [sortBy, setSortBy] = useState<'severity' | 'category'>('severity');

  // Merge report from props and atom
  const activeReport = report || validationState.report;

  // Filter and sort issues
  const filteredIssues = useMemo(() => {
    if (!activeReport?.issues) return [];

    let issues = activeReport.issues.filter((issue) => {
      // Severity filter
      if (issue.severity === 'error' && !filters.errors) return false;
      if (issue.severity === 'warning' && !filters.warnings) return false;
      if (issue.severity === 'info' && !filters.info) return false;

      // Auto-fixable filter
      if (filters.autoFixableOnly && !issue.autoFixable) return false;

      // Search filter
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          issue.message.toLowerCase().includes(query) ||
          issue.category.toLowerCase().includes(query) ||
          issue.rule?.toLowerCase().includes(query)
        );
      }

      return true;
    });

    // Sort
    if (sortBy === 'severity') {
      const severityOrder: Record<ValidationSeverity, number> = {
        error: 0,
        warning: 1,
        info: 2,
        success: 3,
      };
      issues = issues.sort(
        (a, b) => severityOrder[a.severity] - severityOrder[b.severity]
      );
    } else {
      issues = issues.sort((a, b) => a.category.localeCompare(b.category));
    }

    return issues;
  }, [activeReport?.issues, filters, searchQuery, sortBy]);

  // Count auto-fixable issues
  const autoFixableCount = useMemo(
    () => filteredIssues.filter((i) => i.autoFixable).length,
    [filteredIssues]
  );

  const handleToggleExpand = useCallback((issueId: string) => {
    setExpandedIssues((prev) => {
      const next = new Set(prev);
      if (next.has(issueId)) {
        next.delete(issueId);
      } else {
        next.add(issueId);
      }
      return next;
    });
  }, []);

  const handleAutoFix = useCallback(
    async (issueId: string) => {
      if (!onAutoFix) return;
      setFixingIssueId(issueId);
      try {
        await onAutoFix(issueId);
      } finally {
        setFixingIssueId(null);
      }
    },
    [onAutoFix]
  );

  const handleAutoFixAll = useCallback(async () => {
    if (!onAutoFixAll) return;
    setFixingIssueId('all');
    try {
      await onAutoFixAll();
    } finally {
      setFixingIssueId(null);
    }
  }, [onAutoFixAll]);

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-zinc-800">
        <div>
          <h2 className="text-lg font-semibold text-zinc-100">Validation</h2>
          {activeReport && (
            <p className="text-xs text-zinc-500">
              Last run:{' '}
              {new Date(activeReport.timestamp).toLocaleTimeString()}
            </p>
          )}
        </div>

        <div className="flex items-center gap-2">
          {autoFixableCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleAutoFixAll}
              disabled={fixingIssueId === 'all'}
            >
              {fixingIssueId === 'all' ? (
                <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
              ) : (
                <Wand2 className="w-4 h-4 mr-2" />
              )}
              Fix All ({autoFixableCount})
            </Button>
          )}

          <Button
            onClick={onRunValidation}
            disabled={isValidating}
            className="bg-violet-600 hover:bg-violet-700"
          >
            {isValidating ? (
              <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
            ) : (
              <Check className="w-4 h-4 mr-2" />
            )}
            Validate
          </Button>
        </div>
      </div>

      {/* Progress bar for running validation */}
      {isValidating && activeReport?.progress !== undefined && (
        <div className="px-4 pt-3">
          <Progress value={activeReport.progress} className="h-1" />
          <p className="text-xs text-zinc-500 mt-1">
            Validating... {activeReport.progress}%
          </p>
        </div>
      )}

      {/* Summary stats */}
      {activeReport?.summary && (
        <div className="grid grid-cols-4 gap-2 p-4 border-b border-zinc-800">
          <div className="flex items-center gap-2 p-2 rounded bg-red-500/10">
            <AlertCircle className="w-4 h-4 text-red-500" />
            <div>
              <div className="text-lg font-semibold text-red-500">
                {activeReport.summary.errors}
              </div>
              <div className="text-xs text-zinc-500">Errors</div>
            </div>
          </div>
          <div className="flex items-center gap-2 p-2 rounded bg-amber-500/10">
            <AlertTriangle className="w-4 h-4 text-amber-500" />
            <div>
              <div className="text-lg font-semibold text-amber-500">
                {activeReport.summary.warnings}
              </div>
              <div className="text-xs text-zinc-500">Warnings</div>
            </div>
          </div>
          <div className="flex items-center gap-2 p-2 rounded bg-blue-500/10">
            <Info className="w-4 h-4 text-blue-500" />
            <div>
              <div className="text-lg font-semibold text-blue-500">
                {activeReport.summary.info}
              </div>
              <div className="text-xs text-zinc-500">Info</div>
            </div>
          </div>
          <div className="flex items-center gap-2 p-2 rounded bg-green-500/10">
            <CheckCircle2 className="w-4 h-4 text-green-500" />
            <div>
              <div className="text-lg font-semibold text-green-500">
                {activeReport.summary.passed}
              </div>
              <div className="text-xs text-zinc-500">Passed</div>
            </div>
          </div>
        </div>
      )}

      {/* Category summary */}
      {activeReport?.categories && activeReport.categories.length > 0 && (
        <div className="p-4 border-b border-zinc-800">
          <CategorySummary categories={activeReport.categories} />
        </div>
      )}

      {/* Filters and search */}
      <div className="flex items-center gap-2 p-4 border-b border-zinc-800">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
          <Input
            placeholder="Search issues..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-9"
          />
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="icon">
              <Filter className="w-4 h-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-48">
            <DropdownMenuCheckboxItem
              checked={filters.errors}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, errors: checked }))
              }
            >
              <AlertCircle className="w-4 h-4 mr-2 text-red-500" />
              Errors
            </DropdownMenuCheckboxItem>
            <DropdownMenuCheckboxItem
              checked={filters.warnings}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, warnings: checked }))
              }
            >
              <AlertTriangle className="w-4 h-4 mr-2 text-amber-500" />
              Warnings
            </DropdownMenuCheckboxItem>
            <DropdownMenuCheckboxItem
              checked={filters.info}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, info: checked }))
              }
            >
              <Info className="w-4 h-4 mr-2 text-blue-500" />
              Info
            </DropdownMenuCheckboxItem>
            <DropdownMenuSeparator />
            <DropdownMenuCheckboxItem
              checked={filters.autoFixableOnly}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, autoFixableOnly: checked }))
              }
            >
              <Wand2 className="w-4 h-4 mr-2" />
              Auto-fixable only
            </DropdownMenuCheckboxItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="icon">
              <ArrowUpDown className="w-4 h-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => setSortBy('severity')}>
              {sortBy === 'severity' && <Check className="w-4 h-4 mr-2" />}
              Sort by Severity
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setSortBy('category')}>
              {sortBy === 'category' && <Check className="w-4 h-4 mr-2" />}
              Sort by Category
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Issues list */}
      <ScrollArea className="flex-1">
        <div className="p-4 space-y-2">
          {!activeReport ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <div className="w-12 h-12 rounded-full bg-zinc-800 flex items-center justify-center mb-4">
                <Check className="w-6 h-6 text-zinc-500" />
              </div>
              <p className="text-sm text-zinc-400 mb-2">No validation run yet</p>
              <p className="text-xs text-zinc-500">
                Click "Validate" to check your project configuration
              </p>
            </div>
          ) : filteredIssues.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <div className="w-12 h-12 rounded-full bg-green-500/10 flex items-center justify-center mb-4">
                <CheckCircle2 className="w-6 h-6 text-green-500" />
              </div>
              <p className="text-sm text-zinc-400 mb-2">
                {searchQuery
                  ? 'No matching issues found'
                  : 'All validations passed!'}
              </p>
              <p className="text-xs text-zinc-500">
                {searchQuery
                  ? 'Try adjusting your search or filters'
                  : 'Your project configuration looks good'}
              </p>
            </div>
          ) : (
            <AnimatePresence mode="popLayout">
              {filteredIssues.map((issue) => (
                <IssueCard
                  key={issue.id}
                  issue={issue}
                  isExpanded={expandedIssues.has(issue.id)}
                  onToggle={() => handleToggleExpand(issue.id)}
                  onAutoFix={
                    issue.autoFixable ? () => handleAutoFix(issue.id) : undefined
                  }
                  onClick={() => onIssueClick?.(issue)}
                  onDismiss={
                    onDismissIssue ? () => onDismissIssue(issue.id) : undefined
                  }
                  isFixing={fixingIssueId === issue.id}
                />
              ))}
            </AnimatePresence>
          )}
        </div>
      </ScrollArea>
    </div>
  );
};

export default ValidationPanel;
