/**
 * Incident Dashboard Component
 *
 * @description Real-time incident monitoring dashboard with severity tracking,
 * timeline view, and war room quick access for operations phase.
 *
 * @doc.type component
 * @doc.purpose Incident management
 * @doc.layer presentation
 * @doc.phase operations
 */

import React, {
  useState,
  useCallback,
  useMemo,
  useEffect,
} from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  AlertTriangle,
  AlertCircle,
  AlertOctagon,
  Info,
  Clock,
  User,
  Users,
  MessageSquare,
  Phone,
  Video,
  ExternalLink,
  ChevronRight,
  Filter,
  Search,
  RefreshCw,
  Bell,
  BellOff,
  Activity,
  Zap,
  CheckCircle2,
  XCircle,
  Timer,
  TrendingUp,
  TrendingDown,
  MoreHorizontal,
  Plus,
  ArrowUpRight,
  Circle,
} from 'lucide-react';
import { formatDistanceToNow, format, differenceInMinutes } from 'date-fns';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Avatar } from '@ghatana/ui';
import { AvatarFallback, AvatarImage } from '@ghatana/yappc-ui';
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
import { Card, CardContent, CardHeader } from '@ghatana/ui';
import { CardTitle } from '@ghatana/yappc-ui';
import { ScrollArea } from '@ghatana/yappc-ui';
import { Progress } from '@ghatana/ui';
import { Tabs } from '@ghatana/ui';
import { TabsContent, TabsList, TabsTrigger } from '@ghatana/yappc-ui';

