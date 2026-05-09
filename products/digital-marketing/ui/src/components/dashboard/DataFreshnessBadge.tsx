import React from 'react';

interface DataFreshnessBadgeProps {
  source?: string;
  lastUpdated?: string | null;
  isPartial?: boolean;
}

export function DataFreshnessBadge({
  source,
  lastUpdated,
  isPartial = false,
}: DataFreshnessBadgeProps): React.ReactElement {
  const status = isPartial ? 'Partial data' : 'Freshness unknown';

  return (
    <div className="mt-2 text-[11px] text-gray-500" data-testid="data-freshness-badge">
      <span>{status}</span>
      {source ? <span> · Source: {source}</span> : null}
      {lastUpdated ? <span> · Updated: {new Date(lastUpdated).toLocaleString()}</span> : null}
    </div>
  );
}