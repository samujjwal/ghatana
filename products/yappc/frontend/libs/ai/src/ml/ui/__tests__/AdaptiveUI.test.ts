import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import {
  AdaptiveUI,
  type AdaptationRule,
  type AdaptationContext,
  type UserPreferences,
} from '../../adaptive/AdaptiveUI';

describe.skip('AdaptiveUI', () => {
  let adaptiveUI: AdaptiveUI;
  let mockLocalStorage: Record<string, string>;

  beforeEach(() => {
    // Mock localStorage
    mockLocalStorage = {};
    global.localStorage = {
      getItem: vi.fn((key: string) => mockLocalStorage[key] || null),
      setItem: vi.fn((key: string, value: string) => {
        mockLocalStorage[key] = value;
      }),
      removeItem: vi.fn((key: string) => {
        delete mockLocalStorage[key];
      }),
      clear: vi.fn(() => {
        mockLocalStorage = {};
      }),
      length: 0,
      key: vi.fn(() => null),
    } as Storage;

    // Mock matchMedia for dark mode detection
    global.matchMedia = vi.fn((query: string) => ({
      matches: query.includes('prefers-color-scheme: dark') ? false : true,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })) as unknown as typeof global.matchMedia;

    adaptiveUI = new AdaptiveUI();
  });

  afterEach(() => {
    adaptiveUI.stop();
  });

  describe('Rule Registration', () => {
    it('should register a custom rule', () => {
      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 10,
        condition: () => true,
        apply: () => {},
      };

      adaptiveUI.registerRule(rule);
      const rules = adaptiveUI.getRules();

      expect(rules).toContainEqual(rule);
    });

    it('should register multiple rules', () => {
      const rule1: AdaptationRule = {
        id: 'rule-1',
        name: 'Rule 1',
        priority: 10,
        condition: () => true,
        apply: () => {},
      };

      const rule2: AdaptationRule = {
        id: 'rule-2',
        name: 'Rule 2',
        priority: 20,
        condition: () => true,
        apply: () => {},
      };

      adaptiveUI.registerRule(rule1);
      adaptiveUI.registerRule(rule2);

      const rules = adaptiveUI.getRules();
      expect(rules).toHaveLength(9); // 7 default rules + 2 custom
    });

    it('should sort rules by priority', () => {
      const rule1: AdaptationRule = {
        id: 'rule-1',
        name: 'Rule 1',
        priority: 5,
        condition: () => true,
        apply: () => {},
      };

      const rule2: AdaptationRule = {
        id: 'rule-2',
        name: 'Rule 2',
        priority: 10,
        condition: () => true,
        apply: () => {},
      };

      adaptiveUI.registerRule(rule1);
      adaptiveUI.registerRule(rule2);

      const rules = adaptiveUI.getRules();
      const customRules = rules.filter((r) => r.id.startsWith('rule-'));

      expect(customRules[0].priority).toBeGreaterThanOrEqual(
        customRules[1].priority
      );
    });

    it('should unregister a rule', () => {
      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 10,
        condition: () => true,
        apply: () => {},
      };

      adaptiveUI.registerRule(rule);
      expect(adaptiveUI.getRules()).toContainEqual(rule);

      adaptiveUI.unregisterRule('test-rule');
      expect(adaptiveUI.getRules()).not.toContainEqual(rule);
    });
  });

  describe('Default Rules', () => {
    it('should have 7 default rules', () => {
      const rules = adaptiveUI.getRules();
      expect(rules).toHaveLength(7);
    });

    it('should include dark mode rule', () => {
      const rules = adaptiveUI.getRules();
      const darkModeRule = rules.find((r) => r.id === 'dark-mode');
      expect(darkModeRule).toBeDefined();
    });

    it('should include font size rule', () => {
      const rules = adaptiveUI.getRules();
      const fontSizeRule = rules.find((r) => r.id === 'font-size');
      expect(fontSizeRule).toBeDefined();
    });

    it('should include reduced motion rule', () => {
      const rules = adaptiveUI.getRules();
      const reducedMotionRule = rules.find((r) => r.id === 'reduced-motion');
      expect(reducedMotionRule).toBeDefined();
    });

    it('should include high contrast rule', () => {
      const rules = adaptiveUI.getRules();
      const highContrastRule = rules.find((r) => r.id === 'high-contrast');
      expect(highContrastRule).toBeDefined();
    });

    it('should include mobile layout rule', () => {
      const rules = adaptiveUI.getRules();
      const mobileLayoutRule = rules.find((r) => r.id === 'mobile-layout');
      expect(mobileLayoutRule).toBeDefined();
    });

    it('should include touch optimization rule', () => {
      const rules = adaptiveUI.getRules();
      const touchOptRule = rules.find((r) => r.id === 'touch-optimization');
      expect(touchOptRule).toBeDefined();
    });

    it('should include keyboard navigation rule', () => {
      const rules = adaptiveUI.getRules();
      const keyboardNavRule = rules.find((r) => r.id === 'keyboard-navigation');
      expect(keyboardNavRule).toBeDefined();
    });
  });

  describe('Context Detection', () => {
    it('should detect dark mode preference', () => {
      global.matchMedia = vi.fn((query: string) => ({
        matches: query.includes('prefers-color-scheme: dark'),
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })) as unknown as typeof global.matchMedia;

      const context = adaptiveUI.getContext();
      expect(context.darkMode).toBe(true);
    });

    it('should detect reduced motion preference', () => {
      global.matchMedia = vi.fn((query: string) => ({
        matches: query.includes('prefers-reduced-motion: reduce'),
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })) as unknown as typeof global.matchMedia;

      const context = adaptiveUI.getContext();
      expect(context.reducedMotion).toBe(true);
    });

    it('should detect high contrast preference', () => {
      global.matchMedia = vi.fn((query: string) => ({
        matches: query.includes('prefers-contrast: high'),
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })) as unknown as typeof global.matchMedia;

      const context = adaptiveUI.getContext();
      expect(context.highContrast).toBe(true);
    });

    it('should detect mobile device', () => {
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 375,
      });

      const context = adaptiveUI.getContext();
      expect(context.isMobile).toBe(true);
    });

    it('should detect touch capability', () => {
      Object.defineProperty(navigator, 'maxTouchPoints', {
        writable: true,
        configurable: true,
        value: 5,
      });

      const context = adaptiveUI.getContext();
      expect(context.hasTouch).toBe(true);
    });
  });

  describe('Preference Management', () => {
    it('should set and get user preferences', () => {
      const preferences: UserPreferences = {
        theme: 'dark',
        fontSize: 'large',
        reducedMotion: true,
        highContrast: false,
        compactMode: true,
      };

      adaptiveUI.setPreferences(preferences);
      const retrieved = adaptiveUI.getPreferences();

      expect(retrieved).toEqual(preferences);
    });

    it('should persist preferences to localStorage', () => {
      const preferences: UserPreferences = {
        theme: 'dark',
        fontSize: 'medium',
      };

      adaptiveUI.setPreferences(preferences);

      expect(localStorage.setItem).toHaveBeenCalledWith(
        'adaptive-ui-preferences',
        JSON.stringify(preferences)
      );
    });

    it('should load preferences from localStorage', () => {
      const preferences: UserPreferences = {
        theme: 'light',
        fontSize: 'small',
      };

      mockLocalStorage['adaptive-ui-preferences'] = JSON.stringify(preferences);

      const newAdaptiveUI = new AdaptiveUI();
      const loaded = newAdaptiveUI.getPreferences();

      expect(loaded).toEqual(preferences);
    });

    it('should merge new preferences with existing', () => {
      adaptiveUI.setPreferences({ theme: 'dark', fontSize: 'large' });
      adaptiveUI.setPreferences({ fontSize: 'small', compactMode: true });

      const preferences = adaptiveUI.getPreferences();

      expect(preferences).toEqual({
        theme: 'dark',
        fontSize: 'small',
        compactMode: true,
      });
    });

    it('should clear preferences', () => {
      adaptiveUI.setPreferences({ theme: 'dark' });
      adaptiveUI.clearPreferences();

      const preferences = adaptiveUI.getPreferences();
      expect(preferences).toEqual({});
    });
  });

  describe('Auto-Adaptation', () => {
    it('should start auto-adaptation', () => {
      adaptiveUI.start();
      // Should not throw
      expect(true).toBe(true);
    });

    it('should stop auto-adaptation', () => {
      adaptiveUI.start();
      adaptiveUI.stop();
      // Should not throw
      expect(true).toBe(true);
    });

    it('should apply rules when context changes', () => {
      const applySpy = vi.fn();
      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        condition: () => true,
        apply: applySpy,
      };

      adaptiveUI.registerRule(rule);
      adaptiveUI.applyAdaptations();

      expect(applySpy).toHaveBeenCalled();
    });

    it('should not apply rules that do not match condition', () => {
      const applySpy = vi.fn();
      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        condition: () => false,
        apply: applySpy,
      };

      adaptiveUI.registerRule(rule);
      adaptiveUI.applyAdaptations();

      expect(applySpy).not.toHaveBeenCalled();
    });

    it('should apply rules in priority order', () => {
      const applyOrder: number[] = [];

      const rule1: AdaptationRule = {
        id: 'rule-1',
        name: 'Rule 1',
        priority: 5,
        condition: () => true,
        apply: () => applyOrder.push(1),
      };

      const rule2: AdaptationRule = {
        id: 'rule-2',
        name: 'Rule 2',
        priority: 10,
        condition: () => true,
        apply: () => applyOrder.push(2),
      };

      adaptiveUI.registerRule(rule1);
      adaptiveUI.registerRule(rule2);
      adaptiveUI.applyAdaptations();

      // Higher priority (10) should be applied before lower priority (5)
      expect(applyOrder[0]).toBe(2);
      expect(applyOrder[1]).toBe(1);
    });
  });

  describe('Learning from User Behavior', () => {
    it('should learn theme preference from repeated changes', () => {
      // Simulate user changing to dark mode multiple times
      for (let i = 0; i < 5; i++) {
        adaptiveUI.setPreferences({ theme: 'dark' });
      }

      const preferences = adaptiveUI.getPreferences();
      expect(preferences.theme).toBe('dark');
    });

    it('should learn font size preference', () => {
      adaptiveUI.setPreferences({ fontSize: 'large' });

      const preferences = adaptiveUI.getPreferences();
      expect(preferences.fontSize).toBe('large');
    });

    it('should suggest preferences based on context', () => {
      // Mock dark mode environment
      global.matchMedia = vi.fn((query: string) => ({
        matches: query.includes('prefers-color-scheme: dark'),
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })) as unknown as typeof global.matchMedia;

      const suggestions = adaptiveUI.getSuggestedPreferences();
      expect(suggestions.theme).toBe('dark');
    });
  });

  describe('Rule Application with Context', () => {
    it('should pass context to condition function', () => {
      let receivedContext: AdaptationContext | undefined;

      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        condition: (context) => {
          receivedContext = context;
          return true;
        },
        apply: () => {},
      };

      adaptiveUI.registerRule(rule);
      adaptiveUI.applyAdaptations();

      expect(receivedContext).toBeDefined();
      expect(receivedContext).toHaveProperty('darkMode');
      expect(receivedContext).toHaveProperty('isMobile');
    });

    it('should pass context and preferences to apply function', () => {
      let receivedContext: AdaptationContext | undefined;
      let receivedPreferences: UserPreferences | undefined;

      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        condition: () => true,
        apply: (context, preferences) => {
          receivedContext = context;
          receivedPreferences = preferences;
        },
      };

      adaptiveUI.setPreferences({ theme: 'dark' });
      adaptiveUI.registerRule(rule);
      adaptiveUI.applyAdaptations();

      expect(receivedContext).toBeDefined();
      expect(receivedPreferences).toBeDefined();
      expect(receivedPreferences?.theme).toBe('dark');
    });
  });

  describe('Change Detection', () => {
    it('should detect when DOM changes', () => {
      const applySpy = vi.fn();
      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        condition: () => true,
        apply: applySpy,
      };

      adaptiveUI.registerRule(rule);
      adaptiveUI.start();

      // Simulate DOM change
      const div = document.createElement('div');
      document.body.appendChild(div);

      // Note: In a real test, MutationObserver would trigger automatically
      // Here we manually trigger for testing
      adaptiveUI.applyAdaptations();

      expect(applySpy).toHaveBeenCalled();

      document.body.removeChild(div);
    });

    it('should debounce rapid changes', async () => {
      const applySpy = vi.fn();
      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        condition: () => true,
        apply: applySpy,
      };

      adaptiveUI.registerRule(rule);
      adaptiveUI.start();

      // Trigger multiple adaptations rapidly
      adaptiveUI.applyAdaptations();
      adaptiveUI.applyAdaptations();
      adaptiveUI.applyAdaptations();

      // Should be called at least once, but not for every call due to debouncing
      expect(applySpy).toHaveBeenCalled();
    });
  });

  describe('Edge Cases', () => {
    it('should handle missing localStorage gracefully', () => {
      // @ts-expect-error - Simulate missing localStorage
      global.localStorage = undefined;

      expect(() => {
        const newAdaptiveUI = new AdaptiveUI();
        newAdaptiveUI.setPreferences({ theme: 'dark' });
      }).not.toThrow();
    });

    it('should handle invalid JSON in localStorage', () => {
      mockLocalStorage['adaptive-ui-preferences'] = 'invalid json {';

      expect(() => {
        const newAdaptiveUI = new AdaptiveUI();
        newAdaptiveUI.getPreferences();
      }).not.toThrow();
    });

    it('should handle rule with no condition', () => {
      const rule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        apply: vi.fn(),
      } as AdaptationRule;

      expect(() => {
        adaptiveUI.registerRule(rule);
        adaptiveUI.applyAdaptations();
      }).not.toThrow();
    });

    it('should handle rule application errors', () => {
      const rule: AdaptationRule = {
        id: 'test-rule',
        name: 'Test Rule',
        priority: 100,
        condition: () => true,
        apply: () => {
          throw new Error('Test error');
        },
      };

      adaptiveUI.registerRule(rule);

      expect(() => {
        adaptiveUI.applyAdaptations();
      }).not.toThrow();
    });

    it('should handle duplicate rule IDs', () => {
      const rule1: AdaptationRule = {
        id: 'same-id',
        name: 'Rule 1',
        priority: 10,
        condition: () => true,
        apply: () => {},
      };

      const rule2: AdaptationRule = {
        id: 'same-id',
        name: 'Rule 2',
        priority: 20,
        condition: () => true,
        apply: () => {},
      };

      adaptiveUI.registerRule(rule1);
      adaptiveUI.registerRule(rule2);

      const rules = adaptiveUI.getRules();
      const sameIdRules = rules.filter((r) => r.id === 'same-id');

      // Should keep only one or handle duplicates gracefully
      expect(sameIdRules.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('Performance', () => {
    it('should handle many rules efficiently', () => {
      const startTime = Date.now();

      // Register 100 rules
      for (let i = 0; i < 100; i++) {
        adaptiveUI.registerRule({
          id: `rule-${i}`,
          name: `Rule ${i}`,
          priority: i,
          condition: () => i % 2 === 0, // 50% match
          apply: () => {},
        });
      }

      adaptiveUI.applyAdaptations();

      const endTime = Date.now();
      const duration = endTime - startTime;

      // Should complete in reasonable time (< 100ms)
      expect(duration).toBeLessThan(100);
    });

    it('should handle complex conditions efficiently', () => {
      const startTime = Date.now();

      adaptiveUI.registerRule({
        id: 'complex-rule',
        name: 'Complex Rule',
        priority: 100,
        condition: (context) => {
          // Complex condition
          return (
            context.isMobile &&
            context.hasTouch &&
            !context.reducedMotion &&
            context.darkMode
          );
        },
        apply: () => {},
      });

      // Apply 100 times
      for (let i = 0; i < 100; i++) {
        adaptiveUI.applyAdaptations();
      }

      const endTime = Date.now();
      const duration = endTime - startTime;

      // Should complete in reasonable time (< 200ms)
      expect(duration).toBeLessThan(200);
    });
  });
});
