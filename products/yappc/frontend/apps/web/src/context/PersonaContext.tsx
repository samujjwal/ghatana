/**
 * PersonaContext
 * 
 * Provides multi-persona selection with Virtual Persona (AI Agent) support.
 * When a human doesn't fill a role, a Virtual Persona is automatically assigned.
 * 
 * @doc.type context
 * @doc.purpose Persona state management with AI agent support
 * @doc.layer product
 * @doc.pattern Context Provider
 */

import React, { createContext, useContext, useState, useCallback, useMemo, useEffect } from 'react';

// Persona types
export type PersonaType = 
  | 'product-owner'
  | 'developer'
  | 'designer'
  | 'devops'
  | 'qa'
  | 'security';

export type PersonaCategory = 'TECHNICAL' | 'MANAGEMENT' | 'GOVERNANCE' | 'ANALYSIS';

export interface PersonaDefinition {
  id: PersonaType;
  name: string;
  shortName: string;
  description: string;
  category: PersonaCategory;
  icon: string;
  color: string;
  defaultActions: string[];
  canBeVirtual: boolean;
}

// All available personas
export const PERSONA_DEFINITIONS: Record<PersonaType, PersonaDefinition> = {
  'product-owner': {
    id: 'product-owner',
    name: 'Product Owner',
    shortName: 'PO',
    description: 'Defines requirements, prioritizes backlog, accepts deliverables',
    category: 'MANAGEMENT',
    icon: '📋',
    color: '#6366f1', // Indigo
    defaultActions: ['define', 'prioritize', 'accept'],
    canBeVirtual: true,
  },
  'developer': {
    id: 'developer',
    name: 'Developer',
    shortName: 'Dev',
    description: 'Implements features, writes code, creates tests',
    category: 'TECHNICAL',
    icon: '👨‍💻',
    color: '#3b82f6', // Blue
    defaultActions: ['build', 'test', 'deploy'],
    canBeVirtual: true,
  },
  'designer': {
    id: 'designer',
    name: 'Designer',
    shortName: 'UX',
    description: 'Creates UI/UX designs, prototypes, and design systems',
    category: 'TECHNICAL',
    icon: '🎨',
    color: '#ec4899', // Pink
    defaultActions: ['sketch', 'prototype', 'review'],
    canBeVirtual: true,
  },
  'devops': {
    id: 'devops',
    name: 'DevOps Engineer',
    shortName: 'Ops',
    description: 'Manages infrastructure, CI/CD, and deployments',
    category: 'TECHNICAL',
    icon: '🚀',
    color: '#f59e0b', // Amber
    defaultActions: ['configure', 'deploy', 'monitor'],
    canBeVirtual: true,
  },
  'qa': {
    id: 'qa',
    name: 'QA Engineer',
    shortName: 'QA',
    description: 'Tests features, reports bugs, ensures quality',
    category: 'ANALYSIS',
    icon: '🧪',
    color: '#10b981', // Emerald
    defaultActions: ['test', 'report', 'verify'],
    canBeVirtual: true,
  },
  'security': {
    id: 'security',
    name: 'Security Engineer',
    shortName: 'Sec',
    description: 'Audits security, identifies vulnerabilities, ensures compliance',
    category: 'GOVERNANCE',
    icon: '🔒',
    color: '#ef4444', // Red
    defaultActions: ['scan', 'audit', 'remediate'],
    canBeVirtual: true,
  },
};

// All persona types in order
export const ALL_PERSONA_TYPES: PersonaType[] = [
  'product-owner',
  'developer',
  'designer',
  'devops',
  'qa',
  'security',
];

// Context value interface
export interface PersonaContextValue {
  // Active human personas (selected by user)
  activePersonas: PersonaType[];
  
  // Primary persona (main role for the user)
  primaryPersona: PersonaType;
  
  // Virtual personas (AI-filled roles)
  virtualPersonas: PersonaType[];
  
  // All personas (human + virtual)
  allActivePersonas: PersonaType[];
  
  // Actions
  togglePersona: (id: PersonaType) => void;
  setPrimaryPersona: (id: PersonaType) => void;
  setActivePersonas: (ids: PersonaType[]) => void;
  
  // Queries
  hasPersona: (id: PersonaType) => boolean;
  isVirtualPersona: (id: PersonaType) => boolean;
  getPersonaDefinition: (id: PersonaType) => PersonaDefinition;
  
  // Virtual Agent controls
  enableVirtualPersona: (id: PersonaType) => void;
  disableVirtualPersona: (id: PersonaType) => void;
  
