/**
 * PersonaContext Tests
 * 
 * @doc.type test
 * @doc.purpose Unit tests for PersonaContext
 * @doc.layer product
 */

import { renderHook, act } from '@testing-library/react';
import { PersonaProvider, usePersona, PERSONA_DEFINITIONS, ALL_PERSONA_TYPES } from '../PersonaContext';

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { store = {}; },
  };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

describe('PersonaContext', () => {
  beforeEach(() => {
    localStorageMock.clear();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <PersonaProvider>{children}</PersonaProvider>
  );

  describe('Initial State', () => {
    it('should have developer as default active persona', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      expect(result.current.activePersonas).toContain('developer');
      expect(result.current.primaryPersona).toBe('developer');
    });

    it('should have virtual personas for unfilled roles', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      // All personas except developer should be virtual
      const expectedVirtual = ALL_PERSONA_TYPES.filter(p => p !== 'developer');
      expect(result.current.virtualPersonas).toEqual(expect.arrayContaining(expectedVirtual));
    });

    it('should have all personas active (human + virtual)', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      expect(result.current.allActivePersonas).toHaveLength(ALL_PERSONA_TYPES.length);
    });
  });

  describe('Persona Definitions', () => {
    it('should have definitions for all persona types', () => {
      ALL_PERSONA_TYPES.forEach(personaType => {
        expect(PERSONA_DEFINITIONS[personaType]).toBeDefined();
        expect(PERSONA_DEFINITIONS[personaType].id).toBe(personaType);
        expect(PERSONA_DEFINITIONS[personaType].name).toBeTruthy();
        expect(PERSONA_DEFINITIONS[personaType].icon).toBeTruthy();
        expect(PERSONA_DEFINITIONS[personaType].color).toBeTruthy();
      });
    });
  });

  describe('togglePersona', () => {
    it('should add a persona when toggled on', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      act(() => {
        result.current.togglePersona('designer');
      });
      
      expect(result.current.activePersonas).toContain('designer');
      expect(result.current.virtualPersonas).not.toContain('designer');
    });

    it('should remove a persona when toggled off', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      // First add designer
      act(() => {
        result.current.togglePersona('designer');
      });
      
      // Then remove it
      act(() => {
        result.current.togglePersona('designer');
      });
      
      expect(result.current.activePersonas).not.toContain('designer');
      expect(result.current.virtualPersonas).toContain('designer');
    });

    it('should not remove the last active persona', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      // Try to remove the only active persona
      act(() => {
        result.current.togglePersona('developer');
      });
      
      // Should still have developer
      expect(result.current.activePersonas).toContain('developer');
    });
  });

  describe('setPrimaryPersona', () => {
    it('should set the primary persona', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      act(() => {
        result.current.setPrimaryPersona('designer');
      });
      
      expect(result.current.primaryPersona).toBe('designer');
      expect(result.current.activePersonas).toContain('designer');
    });

    it('should add persona to active list if not already active', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      expect(result.current.activePersonas).not.toContain('qa');
      
      act(() => {
        result.current.setPrimaryPersona('qa');
      });
      
      expect(result.current.activePersonas).toContain('qa');
      expect(result.current.primaryPersona).toBe('qa');
    });
  });

  describe('setActivePersonas', () => {
    it('should set multiple active personas', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      act(() => {
        result.current.setActivePersonas(['developer', 'designer', 'qa']);
      });
      
      expect(result.current.activePersonas).toHaveLength(3);
      expect(result.current.activePersonas).toContain('developer');
      expect(result.current.activePersonas).toContain('designer');
      expect(result.current.activePersonas).toContain('qa');
    });

    it('should not allow empty active personas', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      const originalPersonas = [...result.current.activePersonas];
      
      act(() => {
        result.current.setActivePersonas([]);
      });
      
      expect(result.current.activePersonas).toEqual(originalPersonas);
    });
  });

  describe('Query Functions', () => {
    it('hasPersona should return true for active personas', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      expect(result.current.hasPersona('developer')).toBe(true);
    });

    it('hasPersona should return true for virtual personas', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      // Security should be virtual by default
      expect(result.current.hasPersona('security')).toBe(true);
    });

    it('isVirtualPersona should correctly identify virtual personas', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      expect(result.current.isVirtualPersona('developer')).toBe(false);
      expect(result.current.isVirtualPersona('security')).toBe(true);
    });

    it('getPersonaDefinition should return correct definition', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      const def = result.current.getPersonaDefinition('developer');
      expect(def.id).toBe('developer');
      expect(def.name).toBe('Developer');
    });
  });

  describe('Virtual Persona Controls', () => {
    it('disableVirtualPersona should remove from virtual list', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      expect(result.current.virtualPersonas).toContain('security');
      
      act(() => {
        result.current.disableVirtualPersona('security');
      });
      
      expect(result.current.virtualPersonas).not.toContain('security');
    });

    it('enableVirtualPersona should re-add to virtual list', () => {
      const { result } = renderHook(() => usePersona(), { wrapper });
      
      act(() => {
        result.current.disableVirtualPersona('security');
      });
      
      expect(result.current.virtualPersonas).not.toContain('security');
      
      act(() => {
        result.current.enableVirtualPersona('security');
      });
      
      expect(result.current.virtualPersonas).toContain('security');
    });
  });
});
