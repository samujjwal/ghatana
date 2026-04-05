/**
 * usePersonaAdaptiveCanvas Hook
 * 
 * @doc.type hook
 * @doc.purpose Adapts canvas behavior and tools based on active personas
 * @doc.layer product
 * @doc.pattern Hook
 * 
 * This hook provides persona-specific canvas configurations including:
 * - Preferred modes for each persona
 * - Quick actions relevant to the persona
 * - Tool visibility settings
 * - AI agent suggestions when virtual personas are active
 */

import { useMemo, useCallback } from 'react';
import { usePersona } from '../context/PersonaContext';

import type { PersonaType } from '../context/PersonaContext';
import type { CanvasMode } from '../types/canvas';

/**
 * Persona-specific mode preferences
 * Each persona has preferred modes they work in most often
 */
const PERSONA_MODE_PREFERENCES: Record<PersonaType, {
  primaryModes: CanvasMode[];
  secondaryModes: CanvasMode[];
  suggestedStartMode: CanvasMode;
}> = {
  'product-owner': {
    primaryModes: ['brainstorm', 'diagram'],
    secondaryModes: ['design'],
    suggestedStartMode: 'brainstorm',
  },
  'developer': {
    primaryModes: ['code', 'diagram', 'test'],
    secondaryModes: ['brainstorm', 'design'],
    suggestedStartMode: 'code',
  },
  'designer': {
    primaryModes: ['design', 'brainstorm'],
    secondaryModes: ['diagram'],
    suggestedStartMode: 'design',
  },
  'devops': {
    primaryModes: ['deploy', 'observe', 'diagram'],
    secondaryModes: ['code'],
    suggestedStartMode: 'deploy',
  },
  'qa': {
    primaryModes: ['test', 'observe'],
    secondaryModes: ['code', 'diagram'],
    suggestedStartMode: 'test',
  },
  'security': {
    primaryModes: ['test', 'observe', 'deploy'],
    secondaryModes: ['diagram', 'code'],
    suggestedStartMode: 'observe',
  },
};

/**
 * Quick actions available for each persona
 */
const PERSONA_QUICK_ACTIONS: Record<PersonaType, {
  label: string;
  icon: string;
  action: string;
  mode?: CanvasMode;
}[]> = {
  'product-owner': [
    { label: 'New User Story', icon: '📝', action: 'create-user-story', mode: 'brainstorm' },
    { label: 'Prioritize Backlog', icon: '📊', action: 'prioritize', mode: 'brainstorm' },
    { label: 'Review Requirements', icon: '✅', action: 'review-requirements', mode: 'diagram' },
  ],
  'developer': [
    { label: 'New Component', icon: '🧱', action: 'create-component', mode: 'code' },
    { label: 'View Dependencies', icon: '🔗', action: 'view-deps', mode: 'diagram' },
    { label: 'Generate Tests', icon: '🧪', action: 'gen-tests', mode: 'test' },
  ],
  'designer': [
    { label: 'New Page', icon: '📄', action: 'create-page', mode: 'design' },
    { label: 'Edit Theme', icon: '🎨', action: 'edit-theme', mode: 'design' },
    { label: 'Component Specs', icon: '📐', action: 'component-specs', mode: 'design' },
  ],
  'devops': [
    { label: 'New Pipeline', icon: '⚙️', action: 'create-pipeline', mode: 'deploy' },
    { label: 'View Infra', icon: '☁️', action: 'view-infra', mode: 'deploy' },
    { label: 'Check Health', icon: '💚', action: 'health-check', mode: 'observe' },
  ],
  'qa': [
    { label: 'New Test Suite', icon: '📋', action: 'create-test-suite', mode: 'test' },
    { label: 'Run Tests', icon: '▶️', action: 'run-tests', mode: 'test' },
    { label: 'View Coverage', icon: '📊', action: 'view-coverage', mode: 'test' },
  ],
  'security': [
    { label: 'Security Scan', icon: '🔍', action: 'security-scan', mode: 'test' },
    { label: 'Audit Logs', icon: '📜', action: 'audit-logs', mode: 'observe' },
    { label: 'Compliance Check', icon: '✅', action: 'compliance', mode: 'deploy' },
  ],
};

