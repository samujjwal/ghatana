/**
 * DEPRECATED: This file is kept for backward compatibility only
 * The store has been migrated to Jotai atoms
 *
 * @see src/atoms/agentAtoms.ts - Atom definitions
 * @see src/hooks/useStores.ts - Backward-compatible hooks
 */

export { useAgentStore } from '../hooks/useStores';
export type { AgentStatus, Message, AgentContext, AgentCapability } from '../atoms/agentAtoms';

// Legacy types for compatibility
export interface Metric {
  name: string;
  value: number;
  unit?: string;
  timestamp: number;
  labels?: Record<string, string>;
}

export interface Event {
  id: string;
  type: string;
  message: string;
  timestamp: number;
  severity?: 'info' | 'warning' | 'error';
}