  // Persistence
  isLoaded: boolean;
}

// Storage key
const PERSONA_STORAGE_KEY = 'yappc_persona_selection';

interface StoredPersonaState {
  activePersonas: PersonaType[];
  primaryPersona: PersonaType;
  disabledVirtualPersonas: PersonaType[];
}

// Default context value
const defaultContextValue: PersonaContextValue = {
  activePersonas: ['developer'],
  primaryPersona: 'developer',
  virtualPersonas: ['product-owner', 'designer', 'devops', 'qa', 'security'],
  allActivePersonas: ALL_PERSONA_TYPES,
  togglePersona: () => {},
  setPrimaryPersona: () => {},
  setActivePersonas: () => {},
  hasPersona: () => false,
  isVirtualPersona: () => false,
  getPersonaDefinition: (id) => PERSONA_DEFINITIONS[id],
  enableVirtualPersona: () => {},
  disableVirtualPersona: () => {},
  isLoaded: false,
};

// Create context
const PersonaContext = createContext<PersonaContextValue>(defaultContextValue);

// Provider props
interface PersonaProviderProps {
  children: React.ReactNode;
  defaultPersonas?: PersonaType[];
  defaultPrimary?: PersonaType;
}

/**
 * PersonaProvider
 * 
 * Provides persona context to the application.
 * Automatically assigns Virtual Personas (AI Agents) for unfilled roles.
 */
export function PersonaProvider({
  children,
  defaultPersonas = ['developer'],
  defaultPrimary = 'developer',
}: PersonaProviderProps) {
  const [isLoaded, setIsLoaded] = useState(false);
  const [activePersonas, setActivePersonasState] = useState<PersonaType[]>(defaultPersonas);
  const [primaryPersona, setPrimaryPersonaState] = useState<PersonaType>(defaultPrimary);
  const [disabledVirtualPersonas, setDisabledVirtualPersonas] = useState<PersonaType[]>([]);

  // Load from localStorage on mount
  useEffect(() => {
    try {
      const stored = localStorage.getItem(PERSONA_STORAGE_KEY);
      if (stored) {
        const parsed: StoredPersonaState = JSON.parse(stored);
        if (parsed.activePersonas?.length > 0) {
          setActivePersonasState(parsed.activePersonas);
        }
        if (parsed.primaryPersona) {
          setPrimaryPersonaState(parsed.primaryPersona);
        }
        if (parsed.disabledVirtualPersonas) {
          setDisabledVirtualPersonas(parsed.disabledVirtualPersonas);
        }
      }
    } catch (error) {
      console.warn('Failed to load persona state from localStorage:', error);
    }
    setIsLoaded(true);
  }, []);

  // Persist to localStorage on change
  useEffect(() => {
    if (!isLoaded) return;
    
    try {
      const state: StoredPersonaState = {
        activePersonas,
        primaryPersona,
        disabledVirtualPersonas,
      };
      localStorage.setItem(PERSONA_STORAGE_KEY, JSON.stringify(state));
    } catch (error) {
      console.warn('Failed to save persona state to localStorage:', error);
    }
  }, [activePersonas, primaryPersona, disabledVirtualPersonas, isLoaded]);

  // Calculate virtual personas (roles not filled by humans, unless disabled)
  const virtualPersonas = useMemo(() => {
    return ALL_PERSONA_TYPES.filter(
      (id) => 
        !activePersonas.includes(id) && 
        !disabledVirtualPersonas.includes(id) &&
        PERSONA_DEFINITIONS[id].canBeVirtual
    );
  }, [activePersonas, disabledVirtualPersonas]);

  // All active personas (human + virtual)
  const allActivePersonas = useMemo(() => {
    return [...new Set([...activePersonas, ...virtualPersonas])];
  }, [activePersonas, virtualPersonas]);

  // Toggle a persona on/off
  const togglePersona = useCallback((id: PersonaType) => {
    setActivePersonasState((prev) => {
      if (prev.includes(id)) {
        // Don't allow removing the last persona
        if (prev.length === 1) return prev;
        
        // If removing primary, set new primary
        if (id === primaryPersona) {
          const remaining = prev.filter((p) => p !== id);
          setPrimaryPersonaState(remaining[0]);
        }
        
        return prev.filter((p) => p !== id);
      } else {
        return [...prev, id];
      }
    });
  }, [primaryPersona]);

  // Set primary persona
  const setPrimaryPersona = useCallback((id: PersonaType) => {
    setPrimaryPersonaState(id);
    // Ensure primary is in active list
    setActivePersonasState((prev) => {
      if (!prev.includes(id)) {
        return [...prev, id];
      }
      return prev;
    });
  }, []);

  // Set all active personas at once
  const setActivePersonas = useCallback((ids: PersonaType[]) => {
    if (ids.length === 0) return;
    
    setActivePersonasState(ids);
    
    // Ensure primary is in the list
    if (!ids.includes(primaryPersona)) {
      setPrimaryPersonaState(ids[0]);
    }
  }, [primaryPersona]);

  // Check if a persona is active (human or virtual)
  const hasPersona = useCallback((id: PersonaType): boolean => {
    return allActivePersonas.includes(id);
  }, [allActivePersonas]);

  // Check if a persona is virtual
  const isVirtualPersona = useCallback((id: PersonaType): boolean => {
    return virtualPersonas.includes(id);
  }, [virtualPersonas]);

  // Get persona definition
  const getPersonaDefinition = useCallback((id: PersonaType): PersonaDefinition => {
    return PERSONA_DEFINITIONS[id];
  }, []);

  // Enable a virtual persona
  const enableVirtualPersona = useCallback((id: PersonaType) => {
    setDisabledVirtualPersonas((prev) => prev.filter((p) => p !== id));
  }, []);

  // Disable a virtual persona
  const disableVirtualPersona = useCallback((id: PersonaType) => {
    setDisabledVirtualPersonas((prev) => {
      if (prev.includes(id)) return prev;
      return [...prev, id];
    });
  }, []);

  const value: PersonaContextValue = useMemo(() => ({
    activePersonas,
    primaryPersona,
    virtualPersonas,
    allActivePersonas,
    togglePersona,
    setPrimaryPersona,
    setActivePersonas,
    hasPersona,
    isVirtualPersona,
    getPersonaDefinition,
    enableVirtualPersona,
    disableVirtualPersona,
    isLoaded,
  }), [
    activePersonas,
    primaryPersona,
    virtualPersonas,
    allActivePersonas,
    togglePersona,
    setPrimaryPersona,
    setActivePersonas,
    hasPersona,
    isVirtualPersona,
    getPersonaDefinition,
    enableVirtualPersona,
    disableVirtualPersona,
    isLoaded,
  ]);

  return (
    <PersonaContext.Provider value={value}>
      {children}
    </PersonaContext.Provider>
  );
}

