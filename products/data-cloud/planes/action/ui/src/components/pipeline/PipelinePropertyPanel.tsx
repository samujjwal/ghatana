/**
 * PipelinePropertyPanel — Right-hand inspector for the selected
 * pipeline node (stage or connector).
 *
 * Shows metadata fields, agent list, connector configuration,
 * and inline editing.
 *
 * @doc.type component
 * @doc.purpose Property inspector for selected pipeline elements
 * @doc.layer frontend
 */
import React from 'react';
import { useAtomValue, useAtom } from 'jotai';
import { selectedNodeAtom, nodesAtom } from '@/stores/pipeline.store';
import type {
  StageNodeData,
  ConnectorNodeData,
  AgentSpec,
} from '@/types/pipeline.types';

// ─── Helpers ─────────────────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="border-b border-gray-200 pb-3 mb-3">
      <h3 className="text-xs font-bold uppercase text-gray-400 mb-2">{title}</h3>
      {children}
    </div>
  );
}

function Field({ label, value }: { label: string; value: string | number | undefined }) {
  return (
    <div className="flex justify-between items-baseline text-xs mb-1">
      <span className="text-gray-500">{label}</span>
      <span className="font-mono text-gray-800 truncate max-w-[140px]">
        {value ?? '—'}
      </span>
    </div>
  );
}

// ─── Agent List ──────────────────────────────────────────────────────

function AgentListSection({ agents }: { agents: AgentSpec[] }) {
  if (agents.length === 0) {
    return (
      <Section title="Agents">
        <p className="text-xs text-gray-400 italic">No agents in this stage</p>
      </Section>
    );
  }

  return (
    <Section title={`Agents (${agents.length})`}>
      <ul className="space-y-2">
        {agents.map((agent) => (
          <li
            key={agent.id}
            className="bg-gray-50 rounded px-2 py-1.5 border border-gray-100"
          >
            <p className="text-xs font-semibold">{agent.agent}</p>
            <p className="text-[10px] text-gray-500">
              id: {agent.id}
              {agent.role && ` · role: ${agent.role}`}
            </p>
            {agent.inputsSpec && agent.inputsSpec.length > 0 && (
              <p className="text-[10px] text-blue-500 mt-0.5">
                inputs: {agent.inputsSpec.map((s) => s.name).join(', ')}
              </p>
            )}
            {agent.outputsSpec && agent.outputsSpec.length > 0 && (
              <p className="text-[10px] text-green-500">
                outputs: {agent.outputsSpec.map((s) => s.name).join(', ')}
              </p>
            )}
          </li>
        ))}
      </ul>
    </Section>
  );
}

// ─── Stage Panel ─────────────────────────────────────────────────────

function StagePanel({ data }: { data: StageNodeData }) {
  return (
    <>
      <Section title="Stage Properties">
        <Field label="Name" value={data.label} />
        <Field label="Kind" value={data.kind} />
        <Field label="Agent Count" value={data.agentCount} />
        {data.description && <Field label="Description" value={data.description} />}
      </Section>

      <AgentListSection agents={data.agents} />

      {data.connectorIds && data.connectorIds.length > 0 && (
        <Section title="Connector References">
          <ul className="space-y-1">
            {data.connectorIds.map((cid) => (
              <li key={cid} className="text-xs font-mono text-gray-700">
                🔌 {cid}
              </li>
            ))}
          </ul>
        </Section>
      )}
    </>
  );
}

// ─── Connector Panel ─────────────────────────────────────────────────

function ConnectorPanel({ data }: { data: ConnectorNodeData }) {
  return (
    <Section title="Connector Properties">
      <Field label="Label" value={data.label} />
      <Field label="ID" value={data.connectorId} />
      <Field label="Type" value={data.type} />
      <Field label="Direction" value={data.direction} />
      {data.encoding && <Field label="Encoding" value={data.encoding} />}
    </Section>
  );
}

// ─── Main Panel ──────────────────────────────────────────────────────

export function PipelinePropertyPanel() {
  const selected = useAtomValue(selectedNodeAtom);

  if (!selected) {
    return (
      <aside
        className="w-72 border-l border-gray-200 bg-gray-50 flex items-center justify-center"
        data-testid="property-panel"
      >
        <p className="text-xs text-gray-400">Select a node to inspect</p>
      </aside>
    );
  }

  const isStage = selected.type === 'stage';

  return (
    <aside
      className="w-72 border-l border-gray-200 bg-gray-50 overflow-y-auto"
      data-testid="property-panel"
    >
      <div className="px-3 py-3">
        <h2 className="text-sm font-bold mb-3">
          {isStage ? '🔧 Stage Inspector' : '🔌 Connector Inspector'}
        </h2>
        {isStage ? (
          <StagePanel data={selected.data as unknown as StageNodeData} />
        ) : (
          <ConnectorPanel data={selected.data as unknown as ConnectorNodeData} />
        )}
      </div>
    </aside>
  );
}
