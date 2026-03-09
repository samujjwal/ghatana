/**
 * Security Dashboard Component
 *
 * @description Comprehensive security overview with vulnerability tracking,
 * compliance status, security posture scoring, and threat detection.
 *
 * @doc.type component
 * @doc.purpose Security monitoring
 * @doc.layer presentation
 * @doc.phase security
 */

import React, { useState, useMemo, useCallback } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Shield,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
  AlertTriangle,
  AlertCircle,
  AlertOctagon,
  Bug,
  Lock,
  Unlock,
  Key,
  Eye,
  EyeOff,
  FileWarning,
  Server,
  Database,
  Globe,
  GitBranch,
  Clock,
  Calendar,
  TrendingUp,
  TrendingDown,
  ArrowRight,
  RefreshCw,
  Filter,
  Search,
  Download,
  ExternalLink,
  CheckCircle2,
  XCircle,
  MoreHorizontal,
  ChevronRight,
  ChevronDown,
  Target,
  Crosshair,
  Scan,
  Activity,
  BarChart3,
  PieChart,
  Layers,
} from 'lucide-react';
import { format, formatDistanceToNow } from 'date-fns';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Card, CardContent, CardHeader } from '@ghatana/ui';
import { CardDescription, CardTitle } from '@ghatana/yappc-ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
  DropdownMenuCheckboxItem,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';
import { ScrollArea } from '@ghatana/yappc-ui';
import { Progress } from '@ghatana/ui';
import { Tabs } from '@ghatana/ui';
import { TabsContent, TabsList, TabsTrigger } from '@ghatana/yappc-ui';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@ghatana/yappc-ui';