import {
  incidentsAtom,
  alertsAtom,
  activeIncidentAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export type IncidentSeverity = 'critical' | 'high' | 'medium' | 'low';
export type IncidentStatus = 'triggered' | 'acknowledged' | 'investigating' | 'identified' | 'monitoring' | 'resolved';

export interface Incident {
  id: string;
  title: string;
  description?: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  service: string;
  affectedServices: string[];
  startedAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
  commander?: {
    id: string;
    name: string;
    avatar?: string;
  };
  responders: Array<{
    id: string;
    name: string;
    avatar?: string;
    role?: string;
  }>;
  impactDescription?: string;
  customerImpact?: 'none' | 'minor' | 'major' | 'critical';
  timeline: Array<{
    id: string;
    timestamp: string;
    type: 'status_change' | 'message' | 'action' | 'escalation';
    actor: string;
    content: string;
  }>;
  relatedAlerts: number;
  slackChannel?: string;
  zoomLink?: string;
  runbookUrl?: string;
  tags: string[];
}

export interface Alert {
  id: string;
  title: string;
  severity: IncidentSeverity;
  status: 'firing' | 'acknowledged' | 'resolved';
  source: string;
  service: string;
  triggeredAt: string;
  acknowledgedBy?: string;
  incidentId?: string;
  count: number;
  lastOccurrence: string;
}

interface IncidentDashboardProps {
  onIncidentClick?: (incident: Incident) => void;
  onCreateIncident?: () => void;
  onJoinWarRoom?: (incidentId: string) => void;
  onAcknowledgeIncident?: (incidentId: string) => void;
  onResolveIncident?: (incidentId: string) => void;
  onRefresh?: () => void;
  isLoading?: boolean;
  className?: string;
}

// =============================================================================
// Subcomponents
// =============================================================================

const severityConfig: Record<IncidentSeverity, {
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  bgColor: string;
  borderColor: string;
}> = {
  critical: {
    icon: AlertOctagon,
    color: 'text-red-500',
    bgColor: 'bg-red-500/10',
    borderColor: 'border-red-500',
  },
  high: {
    icon: AlertTriangle,
    color: 'text-orange-500',
    bgColor: 'bg-orange-500/10',
    borderColor: 'border-orange-500',
  },
  medium: {
    icon: AlertCircle,
    color: 'text-amber-500',
    bgColor: 'bg-amber-500/10',
    borderColor: 'border-amber-500',
  },
  low: {
    icon: Info,
    color: 'text-blue-500',
    bgColor: 'bg-blue-500/10',
    borderColor: 'border-blue-500',
  },
};

const statusConfig: Record<IncidentStatus, {
  label: string;
  color: string;
  bgColor: string;
}> = {
  triggered: { label: 'Triggered', color: 'text-red-500', bgColor: 'bg-red-500/10' },
  acknowledged: { label: 'Acknowledged', color: 'text-orange-500', bgColor: 'bg-orange-500/10' },
  investigating: { label: 'Investigating', color: 'text-amber-500', bgColor: 'bg-amber-500/10' },
  identified: { label: 'Identified', color: 'text-blue-500', bgColor: 'bg-blue-500/10' },
  monitoring: { label: 'Monitoring', color: 'text-cyan-500', bgColor: 'bg-cyan-500/10' },
  resolved: { label: 'Resolved', color: 'text-green-500', bgColor: 'bg-green-500/10' },
};

const StatsCard = React.memo(({
  title,
  value,
  change,
  changeType,
  icon: Icon,
  iconColor,
}: {
  title: string;
  value: string | number;
  change?: string;
  changeType?: 'increase' | 'decrease' | 'neutral';
  icon: React.ComponentType<{ className?: string }>;
  iconColor: string;
}) => (
  <Card className="bg-zinc-900 border-zinc-800">
    <CardContent className="p-4">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs text-zinc-500 mb-1">{title}</p>
          <p className="text-2xl font-bold text-zinc-100">{value}</p>
          {change && (
            <div className="flex items-center gap-1 mt-1">
              {changeType === 'increase' && (
                <TrendingUp className="w-3 h-3 text-red-500" />
              )}
              {changeType === 'decrease' && (
                <TrendingDown className="w-3 h-3 text-green-500" />
              )}
              <span
                className={cn(
                  'text-xs',
                  changeType === 'increase' && 'text-red-500',
                  changeType === 'decrease' && 'text-green-500',
                  changeType === 'neutral' && 'text-zinc-500'
                )}
              >
                {change}
              </span>
            </div>
          )}
        </div>
        <div className={cn('p-3 rounded-lg', `${iconColor}/10`)}>
          <Icon className={cn('w-6 h-6', iconColor)} />
        </div>
      </div>
    </CardContent>
  </Card>
));

StatsCard.displayName = 'StatsCard';

const IncidentCard = React.memo(({
  incident,
  onClick,
  onJoinWarRoom,
  onAcknowledge,
  onResolve,
}: {
  incident: Incident;
  onClick?: () => void;
  onJoinWarRoom?: () => void;
  onAcknowledge?: () => void;
  onResolve?: () => void;
}) => {
  const severity = severityConfig[incident.severity];
  const status = statusConfig[incident.status];
  const SeverityIcon = severity.icon;

  const duration = useMemo(() => {
    const start = new Date(incident.startedAt);
    const end = incident.resolvedAt ? new Date(incident.resolvedAt) : new Date();
    const minutes = differenceInMinutes(end, start);
    if (minutes < 60) return `${minutes}m`;
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    if (hours < 24) return `${hours}h ${remainingMinutes}m`;
    const days = Math.floor(hours / 24);
    return `${days}d ${hours % 24}h`;
  }, [incident.startedAt, incident.resolvedAt]);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className={cn(
        'p-4 bg-zinc-900 rounded-lg border-l-4 cursor-pointer',
        'hover:bg-zinc-800/50 transition-colors',
        severity.borderColor
      )}
      onClick={onClick}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-start gap-3">
          <div className={cn('p-2 rounded-lg', severity.bgColor)}>
            <SeverityIcon className={cn('w-5 h-5', severity.color)} />
          </div>
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Badge className={cn('text-xs', status.bgColor, status.color)}>
                {status.label}
              </Badge>
              <Badge variant="outline" className="text-xs">
                {incident.severity}
              </Badge>
              {incident.customerImpact && incident.customerImpact !== 'none' && (
                <Badge variant="destructive" className="text-xs">
                  Customer Impact: {incident.customerImpact}
                </Badge>
              )}
            </div>
            <h3 className="font-semibold text-zinc-100">{incident.title}</h3>
            <p className="text-sm text-zinc-500 mt-1">
              {incident.service} • Started {formatDistanceToNow(new Date(incident.startedAt), { addSuffix: true })}
            </p>
          </div>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
            <Button variant="ghost" size="icon" className="h-8 w-8">
              <MoreHorizontal className="w-4 h-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {incident.status === 'triggered' && (
              <DropdownMenuItem onClick={(e) => { e.stopPropagation(); onAcknowledge?.(); }}>
                <CheckCircle2 className="w-4 h-4 mr-2" />
                Acknowledge
              </DropdownMenuItem>
            )}
            {incident.status !== 'resolved' && (
              <DropdownMenuItem onClick={(e) => { e.stopPropagation(); onResolve?.(); }}>
                <XCircle className="w-4 h-4 mr-2" />
                Resolve
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            {incident.runbookUrl && (
              <DropdownMenuItem>
                <ExternalLink className="w-4 h-4 mr-2" />
                Open Runbook
              </DropdownMenuItem>
            )}
            <DropdownMenuItem>
              <Users className="w-4 h-4 mr-2" />
              Page On-Call
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Impact description */}
      {incident.impactDescription && (
        <p className="text-sm text-zinc-400 mb-3 line-clamp-2">
          {incident.impactDescription}
        </p>
      )}

      {/* Metrics row */}
      <div className="flex items-center gap-4 mb-3 text-sm">
        <div className="flex items-center gap-1 text-zinc-400">
          <Timer className="w-4 h-4" />
          <span>{duration}</span>
        </div>
        <div className="flex items-center gap-1 text-zinc-400">
          <Activity className="w-4 h-4" />
          <span>{incident.relatedAlerts} alerts</span>
        </div>
        <div className="flex items-center gap-1 text-zinc-400">
          <MessageSquare className="w-4 h-4" />
          <span>{incident.timeline.length} updates</span>
        </div>
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between pt-3 border-t border-zinc-800">
        {/* Responders */}
        <div className="flex items-center gap-2">
          {incident.commander && (
            <Tooltip>
              <TooltipTrigger>
                <div className="relative">
                  <Avatar className="h-7 w-7 ring-2 ring-violet-500">
                    <AvatarImage src={incident.commander.avatar} />
                    <AvatarFallback className="text-xs">
                      {incident.commander.name.split(' ').map((n) => n[0]).join('')}
                    </AvatarFallback>
                  </Avatar>
                  <Zap className="absolute -bottom-1 -right-1 w-3 h-3 text-amber-500" />
                </div>
              </TooltipTrigger>
              <TooltipContent>
                Commander: {incident.commander.name}
              </TooltipContent>
            </Tooltip>
          )}

          {incident.responders.slice(0, 3).map((responder) => (
            <Tooltip key={responder.id}>
              <TooltipTrigger>
                <Avatar className="h-7 w-7">
                  <AvatarImage src={responder.avatar} />
                  <AvatarFallback className="text-xs">
                    {responder.name.split(' ').map((n) => n[0]).join('')}
                  </AvatarFallback>
                </Avatar>
              </TooltipTrigger>
              <TooltipContent>
                {responder.name}
                {responder.role && ` (${responder.role})`}
              </TooltipContent>
            </Tooltip>
          ))}

          {incident.responders.length > 3 && (
            <span className="text-xs text-zinc-500 ml-1">
              +{incident.responders.length - 3}
            </span>
          )}
        </div>

        {/* War room actions */}
        <div className="flex items-center gap-2">
          {incident.slackChannel && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={(e) => {
                    e.stopPropagation();
                    window.open(incident.slackChannel, '_blank');
                  }}
                >
                  <MessageSquare className="w-4 h-4 mr-1" />
                  Slack
                </Button>
              </TooltipTrigger>
              <TooltipContent>Join Slack channel</TooltipContent>
            </Tooltip>
          )}

          {incident.status !== 'resolved' && (
            <Button
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onJoinWarRoom?.();
              }}
              className="bg-violet-600 hover:bg-violet-700"
            >
              <Video className="w-4 h-4 mr-1" />
              Join War Room
            </Button>
          )}
        </div>
      </div>
    </motion.div>
  );
});

