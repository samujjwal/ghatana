/**
 * Operator Dashboard
 *
 * Consolidated operator workspace with first-class diagnostics.
 * Features: alerts triage, event stream, system diagnostics, plugin health.
 *
 * @doc.type page
 * @doc.purpose Operator-specific workspace with diagnostics consolidation
 * @doc.layer frontend
 */

import React from 'react';
import { useNavigate } from 'react-router';
import { AlertTriangle, Activity, Zap, Wrench, ArrowRight } from 'lucide-react';
import { cn, textStyles, cardStyles } from '../lib/theme';

/**
 * Operator Dashboard Page
 */
export function OperatorDashboard(): React.ReactElement {
  const navigate = useNavigate();

  const cards = [
    {
      id: 'alerts',
      title: 'Alert Triage',
      description: 'Review, acknowledge, and resolve active incidents.',
      icon: <AlertTriangle className="h-6 w-6 text-red-500" />,
      path: '/alerts',
      count: undefined,
    },
    {
      id: 'events',
      title: 'Event Stream',
      description: 'Live-tail and inspect events across the fabric.',
      icon: <Activity className="h-6 w-6 text-blue-500" />,
      path: '/events',
      count: undefined,
    },
    {
      id: 'insights',
      title: 'System Diagnostics',
      description: 'Runtime health, operator diagnostics, and cost.',
      icon: <Zap className="h-6 w-6 text-amber-500" />,
      path: '/insights',
      count: undefined,
    },
    {
      id: 'operations',
      title: 'Operations Console',
      description: 'Real admin diagnostics and runtime inspection.',
      icon: <Wrench className="h-6 w-6 text-purple-500" />,
      path: '/operations',
      count: undefined,
    },
  ];

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900 p-6" data-testid="operator-dashboard">
      <div className="mb-8">
        <h1 className={textStyles.h1}>Operator Workspace</h1>
        <p className={textStyles.muted}>
          All operator diagnostics in one place — alerts, events, runtime health, and operations.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 mb-8">
        {cards.map((card) => (
          <button
            key={card.id}
            onClick={() => navigate(card.path)}
            className={cn(
              cardStyles.base,
              cardStyles.padded,
              'text-left hover:border-primary-300 dark:hover:border-primary-700 transition-all'
            )}
            data-testid={`operator-card-${card.id}`}
          >
            <div className="flex items-start justify-between mb-3">
              <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">{card.icon}</div>
              <ArrowRight className="h-4 w-4 text-gray-400" />
            </div>
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">{card.title}</h3>
            <p className="text-sm text-gray-500 mt-1">{card.description}</p>
          </button>
        ))}
      </div>

      <div className={cn(cardStyles.base, cardStyles.padded)} data-testid="operator-context-note">
        <h3 className={textStyles.h4}>Context</h3>
        <p className={textStyles.muted}>
          This workspace consolidates operator-facing surfaces. If you need deeper controls
          (settings, user management), switch to the admin workspace via the role selector.
        </p>
      </div>
    </div>
  );
}

export default OperatorDashboard;