import {
  vulnerabilitiesAtom,
  complianceStatusAtom,
  securityScoreAtom,
  securityAlertsAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export type VulnerabilitySeverity = 'critical' | 'high' | 'medium' | 'low' | 'informational';
export type VulnerabilityStatus = 'open' | 'in_progress' | 'resolved' | 'accepted' | 'false_positive';
export type ScanType = 'sast' | 'dast' | 'sca' | 'container' | 'secret' | 'iac';

export interface Vulnerability {
  id: string;
  title: string;
  description: string;
  severity: VulnerabilitySeverity;
  status: VulnerabilityStatus;
  cveId?: string;
  cvssScore?: number;
  source: ScanType;
  component?: string;
  location?: {
    file?: string;
    line?: number;
    image?: string;
    package?: string;
  };
  firstDetected: string;
  lastSeen: string;
  slaDeadline?: string;
  slaBreach: boolean;
  remediation?: string;
  references?: string[];
  assignee?: {
    id: string;
    name: string;
  };
  tags: string[];
}

export interface ComplianceFramework {
  id: string;
  name: string;
  version?: string;
  totalControls: number;
  passedControls: number;
  failedControls: number;
  notApplicable: number;
  lastAssessment: string;
  nextAssessment?: string;
}

export interface SecurityScore {
  overall: number;
  trend: 'improving' | 'declining' | 'stable';
  trendValue: number;
  categories: Array<{
    name: string;
    score: number;
    weight: number;
    status: 'good' | 'warning' | 'critical';
  }>;
  history: Array<{
    date: string;
    score: number;
  }>;
}

export interface SecurityAlert {
  id: string;
  title: string;
  severity: VulnerabilitySeverity;
  type: 'threat' | 'anomaly' | 'policy' | 'compliance';
  source: string;
  timestamp: string;
  status: 'new' | 'acknowledged' | 'resolved';
  details?: string;
}

interface SecurityDashboardProps {
  onVulnerabilityClick?: (vulnerability: Vulnerability) => void;
  onRunScan?: (type: ScanType) => Promise<void>;
  onExportReport?: (type: 'pdf' | 'csv') => void;
  onRefresh?: () => void;
  isLoading?: boolean;
  className?: string;
}

// =============================================================================
// Subcomponents
// =============================================================================

const severityConfig: Record<VulnerabilitySeverity, {
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  bgColor: string;
  borderColor: string;
  label: string;
}> = {
  critical: {
    icon: AlertOctagon,
    color: 'text-red-500',
    bgColor: 'bg-red-500/10',
    borderColor: 'border-red-500',
    label: 'Critical',
  },
  high: {
    icon: AlertTriangle,
    color: 'text-orange-500',
    bgColor: 'bg-orange-500/10',
    borderColor: 'border-orange-500',
    label: 'High',
  },
  medium: {
    icon: AlertCircle,
    color: 'text-amber-500',
    bgColor: 'bg-amber-500/10',
    borderColor: 'border-amber-500',
    label: 'Medium',
  },
  low: {
    icon: ShieldAlert,
    color: 'text-blue-500',
    bgColor: 'bg-blue-500/10',
    borderColor: 'border-blue-500',
    label: 'Low',
  },
  informational: {
    icon: Shield,
    color: 'text-zinc-400',
    bgColor: 'bg-zinc-500/10',
    borderColor: 'border-zinc-500',
    label: 'Info',
  },
};

const scanTypeConfig: Record<ScanType, {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  description: string;
}> = {
  sast: { icon: FileWarning, label: 'SAST', description: 'Static Application Security Testing' },
  dast: { icon: Globe, label: 'DAST', description: 'Dynamic Application Security Testing' },
  sca: { icon: Layers, label: 'SCA', description: 'Software Composition Analysis' },
  container: { icon: Server, label: 'Container', description: 'Container Image Scanning' },
  secret: { icon: Key, label: 'Secrets', description: 'Secret Detection' },
  iac: { icon: GitBranch, label: 'IaC', description: 'Infrastructure as Code Scanning' },
};

const SecurityScoreGauge = React.memo(({ score, size = 'lg' }: { score: number; size?: 'sm' | 'md' | 'lg' }) => {
  const radius = size === 'lg' ? 80 : size === 'md' ? 60 : 40;
  const strokeWidth = size === 'lg' ? 12 : size === 'md' ? 10 : 8;
  const circumference = 2 * Math.PI * radius;
  const progress = (score / 100) * circumference;

  const getColor = (score: number) => {
    if (score >= 80) return '#22c55e';
    if (score >= 60) return '#eab308';
    if (score >= 40) return '#f97316';
    return '#ef4444';
  };

  return (
    <div className="relative inline-flex items-center justify-center">
      <svg
        width={(radius + strokeWidth) * 2}
        height={(radius + strokeWidth) * 2}
        className="transform -rotate-90"
      >
        {/* Background circle */}
        <circle
          cx={radius + strokeWidth}
          cy={radius + strokeWidth}
          r={radius}
          fill="none"
          stroke="#3f3f46"
          strokeWidth={strokeWidth}
        />
        {/* Progress circle */}
        <circle
          cx={radius + strokeWidth}
          cy={radius + strokeWidth}
          r={radius}
          fill="none"
          stroke={getColor(score)}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={circumference - progress}
          strokeLinecap="round"
          className="transition-all duration-1000"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span
          className={cn(
            'font-bold',
            size === 'lg' ? 'text-4xl' : size === 'md' ? 'text-2xl' : 'text-xl'
          )}
          style={{ color: getColor(score) }}
        >
          {score}
        </span>
        {size === 'lg' && <span className="text-xs text-zinc-500">Security Score</span>}
      </div>
    </div>
  );
});

SecurityScoreGauge.displayName = 'SecurityScoreGauge';

const VulnerabilityCard = React.memo(({
  vulnerability,
  onClick,
  isExpanded,
  onToggle,
}: {
  vulnerability: Vulnerability;
  onClick?: () => void;
  isExpanded?: boolean;
  onToggle?: () => void;
}) => {
  const severity = severityConfig[vulnerability.severity];
  const SeverityIcon = severity.icon;
  const scanType = scanTypeConfig[vulnerability.source];
  const ScanIcon = scanType.icon;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className={cn(
        'border-l-4 rounded-r-lg bg-zinc-900 overflow-hidden',
        severity.borderColor,
        vulnerability.slaBreach && 'ring-1 ring-red-500/50'
      )}
    >
      <Collapsible open={isExpanded} onOpenChange={onToggle}>
        <div
          className="flex items-start gap-3 p-4 cursor-pointer hover:bg-zinc-800/50 transition-colors"
          onClick={onClick}
        >
          <div className={cn('p-2 rounded-lg', severity.bgColor)}>
            <SeverityIcon className={cn('w-5 h-5', severity.color)} />
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <Badge className={cn('text-xs', severity.bgColor, severity.color)}>
                {severity.label}
              </Badge>
              <Badge variant="outline" className="text-xs">
                <ScanIcon className="w-3 h-3 mr-1" />
                {scanType.label}
              </Badge>
              {vulnerability.cveId && (
                <Badge variant="outline" className="text-xs font-mono">
                  {vulnerability.cveId}
                </Badge>
              )}
              {vulnerability.slaBreach && (
                <Badge variant="destructive" className="text-xs">
                  SLA Breach
                </Badge>
              )}
            </div>

            <h4 className="font-medium text-zinc-100 mb-1">{vulnerability.title}</h4>

            <div className="flex items-center gap-4 text-xs text-zinc-500">
              {vulnerability.component && (
                <span className="truncate max-w-[150px]">
                  {vulnerability.component}
                </span>
              )}
              {vulnerability.cvssScore !== undefined && (
                <span>CVSS: {vulnerability.cvssScore.toFixed(1)}</span>
              )}
              <span>
                Found {formatDistanceToNow(new Date(vulnerability.firstDetected), { addSuffix: true })}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Badge
              variant={
                vulnerability.status === 'resolved' ? 'secondary' :
                vulnerability.status === 'in_progress' ? 'default' :
                'outline'
              }
              className="text-xs"
            >
              {vulnerability.status.replace('_', ' ')}
            </Badge>

            <CollapsibleTrigger asChild onClick={(e) => e.stopPropagation()}>
              <Button variant="ghost" size="icon" className="h-8 w-8">
                {isExpanded ? (
                  <ChevronDown className="w-4 h-4" />
                ) : (
                  <ChevronRight className="w-4 h-4" />
                )}
              </Button>
            </CollapsibleTrigger>
          </div>
        </div>

        <CollapsibleContent>
          <div className="px-4 pb-4 pt-2 border-t border-zinc-800 space-y-3">
            <p className="text-sm text-zinc-400">{vulnerability.description}</p>

            {vulnerability.location && (
              <div className="flex items-center gap-2 text-sm">
                <FileWarning className="w-4 h-4 text-zinc-500" />
                <span className="text-zinc-400 font-mono text-xs">
                  {vulnerability.location.file && `${vulnerability.location.file}`}
                  {vulnerability.location.line && `:${vulnerability.location.line}`}
                  {vulnerability.location.package && vulnerability.location.package}
                  {vulnerability.location.image && vulnerability.location.image}
                </span>
              </div>
            )}

            {vulnerability.remediation && (
              <div className="p-3 rounded-lg bg-zinc-800/50">
                <p className="text-xs font-medium text-zinc-300 mb-1">Remediation</p>
                <p className="text-sm text-zinc-400">{vulnerability.remediation}</p>
              </div>
            )}

            {vulnerability.references && vulnerability.references.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {vulnerability.references.map((ref, index) => (
                  <a
                    key={index}
                    href={ref}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-xs text-blue-400 hover:text-blue-300"
                  >
                    <ExternalLink className="w-3 h-3" />
                    Reference {index + 1}
                  </a>
                ))}
              </div>
            )}

            {vulnerability.slaDeadline && (
              <div className="flex items-center gap-2 text-sm">
                <Clock className="w-4 h-4 text-zinc-500" />
                <span className={cn(
                  'text-xs',
                  vulnerability.slaBreach ? 'text-red-400' : 'text-zinc-400'
                )}>
                  SLA deadline: {format(new Date(vulnerability.slaDeadline), 'MMM d, yyyy')}
                </span>
              </div>
            )}
          </div>
        </CollapsibleContent>
      </Collapsible>
    </motion.div>
  );
});