/**
 * Configuration for virtual (AI agent) personas
 */
const VIRTUAL_PERSONA_CONFIG: Record<PersonaType, {
  agentName: string;
  capabilities: string[];
  autoActions: string[];
}> = {
  'product-owner': {
    agentName: 'Product AI',
    capabilities: ['Auto-generate user stories from diagrams', 'Suggest prioritization', 'Draft acceptance criteria'],
    autoActions: ['suggest-stories', 'prioritize-backlog'],
  },
  'developer': {
    agentName: 'Copilot',
    capabilities: ['Code completion', 'Generate tests', 'Refactor suggestions'],
    autoActions: ['complete-code', 'gen-tests'],
  },
  'designer': {
    agentName: 'Design AI',
    capabilities: ['Generate layouts', 'Suggest component variants', 'Create design tokens'],
    autoActions: ['gen-layout', 'suggest-variants'],
  },
  'devops': {
    agentName: 'Ops AI',
    capabilities: ['Generate Dockerfiles', 'Create CI/CD configs', 'Infrastructure suggestions'],
    autoActions: ['gen-dockerfile', 'gen-pipeline'],
  },
  'qa': {
    agentName: 'Test AI',
    capabilities: ['Generate test cases', 'Find edge cases', 'Coverage analysis'],
    autoActions: ['gen-tests', 'analyze-coverage'],
  },
  'security': {
    agentName: 'SecBot',
    capabilities: ['Vulnerability scanning', 'Compliance checking', 'Security recommendations'],
    autoActions: ['scan-vulnerabilities', 'check-compliance'],
  },
};

export interface PersonaAdaptiveCanvasConfig {
  /** Current primary persona */
  primaryPersona: PersonaType;
  
  /** All active human personas */
  activePersonas: PersonaType[];
  
  /** Virtual (AI agent) personas filling unfilled roles */
  virtualPersonas: PersonaType[];
  
  /** All active personas (human + virtual) */
  allPersonas: PersonaType[];
  
  /** Preferred starting mode based on primary persona */
  suggestedStartMode: CanvasMode;
  
  /** Primary modes for the active personas */
  preferredModes: CanvasMode[];
  
  /** All available quick actions for active personas */
  quickActions: {
    personaId: PersonaType;
    personaName: string;
    personaIcon: string;
    isVirtual: boolean;
    agentName?: string;
    actions: typeof PERSONA_QUICK_ACTIONS[PersonaType];
  }[];
  
  /** Mode visibility - which modes should be highlighted */
  modeVisibility: Record<CanvasMode, 'primary' | 'secondary' | 'available'>;
  
  /** Whether a specific mode is relevant to active personas */
  isModeRelevant: (mode: CanvasMode) => boolean;
  
  /** Get persona-specific tools for a mode */
  getToolsForMode: (mode: CanvasMode) => string[];
  
  /** Get virtual agent info if this role is filled by AI */
  getVirtualAgentInfo: (personaId: PersonaType) => {
    isVirtual: boolean;
    agentName?: string;
    capabilities?: string[];
  };
}

/**
 * Hook for persona-adaptive canvas configuration
 * 
 * @example
 * ```tsx
 * const {
 *   preferredModes,
 *   quickActions,
 *   suggestedStartMode,
 *   isModeRelevant,
 * } = usePersonaAdaptiveCanvas();
 * ```
 */
