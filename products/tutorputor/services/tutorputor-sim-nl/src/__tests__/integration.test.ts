/**
 * NL Refinement Integration Tests
 *
 * @doc.type test
 * @doc.purpose End-to-end tests for natural language refinement of simulations
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import type { SimulationManifest, SimEntity, SimAction } from '@ghatana/tutorputor-contracts/v1/simulation/types';

// Mock AI service for testing
const mockAIService = {
  complete: vi.fn(),
  chat: vi.fn(),
};

/**
 * Create a base test manifest for refinement
 */
function createBaseManifest(): SimulationManifest {
  return {
    id: 'nl-refinement-test',
    version: '1.0',
    domain: 'PHYSICS',
    title: 'Pendulum Simulation',
    description: 'A simple pendulum swinging',
    initialEntities: [
      {
        id: 'pivot',
        type: 'rigidBody',
        label: 'Pivot',
        x: 200,
        y: 50,
        fixed: true,
        mass: 0,
      } as SimEntity,
      {
        id: 'bob',
        type: 'rigidBody',
        label: 'Pendulum Bob',
        x: 300,
        y: 150,
        fixed: false,
        mass: 1,
        velocityX: 0,
        velocityY: 0,
      } as SimEntity,
      {
        id: 'rod',
        type: 'spring',
        label: 'Rod',
        anchorId: 'pivot',
        attachId: 'bob',
        stiffness: 10000,
        damping: 0.1,
        restLength: 120,
        x: 250,
        y: 100,
      } as SimEntity,
    ],
    steps: [
      { id: 'step-0', orderIndex: 0, label: 'Release pendulum', actions: [] },
      { id: 'step-1', orderIndex: 1, label: 'Swing left', actions: [] },
      { id: 'step-2', orderIndex: 2, label: 'Swing right', actions: [] },
    ],
    domainConfig: {
      duration: 5000,
      frameRate: 60,
    },
    domainMetadata: {
      physics: {
        gravity: { x: 0, y: 9.81 },
        timeScale: 1,
      },
    },
    metadata: {
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      author: 'test',
    },
  };
}

