/**
 * AgentCard — compact card showing a registered agent's status and metadata.
 *
 * @doc.type component
 * @doc.purpose Display a single registered AEP agent with status and actions
 * @doc.layer frontend
 */
import React from 'react';
import { Link } from 'react-router';
import type { AgentRegistration } from '@/api/aep.api';
import { AgentStatusBadge } from './AgentStatusBadge';

interface AgentCardProps {
  agent: AgentRegistration;
  onDeregister?: (agentId: string) => void;
  isDeregistering?: boolean;
}

export function AgentCard({ agent, onDeregister, isDeregistering }: AgentCardProps) {
  const lastSeen = agent.lastSeen
    ? `Last seen ${new Date(agent.lastSeen).toLocaleString()}`
    : 'Never seen';

  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 flex flex-col gap-3">
      {/* Header */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-gray-900 dark:text-gray-100 truncate">{agent.name}</p>
          <p className="text-xs text-gray-400 font-mono mt-0.5 truncate">{agent.id}</p>
        </div>
        <AgentStatusBadge status={agent.status} />
      </div>

      {/* Capabilities */}
      {agent.capabilities.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {agent.capabilities.map((cap) => (
            <span
              key={cap}
              className="px-2 py-0.5 rounded bg-indigo-50 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300 text-xs font-mono"
            >
              {cap}
            </span>
          ))}
        </div>
      )}

      {/* Meta */}
      <div className="text-xs text-gray-400 flex items-center gap-3">
        <span>v{agent.version}</span>
        <span>{agent.memoryCount} memory items</span>
        <span className="ml-auto">{lastSeen}</span>
      </div>

      {/* Actions */}
      <div className="flex gap-2 pt-1 border-t border-gray-100 dark:border-gray-800">
        <Link
          to={`/agents/${agent.id}`}
          className="flex-1 text-center px-3 py-1.5 text-xs rounded-md bg-indigo-50 dark:bg-indigo-950 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900 font-medium transition-colors"
        >
          View detail
        </Link>
        {onDeregister && (
          <button
            type="button"
            onClick={() => onDeregister(agent.id)}
            disabled={isDeregistering}
            className="px-3 py-1.5 text-xs rounded-md bg-red-50 dark:bg-red-950 text-red-700 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900 disabled:opacity-50 transition-colors"
          >
            {isDeregistering ? 'Removing…' : 'Deregister'}
          </button>
        )}
      </div>
    </div>
  );
}
