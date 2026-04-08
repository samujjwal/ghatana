/**
 * Risk Alerts Component
 *
 * Displays risk alerts with severity levels, categories, and escalation options.
 * Provides filtering by severity and status, and supports bulk risk mitigation actions.
 *
 * @doc.type component
 * @doc.purpose Risk alerts UI with severity indicators
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { ChevronRight, AlertTriangle, Shield, Zap, Activity, FileCheck, ArrowUp, MoreVertical } from 'lucide-react';
import { Typography, Button, Chip, Box, Surface as Paper } from '@ghatana/design-system';
import type { RiskAlert, RiskSeverity, RiskCategory } from '../../clients/dashboard';

interface RiskAlertsProps {
  alerts: RiskAlert[];
  onEscalate?: (alertId: string, escalateTo: string, reason: string) => Promise<void>;
  onUpdateStatus?: (alertId: string, status: RiskAlert['status']) => Promise<void>;
  onViewAll?: () => void;
  projectId?: string;
}

/**
 * Risk Alerts component
 * 
 * Displays a list of risk alerts with severity indicators and escalation options.
 */
export function RiskAlerts({
  alerts,
  onEscalate,
  onUpdateStatus,
  onViewAll,
  projectId,
}: RiskAlertsProps) {
  const [selectedAlerts, setSelectedAlerts] = useState<Set<string>>(new Set());
  const [showEscalationDialog, setShowEscalationDialog] = useState<string | null>(null);

  const hasActions = Boolean(onEscalate || onUpdateStatus);
  const displayAlerts = projectId ? alerts.filter(alert => alert.projectId === projectId) : alerts;
  const activeAlerts = displayAlerts.filter(alert => alert.status !== 'mitigated' && alert.status !== 'ignored');

  const toggleSelection = (alertId: string) => {
    setSelectedAlerts(prev => {
      const next = new Set(prev);
      if (next.has(alertId)) {
        next.delete(alertId);
      } else {
        next.add(alertId);
      }
      return next;
    });
  };

  const handleEscalate = async (alertId: string) => {
    if (!onEscalate) return;
    // In a real implementation, this would open a dialog to get escalation details
    // For now, we'll just call the function with placeholder values
    await onEscalate(alertId, 'security-team', 'Escalating for review');
  };

  const handleUpdateStatus = async (alertId: string, status: RiskAlert['status']) => {
    if (!onUpdateStatus) return;
    await onUpdateStatus(alertId, status);
  };

  const getSeverityIcon = (severity: RiskSeverity) => {
    switch (severity) {
      case 'critical':
        return <AlertTriangle className="w-5 h-5 text-red-600" />;
      case 'high':
        return <AlertTriangle className="w-5 h-5 text-orange-600" />;
      case 'medium':
        return <AlertTriangle className="w-5 h-5 text-yellow-600" />;
      case 'low':
        return <AlertTriangle className="w-5 h-5 text-green-600" />;
      default:
        return null;
    }
  };

  const getCategoryIcon = (category: RiskCategory) => {
    switch (category) {
      case 'security':
        return <Shield className="w-4 h-4" />;
      case 'performance':
        return <Zap className="w-4 h-4" />;
      case 'reliability':
        return <Activity className="w-4 h-4" />;
      case 'compliance':
        return <FileCheck className="w-4 h-4" />;
      case 'operational':
        return <MoreVertical className="w-4 h-4" />;
      default:
        return null;
    }
  };

  const getSeverityColor = (severity: RiskSeverity) => {
    switch (severity) {
      case 'critical':
        return 'bg-red-100 text-red-700 border-red-300';
      case 'high':
        return 'bg-orange-100 text-orange-700 border-orange-300';
      case 'medium':
        return 'bg-yellow-100 text-yellow-700 border-yellow-300';
      case 'low':
        return 'bg-green-100 text-green-700 border-green-300';
      default:
        return 'bg-gray-100 text-gray-700 border-gray-300';
    }
  };

  const getStatusBadge = (status: RiskAlert['status']) => {
    const statusConfig = {
      open: { color: 'bg-red-100 text-red-700', label: 'Open' },
      mitigating: { color: 'bg-blue-100 text-blue-700', label: 'Mitigating' },
      mitigated: { color: 'bg-green-100 text-green-700', label: 'Mitigated' },
      escalated: { color: 'bg-purple-100 text-purple-700', label: 'Escalated' },
      ignored: { color: 'bg-gray-100 text-gray-700', label: 'Ignored' },
    };

    const config = statusConfig[status];
    return (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${config.color}`}>
        {config.label}
      </span>
    );
  };

  return (
    <div className="mb-10">
      <div className="flex justify-between items-center mb-2">
        <Typography className="flex items-center gap-2 font-bold text-lg">
          Risk Alerts
          <Chip label={activeAlerts.length} size="sm" />
        </Typography>
        <Button size="sm" endIcon={<ChevronRight />} onClick={onViewAll}>View all</Button>
      </div>
      <Paper className="rounded-lg overflow-hidden border">
        {activeAlerts.length === 0 ? (
          <div className="p-8 text-center text-gray-500 dark:text-gray-400">
            <Shield className="mb-2 w-20 h-20 opacity-50 mx-auto" />
            <Typography>
              No active risk alerts. System is healthy!
            </Typography>
          </div>
        ) : (
          <>
            <div className="divide-y divide-gray-200 dark:divide-gray-700">
              {activeAlerts.map((alert) => (
                <div
                  key={alert.id}
                  className={`py-4 px-4 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors ${selectedAlerts.has(alert.id) ? 'bg-blue-50 dark:bg-blue-900/20' : ''}`}
                >
                  <div className="flex items-start gap-4">
                    {hasActions && (
                      <input
                        type="checkbox"
                        checked={selectedAlerts.has(alert.id)}
                        onChange={() => toggleSelection(alert.id)}
                        className="w-4 h-4 mt-1 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      />
                    )}
                    <div className="flex-shrink-0">
                      {getSeverityIcon(alert.severity)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <Typography className="text-lg font-medium truncate">
                          {alert.title}
                        </Typography>
                        {getStatusBadge(alert.status)}
                      </div>
                      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-2">
                        {alert.description}
                      </Typography>
                      <div className="flex items-center gap-4 flex-wrap">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium border ${getSeverityColor(alert.severity)}`}>
                          {alert.severity.toUpperCase()}
                        </span>
                        <span className="flex items-center gap-1 text-sm text-gray-500">
                          {getCategoryIcon(alert.category)}
                          {alert.category}
                        </span>
                        <span className="text-sm text-gray-500">
                          {alert.affectedComponents.length} component{alert.affectedComponents.length !== 1 ? 's' : ''} affected
                        </span>
                        {alert.dueDate && (
                          <span className="text-sm text-gray-500">
                            Due: {new Date(alert.dueDate).toLocaleDateString()}
                          </span>
                        )}
                        {alert.escalatedTo && (
                          <span className="flex items-center gap-1 text-sm text-purple-600">
                            <ArrowUp className="w-4 h-4" />
                            Escalated to: {alert.escalatedTo}
                          </span>
                        )}
                      </div>
                      {alert.mitigationPlan && (
                        <div className="mt-2 p-2 bg-blue-50 dark:bg-blue-900/20 rounded text-sm text-blue-700 dark:text-blue-300">
                          <span className="font-medium">Mitigation Plan:</span> {alert.mitigationPlan}
                        </div>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {hasActions && (
                        <div className="flex gap-1">
                          {onEscalate && alert.status !== 'escalated' && (
                            <button
                              onClick={() => handleEscalate(alert.id)}
                              className="p-2 rounded-md bg-purple-100 text-purple-700 hover:bg-purple-200 transition-colors"
                              title="Escalate"
                            >
                              <ArrowUp className="w-4 h-4" />
                            </button>
                          )}
                          {onUpdateStatus && alert.status === 'open' && (
                            <button
                              onClick={() => handleUpdateStatus(alert.id, 'mitigating')}
                              className="p-2 rounded-md bg-blue-100 text-blue-700 hover:bg-blue-200 transition-colors"
                              title="Start Mitigation"
                            >
                              <Shield className="w-4 h-4" />
                            </button>
                          )}
                          {onUpdateStatus && (alert.status === 'mitigating' || alert.status === 'escalated') && (
                            <button
                              onClick={() => handleUpdateStatus(alert.id, 'mitigated')}
                              className="p-2 rounded-md bg-green-100 text-green-700 hover:bg-green-200 transition-colors"
                              title="Mark as Mitigated"
                            >
                              <FileCheck className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      )}
                      <ChevronRight className="w-5 h-5 text-gray-400" />
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="p-2 text-center bg-gray-100 dark:bg-gray-800">
              <Button size="sm" endIcon={<ChevronRight />} onClick={onViewAll}>Go to Risk Alerts</Button>
            </div>
          </>
        )}
      </Paper>
    </div>
  );
}