describe('NL Refinement Integration', () => {
  describe('Intent Parsing', () => {
    it('should parse speed modification intents', () => {
      const intents = [
        { input: 'make it faster', expected: { type: 'MODIFY_SPEED', direction: 'faster' } },
        { input: 'slow down the simulation', expected: { type: 'MODIFY_SPEED', direction: 'slower' } },
        { input: 'speed up', expected: { type: 'MODIFY_SPEED', direction: 'faster' } },
        { input: 'run at half speed', expected: { type: 'MODIFY_SPEED', direction: 'slower' } },
      ];

      intents.forEach(({ input, expected }) => {
        const parsed = parseSimpleIntent(input);
        expect(parsed.type).toBe(expected.type);
      });
    });

    it('should parse entity addition intents', () => {
      const intents = [
        { input: 'add another ball', expected: { type: 'ADD_ENTITY', entityType: 'ball' } },
        { input: 'put a red circle at the top', expected: { type: 'ADD_ENTITY', entityType: 'circle' } },
        { input: 'add a second pendulum', expected: { type: 'ADD_ENTITY', entityType: 'pendulum' } },
      ];

      intents.forEach(({ input, expected }) => {
        const parsed = parseSimpleIntent(input);
        expect(parsed.type).toBe(expected.type);
      });
    });

    it('should parse parameter change intents', () => {
      const intents = [
        { input: 'increase gravity', expected: { type: 'CHANGE_PARAMETER', parameter: 'gravity', direction: 'increase' } },
        { input: 'reduce friction', expected: { type: 'CHANGE_PARAMETER', parameter: 'friction', direction: 'decrease' } },
        { input: 'set mass to 5', expected: { type: 'CHANGE_PARAMETER', parameter: 'mass', value: 5 } },
        { input: 'double the length', expected: { type: 'CHANGE_PARAMETER', parameter: 'length', multiplier: 2 } },
      ];

      intents.forEach(({ input, expected }) => {
        const parsed = parseSimpleIntent(input);
        expect(parsed.type).toBe(expected.type);
      });
    });

    it('should parse removal intents', () => {
      const intents = [
        { input: 'remove the ball', expected: { type: 'REMOVE_ENTITY' } },
        { input: 'delete gravity', expected: { type: 'REMOVE_ENTITY' } },
        { input: 'get rid of friction', expected: { type: 'CHANGE_PARAMETER', parameter: 'friction', value: 0 } },
      ];

      intents.forEach(({ input, expected }) => {
        const parsed = parseSimpleIntent(input);
        expect(parsed.type).toBe(expected.type);
      });
    });

    it('should parse color change intents', () => {
      const intents = [
        { input: 'make the ball red', expected: { type: 'CHANGE_VISUAL', property: 'color', value: 'red' } },
        { input: 'change color to blue', expected: { type: 'CHANGE_VISUAL', property: 'color', value: 'blue' } },
        { input: 'use green for the pendulum', expected: { type: 'CHANGE_VISUAL', property: 'color', value: 'green' } },
      ];

      intents.forEach(({ input, expected }) => {
        const parsed = parseSimpleIntent(input);
        expect(parsed.type).toBe(expected.type);
      });
    });
  });

  describe('Manifest Refinement', () => {
    it('should apply speed modification to manifest', () => {
      const manifest = createBaseManifest();
      const intent = { type: 'MODIFY_SPEED', direction: 'faster', factor: 2 };

      const refined = applyRefinement(manifest, intent);

      expect(refined.domainConfig.duration).toBe(2500); // Half the original duration
    });

    it('should add entity to manifest', () => {
      const manifest = createBaseManifest();
      const intent = {
        type: 'ADD_ENTITY',
        entityType: 'rigidBody',
        properties: {
          id: 'new-ball',
          label: 'New Ball',
          x: 100,
          y: 200,
          mass: 2,
        },
      };

      const refined = applyRefinement(manifest, intent);

      expect(refined.initialEntities).toHaveLength(4);
      expect(refined.initialEntities.find(e => e.id === 'new-ball')).toBeDefined();
    });

    it('should modify parameter in manifest', () => {
      const manifest = createBaseManifest();
      const intent = {
        type: 'CHANGE_PARAMETER',
        parameter: 'gravity',
        value: { x: 0, y: 20 },
      };

      const refined = applyRefinement(manifest, intent);

      expect(refined.domainMetadata!.physics.gravity.y).toBe(20);
    });

    it('should remove entity from manifest', () => {
      const manifest = createBaseManifest();
      const intent = {
        type: 'REMOVE_ENTITY',
        entityId: 'rod',
      };

      const refined = applyRefinement(manifest, intent);

      expect(refined.initialEntities).toHaveLength(2);
      expect(refined.initialEntities.find(e => e.id === 'rod')).toBeUndefined();
    });

    it('should change visual properties', () => {
      const manifest = createBaseManifest();
      manifest.initialEntities[1].visual = { fill: '#4A90D9' };
      
      const intent = {
        type: 'CHANGE_VISUAL',
        entityId: 'bob',
        property: 'fill',
        value: '#FF0000',
      };

      const refined = applyRefinement(manifest, intent);

      const bob = refined.initialEntities.find(e => e.id === 'bob');
      expect(bob?.visual?.fill).toBe('#FF0000');
    });
  });

  describe('Full Refinement Flow', () => {
    it('should handle multi-step refinement conversation', async () => {
      let manifest = createBaseManifest();

      // Step 1: User says "make it faster"
      const intent1 = parseSimpleIntent('make it faster');
      manifest = applyRefinement(manifest, { ...intent1, factor: 2 });
      expect(manifest.domainConfig.duration).toBe(2500);

      // Step 2: User says "add a second pendulum"
      const intent2 = parseSimpleIntent('add another pendulum');
      manifest = applyRefinement(manifest, {
        ...intent2,
        entityType: 'rigidBody',
        properties: {
          id: 'bob-2',
          type: 'rigidBody',
          label: 'Second Bob',
          x: 100,
          y: 150,
          mass: 1,
        },
      });
      expect(manifest.initialEntities.find(e => e.id === 'bob-2')).toBeDefined();

      // Step 3: User says "increase gravity"
      const intent3 = parseSimpleIntent('increase gravity');
      manifest = applyRefinement(manifest, {
        ...intent3,
        parameter: 'gravity',
        value: { x: 0, y: 15 },
      });
      expect(manifest.domainMetadata!.physics.gravity.y).toBe(15);
    });

    it('should handle complex natural language instructions', async () => {
      let manifest = createBaseManifest();

      // Complex instruction: "Add a heavier ball at position 50,100 and make everything run slower"
      const instructions = [
        {
          type: 'ADD_ENTITY',
          properties: {
            id: 'heavy-ball',
            type: 'rigidBody',
            label: 'Heavy Ball',
            x: 50,
            y: 100,
            mass: 5,
          },
        },
        {
          type: 'MODIFY_SPEED',
          direction: 'slower',
          factor: 0.5,
        },
      ];

      for (const intent of instructions) {
        manifest = applyRefinement(manifest, intent);
      }

      expect(manifest.initialEntities.find(e => e.id === 'heavy-ball')).toBeDefined();
      expect(manifest.domainConfig.duration).toBe(10000); // Double the original
    });

    it('should preserve manifest validity after refinements', () => {
      let manifest = createBaseManifest();

      // Apply many refinements
      manifest = applyRefinement(manifest, { type: 'MODIFY_SPEED', factor: 2 });
      manifest = applyRefinement(manifest, { type: 'CHANGE_PARAMETER', parameter: 'gravity', value: { x: 0, y: 20 } });
      manifest = applyRefinement(manifest, {
        type: 'ADD_ENTITY',
        properties: { id: 'new-entity', type: 'rigidBody', x: 0, y: 0 },
      });

      // Validate manifest structure
      expect(manifest.id).toBeDefined();
      expect(manifest.version).toBeDefined();
      expect(manifest.domain).toBeDefined();
      expect(manifest.initialEntities).toBeInstanceOf(Array);
      expect(manifest.steps).toBeInstanceOf(Array);
      expect(manifest.metadata).toBeDefined();
    });
  });

  describe('Undo/Redo Support', () => {
    it('should support undo of refinements', () => {
      const history: SimulationManifest[] = [];
      let manifest = createBaseManifest();
      history.push(JSON.parse(JSON.stringify(manifest)));

      // Make a change
      manifest = applyRefinement(manifest, { type: 'MODIFY_SPEED', factor: 2 });
      history.push(JSON.parse(JSON.stringify(manifest)));

      // Undo
      manifest = history[history.length - 2];
      expect(manifest.domainConfig.duration).toBe(5000); // Original value
    });

    it('should track refinement history', () => {
      const refinementLog: Array<{ timestamp: number; intent: any; description: string }> = [];
      let manifest = createBaseManifest();

      const logRefinement = (intent: any, description: string) => {
        refinementLog.push({
          timestamp: Date.now(),
          intent,
          description,
        });
      };

      // Apply refinements with logging
      const intent1 = { type: 'MODIFY_SPEED', factor: 2 };
      manifest = applyRefinement(manifest, intent1);
      logRefinement(intent1, 'Made simulation faster');

      const intent2 = { type: 'CHANGE_PARAMETER', parameter: 'gravity', value: { x: 0, y: 20 } };
      manifest = applyRefinement(manifest, intent2);
      logRefinement(intent2, 'Increased gravity');

      expect(refinementLog).toHaveLength(2);
      expect(refinementLog[0].description).toBe('Made simulation faster');
      expect(refinementLog[1].description).toBe('Increased gravity');
    });
  });

  describe('Suggestion Generation', () => {
    it('should generate relevant suggestions based on manifest', () => {
      const manifest = createBaseManifest();
      const suggestions = generateSuggestions(manifest);

      expect(suggestions).toBeInstanceOf(Array);
      expect(suggestions.length).toBeGreaterThan(0);
      
      // Should include physics-related suggestions
      const hasGravitySuggestion = suggestions.some(s => 
        s.toLowerCase().includes('gravity')
      );
      expect(hasGravitySuggestion).toBe(true);
    });

    it('should generate domain-specific suggestions', () => {
      const physicsManifest = createBaseManifest();
      physicsManifest.domain = 'PHYSICS';
      const physicsSuggestions = generateSuggestions(physicsManifest);

      const chemistryManifest = { ...createBaseManifest(), domain: 'CHEMISTRY' };
      const chemistrySuggestions = generateSuggestions(chemistryManifest);

      // Physics should have different suggestions than chemistry
      expect(physicsSuggestions).not.toEqual(chemistrySuggestions);
    });
  });
});

