/**
 * AgentHealthGrid — compact grid of coloured dots showing agent health.
 *
 * Each dot represents one agent. Green = ACTIVE, yellow = IDLE, red = ERROR,
 * grey = UNKNOWN. Hovering a dot shows the agent name + status.
 *
 * @doc.type component
 * @doc.purpose Visualise the health of all agents at a glance
 * @doc.layer frontend
 */
import React from 'react';
import type { AgentRegistration } from '@/types/agent.types';
import { AgentStatusBadge } from '@/components/agents/AgentStatusBadge';

interface AgentHealthGridProps {
  agents: AgentRegistration[];
  className?: string;
}

export function AgentHealthGrid({ agents, className = '' }: AgentHealthGridProps) {
  if (agents.length === 0) {
    return (
      <p className={['text-sm text-gray-400', className].join(' ')}>No agents registered.</p>
    );
  }

  return (
    <div className={['flex flex-wrap gap-3', className].join(' ')}>
      {agents.map((agent) => (
        <div
          key={agent.id}
          title={`${agent.name} — ${agent.status}`}
          className="flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-400"
        >
          <AgentStatusBadge status={agent.status} dotOnly />
          <span className="font-mono truncate max-w-[10rem]">{agent.name}</span>
        </div>
      ))}
    </div>
  );
}
