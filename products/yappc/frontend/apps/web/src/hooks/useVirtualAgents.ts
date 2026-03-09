/**
 * useVirtualAgents Hook
 * 
 * React hook for integrating Virtual Agent Service with components.
 * Manages agent lifecycle and provides reactive access to agent actions.
 * 
 * @doc.type hook
 * @doc.purpose Virtual Agent integration
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { 
  getVirtualAgentService, 
  type AgentAction, 
  type PersonaType 
} from '../services/VirtualAgentService';
import { canvasAtom } from '../components/canvas/workspace/canvasAtoms';
import { usePersona } from '../context/PersonaContext';

interface UseVirtualAgentsOptions {
  projectId: string;
  lifecyclePhase: string;
  autoStart?: boolean;
}

interface UseVirtualAgentsResult {
  actions: AgentAction[];
  activeActions: AgentAction[];
  blockingActions: AgentAction[];
  
  actionsByPersona: (persona: PersonaType) => AgentAction[];
  
  dismissAction: (actionId: string) => void;
  overrideAction: (actionId: string) => void;
  clearActions: () => void;
  
  runCheck: (persona: PersonaType) => Promise<void>;
  runAllChecks: () => Promise<void>;
  checkDeployBlocked: () => Promise<{ blocked: boolean; blockers: AgentAction[] }>;
  
  isChecking: boolean;
  lastCheckTime: Date | null;
}

export function useVirtualAgents({
  projectId,
  lifecyclePhase,
  autoStart = true,
}: UseVirtualAgentsOptions): UseVirtualAgentsResult {
  const [actions, setActions] = useState<AgentAction[]>([]);
  const [isChecking, setIsChecking] = useState(false);
  const [lastCheckTime, setLastCheckTime] = useState<Date | null>(null);
  
  const canvasState = useAtomValue(canvasAtom);
  const { virtualPersonas } = usePersona();
  
  const service = useMemo(() => getVirtualAgentService(), []);

  // Update service context when dependencies change
  useEffect(() => {
    service.setContext({
      projectId,
      canvasState,
      lifecyclePhase,
    });
  }, [service, projectId, canvasState, lifecyclePhase]);

  // Subscribe to action updates
  useEffect(() => {
    const unsubscribe = service.subscribe((newActions) => {
      setActions(newActions);
    });

    // Initialize with current actions
    setActions(service.getActions());

    return unsubscribe;
  }, [service]);

  // Start/stop agent checks based on virtual personas
  useEffect(() => {
    if (!autoStart) return;

    // Start checks for virtual personas
    virtualPersonas.forEach((persona) => {
      service.enableAgent(persona);
      service.startAgentChecks(persona);
    });

    return () => {
      // Stop all checks on cleanup
      virtualPersonas.forEach((persona) => {
        service.stopAgentChecks(persona);
      });
    };
  }, [service, virtualPersonas, autoStart]);

  // Derived state
  const activeActions = useMemo(() => 
    actions.filter(a => !a.dismissed),
    [actions]
  );

  const blockingActions = useMemo(() => 
    actions.filter(a => a.type === 'block_deploy' && !a.dismissed),
    [actions]
  );

  // Action handlers
  const dismissAction = useCallback((actionId: string) => {
    service.dismissAction(actionId);
  }, [service]);

  const overrideAction = useCallback((actionId: string) => {
    service.overrideAction(actionId);
  }, [service]);

  const clearActions = useCallback(() => {
    service.clearActions();
  }, [service]);

  const runCheck = useCallback(async (persona: PersonaType) => {
    setIsChecking(true);
    try {
      await service.runAgentCheck(persona);
      setLastCheckTime(new Date());
    } finally {
      setIsChecking(false);
    }
  }, [service]);

  const runAllChecks = useCallback(async () => {
    setIsChecking(true);
    try {
      await service.runAllChecks();
      setLastCheckTime(new Date());
    } finally {
      setIsChecking(false);
    }
  }, [service]);

  const checkDeployBlocked = useCallback(async () => {
    setIsChecking(true);
    try {
      const result = await service.checkDeployBlocked();
      setLastCheckTime(new Date());
      return result;
    } finally {
      setIsChecking(false);
    }
  }, [service]);

  const actionsByPersona = useCallback((persona: PersonaType) => {
    return actions.filter(a => a.agentPersona === persona);
  }, [actions]);

  return {
    actions,
    activeActions,
    blockingActions,
    actionsByPersona,
    dismissAction,
    overrideAction,
    clearActions,
    runCheck,
    runAllChecks,
    checkDeployBlocked,
    isChecking,
    lastCheckTime,
  };
}

export default useVirtualAgents;