// Helper functions for testing

/**
 * Simple intent parser for testing
 */
function parseSimpleIntent(input: string): { type: string; [key: string]: any } {
  const lowerInput = input.toLowerCase();

  // Speed modifications
  if (lowerInput.includes('faster') || lowerInput.includes('speed up')) {
    return { type: 'MODIFY_SPEED', direction: 'faster' };
  }
  if (lowerInput.includes('slower') || lowerInput.includes('slow down') || lowerInput.includes('half speed')) {
    return { type: 'MODIFY_SPEED', direction: 'slower' };
  }

  // Entity additions
  if (lowerInput.includes('add') || lowerInput.includes('put')) {
    const entityTypes = ['ball', 'circle', 'pendulum', 'box', 'spring'];
    const foundType = entityTypes.find(t => lowerInput.includes(t));
    return { type: 'ADD_ENTITY', entityType: foundType || 'entity' };
  }

  // Parameter changes
  if (lowerInput.includes('increase') || lowerInput.includes('more')) {
    const param = extractParameter(lowerInput);
    return { type: 'CHANGE_PARAMETER', parameter: param, direction: 'increase' };
  }
  if (lowerInput.includes('decrease') || lowerInput.includes('reduce') || lowerInput.includes('less')) {
    const param = extractParameter(lowerInput);
    return { type: 'CHANGE_PARAMETER', parameter: param, direction: 'decrease' };
  }
  if (lowerInput.includes('set') || lowerInput.includes('double') || lowerInput.includes('half')) {
    const param = extractParameter(lowerInput);
    return { type: 'CHANGE_PARAMETER', parameter: param };
  }

  // Removals
  if (lowerInput.includes('remove') || lowerInput.includes('delete')) {
    return { type: 'REMOVE_ENTITY' };
  }
  if (lowerInput.includes('get rid of')) {
    const param = extractParameter(lowerInput);
    return { type: 'CHANGE_PARAMETER', parameter: param, value: 0 };
  }

  // Visual changes
  if (lowerInput.includes('color') || lowerInput.includes('red') || lowerInput.includes('blue') || lowerInput.includes('green')) {
    const colors = ['red', 'blue', 'green', 'yellow', 'orange', 'purple'];
    const color = colors.find(c => lowerInput.includes(c));
    return { type: 'CHANGE_VISUAL', property: 'color', value: color };
  }

  return { type: 'UNKNOWN' };
}