IncidentCard.displayName = 'IncidentCard';

const AlertItem = React.memo(({
  alert,
  onClick,
}: {
  alert: Alert;
  onClick?: () => void;
}) => {
  const severity = severityConfig[alert.severity];
  const SeverityIcon = severity.icon;

  return (
    <div
      onClick={onClick}
      className={cn(
        'flex items-center gap-3 p-3 rounded-lg cursor-pointer',
        'hover:bg-zinc-800/50 transition-colors',
        alert.status === 'firing' && severity.bgColor
      )}
    >
      <SeverityIcon className={cn('w-4 h-4 flex-shrink-0', severity.color)} />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-zinc-100 truncate">
          {alert.title}
        </p>
        <p className="text-xs text-zinc-500 truncate">
          {alert.service} • {alert.source}
        </p>
      </div>
      <div className="text-right flex-shrink-0">
        <p className="text-xs text-zinc-400">
          {formatDistanceToNow(new Date(alert.lastOccurrence), { addSuffix: true })}
        </p>
        {alert.count > 1 && (
          <Badge variant="secondary" className="text-xs mt-1">
            ×{alert.count}
          </Badge>
        )}
      </div>
    </div>
  );
});

AlertItem.displayName = 'AlertItem';

// =============================================================================
// Main Component
// =============================================================================