/**
 * usePersona hook
 * 
 * Access persona context from any component.
 */
export function usePersona(): PersonaContextValue {
  const context = useContext(PersonaContext);
  if (!context) {
    throw new Error('usePersona must be used within a PersonaProvider');
  }
  return context;
}

/**
 * usePersonaCanvasConfig hook
 * 
 * Returns persona-specific canvas configuration.
 */
export function usePersonaCanvasConfig(activePersonas: PersonaType[]) {
  return useMemo(() => {
    // Determine visible tools based on personas
    const visibleTools: string[] = [];
    const quickActions: string[] = [];
    const suggestedPanels: string[] = [];

    activePersonas.forEach((persona) => {
      switch (persona) {
        case 'developer':
          visibleTools.push('component', 'api', 'data', 'flow');
          quickActions.push('add-component', 'generate-code', 'run-tests');
          suggestedPanels.push('codeGen', 'validation');
          break;
        case 'designer':
          visibleTools.push('sketch', 'page', 'component', 'sticky');
          quickActions.push('add-page', 'sketch-mode', 'design-system');
          suggestedPanels.push('designer', 'comments');
          break;
        case 'product-owner':
          visibleTools.push('sticky', 'flow', 'page');
          quickActions.push('add-story', 'prioritize', 'accept');
          suggestedPanels.push('guidance', 'tasks');
          break;
        case 'devops':
          visibleTools.push('api', 'data', 'flow');
          quickActions.push('deploy', 'configure', 'monitor');
          suggestedPanels.push('validation', 'codeGen');
          break;
        case 'qa':
          visibleTools.push('component', 'flow', 'api');
          quickActions.push('run-tests', 'create-bug', 'verify');
          suggestedPanels.push('validation', 'tasks');
          break;
        case 'security':
          visibleTools.push('api', 'data', 'flow');
          quickActions.push('scan', 'audit', 'block');
          suggestedPanels.push('validation', 'guidance');
          break;
      }
    });

    return {
      visibleTools: [...new Set(visibleTools)],
      quickActions: [...new Set(quickActions)],
      suggestedPanels: [...new Set(suggestedPanels)],
      defaultMode: activePersonas.includes('designer') ? 'sketch' : 'select',
    };
  }, [activePersonas]);
}

export default PersonaContext;
