/**
 * AgentTable — filterable table of registered AEP agents.
 *
 * Renders a compact table view with name, status, version, memory count, and
 * last-seen. Clicking a row selects/deselects it. Accepts an optional search
 * string for caller-controlled filtering.
 *
 * @doc.type component
 * @doc.purpose Browseable table of all registered agents for a tenant
 * @doc.layer frontend
 */
import React from 'react';
import type { AgentRegistration } from '@/api/aep.api';
import { AgentStatusBadge } from './AgentStatusBadge';

interface AgentTableProps {
  agents: AgentRegistration[];
  selectedId?: string | null;
  onSelect: (agent: AgentRegistration) => void;
  className?: string;
}

export function AgentTable({ agents, selectedId, onSelect, className = '' }: AgentTableProps) {
  if (agents.length === 0) {
    return (
      <p className={['text-sm italic text-gray-400 py-8 text-center', className].join(' ')}>
        No agents registered yet.
      </p>
    );
  }

  return (
    <div className={['overflow-x-auto', className].join(' ')}>
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider border-b border-gray-200 dark:border-gray-800">
            <th className="pb-2 pr-4">Name</th>
            <th className="pb-2 pr-4">Status</th>
            <th className="pb-2 pr-4">Version</th>
            <th className="pb-2 pr-4">Memory</th>
            <th className="pb-2">Last seen</th>
          </tr>
        </thead>
        <tbody>
          {agents.map((agent) => (
            <tr
              key={agent.id}
              onClick={() => onSelect(agent)}
              className={[
                'border-b border-gray-100 dark:border-gray-900 cursor-pointer transition-colors',
                selectedId === agent.id
                  ? 'bg-indigo-50 dark:bg-indigo-950'
                  : 'hover:bg-gray-50 dark:hover:bg-gray-900',
              ].join(' ')}
            >
              <td className="py-2.5 pr-4">
                <p className="font-medium text-gray-900 dark:text-gray-100">{agent.name}</p>
                <p className="text-xs text-gray-400 font-mono truncate max-w-[12rem]">{agent.id}</p>
              </td>
              <td className="py-2.5 pr-4">
                <AgentStatusBadge status={agent.status} />
              </td>
              <td className="py-2.5 pr-4 text-gray-500 dark:text-gray-400 font-mono text-xs">
                {agent.version}
              </td>
              <td className="py-2.5 pr-4 text-gray-500 dark:text-gray-400">
                {agent.memoryCount}
              </td>
              <td className="py-2.5 text-gray-400 text-xs">
                {agent.lastSeen ? new Date(agent.lastSeen).toLocaleString() : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
