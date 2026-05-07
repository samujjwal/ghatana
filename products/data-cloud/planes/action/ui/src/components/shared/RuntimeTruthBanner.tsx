import React from 'react';
import { useCapabilities } from '@/hooks/useCapabilities';
interface CapabilityBadgeProps { label: string; active: boolean; }
function CapabilityBadge({ label, active }: CapabilityBadgeProps): React.ReactElement {
  return (
    <span className={['inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide', active ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200' : 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200'].join(' ')}>
      <span aria-hidden="true" className={active ? 'text-emerald-500' : 'text-amber-500'}>{active ? '●' : '○'}</span>
      {label}
    </span>
  );
}
export function RuntimeTruthBanner(): React.ReactElement | null {
  const { capabilities, isLoading, isDegraded } = useCapabilities();
  if (isLoading) return null;
  return (
    <div data-testid="runtime-truth-banner" role="status" aria-label="Runtime capability status" className={['flex flex-wrap items-center gap-2 px-4 py-1.5 text-xs border-b', isDegraded ? 'bg-amber-50 border-amber-200 dark:bg-amber-950/30 dark:border-amber-800/50' : 'bg-gray-50 border-gray-200 dark:bg-gray-900 dark:border-gray-800'].join(' ')}>
      <span className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mr-1">Runtime</span>
      <CapabilityBadge label="Data Cloud" active={capabilities.dataCloud} />
      <CapabilityBadge label="Durable sessions" active={capabilities.durableSessions} />
      <CapabilityBadge label="PII block" active={capabilities.piiEnforcement} />
      <CapabilityBadge label="GDPR" active={capabilities.gdprCompliance} />
      <CapabilityBadge label="Learning" active={capabilities.episodeLearning} />
      <CapabilityBadge label="SOC2" active={capabilities.soc2Compliance} />
      {isDegraded && <span className="ml-auto text-amber-700 dark:text-amber-300 text-[10px]">⚠ Non-durable capabilities detected</span>}
    </div>
  );
}
