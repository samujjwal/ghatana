/**
 * Admin Workspace
 *
 * Privileged controls and system-wide configuration for admin users.
 * Features: settings, user/role management, audit trail, blast-radius context.
 *
 * @doc.type page
 * @doc.purpose Admin-specific workspace with privileged controls
 * @doc.layer frontend
 */

import React from 'react';
import { useNavigate } from 'react-router';
import {
  Settings,
  Users,
  Shield,
  Database,
  AlertTriangle,
  ArrowRight,
} from 'lucide-react';
import { cn, textStyles, cardStyles } from '../lib/theme';

/**
 * Admin Workspace Page
 */
export function AdminWorkspace(): React.ReactElement {
  const navigate = useNavigate();

  const cards = [
    {
      id: 'settings',
      title: 'System Settings',
      description: 'Configure platform-wide preferences, secrets, and integrations.',
      icon: <Settings className="h-6 w-6 text-blue-500" />,
      path: '/settings',
      danger: false,
    },
    {
      id: 'users',
      title: 'User & Role Management',
      description: 'Manage tenants, users, and role assignments.',
      icon: <Users className="h-6 w-6 text-green-500" />,
      path: '/settings?tab=users',
      danger: false,
    },
    {
      id: 'trust',
      title: 'Trust Center',
      description: 'Governance policies, retention, redaction, and audit review.',
      icon: <Shield className="h-6 w-6 text-amber-500" />,
      path: '/trust',
      danger: false,
    },
    {
      id: 'operations',
      title: 'Dangerous Operations',
      description: 'Destructive actions with blast-radius context. Requires extra confirmation.',
      icon: <AlertTriangle className="h-6 w-6 text-red-500" />,
      path: '/operations',
      danger: true,
    },
  ];

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900 p-6" data-testid="admin-workspace">
      <div className="mb-8">
        <h1 className={textStyles.h1}>Admin Workspace</h1>
        <p className={textStyles.muted}>
          Privileged controls and system-wide configuration. Use with caution — changes here affect
          all users in the tenant.
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
              'text-left transition-all',
              card.danger
                ? 'border-red-200 dark:border-red-800 hover:border-red-400 dark:hover:border-red-600'
                : 'hover:border-primary-300 dark:hover:border-primary-700'
            )}
            data-testid={`admin-card-${card.id}`}
          >
            <div className="flex items-start justify-between mb-3">
              <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">{card.icon}</div>
              <ArrowRight className="h-4 w-4 text-gray-400" />
            </div>
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">{card.title}</h3>
            <p className="text-sm text-gray-500 mt-1">{card.description}</p>
            {card.danger && (
              <div className="mt-3 inline-flex items-center gap-1 text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 px-2 py-1 rounded">
                <AlertTriangle className="h-3 w-3" />
                Requires confirmation
              </div>
            )}
          </button>
        ))}
      </div>

      <div className={cn(cardStyles.base, cardStyles.padded)} data-testid="admin-blast-radius-note">
        <div className="flex items-start gap-3">
          <AlertTriangle className="h-5 w-5 text-amber-500 mt-0.5" />
          <div>
            <h3 className={textStyles.h4}>Blast-radius Awareness</h3>
            <p className={textStyles.muted}>
              Admin actions affect all tenant users. Destructive actions require explicit
              confirmation and are logged to the audit trail. When in doubt, preview the impact
              before committing.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default AdminWorkspace;
