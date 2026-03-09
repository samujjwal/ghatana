/**
 * Agent Store Atoms - Jotai migration from Zustand
 * Manages AI agent state and conversation with fine-grained reactivity
 */

import { atom } from 'jotai';

// Types (preserved from original store)
export type AgentStatus = 'idle' | 'thinking' | 'responding' | 'error';

export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
}

export interface AgentContext {
  current_page?: string;
  selected_text?: string;
  user_intent?: string;
  metrics?: Record<string, unknown>;
}

export interface AgentCapability {
  name: string;
  description: string;
  enabled: boolean;
}

// Core state atoms
export const agentStatusAtom = atom<AgentStatus>('idle');
export const agentMessagesBackingAtom = atom<Message[]>([]);
export const agentContextAtom = atom<AgentContext>({});
export const agentCapabilitiesAtom = atom<AgentCapability[]>([
  { name: 'data_analysis', description: 'Analyze metrics and trends', enabled: true },
  { name: 'recommendations', description: 'Provide actionable insights', enabled: true },
  { name: 'automation', description: 'Automate repetitive tasks', enabled: false },
]);
export const agentErrorAtom = atom<string | null>(null);
export const agentIsProcessingAtom = atom<boolean>(false);

// Public read-only messages atom
export const agentMessagesAtom = atom(get => get(agentMessagesBackingAtom));

// Recent messages (optimized for chat UI)
export const agentRecentMessagesAtom = atom(get => get(agentMessagesBackingAtom).slice(-50));

// Messages count
export const agentMessagesCountAtom = atom(get => get(agentMessagesBackingAtom).length);

// Derived status for UI
export const agentIsActiveAtom = atom(get => {
  const status = get(agentStatusAtom);
  return status === 'thinking' || status === 'responding';
});

// Write-only action atoms
export const addAgentMessageAtom = atom(null, (get, set, message: Message) => {
  set(agentMessagesBackingAtom, prev => [...prev, message]);
  // Reset status to idle after adding message
  if (message.role === 'assistant') {
    set(agentStatusAtom, 'idle');
    set(agentIsProcessingAtom, false);
  }
});

export const clearAgentMessagesAtom = atom(null, (get, set) => {
  set(agentMessagesBackingAtom, []);
  set(agentStatusAtom, 'idle');
  set(agentErrorAtom, null);
});

export const setAgentStatusAtom = atom(null, (get, set, status: AgentStatus) => {
  set(agentStatusAtom, status);
  set(agentIsProcessingAtom, status === 'thinking' || status === 'responding');
  if (status === 'error') {
    set(agentIsProcessingAtom, false);
  }
});

export const updateAgentContextAtom = atom(
  null,
  (get, set, contextUpdate: Partial<AgentContext>) => {
    set(agentContextAtom, prev => ({
      ...prev,
      ...contextUpdate,
    }));
  }
);

export const setAgentErrorAtom = atom(null, (get, set, error: string | null) => {
  set(agentErrorAtom, error);
  if (error) {
    set(agentStatusAtom, 'error');
    set(agentIsProcessingAtom, false);
  }
});

export const toggleAgentCapabilityAtom = atom(null, (get, set, capabilityName: string) => {
  set(agentCapabilitiesAtom, prev =>
    prev.map(cap => (cap.name === capabilityName ? { ...cap, enabled: !cap.enabled } : cap))
  );
});

// Derived agent state summary
export const agentStateSummaryAtom = atom(get => {
  const status = get(agentStatusAtom);
  const messageCount = get(agentMessagesCountAtom);
  const error = get(agentErrorAtom);
  const isActive = get(agentIsActiveAtom);

  return {
    status,
    messageCount,
    error,
    isActive,
    hasMessages: messageCount > 0,
  };
});
