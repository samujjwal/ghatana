/**
 * Phase Actions Tests — YAPPC Web.
 *
 * Tests for phase-specific action contracts.
 *
 * @doc.type test
 * @doc.purpose Test phase action contracts
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import {
  INTENT_PHASE_ACTIONS,
  SHAPE_PHASE_ACTIONS,
  VALIDATE_PHASE_ACTIONS,
  GENERATE_PHASE_ACTIONS,
  RUN_PHASE_ACTIONS,
  OBSERVE_PHASE_ACTIONS,
  LEARN_PHASE_ACTIONS,
  EVOLVE_PHASE_ACTIONS,
  getPhaseActions,
  getPhaseActionById,
  type LifecyclePhase,
} from '../phaseActions';

describe('Phase Actions', () => {
  describe('Intent Phase Actions', () => {
    it('should have all required intent actions', () => {
      expect(INTENT_PHASE_ACTIONS).toHaveLength(6);
      const actionIds = INTENT_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('intent.capture');
      expect(actionIds).toContain('intent.update');
      expect(actionIds).toContain('intent.delete');
      expect(actionIds).toContain('intent.analyze');
      expect(actionIds).toContain('intent.approve');
      expect(actionIds).toContain('intent.reject');
    });

    it('should have correct permissions for each action', () => {
      const capture = INTENT_PHASE_ACTIONS.find((a) => a.actionId === 'intent.capture');
      expect(capture?.requiredPermission).toBe('PROJECT_UPDATE');

      const deleteAction = INTENT_PHASE_ACTIONS.find((a) => a.actionId === 'intent.delete');
      expect(deleteAction?.requiredPermission).toBe('PROJECT_DELETE');
    });
  });

  describe('Shape Phase Actions', () => {
    it('should have all required shape actions', () => {
      expect(SHAPE_PHASE_ACTIONS).toHaveLength(7);
      const actionIds = SHAPE_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('shape.create');
      expect(actionIds).toContain('shape.update');
      expect(actionIds).toContain('shape.delete');
      expect(actionIds).toContain('shape.analyze');
      expect(actionIds).toContain('shape.approve');
      expect(actionIds).toContain('shape.reject');
      expect(actionIds).toContain('shape.preview');
    });
  });

  describe('Validate Phase Actions', () => {
    it('should have all required validate actions', () => {
      expect(VALIDATE_PHASE_ACTIONS).toHaveLength(4);
      const actionIds = VALIDATE_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('validate.run');
      expect(actionIds).toContain('validate.approve');
      expect(actionIds).toContain('validate.reject');
      expect(actionIds).toContain('validate.view-results');
    });
  });

  describe('Generate Phase Actions', () => {
    it('should have all required generate actions', () => {
      expect(GENERATE_PHASE_ACTIONS).toHaveLength(6);
      const actionIds = GENERATE_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('generate.run');
      expect(actionIds).toContain('generate.apply');
      expect(actionIds).toContain('generate.rollback');
      expect(actionIds).toContain('generate.approve');
      expect(actionIds).toContain('generate.reject');
      expect(actionIds).toContain('generate.preview');
    });

    it('should require PROJECT_DELETE for rollback', () => {
      const rollback = GENERATE_PHASE_ACTIONS.find((a) => a.actionId === 'generate.rollback');
      expect(rollback?.requiredPermission).toBe('PROJECT_DELETE');
    });
  });

  describe('Run Phase Actions', () => {
    it('should have all required run actions', () => {
      expect(RUN_PHASE_ACTIONS).toHaveLength(6);
      const actionIds = RUN_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('run.build');
      expect(actionIds).toContain('run.test');
      expect(actionIds).toContain('run.deploy');
      expect(actionIds).toContain('run.rollback');
      expect(actionIds).toContain('run.view-logs');
      expect(actionIds).toContain('run.view-metrics');
    });

    it('should require PROJECT_DELETE for rollback', () => {
      const rollback = RUN_PHASE_ACTIONS.find((a) => a.actionId === 'run.rollback');
      expect(rollback?.requiredPermission).toBe('PROJECT_DELETE');
    });
  });

  describe('Observe Phase Actions', () => {
    it('should have all required observe actions', () => {
      expect(OBSERVE_PHASE_ACTIONS).toHaveLength(5);
      const actionIds = OBSERVE_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('observe.collect');
      expect(actionIds).toContain('observe.analyze');
      expect(actionIds).toContain('observe.view-metrics');
      expect(actionIds).toContain('observe.view-logs');
      expect(actionIds).toContain('observe.set-alerts');
    });
  });

  describe('Learn Phase Actions', () => {
    it('should have all required learn actions', () => {
      expect(LEARN_PHASE_ACTIONS).toHaveLength(5);
      const actionIds = LEARN_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('learn.collect-feedback');
      expect(actionIds).toContain('learn.analyze-feedback');
      expect(actionIds).toContain('learn.generate-insights');
      expect(actionIds).toContain('learn.update-models');
      expect(actionIds).toContain('learn.view-insights');
    });
  });

  describe('Evolve Phase Actions', () => {
    it('should have all required evolve actions', () => {
      expect(EVOLVE_PHASE_ACTIONS).toHaveLength(5);
      const actionIds = EVOLVE_PHASE_ACTIONS.map((a) => a.actionId);
      expect(actionIds).toContain('evolve.propose');
      expect(actionIds).toContain('evolve.approve');
      expect(actionIds).toContain('evolve.reject');
      expect(actionIds).toContain('evolve.apply');
      expect(actionIds).toContain('evolve.view-proposals');
    });
  });

  describe('getPhaseActions', () => {
    it('should return correct actions for each phase', () => {
      expect(getPhaseActions('intent')).toEqual(INTENT_PHASE_ACTIONS);
      expect(getPhaseActions('shape')).toEqual(SHAPE_PHASE_ACTIONS);
      expect(getPhaseActions('validate')).toEqual(VALIDATE_PHASE_ACTIONS);
      expect(getPhaseActions('generate')).toEqual(GENERATE_PHASE_ACTIONS);
      expect(getPhaseActions('run')).toEqual(RUN_PHASE_ACTIONS);
      expect(getPhaseActions('observe')).toEqual(OBSERVE_PHASE_ACTIONS);
      expect(getPhaseActions('learn')).toEqual(LEARN_PHASE_ACTIONS);
      expect(getPhaseActions('evolve')).toEqual(EVOLVE_PHASE_ACTIONS);
    });
  });

  describe('getPhaseActionById', () => {
    it('should return action by ID for each phase', () => {
      const intentCapture = getPhaseActionById('intent', 'intent.capture');
      expect(intentCapture?.actionId).toBe('intent.capture');
      expect(intentCapture?.label).toBe('Capture Intent');

      const generateRun = getPhaseActionById('generate', 'generate.run');
      expect(generateRun?.actionId).toBe('generate.run');
      expect(generateRun?.label).toBe('Generate Artifacts');

      const nonExistent = getPhaseActionById('intent', 'non-existent');
      expect(nonExistent).toBeUndefined();
    });
  });

  describe('Action Contract Consistency', () => {
    it('should have consistent action contract structure across all phases', () => {
      const allActions = [
        ...INTENT_PHASE_ACTIONS,
        ...SHAPE_PHASE_ACTIONS,
        ...VALIDATE_PHASE_ACTIONS,
        ...GENERATE_PHASE_ACTIONS,
        ...RUN_PHASE_ACTIONS,
        ...OBSERVE_PHASE_ACTIONS,
        ...LEARN_PHASE_ACTIONS,
        ...EVOLVE_PHASE_ACTIONS,
      ];

      allActions.forEach((action) => {
        expect(action).toHaveProperty('actionId');
        expect(action).toHaveProperty('label');
        expect(action).toHaveProperty('description');
        expect(action).toHaveProperty('enabled');
        expect(action).toHaveProperty('requiredPermission');
        expect(action).toHaveProperty('parameters');
        expect(typeof action.actionId).toBe('string');
        expect(typeof action.label).toBe('string');
        expect(typeof action.description).toBe('string');
        expect(typeof action.enabled).toBe('boolean');
        expect(typeof action.requiredPermission).toBe('string');
        expect(typeof action.parameters).toBe('object');
      });
    });

    it('should have unique action IDs within each phase', () => {
      const phases: LifecyclePhase[] = ['intent', 'shape', 'validate', 'generate', 'run', 'observe', 'learn', 'evolve'];
      
      phases.forEach((phase) => {
        const actions = getPhaseActions(phase);
        const actionIds = actions.map((a) => a.actionId);
        const uniqueIds = new Set(actionIds);
        expect(uniqueIds.size).toBe(actionIds.length);
      });
    });
  });
});
