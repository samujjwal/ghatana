import type { BadgeProps } from '@ghatana/design-system';
import type { StudioLifecycleDataStatus } from '../data/StudioLifecycleDataContext';

export function describeLifecycleDataStatus(status: StudioLifecycleDataStatus): string {
  if (status === 'ready') {
    return 'Kernel data loaded';
  }
  if (status === 'loading') {
    return 'Loading Kernel data';
  }
  if (status === 'degraded') {
    return 'Kernel data degraded';
  }
  return 'Kernel API not configured';
}

export function lifecycleDataBadgeTone(status: StudioLifecycleDataStatus): BadgeProps['tone'] {
  if (status === 'ready') {
    return 'success';
  }
  if (status === 'loading') {
    return 'info';
  }
  if (status === 'degraded') {
    return 'warning';
  }
  return 'neutral';
}

export function formatBytes(sizeBytes: number): string {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  const kibibytes = sizeBytes / 1024;
  if (kibibytes < 1024) {
    return `${kibibytes.toFixed(1)} KiB`;
  }
  return `${(kibibytes / 1024).toFixed(1)} MiB`;
}
