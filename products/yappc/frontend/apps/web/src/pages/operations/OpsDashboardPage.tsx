/**
 * Ops Dashboard Page
 *
 * @description Operations overview with health status, incidents,
 * and key metrics for monitoring.
 */

import React from 'react';
import { NavLink, useParams } from 'react-router';
import { useAtomValue } from 'jotai';
import { motion } from 'framer-motion';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Cpu,
  HardDrive,
  Wifi,
  ArrowRight,
  TrendingUp,
  TrendingDown,
  Bell,
  Users,
  Shield,
  Zap,
  Server,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import {
  incidentsAtom,
  alertsAtom,
} from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

interface ServiceStatus {
  name: string;
  status: 'healthy' | 'degraded' | 'down';
  latency: number;
  uptime: number;
  requests: number;
}

interface MetricCard {
  label: string;
  value: string;
  change: number;
  trend: 'up' | 'down' | 'neutral';
  icon: React.ReactNode;
}

// =============================================================================
// Components
// =============================================================================

const StatusBadge: React.FC<{ status: 'healthy' | 'degraded' | 'down' }> = ({
  status,
}) => {
  const config = {
    healthy: { label: 'Healthy', color: 'bg-emerald-500', textColor: 'text-emerald-400' },
    degraded: { label: 'Degraded', color: 'bg-amber-500', textColor: 'text-amber-400' },
    down: { label: 'Down', color: 'bg-red-500', textColor: 'text-red-400' },
  };

  const { label, color, textColor } = config[status];

  return (
    <div className="flex items-center gap-2">
      <span className={cn('w-2 h-2 rounded-full', color)} />
      <span className={cn('text-sm', textColor)}>{label}</span>
    </div>
  );
};

const ServiceCard: React.FC<{ service: ServiceStatus }> = ({ service }) => (
  <div className="p-4 rounded-xl bg-zinc-900 border border-zinc-800 hover:border-zinc-700 transition-colors">
    <div className="flex items-start justify-between mb-3">
      <div className="flex items-center gap-3">
        <div
          className={cn(
            'p-2 rounded-lg',
            service.status === 'healthy' && 'bg-emerald-500/10 text-emerald-400',
            service.status === 'degraded' && 'bg-amber-500/10 text-amber-400',
            service.status === 'down' && 'bg-red-500/10 text-red-400'
          )}
        >
          <Server className="w-4 h-4" />
        </div>
        <div>
          <div className="font-medium text-white">{service.name}</div>
          <StatusBadge status={service.status} />
        </div>
      </div>
    </div>
    <div className="grid grid-cols-3 gap-2 text-xs">
      <div>
        <div className="text-zinc-500">Latency</div>
        <div className="text-white font-medium">{service.latency}ms</div>
      </div>
      <div>
        <div className="text-zinc-500">Uptime</div>
        <div className="text-white font-medium">{service.uptime}%</div>
      </div>
      <div>
        <div className="text-zinc-500">Req/s</div>
        <div className="text-white font-medium">{service.requests}</div>
      </div>
    </div>
  </div>
);

const MetricTile: React.FC<MetricCard> = ({ label, value, change, trend, icon }) => (
  <div className="p-6 rounded-xl bg-zinc-900 border border-zinc-800">
    <div className="flex items-start justify-between mb-4">
      <div className="p-2 rounded-lg bg-zinc-800 text-zinc-400">{icon}</div>
      <div
        className={cn(
          'flex items-center gap-1 text-sm',
          trend === 'up' && 'text-emerald-400',
          trend === 'down' && 'text-red-400',
          trend === 'neutral' && 'text-zinc-400'
        )}
      >
        {trend === 'up' && <TrendingUp className="w-4 h-4" />}
        {trend === 'down' && <TrendingDown className="w-4 h-4" />}
        {change > 0 ? '+' : ''}
        {change}%
      </div>
    </div>
    <div className="text-3xl font-bold text-white mb-1">{value}</div>
    <div className="text-sm text-zinc-400">{label}</div>
  </div>
);

// =============================================================================
// Ops Dashboard Page Component
// =============================================================================

const OpsDashboardPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const incidents = useAtomValue(incidentsAtom);
  const alerts = useAtomValue(alertsAtom);

  // Filter active incidents and unacknowledged alerts
  const activeIncidentsList = incidents.filter((i) => i.status !== 'resolved');
  const activeAlertsList = alerts.filter((a) => !a.resolvedAt);

  // Mock service data
  const services: ServiceStatus[] = [
    { name: 'API Gateway', status: 'healthy', latency: 45, uptime: 99.99, requests: 1250 },
    { name: 'Auth Service', status: 'healthy', latency: 12, uptime: 99.98, requests: 890 },
    { name: 'Database', status: 'healthy', latency: 8, uptime: 99.99, requests: 2100 },
    { name: 'Cache', status: 'degraded', latency: 15, uptime: 98.5, requests: 4500 },
    { name: 'Search', status: 'healthy', latency: 85, uptime: 99.95, requests: 320 },
    { name: 'Worker Queue', status: 'healthy', latency: 120, uptime: 99.9, requests: 180 },
  ];

  const metrics: MetricCard[] = [
    {
      label: 'Avg Response Time',
      value: '42ms',
      change: -8,
      trend: 'up',
      icon: <Zap className="w-5 h-5" />,
    },
    {
      label: 'Request Rate',
      value: '12.4k/s',
      change: 15,
      trend: 'up',
      icon: <Activity className="w-5 h-5" />,
    },
    {
      label: 'Error Rate',
      value: '0.12%',
      change: -25,
      trend: 'up',
      icon: <AlertTriangle className="w-5 h-5" />,
    },
    {
      label: 'Uptime (30d)',
      value: '99.98%',
      change: 0.01,
      trend: 'up',
      icon: <CheckCircle2 className="w-5 h-5" />,
    },
  ];

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white mb-2">Operations</h1>
          <p className="text-zinc-400">Monitor system health and incidents</p>
        </div>
        <div className="flex items-center gap-3">
          <NavLink
            to={ROUTES.operations.alerts(projectId || '')}
            className={cn(
              'flex items-center gap-2 px-4 py-2 rounded-lg',
              'bg-zinc-800 border border-zinc-700 text-zinc-300',
              'hover:border-zinc-600 transition-colors'
            )}
          >
            <Bell className="w-4 h-4" />
            Alerts
            {activeAlertsList.length > 0 && (
              <span className="px-2 py-0.5 rounded-full bg-red-500 text-white text-xs">
                {activeAlertsList.length}
              </span>
            )}
          </NavLink>
          <NavLink
            to={ROUTES.operations.incidents(projectId || '')}
            className={cn(
              'flex items-center gap-2 px-4 py-2 rounded-lg font-medium',
              activeIncidentsList.length > 0
                ? 'bg-red-500 text-white hover:bg-red-600'
                : 'bg-violet-500 text-white hover:bg-violet-600',
              'transition-colors'
            )}
          >
            <AlertTriangle className="w-4 h-4" />
            {activeIncidentsList.length > 0 ? `${activeIncidentsList.length} Active` : 'Incidents'}
          </NavLink>
        </div>
      </div>

      {/* Active Incident Banner */}
      {activeIncidentsList.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className={cn(
            'mb-6 p-4 rounded-xl border',
            'bg-red-500/10 border-red-500/30'
          )}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="p-2 rounded-lg bg-red-500/20">
                <AlertTriangle className="w-5 h-5 text-red-400" />
              </div>
              <div>
                <div className="font-medium text-white">
                  {activeIncidentsList[0].title}
                </div>
                <div className="text-sm text-zinc-400">
                  Severity: {activeIncidentsList[0].severity} • Started{' '}
                  {activeIncidentsList[0].startedAt || 'recently'}
                </div>
              </div>
            </div>
            <NavLink
              to={ROUTES.operations.incidents(projectId || '', activeIncidentsList[0].id)}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-red-500 text-white hover:bg-red-600 transition-colors"
            >
              View Incident
              <ArrowRight className="w-4 h-4" />
            </NavLink>
          </div>
        </motion.div>
      )}

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {metrics.map((metric) => (
          <MetricTile key={metric.label} {...metric} />
        ))}
      </div>

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Service Health */}
        <div className="lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-white">Service Health</h2>
            <NavLink
              to={ROUTES.operations.services(projectId || '')}
              className="text-sm text-violet-400 hover:text-violet-300 flex items-center gap-1"
            >
              View Map
              <ArrowRight className="w-4 h-4" />
            </NavLink>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {services.map((service) => (
              <ServiceCard key={service.name} service={service} />
            ))}
          </div>
        </div>

        {/* Quick Links & Recent Incidents */}
        <div className="space-y-6">
          {/* Quick Links */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">Quick Access</h2>
            <div className="space-y-2">
              {[
                {
                  label: 'Dashboards',
                  icon: <Activity className="w-4 h-4" />,
                  href: ROUTES.operations.dashboards(projectId || ''),
                },
                {
                  label: 'Log Explorer',
                  icon: <HardDrive className="w-4 h-4" />,
                  href: ROUTES.operations.logs(projectId || ''),
                },
                {
                  label: 'Metrics',
                  icon: <TrendingUp className="w-4 h-4" />,
                  href: ROUTES.operations.metrics(projectId || ''),
                },
                {
                  label: 'Runbooks',
                  icon: <Shield className="w-4 h-4" />,
                  href: ROUTES.operations.runbooks(projectId || ''),
                },
                {
                  label: 'On-Call',
                  icon: <Users className="w-4 h-4" />,
                  href: ROUTES.operations.oncall(projectId || ''),
                },
              ].map((link) => (
                <NavLink
                  key={link.label}
                  to={link.href}
                  className="flex items-center gap-3 p-3 rounded-lg hover:bg-zinc-800 transition-colors group"
                >
                  <div className="p-2 rounded-lg bg-zinc-800 text-zinc-400 group-hover:text-violet-400 transition-colors">
                    {link.icon}
                  </div>
                  <span className="text-sm text-zinc-300 group-hover:text-white transition-colors">
                    {link.label}
                  </span>
                  <ArrowRight className="w-4 h-4 ml-auto text-zinc-600 group-hover:text-zinc-400 transition-colors" />
                </NavLink>
              ))}
            </div>
          </div>

          {/* Recent Incidents */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-white">Recent Incidents</h2>
              <NavLink
                to={ROUTES.operations.incidents(projectId || '')}
                className="text-sm text-violet-400 hover:text-violet-300"
              >
                View All
              </NavLink>
            </div>
            <div className="space-y-3">
              {incidents.slice(0, 3).map((incident) => (
                <NavLink
                  key={incident.id}
                  to={ROUTES.operations.incidents(projectId || '', incident.id)}
                  className="block p-3 rounded-lg hover:bg-zinc-800 transition-colors"
                >
                  <div className="flex items-start gap-3">
                    <div
                      className={cn(
                        'w-2 h-2 rounded-full mt-2',
                        incident.status === 'resolved'
                          ? 'bg-emerald-500'
                          : incident.severity === 'critical'
                          ? 'bg-red-500'
                          : 'bg-amber-500'
                      )}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-white truncate">
                        {incident.title}
                      </div>
                      <div className="text-xs text-zinc-500 mt-1">
                        {incident.status} • {incident.startedAt}
                      </div>
                    </div>
                  </div>
                </NavLink>
              ))}
              {incidents.length === 0 && (
                <div className="text-center py-6">
                  <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto mb-2" />
                  <p className="text-sm text-zinc-400">No recent incidents</p>
                </div>
              )}
            </div>
          </div>

          {/* System Resources */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
            <h2 className="text-lg font-semibold text-white mb-4">System Resources</h2>
            <div className="space-y-4">
              {[
                { label: 'CPU Usage', value: 42, icon: <Cpu className="w-4 h-4" /> },
                { label: 'Memory', value: 68, icon: <HardDrive className="w-4 h-4" /> },
                { label: 'Network I/O', value: 35, icon: <Wifi className="w-4 h-4" /> },
              ].map((resource) => (
                <div key={resource.label}>
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2 text-sm text-zinc-400">
                      {resource.icon}
                      {resource.label}
                    </div>
                    <span className="text-sm font-medium text-white">{resource.value}%</span>
                  </div>
                  <div className="h-2 bg-zinc-800 rounded-full overflow-hidden">
                    <div
                      className={cn(
                        'h-full rounded-full transition-all',
                        resource.value > 80
                          ? 'bg-red-500'
                          : resource.value > 60
                          ? 'bg-amber-500'
                          : 'bg-emerald-500'
                      )}
                      style={{ width: `${resource.value}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default OpsDashboardPage;
