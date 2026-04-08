/**
 * LearningService Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import {
  recordAction,
  getState,
  getPreferences,
  getPatterns,
  resetState,
  type UserAction,
} from '../LearningService';

describe('LearningService', () => {
  beforeEach(() => {
    resetState();
  });

  describe('recordAction', () => {
    it('should add action to state', () => {
      const state = recordAction({
        category: 'navigation',
        action: 'open-project',
        context: 'project:p1',
      });

      expect(state.actions.length).toBe(1);
      expect(state.actions[0].action).toBe('open-project');
    });

    it('should generate unique id and timestamp', () => {
      const state = recordAction({
        category: 'creation',
        action: 'create-task',
        context: 'project:p1',
      });

      expect(state.actions[0].id).toMatch(/^act-/);
      expect(state.actions[0].timestamp).toBeGreaterThan(0);
    });

    it('should cap actions at 500', () => {
      for (let i = 0; i < 510; i++) {
        recordAction({ category: 'navigation', action: `nav-${i}`, context: 'ctx' });
      }
      const state = getState();
      expect(state.actions.length).toBe(500);
    });

    it('should update patterns after recording', () => {
      recordAction({ category: 'navigation', action: 'open-project', context: 'project:p1' });
      recordAction({ category: 'navigation', action: 'open-settings', context: 'global' });

      const patterns = getPatterns();
      const navPattern = patterns.find((p) => p.category === 'navigation');
      expect(navPattern).toBeDefined();
      expect(navPattern!.frequency).toBe(2);
    });

    it('should update preferences after recording', () => {
      recordAction({ category: 'navigation', action: 'open-project', context: 'project:p1' });
      const prefs = getPreferences();
      expect(prefs.frequentActions).toContain('open-project');
    });
  });

  describe('getState', () => {
    it('should return default state when empty', () => {
      const state = getState();
      expect(state.actions).toEqual([]);
      expect(state.patterns).toEqual([]);
      expect(state.preferences.preferredLayout).toBe('comfortable');
    });
  });

  describe('getPreferences', () => {
    it('should return default preferences when no actions', () => {
      const prefs = getPreferences();
      expect(prefs.preferredLayout).toBe('comfortable');
      expect(prefs.navigationStyle).toBe('sidebar');
      expect(prefs.aiAssistanceLevel).toBe('moderate');
    });

    it('should derive compact layout for heavy usage', () => {
      for (let i = 0; i < 210; i++) {
        recordAction({ category: 'editing', action: `edit-${i}`, context: 'ctx' });
      }
      const prefs = getPreferences();
      expect(prefs.preferredLayout).toBe('compact');
    });

    it('should derive spacious layout for light usage', () => {
      for (let i = 0; i < 10; i++) {
        recordAction({ category: 'editing', action: `edit-${i}`, context: 'ctx' });
      }
      const prefs = getPreferences();
      expect(prefs.preferredLayout).toBe('spacious');
    });

    it('should track recent projects', () => {
      recordAction({ category: 'navigation', action: 'open', context: 'project:alpha' });
      recordAction({ category: 'navigation', action: 'open', context: 'project:beta' });

      const prefs = getPreferences();
      expect(prefs.recentProjects).toContain('alpha');
      expect(prefs.recentProjects).toContain('beta');
    });

    it('should detect command navigation style for search-heavy users', () => {
      for (let i = 0; i < 20; i++) {
        recordAction({ category: 'search', action: 'search', context: 'global' });
      }
      for (let i = 0; i < 5; i++) {
        recordAction({ category: 'navigation', action: 'nav', context: 'global' });
      }
      const prefs = getPreferences();
      expect(prefs.navigationStyle).toBe('command');
    });
  });

  describe('getPatterns', () => {
    it('should return empty array when no actions', () => {
      expect(getPatterns()).toEqual([]);
    });

    it('should group by category', () => {
      recordAction({ category: 'creation', action: 'a', context: 'c' });
      recordAction({ category: 'editing', action: 'b', context: 'c' });

      const patterns = getPatterns();
      expect(patterns.length).toBe(2);
    });

    it('should sort by frequency descending', () => {
      recordAction({ category: 'editing', action: 'a', context: 'c' });
      recordAction({ category: 'editing', action: 'a', context: 'c' });
      recordAction({ category: 'creation', action: 'b', context: 'c' });

      const patterns = getPatterns();
      expect(patterns[0].category).toBe('editing');
    });
  });

  describe('resetState', () => {
    it('should clear all data', () => {
      recordAction({ category: 'navigation', action: 'nav', context: 'c' });
      resetState();
      const state = getState();
      expect(state.actions).toEqual([]);
    });
  });
});