export const IncidentDashboard: React.FC<IncidentDashboardProps> = ({
  onIncidentClick,
  onCreateIncident,
  onJoinWarRoom,
  onAcknowledgeIncident,
  onResolveIncident,
  onRefresh,
  isLoading = false,
  className,
}) => {
  const incidents = useAtomValue(incidentsAtom);
  const alerts = useAtomValue(alertsAtom);
  const setActiveIncident = useSetAtom(activeIncidentAtom);

  const [searchQuery, setSearchQuery] = useState('');
  const [filters, setFilters] = useState({
    critical: true,
    high: true,
    medium: true,
    low: true,
    activeOnly: true,
  });
  const [activeTab, setActiveTab] = useState<'incidents' | 'alerts'>('incidents');

  // Stats
  const stats = useMemo(() => {
    const active = incidents.filter((i) => i.status !== 'resolved');
    const critical = active.filter((i) => i.severity === 'critical').length;
    const resolvedToday = incidents.filter((i) => {
      if (!i.resolvedAt) return false;
      const resolved = new Date(i.resolvedAt);
      const today = new Date();
      return (
        resolved.getDate() === today.getDate() &&
        resolved.getMonth() === today.getMonth() &&
        resolved.getFullYear() === today.getFullYear()
      );
    }).length;
    const firingAlerts = alerts.filter((a) => a.status === 'firing').length;

    // Calculate average resolution time
    const resolvedIncidents = incidents.filter((i) => i.resolvedAt);
    const avgResolutionMinutes = resolvedIncidents.length > 0
      ? resolvedIncidents.reduce((sum, i) => {
          return sum + differenceInMinutes(new Date(i.resolvedAt!), new Date(i.startedAt));
        }, 0) / resolvedIncidents.length
      : 0;

    const avgResolution = avgResolutionMinutes < 60
      ? `${Math.round(avgResolutionMinutes)}m`
      : `${Math.round(avgResolutionMinutes / 60)}h`;

    return {
      activeIncidents: active.length,
      criticalIncidents: critical,
      resolvedToday,
      firingAlerts,
      avgResolution,
    };
  }, [incidents, alerts]);

  // Filter incidents
  const filteredIncidents = useMemo(() => {
    return incidents.filter((incident) => {
      if (!filters[incident.severity]) return false;
      if (filters.activeOnly && incident.status === 'resolved') return false;
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          incident.title.toLowerCase().includes(query) ||
          incident.service.toLowerCase().includes(query) ||
          incident.id.toLowerCase().includes(query)
        );
      }
      return true;
    });
  }, [incidents, filters, searchQuery]);

  // Filter alerts
  const filteredAlerts = useMemo(() => {
    return alerts.filter((alert) => {
      if (!filters[alert.severity]) return false;
      if (filters.activeOnly && alert.status === 'resolved') return false;
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          alert.title.toLowerCase().includes(query) ||
          alert.service.toLowerCase().includes(query)
        );
      }
      return true;
    }).sort((a, b) => {
      // Sort by status (firing first) then by severity
      if (a.status === 'firing' && b.status !== 'firing') return -1;
      if (a.status !== 'firing' && b.status === 'firing') return 1;
      const severityOrder: Record<IncidentSeverity, number> = {
        critical: 0,
        high: 1,
        medium: 2,
        low: 3,
      };
      return severityOrder[a.severity] - severityOrder[b.severity];
    });
  }, [alerts, filters, searchQuery]);

  const handleIncidentClick = useCallback(
    (incident: Incident) => {
      setActiveIncident(incident);
      onIncidentClick?.(incident);
    },
    [setActiveIncident, onIncidentClick]
  );

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-zinc-800">
        <div>
          <h1 className="text-xl font-semibold text-zinc-100">Incident Dashboard</h1>
          <p className="text-sm text-zinc-500">
            Monitor and manage active incidents
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={onRefresh}
            disabled={isLoading}
          >
            <RefreshCw className={cn('w-4 h-4 mr-2', isLoading && 'animate-spin')} />
            Refresh
          </Button>
          <Button
            onClick={onCreateIncident}
            className="bg-red-600 hover:bg-red-700"
          >
            <Plus className="w-4 h-4 mr-2" />
            Declare Incident
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-5 gap-4 p-4">
        <StatsCard
          title="Active Incidents"
          value={stats.activeIncidents}
          icon={AlertTriangle}
          iconColor="text-amber-500"
        />
        <StatsCard
          title="Critical"
          value={stats.criticalIncidents}
          icon={AlertOctagon}
          iconColor="text-red-500"
        />
        <StatsCard
          title="Resolved Today"
          value={stats.resolvedToday}
          icon={CheckCircle2}
          iconColor="text-green-500"
        />
        <StatsCard
          title="Firing Alerts"
          value={stats.firingAlerts}
          icon={Bell}
          iconColor="text-orange-500"
        />
        <StatsCard
          title="Avg Resolution"
          value={stats.avgResolution}
          change="-15% vs last week"
          changeType="decrease"
          icon={Timer}
          iconColor="text-blue-500"
        />
      </div>

      {/* Search and filters */}
      <div className="flex items-center gap-2 px-4 pb-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
          <Input
            placeholder="Search incidents..."
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
              checked={filters.critical}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, critical: checked }))
              }
            >
              <AlertOctagon className="w-4 h-4 mr-2 text-red-500" />
              Critical
            </DropdownMenuCheckboxItem>
            <DropdownMenuCheckboxItem
              checked={filters.high}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, high: checked }))
              }
            >
              <AlertTriangle className="w-4 h-4 mr-2 text-orange-500" />
              High
            </DropdownMenuCheckboxItem>
            <DropdownMenuCheckboxItem
              checked={filters.medium}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, medium: checked }))
              }
            >
              <AlertCircle className="w-4 h-4 mr-2 text-amber-500" />
              Medium
            </DropdownMenuCheckboxItem>
            <DropdownMenuCheckboxItem
              checked={filters.low}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, low: checked }))
              }
            >
              <Info className="w-4 h-4 mr-2 text-blue-500" />
              Low
            </DropdownMenuCheckboxItem>
            <DropdownMenuSeparator />
            <DropdownMenuCheckboxItem
              checked={filters.activeOnly}
              onCheckedChange={(checked) =>
                setFilters((f) => ({ ...f, activeOnly: checked }))
              }
            >
              Active only
            </DropdownMenuCheckboxItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Content */}
      <Tabs
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as 'incidents' | 'alerts')}
        className="flex-1 flex flex-col"
      >
        <div className="px-4">
          <TabsList className="w-full justify-start">
            <TabsTrigger value="incidents" className="gap-2">
              <AlertTriangle className="w-4 h-4" />
              Incidents
              {stats.activeIncidents > 0 && (
                <Badge variant="secondary" className="ml-1">
                  {stats.activeIncidents}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="alerts" className="gap-2">
              <Bell className="w-4 h-4" />
              Alerts
              {stats.firingAlerts > 0 && (
                <Badge variant="destructive" className="ml-1">
                  {stats.firingAlerts}
                </Badge>
              )}
            </TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="incidents" className="flex-1 mt-0">
          <ScrollArea className="h-full">
            <div className="p-4 space-y-3">
              {filteredIncidents.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <CheckCircle2 className="w-12 h-12 text-green-500 mb-4" />
                  <p className="text-lg font-medium text-zinc-100">
                    All clear!
                  </p>
                  <p className="text-sm text-zinc-500 mt-1">
                    No active incidents at this time
                  </p>
                </div>
              ) : (
                <AnimatePresence mode="popLayout">
                  {filteredIncidents.map((incident) => (
                    <IncidentCard
                      key={incident.id}
                      incident={incident}
                      onClick={() => handleIncidentClick(incident)}
                      onJoinWarRoom={() => onJoinWarRoom?.(incident.id)}
                      onAcknowledge={() => onAcknowledgeIncident?.(incident.id)}
                      onResolve={() => onResolveIncident?.(incident.id)}
                    />
                  ))}
                </AnimatePresence>
              )}
            </div>
          </ScrollArea>
        </TabsContent>

        <TabsContent value="alerts" className="flex-1 mt-0">
          <ScrollArea className="h-full">
            <div className="p-4">
              {filteredAlerts.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <BellOff className="w-12 h-12 text-zinc-500 mb-4" />
                  <p className="text-lg font-medium text-zinc-100">
                    No alerts
                  </p>
                  <p className="text-sm text-zinc-500 mt-1">
                    All systems are operating normally
                  </p>
                </div>
              ) : (
                <div className="space-y-1">
                  {filteredAlerts.map((alert) => (
                    <AlertItem
                      key={alert.id}
                      alert={alert}
                    />
                  ))}
                </div>
              )}
            </div>
          </ScrollArea>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default IncidentDashboard;
