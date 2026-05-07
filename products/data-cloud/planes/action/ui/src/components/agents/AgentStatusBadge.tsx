/**
 * AgentStatusBadge — compact coloured dot + label for agent health state.
 *
 * @doc.type component
 * @doc.purpose Display agent health/activity status visually
 * @doc.layer frontend
 */
import React from 'react';
import type { AgentStatus } from '@/types/agent.types';

interface AgentStatusBadgeProps {
  status: AgentStatus;
  /** If true, only the dot is shown (no label). Default: false. */
  dotOnly?: boolean;
  className?: string;
}

const STATUS_CONFIG: Record<AgentStatus, { dot: string; label: string }> = {
  ACTIVE: { dot: 'bg-green-500', label: 'Active' },
  IDLE: { dot: 'bg-yellow-400', label: 'Idle' },
  ERROR: { dot: 'bg-red-500', label: 'Error' },
  UNKNOWN: { dot: 'bg-gray-300', label: 'Unknown' },
};

export function AgentStatusBadge({ status, dotOnly = false, className = '' }: AgentStatusBadgeProps) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.UNKNOWN;

  return (
    <span
      className={['inline-flex items-center gap-1.5 text-xs font-medium', className].join(' ')}
      title={cfg.label}
    >
      <span className={['h-2 w-2 rounded-full flex-shrink-0', cfg.dot].join(' ')} aria-hidden />
      {!dotOnly && <span>{cfg.label}</span>}
    </span>
  );
}
