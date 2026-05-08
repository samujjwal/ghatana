/**
 * RunLineage — YAPPC Web.
 *
 * Displays the lineage chain for a YAPPC run: which AEP workflow triggered it,
 * which plan step it belongs to, and what agent(s) executed it.
 * Intended for use in result panels, audit drawers, and telemetry surfaces.
 *
 * Data is fetched from the AEP run details endpoint using TanStack Query.
 * Renders a breadcrumb-style lineage trail with links to each parent entity.
 *
 * @doc.type component
 * @doc.purpose AEP run lineage display for traceability and auditability
 * @doc.layer product
 * @doc.pattern Molecule
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '../ui/Button';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export interface RunLineageNode {
  id: string;
  label: string;
  type: 'workflow' | 'plan' | 'step' | 'run' | 'agent';
  href?: string;
}

export interface RunLineageData {
  runId: string;
  nodes: RunLineageNode[];
}

export interface RunLineageProps {
  /** The AEP run ID whose lineage to display. */
  runId: string;
  /**
   * Async function that fetches lineage for the given run ID.
   * Injected to avoid coupling this component to a specific API client.
   */
  fetchLineage: (runId: string) => Promise<RunLineageData>;
  /** Called when a lineage node is clicked. If absent, nodes are not interactive. */
  onNodeClick?: (node: RunLineageNode) => void;
  /** Additional CSS class names. */
  className?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────

export const RunLineage = React.memo<RunLineageProps>(function RunLineage({
  runId,
  fetchLineage,
  onNodeClick,
  className,
}) {
  const { data, isLoading, isError, error } = useQuery<RunLineageData, Error>({
    queryKey: ['run-lineage', runId],
    queryFn: () => fetchLineage(runId),
    staleTime: 60_000, // lineage is append-only; cache aggressively
  });

  if (isLoading) {
    return (
      <div
        role="status"
        aria-label="Loading run lineage"
        className={['animate-pulse flex gap-2', className].filter(Boolean).join(' ')}
      >
        {[1, 2, 3].map((i) => (
          <span key={i} className="h-5 w-20 bg-surface-muted rounded" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <p
        role="alert"
        className={['text-xs text-destructive', className].filter(Boolean).join(' ')}
      >
        Failed to load lineage: {error.message}
      </p>
    );
  }

  if (!data || data.nodes.length === 0) {
    return (
      <p className={['text-xs text-fg-muted', className].filter(Boolean).join(' ')}>
        No lineage available
      </p>
    );
  }

  return (
    <nav
      aria-label="Run lineage"
      className={['flex items-center gap-1 flex-wrap text-xs', className]
        .filter(Boolean)
        .join(' ')}
    >
      {data.nodes.map((node, index) => (
        <React.Fragment key={node.id}>
          {index > 0 && (
            <span aria-hidden="true" className="text-fg-muted">
              /
            </span>
          )}
          <LineageNode node={node} onClick={onNodeClick} />
        </React.Fragment>
      ))}
    </nav>
  );
});

// ─────────────────────────────────────────────────────────────────────────────
// Sub-component
// ─────────────────────────────────────────────────────────────────────────────

interface LineageNodeProps {
  node: RunLineageNode;
  onClick?: (node: RunLineageNode) => void;
}

const TYPE_COLORS: Record<RunLineageNode['type'], string> = {
  workflow: 'text-info-color',
  plan: 'text-info-color',
  step: 'text-info-color',
  run: 'text-fg',
  agent: 'text-info-color',
};

function LineageNode({ node, onClick }: LineageNodeProps): React.ReactElement {
  const colorClass = TYPE_COLORS[node.type];
  const isInteractive = Boolean(onClick || node.href);

  if (isInteractive && onClick) {
    return (
      <Button
        type="button"
        onClick={() => onClick(node)}
        title={`${node.type}: ${node.id}`}
        className={[
          'font-medium hover:underline focus:outline-none focus-visible:ring-1 rounded',
          colorClass,
        ].join(' ')}
        variant="ghost"
        size="sm"
        aria-label={`${node.type} ${node.label}`}
      >
        {node.label}
      </Button>
    );
  }

  if (isInteractive && node.href) {
    return (
      <a
        href={node.href}
        title={`${node.type}: ${node.id}`}
        className={['font-medium hover:underline', colorClass].join(' ')}
        aria-label={`${node.type} ${node.label}`}
      >
        {node.label}
      </a>
    );
  }

  return (
    <span
      className={['font-medium', colorClass].join(' ')}
      title={`${node.type}: ${node.id}`}
    >
      {node.label}
    </span>
  );
}