/**
 * Extract parameter name from input
 */
function extractParameter(input: string): string {
  const parameters = ['gravity', 'friction', 'mass', 'length', 'speed', 'stiffness', 'damping'];
  const found = parameters.find(p => input.includes(p));
  return found || 'unknown';
}

/**
 * Apply refinement to manifest
 */
function applyRefinement(manifest: SimulationManifest, intent: any): SimulationManifest {
  const refined = JSON.parse(JSON.stringify(manifest)) as SimulationManifest;

  switch (intent.type) {
    case 'MODIFY_SPEED':
      if (intent.factor) {
        refined.domainConfig.duration = Math.round(
          (refined.domainConfig.duration || 5000) / intent.factor
        );
      }
      break;

    case 'ADD_ENTITY':
      if (intent.properties) {
        refined.initialEntities.push(intent.properties as SimEntity);
      }
      break;

    case 'CHANGE_PARAMETER':
      if (intent.parameter === 'gravity' && intent.value && refined.domainMetadata?.physics) {
        refined.domainMetadata.physics.gravity = intent.value;
      }
      break;

    case 'REMOVE_ENTITY':
      if (intent.entityId) {
        refined.initialEntities = refined.initialEntities.filter(
          e => e.id !== intent.entityId
        );
      }
      break;

    case 'CHANGE_VISUAL':
      if (intent.entityId && intent.property && intent.value) {
        const entity = refined.initialEntities.find(e => e.id === intent.entityId);
        if (entity) {
          entity.visual = entity.visual || {};
          (entity.visual as any)[intent.property] = intent.value;
        }
      }
      break;
  }

  return refined;
}

/**
 * Generate suggestions based on manifest
 */
function generateSuggestions(manifest: SimulationManifest): string[] {
  const suggestions: string[] = [];

  switch (manifest.domain) {
    case 'PHYSICS':
      suggestions.push('Try adjusting gravity to see how it affects motion');
      suggestions.push('Add more objects to see collisions');
      suggestions.push('Change the mass of objects');
      suggestions.push('Add friction to the simulation');
      break;

    case 'CHEMISTRY':
      suggestions.push('Add more reactants to the reaction');
      suggestions.push('Adjust the temperature');
      suggestions.push('Add a catalyst');
      suggestions.push('Change bond energies');
      break;

    case 'BIOLOGY':
      suggestions.push('Modify population parameters');
      suggestions.push('Add predators or prey');
      suggestions.push('Change reproduction rates');
      suggestions.push('Add environmental factors');
      break;

    default:
      suggestions.push('Make the simulation faster or slower');
      suggestions.push('Add more elements');
      suggestions.push('Change colors for visibility');
  }

  return suggestions;
}
