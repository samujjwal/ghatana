/**
 * EntityDeepLink — typed route + filter deep-linking for pipeline, run, agent, policy entities.
 *
 * @doc.type component
 * @doc.purpose Generate deep links to entity detail and filtered list pages
 * @doc.layer frontend
 * @doc.pattern Navigation / Deep-linking
 */
import React from 'react';
import { Link } from 'react-router';

export type EntityType = 'pipeline' | 'run' | 'agent' | 'policy' | 'review' | 'cost-alert';

interface EntityDeepLinkProps {
  entityType: EntityType;
  entityId: string;
  label: string;
  /** Optional filter params to apply on the destination list page. */
  filterParams?: Record<string, string>;
  className?: string;
}

function buildUrl(type: EntityType, id: string, filterParams?: Record<string, string>): string {
  switch (type) {
    case 'pipeline': return `/build/pipelines/${id}/edit`;
    case 'run': return `/operate/runs/${id}`;
    case 'agent': return `/catalog/agents/${id}`;
    case 'policy': return `/build/patterns?tab=learning&policy=${id}`;
    case 'review': return `/operate/reviews?run=${id}`;
    case 'cost-alert': return `/operate/costs?alert=${id}`;
  }
}

export function EntityDeepLink({
  entityType,
  entityId,
  label,
  filterParams,
  className = '',
}: EntityDeepLinkProps): React.ReactElement {
  const to = buildUrl(entityType, entityId, filterParams);
  const search = filterParams ? new URLSearchParams(filterParams).toString() : '';
  const fullPath = search ? `${to}?${search}` : to;

  return (
    <Link
      to={fullPath}
      className={[
        'inline-flex items-center gap-1 text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium',
        className,
      ].join(' ')}
      title={`Open ${entityType} ${entityId}`}
    >
      {label}
    </Link>
  );
}

export function getEntityUrl(type: EntityType, id: string): string {
  return buildUrl(type, id);
}