export function usePersonaAdaptiveCanvas(): PersonaAdaptiveCanvasConfig {
  const {
    primaryPersona,
    activePersonas,
    virtualPersonas,
    allActivePersonas,
    isVirtualPersona,
    getPersonaDefinition,
  } = usePersona();

  // Calculate preferred modes across all active personas
  const preferredModes = useMemo(() => {
    const modes = new Set<CanvasMode>();
    
    // Add primary modes from all active personas
    allActivePersonas.forEach(personaId => {
      const prefs = PERSONA_MODE_PREFERENCES[personaId];
      prefs.primaryModes.forEach(mode => modes.add(mode));
    });
    
    return Array.from(modes);
  }, [allActivePersonas]);

  // Get suggested start mode from primary persona
  const suggestedStartMode = useMemo(() => {
    return PERSONA_MODE_PREFERENCES[primaryPersona].suggestedStartMode;
  }, [primaryPersona]);

  // Calculate mode visibility
  const modeVisibility = useMemo(() => {
    const visibility: Record<CanvasMode, 'primary' | 'secondary' | 'available'> = {
      brainstorm: 'available',
      diagram: 'available',
      design: 'available',
      code: 'available',
      test: 'available',
      deploy: 'available',
      observe: 'available',
    };

    // Mark primary modes
    allActivePersonas.forEach(personaId => {
      const prefs = PERSONA_MODE_PREFERENCES[personaId];
      prefs.primaryModes.forEach(mode => {
        visibility[mode] = 'primary';
      });
    });

    // Mark secondary modes (only if not already primary)
    allActivePersonas.forEach(personaId => {
      const prefs = PERSONA_MODE_PREFERENCES[personaId];
      prefs.secondaryModes.forEach(mode => {
        if (visibility[mode] === 'available') {
          visibility[mode] = 'secondary';
        }
      });
    });

    return visibility;
  }, [allActivePersonas]);

  // Collect quick actions from all personas
  const quickActions = useMemo(() => {
    return allActivePersonas.map(personaId => {
      const definition = getPersonaDefinition(personaId);
      const isVirtual = isVirtualPersona(personaId);
      const virtualConfig = isVirtual ? VIRTUAL_PERSONA_CONFIG[personaId] : undefined;

      return {
        personaId,
        personaName: definition.name,
        personaIcon: definition.icon,
        isVirtual,
        agentName: virtualConfig?.agentName,
        actions: PERSONA_QUICK_ACTIONS[personaId],
      };
    });
  }, [allActivePersonas, getPersonaDefinition, isVirtualPersona]);

  // Check if a mode is relevant to active personas
  const isModeRelevant = useCallback((mode: CanvasMode): boolean => {
    return modeVisibility[mode] === 'primary' || modeVisibility[mode] === 'secondary';
  }, [modeVisibility]);

  // Get tools for a mode based on active personas
  const getToolsForMode = useCallback((mode: CanvasMode): string[] => {
    const tools: string[] = [];
    
    allActivePersonas.forEach(personaId => {
      const prefs = PERSONA_MODE_PREFERENCES[personaId];
      if (prefs.primaryModes.includes(mode) || prefs.secondaryModes.includes(mode)) {
        const definition = getPersonaDefinition(personaId);
        // Add persona-specific tools
        tools.push(...definition.defaultActions.map(action => 
          `${definition.shortName}: ${action}`
        ));
      }
    });
    
    return [...new Set(tools)];
  }, [allActivePersonas, getPersonaDefinition]);

  // Get virtual agent info for a persona
  const getVirtualAgentInfo = useCallback((personaId: PersonaType) => {
    const isVirtual = isVirtualPersona(personaId);
    
    if (!isVirtual) {
      return { isVirtual: false };
    }
    
    const config = VIRTUAL_PERSONA_CONFIG[personaId];
    return {
      isVirtual: true,
      agentName: config.agentName,
      capabilities: config.capabilities,
    };
  }, [isVirtualPersona]);

  return {
    primaryPersona,
    activePersonas,
    virtualPersonas,
    allPersonas: allActivePersonas,
    suggestedStartMode,
    preferredModes,
    quickActions,
    modeVisibility,
    isModeRelevant,
    getToolsForMode,
    getVirtualAgentInfo,
  };
}

export default usePersonaAdaptiveCanvas;