VulnerabilityCard.displayName = 'VulnerabilityCard';

const ComplianceCard = React.memo(({ framework }: { framework: ComplianceFramework }) => {
  const passRate = Math.round((framework.passedControls / framework.totalControls) * 100);

  return (
    <Card className="bg-zinc-900 border-zinc-800">
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">{framework.name}</CardTitle>
          {framework.version && (
            <Badge variant="outline" className="text-xs">
              v{framework.version}
            </Badge>
          )}
        </div>
        <CardDescription>
          Last assessed {formatDistanceToNow(new Date(framework.lastAssessment), { addSuffix: true })}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {/* Progress */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-zinc-400">Compliance Rate</span>
              <span className={cn(
                'text-sm font-medium',
                passRate >= 80 ? 'text-green-500' :
                passRate >= 60 ? 'text-amber-500' : 'text-red-500'
              )}>
                {passRate}%
              </span>
            </div>
            <Progress
              value={passRate}
              className="h-2"
            />
          </div>

          {/* Controls breakdown */}
          <div className="grid grid-cols-3 gap-2">
            <div className="text-center p-2 rounded bg-green-500/10">
              <div className="text-lg font-semibold text-green-500">
                {framework.passedControls}
              </div>
              <div className="text-xs text-zinc-500">Passed</div>
            </div>
            <div className="text-center p-2 rounded bg-red-500/10">
              <div className="text-lg font-semibold text-red-500">
                {framework.failedControls}
              </div>
              <div className="text-xs text-zinc-500">Failed</div>
            </div>
            <div className="text-center p-2 rounded bg-zinc-500/10">
              <div className="text-lg font-semibold text-zinc-400">
                {framework.notApplicable}
              </div>
              <div className="text-xs text-zinc-500">N/A</div>
            </div>
          </div>

          {/* Next assessment */}
          {framework.nextAssessment && (
            <div className="flex items-center gap-2 text-xs text-zinc-500">
              <Calendar className="w-3.5 h-3.5" />
              Next: {format(new Date(framework.nextAssessment), 'MMM d, yyyy')}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
});

ComplianceCard.displayName = 'ComplianceCard';

// =============================================================================
// Main Component
// =============================================================================

export const SecurityDashboard: React.FC<SecurityDashboardProps> = ({
  onVulnerabilityClick,
  onRunScan,
  onExportReport,
  onRefresh,
  isLoading = false,
  className,
}) => {
  const vulnerabilities = useAtomValue(vulnerabilitiesAtom);
  const complianceStatus = useAtomValue(complianceStatusAtom);
  const securityScore = useAtomValue(securityScoreAtom);
  const securityAlerts = useAtomValue(securityAlertsAtom);

  const [searchQuery, setSearchQuery] = useState('');
  const [expandedVulns, setExpandedVulns] = useState<Set<string>>(new Set());
  const [filters, setFilters] = useState({
    critical: true,
    high: true,
    medium: true,
    low: true,
    informational: false,
    openOnly: true,
  });
  const [activeTab, setActiveTab] = useState<'overview' | 'vulnerabilities' | 'compliance'>('overview');

  // Stats
  const stats = useMemo(() => {
    const openVulns = vulnerabilities.filter((v) => v.status === 'open' || v.status === 'in_progress');
    const critical = openVulns.filter((v) => v.severity === 'critical').length;
    const high = openVulns.filter((v) => v.severity === 'high').length;
    const slaBreaches = openVulns.filter((v) => v.slaBreach).length;
    const newAlerts = securityAlerts.filter((a) => a.status === 'new').length;

    return {
      totalVulnerabilities: openVulns.length,
      critical,
      high,
      slaBreaches,
      newAlerts,
    };
  }, [vulnerabilities, securityAlerts]);

  // Filter vulnerabilities
  const filteredVulns = useMemo(() => {
    return vulnerabilities.filter((vuln) => {
      if (!filters[vuln.severity]) return false;
      if (filters.openOnly && (vuln.status === 'resolved' || vuln.status === 'false_positive')) return false;
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          vuln.title.toLowerCase().includes(query) ||
          vuln.cveId?.toLowerCase().includes(query) ||
          vuln.component?.toLowerCase().includes(query)
        );
      }
      return true;
    }).sort((a, b) => {
      const severityOrder: Record<VulnerabilitySeverity, number> = {
        critical: 0,
        high: 1,
        medium: 2,
        low: 3,
        informational: 4,
      };
      return severityOrder[a.severity] - severityOrder[b.severity];
    });
  }, [vulnerabilities, filters, searchQuery]);

  const toggleVulnExpand = useCallback((id: string) => {
    setExpandedVulns((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-zinc-800">
        <div>
          <h1 className="text-xl font-semibold text-zinc-100">Security Dashboard</h1>
          <p className="text-sm text-zinc-500">
            Monitor security posture and vulnerabilities
          </p>
        </div>

        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">
                <Scan className="w-4 h-4 mr-2" />
                Run Scan
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {Object.entries(scanTypeConfig).map(([type, config]) => (
                <DropdownMenuItem
                  key={type}
                  onClick={() => onRunScan?.(type as ScanType)}
                >
                  <config.icon className="w-4 h-4 mr-2" />
                  {config.label} - {config.description}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">
                <Download className="w-4 h-4 mr-2" />
                Export
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => onExportReport?.('pdf')}>
                Export as PDF
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onExportReport?.('csv')}>
                Export as CSV
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          <Button
            variant="outline"
            size="sm"
            onClick={onRefresh}
            disabled={isLoading}
          >
            <RefreshCw className={cn('w-4 h-4 mr-2', isLoading && 'animate-spin')} />
            Refresh
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <Tabs
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as typeof activeTab)}
        className="flex-1 flex flex-col"
      >
        <div className="px-4 py-2 border-b border-zinc-800">
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="vulnerabilities">
              Vulnerabilities
              {stats.totalVulnerabilities > 0 && (
                <Badge variant="secondary" className="ml-2">
                  {stats.totalVulnerabilities}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="compliance">Compliance</TabsTrigger>
          </TabsList>
        </div>

        {/* Overview Tab */}
        <TabsContent value="overview" className="flex-1 mt-0 overflow-auto">
          <div className="p-4 space-y-4">
            {/* Score and stats */}
            <div className="grid grid-cols-4 gap-4">
              {/* Security Score */}
              <Card className="bg-zinc-900 border-zinc-800 col-span-1">
                <CardContent className="p-6 flex flex-col items-center justify-center">
                  <SecurityScoreGauge score={securityScore.overall} />
                  <div className="flex items-center gap-2 mt-4">
                    {securityScore.trend === 'improving' && (
                      <>
                        <TrendingUp className="w-4 h-4 text-green-500" />
                        <span className="text-sm text-green-500">+{securityScore.trendValue}%</span>
                      </>
                    )}
                    {securityScore.trend === 'declining' && (
                      <>
                        <TrendingDown className="w-4 h-4 text-red-500" />
                        <span className="text-sm text-red-500">-{securityScore.trendValue}%</span>
                      </>
                    )}
                    {securityScore.trend === 'stable' && (
                      <span className="text-sm text-zinc-500">Stable</span>
                    )}
                    <span className="text-xs text-zinc-500">vs last week</span>
                  </div>
                </CardContent>
              </Card>

              {/* Stats grid */}
              <div className="col-span-3 grid grid-cols-4 gap-4">
                <Card className="bg-zinc-900 border-zinc-800">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-xs text-zinc-500">Open Vulnerabilities</p>
                        <p className="text-2xl font-bold text-zinc-100">
                          {stats.totalVulnerabilities}
                        </p>
                      </div>
                      <Bug className="w-8 h-8 text-amber-500" />
                    </div>
                  </CardContent>
                </Card>

                <Card className="bg-zinc-900 border-zinc-800">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-xs text-zinc-500">Critical</p>
                        <p className="text-2xl font-bold text-red-500">
                          {stats.critical}
                        </p>
                      </div>
                      <AlertOctagon className="w-8 h-8 text-red-500" />
                    </div>
                  </CardContent>
                </Card>

                <Card className="bg-zinc-900 border-zinc-800">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-xs text-zinc-500">SLA Breaches</p>
                        <p className="text-2xl font-bold text-orange-500">
                          {stats.slaBreaches}
                        </p>
                      </div>
                      <Clock className="w-8 h-8 text-orange-500" />
                    </div>
                  </CardContent>
                </Card>

                <Card className="bg-zinc-900 border-zinc-800">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-xs text-zinc-500">New Alerts</p>
                        <p className="text-2xl font-bold text-violet-500">
                          {stats.newAlerts}
                        </p>
                      </div>
                      <ShieldAlert className="w-8 h-8 text-violet-500" />
                    </div>
                  </CardContent>
                </Card>
              </div>
            </div>

            {/* Category scores */}
            <Card className="bg-zinc-900 border-zinc-800">
              <CardHeader>
                <CardTitle className="text-base">Security Categories</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-3 gap-4">
                  {securityScore.categories.map((category) => (
                    <div key={category.name} className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-sm text-zinc-400">{category.name}</span>
                        <span
                          className={cn(
                            'text-sm font-medium',
                            category.status === 'good' && 'text-green-500',
                            category.status === 'warning' && 'text-amber-500',
                            category.status === 'critical' && 'text-red-500'
                          )}
                        >
                          {category.score}%
                        </span>
                      </div>
                      <Progress value={category.score} className="h-2" />
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>

            {/* Compliance overview */}
            <div>
              <h3 className="text-sm font-medium text-zinc-300 mb-3">Compliance Status</h3>
              <div className="grid grid-cols-3 gap-4">
                {complianceStatus.map((framework) => (
                  <ComplianceCard key={framework.id} framework={framework} />
                ))}
              </div>
            </div>
          </div>
        </TabsContent>

        {/* Vulnerabilities Tab */}
        <TabsContent value="vulnerabilities" className="flex-1 mt-0 flex flex-col">
          {/* Search and filters */}
          <div className="flex items-center gap-2 p-4 border-b border-zinc-800">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
              <Input
                placeholder="Search vulnerabilities..."
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
                {Object.entries(severityConfig).map(([key, config]) => (
                  <DropdownMenuCheckboxItem
                    key={key}
                    checked={filters[key as VulnerabilitySeverity]}
                    onCheckedChange={(checked) =>
                      setFilters((f) => ({ ...f, [key]: checked }))
                    }
                  >
                    <config.icon className={cn('w-4 h-4 mr-2', config.color)} />
                    {config.label}
                  </DropdownMenuCheckboxItem>
                ))}
                <DropdownMenuSeparator />
                <DropdownMenuCheckboxItem
                  checked={filters.openOnly}
                  onCheckedChange={(checked) =>
                    setFilters((f) => ({ ...f, openOnly: checked }))
                  }
                >
                  Open issues only
                </DropdownMenuCheckboxItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>

          {/* Vulnerabilities list */}
          <ScrollArea className="flex-1">
            <div className="p-4 space-y-3">
              {filteredVulns.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <ShieldCheck className="w-12 h-12 text-green-500 mb-4" />
                  <p className="text-lg font-medium text-zinc-100">All clear!</p>
                  <p className="text-sm text-zinc-500 mt-1">
                    No vulnerabilities matching your filters
                  </p>
                </div>
              ) : (
                <AnimatePresence mode="popLayout">
                  {filteredVulns.map((vuln) => (
                    <VulnerabilityCard
                      key={vuln.id}
                      vulnerability={vuln}
                      onClick={() => onVulnerabilityClick?.(vuln)}
                      isExpanded={expandedVulns.has(vuln.id)}
                      onToggle={() => toggleVulnExpand(vuln.id)}
                    />
                  ))}
                </AnimatePresence>
              )}
            </div>
          </ScrollArea>
        </TabsContent>

        {/* Compliance Tab */}
        <TabsContent value="compliance" className="flex-1 mt-0 overflow-auto">
          <div className="p-4">
            <div className="grid grid-cols-2 gap-4">
              {complianceStatus.map((framework) => (
                <ComplianceCard key={framework.id} framework={framework} />
              ))}
            </div>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default SecurityDashboard;
